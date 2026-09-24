package com.example.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModeTest {

    @Test
    fun `auto stays in touch UI when no signal`() {
        val s = DesktopMode.Signals()
        assertFalse(DesktopMode.resolve(s, DesktopMode.OVERRIDE_AUTO))
    }

    @Test
    fun `each legacy and modern trigger enters desktop UI`() {
        assertTrue(
            DesktopMode.resolve(
                DesktopMode.Signals(legacyDeskUiMode = true),
                DesktopMode.OVERRIDE_AUTO,
            ),
        )
        assertTrue(
            DesktopMode.resolve(
                DesktopMode.Signals(captionBarVisible = true),
                DesktopMode.OVERRIDE_AUTO,
            ),
        )
        assertTrue(
            DesktopMode.resolve(
                DesktopMode.Signals(multiWindow = true),
                DesktopMode.OVERRIDE_AUTO,
            ),
        )
        assertTrue(
            DesktopMode.resolve(
                DesktopMode.Signals(freeformWindowing = true),
                DesktopMode.OVERRIDE_AUTO,
            ),
        )
        assertTrue(
            DesktopMode.resolve(
                DesktopMode.Signals(samsungDexReflection = true),
                DesktopMode.OVERRIDE_AUTO,
            ),
        )
    }

    @Test
    fun `keyboard mouse and external display alone never trigger`() {
        val s = DesktopMode.Signals(hasKeyboardOrMouse = true, hasExternalDisplay = true)
        assertFalse(DesktopMode.resolve(s, DesktopMode.OVERRIDE_AUTO))
    }

    @Test
    fun `manual override wins over signals`() {
        val desktop = DesktopMode.Signals(
            legacyDeskUiMode = true,
            captionBarVisible = true,
            multiWindow = true,
            freeformWindowing = true,
            samsungDexReflection = true,
        )
        assertTrue(DesktopMode.resolve(desktop, DesktopMode.OVERRIDE_FORCE_DESKTOP))
        assertFalse(DesktopMode.resolve(desktop, DesktopMode.OVERRIDE_FORCE_TOUCH))
        assertFalse(
            DesktopMode.resolve(DesktopMode.Signals(), DesktopMode.OVERRIDE_FORCE_TOUCH),
        )
        assertTrue(
            DesktopMode.resolve(DesktopMode.Signals(), DesktopMode.OVERRIDE_FORCE_DESKTOP),
        )
    }

    @Test
    fun `vendor mapping covers all eight PC modes`() {
        assertEquals(DesktopMode.Vendor.SAMSUNG_DEX, DesktopMode.vendorForManufacturer("samsung"))
        assertEquals(DesktopMode.Vendor.MOTOROLA, DesktopMode.vendorForManufacturer("motorola"))
        assertEquals(DesktopMode.Vendor.HUAWEI, DesktopMode.vendorForManufacturer("HUAWEI"))
        assertEquals(DesktopMode.Vendor.HONOR, DesktopMode.vendorForManufacturer("honor"))
        assertEquals(DesktopMode.Vendor.XIAOMI, DesktopMode.vendorForManufacturer("Xiaomi"))
        assertEquals(DesktopMode.Vendor.XIAOMI, DesktopMode.vendorForManufacturer("Redmi"))
        assertEquals(DesktopMode.Vendor.OPPO, DesktopMode.vendorForManufacturer("OPPO"))
        assertEquals(DesktopMode.Vendor.OPPO, DesktopMode.vendorForManufacturer("OnePlus"))
        assertEquals(DesktopMode.Vendor.LENOVO, DesktopMode.vendorForManufacturer("LENOVO"))
        assertEquals(DesktopMode.Vendor.GENERIC, DesktopMode.vendorForManufacturer("Google"))
        assertEquals(DesktopMode.Vendor.GENERIC, DesktopMode.vendorForManufacturer(null))
    }
}
