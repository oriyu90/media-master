package com.example.playback

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Int, val centerFreqHz: Int, val level: Short)

data class EqState(
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevel: Short = 0,
    val maxLevel: Short = 0,
    val presets: List<String> = emptyList(),
    val currentPreset: Short = -1,
)

/**
 * Owns the Equalizer effect attached to PlaybackService's ExoPlayer audio
 * session. PlaybackService and this object always run in the same process
 * (PlaybackService has no android:process override), so this can be a plain
 * singleton instead of routing calls through MediaController custom
 * SessionCommands. Lifecycle mirrors the player: attach() is called once the
 * session id becomes available, release() when the service is destroyed.
 */
object EqualizerController {
    private var equalizer: Equalizer? = null
    private var attachedSessionId: Int = 0

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == attachedSessionId) return
        release()
        attachedSessionId = audioSessionId
        val eq = runCatching { Equalizer(0, audioSessionId) }.getOrNull() ?: return
        equalizer = eq
        runCatching {
            eq.enabled = true
            val range = eq.bandLevelRange
            val bands = (0 until eq.numberOfBands).map { i ->
                val band = i.toShort()
                EqBand(
                    index = i,
                    centerFreqHz = eq.getCenterFreq(band) / 1000,
                    level = eq.getBandLevel(band),
                )
            }
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            _state.value = EqState(
                enabled = true,
                bands = bands,
                minLevel = range[0],
                maxLevel = range[1],
                presets = presets,
                currentPreset = runCatching { eq.currentPreset }.getOrDefault(-1),
            )
        }
    }

    fun setBandLevel(bandIndex: Int, level: Short) {
        val eq = equalizer ?: return
        runCatching {
            eq.setBandLevel(bandIndex.toShort(), level)
            _state.value = _state.value.copy(
                bands = _state.value.bands.map { if (it.index == bandIndex) it.copy(level = level) else it },
                currentPreset = -1,
            )
        }
    }

    fun usePreset(preset: Short) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(preset)
            val bands = _state.value.bands.map { it.copy(level = eq.getBandLevel(it.index.toShort())) }
            _state.value = _state.value.copy(bands = bands, currentPreset = preset)
        }
    }

    fun release() {
        equalizer?.let { runCatching { it.release() } }
        equalizer = null
        attachedSessionId = 0
        _state.value = EqState()
    }
}
