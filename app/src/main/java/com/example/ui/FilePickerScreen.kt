package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FileViewModel
import com.example.MediaFile
import com.example.R
import com.example.formatDate
import com.example.formatSize
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.EmptyState
import com.example.ui.components.Hallmark
import com.example.ui.components.MediaThumbnail
import java.io.File
import kotlinx.coroutines.withContext

/**
 * v1.12.0 system-picker surface: shown instead of the normal shells while
 * serving another app's `ACTION_GET_CONTENT` / `ACTION_OPEN_DOCUMENT`.
 *
 * The user browses (same roots/engine as the file browser) and taps exactly
 * one file; it is returned to the requester via `setResult + finish` by the
 * caller. Tapping a directory navigates into it (never returned). Cancel /
 * system back returns `RESULT_CANCELED`. No delete/rename/share actions exist
 * here — picking must be side-effect free.
 *
 * @param mimeFilter requester's MIME filter (wildcard = everything). Directories
 * always stay visible; files that clearly do not match are hidden.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FilePickerScreen(
    viewModel: FileViewModel,
    mimeFilter: String,
    onPick: (uri: Uri, mimeType: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val viewState by viewModel.fileTreeState.collectAsStateWithLifecycle()
    var storageRoots by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        storageRoots = withContext(kotlinx.coroutines.Dispatchers.IO) { viewModel.storageRoots() }
    }
    LaunchedEffect(storageRoots) {
        val first = storageRoots.firstOrNull()
        if (first != null) viewModel.loadFiles(first)
    }

    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_file), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(R.string.pick_file_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val success = viewState as? com.example.ViewState.Success
            if (success != null) {
                val root = storageRoots.firstOrNull { success.currentPath.startsWith(it) }
                    ?: storageRoots.firstOrNull()
                if (root != null) {
                    val isInternal = root.contains("emulated")
                    BreadcrumbBar(
                        rootPath = root,
                        rootLabel = FolderNames.storageRootTitle(
                            root, isInternal,
                            stringResource(R.string.internal_storage),
                            stringResource(R.string.external_storage),
                        ),
                        currentPath = success.currentPath,
                        onNavigate = { viewModel.loadFiles(it) },
                    )
                }
            }
            when (val state = viewState) {
                is com.example.ViewState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is com.example.ViewState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is com.example.ViewState.Success -> {
                    val visible = remember(state.files, mimeFilter) {
                        state.files.filter { file ->
                            file.name != ".." && (file.isDirectory || mimeMatches(mimeFilter, file.mimeType))
                        }
                    }
                    if (visible.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                title = stringResource(R.string.no_files_found),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = Hallmark.ContentEdge,
                                vertical = Hallmark.ItemGap,
                            ),
                        ) {
                            itemsIndexed(
                                visible,
                                key = { _, file -> file.path },
                                contentType = { _, file -> if (file.isDirectory) "dir" else "file" },
                            ) { _, file ->
                                PickerRow(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.loadFiles(file.path)
                                        } else {
                                            val uri = file.contentUri ?: runCatching {
                                                androidx.core.content.FileProvider.getUriForFile(
                                                    context, "${context.packageName}.fileprovider", File(file.path),
                                                )
                                            }.getOrNull()
                                            if (uri != null) {
                                                onPick(uri, file.mimeType.ifBlank { "*/*" })
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.could_not_open_file),
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(file: MediaFile, onClick: () -> Unit) {
    Column {
        ListItem(
            headlineContent = {
                Text(text = file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                if (!file.isDirectory) {
                    Text(
                        text = "${formatSize(file.size)} · ${formatDate(file.dateModified)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            leadingContent = {
                MediaThumbnail(
                    file = file,
                    modifier = Modifier.size(48.dp),
                    iconSize = 40.dp,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Hallmark.RowMinHeight)
                .combinedClickable(onClick = onClick),
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * v1.12.0 picker filter. Pure so unit tests pin it: wildcard (or blank)
 * accepts everything, a major-type prefix matches its family, otherwise exact
 * (case-insensitive) match. Unknown file types (`""`/`octet-stream`) are
 * accepted — the picker must not hide a file it cannot judge.
 */
internal fun mimeMatches(filter: String, fileMime: String): Boolean {
    val f = filter.trim().lowercase()
    if (f.isEmpty() || f == "*/*") return true
    val m = fileMime.trim().lowercase()
    if (m.isEmpty() || m == "application/octet-stream") return true
    if (f.endsWith("/*")) return m.startsWith(f.dropLast(1))
    return m == f
}
