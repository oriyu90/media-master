package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.PathInput

/**
 * v2.1.0 Explorer parity with Windows Explorer's "Cut/Copy to path":
 * type (or paste) a destination directory and move/copy the selection there.
 *
 * Validation is delegated to the pure [PathInput.validate]; the dialog only
 * shows the localized reason and keeps the confirm button disabled until the
 * path is acceptable, so an invalid destination can never reach the
 * filesystem. [onConfirm] receives the normalized absolute path.
 */
@Composable
fun PathMoveDialog(
    isMove: Boolean,
    sourcePaths: List<String>,
    primaryRoot: String?,
    storageRoots: List<String>,
    initialPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialPath) }
    val result = remember(text, sourcePaths, storageRoots, primaryRoot) {
        PathInput.validate(text, primaryRoot, storageRoots, sourcePaths)
    }
    val errorText = (result as? PathInput.Result.Invalid)?.let { localizedReason(it.error) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isMove) R.string.move_to_path else R.string.copy_to_path)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        if (isMove) R.string.move_to_path_desc else R.string.copy_to_path_desc,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.destination_path)) },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { (result as? PathInput.Result.Ok)?.let { onConfirm(it.path) } },
                enabled = result is PathInput.Result.Ok,
            ) { Text(stringResource(if (isMove) R.string.move else R.string.copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun localizedReason(error: PathInput.Error): String = stringResource(
    when (error) {
        PathInput.Error.EMPTY -> R.string.path_error_empty
        PathInput.Error.NOT_ABSOLUTE -> R.string.path_error_not_absolute
        PathInput.Error.NOT_FOUND -> R.string.path_error_not_found
        PathInput.Error.NOT_A_DIRECTORY -> R.string.path_error_not_a_directory
        PathInput.Error.OUTSIDE_STORAGE -> R.string.path_error_outside_storage
        PathInput.Error.SAME_AS_SOURCE -> R.string.path_error_same_as_source
        PathInput.Error.INSIDE_SOURCE -> R.string.path_error_inside_source
        PathInput.Error.NOT_WRITABLE -> R.string.path_error_not_writable
    }
)

/**
 * v2.1.0 "Copy path": plain-text clipboard hand-off with a confirmation
 * toast. Uses the platform ClipboardManager (no permissions needed) and
 * never throws for oversized content.
 */
fun copyPathToClipboard(context: android.content.Context, path: String) {
    if (path.isBlank()) return
    runCatching {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("path", path))
    }
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.path_copied),
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}
