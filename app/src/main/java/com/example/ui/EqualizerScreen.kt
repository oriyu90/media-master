package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.R
import com.example.playback.EqBand
import com.example.playback.PlaybackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(navController: NavHostController) {
    val state by PlaybackManager.equalizerState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.equalizer)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        }
    ) { innerPadding ->
        if (!state.enabled || state.bands.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.no_files_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            if (state.presets.isNotEmpty()) {
                var presetMenuExpanded by remember { mutableStateOf(false) }
                val presetLabel = state.currentPreset.let { p ->
                    if (p >= 0 && p < state.presets.size) state.presets[p.toInt()] else stringResource(R.string.preset)
                }
                ExposedDropdownMenuBox(
                    expanded = presetMenuExpanded,
                    onExpandedChange = { presetMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = presetLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.preset)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = presetMenuExpanded, onDismissRequest = { presetMenuExpanded = false }) {
                        state.presets.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    PlaybackManager.useEqualizerPreset(index.toShort())
                                    presetMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.bands.forEach { band ->
                    EqBandSlider(
                        band = band,
                        minLevel = state.minLevel,
                        maxLevel = state.maxLevel,
                        onLevelChanged = { PlaybackManager.setEqualizerBand(band.index, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EqBandSlider(band: EqBand, minLevel: Short, maxLevel: Short, onLevelChanged: (Short) -> Unit) {
    var level by remember(band.index, band.level) { mutableFloatStateOf(band.level.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}k" else "${band.centerFreqHz}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp),
        )
        Slider(
            value = level,
            onValueChange = { level = it },
            onValueChangeFinished = { onLevelChanged(level.toInt().toShort()) },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            modifier = Modifier.weight(1f),
        )
    }
}
