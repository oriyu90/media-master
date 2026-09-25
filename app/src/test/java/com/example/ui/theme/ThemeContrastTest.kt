package com.example.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the dark-mode visibility promise: every foreground/background text
 * pairing the app relies on must meet WCAG 2.1 AA (>= 4.5:1 for body text).
 * Uses raw hex values (mirroring Color.kt) so the test runs on plain JVM
 * without Compose dependencies.
 */
class ThemeContrastTest {

    private fun channel(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    private fun luminance(hex: String): Double {
        val r = channel(Integer.parseInt(hex.substring(0, 2), 16) / 255.0)
        val g = channel(Integer.parseInt(hex.substring(2, 4), 16) / 255.0)
        val b = channel(Integer.parseInt(hex.substring(4, 6), 16) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun ratio(fg: String, bg: String): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertTextContrast(name: String, fg: String, bg: String) {
        assertTrue("$name: ${ratio(fg, bg)}:1 below 4.5:1", ratio(fg, bg) >= 4.5)
    }

    @Test
    fun `dark theme text pairings meet AA`() {
        assertTextContrast("dark onSurface/surface", "EBE1D0", "17130B")
        assertTextContrast("dark onSurfaceVariant/surface", "D0C5B4", "17130B")
        assertTextContrast("dark onSurfaceVariant/surfaceVariant", "D0C5B4", "4D4639")
        assertTextContrast("dark onPrimaryContainer/primaryContainer", "FFE08B", "5B4300")
        assertTextContrast("dark onSecondaryContainer/secondaryContainer", "F5E0BB", "52452A")
        assertTextContrast("dark onPrimary/primary", "3F2E00", "F2BF48")
        // Selected document rows now pair primaryContainer with onPrimaryContainer.
        assertTextContrast("dark selected row", "FFE08B", "5B4300")
    }

    @Test
    fun `light theme text pairings meet AA`() {
        assertTextContrast("light onSurface/surface", "1F1B13", "FFF9EE")
        assertTextContrast("light onSurfaceVariant/surfaceVariant", "4D4639", "EDE1CF")
        assertTextContrast("light onPrimaryContainer/primaryContainer", "5B4300", "FFE08B")
        assertTextContrast("light selected row", "5B4300", "FFE08B")
    }

    @Test
    fun `media scrims keep white text at AA`() {
        // ViewerOnSurface F5F5F5 over the 72% black scrims used by
        // LibraryScreen and CategoryScreen (worst case: over white).
        // 50% black measured ~3.9:1 and was replaced for this reason.
        assertTextContrast("scrim 72pct over white", "F5F5F5", "474747")
        assertTextContrast("scrim 72pct over black", "F5F5F5", "0B0B0B")
        assertTextContrast("viewer text on viewer surface", "F5F5F5", "0B0B0B")
        assertTextContrast("viewer variant on viewer surface", "C7C7C7", "0B0B0B")
    }
}
