package com.example.ui.editor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * v1.14.0: resolution-independent geometry for the image editor.
 *
 * The old preview used Compose `graphicsLayer` rotations while the encoder
 * used `android.graphics.Camera`; the two disagreed, and `Camera`'s fixed
 * pixel-space camera distance made a 1024 px preview tilt differently from a
 * 4000 px export. The tilt is now a pure projection whose camera distance is
 * proportional to the long edge, so preview and save share one result.
 *
 * Points use the image's own pixel coordinate system; the returned arrays are
 * in TL, TR, BR, BL order (8 floats).
 */
private const val CAMERA_DISTANCE_FACTOR = 2.0f
private const val ANGLE_EPSILON = 0.001f

/** Source rectangle corners (TL, TR, BR, BL). */
fun sourceCorners(width: Float, height: Float): FloatArray = floatArrayOf(
    0f, 0f,
    width, 0f,
    width, height,
    0f, height,
)

/**
 * Corners of the image after pitch (X) and yaw (Y) rotation, projected with a
 * perspective divide. At zero angles this returns [sourceCorners] exactly.
 */
fun tiltProjectedCorners(width: Float, height: Float, tiltX: Float, tiltY: Float): FloatArray {
    if (abs(tiltX) < ANGLE_EPSILON && abs(tiltY) < ANGLE_EPSILON) return sourceCorners(width, height)
    val cx = width / 2f
    val cy = height / 2f
    val distance = (CAMERA_DISTANCE_FACTOR * max(width, height)).coerceAtLeast(1f)
    val rx = (-tiltX) * PI.toFloat() / 180f
    val ry = tiltY * PI.toFloat() / 180f
    val sinX = sin(rx)
    val cosX = cos(rx)
    val sinY = sin(ry)
    val cosY = cos(ry)
    val centered = floatArrayOf(-cx, -cy, cx, -cy, cx, cy, -cx, cy)
    val out = FloatArray(8)
    for (i in 0 until 4) {
        val x = centered[i * 2]
        val y = centered[i * 2 + 1]
        // Pitch around X: (y, z) rotate; the source plane has z = 0.
        val y1 = y * cosX
        val z1 = y * sinX
        // Yaw around Y: (x, z) rotate.
        val x2 = x * cosY + z1 * sinY
        val z2 = -x * sinY + z1 * cosY
        // Keep the divisor positive even for extreme slider values.
        val divisor = (distance - z2).coerceAtLeast(distance * 0.25f)
        val scale = distance / divisor
        out[i * 2] = x2 * scale + cx
        out[i * 2 + 1] = y1 * scale + cy
    }
    return out
}

/**
 * Final corners after tilt, horizontal flip and Z rotation (in the same order
 * the encoder applies them). Pure: shared by preview, save and tests.
 */
fun geometryCorners(width: Float, height: Float, params: GeometryParams): FloatArray {
    var quad = tiltProjectedCorners(width, height, params.tiltX, params.tiltY)
    if (params.flipHorizontal) {
        for (i in 0 until 4) {
            quad[i * 2] = width - quad[i * 2]
        }
    }
    if (abs(params.rotateZ) >= ANGLE_EPSILON) {
        val cx = width / 2f
        val cy = height / 2f
        val radians = params.rotateZ * PI.toFloat() / 180f
        val cosA = cos(radians)
        val sinA = sin(radians)
        for (i in 0 until 4) {
            val dx = quad[i * 2] - cx
            val dy = quad[i * 2 + 1] - cy
            quad[i * 2] = cx + dx * cosA - dy * sinA
            quad[i * 2 + 1] = cy + dx * sinA + dy * cosA
        }
    }
    return quad
}

/** Bounding box of [geometryCorners] as left, top, right, bottom. */
fun geometryBounds(width: Float, height: Float, params: GeometryParams): FloatArray {
    val quad = geometryCorners(width, height, params)
    var minX = quad[0]
    var maxX = quad[0]
    var minY = quad[1]
    var maxY = quad[1]
    for (i in 1 until 4) {
        minX = minOf(minX, quad[i * 2])
        maxX = maxOf(maxX, quad[i * 2])
        minY = minOf(minY, quad[i * 2 + 1])
        maxY = maxOf(maxY, quad[i * 2 + 1])
    }
    return floatArrayOf(minX, minY, maxX, maxY)
}
