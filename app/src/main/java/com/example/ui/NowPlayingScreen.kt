package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.R
import com.example.playback.PlaybackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(navController: NavHostController) {
    val isPlaying by PlaybackManager.isPlaying.collectAsStateWithLifecycle()
    val title by PlaybackManager.currentMediaTitle.collectAsStateWithLifecycle()
    val artist by PlaybackManager.currentArtist.collectAsStateWithLifecycle()
    val artworkUri by PlaybackManager.currentArtworkUri.collectAsStateWithLifecycle()
    val position by PlaybackManager.currentPosition.collectAsStateWithLifecycle()
    val duration by PlaybackManager.duration.collectAsStateWithLifecycle()
    val shuffleEnabled by PlaybackManager.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by PlaybackManager.repeatMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("equalizer") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Equalizer, contentDescription = stringResource(R.string.equalizer))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = title.ifBlank { stringResource(R.string.now_playing) },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            var seekPosition by remember(position) { mutableFloatStateOf(position.toFloat()) }
            var isSeeking by remember { mutableStateOf(false) }
            Slider(
                value = if (isSeeking) seekPosition else position.toFloat(),
                onValueChange = {
                    isSeeking = true
                    seekPosition = it
                },
                onValueChangeFinished = {
                    PlaybackManager.player?.seekTo(seekPosition.toLong())
                    isSeeking = false
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMillis(if (isSeeking) seekPosition.toLong() else position), style = MaterialTheme.typography.labelMedium)
                Text(formatMillis(duration), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { PlaybackManager.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { PlaybackManager.player?.seekToPreviousMediaItem() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = {
                    val player = PlaybackManager.player ?: return@FilledIconButton
                    if (player.isPlaying) player.pause() else player.play()
                }, modifier = Modifier.size(64.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { PlaybackManager.player?.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next), modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { PlaybackManager.cycleRepeatMode() }) {
                    Icon(
                        repeatModeIcon(repeatMode),
                        contentDescription = stringResource(R.string.repeat),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun repeatModeIcon(repeatMode: Int): ImageVector = when (repeatMode) {
    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
    else -> Icons.Default.Repeat
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
