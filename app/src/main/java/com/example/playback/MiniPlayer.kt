package com.example.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R

/**
 * Compact now-playing bar. Anchored to the bottom by the caller; it reserves the
 * navigation-bar inset itself so controls are never under the system bar.
 * Position comes from [PlaybackManager] (listener + light ticker) rather than a
 * per-frame poll in the composable.
 */
@Composable
fun MiniPlayer(onNavigateToAudio: () -> Unit, modifier: Modifier = Modifier) {
    val isPlaying by PlaybackManager.isPlaying.collectAsStateWithLifecycle()
    val title by PlaybackManager.currentMediaTitle.collectAsStateWithLifecycle()
    val position by PlaybackManager.currentPosition.collectAsStateWithLifecycle()
    val duration by PlaybackManager.duration.collectAsStateWithLifecycle()

    if (title.isEmpty()) return

    val remaining = (duration - position).coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToAudio),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "-${formatTime(remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { PlaybackManager.player?.seekToPreviousMediaItem() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous))
            }
            IconButton(onClick = {
                val player = PlaybackManager.player ?: return@IconButton
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                )
            }
            IconButton(onClick = { PlaybackManager.player?.seekToNextMediaItem() }) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
}
