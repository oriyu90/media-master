package com.example

import androidx.compose.ui.res.stringResource
import com.example.R
import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
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
import com.example.ui.components.EmptyState
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.FileDragDrop
import com.example.ui.components.dropTarget
import com.example.ui.components.dragSource
import com.example.ui.components.PathMoveDialog
import com.example.ui.components.copyPathToClipboard
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

/**
 * v2.1.0: one pending Explorer transfer (move/copy), shared by the toolbar
 * menu, the context menu, the path dialog and drag & drop so every entry
 * point funnels into the same confirmation + execution path.
 */
private data class FolderPickerMode(val isMove: Boolean, val paths: List<String>)

private data class TransferRequest(
    val isMove: Boolean,
    val paths: List<String>,
    /** Explicit destination (drag & drop); null = ask with the folder picker. */
    val destination: String? = null,
    /** Open the typed-path dialog instead of the folder picker. */
    val byPath: Boolean = false,
)

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
    // v1.12.0: desktop opens documents in a new tab (title = file name).
    // Phone default (no-op lambda) keeps direct navigation.
    onOpenDocInNewTab: (uriString: String, path: String?, title: String) -> Unit = { _, _, _ -> },
    // v1.13.0: desktop opens media in a new tab (title = file name); phone
    // keeps direct viewer navigation (default = old behavior).
    onOpenViewerInNewTab: (path: String, title: String) -> Unit = { _, _ -> },
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
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var folderPickerMode by remember { mutableStateOf<FolderPickerMode?>(null) }
    var transfer by remember { mutableStateOf<TransferRequest?>(null) }
    // v2.1.0: multi-select mode survives an empty selection so "Select all"
    // then deselecting everything does not drop the user out of the toolbar.
    var selectionMode by remember { mutableStateOf(false) }

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

    val currentFiles = (viewState as? ViewState.Success)?.files.orEmpty()
    val currentPath = (viewState as? ViewState.Success)?.currentPath
    val primaryRoot = storageRoots.firstOrNull()
    val itemsOf: (List<String>) -> List<Pair<String, Uri?>> = { paths ->
        paths.map { path -> path to currentFiles.find { it.path == path }?.contentUri }
    }

    /** Runs a move/copy batch and reports the outcome (localized). */
    fun runTransfer(request: TransferRequest, destination: String) {
        val items = itemsOf(request.paths)
        val label = context.getString(if (request.isMove) R.string.move else R.string.copy)
        if (request.isMove) {
            viewModel.moveFiles(items, destination) { ok, failed ->
                reportTransfer(context, label, ok, failed)
            }
        } else {
            viewModel.copyFiles(items, destination) { ok, failed ->
                reportTransfer(context, label, ok, failed)
            }
        }
        selectedFiles.clear()
        selectionMode = false
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
                val files = currentFiles
                selectedFiles.clear()
                selectionMode = false
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

    // v2.1.0: rename is reachable from the toolbar, the context menu and a
    // mouse double-click; all three open this one dialog.
    renameTarget?.let { path ->
        val target = currentFiles.find { it.path == path }
        if (target != null) {
            var newName by remember(target.path) { mutableStateOf(target.name) }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.rename)) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.new_name)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            renameTarget = null
                            viewModel.renameFile(target.path, target.contentUri, newName.trim())
                            selectedFiles.clear()
                            selectionMode = false
                        },
                        enabled = newName.isNotBlank() && newName.trim() != target.name,
                    ) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        } else {
            renameTarget = null
        }
    }

    folderPickerMode?.let { mode ->
        val startDir = currentPath ?: primaryRoot ?: "/"
        FolderPickerDialog(
            startPath = startDir,
            isMove = mode.isMove,
            onDismiss = { folderPickerMode = null },
            onConfirm = { destDir ->
                folderPickerMode = null
                runTransfer(TransferRequest(mode.isMove, mode.paths), destDir)
            },
        )
    }

    // v2.1.0: typed-path move/copy (Explorer "move to path" parity).
    transfer?.takeIf { it.byPath && it.destination == null }?.let { request ->
        PathMoveDialog(
            isMove = request.isMove,
            sourcePaths = request.paths,
            primaryRoot = primaryRoot,
            storageRoots = storageRoots,
            initialPath = currentPath ?: primaryRoot ?: "/",
            onDismiss = { transfer = null },
            onConfirm = { destDir ->
                transfer = null
                runTransfer(request, destDir)
            },
        )
    }

    // A drop with an explicit destination runs immediately (no picker).
    LaunchedEffect(transfer) {
        val request = transfer ?: return@LaunchedEffect
        val destination = request.destination ?: return@LaunchedEffect
        transfer = null
        runTransfer(request, destination)
    }

    BackHandler(enabled = viewState is ViewState.Success && !(viewModel.isStorageRoot((viewState as ViewState.Success).currentPath))) {
        if (isSelectionMode || selectionMode) {
            selectedFiles.clear()
            selectionMode = false
        } else {
            viewModel.navigateUp()
        }
    }

    val showSelectionBar = isSelectionMode || selectionMode

    Scaffold(
        topBar = {
            if (showSelectionBar) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.items_selected, selectedFiles.size, selectedFiles.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles.clear(); selectionMode = false }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val uris = selectedFiles.mapNotNull { path ->
                                currentFiles.find { it.path == path }?.contentUri
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_files)))
                                }
                            }
                            selectedFiles.clear()
                            selectionMode = false
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        // v2.1.0: the toolbar keeps only the four most used
                        // actions; rename/move/copy/path live in the overflow
                        // menu so a phone never runs out of space.
                        if (selectedFiles.size == 1) {
                            IconButton(onClick = { renameTarget = selectedFiles.first() }) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringResource(R.string.rename))
                            }
                        }
                        IconButton(onClick = { pendingDeletePaths = selectedFiles.toList() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                        var moreExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { moreExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move)) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = {
                                        moreExpanded = false
                                        transfer = TransferRequest(isMove = true, paths = selectedFiles.toList())
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    onClick = {
                                        moreExpanded = false
                                        transfer = TransferRequest(isMove = false, paths = selectedFiles.toList())
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_to_path)) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = {
                                        moreExpanded = false
                                        transfer = TransferRequest(isMove = true, paths = selectedFiles.toList(), byPath = true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_path)) },
                                    leadingIcon = { Icon(Icons.Default.Link, null) },
                                    onClick = {
                                        moreExpanded = false
                                        copyPathToClipboard(context, selectedFiles.joinToString("\n"))
                                        selectedFiles.clear()
                                        selectionMode = false
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_all)) },
                                    leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                    onClick = {
                                        moreExpanded = false
                                        selectedFiles.clear()
                                        selectedFiles.addAll(currentFiles.filter { it.name != ".." }.map { it.path })
                                    },
                                )
                            }
                        }
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
                                selectionMode = true
                            }
                        })
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!showSelectionBar && viewState is ViewState.Success) {
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
                        // v2.1.0: drop files on a crumb to move them there.
                        onDropPaths = { destination, paths ->
                            transfer = TransferRequest(isMove = true, paths = paths, destination = destination)
                            true
                        },
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
                    // v1.13.0: Explorer media opens in its own desktop tab;
                    // touch keeps direct navigation. Either way the viewer
                    // receives this folder's exact displayed order (including
                    // the active search filter and the current sort).
                    val openFile: (MediaFile) -> Unit = { file ->
                        val isMedia = file.mimeType.startsWith("image/") ||
                            file.mimeType.startsWith("video/") ||
                            file.mimeType.startsWith("audio/")
                        if (isMedia) {
                            viewModel.prepareViewer(displayedFiles, file.path, startFullscreen = !desktop)
                        }
                        if (desktop && isMedia) {
                            onOpenViewerInNewTab(file.path, file.name)
                        } else {
                            openMediaFile(
                                context, file, navController,
                                onOpenDocInTab = if (desktop) onOpenDocInNewTab else null,
                            )
                        }
                    }
                    val onItemClick: (MediaFile) -> Unit = { file ->
                        if (FileDragDrop.dragInProgress) {
                            // A drag just ended over this row: never treat the
                            // release as "open".
                        } else if (showSelectionBar) {
                            if (file.name != "..") {
                                if (selectedFiles.contains(file.path)) selectedFiles.remove(file.path)
                                else selectedFiles.add(file.path)
                            }
                        } else if (file.isDirectory) {
                            viewModel.loadFiles(file.path)
                        } else {
                            openFile(file)
                        }
                    }
                    // v2.1.0: drag payload = the whole selection when the
                    // dragged row is part of it, otherwise just that row.
                    val dragPathsOf: (MediaFile) -> List<String> = { file ->
                        if (selectedFiles.contains(file.path)) selectedFiles.toList() else listOf(file.path)
                    }
                    val dropOnFolder: (MediaFile, List<String>) -> Boolean = { folder, paths ->
                        val destination = folder.path
                        val moving = paths.filter { File(it).parentFile?.absolutePath != destination }
                        if (moving.isEmpty()) {
                            false
                        } else {
                            transfer = TransferRequest(isMove = true, paths = moving, destination = destination)
                            true
                        }
                    }
                    if (displayedFiles.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.FolderOpen,
                            title = stringResource(if (searchQuery.isBlank()) R.string.folder_is_empty else R.string.no_files_found),
                            description = if (searchQuery.isBlank()) null else stringResource(R.string.search_files),
                        )
                    } else if (viewMode == ViewMode.LIST) {
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
                                    file = file,
                                    isSelected = isSelected,
                                    isSelectionMode = showSelectionBar,
                                    onToggleSelect = {
                                        if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                    },
                                    onPinFolder = onPinFolder,
                                    onOpenFolderInNewTab = onOpenFolderInNewTab,
                                    showSize = showSize,
                                    showModified = showModified,
                                    showCreated = showCreated,
                                    showType = showType,
                                    dragPaths = { dragPathsOf(file) },
                                    onDropPaths = { paths -> dropOnFolder(file, paths) },
                                    onRename = { renameTarget = file.path },
                                    onCopyPath = { copyPathToClipboard(context, file.path) },
                                    onMoveToPath = {
                                        transfer = TransferRequest(isMove = true, paths = listOf(file.path), byPath = true)
                                    },
                                    onMove = { transfer = TransferRequest(isMove = true, paths = listOf(file.path)) },
                                    onCopy = { transfer = TransferRequest(isMove = false, paths = listOf(file.path)) },
                                    onDelete = { pendingDeletePaths = listOf(file.path) },
                                    onClick = { onItemClick(file) },
                                )
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
                                FileItemGrid(
                                    file = file,
                                    isSelected = isSelected,
                                    isSelectionMode = showSelectionBar,
                                    onToggleSelect = {
                                        if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                    },
                                    onPinFolder = onPinFolder,
                                    onOpenFolderInNewTab = onOpenFolderInNewTab,
                                    dragPaths = { dragPathsOf(file) },
                                    onDropPaths = { paths -> dropOnFolder(file, paths) },
                                    onRename = { renameTarget = file.path },
                                    onCopyPath = { copyPathToClipboard(context, file.path) },
                                    onMoveToPath = {
                                        transfer = TransferRequest(isMove = true, paths = listOf(file.path), byPath = true)
                                    },
                                    onMove = { transfer = TransferRequest(isMove = true, paths = listOf(file.path)) },
                                    onCopy = { transfer = TransferRequest(isMove = false, paths = listOf(file.path)) },
                                    onDelete = { pendingDeletePaths = listOf(file.path) },
                                    onClick = { onItemClick(file) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** v2.1.0: localized toast for a finished move/copy batch. */
private fun reportTransfer(context: android.content.Context, label: String, ok: Int, failed: Int) {
    if (ok == 0 && failed == 0) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.path_error_same_as_source),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val message = when {
        failed == 0 -> context.getString(R.string.transfer_done, label, ok)
        ok == 0 -> context.getString(R.string.transfer_failed, label, failed)
        else -> context.getString(R.string.transfer_partial, label, ok, failed)
    }
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

/**
 * v2.1.0: shared column weights so the desktop header and the list rows can
 * never drift apart (the header used to sort "Modified" by DATE_CREATED and
 * the divider was pinned at 72.dp regardless of the thumbnail size).
 */
private object FileColumns {
    const val NAME = 2f
    const val SIZE = 1f
    const val MODIFIED = 1.4f
    const val CREATED = 1.4f
    const val TYPE = 1f
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
                modifier = Modifier.weight(FileColumns.NAME),
                onClick = { viewModel.setSortOption(SortOption.NAME) },
            )
            if (showSize) {
                HeaderCell(
                    label = stringResource(R.string.list_header_size),
                    selected = currentSort == SortOption.SIZE,
                    modifier = Modifier.weight(FileColumns.SIZE),
                    onClick = { viewModel.setSortOption(SortOption.SIZE) },
                )
            }
            if (showModified) {
                HeaderCell(
                    label = stringResource(R.string.list_header_modified),
                    selected = currentSort == SortOption.DATE_CREATED,
                    modifier = Modifier.weight(FileColumns.MODIFIED),
                    onClick = { viewModel.setSortOption(SortOption.DATE_CREATED) },
                )
            }
            if (showCreated) {
                HeaderCell(
                    label = stringResource(R.string.list_header_created),
                    selected = false,
                    modifier = Modifier.weight(FileColumns.CREATED),
                    onClick = { viewModel.setSortOption(SortOption.DATE_CREATED) },
                )
            }
            if (showType) {
                HeaderCell(
                    label = stringResource(R.string.list_header_type),
                    selected = currentSort == SortOption.TYPE,
                    modifier = Modifier.weight(FileColumns.TYPE),
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

/**
 * v2.1.0: mouse double-click detection. Compose's [combinedClickable] has no
 * double-click, so the row watches raw presses: a second primary-button press
 * on the same row within [DOUBLE_CLICK_MS] triggers [onDoubleClick]. Touch is
 * ignored (long-press opens the menu there, as before).
 */
private const val DOUBLE_CLICK_MS = 400L

@Composable
private fun Modifier.mouseDoubleClick(
    key: Any?,
    enabled: Boolean,
    onDoubleClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    var lastClickAt by remember(key) { mutableLongStateOf(0L) }
    return this.pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.type != PointerType.Mouse || event.buttons.isSecondaryPressed) continue
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastClickAt <= DOUBLE_CLICK_MS) {
                    lastClickAt = 0L
                    onDoubleClick()
                } else {
                    lastClickAt = now
                }
            }
        }
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
    // v2.1.0 Explorer additions (all default to no-ops so previews/tests and
    // any other call site keep compiling unchanged).
    dragPaths: () -> List<String> = { listOf(file.path) },
    onDropPaths: (List<String>) -> Boolean = { false },
    onRename: () -> Unit = {},
    onCopyPath: () -> Unit = {},
    onMoveToPath: () -> Unit = {},
    onMove: () -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit = {},
    /** v2.1.0: true while another dragged row hovers this folder. */
    isDropHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var dropHover by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isReal = file.name != ".."
    val highlighted = isDropHighlighted || dropHover
    // v2.1.0: files drag too (multi-select aware); ".." never drags.
    val dragModifier = Modifier.dragSource(enabled = isReal, key = file.path, paths = dragPaths)
    // v1.11.0: desktop right-click opens the same context menu as long-press.
    val rightClickModifier = if (isReal) {
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
    // v2.1.0: folders accept drops (move into); files do not.
    val dropModifier = Modifier.dropTarget(
        enabled = file.isDirectory && isReal,
        onHover = { dropHover = it },
        onDrop = onDropPaths,
    )
    Box(modifier = dragModifier.then(dropModifier)) {
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
                supportingContent = if (!file.isDirectory && isReal) {
                    {
                        val createdMs = file.dateAdded.takeIf { it > 0 } ?: file.dateModified
                        val parts = com.example.ui.FileDetails.detailParts(
                            sizeText = formatSizeLocalized(file.size),
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
                                .background(
                                    if (highlighted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primaryContainer,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (highlighted) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onPrimaryContainer,
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
                    containerColor = when {
                        highlighted -> MaterialTheme.colorScheme.primaryContainer
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = Hallmark.RowMinHeight)
                    .then(rightClickModifier)
                    .mouseDoubleClick(key = file.path, enabled = isReal && !isSelectionMode, onDoubleClick = onRename)
                    .semantics { selected = isSelected }
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            // ".." is the synthetic "go up" entry, not a real file: it must never enter
                            // multi-select (rename/move/copy/delete on it would target the parent dir).
                            // v1.11.0: files also open the context menu (Open with)
                            // on long-press; Select inside the menu preserves batch ops.
                            if (isReal) {
                                menuExpanded = true
                            }
                        },
                        role = if (isSelectionMode) Role.Checkbox else null,
                    )
            )
            // v2.1.0: divider starts where the content starts (after the
            // 48dp thumbnail + ListItem's 16dp padding) instead of a magic 72dp.
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        ExplorerContextMenu(
            expanded = menuExpanded,
            file = file,
            onDismiss = { menuExpanded = false },
            onOpenFolderInNewTab = onOpenFolderInNewTab,
            onPinFolder = onPinFolder,
            onToggleSelect = onToggleSelect,
            onRename = onRename,
            onCopyPath = onCopyPath,
            onMoveToPath = onMoveToPath,
            onMove = onMove,
            onCopy = onCopy,
            onDelete = onDelete,
            context = context,
        )
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
    // v2.1.0 Explorer additions (defaults keep existing call sites valid).
    dragPaths: () -> List<String> = { listOf(file.path) },
    onDropPaths: (List<String>) -> Boolean = { false },
    onRename: () -> Unit = {},
    onCopyPath: () -> Unit = {},
    onMoveToPath: () -> Unit = {},
    onMove: () -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var dropHover by remember { mutableStateOf(false) }
    val gridContext = LocalContext.current
    val isReal = file.name != ".."
    val dragModifier = Modifier.dragSource(enabled = isReal, key = file.path, paths = dragPaths)
    val dropModifier = Modifier.dropTarget(
        enabled = file.isDirectory && isReal,
        onHover = { dropHover = it },
        onDrop = onDropPaths,
    )
    val gridRightClick = if (isReal) {
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
    Box(
        modifier = modifier
            .then(dragModifier)
            .then(dropModifier)
            .then(gridRightClick)
            .mouseDoubleClick(key = file.path, enabled = isReal && !isSelectionMode, onDoubleClick = onRename)
            .semantics { selected = isSelected }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 116.dp)
                .padding(4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (isReal) {
                            menuExpanded = true
                        }
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    dropHover -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
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
                            tint = if (dropHover) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
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
            }
        }
        ExplorerContextMenu(
            expanded = menuExpanded,
            file = file,
            onDismiss = { menuExpanded = false },
            onOpenFolderInNewTab = onOpenFolderInNewTab,
            onPinFolder = onPinFolder,
            onToggleSelect = onToggleSelect,
            onRename = onRename,
            onCopyPath = onCopyPath,
            onMoveToPath = onMoveToPath,
            onMove = onMove,
            onCopy = onCopy,
            onDelete = onDelete,
            context = gridContext,
        )
    }
}

/**
 * v2.1.0: one context menu for both layouts (the grid menu used to be a
 * stripped-down copy that lacked Share/Delete). Folders get navigation +
 * pin + the file operations that make sense (rename/move/copy/path/delete);
 * files add "Open with" and Share.
 */
@Composable
private fun ExplorerContextMenu(
    expanded: Boolean,
    file: MediaFile,
    onDismiss: () -> Unit,
    onOpenFolderInNewTab: (String) -> Unit,
    onPinFolder: (String) -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onCopyPath: () -> Unit,
    onMoveToPath: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    context: android.content.Context,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (file.isDirectory) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_in_new_tab)) },
                onClick = { onDismiss(); onOpenFolderInNewTab(file.path) },
                leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pin_to_sidebar)) },
                onClick = { onDismiss(); onPinFolder(file.path) },
                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_with)) },
                onClick = { onDismiss(); openWithExternalApp(context, file) },
                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = {
                    onDismiss()
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
                onClick = { onDismiss(); onToggleSelect() },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename)) },
            onClick = { onDismiss(); onRename() },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_path)) },
            onClick = { onDismiss(); onCopyPath() },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.move_to_path)) },
            onClick = { onDismiss(); onMoveToPath() },
            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.move)) },
            onClick = { onDismiss(); onMove() },
            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy)) },
            onClick = { onDismiss(); onCopy() },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        )
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

fun openMediaFile(
    context: android.content.Context,
    file: MediaFile,
    navController: NavHostController,
    // v1.12.0: desktop tab opener for documents. When provided, document
    // kinds open in a new desktop tab instead of direct navigation
    // (media/APK/external flows are untouched).
    onOpenDocInTab: ((uriString: String, path: String?, title: String) -> Unit)? = null,
) {
    if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/")) {
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
            val openInTab = onOpenDocInTab
            if (openInTab != null) {
                openInTab(uri.toString(), file.path, file.name)
            } else {
                navController.navigate("docViewer/${Uri.encode(uri.toString())}?path=${Uri.encode(file.path)}") { launchSingleTop = true }
            }
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
