package com.example.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.FileViewModel
import com.example.ViewState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(playlistName: String, viewModel: FileViewModel, navController: NavHostController) {
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        when (viewState) {
            is ViewState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ViewState.Success -> {
                val playlistFiles = (viewState as ViewState.Success).files.filter { 
                    it.mimeType.startsWith("audio/") && 
                    (File(it.path).parentFile?.name == playlistName)
                }
                
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                        items(playlistFiles.size) { index ->
                            val file = playlistFiles[index]
                            ListItem(
                                headlineContent = { Text(file.name) },
                                supportingContent = { Text(File(file.path).parentFile?.name ?: stringResource(R.string.unknown)) },
                                leadingContent = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    playAudioList(playlistFiles, index)
                                }
                            )
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
