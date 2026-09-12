package com.example.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.FileViewModel
import com.example.MediaFile
import com.example.R
import com.example.SortOption
import com.example.ViewState
import com.example.formatDate
import com.example.sortMediaFiles
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

private enum class LibraryDensityMode { BY_DATE, COMPACT_ALL }

private data class LibraryFolderPickerMode(val isMove: Boolean, val paths: List<String>)

/** Truncates a timestamp to a day key so same-day items group together, e.g. Google Photos. */
private fun dayKey(dateMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dateMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(viewModel: FileViewModel, navController: NavHostController) {
    val context = LocalContext.current
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()

    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()
    var pendingDeleteCount by remember { mutableStateOf(0) }
    var folderPickerMode by remember { mutableStateOf<LibraryFolderPickerMode?>(null) }
    var densityMode by remember { mutableStateOf(LibraryDensityMode.BY_DATE) }
    var compactSortOption by remember { mutableStateOf(SortOption.DATE_CREATED) }

    LaunchedEffect(Unit) {
        viewModel.loadAllMedia()
    }

    // Oldest first so the most recent photo/video ends up last — the initial
    // scroll-to-bottom (below) then lands on the newest item, matching a
    // standard gallery app.
    val photosAndVideos = remember(viewState, excludedFolders) {
        (viewState as? ViewState.Success)?.files
            ?.filter {
                (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")) &&
                    !excludedFolders.any { excluded -> it.path.startsWith(excluded) }
            }
            ?.sortedBy { it.dateModified }
            ?: emptyList()
    }

    if (pendingDeleteCount > 0) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            message = pluralStringResource(R.plurals.items_selected, pendingDeleteCount, pendingDeleteCount),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { pendingDeleteCount = 0 },
            onConfirm = {
                val toDelete = selectedFiles.toList()
                pendingDeleteCount = 0
                selectedFiles.clear()
                toDelete.forEach { path ->
                    photosAndVideos.find { it.path == path }?.let {
                        viewModel.deleteFile(it.path, it.contentUri)
                    }
                }
            },
        )
    }

    folderPickerMode?.let { mode ->
        val startDir = mode.paths.firstOrNull()
            ?.let { path -> photosAndVideos.find { it.path == path } }
            ?.let { File(it.path).parent }
            ?: android.os.Environment.getExternalStorageDirectory().path
        com.example.ui.components.FolderPickerDialog(
            startPath = startDir,
            isMove = mode.isMove,
            onDismiss = { folderPickerMode = null },
            onConfirm = { destDir ->
                mode.paths.forEach { path ->
                    val file = photosAndVideos.find { it.path == path } ?: return@forEach
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

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                var albumMenuExpanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.items_selected, selectedFiles.size, selectedFiles.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selectedFiles.size == photosAndVideos.size) {
                                selectedFiles.clear()
                            } else {
                                selectedFiles.clear()
                                selectedFiles.addAll(photosAndVideos.map { it.path })
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                        }
                        IconButton(onClick = {
                            val uris = selectedFiles.mapNotNull { path ->
                                photosAndVideos.find { it.path == path }?.contentUri
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
                        Box {
                            IconButton(onClick = { albumMenuExpanded = true }) {
                                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.options))
                            }
                            DropdownMenu(expanded = albumMenuExpanded, onDismissRequest = { albumMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                    onClick = {
                                        albumMenuExpanded = false
                                        folderPickerMode = LibraryFolderPickerMode(isMove = true, paths = selectedFiles.toList())
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        albumMenuExpanded = false
                                        folderPickerMode = LibraryFolderPickerMode(isMove = false, paths = selectedFiles.toList())
                                    },
                                )
                            }
                        }
                        IconButton(onClick = { pendingDeleteCount = selectedFiles.size }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.library)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (pagerState.currentPage == 0 && densityMode == LibraryDensityMode.COMPACT_ALL) {
                            LibrarySortMenu(current = compactSortOption, onSelect = { compactSortOption = it })
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.photos)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.albums)) }
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> PhotosView(
                        viewState = viewState,
                        navController = navController,
                        selectedFiles = selectedFiles,
                        isSelectionMode = isSelectionMode,
                        photosAndVideos = photosAndVideos,
                        densityMode = densityMode,
                        onDensityModeChange = { densityMode = it },
                        compactSortOption = compactSortOption,
                    )
                    1 -> AlbumsView(viewState, navController, excludedFolders)
                }
            }
        }
    }
}

@Composable
private fun LibrarySortMenu(current: SortOption, onSelect: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val options = listOf(
                SortOption.NAME to R.string.sort_by_name,
                SortOption.DATE_CREATED to R.string.sort_by_date,
                SortOption.SIZE to R.string.sort_by_size,
                SortOption.TYPE to R.string.sort_by_type,
            )
            options.forEach { (option, labelRes) ->
                val label = stringResource(labelRes)
                DropdownMenuItem(
                    text = { Text(if (option == current) "$label (${stringResource(R.string.current)})" else label) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun PhotosView(
    viewState: ViewState,
    navController: NavHostController,
    selectedFiles: MutableList<String>,
    isSelectionMode: Boolean,
    photosAndVideos: List<MediaFile>,
    densityMode: LibraryDensityMode,
    onDensityModeChange: (LibraryDensityMode) -> Unit,
    compactSortOption: SortOption,
) {
    when (viewState) {
        is ViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ViewState.Success -> {
            if (photosAndVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.PhotoLibrary,
                        title = stringResource(R.string.photos),
                        description = stringResource(R.string.no_files_found),
                    )
                }
                return
            }

            // Pinch-to-change-density: only reacts once a second pointer joins so a
            // plain one-finger drag is left untouched for the grid's own scrolling.
            val pinchModifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    var zoomAccum = 1f
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            zoomAccum *= event.calculateZoom()
                            if (zoomAccum < 0.7f) {
                                onDensityModeChange(LibraryDensityMode.COMPACT_ALL)
                                zoomAccum = 1f
                            } else if (zoomAccum > 1.4f) {
                                onDensityModeChange(LibraryDensityMode.BY_DATE)
                                zoomAccum = 1f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }

            if (densityMode == LibraryDensityMode.BY_DATE) {
                val grouped = remember(photosAndVideos) {
                    photosAndVideos.groupBy { dayKey(it.dateModified) }
                }
                val gridState = rememberLazyGridState()
                var didInitialScroll by remember { mutableStateOf(false) }
                LaunchedEffect(photosAndVideos.isNotEmpty()) {
                    if (!didInitialScroll && photosAndVideos.isNotEmpty()) {
                        val totalItems = grouped.entries.sumOf { 1 + it.value.size }
                        if (totalItems > 0) gridState.scrollToItem(totalItems - 1)
                        didInitialScroll = true
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().then(pinchModifier),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    grouped.forEach { (day, files) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = formatDate(day),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        items(files, key = { it.path }) { file ->
                            val isSelected = selectedFiles.contains(file.path)
                            MediaGridItem(
                                file = file,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                    } else {
                                        navController.navigate("viewer/${Uri.encode(file.path)}")
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) selectedFiles.add(file.path)
                                }
                            )
                        }
                    }
                }
            } else {
                val sorted = remember(photosAndVideos, compactSortOption) {
                    sortMediaFiles(photosAndVideos, compactSortOption)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    modifier = Modifier.fillMaxSize().then(pinchModifier),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(sorted, key = { it.path }) { file ->
                        val isSelected = selectedFiles.contains(file.path)
                        MediaGridItem(
                            file = file,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                } else {
                                    navController.navigate("viewer/${Uri.encode(file.path)}")
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) selectedFiles.add(file.path)
                            }
                        )
                    }
                }
            }
        }
        is ViewState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = viewState.message)
            }
        }
    }
}

@Composable
fun AlbumsView(viewState: ViewState, navController: NavHostController, excludedFolders: Set<String>) {
    when (viewState) {
        is ViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ViewState.Success -> {
            val photosAndVideos = viewState.files.filter {
                (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")) &&
                !excludedFolders.any { excluded -> it.path.startsWith(excluded) }
            }
            val unknown = stringResource(R.string.unknown)
            val albums = photosAndVideos.groupBy { File(it.path).parentFile?.name ?: unknown }

            if (albums.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.PhotoLibrary,
                        title = stringResource(R.string.albums),
                        description = stringResource(R.string.no_files_found),
                    )
                }
            } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(albums.keys.toList(), key = { it }) { albumName ->
                    val files = albums[albumName] ?: emptyList()
                    val firstFile = files.firstOrNull()

                    Card(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable {
                            navController.navigate("album/${Uri.encode(albumName)}")
                        },
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (firstFile != null) {
                                AsyncImage(
                                    model = firstFile.contentUri ?: File(firstFile.path),
                                    contentDescription = albumName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            0f to androidx.compose.ui.graphics.Color.Transparent,
                                            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f),
                                        )
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = albumName,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
        is ViewState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = viewState.message)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(file: MediaFile, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .semantics { selected = isSelected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                if (isSelected) 3.dp else 0.5.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
    ) {
        AsyncImage(
            model = file.contentUri ?: File(file.path),
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
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
