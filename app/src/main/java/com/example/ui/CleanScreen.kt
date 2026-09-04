package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TaskAlt
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
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(viewModel: FileViewModel, navController: NavHostController) {
    val duplicates by viewModel.duplicateFiles.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningDuplicates.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clean_duplicates)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LoadingButton(
                onClick = { viewModel.findDuplicates() },
                loading = isScanning,
                text = if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.scan_for_duplicates),
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            )

            if (duplicates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.TaskAlt,
                        title = stringResource(R.string.clean_duplicates),
                        description = stringResource(R.string.free_up_space),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(duplicates, key = { group -> group.firstOrNull()?.path ?: group.hashCode().toString() }) { group ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.found_duplicates, group.size),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                group.forEach { file ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = file.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.deleteFile(file.path, file.contentUri) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
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
