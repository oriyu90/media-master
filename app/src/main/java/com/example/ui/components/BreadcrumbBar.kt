package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Files-by-Google/My-Files-style breadcrumb trail: [rootPath] (shown as
 * [rootLabel], e.g. "Internal Storage") followed by one chip per path segment
 * down to [currentPath]. Tapping any chip jumps straight there.
 */
@Composable
fun BreadcrumbBar(
    rootPath: String,
    rootLabel: String,
    currentPath: String,
    onNavigate: (String) -> Unit,
    // v2.1.0: dropping files on a crumb moves them into that folder
    // (Windows Explorer parity). Null keeps the bar read-only.
    onDropPaths: ((destination: String, paths: List<String>) -> Boolean)? = null,
) {
    val relative = currentPath.removePrefix(rootPath).trim('/')
    val segments = if (relative.isBlank()) emptyList() else relative.split('/')
    val scrollState = rememberScrollState()
    // v1.9.0 (MD3 remake): wait for the first layout pass before scrolling —
    // maxValue is 0 until content is measured, so the old animateScrollTo
    // raced layout and the trail stayed at the start.
    LaunchedEffect(currentPath, scrollState.maxValue) {
        if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BreadcrumbChip(
            label = rootLabel,
            description = stringResource(R.string.navigate_to, rootLabel),
            selected = segments.isEmpty(),
            onClick = { onNavigate(rootPath) },
            onDropPaths = onDropPaths?.let { drop -> { paths -> drop(rootPath, paths) } },
        )
        var accumulated = rootPath
        segments.forEachIndexed { index, segment ->
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            accumulated = "$accumulated/$segment"
            val target = accumulated
            BreadcrumbChip(
                label = segment,
                description = stringResource(R.string.navigate_to, segment),
                selected = index == segments.lastIndex,
                onClick = { onNavigate(target) },
                onDropPaths = onDropPaths?.let { drop -> { paths -> drop(target, paths) } },
            )
        }
    }
}

@Composable
private fun BreadcrumbChip(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDropPaths: ((List<String>) -> Boolean)? = null,
) {
    var dropHover by remember { mutableStateOf(false) }
    val dropModifier = Modifier.dropTarget(
        enabled = onDropPaths != null,
        onHover = { dropHover = it },
        onDrop = { paths -> onDropPaths?.invoke(paths) ?: false },
    )
    SuggestionChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier
            .then(dropModifier)
            .semantics {
                this.selected = selected
                this.contentDescription = description
            },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (dropHover) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = if (dropHover) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        ),
    )
}
