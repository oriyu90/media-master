package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.MediaFile
import com.example.R
import com.example.openWithExternalApp

/**
 * v1.11.0 shared "Open with" pieces: every file list (Files, Category,
 * Documents, Library) offers the same right-click / long-press menu entry
 * that hands the file to a user-picked app via the system chooser.
 */

/** Dropdown entry resolving context internally; [onDone] closes the menu. */
@Composable
fun OpenWithDropdownItem(file: MediaFile, onDone: () -> Unit) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text(stringResource(R.string.open_with)) },
        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
        onClick = {
            onDone()
            openWithExternalApp(context, file)
        },
    )
}

/** Dropdown entry for entering multi-select from the context menu. */
@Composable
fun SelectDropdownItem(onSelect: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.select)) },
        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
        onClick = onSelect,
    )
}

/**
 * Desktop right-click opener for the context menu. No-op when [enabled] is
 * false (e.g. the synthetic ".." entry). Keyed on [menuKey] (usually the
 * file path) so recycled rows never fire for a stale item.
 */
fun Modifier.rightClickMenu(
    enabled: Boolean,
    menuKey: Any?,
    onOpenMenu: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(menuKey) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                onOpenMenu()
                event.changes.forEach { it.consume() }
            }
        }
    }
}
