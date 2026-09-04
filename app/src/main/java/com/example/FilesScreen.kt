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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    viewModel: FileViewModel,
    navController: NavHostController,
    initialPath: String? = null,
    onPinFolder: (String) -> Unit = {},
    onOpenFolderInNewTab: (String) -> Unit = {}
) {
    val viewState by viewModel.fileTreeState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(initialPath) {
        initialPath?.takeIf { it.startsWith("/") }?.let(viewModel::loadFiles)
    }

    BackHandler(enabled = viewState is ViewState.Success && (viewState as ViewState.Success).currentPath != "/storage/emulated/0") {
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
                    title = { Text("${selectedFiles.size} ${stringResource(R.string.selected)}") },
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
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_files)))
                            }
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }

                        IconButton(onClick = { 
                            selectedFiles.forEach { path ->
                                val mediaFile = (viewState as? ViewState.Success)?.files?.find { it.path == path }
                                if (mediaFile != null && !mediaFile.isDirectory) {
                                    viewModel.deleteFile(mediaFile.path, mediaFile.contentUri)
                                } else {
                                    // Handle directory deletion or show warning
                                    val f = File(path)
                                    if(f.isDirectory) {
                                        f.deleteRecursively()
                                    } else {
                                        f.delete()
                                    }
                                }
                            }
                            viewModel.reload()
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
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
                TopAppBar(
                    title = { 
                        val title = if (viewState is ViewState.Success) {
                            (viewState as ViewState.Success).currentPath.substringAfterLast("/")
                        } else stringResource(R.string.manage_files)
                        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            if (viewState is ViewState.Success && (viewState as ViewState.Success).currentPath != "/storage/emulated/0") {
                                viewModel.navigateUp()
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
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
                    if (viewMode == ViewMode.LIST) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(state.files) { index, file ->
                                val isSelected = selectedFiles.contains(file.path)
                                Column {
                                    FileItemRow(file, isSelected, isSelectionMode, onToggleSelect = {
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
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.files) { file ->
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: MediaFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onPinFolder: (String) -> Unit = {},
    onOpenFolderInNewTab: (String) -> Unit = {},
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dragModifier = if (file.isDirectory && file.name != "..") {
        Modifier.dragAndDropSource(transferData = {
            DragAndDropTransferData(
                clipData = ClipData.newPlainText("Media Master folder", file.path),
                flags = View.DRAG_FLAG_GLOBAL
            )
        })
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
        supportingContent = if (!file.isDirectory && file.name != "..") {
            { Text("${formatSize(file.size)} • ${formatDate(file.dateModified)}") }
        } else null,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (file.isDirectory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, 
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = if (isSelected) {
            { Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary) }
        } else null,
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(file.path) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (file.isDirectory && event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            menuExpanded = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (file.isDirectory && file.name != "..") menuExpanded = true else onToggleSelect() }
            )
    )
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dragModifier = if (file.isDirectory && file.name != "..") {
        Modifier.dragAndDropSource(transferData = {
            DragAndDropTransferData(
                clipData = ClipData.newPlainText("Media Master folder", file.path),
                flags = View.DRAG_FLAG_GLOBAL
            )
        })
    } else Modifier
    Box(modifier = dragModifier) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .padding(4.dp)
            .pointerInput(file.path) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (file.isDirectory && event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            menuExpanded = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (file.isDirectory && file.name != "..") menuExpanded = true else onToggleSelect() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = if (file.isDirectory) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal
                )
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
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_in_new_tab)) },
                    onClick = { menuExpanded = false; onOpenFolderInNewTab(file.path) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pin_to_sidebar)) },
                    onClick = { menuExpanded = false; onPinFolder(file.path) }
                )
            }
        }
    }
    }
}

fun openMediaFile(context: android.content.Context, file: MediaFile, navController: NavHostController) {
    if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/")) {
        navController.navigate("viewer/${Uri.encode(file.path)}")
    } else if (file.mimeType == "application/vnd.android.package-archive" || file.path.endsWith(".apk", ignoreCase = true)) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_apk), android.widget.Toast.LENGTH_SHORT).show()
        }
    } else {
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
    }
}

fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDate(dateMs: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(dateMs))
}
