package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
fun AlbumScreen(albumName: String, viewModel: FileViewModel, navController: NavHostController) {
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumName) },
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
                val albumFiles = (viewState as ViewState.Success).files.filter { 
                    (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")) && 
                    (File(it.path).parentFile?.name == albumName)
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(albumFiles) { file ->
                        MediaGridItem(
                            file = file,
                            isSelected = false,
                            onClick = { navController.navigate("viewer/${Uri.encode(file.path)}") },
                            onLongClick = {}
                        )
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
