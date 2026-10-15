package com.example

import androidx.compose.ui.res.stringResource
import com.example.R
import android.net.Uri
import android.content.Intent
import android.content.ClipData
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.ui.components.SortViewMenu
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.Hallmark
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.FolderPickerDialog
import java.text.DateFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import java.util.Date
import java.util.Locale
import java.io.File
import androidx.compose.foundation.ExperimentalFoundationApi

private data class FolderPickerMode(val isMove: Boolean, val paths: List<String>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    viewModel: FileViewModel,
    navController: NavHostController,
    initialPath: String? = null,
    onPinFolder: (String) -> Unit = {},
    onOpenFolderInNewTab: (String) -> Unit = {},
    // v1.10.0: desktop shell passes true so the list defaults to LIST
    // (Explorer Details parity) without clobbering explicit user choices.
    desktop: Boolean = false,
) {
    val viewState by viewModel.fileTreeState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    // v1.10.0: Explorer column visibility.
    val showSize by viewModel.listShowSize.collectAsStateWithLifecycle()
    val showModified by viewModel.listShowDateModified.collectAsStateWithLifecycle()
    val showCreated by viewModel.listShowDateCreated.collectAsStateWithLifecycle()
    val showType by viewModel.listShowType.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()
    var pendingDeletePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    val renameError by viewModel.renameError.collectAsStateWithLifecycle()

    var storageRoots by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var folderPickerMode by remember { mutableStateOf<FolderPickerMode?>(null) }

    LaunchedEffect(Unit) {
        storageRoots = withContext(kotlinx.coroutines.Dispatchers.IO) { viewModel.storageRoots() }
    }

    // v1.10.0 (#3): desktop defaults to LIST; guarded inside ViewModel so a
    // manual GRID choice is never reset on recompose.
    LaunchedEffect(desktop) {
        viewModel.applyDesktopDefault(desktop)
    }

    LaunchedEffect(initialPath) {
        initialPath?.takeIf { it.startsWith("/") }?.let(viewModel::loadFiles)
    }

    if (pendingDeletePaths.isNotEmpty()) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            message = pluralStringResource(R.plurals.items_selected, pendingDeletePaths.size, pendingDeletePaths.size),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { pendingDeletePaths = emptyList() },
            onConfirm = {
                val toDelete = pendingDeletePaths
                pendingDeletePaths = emptyList()
                val files = (viewState as? ViewState.Success)?.files.orEmpty()
                selectedFiles.clear()
                coroutineScope.launch {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        toDelete.forEach { path ->
                            val mediaFile = files.find { it.path == path }
                            if (mediaFile != null && !mediaFile.isDirectory) {
                                // MediaStore delete stays on the ViewModel (handles scoped storage).
                            } else {
                                val f = File(path)
                                runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }
                            }
                        }
                    }
                    toDelete.forEach { path ->
                        files.find { it.path == path }?.takeIf { !it.isDirectory }?.let {
                            viewModel.deleteFile(it.path, it.contentUri)
                        }
                    }
                    viewModel.reload()
                }
            },
        )
    }

    deleteError?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearDeleteError()
        }
    }

    renameError?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearRenameError()
        }
    }

    if (showRenameDialog && selectedFiles.size == 1) {
        val target = (viewState as? ViewState.Success)?.files?.find { it.path == selectedFiles.first() }
        if (target != null) {
            var newName by remember(target.path) { mutableStateOf(target.name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text(stringResource(R.string.rename)) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.new_name)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRenameDialog = false
                            viewModel.renameFile(target.path, target.contentUri, newName.trim())
                            selectedFiles.clear()
                        },
                        enabled = newName.isNotBlank(),
                    ) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }

    folderPickerMode?.let { mode ->
        val startDir = (viewState as? ViewState.Success)?.currentPath ?: storageRoots.firstOrNull() ?: "/"
        FolderPickerDialog(
            startPath = startDir,
            isMove = mode.isMove,
            onDismiss = { folderPickerMode = null },
            onConfirm = { destDir ->
                val files = (viewState as? ViewState.Success)?.files.orEmpty()
                mode.paths.forEach { path ->
                    val file = files.find { it.path == path } ?: return@forEach
                    if (mode.isMove) {
                        viewModel.moveFile(file.path, file.contentUri, destDir)
                    } else {
                        viewModel.copyFile(file.path, file.contentUri, destDir)
                    }
                }
                selectedFiles.clear()
                folderPickerMode = null
            },
        )
    }

    BackHandler(enabled = viewState is ViewState.Success && !(viewModel.isStorageRoot((viewState as ViewState.Success).currentPath))) {
        if (isSelectionMode) {
            selectedFiles.clear()
        } else {
            viewModel.navigateUp()
        }
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
                            val uris = selectedFiles.mapNotNull { path ->
                                (viewState as? ViewState.Success)?.files?.find { it.path == path }?.contentUri
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_files)))
                            }
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }

                        IconButton(onClick = {
                            pendingDeletePaths = selectedFiles.toList()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                        if (selectedFiles.size == 1) {
                            IconButton(onClick = { showRenameDialog = true }) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringResource(R.string.rename))
                            }
                        }
                        IconButton(onClick = { folderPickerMode = FolderPickerMode(isMove = false, paths = selectedFiles.toList()) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                        IconButton(onClick = { folderPickerMode = FolderPickerMode(isMove = true, paths = selectedFiles.toList()) }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = stringResource(R.string.move))
                        }
                        SortViewMenu(viewModel = viewModel, onSelectAll = {
                            if (viewState is ViewState.Success) {
                                val files = (viewState as ViewState.Success).files.filter { !it.isDirectory }
                                selectedFiles.clear()
                                selectedFiles.addAll(files.map { it.path })
                            }
                        })
                    }
                )
            } else {
                // v1.10.0 (#2): storage roots show the real volume name
                // (e.g. "ABCD-1234"), not the generic external label; other
                // folders show the accurate last segment via FolderNames.
                val internalLabel = stringResource(R.string.internal_storage)
                val externalFallback = stringResource(R.string.external_storage)
                TopAppBar(
                    title = { 
                        val title = if (viewState is ViewState.Success) {
                            val path = (viewState as ViewState.Success).currentPath
                            if (viewModel.isStorageRoot(path)) {
                                val isInternal = path.contains("emulated")
                                com.example.ui.FolderNames.storageRootTitle(path, isInternal, internalLabel, externalFallback)
                            } else {
                                com.example.ui.FolderNames.folderDisplayName(path)
                            }
                        } else stringResource(R.string.manage_files)
                        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (viewState is ViewState.Success && !viewModel.isStorageRoot((viewState as ViewState.Success).currentPath)) {
                                viewModel.navigateUp()
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = { navController.navigate("clean") }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clean_duplicates))
                        }
                        SortViewMenu(viewModel = viewModel, onSelectAll = {
                            if (viewState is ViewState.Success) {
                                val files = (viewState as ViewState.Success).files.filter { !it.isDirectory }
                                selectedFiles.clear()
                                selectedFiles.addAll(files.map { it.path })
                            }
                        })
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!isSelectionMode && viewState is ViewState.Success) {
                val currentSuccess = viewState as ViewState.Success
                val root = storageRoots.firstOrNull { currentSuccess.currentPath.startsWith(it) } ?: storageRoots.firstOrNull()
                if (root != null) {
                    val isInternal = root.contains("emulated")
                    // v1.10.0 (#2): breadcrumb root uses the real volume name.
                    val rootLabel = com.example.ui.FolderNames.storageRootTitle(
                        root, isInternal,
                        stringResource(R.string.internal_storage),
                        stringResource(R.string.external_storage),
                    )
                    BreadcrumbBar(
                        rootPath = root,
                        rootLabel = rootLabel,
                        currentPath = currentSuccess.currentPath,
                        onNavigate = { viewModel.loadFiles(it) },
                    )
                }
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.search_files)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    )
                }
            }
            when (val state = viewState) {
                is ViewState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ViewState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is ViewState.Success -> {
                    val displayedFiles = remember(state.files, searchQuery) {
                        if (searchQuery.isBlank()) state.files
                        else state.files.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                    if (viewMode == ViewMode.LIST) {
                        // v1.10.0 (#4): Win11 Details header on desktop lists.
                        if (desktop) {
                            FileListHeader(
                                viewModel = viewModel,
                                showSize = showSize,
                                showModified = showModified,
                                showCreated = showCreated,
                                showType = showType,
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = Hallmark.ContentEdge,
                                vertical = Hallmark.ItemGap,
                            ),
                        ) {
                            itemsIndexed(
                                displayedFiles,
                                key = { _, file -> file.path },
                                contentType = { _, file -> if (file.isDirectory) "dir" else "file" },
                            ) { _, file ->
                                val isSelected = selectedFiles.contains(file.path)
                                FileItemRow(
                                    file, isSelected, isSelectionMode,
                                    showSize = showSize,
                                    showModified = showModified,
                                    showCreated = showCreated,
                                    showType = showType,
                                    onToggleSelect = {
                                    if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                }, onPinFolder = onPinFolder, onOpenFolderInNewTab = onOpenFolderInNewTab) {
                                    if (isSelectionMode) {
                                        if (!file.isDirectory) {
                                            if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                        }
                                    } else {
                                        if (file.isDirectory) {
                                            viewModel.loadFiles(file.path)
                                        } else {
                                            openMediaFile(context, file, navController)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Hallmark.ContentEdge),
                            horizontalArrangement = Arrangement.spacedBy(Hallmark.ItemGap),
                            verticalArrangement = Arrangement.spacedBy(Hallmark.ItemGap)
                        ) {
                            items(
                                displayedFiles,
                                key = { it.path },
                                contentType = { if (it.isDirectory) "dir" else "file" },
                            ) { file ->
                                val isSelected = selectedFiles.contains(file.path)
                                FileItemGrid(file, isSelected, isSelectionMode, onToggleSelect = {
                                    if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                }, onPinFolder = onPinFolder, onOpenFolderInNewTab = onOpenFolderInNewTab) {
                                    if (isSelectionMode) {
                                        if (!file.isDirectory) {
                                            if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                        }
                                    } else {
                                        if (file.isDirectory) {
                                            viewModel.loadFiles(file.path)
                                        } else {
                                            openMediaFile(context, file, navController)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * v1.10.0 (#4): Windows 11 Explorer Details header for desktop lists.
 * Column taps sort (Name/Size/Type/Modified/Created); hidden columns stay
 * hidden per the column-chooser toggles in SortViewMenu.
 */
@Composable
private fun FileListHeader(
    viewModel: FileViewModel,
    showSize: Boolean,
    showModified: Boolean,
    showCreated: Boolean,
    showType: Boolean,
) {
    val currentSort by viewModel.sortOption.collectAsStateWithLifecycle()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Hallmark.ContentEdge, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell(
                label = stringResource(R.string.list_header_name),
                selected = currentSort == SortOption.NAME,
                modifier = Modifier.weight(2f),
                onClick = { viewModel.setSortOption(SortOption.NAME) },
            )
            if (showSize) {
                HeaderCell(
                    label = stringResource(R.string.list_header_size),
                    selected = currentSort == SortOption.SIZE,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setSortOption(SortOption.SIZE) },
                )
            }
            if (showModified) {
                HeaderCell(
                    label = stringResource(R.string.list_header_modified),
                    selected = currentSort == SortOption.DATE_CREATED,
                    modifier = Modifier.weight(1.4f),
                    onClick = { viewModel.setSortOption(SortOption.DATE_CREATED) },
                )
            }
            if (showCreated) {
                HeaderCell(
                    label = stringResource(R.string.list_header_created),
                    selected = false,
                    modifier = Modifier.weight(1.4f),
                    onClick = { viewModel.setSortOption(SortOption.DATE_CREATED) },
                )
            }
            if (showType) {
                HeaderCell(
                    label = stringResource(R.string.list_header_type),
                    selected = currentSort == SortOption.TYPE,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setSortOption(SortOption.TYPE) },
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            text = if (selected) "▲ $label" else label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: MediaFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onPinFolder: (String) -> Unit = {},
    onOpenFolderInNewTab: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    // v1.10.0 (#4): Explorer Details visibility (defaults preserve old behaviour
    // for previews/tests that omit them: size+modified shown, created hidden).
    showSize: Boolean = true,
    showModified: Boolean = true,
    showCreated: Boolean = false,
    showType: Boolean = true,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dragModifier = if (file.isDirectory && file.name != "..") {
        Modifier.dragAndDropSource(transferData = {
            DragAndDropTransferData(
                clipData = ClipData.newPlainText("Media Master folder", file.path),
                flags = View.DRAG_FLAG_GLOBAL
            )
        })
    } else Modifier
    // v1.11.0: desktop right-click opens the same context menu as long-press.
    val rightClickModifier = if (file.name != "..") {
        Modifier.pointerInput(file.path) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        menuExpanded = true
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    } else Modifier
    Box(modifier = dragModifier) {
    Column {
    androidx.compose.material3.ListItem(
        headlineContent = { 
            Text(
                text = file.name, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (file.isDirectory) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal
            ) 
        },
        // v1.10.0 (#4): Explorer Details — Size / Modified / Created / Type
        // with per-column show/hide. Created falls back to Modified when
        // unknown (dateAdded==0) and is hidden when identical to Modified.
        supportingContent = if (!file.isDirectory && file.name != "..") {
            {
                val createdMs = file.dateAdded.takeIf { it > 0 } ?: file.dateModified
                val parts = com.example.ui.FileDetails.detailParts(
                    sizeText = formatSize(file.size),
                    modifiedText = formatDate(file.dateModified),
                    createdText = formatDate(createdMs),
                    typeText = com.example.ui.FileDetails.typeLabel(file.name, file.mimeType),
                    showSize = showSize,
                    showModified = showModified,
                    showCreated = showCreated,
                    showType = showType,
                    isDirectory = false,
                )
                if (parts.isNotEmpty()) Text(
                    com.example.ui.FileDetails.joinParts(parts),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else null,
        leadingContent = {
            if (file.isDirectory) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    com.example.ui.components.MediaThumbnail(
                        file = file,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        trailingContent = if (isSelected) {
            { Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary) }
        } else null,
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Hallmark.RowMinHeight)
            .then(rightClickModifier)
            .semantics { selected = isSelected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // ".." is the synthetic "go up" entry, not a real file: it must never enter
                    // multi-select (rename/move/copy/delete on it would target the parent dir).
                    // v1.11.0: files also open the context menu (Open with)
                    // on long-press; Select inside the menu preserves batch ops.
                    if (file.name != "..") {
                        menuExpanded = true
                    }
                },
                role = if (isSelectionMode) Role.Checkbox else null,
            )
    )
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (file.isDirectory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_in_new_tab)) },
                    onClick = { menuExpanded = false; onOpenFolderInNewTab(file.path) },
                    leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pin_to_sidebar)) },
                    onClick = { menuExpanded = false; onPinFolder(file.path) },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_with)) },
                    onClick = { menuExpanded = false; openWithExternalApp(context, file) },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    onClick = {
                        menuExpanded = false
                        val uri = file.contentUri ?: runCatching {
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
                        }.getOrNull()
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = file.mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_files))) }
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select)) },
                    onClick = { menuExpanded = false; onToggleSelect() },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemGrid(
    file: MediaFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onPinFolder: (String) -> Unit = {},
    onOpenFolderInNewTab: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val gridContext = LocalContext.current
    val dragModifier = if (file.isDirectory && file.name != "..") {
        Modifier.dragAndDropSource(transferData = {
            DragAndDropTransferData(
                clipData = ClipData.newPlainText("Media Master folder", file.path),
                flags = View.DRAG_FLAG_GLOBAL
            )
        })
    } else Modifier
    val gridRightClick = if (file.name != "..") {
        Modifier.pointerInput(file.path) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        menuExpanded = true
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    } else Modifier
    Box(modifier = modifier.then(dragModifier).then(gridRightClick).semantics { selected = isSelected }) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .padding(4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (file.name != "..") {
                        menuExpanded = true
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (file.isDirectory) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    com.example.ui.components.MediaThumbnail(
                        file = file,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).align(Alignment.TopEnd)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (file.isDirectory) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_in_new_tab)) },
                        onClick = { menuExpanded = false; onOpenFolderInNewTab(file.path) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pin_to_sidebar)) },
                        onClick = { menuExpanded = false; onPinFolder(file.path) }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_with)) },
                        onClick = { menuExpanded = false; openWithExternalApp(gridContext, file) },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select)) },
                        onClick = { menuExpanded = false; onToggleSelect() },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                    )
                }
            }
        }
    }
    }
}

/**
 * v1.11.0: "Open with" — hand a file to the app the user picks from the
 * system chooser (right-click / long-press menu). Crash-safe: unknown MIME
 * falls back to content-resolver sniffing, then a wildcard type; a missing
 * handler shows a toast instead of throwing. Directories resolve via FileProvider only
 * when they back a real path; MediaStore content URIs are preferred.
 */
fun openWithExternalApp(context: android.content.Context, file: MediaFile) {
    if (file.name == "..") return
    val uri = file.contentUri ?: runCatching {
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", File(file.path),
        )
    }.getOrNull()
    if (uri == null) {
        android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_file), android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    var mime = file.mimeType.takeUnless { it.isBlank() || it == "application/octet-stream" }
    if (mime == null) {
        mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    }
    val type = mime.takeUnless { it.isNullOrBlank() } ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, type)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with_chooser)))
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, context.getString(R.string.no_app_to_open), android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_file), android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun openMediaFile(context: android.content.Context, file: MediaFile, navController: NavHostController) {    if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/")) {
        navController.navigate("viewer/${Uri.encode(file.path)}")
    } else if (file.mimeType == "application/vnd.android.package-archive" || file.path.endsWith(".apk", ignoreCase = true)) {
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
        }.getOrNull()
        if (uri == null) {
            android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_apk), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_apk), android.widget.Toast.LENGTH_SHORT).show()
        }
    } else if (com.example.viewer.ViewerKindClassifier.classify(file.name, file.mimeType) == com.example.viewer.ViewerKind.EXTERNAL_ONLY) {
        // Legacy binary Office formats (.doc/.ppt): no safe in-app renderer
        // (see ViewerKindClassifier), so keep the original external hand-off.
        val uri = file.contentUri ?: runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
        }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType.takeUnless { it.isBlank() || it == "application/octet-stream" } ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, file.name))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, context.getString(R.string.invalid_file_path), android.widget.Toast.LENGTH_SHORT).show()
        }
    } else {
        // Everything else opens in Media Master's own universal document viewer
        // (text/CSV/JSON/Markdown/PDF/Office/hex fallback) instead of always
        // handing off to another app.
        val uri = file.contentUri ?: runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
        }.getOrNull()
        if (uri == null) {
            android.widget.Toast.makeText(context, context.getString(R.string.invalid_file_path), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            navController.navigate("docViewer/${Uri.encode(uri.toString())}?path=${Uri.encode(file.path)}") { launchSingleTop = true }
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, context.getString(R.string.invalid_file_path), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

fun formatSize(sizeBytes: Long): String {
    // Non-composable fallback: locale-aware grouping, binary units.
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun formatSizeLocalized(sizeBytes: Long): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(sizeBytes) {
        android.text.format.Formatter.formatFileSize(context, sizeBytes.coerceAtLeast(0L))
    }
}

fun formatDate(dateMs: Long): String {
    // Locale-aware medium date (respects the user's language / region format).
    return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(dateMs))
}
