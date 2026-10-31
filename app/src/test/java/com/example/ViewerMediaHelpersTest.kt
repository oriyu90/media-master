package com.example

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v2.0.1 hardening: pure helpers extracted from the viewer.
 *
 *  - [shareableUri]: an external `file://` URI must never be forwarded to
 *    another app (FileUriExposedException on targetSdk 24+).
 *  - [dragPageDelta]: the manual video swipe must mirror the pager in RTL.
 */
@RunWith(RobolectricTestRunner::class)
class ViewerMediaHelpersTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `content uri is shared unchanged`() {
        val uri = Uri.parse("content://media/external/images/media/30")
        assertSame(uri, shareableUri(context, uri))
    }

    @Test
    fun `file uri without a path cannot be shared`() {
        assertNull(shareableUri(context, Uri.parse("file://")))
        assertNull(shareableUri(context, Uri.parse("file:///")))
    }

    @Test
    fun `video swipe follows the pager direction in ltr`() {
        assertEquals(1, dragPageDelta(-120f, rtl = false))
        assertEquals(-1, dragPageDelta(120f, rtl = false))
        assertEquals(0, dragPageDelta(0f, rtl = false))
    }

    @Test
    fun `video swipe is mirrored in rtl`() {
        assertEquals(-1, dragPageDelta(-120f, rtl = true))
        assertEquals(1, dragPageDelta(120f, rtl = true))
    }

    @Test
    fun `file provider authority is declared for the package`() {
        // Guards against the manifest authority template drifting away from the
        // value the viewer passes to FileProvider.getUriForFile.
        val provider = context.packageManager.resolveContentProvider(
            "${context.packageName}.fileprovider",
            0,
        )
        assertNotNull(provider)
        assertTrue(provider!!.name == FileProvider::class.java.name)
    }
}
