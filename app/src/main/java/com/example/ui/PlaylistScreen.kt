package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.FileViewModel
import com.example.R
import com.example.ViewState
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(playlistName: String, viewModel: FileViewModel, navController: NavHostController) {
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val vs = viewState) {
                is ViewState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ViewState.Success -> {
                    val playlistFiles = vs.files.filter {
                        it.mimeType.startsWith("audio/") && File(it.path).parentFile?.name == playlistName
                    }
                    if (playlistFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                title = playlistName,
                                description = stringResource(R.string.no_files_found),
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(onClick = { playAudioList(playlistFiles) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_forward))
                                }
                                Button(onClick = { playAudioList(playlistFiles, reverse = true) }) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.play_reverse))
                                }
                                Button(onClick = { playAudioList(playlistFiles, shuffle = true) }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle))
                                }
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(playlistFiles, key = { it.path }) { file ->
                                    val index = playlistFiles.indexOf(file)
                                    ListItem(
                                        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = {
                                            Text(File(file.path).parentFile?.name ?: stringResource(R.string.unknown))
                                        },
                                        leadingContent = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
                                        modifier = Modifier.clickable { playAudioList(playlistFiles, index) }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                is ViewState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(message = vs.message)
                    }
                }
            }
        }
    }
}
