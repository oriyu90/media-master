package com.example.ui.editor

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * v1.14.0: four-corner ("align corners") perspective selection.
 *
 * Points are 0..1 in the geometry-applied working bitmap, so the overlay and
 * the save pipeline share one coordinate space. Pure Kotlin, unit-tested.
 */
data class NormalizedPoint(val x: Float, val y: Float)

data class PerspectiveQuad(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
) {
    val points: List<NormalizedPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun withPoint(index: Int, point: NormalizedPoint): PerspectiveQuad {
        val p = points.toMutableList()
        if (index !in p.indices) return this
        p[index] = point
        return PerspectiveQuad(p[0], p[1], p[2], p[3])
    }

    fun point(index: Int): NormalizedPoint = points[index.coerceIn(0, 3)]
}

fun defaultQuad(): PerspectiveQuad = PerspectiveQuad(
    topLeft = NormalizedPoint(0f, 0f),
    topRight = NormalizedPoint(1f, 0f),
    bottomRight = NormalizedPoint(1f, 1f),
    bottomLeft = NormalizedPoint(0f, 1f),
)

private fun cross(a: NormalizedPoint, b: NormalizedPoint, c: NormalizedPoint): Float =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

/**
 * True when the four points form a convex, non-self-intersecting quad in the
 * order TL → TR → BR → BL. Dragged corners that would fold the shape are
 * rejected so `Matrix.setPolyToPoly` never receives a degenerate mapping.
 */
fun isConvexQuad(quad: PerspectiveQuad): Boolean {
    val p = quad.points
    val signs = listOf(
        cross(p[0], p[1], p[2]),
        cross(p[1], p[2], p[3]),
        cross(p[2], p[3], p[0]),
        cross(p[3], p[0], p[1]),
    )
    if (signs.any { abs(it) < 1e-6f }) return false
    val allPositive = signs.all { it > 0f }
    val allNegative = signs.all { it < 0f }
    if (!allPositive && !allNegative) return false
    return abs(signedArea(quad)) >= 0.01f
}

/** Shoelace signed area in normalized units (positive for the default quad). */
fun signedArea(quad: PerspectiveQuad): Float {
    val p = quad.points
    var sum = 0f
    for (i in p.indices) {
        val a = p[i]
        val b = p[(i + 1) % p.size]
        sum += a.x * b.y - b.x * a.y
    }
    return sum / 2f
}

/** Usable for the perspective crop: convex plus a minimum visible area. */
fun isUsableQuad(quad: PerspectiveQuad): Boolean = isConvexQuad(quad)

/** Clamp a corner inside the bitmap and return it (convexity is checked by callers). */
fun clampQuadPoint(point: NormalizedPoint): NormalizedPoint =
    NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))

/**
 * Output size for a quad mapped onto a rectangle: the average length of the
 * opposite edges, so the straightened result keeps roughly the selected
 * area's resolution instead of the source's.
 */
fun perspectiveOutputSize(quad: PerspectiveQuad, bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
    val w = bitmapWidth.coerceAtLeast(1).toFloat()
    val h = bitmapHeight.coerceAtLeast(1).toFloat()
    val p = quad.points
    fun dist(a: NormalizedPoint, b: NormalizedPoint): Float = hypot((b.x - a.x) * w, (b.y - a.y) * h)
    val top = dist(p[0], p[1])
    val bottom = dist(p[3], p[2])
    val left = dist(p[0], p[3])
    val right = dist(p[1], p[2])
    val outW = ((top + bottom) / 2f).roundToInt().coerceAtLeast(1)
    val outH = ((left + right) / 2f).roundToInt().coerceAtLeast(1)
    return outW to outH
}

/** Source corner array (8 floats) in bitmap pixels for `Matrix.setPolyToPoly`. */
fun perspectiveSourcePoints(quad: PerspectiveQuad, bitmapWidth: Int, bitmapHeight: Int): FloatArray {
    val w = bitmapWidth.toFloat()
    val h = bitmapHeight.toFloat()
    return floatArrayOf(
        quad.topLeft.x * w, quad.topLeft.y * h,
        quad.topRight.x * w, quad.topRight.y * h,
        quad.bottomRight.x * w, quad.bottomRight.y * h,
        quad.bottomLeft.x * w, quad.bottomLeft.y * h,
    )
}

/** Destination rectangle corners (0,0)-(w,0)-(w,h)-(0,h). */
fun perspectiveDestinationPoints(width: Int, height: Int): FloatArray = floatArrayOf(
    0f, 0f,
    width.toFloat(), 0f,
    width.toFloat(), height.toFloat(),
    0f, height.toFloat(),
)

/** Project a normalized point into a display rectangle (for the overlay). */
fun normalizedToDisplay(
    point: NormalizedPoint,
    displayLeft: Float,
    displayTop: Float,
    displayWidth: Float,
    displayHeight: Float,
): Pair<Float, Float> = Pair(
    displayLeft + point.x * displayWidth,
    displayTop + point.y * displayHeight,
)

/** Convert a display delta into normalized delta for a display rectangle. */
fun displayDeltaToNormalized(
    dx: Float,
    dy: Float,
    displayWidth: Float,
    displayHeight: Float,
): Pair<Float, Float> = Pair(
    if (displayWidth > 0f) dx / displayWidth else 0f,
    if (displayHeight > 0f) dy / displayHeight else 0f,
)

/** Index of the corner within [tolerance] of a normalized point, or null. */
fun hitTestQuadCorner(quad: PerspectiveQuad, x: Float, y: Float, tolerance: Float): Int? =
    hitTestQuadCorner(quad, x, y, tolerance, tolerance)

/** Anisotropic variant used by the overlay's pixel-circular touch target. */
fun hitTestQuadCorner(
    quad: PerspectiveQuad,
    x: Float,
    y: Float,
    toleranceX: Float,
    toleranceY: Float,
): Int? {
    return quad.points.indexOfFirst { point ->
        abs(point.x - x) <= toleranceX && abs(point.y - y) <= toleranceY
    }.takeIf { it >= 0 }
}
