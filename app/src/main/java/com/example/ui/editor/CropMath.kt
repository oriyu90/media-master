package com.example.ui.editor

import androidx.annotation.StringRes
import com.example.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v1.14.0: normalized crop rectangle for the image editor.
 *
 * Coordinates are 0..1 relative to the *geometry-applied* working bitmap
 * (the same bitmap the preview shows), so the overlay, the aspect-ratio
 * chips and the save pipeline all agree. Pure Kotlin: unit-tested on the JVM
 * without Android or Compose.
 */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/** The four draggable corners of the crop frame. */
enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

/**
 * Aspect-ratio presets. [ratio] is width/height in *pixels*; [FREE] and
 * [ORIGINAL] are resolved against the current bitmap aspect at use time.
 */
enum class AspectOption(@StringRes val labelRes: Int, val ratio: Float?) {
    FREE(R.string.aspect_free, null),
    ORIGINAL(R.string.aspect_original, null),
    SQUARE(R.string.aspect_square, 1f),
    RATIO_4_3(R.string.aspect_4_3, 4f / 3f),
    RATIO_3_4(R.string.aspect_3_4, 3f / 4f),
    RATIO_16_9(R.string.aspect_16_9, 16f / 9f),
    RATIO_9_16(R.string.aspect_9_16, 9f / 16f),
    RATIO_3_2(R.string.aspect_3_2, 3f / 2f),
    RATIO_2_3(R.string.aspect_2_3, 2f / 3f),
}

/**
 * Resolve the preset to a pixel width/height ratio. [imageAspect] is the
 * working bitmap's width/height; [ORIGINAL] therefore returns the source
 * ratio, [FREE] returns null (unlocked).
 */
fun AspectOption.resolveRatio(imageAspect: Float): Float? = when (this) {
    AspectOption.FREE -> null
    AspectOption.ORIGINAL -> imageAspect.takeIf { it.isFinite() && it > 0f }
    else -> ratio
}

/** True when [imageAspect] is usable as a pixel aspect ratio. */
fun isValidAspect(imageAspect: Float): Boolean = imageAspect.isFinite() && imageAspect > 0f

private fun CropRect.normalized(): CropRect = CropRect(
    left = min(left, right),
    top = min(top, bottom),
    right = max(left, right),
    bottom = max(top, bottom),
)

private fun CropRect.clampedToBounds(minSize: Float): CropRect {
    val l = left.coerceIn(0f, 1f)
    val t = top.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    val b = bottom.coerceIn(0f, 1f)
    val safeRight = max(r, min(1f, l + minSize))
    val safeBottom = max(b, min(1f, t + minSize))
    val safeLeft = min(l, safeRight - minSize).coerceAtLeast(0f)
    val safeTop = min(t, safeBottom - minSize).coerceAtLeast(0f)
    return CropRect(safeLeft, safeTop, safeRight, safeBottom)
}

/**
 * Largest rectangle with [ratio] (pixels) that fits the image bounds,
 * centred on [current]'s centre and then pushed fully inside 0..1.
 *
 * Used when the user picks an aspect chip: a full-frame 16:9 band on a
 * 4:3 photo, for example, or a centred square.
 */
fun fitAspect(current: CropRect, ratio: Float, imageAspect: Float): CropRect {
    if (!isValidAspect(ratio) || !isValidAspect(imageAspect)) return current.normalized()
    val normalizedRatio = ratio / imageAspect // width / height in normalized units
    var w = 1f
    var h = w / normalizedRatio
    if (h > 1f) {
        h = 1f
        w = h * normalizedRatio
    }
    val cx = current.centerX.coerceIn(w / 2f, 1f - w / 2f)
    val cy = current.centerY.coerceIn(h / 2f, 1f - h / 2f)
    return CropRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
}

/**
 * Drag one corner by a normalized delta. With [ratio] null the corner moves
 * freely (clamped to bounds and [minSize]); with a ratio the opposite corner
 * is the anchor and the frame keeps the exact pixel ratio.
 */
fun resizeCorner(
    rect: CropRect,
    corner: CropCorner,
    dx: Float,
    dy: Float,
    ratio: Float?,
    imageAspect: Float,
    minSize: Float = 0.05f,
): CropRect {
    val current = rect.normalized()
    val anchorX: Float
    val anchorY: Float
    val cornerX: Float
    val cornerY: Float
    when (corner) {
        CropCorner.TOP_LEFT -> { cornerX = current.left; cornerY = current.top; anchorX = current.right; anchorY = current.bottom }
        CropCorner.TOP_RIGHT -> { cornerX = current.right; cornerY = current.top; anchorX = current.left; anchorY = current.bottom }
        CropCorner.BOTTOM_RIGHT -> { cornerX = current.right; cornerY = current.bottom; anchorX = current.left; anchorY = current.top }
        CropCorner.BOTTOM_LEFT -> { cornerX = current.left; cornerY = current.bottom; anchorX = current.right; anchorY = current.top }
    }
    val pointerX = (cornerX + dx).coerceIn(0f, 1f)
    val pointerY = (cornerY + dy).coerceIn(0f, 1f)

    if (ratio == null || !isValidAspect(ratio) || !isValidAspect(imageAspect)) {
        val l = min(pointerX, anchorX).coerceIn(0f, 1f - minSize)
        val r = max(pointerX, anchorX).coerceIn(minSize, 1f)
        val t = min(pointerY, anchorY).coerceIn(0f, 1f - minSize)
        val b = max(pointerY, anchorY).coerceIn(minSize, 1f)
        return CropRect(
            left = min(l, r - minSize),
            top = min(t, b - minSize),
            right = max(r, l + minSize),
            bottom = max(b, t + minSize),
        ).clampedToBounds(minSize)
    }

    val k = ratio / imageAspect // desired normalized width / height
    if (!k.isFinite() || k <= 0f) return current
    val signX = if (pointerX >= anchorX) 1f else -1f
    val signY = if (pointerY >= anchorY) 1f else -1f
    val proposedW = abs(pointerX - anchorX)
    val proposedH = abs(pointerY - anchorY)
    // Follow whichever axis the finger moved further, projected into width.
    var w = max(proposedW, proposedH * k)
    val maxWByX = if (signX > 0f) 1f - anchorX else anchorX
    val maxHByY = if (signY > 0f) 1f - anchorY else anchorY
    w = min(w, maxWByX)
    w = min(w, maxHByY * k)
    val minW = max(minSize, minSize * k)
    w = max(w, minW)
    // The anchor may sit at the edge; never exceed the remaining room.
    w = min(w, max(maxWByX, 0f))
    w = min(w, max(maxHByY * k, 0f))
    if (w < minW) return current
    val h = w / k
    val newCornerX = anchorX + signX * w
    val newCornerY = anchorY + signY * h
    return CropRect(
        left = min(anchorX, newCornerX),
        top = min(anchorY, newCornerY),
        right = max(anchorX, newCornerX),
        bottom = max(anchorY, newCornerY),
    ).clampedToBounds(minSize)
}

/** Translate the frame without resizing; it stops at the image bounds. */
fun moveRect(rect: CropRect, dx: Float, dy: Float): CropRect {
    val current = rect.normalized()
    val w = current.width
    val h = current.height
    val left = (current.left + dx).coerceIn(0f, max(0f, 1f - w))
    val top = (current.top + dy).coerceIn(0f, max(0f, 1f - h))
    return CropRect(left, top, left + w, top + h)
}

/** True when the rectangle has a positive, non-degenerate area. */
fun isUsableCrop(rect: CropRect): Boolean =
    rect.width.isFinite() && rect.height.isFinite() &&
        rect.width > 0.001f && rect.height > 0.001f

/** Integer pixel rectangle (exclusive right/bottom) clamped to the bitmap. */
data class CropPixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

fun CropRect.toPixelRect(bitmapWidth: Int, bitmapHeight: Int): CropPixelRect {
    val w = bitmapWidth.coerceAtLeast(1)
    val h = bitmapHeight.coerceAtLeast(1)
    val l = (left * w).roundToInt().coerceIn(0, w - 1)
    val t = (top * h).roundToInt().coerceIn(0, h - 1)
    val r = (right * w).roundToInt().coerceIn(l + 1, w)
    val b = (bottom * h).roundToInt().coerceIn(t + 1, h)
    return CropPixelRect(l, t, r, b)
}

/** Hit-test a normalized point against the four corners (same tolerance on both axes). */
fun hitTestCorner(rect: CropRect, x: Float, y: Float, tolerance: Float): CropCorner? =
    hitTestCorner(rect, x, y, tolerance, tolerance)

/**
 * Anisotropic hit test: the overlay converts its 28 dp touch radius to
 * separate normalized X/Y tolerances so the touch target stays circular in
 * pixels even when the displayed image is not square.
 */
fun hitTestCorner(
    rect: CropRect,
    x: Float,
    y: Float,
    toleranceX: Float,
    toleranceY: Float,
): CropCorner? {
    val current = rect.normalized()
    val corners = listOf(
        CropCorner.TOP_LEFT to (current.left to current.top),
        CropCorner.TOP_RIGHT to (current.right to current.top),
        CropCorner.BOTTOM_RIGHT to (current.right to current.bottom),
        CropCorner.BOTTOM_LEFT to (current.left to current.bottom),
    )
    return corners.firstOrNull { (_, point) ->
        abs(point.first - x) <= toleranceX && abs(point.second - y) <= toleranceY
    }?.first
}

/** Display rectangle of a `ContentScale.Fit` image inside a container. */
data class ImageFit(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val aspect: Float get() = if (height > 0f) width / height else 1f
}

fun fitImageRect(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
): ImageFit {
    if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
        return ImageFit(0f, 0f, 0f, 0f)
    }
    val scale = min(containerWidth / imageWidth, containerHeight / imageHeight)
    val w = imageWidth * scale
    val h = imageHeight * scale
    return ImageFit(
        left = (containerWidth - w) / 2f,
        top = (containerHeight - h) / 2f,
        width = w,
        height = h,
    )
}

/** True when a normalized point lies inside the rectangle. */
fun CropRect.containsNormalized(x: Float, y: Float): Boolean =
    x >= left && x <= right && y >= top && y <= bottom

/**
 * Keep a locked-aspect frame when the working bitmap's aspect changes (tilt
 * bounds or a rotation). Unlike [fitAspect] this never jumps to the maximum
 * frame: the current height is kept and only the width is adjusted to the
 * new normalized ratio, centred on the old frame and clamped to bounds.
 */
fun rescaleToAspect(
    rect: CropRect,
    ratio: Float,
    imageAspect: Float,
    minSize: Float = 0.05f,
): CropRect {
    if (!isValidAspect(ratio) || !isValidAspect(imageAspect)) return rect.normalized()
    val k = ratio / imageAspect // normalized width / height
    if (!k.isFinite() || k <= 0f) return rect.normalized()
    val current = rect.normalized()
    var h = current.height.coerceAtLeast(minSize)
    var w = h * k
    if (w > 1f) {
        w = 1f
        h = w / k
    }
    if (h > 1f) {
        h = 1f
        w = h * k
    }
    if (w < minSize) {
        w = minSize
        h = w / k
    }
    if (h < minSize) {
        h = minSize
        w = h * k
    }
    if (w > 1f || h > 1f) return fitAspect(current, ratio, imageAspect)
    val cx = current.centerX.coerceIn(w / 2f, 1f - w / 2f)
    val cy = current.centerY.coerceIn(h / 2f, 1f - h / 2f)
    return CropRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
}
