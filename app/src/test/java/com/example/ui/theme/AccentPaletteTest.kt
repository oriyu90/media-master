package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.1.0 (WP2): guards the accent-colour contract.
 *
 *  - every preset (and every extreme seed) keeps WCAG AA text contrast in both
 *    brightnesses,
 *  - the amber seed reproduces the shipped Color.kt tokens,
 *  - only the six accent roles move; neutrals/secondary/tertiary/error do not,
 *  - hex parsing never throws and rejects malformed input.
 *
 * Compose's `Color`/`ColorScheme` are pure JVM value types, so this runs
 * without Robolectric.
 */
class AccentPaletteTest {

    private val presets = AccentPalette.PRESETS

    // -----------------------------------------------------------------------
    // (a) presets x light/dark meet 4.5:1
    // -----------------------------------------------------------------------

    @Test
    fun `all eight presets exist and start with amber`() {
        assertEquals(8, presets.size)
        assertEquals(8, presets.toSet().size)
        assertEquals(AccentPalette.DEFAULT_AMBER, presets.first())
    }

    @Test
    fun `every preset meets AA text contrast in light and dark`() {
        for (seed in presets) {
            val label = AccentPalette.toHexString(seed)
            for (dark in listOf(false, true)) {
                val scheme = AccentPalette.buildScheme(seed, dark)
                assertTextContrast("$label dark=$dark onPrimary/primary", scheme.onPrimary, scheme.primary)
                assertTextContrast(
                    "$label dark=$dark onPrimaryContainer/primaryContainer",
                    scheme.onPrimaryContainer,
                    scheme.primaryContainer,
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // (b) amber seed == shipped tokens
    // -----------------------------------------------------------------------

    @Test
    fun `amber seed reproduces the shipped light tokens`() {
        val scheme = AccentPalette.buildScheme(AccentPalette.DEFAULT_AMBER, dark = false)
        assertNear("light primary", md_primary_light, scheme.primary)
        assertNear("light onPrimary", md_onPrimary_light, scheme.onPrimary)
        assertNear("light primaryContainer", md_primaryContainer_light, scheme.primaryContainer)
        assertNear("light onPrimaryContainer", md_onPrimaryContainer_light, scheme.onPrimaryContainer)
        assertNear("light inversePrimary", md_inversePrimary_light, scheme.inversePrimary)
        assertNear("light surfaceTint", md_surfaceTint_light, scheme.surfaceTint)
    }

    @Test
    fun `amber seed reproduces the shipped dark tokens`() {
        val scheme = AccentPalette.buildScheme(AccentPalette.DEFAULT_AMBER, dark = true)
        assertNear("dark primary", md_primary_dark, scheme.primary)
        assertNear("dark onPrimary", md_onPrimary_dark, scheme.onPrimary)
        assertNear("dark primaryContainer", md_primaryContainer_dark, scheme.primaryContainer)
        assertNear("dark onPrimaryContainer", md_onPrimaryContainer_dark, scheme.onPrimaryContainer)
        assertNear("dark inversePrimary", md_inversePrimary_dark, scheme.inversePrimary)
        assertNear("dark surfaceTint", md_surfaceTint_dark, scheme.surfaceTint)
    }

    @Test
    fun `non-accent roles are untouched by any preset`() {
        val lightBase = AccentPalette.buildScheme(AccentPalette.DEFAULT_AMBER, dark = false)
        val darkBase = AccentPalette.buildScheme(AccentPalette.DEFAULT_AMBER, dark = true)
        for (seed in presets) {
            assertNonAccentRolesEqual(lightBase, AccentPalette.buildScheme(seed, dark = false), "light")
            assertNonAccentRolesEqual(darkBase, AccentPalette.buildScheme(seed, dark = true), "dark")
        }
    }

    @Test
    fun `non-accent roles match the shipped Color kt values`() {
        val light = AccentPalette.buildScheme(AccentPalette.DEFAULT_AMBER, dark = false)
        assertEquals(md_secondary_light, light.secondary)
        assertEquals(md_tertiary_light, light.tertiary)
        assertEquals(md_error_light, light.error)
        assertEquals(md_surface_light, light.surface)
        assertEquals(md_outline_light, light.outline)
        val dark = AccentPalette.buildScheme(AccentPalette.DEFAULT_AMBER, dark = true)
        assertEquals(md_secondary_dark, dark.secondary)
        assertEquals(md_tertiary_dark, dark.tertiary)
        assertEquals(md_error_dark, dark.error)
        assertEquals(md_surface_dark, dark.surface)
        assertEquals(md_outline_dark, dark.outline)
    }

    @Test
    fun `distinct presets produce distinct primaries in both brightnesses`() {
        for (dark in listOf(false, true)) {
            val primaries = presets.map { AccentPalette.buildScheme(it, dark).primary }
            assertEquals("dark=$dark: presets collapsed to the same primary", presets.size, primaries.toSet().size)
        }
    }

    // -----------------------------------------------------------------------
    // (c) hex parsing
    // -----------------------------------------------------------------------

    @Test
    fun `parseHexColor accepts canonical and shorthand forms`() {
        assertEquals(0xFF3A7BD5.toInt(), AccentPalette.parseHexColor("#3A7BD5"))
        assertEquals(0xFF3A7BD5.toInt(), AccentPalette.parseHexColor("#3a7bd5"))
        assertEquals(0xFF3A7BD5.toInt(), AccentPalette.parseHexColor("3A7BD5"))
        assertEquals(0xFF3A7BD5.toInt(), AccentPalette.parseHexColor("  #3a7bd5  "))
        assertEquals(0xFFFF0000.toInt(), AccentPalette.parseHexColor("#F00"))
        assertEquals(0xFFFFFFFF.toInt(), AccentPalette.parseHexColor("#fff"))
        assertEquals(0xFF000000.toInt(), AccentPalette.parseHexColor("#000000"))
    }

    @Test
    fun `parseHexColor rejects malformed input without throwing`() {
        val bad = listOf(
            null,
            "",
            "   ",
            "#",
            "##123456",
            "#12345",
            "#1234567",
            "#GGGGGG",
            "not a colour",
            "#12 34 56",
            "-123456",
            "0x123456",
            "#".repeat(40),
            "F".repeat(200),
            "\u0000\u0001",
        )
        for (input in bad) {
            assertNull("expected null for ${input?.let { "\"$it\"" } ?: "null"}", AccentPalette.parseHexColor(input))
        }
    }

    @Test
    fun `parseHexColor round-trips every preset`() {
        for (seed in presets) {
            val hex = AccentPalette.toHexString(seed)
            assertEquals(hex, AccentPalette.toHexString(AccentPalette.parseHexColor(hex)!!))
        }
    }

    @Test
    fun `isPreset recognises presets only`() {
        for (seed in presets) assertTrue(AccentPalette.isPreset(seed))
        assertTrue(!AccentPalette.isPreset(0xFF123456.toInt()))
    }

    // -----------------------------------------------------------------------
    // (d) extreme colours
    // -----------------------------------------------------------------------

    @Test
    fun `extreme seeds still meet AA text contrast`() {
        val extremes = listOf(
            "#000000", "#FFFFFF", "#808080", "#7F7F7F",
            "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#00FFFF", "#FF00FF",
            "#010101", "#FEFEFE",
        )
        for (hex in extremes) {
            val seed = AccentPalette.parseHexColor(hex)!!
            for (dark in listOf(false, true)) {
                val scheme = AccentPalette.buildScheme(seed, dark)
                assertTextContrast("$hex dark=$dark onPrimary/primary", scheme.onPrimary, scheme.primary)
                assertTextContrast(
                    "$hex dark=$dark onPrimaryContainer/primaryContainer",
                    scheme.onPrimaryContainer,
                    scheme.primaryContainer,
                )
            }
        }
    }

    @Test
    fun `achromatic seeds fall back to the amber hue`() {
        // Greys have no hue; rendering them as the shipped amber beats an
        // arbitrary "red-ish" accent that HSL would otherwise report.
        val amber = AccentPalette.buildScheme(AccentPalette.DEFAULT_AMBER, dark = true)
        for (hex in listOf("#000000", "#FFFFFF", "#808080")) {
            val scheme = AccentPalette.buildScheme(AccentPalette.parseHexColor(hex)!!, dark = true)
            assertNear("$hex primary", amber.primary, scheme.primary)
            assertNear("$hex primaryContainer", amber.primaryContainer, scheme.primaryContainer)
        }
    }

    @Test
    fun `ensureTextContrast returns the input when it already passes`() {
        val text = Color(0xFF3F2E00)
        val background = Color(0xFFF2BF48)
        assertEquals(text, AccentPalette.ensureTextContrast(text, background))
    }

    @Test
    fun `ensureTextContrast darkens unreadable text on a light background`() {
        val background = Color(0xFFFFE08B)
        val tooLight = Color(0xFFFFF0C0)
        val fixed = AccentPalette.ensureTextContrast(tooLight, background)
        assertTextContrast("fixed on light", fixed, background)
        assertTrue("expected a darker tone", AccentPalette.relativeLuminance(fixed) < AccentPalette.relativeLuminance(tooLight))
    }

    @Test
    fun `ensureTextContrast lightens unreadable text on a dark background`() {
        val background = Color(0xFF102040)
        val tooDark = Color(0xFF152238)
        val fixed = AccentPalette.ensureTextContrast(tooDark, background)
        assertTextContrast("fixed on dark", fixed, background)
        assertTrue("expected a lighter tone", AccentPalette.relativeLuminance(fixed) > AccentPalette.relativeLuminance(tooDark))
    }

    @Test
    fun `contrastRatio matches known WCAG values`() {
        assertEquals(21f, AccentPalette.contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, AccentPalette.contrastRatio(Color.White, Color.White), 0.001f)
        // #785A00 on white is the shipped light primary pairing.
        assertEquals(6.44f, AccentPalette.contrastRatio(Color(0xFF785A00), Color.White), 0.02f)
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private fun assertTextContrast(name: String, fg: Color, bg: Color) {
        val ratio = AccentPalette.contrastRatio(fg, bg)
        assertTrue(
            "$name: ${"%.2f".format(ratio)}:1 below 4.5:1 (fg=${AccentPalette.toHexString(fg.toArgbInt())}, " +
                "bg=${AccentPalette.toHexString(bg.toArgbInt())})",
            ratio >= AccentPalette.MIN_TEXT_CONTRAST,
        )
    }

    /** Hue-swapped tokens must land on the reference token within one 8-bit step per channel. */
    private fun assertNear(name: String, expected: Color, actual: Color) {
        assertNotNull(name, actual)
        val dr = Math.abs(((expected.red - actual.red) * 255f).toInt())
        val dg = Math.abs(((expected.green - actual.green) * 255f).toInt())
        val db = Math.abs(((expected.blue - actual.blue) * 255f).toInt())
        assertTrue(
            "$name: expected ${AccentPalette.toHexString(expected.toArgbInt())}, " +
                "got ${AccentPalette.toHexString(actual.toArgbInt())}",
            dr <= 1 && dg <= 1 && db <= 1,
        )
    }

    private fun assertNonAccentRolesEqual(base: ColorScheme, other: ColorScheme, label: String) {
        fun eq(name: String, a: Color, b: Color) = assertEquals("$label $name changed", a, b)
        eq("secondary", base.secondary, other.secondary)
        eq("onSecondary", base.onSecondary, other.onSecondary)
        eq("secondaryContainer", base.secondaryContainer, other.secondaryContainer)
        eq("onSecondaryContainer", base.onSecondaryContainer, other.onSecondaryContainer)
        eq("tertiary", base.tertiary, other.tertiary)
        eq("onTertiary", base.onTertiary, other.onTertiary)
        eq("tertiaryContainer", base.tertiaryContainer, other.tertiaryContainer)
        eq("onTertiaryContainer", base.onTertiaryContainer, other.onTertiaryContainer)
        eq("error", base.error, other.error)
        eq("onError", base.onError, other.onError)
        eq("errorContainer", base.errorContainer, other.errorContainer)
        eq("onErrorContainer", base.onErrorContainer, other.onErrorContainer)
        eq("background", base.background, other.background)
        eq("onBackground", base.onBackground, other.onBackground)
        eq("surface", base.surface, other.surface)
        eq("onSurface", base.onSurface, other.onSurface)
        eq("surfaceVariant", base.surfaceVariant, other.surfaceVariant)
        eq("onSurfaceVariant", base.onSurfaceVariant, other.onSurfaceVariant)
        eq("outline", base.outline, other.outline)
        eq("outlineVariant", base.outlineVariant, other.outlineVariant)
        eq("surfaceContainerLowest", base.surfaceContainerLowest, other.surfaceContainerLowest)
        eq("surfaceContainerLow", base.surfaceContainerLow, other.surfaceContainerLow)
        eq("surfaceContainer", base.surfaceContainer, other.surfaceContainer)
        eq("surfaceContainerHigh", base.surfaceContainerHigh, other.surfaceContainerHigh)
        eq("surfaceContainerHighest", base.surfaceContainerHighest, other.surfaceContainerHighest)
        eq("inverseSurface", base.inverseSurface, other.inverseSurface)
        eq("inverseOnSurface", base.inverseOnSurface, other.inverseOnSurface)
        eq("surfaceDim", base.surfaceDim, other.surfaceDim)
        eq("surfaceBright", base.surfaceBright, other.surfaceBright)
        eq("scrim", base.scrim, other.scrim)
    }

    private fun Color.toArgbInt(): Int {
        fun c(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
    }
}
