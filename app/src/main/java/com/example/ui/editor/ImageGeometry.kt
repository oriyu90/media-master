package com.example.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v1.14.0: one geometry pipeline shared by the live preview and the save.
 *
 * Before this, the preview used `graphicsLayer` (rotation/clip differently
 * from the encoder) while [com.example.ui.ImageEditorScreen.saveImage] used
 * `Bitmap.createBitmap` with the *original* width/height — a 90° rotation was
 * silently cropped in the output. Both paths now call [applyGeometry], so what
 * the user sees is what gets saved.
 */
data class GeometryParams(
    val flipHorizontal: Boolean = false,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val rotateZ: Float = 0f,
) {
    val isIdentity: Boolean
        get() = !flipHorizontal &&
            abs(tiltX) < EPSILON &&
            abs(tiltY) < EPSILON &&
            abs(rotateZ) < EPSILON

    companion object {
        private const val EPSILON = 0.001f
    }
}

/** Limit for the *working* bitmap decoded for editing (memory safety). */
const val MAX_EDIT_EDGE = 4096

/** Long edge of the preview copy rendered while dragging sliders. */
const val PREVIEW_EDGE = 1024

/**
 * Decode a bitmap sampled so its long edge is at most [maxEdge]. Huge phone
 * photos (48 MP+) would otherwise allocate a 190 MB ARGB buffer and crash.
 */
@WorkerThread
fun decodeSampledBitmap(context: Context, uri: Uri, maxEdge: Int = MAX_EDIT_EDGE): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

/** Downscale for the preview copy; returns [source] unchanged when small. */
@WorkerThread
fun downscaleBitmap(source: Bitmap, maxEdge: Int = PREVIEW_EDGE): Bitmap {
    val longEdge = max(source.width, source.height)
    if (longEdge <= maxEdge) return source
    val scale = maxEdge.toFloat() / longEdge
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    return runCatching { Bitmap.createScaledBitmap(source, w, h, true) }.getOrDefault(source)
}

/**
 * Matrix mapping the source rectangle onto the final quad produced by
 * [geometryCorners]. A 4-point `setPolyToPoly` handles projective tilt,
 * mirroring and rotation with one code path; because the quad is computed in
 * normalized, resolution-proportional space the preview and the export match.
 */
fun geometryMatrix(width: Int, height: Int, params: GeometryParams): Matrix {
    val matrix = Matrix()
    if (params.isIdentity) return matrix
    val w = width.toFloat()
    val h = height.toFloat()
    val mapped = matrix.setPolyToPoly(
        sourceCorners(w, h), 0,
        geometryCorners(w, h, params), 0,
        4,
    )
    return if (mapped) matrix else Matrix()
}

/** Bounds of the transformed corners, used to size the output bitmap. */
fun transformedBounds(width: Int, height: Int, params: GeometryParams): FloatArray =
    geometryBounds(width.toFloat(), height.toFloat(), params)

/**
 * Apply [params] to [source]. Returns [source] itself for the identity case
 * (callers must therefore not recycle the result blindly).
 */
@WorkerThread
fun applyGeometry(source: Bitmap, params: GeometryParams): Bitmap {
    if (params.isIdentity) return source
    val width = source.width
    val height = source.height
    val matrix = geometryMatrix(width, height, params)
    val bounds = transformedBounds(width, height, params)
    val rawW = ceil(bounds[2] - bounds[0]).toInt().coerceAtLeast(1)
    val rawH = ceil(bounds[3] - bounds[1]).toInt().coerceAtLeast(1)
    // Rotation/tilt enlarge the bounding box (a 45° rotation of a 4096px
    // image is ~5800px). Clamp the output too, not just the decode, so the
    // save cannot allocate a >100 MB ARGB bitmap on top of the source.
    var outW = rawW
    var outH = rawH
    val maxEdge = max(rawW, rawH)
    val scale = if (maxEdge > MAX_EDIT_EDGE) {
        MAX_EDIT_EDGE.toFloat() / maxEdge
    } else {
        1f
    }
    if (scale < 1f) {
        outW = (rawW * scale).roundToInt().coerceAtLeast(1)
        outH = (rawH * scale).roundToInt().coerceAtLeast(1)
    }
    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    // The editor/viewer background is black; use the same for the corners
    // exposed by a tilt so the preview and the saved JPEG agree.
    canvas.drawColor(Color.BLACK)
    matrix.postTranslate(-bounds[0], -bounds[1])
    if (scale < 1f) matrix.postScale(scale, scale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)
    return output
}

/**
 * Bake the selected quad into a new bitmap: the quad's four corners become
 * the image's four corners (Google-Photos-style "align corners").
 * Returns null when the quad is degenerate or the mapping fails.
 */
@WorkerThread
fun applyPerspective(source: Bitmap, params: GeometryParams, quad: PerspectiveQuad): Bitmap? {
    if (!isUsableQuad(quad)) return null
    var base: Bitmap? = null
    return try {
        val geometry = applyGeometry(source, params)
        if (geometry !== source) base = geometry
        val (outW, outH) = perspectiveOutputSize(quad, geometry.width, geometry.height)
        val matrix = Matrix()
        val mapped = matrix.setPolyToPoly(
            perspectiveSourcePoints(quad, geometry.width, geometry.height), 0,
            perspectiveDestinationPoints(outW, outH), 0,
            4,
        )
        if (!mapped) return null
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(geometry, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        output
    } catch (_: Throwable) {
        null
    } finally {
        // `base` is our own intermediate; the caller still owns `source`.
        base?.let { if (!it.isRecycled) it.recycle() }
    }
}

/**
 * Save the edited image as a new JPEG in `Pictures/Edited` (the original is
 * never modified). Runs on IO and returns a [Result] so the UI can keep the
 * screen open and surface a message instead of silently closing.
 */
suspend fun saveEditedImage(
    context: Context,
    source: Bitmap,
    params: GeometryParams,
    crop: CropRect,
    colorMatrix: android.graphics.ColorMatrix?,
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val owned = mutableListOf<Bitmap>()
        try {
            val geometry = applyGeometry(source, params)
            if (geometry !== source) owned += geometry
            val pixels = crop.toPixelRect(geometry.width, geometry.height)
            val full = pixels.left == 0 && pixels.top == 0 &&
                pixels.right == geometry.width && pixels.bottom == geometry.height
            // One pass for the colour path: draw the crop rect straight into
            // the colour target instead of allocating a separate cropped
            // bitmap (saves a full-resolution ARGB buffer at save time).
            val output: Bitmap = if (colorMatrix != null) {
                val target = Bitmap.createBitmap(pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
                owned += target
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
                Canvas(target).drawBitmap(
                    geometry,
                    Rect(pixels.left, pixels.top, pixels.right, pixels.bottom),
                    Rect(0, 0, pixels.width, pixels.height),
                    paint,
                )
                target
            } else if (full) {
                geometry
            } else {
                val cropped = Bitmap.createBitmap(
                    geometry,
                    pixels.left,
                    pixels.top,
                    pixels.width,
                    pixels.height,
                )
                owned += cropped
                cropped
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Edited_Image_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/Edited",
                    )
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert returned null")
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    output.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                } ?: false
            }.getOrDefault(false)
            if (!saved) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw IOException("Could not encode the edited image")
            }
            uri
        } finally {
            // Only bitmaps we allocated; the caller still owns `source`.
            owned.forEach { bitmap ->
                if (!bitmap.isRecycled) runCatching { bitmap.recycle() }
            }
        }
    }
}
