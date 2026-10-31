package com.example.external

import android.net.Uri
import com.example.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v2.0.0: external ACTION_VIEW media. Paging is enabled only when the incoming
 * URI can be identified as a file visible in the Library.
 */
@RunWith(RobolectricTestRunner::class)
class ExternalMediaTest {

    private fun media(
        id: Long,
        path: String,
        mime: String = "image/jpeg",
        date: Long = 0L,
        uri: String? = null,
    ) = MediaFile(
        id = id,
        name = path.substringAfterLast('/'),
        path = path,
        size = 100,
        mimeType = mime,
        dateModified = date,
        contentUri = uri?.let(Uri::parse),
    )

    private fun info(
        uri: String = "content://other/1",
        name: String = "photo.jpg",
        size: Long = 100,
        path: String? = null,
        id: Long = -1,
        volume: String? = null,
        mime: String = "image/jpeg",
    ) = ExternalMediaInfo(uri, mime, name, size, path, id, volume)

    @Test
    fun `matches by the exact content uri`() {
        val target = media(7, "/storage/a.jpg", uri = "content://media/external/images/media/7")
        val files = listOf(media(8, "/storage/b.jpg"), target)
        assertSame(target, matchLibraryFile(files, info(uri = "content://media/external/images/media/7")))
    }

    @Test
    fun `matches a file uri by path`() {
        val target = media(7, "/storage/emulated/0/Pictures/a.jpg")
        val files = listOf(media(8, "/storage/emulated/0/Pictures/b.jpg"), target)
        assertSame(
            target,
            matchLibraryFile(
                files,
                info(uri = "file:///storage/emulated/0/Pictures/a.jpg", path = "/storage/emulated/0/Pictures/a.jpg"),
            ),
        )
    }

    @Test
    fun `matches a MediaStore id only inside the same volume`() {
        val target = media(
            42,
            "/storage/emulated/0/DCIM/Camera/a.jpg",
            uri = "content://media/external_primary/file/42",
        )
        val files = listOf(media(7, "/storage/emulated/0/DCIM/Camera/other.jpg"), target)
        assertSame(
            target,
            matchLibraryFile(
                files,
                info(id = 42, volume = "external_primary", uri = "content://media/external/images/media/42"),
            ),
        )
    }

    @Test
    fun `same id on another volume is not the same file`() {
        val target = media(
            42,
            "/storage/emulated/0/DCIM/Camera/a.jpg",
            uri = "content://media/external_primary/file/42",
        )
        val files = listOf(target)
        assertNull(
            matchLibraryFile(
                files,
                info(uri = "content://media/1234-5678/images/media/42", id = 42, volume = "1234-5678"),
            ),
        )
    }

    @Test
    fun `a third-party numeric path segment is never a MediaStore id`() {
        val files = listOf(media(42, "/storage/emulated/0/DCIM/Camera/a.jpg"))
        assertNull(
            matchLibraryFile(
                files,
                info(uri = "content://com.whatsapp.provider.media/item/42", id = 42, volume = null),
            ),
        )
    }

    @Test
    fun `name and size alone do not create a false match`() {
        val files = listOf(media(7, "/storage/a.jpg"))
        assertNull(
            matchLibraryFile(
                files,
                info(uri = "content://chat/attachment", name = "a.jpg", size = 100, id = -1),
            ),
        )
    }

    @Test
    fun `unknown id is ignored`() {
        val files = listOf(media(7, "/storage/a.jpg"))
        assertNull(matchLibraryFile(files, info(id = -1)))
        assertNull(matchLibraryFile(files, info(id = 0)))
    }

    @Test
    fun `library order keeps images and videos oldest first and hides excluded folders`() {
        val files = listOf(
            media(1, "/storage/Pictures/late.jpg", date = 300),
            media(2, "/storage/Pictures/early.jpg", date = 100),
            media(3, "/storage/Pictures/clip.mp4", mime = "video/mp4", date = 200),
            media(4, "/storage/Hidden/hidden.jpg", date = 50),
            media(5, "/storage/Documents/readme.txt", mime = "text/plain", date = 25),
        )
        val order = libraryMediaOrder(files, setOf("/storage/Hidden"))
        assertEquals(listOf(2L, 3L, 1L), order.map { it.id })
    }

    @Test
    fun `external pager is the whole library order when matched`() {
        val a = media(1, "/storage/a.jpg", date = 100)
        val b = media(2, "/storage/b.jpg", date = 200)
        val incoming = media(-1, "content://other/x")
        val list = externalMediaList(listOf(a, b), a, incoming)
        assertEquals(listOf(a, b), list)
    }

    @Test
    fun `external pager is one page when the file is not in the library`() {
        val a = media(1, "/storage/a.jpg")
        val incoming = media(-1, "content://other/x")
        val list = externalMediaList(listOf(a), null, incoming)
        assertEquals(listOf(incoming), list)
    }

    @Test
    fun `media id parsing accepts numeric and documents-provider segments`() {
        assertEquals(123L, parseMediaId("123"))
        assertEquals(123L, parseMediaId("image:123"))
        assertEquals(123L, parseMediaId("video:123"))
        assertEquals(-1L, parseMediaId("image%3A123"))
        assertEquals(-1L, parseMediaId(null))
    }
}
