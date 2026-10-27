package com.example.viewer

import com.example.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.13.0: pins the viewer pager ordering contract. The viewer must page
 * through the same ordered list the user sees; unknown/deep-link launches keep
 * the v1.12.2 global-list fallback. Pure JUnit — no Compose/Android.
 */
class ViewerOrderTest {

    private fun file(path: String, mime: String): MediaFile =
        MediaFile(0, path.substringAfterLast('/'), path, 0, mime, 0)

    private val imgA = file("/s/a.jpg", "image/jpeg")
    private val imgB = file("/s/b.jpg", "image/jpeg")
    private val imgC = file("/s/c.png", "image/png")
    private val vid = file("/s/d.mp4", "video/mp4")
    private val audA = file("/s/a.mp3", "audio/mpeg")
    private val audB = file("/s/b.flac", "audio/flac")
    private val pdf = file("/s/doc.pdf", "application/pdf")
    private val txt = file("/s/notes.txt", "text/plain")

    @Test
    fun `preserves the given display order`() {
        val all = listOf(imgA, imgB, imgC, vid)
        val resolved = ViewerOrder.resolve(all, listOf(imgC.path, vid.path, imgA.path, imgB.path), imgA.path)
        assertEquals(listOf(imgC.path, vid.path, imgA.path, imgB.path), resolved.map { it.path })
    }

    @Test
    fun `skips missing entries but keeps the rest`() {
        val all = listOf(imgA, imgB, imgC)
        val resolved = ViewerOrder.resolve(all, listOf(imgB.path, "/s/deleted.jpg", imgA.path), imgA.path)
        assertEquals(listOf(imgB.path, imgA.path), resolved.map { it.path })
    }

    @Test
    fun `target absent from the order falls back to the media family`() {
        val all = listOf(imgA, imgB, imgC, vid, pdf)
        val resolved = ViewerOrder.resolve(all, listOf(imgB.path, vid.path), imgA.path)
        // v1.12.2 fallback: every image/video in global order, target included.
        assertEquals(listOf(imgA.path, imgB.path, imgC.path, vid.path), resolved.map { it.path })
    }

    @Test
    fun `audio family stays audio-only and preserves order`() {
        val all = listOf(imgA, audA, audB, vid)
        val resolved = ViewerOrder.resolve(all, listOf(imgA.path, audB.path, "/s/missing.mp3", audA.path), audA.path)
        assertEquals(listOf(audB.path, audA.path), resolved.map { it.path })
        assertEquals(listOf(audA.path, audB.path), ViewerOrder.resolve(all, null, audA.path).map { it.path })
    }

    @Test
    fun `non-media target resolves to the single file`() {
        val all = listOf(imgA, pdf, txt)
        assertEquals(listOf(pdf.path), ViewerOrder.resolve(all, null, pdf.path).map { it.path })
        // Even a prepared list cannot make a document page through siblings.
        assertEquals(listOf(pdf.path), ViewerOrder.resolve(all, listOf(txt.path, pdf.path), pdf.path).map { it.path })
    }

    @Test
    fun `target not found returns an empty list`() {
        assertTrue(ViewerOrder.resolve(listOf(imgA, imgB), listOf(imgA.path), "/s/gone.jpg").isEmpty())
        assertTrue(ViewerOrder.resolve(emptyList(), null, imgA.path).isEmpty())
    }
}
