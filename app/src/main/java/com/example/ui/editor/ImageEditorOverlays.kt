package com.example.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val OVERLAY_DIM = Color.Black.copy(alpha = 0.55f)
private val OVERLAY_LINE = Color.White
private val OVERLAY_GRID = Color.White.copy(alpha = 0.42f)

/**
 * v1.14.0: rectangular crop overlay with aspect-ratio locking.
 *
 * The frame lives in the same normalized space as [CropMath], and gestures
 * are hit-tested on a Canvas: a corner handle resizes, a drag inside moves.
 * Starting outside both is ignored (no gesture stealing).
 */
@Composable
fun CropOverlay(
    fit: ImageFit,
    cropRect: CropRect,
    ratio: Float?,
    imageAspect: Float,
    onChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fit.width <= 0f || fit.height <= 0f) return
    val density = LocalDensity.current
    val handlePx = with(density) { 28.dp.toPx() }
    val latest by rememberUpdatedState(cropRect)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(fit, ratio, imageAspect, handlePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val nx = (down.position.x - fit.left) / fit.width
                    val ny = (down.position.y - fit.top) / fit.height
                    if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return@awaitEachGesture
                    val toleranceX = (handlePx / fit.width).coerceIn(0.01f, 0.5f)
                    val toleranceY = (handlePx / fit.height).coerceIn(0.01f, 0.5f)
                    val corner = hitTestCorner(latest, nx, ny, toleranceX, toleranceY)
                    val moving = corner == null && latest.containsNormalized(nx, ny)
                    if (corner == null && !moving) return@awaitEachGesture
                    down.consume()
                    var working = latest
                    var last = down.position
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = (change.position.x - last.x) / fit.width
                        val dy = (change.position.y - last.y) / fit.height
                        last = change.position
                        working = if (corner != null) {
                            resizeCorner(working, corner, dx, dy, ratio, imageAspect)
                        } else {
                            moveRect(working, dx, dy)
                        }
                        onChange(working)
                        change.consume()
                    } while (change.pressed)
                }
            },
    ) {
        val left = fit.left + cropRect.left * fit.width
        val top = fit.top + cropRect.top * fit.height
        val right = fit.left + cropRect.right * fit.width
        val bottom = fit.top + cropRect.bottom * fit.height
        val cropW = (right - left).coerceAtLeast(0f)
        val cropH = (bottom - top).coerceAtLeast(0f)

        // Dim everything outside the selection (letterbox included).
        drawRect(OVERLAY_DIM, Offset.Zero, Size(size.width, top))
        drawRect(OVERLAY_DIM, Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(OVERLAY_DIM, Offset(0f, top), Size(left, cropH))
        drawRect(OVERLAY_DIM, Offset(right, top), Size(size.width - right, cropH))

        drawRect(OVERLAY_LINE, Offset(left, top), Size(cropW, cropH), style = Stroke(width = 2.dp.toPx()))
        if (cropW > 12.dp.toPx() && cropH > 12.dp.toPx()) {
            for (i in 1..2) {
                val x = left + cropW * i / 3f
                val y = top + cropH * i / 3f
                drawLine(OVERLAY_GRID, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                drawLine(OVERLAY_GRID, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
        }

        // L-shaped corner grips.
        val grip = minOf(with(density) { 22.dp.toPx() }, cropW / 3f, cropH / 3f)
        val stroke = 3.dp.toPx()
        fun cornerGrip(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(OVERLAY_LINE, Offset(x, y), Offset(x + dx * grip, y), strokeWidth = stroke)
            drawLine(OVERLAY_LINE, Offset(x, y), Offset(x, y + dy * grip), strokeWidth = stroke)
        }
        if (grip > 2f) {
            cornerGrip(left, top, 1f, 1f)
            cornerGrip(right, top, -1f, 1f)
            cornerGrip(right, bottom, -1f, -1f)
            cornerGrip(left, bottom, 1f, -1f)
        }
    }
}

/**
 * v1.14.0: four-corner perspective overlay. Each corner is draggable; a move
 * that would fold the quad is ignored (kept convex, so the poly-to-poly
 * mapping can never produce a broken output).
 */
@Composable
fun PerspectiveOverlay(
    fit: ImageFit,
    quad: PerspectiveQuad,
    onChange: (PerspectiveQuad) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fit.width <= 0f || fit.height <= 0f) return
    val density = LocalDensity.current
    val handlePx = with(density) { 30.dp.toPx() }
    val latest by rememberUpdatedState(quad)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(fit, handlePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val nx = (down.position.x - fit.left) / fit.width
                    val ny = (down.position.y - fit.top) / fit.height
                    val toleranceX = (handlePx / fit.width).coerceIn(0.01f, 0.5f)
                    val toleranceY = (handlePx / fit.height).coerceIn(0.01f, 0.5f)
                    val index = hitTestQuadCorner(latest, nx, ny, toleranceX, toleranceY)
                        ?: return@awaitEachGesture
                    down.consume()
                    val startQuad = latest
                    val startPoint = startQuad.point(index)
                    var last = down.position
                    var totalX = 0f
                    var totalY = 0f
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        totalX += (change.position.x - last.x) / fit.width
                        totalY += (change.position.y - last.y) / fit.height
                        last = change.position
                        val candidate = startQuad.withPoint(
                            index,
                            clampQuadPoint(NormalizedPoint(startPoint.x + totalX, startPoint.y + totalY)),
                        )
                        if (isConvexQuad(candidate)) onChange(candidate)
                        change.consume()
                    } while (change.pressed)
                }
            },
    ) {
        val points = quad.points.map {
            Offset(fit.left + it.x * fit.width, fit.top + it.y * fit.height)
        }
        // Dim outside the quad with an even-odd hole.
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        drawPath(path, OVERLAY_DIM)
        for (i in 0 until 4) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            drawLine(OVERLAY_LINE, a, b, strokeWidth = 2.dp.toPx())
        }
        // Guide lines to the matching opposite edge midpoint for aiming.
        for (point in points) {
            drawCircle(Color.White, radius = 9.dp.toPx(), center = point)
            drawCircle(Color.Black, radius = 12.dp.toPx(), center = point, style = Stroke(2.dp.toPx()))
        }
    }
}
