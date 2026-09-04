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
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(categoryName: String, viewModel: FileViewModel, navController: NavHostController) {
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()
    val categoryViewMode by viewModel.categoryViewMode.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val showExcluded by viewModel.showExcludedInManage.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.loadAllMedia()
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
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_media)))
                            }
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = { 
                            selectedFiles.forEach { path ->
                                val mediaFile = (viewState as? ViewState.Success)?.files?.find { it.path == path }
                                if (mediaFile != null) {
                                    viewModel.deleteFile(mediaFile.path, mediaFile.contentUri)
                                }
                            }
                            selectedFiles.clear()
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
                
                val groupedFiles = categoryFiles.groupBy { File(it.path).parentFile?.name ?: context.getString(R.string.unknown) }
                val tabs = listOf(context.getString(R.string.all)) + groupedFiles.keys.toList().sorted()
                val pagerState = rememberPagerState(pageCount = { tabs.size })

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
                            groupedFiles[tabs[page]] ?: emptyList()
                        }

                        if (filesToShow.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_files_found), style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            if (categoryViewMode == ViewMode.LIST) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filesToShow) { file ->
                                        CategoryFileRow(
                                            file = file,
                                            selectedFiles = selectedFiles,
                                            isSelectionMode = isSelectionMode,
                                            navController = navController
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
                                    items(filesToShow) { file ->
                                        CategoryFileGridItem(
                                            file = file,
                                            selectedFiles = selectedFiles,
                                            isSelectionMode = isSelectionMode,
                                            navController = navController
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
    navController: NavHostController
) {
    val isSelected = selectedFiles.contains(file.path)
    Column {
    ListItem(
        headlineContent = { Text(file.name) },
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
        modifier = Modifier.combinedClickable(
            onClick = {
                if (isSelectionMode) {
                    if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                } else {
                    if (file.mimeType == "application/vnd.android.package-archive") {
                        val context = navController.context
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_apk), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        navController.navigate("viewer/${Uri.encode(file.path)}")
                    }
                }
            },
            onLongClick = {
                if (!isSelectionMode) {
                    selectedFiles.add(file.path)
                }
            }
        )
    )
        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryFileGridItem(
    file: MediaFile,
    selectedFiles: MutableList<String>,
    isSelectionMode: Boolean,
    navController: NavHostController
) {
    val isSelected = selectedFiles.contains(file.path)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                    } else {
                        if (file.mimeType == "application/vnd.android.package-archive") {
                            val context = navController.context
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_apk), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            navController.navigate("viewer/${Uri.encode(file.path)}")
                        }
                    }
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        selectedFiles.add(file.path)
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
                modifier = Modifier.align(Alignment.BottomCenter).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)).fillMaxWidth().padding(4.dp),
                color = androidx.compose.ui.graphics.Color.White,
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
    }
}
