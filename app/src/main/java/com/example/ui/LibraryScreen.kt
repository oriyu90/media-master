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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.FileViewModel
import com.example.MediaFile
import com.example.R
import com.example.ViewMode
import com.example.ViewState
import com.example.ui.components.SortViewMenu
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(viewModel: FileViewModel, navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    
    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.loadAllMedia()
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

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
                        SortViewMenu(viewModel = viewModel)
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
                    0 -> PhotosView(viewState, navController, selectedFiles, isSelectionMode, viewMode, excludedFolders)
                    1 -> AlbumsView(viewState, navController, excludedFolders)
                }
            }
        }
    }
}

@Composable
fun PhotosView(viewState: ViewState, navController: NavHostController, selectedFiles: MutableList<String>, isSelectionMode: Boolean, viewMode: ViewMode, excludedFolders: Set<String>) {
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
            
            if (viewMode == ViewMode.LIST) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(photosAndVideos) { file ->
                        val isSelected = selectedFiles.contains(file.path)
                        com.example.FileItemRow(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onToggleSelect = { if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path) },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                } else {
                                    navController.navigate("viewer/${Uri.encode(file.path)}")
                                }
                            }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(photosAndVideos) { file ->
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
                                if (!isSelectionMode) {
                                    selectedFiles.add(file.path)
                                }
                            }
                        )
                    }
                }
            }
        }
        is ViewState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(viewState.message, color = MaterialTheme.colorScheme.error)
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
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(albums.keys.toList()) { albumName ->
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
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = albumName, 
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
        is ViewState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(viewState.message, color = MaterialTheme.colorScheme.error)
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                if (isSelected) 4.dp else 0.5.dp, 
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
