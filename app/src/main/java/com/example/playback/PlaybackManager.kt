@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackManager {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var player: MediaController? = null
        private set

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var positionJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentMediaTitle = MutableStateFlow("")
    val currentMediaTitle: StateFlow<String> = _currentMediaTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow("")
    val currentArtist: StateFlow<String> = _currentArtist.asStateFlow()

    private val _currentArtworkUri = MutableStateFlow<android.net.Uri?>(null)
    val currentArtworkUri: StateFlow<android.net.Uri?> = _currentArtworkUri.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    /** Equalizer state, forwarded as-is from EqualizerController (same process). */
    val equalizerState: StateFlow<EqState> = EqualizerController.state

    fun toggleShuffle() {
        val p = player ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val p = player ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setEqualizerBand(bandIndex: Int, level: Short) = EqualizerController.setBandLevel(bandIndex, level)

    fun useEqualizerPreset(preset: Short) = EqualizerController.usePreset(preset)

    fun initialize(context: Context) {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            { 
                player = future.get()
                setupPlayerListeners()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupPlayerListeners() {
        val p = player ?: return
        _isPlaying.value = p.isPlaying
        _currentMediaTitle.value = p.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: p.currentMediaItem?.mediaId ?: ""
        _currentArtist.value = p.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
        _currentArtworkUri.value = p.currentMediaItem?.mediaMetadata?.artworkUri
        _duration.value = p.duration.coerceAtLeast(0)
        _currentPosition.value = p.currentPosition.coerceAtLeast(0)
        _shuffleEnabled.value = p.shuffleModeEnabled
        _repeatMode.value = p.repeatMode
        if (p.isPlaying) startPositionUpdates()

        p.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startPositionUpdates() else {
                    stopPositionUpdates()
                    _currentPosition.value = p.currentPosition.coerceAtLeast(0)
                }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                _currentMediaTitle.value = mediaItem?.mediaMetadata?.title?.toString() ?: mediaItem?.mediaId ?: ""
                _currentArtist.value = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
                _currentArtworkUri.value = mediaItem?.mediaMetadata?.artworkUri
                _duration.value = p.duration.coerceAtLeast(0)
                _currentPosition.value = p.currentPosition.coerceAtLeast(0)
            }
            // Fires once ExoPlayer parses the file's own ID3/Vorbis tags, overlaying
            // (title/artist/artwork) on top of whatever MediaItem.Builder set statically.
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                mediaMetadata.title?.toString()?.let { _currentMediaTitle.value = it }
                _currentArtist.value = mediaMetadata.artist?.toString() ?: mediaMetadata.albumArtist?.toString() ?: ""
                _currentArtworkUri.value = mediaMetadata.artworkUri
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                _currentPosition.value = p.currentPosition.coerceAtLeast(0)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = p.duration.coerceAtLeast(0)
                }
            }
        })
    }

    /** Ticks [currentPosition] roughly twice a second while playing (no busy loop when paused). */
    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                player?.let { _currentPosition.value = it.currentPosition.coerceAtLeast(0) }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    fun release() {
        stopPositionUpdates()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        player = null
        _isPlaying.value = false
        _currentPosition.value = 0L
    }
}
