package com.example

import com.example.ui.volumeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.9.2: drag-to-dismiss decision + veil math are pure and pinned here;
 * volume labels are pure java.io logic. No Compose/Android needed.
 */
class ViewerDismissTest {

    @Test
    fun `short drags spring back, long drags confirm`() {
        assertEquals(false, shouldConfirmDismiss(0f, 140f))
        assertEquals(false, shouldConfirmDismiss(140f, 140f))
        assertEquals(false, shouldConfirmDismiss(-50f, 140f))
        assertEquals(true, shouldConfirmDismiss(140.01f, 140f))
        assertEquals(true, shouldConfirmDismiss(2000f, 140f))
    }

    @Test
    fun `veil stays opaque at rest and dims while dragging`() {
        assertEquals(1f, dismissScrimAlpha(0f, 2000f))
        val mid = dismissScrimAlpha(1000f, 2000f)
        assertEquals(0.725f, mid, 0.001f)
        // Clamped: overshoot and degenerate heights never invert or crash.
        assertEquals(0.45f, dismissScrimAlpha(4000f, 2000f), 0.001f)
        assertEquals(1f, dismissScrimAlpha(0f, 0f))
        assertEquals(0.45f, dismissScrimAlpha(50f, 0f), 0.001f)
    }

    @Test
    fun `volume names expose the last segment`() {
        assertEquals("ABCD-1234", volumeDisplayName("/storage/ABCD-1234"))
        assertEquals("UsbDriveA", volumeDisplayName("/storage/UsbDriveA/"))
        assertEquals("0", volumeDisplayName("/storage/emulated/0"))
        assertEquals("", volumeDisplayName("/"))
        assertEquals("", volumeDisplayName(""))
    }
}
