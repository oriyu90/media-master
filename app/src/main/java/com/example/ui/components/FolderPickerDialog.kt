package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import java.io.File

/**
 * Minimal, self-contained destination picker for move/copy: browses
 * directories only, independent of FileViewModel's shared fileTreeState (so
 * it can't disturb whatever folder the user is currently browsing behind it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    startPath: String,
    isMove: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var currentDir by remember { mutableStateOf(File(startPath).takeIf { it.isDirectory } ?: File(startPath).parentFile ?: File(startPath)) }
    val subDirs = remember(currentDir) {
        currentDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.8f), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(currentDir.name.ifBlank { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        currentDir.parentFile?.let { parent ->
                            IconButton(onClick = { currentDir = parent }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(subDirs, key = { it.absolutePath }) { dir ->
                        ListItem(
                            headlineContent = { Text(dir.name) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { currentDir = dir },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(currentDir.absolutePath) }) {
                        Text(stringResource(if (isMove) R.string.move_here else R.string.copy_here))
                    }
                }
            }
        }
    }
}
