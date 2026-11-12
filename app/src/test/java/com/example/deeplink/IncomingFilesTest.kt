package com.example.deeplink

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * WP3 pure helpers: duplicate naming, path-traversal safety and the share
 * intent parser. Robolectric is used only because [Intent] / [Uri] /
 * [ClipData] are Android classes; no Activity or provider is involved.
 */
@RunWith(RobolectricTestRunner::class)
class IncomingFilesTest {

    // ---------- uniqueName ----------

    @Test
    fun uniqueName_freeName_isReturnedAsIs() {
        assertEquals("photo.jpg", IncomingFiles.uniqueName("photo.jpg") { false })
    }

    @Test
    fun uniqueName_collision_appendsCounterBeforeExtension() {
        val taken = setOf("photo.jpg", "photo (1).jpg")
        assertEquals("photo (2).jpg", IncomingFiles.uniqueName("photo.jpg") { it in taken })
    }

    @Test
    fun uniqueName_firstCollision_usesOne() {
        assertEquals("photo (1).jpg", IncomingFiles.uniqueName("photo.jpg") { it == "photo.jpg" })
    }

    @Test
    fun uniqueName_withoutExtension_appendsCounterAtEnd() {
        assertEquals("README (1)", IncomingFiles.uniqueName("README") { it == "README" })
    }

    @Test
    fun uniqueName_leadingDotFile_isTreatedAsExtensionless() {
        // `.nomedia` must never become ` (1).nomedia`.
        assertEquals(".nomedia (1)", IncomingFiles.uniqueName(".nomedia") { it == ".nomedia" })
    }

    @Test
    fun uniqueName_trailingDotName_isTreatedAsExtensionless() {
        // The trailing dot is trimmed, so the free name is "weird"; once that
        // is taken the counter goes at the end ("weird (1)"), never
        // "weird (1)." or " (1).weird".
        assertEquals("weird", IncomingFiles.uniqueName("weird.") { false })
        assertEquals("weird (1)", IncomingFiles.uniqueName("weird.") { it == "weird" })
    }

    @Test
    fun uniqueName_multiPartExtension_keepsOnlyTheLastPart() {
        assertEquals("archive.tar (1).gz", IncomingFiles.uniqueName("archive.tar.gz") { it == "archive.tar.gz" })
    }

    @Test
    fun uniqueName_longName_isCappedButKeepsExtension() {
        val stem = "a".repeat(300)
        val result = IncomingFiles.uniqueName("$stem.jpg") { false }
        assertTrue("length ${result.length}", result.length <= 100)
        assertTrue(result.endsWith(".jpg"))
        // A collision on the truncated name still gets a counter.
        val collided = IncomingFiles.uniqueName("$stem.jpg") { it == result }
        assertTrue(collided.endsWith(".jpg"))
        assertTrue(collided.contains(" (1)"))
        assertTrue(collided.length <= 104)
    }

    @Test
    fun uniqueName_pathTraversal_isReducedToBasename() {
        assertEquals("evil.txt", IncomingFiles.uniqueName("../../evil.txt") { false })
        assertEquals("evil.txt", IncomingFiles.uniqueName("..\\..\\evil.txt") { false })
    }

    @Test
    fun uniqueName_unusableName_fallsBack() {
        assertEquals(IncomingFiles.FALLBACK_NAME, IncomingFiles.uniqueName("..") { false })
        assertEquals(IncomingFiles.FALLBACK_NAME, IncomingFiles.uniqueName("") { false })
        assertEquals(IncomingFiles.FALLBACK_NAME, IncomingFiles.uniqueName("/") { false })
    }

    @Test
    fun uniqueName_reservedCharacters_areReplaced() {
        assertEquals("a_b_c.txt", IncomingFiles.uniqueName("a:b?c.txt") { false })
    }

    @Test
    fun uniqueName_manyCollisions_keepsCounting() {
        val taken = (0..30).map { if (it == 0) "f.png" else "f ($it).png" }.toSet()
        assertEquals("f (31).png", IncomingFiles.uniqueName("f.png") { it in taken })
    }

    // ---------- resolveDestination ----------

    @Test
    fun resolveDestination_staysInsideDestinationDirectory() {
        val dir = File("/tmp/dest")
        assertEquals(File(dir, "evil.txt"), IncomingFiles.resolveDestination(dir, "../../evil.txt"))
        assertEquals(File(dir, "a.txt"), IncomingFiles.resolveDestination(dir, "/etc/a.txt"))
        // A bare ".." is unusable and falls back to the fixed name.
        assertEquals(File(dir, IncomingFiles.FALLBACK_NAME), IncomingFiles.resolveDestination(dir, ".."))
    }

    // ---------- sanitizeName / extensionOf ----------

    @Test
    fun sanitizeName_trimsTrailingDotsAndSpaces() {
        assertEquals("name", IncomingFiles.sanitizeName("name. . "))
        assertNull(IncomingFiles.sanitizeName(null))
        assertNull(IncomingFiles.sanitizeName("   "))
    }

    @Test
    fun extensionOf_reportsLowercaseExtension() {
        assertEquals("jpg", IncomingFiles.extensionOf("A.JPG"))
        assertEquals("", IncomingFiles.extensionOf("README"))
        assertEquals("", IncomingFiles.extensionOf(".nomedia"))
    }

    // ---------- SendRequest.parse ----------

    private fun uri(s: String) = Uri.parse(s)

    @Test
    fun sendRequest_singleStream_isParsed() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri("content://p/img/1"))
        }
        val r = SendRequest.parse(i)
        assertEquals(listOf(uri("content://p/img/1")), r?.uris)
        assertEquals("image/jpeg", r?.mimeType)
        assertEquals(false, r?.multiple)
        assertEquals(1, r?.count)
    }

    @Test
    fun sendRequest_multipleStreams_areParsedInOrder() {
        val list = arrayListOf(uri("content://p/a"), uri("content://p/b"), uri("content://p/c"))
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
        }
        val r = SendRequest.parse(i)
        assertEquals(list, r?.uris)
        assertEquals(true, r?.multiple)
        assertEquals(3, r?.count)
    }

    @Test
    fun sendRequest_clipDataOnly_isParsed() {
        val clip = ClipData.newPlainText("share", "x")
        clip.addItem(ClipData.Item(uri("content://p/x")))
        clip.addItem(ClipData.Item(uri("content://p/y")))
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            clipData = clip
        }
        assertEquals(listOf(uri("content://p/x"), uri("content://p/y")), SendRequest.parse(i)?.uris)
    }

    @Test
    fun sendRequest_textOnly_isNull() {
        // Sharing a URL or a quote is not a file hand-off.
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/article")
        }
        assertNull(SendRequest.parse(i))
    }

    @Test
    fun sendRequest_textWithStream_keepsOnlyTheStream() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com")
            putExtra(Intent.EXTRA_STREAM, uri("content://p/doc"))
        }
        assertEquals(listOf(uri("content://p/doc")), SendRequest.parse(i)?.uris)
    }

    @Test
    fun sendRequest_emptyClipData_isNull() {
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            clipData = ClipData.newPlainText("empty", "")
        }
        assertNull(SendRequest.parse(i))
    }

    @Test
    fun sendRequest_nullIntentAndOtherActions_areNull() {
        assertNull(SendRequest.parse(null))
        assertNull(SendRequest.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(SendRequest.parse(Intent(Intent.ACTION_VIEW).apply { type = "image/png" }))
        assertNull(SendRequest.parse(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }))
    }

    @Test
    fun sendRequest_capsAtFiftyAndDeduplicates() {
        val list = ArrayList<Uri>()
        for (i in 0 until 60) list.add(uri("content://p/$i"))
        list.add(uri("content://p/0"))
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
        }
        val r = SendRequest.parse(i)
        assertEquals(IncomingFiles.MAX_FILES, r?.count)
        assertEquals(IncomingFiles.MAX_FILES, r?.uris?.distinct()?.size)
    }

    @Test
    fun sendRequest_remoteUris_areDropped() {
        val list = arrayListOf(uri("https://example.com/a.jpg"), uri("content://p/ok"))
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
        }
        assertEquals(listOf(uri("content://p/ok")), SendRequest.parse(i)?.uris)
    }

    @Test
    fun sendRequest_missingType_defaultsToWildcard() {
        val i = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri("content://p/x")) }
        assertEquals("*/*", SendRequest.parse(i)?.mimeType)
    }

    // ---------- PickRequest.buildResult ----------

    @Test
    fun buildResult_singleUri_usesDataAndType() {
        val result = PickRequest.buildResult(listOf(uri("content://p/one")), "image/*", false)
        assertEquals(uri("content://p/one"), result?.data)
        assertEquals("image/*", result?.type)
        assertTrue(result!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(result.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
    }

    @Test
    fun buildResult_multipleUris_usesClipDataOnly() {
        val uris = listOf(uri("content://p/one"), uri("content://p/two"))
        val result = PickRequest.buildResult(uris, "image/*", true)!!
        assertNull(result.data)
        assertEquals("image/*", result.type)
        assertEquals(2, result.clipData?.itemCount)
        assertEquals(uri("content://p/one"), result.clipData?.getItemAt(0)?.uri)
        assertEquals(uri("content://p/two"), result.clipData?.getItemAt(1)?.uri)
        assertTrue(result.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }

    @Test
    fun buildResult_blankType_fallsBackToWildcard() {
        val uris = listOf(uri("content://p/a"), uri("content://p/b"))
        assertEquals("*/*", PickRequest.buildResult(uris, " ", false)?.type)
    }

    @Test
    fun buildResult_empty_isNull() {
        assertNull(PickRequest.buildResult(emptyList(), "image/*", false))
        assertNull(PickRequest.buildResult(listOf(Uri.EMPTY), "image/*", false))
    }

    @Test
    fun pickRequest_allowMultipleStillParses() {
        // Existing API contract: the flag is preserved, single result still works.
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        assertEquals(PickRequest("image/*", true, false), PickRequest.parse(i))
    }
}
