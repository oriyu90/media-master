package com.example.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun MiniPlayer(onNavigateToAudio: () -> Unit) {
    val isPlaying by PlaybackManager.isPlaying.collectAsState(initial = false)
    val title by PlaybackManager.currentMediaTitle.collectAsState(initial = "")
    val currentPosition by PlaybackManager.currentPosition.collectAsState(initial = 0L)
    val duration by PlaybackManager.duration.collectAsState(initial = 0L)

    var position by remember { mutableStateOf(0L) }
    
    LaunchedEffect(isPlaying, currentPosition) {
        if (isPlaying) {
            while (true) {
                position = PlaybackManager.player?.currentPosition ?: 0L
                delay(1000)
            }
        } else {
            position = PlaybackManager.player?.currentPosition ?: 0L
        }
    }

    if (title.isNotEmpty()) {
        val remaining = (duration - position).coerceAtLeast(0)
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAudio() },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "-${formatTime(remaining)}", style = MaterialTheme.typography.bodySmall)
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
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play)
                    )
                }
                IconButton(onClick = { PlaybackManager.player?.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next))
                }
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
