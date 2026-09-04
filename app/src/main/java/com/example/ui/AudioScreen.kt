@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.SortViewMenu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.media3.common.MediaItem
import com.example.FileViewModel
import com.example.MediaFile
import com.example.ViewState
import com.example.playback.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioScreen(viewModel: FileViewModel, navController: NavHostController) {
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.loadAllMedia()
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

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
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "audio/*"
                                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_media)))
                            }
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = { 
                            showPlaylistDialog = true
                        }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist))
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
                        SortViewMenu(viewModel = viewModel, onSelectAll = {
                            (viewState as? ViewState.Success)?.let { s ->
                                val audioFiles = s.files.filter {
                                    it.mimeType.startsWith("audio/") && !excludedFolders.any { excluded -> it.path.startsWith(excluded) }
                                }
                                selectedFiles.clear()
                                selectedFiles.addAll(audioFiles.map { it.path })
                            }
                        })
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.audio)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        SortViewMenu(viewModel = viewModel, onSelectAll = {
                            (viewState as? ViewState.Success)?.let { s ->
                                val audioFiles = s.files.filter {
                                    it.mimeType.startsWith("audio/") && !excludedFolders.any { excluded -> it.path.startsWith(excluded) }
                                }
                                selectedFiles.clear()
                                selectedFiles.addAll(audioFiles.map { it.path })
                            }
                        })
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
                    text = { Text(stringResource(R.string.tracks)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.playlists)) }
                )
            }
            
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> TracksView(viewState, navController, selectedFiles, isSelectionMode)
                    1 -> PlaylistsView(viewState, navController, excludedFolders)
                }
            }
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text(stringResource(R.string.add_to_playlist_dialog)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        val name = newPlaylistName
                        val paths = selectedFiles.toList()
                        selectedFiles.clear()
                        showPlaylistDialog = false
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                                val playlistDir = File(musicDir, name)
                                if (!playlistDir.exists()) playlistDir.mkdirs()
                                paths.forEach { path ->
                                    val file = File(path)
                                    if (file.exists()) {
                                        runCatching { file.copyTo(File(playlistDir, file.name), overwrite = true) }
                                    }
                                }
                            }
                            viewModel.reload()
                        }
                    }
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

fun playAudioList(files: List<MediaFile>, startIndex: Int = 0, shuffle: Boolean = false, reverse: Boolean = false) {
    val p = PlaybackManager.player ?: return
    val mediaItems = files.map { MediaItem.fromUri(it.contentUri ?: Uri.fromFile(File(it.path))) }
    p.setMediaItems(if (reverse) mediaItems.reversed() else mediaItems)
    p.shuffleModeEnabled = shuffle
    val actualStartIndex = if (reverse) mediaItems.size - 1 - startIndex else startIndex
    p.seekTo(actualStartIndex, 0L)
    p.prepare()
    p.play()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TracksView(viewState: ViewState, navController: NavHostController, selectedFiles: MutableList<String>, isSelectionMode: Boolean) {
    when (viewState) {
        is ViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ViewState.Success -> {
            val audioFiles = viewState.files.filter { it.mimeType.startsWith("audio/") }

            if (audioFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.LibraryMusic,
                        title = stringResource(R.string.tracks),
                        description = stringResource(R.string.no_files_found),
                    )
                }
            } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { playAudioList(audioFiles) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_forward))
                    }
                    Button(onClick = { playAudioList(audioFiles, reverse = true) }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.play_reverse))
                    }
                    Button(onClick = { playAudioList(audioFiles, shuffle = true) }) {
                        Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle))
                    }
                }
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(audioFiles.size) { index ->
                        val file = audioFiles[index]
                        val isSelected = selectedFiles.contains(file.path)
                        ListItem(
                            headlineContent = { Text(file.name) },
                            supportingContent = { Text(File(file.path).parentFile?.name ?: stringResource(R.string.unknown)) },
                            leadingContent = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
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
                                        playAudioList(audioFiles, index)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedFiles.add(file.path)
                                    }
                                }
                            )
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
fun PlaylistsView(viewState: ViewState, navController: NavHostController, excludedFolders: Set<String>) {
    when (viewState) {
        is ViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ViewState.Success -> {
            val audioFiles = viewState.files.filter { it.mimeType.startsWith("audio/") }
            val unknown = stringResource(R.string.unknown)
            val playlists = audioFiles.groupBy { File(it.path).parentFile?.name ?: unknown }
            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        title = stringResource(R.string.playlists),
                        description = stringResource(R.string.no_files_found),
                    )
                }
            } else
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists.keys.toList(), key = { it }) { playlistName ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            navController.navigate("playlist/${Uri.encode(playlistName)}") 
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = playlistName, 
                                style = MaterialTheme.typography.titleMedium, 
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.track_count, playlists[playlistName]?.size ?: 0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
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
