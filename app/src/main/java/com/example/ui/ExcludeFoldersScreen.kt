package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.FileViewModel
import com.example.R
import java.io.File
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludeFoldersScreen(viewModel: FileViewModel, navController: NavHostController) {
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    var showFolderPicker by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exclude_folders)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showFolderPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_folder))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (excludedFolders.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_folders_excluded),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(excludedFolders.toList()) { path ->
                    ListItem(
                        headlineContent = { Text(File(path).name) },
                        supportingContent = { Text(path) },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeExcludedFolder(path) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
    
    if (showFolderPicker) {
        FolderPickerDialog(
            onDismiss = { showFolderPicker = false },
            onFolderSelected = { path ->
                viewModel.addExcludedFolder(path)
                showFolderPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(onDismiss: () -> Unit, onFolderSelected: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentPath by remember { 
        mutableStateOf(android.os.Environment.getExternalStorageDirectory().absolutePath) 
    }
    
    // Get external storage roots
    val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
    val storageRoots = externalDirs.mapNotNull { dir ->
        if (dir != null) {
            val path = dir.absolutePath
            val androidIndex = path.indexOf("/Android/data/")
            if (androidIndex != -1) {
                path.substring(0, androidIndex)
            } else {
                null
            }
        } else null
    }.distinct()

    // Disk I/O off the composition: recompute only when the browsed path changes.
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(currentPath) {
        files = withContext(kotlinx.coroutines.Dispatchers.IO) {
            File(currentPath).listFiles()
                ?.filter { it.isDirectory && !it.isHidden }
                ?.sortedBy { it.name }
                .orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_folder)) },
        text = {
            Column {
                // Volume selector
                if (storageRoots.size > 1) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = if (currentPath.contains("emulated")) stringResource(R.string.internal_storage) else stringResource(R.string.external_storage),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            storageRoots.forEach { root ->
                                val isInternal = root.contains("emulated")
                                DropdownMenuItem(
                                    text = { Text(if (isInternal) stringResource(R.string.internal_storage) else stringResource(R.string.external_storage)) },
                                    onClick = {
                                        currentPath = root
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Text(currentPath, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    if (currentPath !in storageRoots) {
                        item {
                            ListItem(
                                headlineContent = { Text("..") },
                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    File(currentPath).parentFile?.let { parent ->
                                        currentPath = parent.absolutePath
                                    }
                                }
                            )
                        }
                    }
                    items(files) { file ->
                        ListItem(
                            headlineContent = { Text(file.name) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { currentPath = file.absolutePath }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onFolderSelected(currentPath) }) {
                Text(stringResource(R.string.add_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
