package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.14.0: desktop shell collapse is route-gated so leaving the viewer can
 * never strand the app in a chrome-less window. Pure rules, no Compose.
 */
class DesktopViewerPolicyTest {

    @Test
    fun `viewer destination is a media presentation route`() {
        assertTrue(isMediaPresentationRoute("viewer/{path}"))
        assertTrue(isMediaPresentationRoute("viewer/%2Fstorage%2Femulated%2F0%2Fa.jpg"))
    }

    @Test
    fun `external media handed over by another app is presented full-window`() {
        assertTrue(isMediaPresentationRoute("externalViewer/{uriString}?mime={mime}"))
        assertTrue(shouldCollapseDesktopShell(true, "externalViewer/{uriString}?mime={mime}"))
    }

    @Test
    fun `editors reached from the gallery keep the full-window presentation`() {
        assertTrue(isMediaPresentationRoute("imageEditor/{uriString}"))
        assertTrue(isMediaPresentationRoute("videoEditor/{uriString}"))
    }

    @Test
    fun `ordinary destinations are not media presentation routes`() {
        assertFalse(isMediaPresentationRoute("library"))
        assertFalse(isMediaPresentationRoute("home"))
        assertFalse(isMediaPresentationRoute("file_browser?path=%2Fstorage"))
        assertFalse(isMediaPresentationRoute("docViewer/{uriString}?path={path}"))
        assertFalse(isMediaPresentationRoute(null))
    }

    @Test
    fun `shell collapses only when requested on a media route`() {
        assertTrue(shouldCollapseDesktopShell(true, "viewer/{path}"))
        assertTrue(shouldCollapseDesktopShell(true, "imageEditor/{uriString}"))
        assertFalse(shouldCollapseDesktopShell(false, "viewer/{path}"))
        assertFalse(shouldCollapseDesktopShell(true, "library"))
        assertFalse(shouldCollapseDesktopShell(true, null))
    }
}
