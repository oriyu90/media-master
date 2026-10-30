package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.abs

/**
 * v1.14.0: a labelled slider with a one-tap "back to the middle/default"
 * button at its right end. Used by tilt (X/Y/Z) and every colour adjustment
 * so centring a value no longer requires pixel-perfect dragging.
 *
 * The button is disabled when the value is already at its default, which
 * tells the user at a glance that the slider is centred.
 */
@Composable
fun EditorSlider(
    label: String,
    value: Float,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val atDefault = abs(value - defaultValue) <= DEFAULT_EPSILON
    val resetLabel = stringResource(R.string.reset_adjustment)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
            )
            IconButton(
                onClick = { onValueChange(defaultValue) },
                enabled = !atDefault,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = resetLabel },
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Float comparison used to decide whether the reset button is a no-op. */
internal const val DEFAULT_EPSILON = 0.001f
