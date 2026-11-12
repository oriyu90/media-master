package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.FileViewModel
import com.example.R
import com.example.deeplink.IncomingFiles
import com.example.deeplink.SendRequest
import com.example.ui.components.FolderPickerDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WP3 "save shared files": shown instead of the normal shells while serving
 * another app's `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.
 *
 * The user confirms (or changes) the destination folder, then the files are
 * copied by [FileViewModel.saveIncomingFiles] (streamed, per-file failure
 * counted, MediaScanner notified). Progress is shown as a count; the result
 * is reported with a toast and [onFinished] closes the Activity with
 * RESULT_OK. Cancelling before saving answers RESULT_CANCELED.
 *
 * No storage permission is required to receive a shared stream: the sender
 * already granted read access to its URI. The picker only lists folders when
 * the file system is readable, so the default destination stays usable on a
 * device without MANAGE_EXTERNAL_STORAGE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingSaveScreen(
    viewModel: FileViewModel,
    request: SendRequest,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
) {
    val context = LocalContext.current
    var destination by remember { mutableStateOf<String?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var doneCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        destination = withContext(Dispatchers.IO) { IncomingFiles.defaultDestination(context) }
    }

    BackHandler(enabled = !saving, onBack = onCancelled)

    val total = request.count
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.incoming_save_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancelled, enabled = !saving) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.incoming_save_count, total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = request.uris.firstOrNull()?.let { uri ->
                            runCatching { uri.lastPathSegment }.getOrNull()
                        }?.takeIf { it.isNotBlank() } ?: request.mimeType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.incoming_save_destination),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = destination?.let { File(it).name.ifBlank { it } } ?: stringResource(R.string.incoming_save_destination_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = destination.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = { showFolderPicker = true },
                enabled = !saving && destination != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.incoming_choose_folder))
            }

            if (saving) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else doneCount.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.incoming_saving_progress, doneCount, total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = {
                    val dest = destination ?: return@Button
                    saving = true
                    viewModel.saveIncomingFiles(
                        uris = request.uris,
                        destDir = dest,
                        onProgress = { done, _ -> doneCount = done },
                        onDone = { _, saved, failed ->
                            saving = false
                            val message = if (failed == 0) {
                                context.getString(R.string.incoming_save_done, saved)
                            } else {
                                context.getString(R.string.incoming_save_done, saved) + "\n" +
                                    context.getString(R.string.incoming_save_failed, failed)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            // v2.1.0: refresh the in-app listing so the saved
                            // files appear when the user returns to the app.
                            if (saved > 0) viewModel.loadFiles(dest)
                            onFinished()
                        },
                    )
                },
                enabled = !saving && destination != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.incoming_save_action))
            }

            Text(
                text = stringResource(R.string.incoming_save_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showFolderPicker) {
        // FolderPickerDialog walks up with parentFile, so start at an existing
        // directory (a not-yet-created Download would trap the user at "/").
        val pickerStart = remember(destination) {
            var dir = destination?.let(::File)
            while (dir != null && !dir.isDirectory) dir = dir.parentFile
            dir?.absolutePath ?: destination.orEmpty()
        }
        FolderPickerDialog(
            startPath = pickerStart,
            isMove = false,
            onDismiss = { showFolderPicker = false },
            onConfirm = { path ->
                destination = path
                showFolderPicker = false
            },
        )
    }
}
