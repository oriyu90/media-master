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
    fun actionView_documentTypes_openDocViewerWithTheIncomingUri() {
        val types = listOf(
            "text/plain", "text/csv", "text/markdown", "application/json", "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip", "application/x-zip-compressed", "application/x-zip",
        )
        for (type in types) {
            val i = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://x/doc"), type) }
            assertEquals("docViewer/${Uri.encode("content://x/doc")}", DeepLinks.resolve(i))
        }
    }

    @Test
    fun actionView_zipExtensionFallback_routesWhenMimeMissing() {
        val nullType = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("content://x/archive.zip") }
        assertEquals("docViewer/${Uri.encode("content://x/archive.zip")}", DeepLinks.resolve(nullType))
        val octet = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://x/archive.zip"), "application/octet-stream")
        }
        assertEquals("docViewer/${Uri.encode("content://x/archive.zip")}", DeepLinks.resolve(octet))
    }

    @Test
    fun actionView_zipFallback_doesNotHijackNonZip() {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://x/photo.png"), "application/octet-stream")
        }
        assertNull(DeepLinks.resolve(i))
    }

    @Test
    fun actionView_legacyOfficeTypes_areNotRoutedToDocViewer() {
        val doc = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://x/old"), "application/msword") }
        assertNull(DeepLinks.resolve(doc))
        val ppt = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://x/old"), "application/vnd.ms-powerpoint") }
        assertNull(DeepLinks.resolve(ppt))
    }

    @Test
    fun actionView_directory_opensFileBrowser() {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file:///storage/emulated/0/DCIM"), "vnd.android.document/directory")
        }
        assertEquals("file_browser?path=${Uri.encode("/storage/emulated/0/DCIM")}", DeepLinks.resolve(i))
    }

    @Test
    fun actionView_resourceFolder_opensFileBrowser() {
        // v1.12.0: third-party "open folder" senders using resource/folder.
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file:///storage/emulated/0/Download"), "resource/folder")
        }
        assertEquals("file_browser?path=${Uri.encode("/storage/emulated/0/Download")}", DeepLinks.resolve(i))
    }

    @Test
    fun actionView_directory_nonFilesystemPath_fallsBackToManage() {
        // v1.12.0: a content:// provider path is not a raw path — never
        // navigate to a bogus browser location.
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://com.other.provider/tree/12"), "vnd.android.document/directory")
        }
        assertEquals("manage", DeepLinks.resolve(i))
    }

    @Test
    fun pickRequest_getContent_parsesType() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        assertEquals(PickRequest("image/*", false, false), PickRequest.parse(i))
    }

    @Test
    fun pickRequest_openDocument_parsesPersistableAndMultiple() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        assertEquals(PickRequest("application/pdf", true, true), PickRequest.parse(i))
    }

    @Test
    fun pickRequest_missingType_defaultsToWildcard() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        assertEquals(PickRequest("*/*", false, false), PickRequest.parse(i))
    }

    @Test
    fun pickRequest_nonPickerIntents_areNull() {
        assertNull(PickRequest.parse(null))
        assertNull(PickRequest.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(PickRequest.parse(Intent(Intent.ACTION_VIEW).apply { type = "image/png" }))
        // Picker launches must not become normal deep links either.
        assertNull(DeepLinks.resolve(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }))
    }

    @Test
    fun docTabRoute_buildsViewerRoute() {
        assertEquals(
            "docViewer/${Uri.encode("content://x/a")}?path=${Uri.encode("/s/a.pdf")}",
            docTabRoute("content://x/a", "/s/a.pdf"),
        )
        assertEquals("docViewer/${Uri.encode("content://x/a")}", docTabRoute("content://x/a", null))
    }

    @Test
    fun unrelatedIntent_isNull() {
        assertNull(DeepLinks.resolve(Intent(Intent.ACTION_MAIN)))
        assertNull(DeepLinks.resolve(null))
        assertNull(DeepLinks.resolve(Intent(Intent.ACTION_SEND).apply { type = "text/plain" }))
    }
}
