package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AdjustmentControls(
    adjustmentValues: Map<AdjustmentType, Float>,
    onAdjustmentChange: (AdjustmentType, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAdjustment by remember { mutableStateOf<AdjustmentType?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        selectedAdjustment?.let { adjustment ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(adjustment.label, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = adjustmentValues[adjustment] ?: getDefaultAdjustmentValue(adjustment),
                    onValueChange = { onAdjustmentChange(adjustment, it) },
                    valueRange = getAdjustmentRange(adjustment)
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(AdjustmentType.values()) { adjustment ->
                val isSelected = selectedAdjustment == adjustment
                Column(
                    modifier = Modifier.clickable {
                        selectedAdjustment = if (isSelected) null else adjustment
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = getAdjustmentIcon(adjustment),
                        contentDescription = adjustment.label,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.size(32.dp).padding(4.dp)
                    )
                    Text(
                        text = adjustment.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }
    }
}

fun getAdjustmentRange(type: AdjustmentType): ClosedFloatingPointRange<Float> {
    return when (type) {
        AdjustmentType.EXPOSURE -> -1f..1f
        AdjustmentType.BRIGHTNESS -> -1f..1f
        AdjustmentType.CONTRAST -> 0.1f..2f
        AdjustmentType.BRILLIANCE -> -1f..1f
        AdjustmentType.SHARPNESS -> 0f..1f
        AdjustmentType.BLACK_POINT -> -1f..1f
        AdjustmentType.WHITE_POINT -> -1f..1f
        AdjustmentType.SATURATION -> 0f..2f
        AdjustmentType.HUE -> -180f..180f
    }
}

fun getDefaultAdjustmentValue(type: AdjustmentType): Float {
    return when (type) {
        AdjustmentType.CONTRAST, AdjustmentType.SATURATION -> 1f
        else -> 0f
    }
}

fun getAdjustmentIcon(type: AdjustmentType): ImageVector {
    return when (type) {
        AdjustmentType.EXPOSURE -> Icons.Default.Exposure
        AdjustmentType.BRIGHTNESS -> Icons.Default.BrightnessMedium
        AdjustmentType.CONTRAST -> Icons.Default.Contrast
        AdjustmentType.BRILLIANCE -> Icons.Default.AutoAwesome
        AdjustmentType.SHARPNESS -> Icons.Default.Details
        AdjustmentType.BLACK_POINT -> Icons.Default.FormatColorFill
        AdjustmentType.WHITE_POINT -> Icons.Default.FormatColorReset
        AdjustmentType.SATURATION -> Icons.Default.ColorLens
        AdjustmentType.HUE -> Icons.Default.Palette
    }
}
