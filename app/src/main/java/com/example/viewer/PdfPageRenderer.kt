package com.example.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Page-at-a-time PDF rasterizer built on the platform [PdfRenderer] (already
 * used elsewhere in the app for scan page-counting/appending). Only one
 * page's [Bitmap] is held at a time by the caller — this class never keeps
 * more than the currently open [PdfRenderer.Page] in memory itself.
 */
class PdfPageRenderer private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : AutoCloseable {

    val pageCount: Int get() = runCatching { renderer.pageCount }.getOrDefault(0)

    /** Renders [pageIndex] at [targetWidthPx] wide (height follows the page aspect ratio). Null on any failure. */
    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? = runCatching {
        if (pageIndex !in 0 until renderer.pageCount) return null
        renderer.openPage(pageIndex).use { page ->
            val width = targetWidthPx.coerceAtLeast(1)
            val height = (width.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }.getOrNull()

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        /** Opens [uri] for page rendering, or null if it isn't a readable/valid PDF. */
        fun open(context: Context, uri: Uri): PdfPageRenderer? = runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            PdfPageRenderer(pfd, PdfRenderer(pfd))
        }.getOrNull()
    }
}
