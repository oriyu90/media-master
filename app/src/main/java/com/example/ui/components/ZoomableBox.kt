package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Pinch-to-zoom + pan container for content that has no zoom of its own
 * (video surfaces, PDF/Office document pages). Not used by the plain image
 * viewer's OCR mode (ImageWithOcrOverlay in ViewerScreen.kt), which has its
 * own zoom state feeding OCR hit-testing math and is left untouched to avoid
 * regressing that already-working coordinate mapping.
 *
 * [onZoomChanged] reports whether the content is currently zoomed in
 * (scale > 1), so a caller inside a HorizontalPager can disable the pager's
 * own swipe handling while the user is panning around a zoomed page —
 * otherwise a pan gesture fights the pager's swipe-to-next-page gesture.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    enabled: Boolean = true,
    onZoomChanged: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var scale by remember { mutableFloatStateOf(minScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun reset() {
        scale = minScale
        offset = Offset.Zero
        onZoomChanged(false)
    }

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { reset() })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // Only claim the gesture for a pinch (2+ pointers) or while already
                // zoomed in — a single-finger drag at rest must fall through
                // unconsumed so an enclosing HorizontalPager can swipe pages.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2 || scale > minScale) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                            scale = newScale
                            offset = if (newScale <= minScale) Offset.Zero else offset + pan
                            onZoomChanged(newScale > minScale)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
        content = content,
    )
}
