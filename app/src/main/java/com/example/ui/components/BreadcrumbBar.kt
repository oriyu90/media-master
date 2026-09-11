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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
) {
    val relative = currentPath.removePrefix(rootPath).trim('/')
    val segments = if (relative.isBlank()) emptyList() else relative.split('/')
    val scrollState = rememberScrollState()
    LaunchedEffect(currentPath) { scrollState.animateScrollTo(scrollState.maxValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BreadcrumbChip(rootLabel, onClick = { onNavigate(rootPath) })
        var accumulated = rootPath
        segments.forEach { segment ->
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            accumulated = "$accumulated/$segment"
            val target = accumulated
            BreadcrumbChip(segment, onClick = { onNavigate(target) })
        }
    }
}

@Composable
private fun BreadcrumbChip(label: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}
