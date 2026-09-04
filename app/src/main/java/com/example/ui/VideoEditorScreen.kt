@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(uriString: String, navController: NavHostController) {
    val context = LocalContext.current
    val uri = Uri.parse(uriString)
    val coroutineScope = rememberCoroutineScope()
    
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startTrimMs by remember { mutableLongStateOf(0L) }
    var endTrimMs by remember { mutableLongStateOf(100L) } // dummy initial value
    var isSaving by remember { mutableStateOf(false) }

    var isMuted by remember { mutableStateOf(false) }

    val adjustmentValues = remember { mutableStateMapOf<AdjustmentType, Float>() }
    var editTab by remember { mutableIntStateOf(0) } // 0 for Trim, 1 for Adjust

    DisposableEffect(uri) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY && durationMs == 0L) {
                        durationMs = duration.coerceAtLeast(100L)
                        endTrimMs = durationMs
                    }
                }
            })
        }
        exoPlayer = player

        onDispose {
            player.release()
        }
    }
    
    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(startTrimMs, endTrimMs) {
        while (true) {
            val player = exoPlayer ?: break
            if (player.isPlaying) {
                if (player.currentPosition > endTrimMs) {
                    player.seekTo(startTrimMs)
                }
            }
            delay(100)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_video)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isSaving = true
                            exportTrimmedVideo(context, uri, startTrimMs, endTrimMs, isMuted, adjustmentValues)
                            isSaving = false
                            navController.popBackStack()
                        }
                    }, enabled = durationMs > 0 && !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = {
                            PlayerView(context).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TabRow(selectedTabIndex = editTab) {
                    Tab(selected = editTab == 0, onClick = { editTab = 0 }) {
                        Text(stringResource(R.string.trim), modifier = Modifier.padding(16.dp))
                    }
                    Tab(selected = editTab == 1, onClick = { editTab = 1 }) {
                        Text(stringResource(R.string.adjust), modifier = Modifier.padding(16.dp))
                    }
                }
                if (editTab == 0) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.mute_audio), style = MaterialTheme.typography.labelLarge)
                            Switch(checked = isMuted, onCheckedChange = { isMuted = it })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.trim_video), style = MaterialTheme.typography.labelMedium)
                        if (durationMs > 0) {
                            RangeSlider(
                                value = (startTrimMs.toFloat()..endTrimMs.toFloat()),
                                onValueChange = { range ->
                                    startTrimMs = range.start.toLong()
                                    endTrimMs = range.endInclusive.toLong()
                                    exoPlayer?.seekTo(startTrimMs)
                                },
                                valueRange = 0f..durationMs.toFloat()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatTime(startTrimMs), style = MaterialTheme.typography.bodySmall)
                                Text(formatTime(endTrimMs), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    AdjustmentControls(
                        adjustmentValues = adjustmentValues,
                        onAdjustmentChange = { type, value -> adjustmentValues[type] = value }
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return java.lang.String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

suspend fun exportTrimmedVideo(context: Context, inputUri: Uri, startMs: Long, endMs: Long, isMuted: Boolean, adjustments: Map<AdjustmentType, Float>) {
    withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "trimmed_video_${System.currentTimeMillis()}.mp4")
        
        val clippingConfig = ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clippingConfig)
            .build()
            
        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        
        val contrastValue = adjustments[AdjustmentType.CONTRAST] ?: 1f
        if (contrastValue != 1f) {
            videoEffects.add(androidx.media3.effect.Contrast(contrastValue))
        }

        val saturationValue = adjustments[AdjustmentType.SATURATION] ?: 1f
        val hueValue = adjustments[AdjustmentType.HUE] ?: 0f
        val lightnessValue = adjustments[AdjustmentType.BRIGHTNESS] ?: 0f
        
        if (saturationValue != 1f || hueValue != 0f || lightnessValue != 0f) {
            val hslBuilder = androidx.media3.effect.HslAdjustment.Builder()
            if (hueValue != 0f) hslBuilder.adjustHue(hueValue)
            // HslAdjustment uses percentage or difference? Let's try adjustSaturation(saturationValue * 100)
            if (saturationValue != 1f) hslBuilder.adjustSaturation(saturationValue * 100f - 100f) 
            if (lightnessValue != 0f) hslBuilder.adjustLightness(lightnessValue * 100f)
            videoEffects.add(hslBuilder.build())
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(isMuted)
            .setEffects(androidx.media3.transformer.Effects(emptyList(), videoEffects))
            .build()
        val transformer = Transformer.Builder(context).build()

        val job = CompletableDeferred<Boolean>()

        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                job.complete(true)
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                job.complete(false)
            }
        })

        transformer.start(editedMediaItem, outputFile.absolutePath)
        
        val success = job.await()
        if (success) {
            // Save to MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Edited_Video_${System.currentTimeMillis()}.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/Edited")
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                val outputStream = context.contentResolver.openOutputStream(uri)
                val inputStream = outputFile.inputStream()
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
                inputStream.close()
            }
        }
        outputFile.delete()
    }
}
