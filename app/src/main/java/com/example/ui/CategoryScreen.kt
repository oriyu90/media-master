package com.example.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.FileViewModel
import com.example.MediaFile
import com.example.ViewState
import com.example.ViewMode
import com.example.R
import com.example.ui.components.SortViewMenu
import com.example.ui.components.OpenWithDropdownItem
import com.example.ui.components.SelectDropdownItem
import com.example.ui.components.rightClickMenu
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    categoryName: String,
    viewModel: FileViewModel,
    navController: NavHostController,
    // v1.10.0 (#3): desktop shell passes true → LIST default (Explorer parity).
    desktop: Boolean = false,
    // v1.12.0: desktop opens documents in a new tab (title = file name).
    onOpenDocInNewTab: (uriString: String, path: String?, title: String) -> Unit = { _, _, _ -> },
) {
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()
    val categoryViewMode by viewModel.categoryViewMode.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val showExcluded by viewModel.showExcludedInManage.collectAsStateWithLifecycle()
    // v1.10.0 (#4): same Explorer columns as the file browser.
    val showSize by viewModel.listShowSize.collectAsStateWithLifecycle()
    val showModified by viewModel.listShowDateModified.collectAsStateWithLifecycle()
    val showCreated by viewModel.listShowDateCreated.collectAsStateWithLifecycle()
    val showType by viewModel.listShowType.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()
    var pendingDeleteCount by remember { mutableStateOf(0) }

    if (pendingDeleteCount > 0) {
        com.example.ui.components.ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            message = pluralStringResource(R.plurals.items_selected, pendingDeleteCount, pendingDeleteCount),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { pendingDeleteCount = 0 },
            onConfirm = {
                val toDelete = selectedFiles.toList()
                pendingDeleteCount = 0
                val files = (viewState as? ViewState.Success)?.files.orEmpty()
                selectedFiles.clear()
                toDelete.forEach { path ->
                    files.find { it.path == path }?.let {
                        viewModel.deleteFile(it.path, it.contentUri)
                    }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllMedia()
    }

    // v1.10.0 (#2/#3): group by full parent *path* (never merge same-named
    // folders), desktop defaults to LIST. Guarded in ViewModel so explicit
    // user toggles survive recompose.
    LaunchedEffect(categoryName, desktop) {
        val category = MediaCategory.fromKey(categoryName)
        val isGallery = category == MediaCategory.IMAGES || category == MediaCategory.VIDEOS
        viewModel.applyCategoryDesktopDefault(desktop, isGallery)
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.items_selected, selectedFiles.size, selectedFiles.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            val allDisplayedFiles = (viewState as? ViewState.Success)?.files?.filter { file ->
                                val isExcluded = excludedFolders.any { file.path.startsWith(it) }
                                val shouldShow = !isExcluded || showExcluded
                                
                                shouldShow && (MediaCategory.fromKey(categoryName)?.matches(file) == true)
                            } ?: emptyList()
                            if (selectedFiles.size == allDisplayedFiles.size) {
                                selectedFiles.clear()
                            } else {
                                selectedFiles.clear()
                                selectedFiles.addAll(allDisplayedFiles.map { it.path })
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                        }
                        IconButton(onClick = { 
                            val uris = selectedFiles.mapNotNull { path ->
                                (viewState as? ViewState.Success)?.files?.find { it.path == path }?.contentUri
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_media)))
                            }
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = {
                            pendingDeleteCount = selectedFiles.size
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                )
            } else {
                val titleRes = MediaCategory.fromKey(categoryName)?.titleRes?.let { stringResource(it) } ?: categoryName
                TopAppBar(
                    title = { Text(titleRes) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        SortViewMenu(viewModel = viewModel, isCategory = true, showExcludedToggle = true)
                    }
                )
            }
        }
    ) { innerPadding ->
        when (viewState) {
            is ViewState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ViewState.Success -> {
                val allFiles = (viewState as ViewState.Success).files  // guarded by `is` above
                val categoryFiles = allFiles.filter { file ->
                    val isExcluded = excludedFolders.any { file.path.startsWith(it) }
                    val shouldShow = !isExcluded || showExcluded
                    
                    shouldShow && (MediaCategory.fromKey(categoryName)?.matches(file) == true)
                }
                
                // v1.10.0 (#2): key by full parent *path* so same-named folders
                // never merge; display labels disambiguated via FolderNames.
                val groupedByParentPath = categoryFiles.groupBy {
                    File(it.path).parent ?: context.getString(R.string.unknown)
                }
                val parentLabels = remember(groupedByParentPath.keys) {
                    FolderNames.disambiguateParentLabels(groupedByParentPath.keys.toList())
                }
                val sortedParents = groupedByParentPath.keys.sorted()
                val allLabel = context.getString(R.string.all)
                val tabs = listOf(allLabel) + sortedParents.map { parentLabels[it].orEmpty() }
                // Map displayed tab label back to parent path (labels are unique
                // after disambiguation, except "All" at index 0).
                val labelToParent = sortedParents.associateBy { parentLabels[it].orEmpty() }
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                // v1.11.0 (scroll stability): tabs rebuild when MediaStore
                // rescans; clamp the settled page instead of jumping, and
                // reset to All when the category itself changes.
                LaunchedEffect(categoryName) {
                    runCatching { pagerState.scrollToPage(0) }
                }
                LaunchedEffect(tabs.size) {
                    if (pagerState.currentPage >= tabs.size) {
                        runCatching { pagerState.scrollToPage(0) }
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (tabs.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            edgePadding = 8.dp
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { Text(title) }
                                )
                            }
                        }
                    }

                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        val filesToShow = if (page == 0) {
                            categoryFiles
                        } else {
                            val parent = labelToParent[tabs[page]]
                            if (parent != null) groupedByParentPath[parent].orEmpty() else emptyList()
                        }

                        if (filesToShow.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_files_found), style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            if (categoryViewMode == ViewMode.LIST) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filesToShow, key = { it.path }, contentType = { "cat-row" }) { file ->
                                        CategoryFileRow(
                                            file = file,
                                            selectedFiles = selectedFiles,
                                            isSelectionMode = isSelectionMode,
                                            navController = navController,
                                            showSize = showSize,
                                            showModified = showModified,
                                            showCreated = showCreated,
                                            showType = showType,
                                            onOpenDocInTab = if (desktop) onOpenDocInNewTab else null,
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 100.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(filesToShow, key = { it.path }, contentType = { "cat-grid" }) { file ->
                                        CategoryFileGridItem(
                                            file = file,
                                            selectedFiles = selectedFiles,
                                            isSelectionMode = isSelectionMode,
                                            navController = navController,
                                            onOpenDocInTab = if (desktop) onOpenDocInNewTab else null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is ViewState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text((viewState as ViewState.Error).message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryFileRow(
    file: MediaFile,
    selectedFiles: MutableList<String>,
    isSelectionMode: Boolean,
    navController: NavHostController,
    // v1.10.0 (#4): Explorer Details visibility (same defaults as FilesScreen).
    showSize: Boolean = true,
    showModified: Boolean = true,
    showCreated: Boolean = false,
    showType: Boolean = true,
    // v1.12.0: desktop opens documents in a new tab (title = file name).
    onOpenDocInTab: ((uriString: String, path: String?, title: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isSelected = selectedFiles.contains(file.path)
    var menuExpanded by remember { mutableStateOf(false) }
    // v1.11.0: right-click / long-press menu (Open with) for files.
    Box {
    Column {
    ListItem(
        headlineContent = { Text(file.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        // v1.10.0 (#4): category lists show the same Size/Dates/Type details
        // as the file browser (Win11 parity), honouring column toggles.
        supportingContent = {
            val createdMs = file.dateAdded.takeIf { it > 0 } ?: file.dateModified
            val parts = FileDetails.detailParts(
                sizeText = com.example.formatSize(file.size),
                modifiedText = com.example.formatDate(file.dateModified),
                createdText = com.example.formatDate(createdMs),
                typeText = FileDetails.typeLabel(file.name, file.mimeType),
                showSize = showSize,
                showModified = showModified,
                showCreated = showCreated,
                showType = showType,
                isDirectory = file.isDirectory,
            )
            if (parts.isNotEmpty()) Text(
                FileDetails.joinParts(parts),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        leadingContent = { 
            if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")) {
                AsyncImage(
                    model = file.contentUri ?: File(file.path),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        },
        trailingContent = {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier
            .rightClickMenu(enabled = !isSelectionMode, menuKey = file.path) { menuExpanded = true }
            .combinedClickable(
            onClick = {
                if (isSelectionMode) {
                    if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                } else {
                    // v1.12.0: same routing as the file browser (media →
                    // viewer, APK → installer, documents → doc viewer, legacy
                    // Office → external). Previously every non-APK went to
                    // the image viewer route, which rendered documents blank.
                    com.example.openMediaFile(context, file, navController, onOpenDocInTab)
                }
            },
            onLongClick = {
                if (!isSelectionMode) {
                    menuExpanded = true
                }
            }
        )
    )
        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
        // v1.11.0: file context menu — Open with (system chooser) + Select.
        // Tap keeps the previous internal-viewer behavior; only the menu
        // hands the file to another app, so existing flows are unchanged.
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            OpenWithDropdownItem(file = file, onDone = { menuExpanded = false })
            SelectDropdownItem(onSelect = {
                menuExpanded = false
                if (!selectedFiles.contains(file.path)) selectedFiles.add(file.path)
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryFileGridItem(
    file: MediaFile,
    selectedFiles: MutableList<String>,
    isSelectionMode: Boolean,
    navController: NavHostController,
    // v1.12.0: desktop opens documents in a new tab (title = file name).
    onOpenDocInTab: ((uriString: String, path: String?, title: String) -> Unit)? = null,
) {
    val gridContext = LocalContext.current
    val isSelected = selectedFiles.contains(file.path)
    var gridMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .rightClickMenu(enabled = !isSelectionMode, menuKey = file.path) { gridMenuExpanded = true }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                    } else {
                        // v1.12.0: same routing as the file browser (see
                        // CategoryFileRow) — documents no longer land on a
                        // blank image-viewer page.
                        com.example.openMediaFile(gridContext, file, navController, onOpenDocInTab)
                    }
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        gridMenuExpanded = true
                    }
                }
            )
            .border(
                if (isSelected) 4.dp else 0.5.dp, 
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
    ) {
        if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")) {
            AsyncImage(
                model = file.contentUri ?: File(file.path),
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile, 
                contentDescription = null, 
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = file.name,
                // 72% scrim keeps white text at >= 4.5:1 even over a bright
                // card background (50% measured only ~3.9:1 and failed AA).
                modifier = Modifier.align(Alignment.BottomCenter).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f)).fillMaxWidth().padding(4.dp),
                color = com.example.ui.theme.ViewerOnSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(4.dp).align(Alignment.TopEnd)
            )
        }
        DropdownMenu(expanded = gridMenuExpanded, onDismissRequest = { gridMenuExpanded = false }) {
            OpenWithDropdownItem(file = file, onDone = { gridMenuExpanded = false })
            SelectDropdownItem(onSelect = {
                gridMenuExpanded = false
                if (!selectedFiles.contains(file.path)) selectedFiles.add(file.path)
            })
        }
    }
}
