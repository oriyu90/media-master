package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

/**
 * v1.11.0 bounded zoom helpers (pure — unit-tested in ZoomBoundsTest).
 *
 * Contract the viewer honors:
 * - scale never drops below [minScale] (fit = 1): shrinking past the long
 *   edge is not a use case, so the content always covers its container;
 * - pan is clamped so no empty gutter appears beyond the scaled content
 *   (at rest the offset is zero; zoomed panning stops at the edge).
 */

/** Fit-draw size of [imageSize] inside [boxSize] (ContentScale.Fit math). */
internal fun fitDrawSize(boxSize: IntSize, imageSize: IntSize): androidx.compose.ui.geometry.Size {
    if (boxSize.width <= 0 || boxSize.height <= 0 || imageSize.width <= 0 || imageSize.height <= 0) {
        return androidx.compose.ui.geometry.Size(boxSize.width.toFloat(), boxSize.height.toFloat())
    }
    val canvasAspect = boxSize.width.toFloat() / boxSize.height.toFloat()
    val imageAspect = imageSize.width.toFloat() / imageSize.height.toFloat()
    return if (imageAspect > canvasAspect) {
        val h = boxSize.width / imageAspect
        androidx.compose.ui.geometry.Size(boxSize.width.toFloat(), h)
    } else {
        val w = boxSize.height * imageAspect
        androidx.compose.ui.geometry.Size(w, boxSize.height.toFloat())
    }
}

/**
 * Max allowed translation per axis for [drawSize] scaled by [scale] inside
 * [boxSize]: half the overhang, floored at zero so an unzoomed (or
 * short-edge-covered) axis cannot drift at all.
 */
internal fun maxPanOffset(boxSize: IntSize, drawSize: androidx.compose.ui.geometry.Size, scale: Float): Offset {
    val scaledW = drawSize.width * scale
    val scaledH = drawSize.height * scale
    return Offset(
        x = max(0f, (scaledW - boxSize.width) / 2f),
        y = max(0f, (scaledH - boxSize.height) / 2f),
    )
}

/** Clamps [scale] into [minScale]..[maxScale]. */
internal fun clampZoomScale(scale: Float, minScale: Float, maxScale: Float): Float =
    scale.coerceIn(minScale, maxScale)

/**
 * Clamps a pan [offset] for a fill-container zoom (content size ==
 * container size at scale 1, e.g. ZoomableBox video/PDF pages).
 */
internal fun clampFillOffset(offset: Offset, scale: Float, containerPx: IntSize): Offset {
    if (containerPx.width <= 0 || containerPx.height <= 0) return Offset.Zero
    val max = Offset(
        x = max(0f, containerPx.width * (scale - 1f) / 2f),
        y = max(0f, containerPx.height * (scale - 1f) / 2f),
    )
    // Exact zero (not -0.0) when an axis cannot move: keeps Offset.equals
    // stable and the rest position exactly Offset.Zero.
    val cx = if (max.x <= 0f) 0f else offset.x.coerceIn(-max.x, max.x)
    val cy = if (max.y <= 0f) 0f else offset.y.coerceIn(-max.y, max.y)
    return Offset(cx, cy)
}

/**
 * Clamps a pan [offset] for Fit content ([drawSize] at scale 1 inside
 * [boxSize], e.g. the OCR image viewer).
 */
internal fun clampFitOffset(
    offset: Offset,
    scale: Float,
    boxSize: IntSize,
    drawSize: androidx.compose.ui.geometry.Size,
): Offset {
    val max = maxPanOffset(boxSize, drawSize, scale)
    val cx = if (max.x <= 0f) 0f else offset.x.coerceIn(-max.x, max.x)
    val cy = if (max.y <= 0f) 0f else offset.y.coerceIn(-max.y, max.y)
    return Offset(cx, cy)
}
