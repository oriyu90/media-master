package com.example.deeplink

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinksTest {

    private fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun simpleHosts_mapToRoutes() {
        for (host in listOf("home", "library", "audio", "documents", "manage", "apps", "clean", "settings")) {
            assertEquals(host, DeepLinks.resolve(viewIntent("mediamaster://$host")))
        }
    }

    @Test
    fun host_isCaseInsensitive() {
        assertEquals("library", DeepLinks.resolve(viewIntent("mediamaster://LIBRARY")))
        assertEquals("audio", DeepLinks.resolve(viewIntent("MEDIAMASTER://audio")))
    }

    @Test
    fun browse_withAbsolutePath_buildsFileBrowserRoute() {
        val route = DeepLinks.resolve(viewIntent("mediamaster://browse?path=/storage/emulated/0/Download"))
        assertEquals("file_browser?path=${Uri.encode("/storage/emulated/0/Download")}", route)
    }

    @Test
    fun browse_withoutValidPath_fallsBackToManage() {
        assertEquals("manage", DeepLinks.resolve(viewIntent("mediamaster://browse")))
        assertEquals("manage", DeepLinks.resolve(viewIntent("mediamaster://browse?path=relative/dir")))
    }

    @Test
    fun editScheme_routesToEditors() {
        val img = DeepLinks.resolve(viewIntent("mediamaster://edit/image?uri=content://m/1"))
        assertEquals("imageEditor/${Uri.encode("content://m/1")}", img)
        val vid = DeepLinks.resolve(viewIntent("mediamaster://edit/video?uri=content://m/2"))
        assertEquals("videoEditor/${Uri.encode("content://m/2")}", vid)
    }

    @Test
    fun editScheme_withoutUri_isNull() {
        assertNull(DeepLinks.resolve(viewIntent("mediamaster://edit/image")))
    }

    @Test
    fun unknownHost_isNull() {
        assertNull(DeepLinks.resolve(viewIntent("mediamaster://not-a-feature")))
        assertNull(DeepLinks.resolve(viewIntent("mediamaster://")))
    }

    @Test
    fun actionEdit_image_and_video() {
        val img = Intent(Intent.ACTION_EDIT).apply { setDataAndType(Uri.parse("content://x/1"), "image/png") }
        assertEquals("imageEditor/${Uri.encode("content://x/1")}", DeepLinks.resolve(img))
        val vid = Intent(Intent.ACTION_EDIT).apply { setDataAndType(Uri.parse("content://x/2"), "video/mp4") }
        assertEquals("videoEditor/${Uri.encode("content://x/2")}", DeepLinks.resolve(vid))
    }

    @Test
    fun actionView_media_opensLibrary() {
        val i = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://x/1"), "image/jpeg") }
        assertEquals("library", DeepLinks.resolve(i))
    }

    @Test
    fun actionView_directory_opensFileBrowser() {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file:///storage/emulated/0/DCIM"), "vnd.android.document/directory")
        }
        assertEquals("file_browser?path=${Uri.encode("/storage/emulated/0/DCIM")}", DeepLinks.resolve(i))
    }

    @Test
    fun unrelatedIntent_isNull() {
        assertNull(DeepLinks.resolve(Intent(Intent.ACTION_MAIN)))
        assertNull(DeepLinks.resolve(null))
        assertNull(DeepLinks.resolve(Intent(Intent.ACTION_SEND).apply { type = "text/plain" }))
    }
}
