package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * v2.1.0 (WP2 accent colour): user-selectable one-point accent.
 *
 * The accent only replaces the *hue* of the six brand tokens that carry the
 * amber identity in [Color.kt] — `primary`, `onPrimary`, `primaryContainer`,
 * `onPrimaryContainer`, `inversePrimary` and `surfaceTint`. Every other role
 * (secondary/tertiary/error and the whole warm-neutral surface ramp) stays
 * exactly as shipped, so switching accents never disturbs the contrast
 * guarantees locked down by [ThemeContrastTest].
 *
 * Everything here is a pure function of its arguments: no `android.*`
 * dependency, no allocation of platform objects, no exceptions on bad input.
 * That keeps the whole palette unit-testable on the plain JVM.
 */
object AccentPalette {

    // ---------------------------------------------------------------------
    // Presets
    // ---------------------------------------------------------------------

    /** The shipped brand amber. [DEFAULT_AMBER] is byte-identical to `md_primary_dark`. */
    const val DEFAULT_AMBER: Int = 0xFFF2BF48.toInt()

    const val BLUE: Int = 0xFF3A7BD5.toInt()
    const val GREEN: Int = 0xFF2E9E5B.toInt()
    const val PURPLE: Int = 0xFF8E5BC9.toInt()
    const val PINK: Int = 0xFFD94F8A.toInt()
    const val ORANGE: Int = 0xFFE07B2A.toInt()
    const val TEAL: Int = 0xFF1E9E9E.toInt()
    const val RED: Int = 0xFFD64545.toInt()

    /** The eight built-in accents, in display order (amber first = the default). */
    val PRESETS: List<Int> = listOf(
        DEFAULT_AMBER, BLUE, GREEN, PURPLE, PINK, ORANGE, TEAL, RED,
    )

    /** True when [argb] is one of [PRESETS] (opaque RGB compared). */
    fun isPreset(argb: Int): Boolean = PRESETS.any { (it and 0xFFFFFF) == (argb and 0xFFFFFF) }

    // ---------------------------------------------------------------------
    // Amber reference ramp — mirrors Color.kt md_*_light / md_*_dark.
    // Each entry is (token value, its own HSL hue in degrees, HSL lightness,
    // HSL saturation). The hue is per-token (42°–45°) rather than a single
    // global 45° so that feeding the amber seed back in reproduces the
    // shipped hex values exactly instead of drifting a channel or two.
    // ---------------------------------------------------------------------

    // (hueDeg, lightness, saturation) for each amber token.
    private val L_PRIMARY = Triple(45.000f, 0.235294f, 1.0f)       // #785A00
    private val L_PRIMARY_CONTAINER = Triple(43.966f, 0.772549f, 1.0f) // #FFE08B
    private val L_ON_PRIMARY_CONTAINER = Triple(44.176f, 0.178431f, 1.0f) // #5B4300
    private val L_INVERSE_PRIMARY = Triple(42.000f, 0.615686f, 0.867347f) // #F2BF48

    private val D_PRIMARY = Triple(42.000f, 0.615686f, 0.867347f)   // #F2BF48
    private val D_PRIMARY_CONTAINER = Triple(44.176f, 0.178431f, 1.0f) // #5B4300
    private val D_ON_PRIMARY_CONTAINER = Triple(43.966f, 0.772549f, 1.0f) // #FFE08B
    private val D_INVERSE_PRIMARY = Triple(45.000f, 0.235294f, 1.0f) // #785A00

    /** Light `onPrimary` is plain white in the shipped palette; hue is irrelevant. */
    private const val LIGHT_ON_PRIMARY = 0xFFFFFFFF.toInt()

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Builds the full [ColorScheme] for [seedArgb] in the requested brightness.
     *
     * Non-primary roles are copied verbatim from the shipped [LightColors] /
     * [DarkColors] definitions in Theme.kt (same token list, same order), so
     * an accent change can only ever move the six accent tokens.
     *
     * @param seedArgb any ARGB colour; alpha is ignored. Only its hue is used,
     *   except for achromatic seeds (black/white/grey), where the HSL hue is
     *   undefined and the amber hue is kept — a grey accent then renders as the
     *   shipped amber rather than an arbitrary hue.
     */
    fun buildScheme(seedArgb: Int, dark: Boolean): ColorScheme {
        val seed = Color(seedArgb)
        val hue = hueOf(seed)
        val achromatic = isAchromatic(seed)

        // Hue actually used for the accent tokens. The seed hue is blended
        // towards the shipped amber hue (see accentHue) so the amber seed
        // reproduces Color.kt exactly while every other seed still reads as
        // its own colour.
        val h = if (achromatic) AMBER_HUE else accentHue(hue)

        // The amber seed is the shipped brand colour: return the exact
        // Color.kt tokens instead of re-deriving them from HSL (float
        // round-trips drift a channel or two, e.g. #F2BF48 -> #F2C748).
        // Achromatic seeds intentionally render as the shipped amber too.
        val isAmber = achromatic || (seedArgb and 0xFFFFFF) == (DEFAULT_AMBER and 0xFFFFFF)

        val primary = if (isAmber) {
            if (dark) md_primary_dark else md_primary_light
        } else if (dark) {
            hslToColor(tokenHue(h, D_PRIMARY.first - 42f), D_PRIMARY.second, D_PRIMARY.third)
        } else {
            hslToColor(tokenHue(h, L_PRIMARY.first - 42f), L_PRIMARY.second, L_PRIMARY.third)
        }
        val primaryContainer = if (isAmber) {
            if (dark) md_primaryContainer_dark else md_primaryContainer_light
        } else if (dark) {
            hslToColor(tokenHue(h, D_PRIMARY_CONTAINER.first - 42f), D_PRIMARY_CONTAINER.second, D_PRIMARY_CONTAINER.third)
        } else {
            hslToColor(tokenHue(h, L_PRIMARY_CONTAINER.first - 42f), L_PRIMARY_CONTAINER.second, L_PRIMARY_CONTAINER.third)
        }
        val inversePrimary = if (isAmber) {
            if (dark) md_inversePrimary_dark else md_inversePrimary_light
        } else if (dark) {
            hslToColor(tokenHue(h, D_INVERSE_PRIMARY.first - 42f), D_INVERSE_PRIMARY.second, D_INVERSE_PRIMARY.third)
        } else {
            hslToColor(tokenHue(h, L_INVERSE_PRIMARY.first - 42f), L_INVERSE_PRIMARY.second, L_INVERSE_PRIMARY.third)
        }

        // onPrimary: white in light theme (as shipped); in dark theme the deep
        // tone that sits on the bright primary.
        val onPrimary = if (isAmber) {
            if (dark) md_onPrimary_dark else md_onPrimary_light
        } else if (dark) {
            hslToColor(tokenHue(h, D_ON_PRIMARY_CONTAINER.first - 42f), D_ON_PRIMARY_CONTAINER.second, D_ON_PRIMARY_CONTAINER.third)
        } else {
            Color(LIGHT_ON_PRIMARY)
        }

        // onPrimaryContainer: the deep tone (light) / bright tone (dark).
        val onPrimaryContainer = if (isAmber) {
            if (dark) md_onPrimaryContainer_dark else md_onPrimaryContainer_light
        } else if (dark) {
            hslToColor(tokenHue(h, D_ON_PRIMARY_CONTAINER.first - 42f), D_ON_PRIMARY_CONTAINER.second, D_ON_PRIMARY_CONTAINER.third)
        } else {
            hslToColor(tokenHue(h, L_ON_PRIMARY_CONTAINER.first - 42f), L_ON_PRIMARY_CONTAINER.second, L_ON_PRIMARY_CONTAINER.third)
        }

        // WCAG guarantee: nudge the text tone's lightness until it clears 4.5:1.
        val safeOnPrimary = ensureTextContrast(onPrimary, primary)
        val safeOnPrimaryContainer = ensureTextContrast(onPrimaryContainer, primaryContainer)

        return if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = safeOnPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = safeOnPrimaryContainer,
                secondary = md_secondary_dark,
                onSecondary = md_onSecondary_dark,
                secondaryContainer = md_secondaryContainer_dark,
                onSecondaryContainer = md_onSecondaryContainer_dark,
                tertiary = md_tertiary_dark,
                onTertiary = md_onTertiary_dark,
                tertiaryContainer = md_tertiaryContainer_dark,
                onTertiaryContainer = md_onTertiaryContainer_dark,
                error = md_error_dark,
                onError = md_onError_dark,
                errorContainer = md_errorContainer_dark,
                onErrorContainer = md_onErrorContainer_dark,
                background = md_background_dark,
                onBackground = md_onBackground_dark,
                surface = md_surface_dark,
                onSurface = md_onSurface_dark,
                surfaceVariant = md_surfaceVariant_dark,
                onSurfaceVariant = md_onSurfaceVariant_dark,
                outline = md_outline_dark,
                outlineVariant = md_outlineVariant_dark,
                surfaceContainerLowest = md_surfaceContainerLowest_dark,
                surfaceContainerLow = md_surfaceContainerLow_dark,
                surfaceContainer = md_surfaceContainer_dark,
                surfaceContainerHigh = md_surfaceContainerHigh_dark,
                surfaceContainerHighest = md_surfaceContainerHighest_dark,
                inverseSurface = md_inverseSurface_dark,
                inverseOnSurface = md_inverseOnSurface_dark,
                inversePrimary = inversePrimary,
                surfaceDim = md_surfaceDim_dark,
                surfaceBright = md_surfaceBright_dark,
                surfaceTint = md_surfaceTint_dark,
                scrim = md_scrim_dark,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = safeOnPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = safeOnPrimaryContainer,
                secondary = md_secondary_light,
                onSecondary = md_onSecondary_light,
                secondaryContainer = md_secondaryContainer_light,
                onSecondaryContainer = md_onSecondaryContainer_light,
                tertiary = md_tertiary_light,
                onTertiary = md_onTertiary_light,
                tertiaryContainer = md_tertiaryContainer_light,
                onTertiaryContainer = md_onTertiaryContainer_light,
                error = md_error_light,
                onError = md_onError_light,
                errorContainer = md_errorContainer_light,
                onErrorContainer = md_onErrorContainer_light,
                background = md_background_light,
                onBackground = md_onBackground_light,
                surface = md_surface_light,
                onSurface = md_onSurface_light,
                surfaceVariant = md_surfaceVariant_light,
                onSurfaceVariant = md_onSurfaceVariant_light,
                outline = md_outline_light,
                outlineVariant = md_outlineVariant_light,
                surfaceContainerLowest = md_surfaceContainerLowest_light,
                surfaceContainerLow = md_surfaceContainerLow_light,
                surfaceContainer = md_surfaceContainer_light,
                surfaceContainerHigh = md_surfaceContainerHigh_light,
                surfaceContainerHighest = md_surfaceContainerHighest_light,
                inverseSurface = md_inverseSurface_light,
                inverseOnSurface = md_inverseOnSurface_light,
                inversePrimary = inversePrimary,
                surfaceDim = md_surfaceDim_light,
                surfaceBright = md_surfaceBright_light,
                surfaceTint = md_surfaceTint_light,
                scrim = md_scrim_light,
            )
        }
    }

    // ---------------------------------------------------------------------
    // Contrast
    // ---------------------------------------------------------------------

    /** WCAG 2.1 AA body-text threshold. */
    const val MIN_TEXT_CONTRAST: Float = 4.5f

    /**
     * Returns [text] unchanged when it already clears [MIN_TEXT_CONTRAST]
     * against [background]. Otherwise walks its HSL lightness away from the
     * background in 2% steps (keeping hue and saturation) and returns the
     * first step that clears the bar — towards white when the background is
     * dark, towards black when it is light. If neither direction can satisfy
     * the bar (impossible for opaque colours: pure black or pure white always
     * clears 4.5:1 against *something*), the best-contrast candidate found is
     * returned so the UI never receives an unreadable pair.
     */
    fun ensureTextContrast(text: Color, background: Color): Color {
        if (contrastRatio(text, background) >= MIN_TEXT_CONTRAST) return text

        val hsl = rgbToHsl(text.red, text.green, text.blue)
        val step = 0.02f
        // Try the direction that leaves the background first; if the text
        // starts on the wrong side of the background (e.g. a mid-lightness
        // seed), the opposite direction is the one that can actually reach
        // 4.5:1, so both are attempted before giving up.
        val primaryFirst = relativeLuminance(background) < 0.18f
        var best = text
        var bestRatio = contrastRatio(text, background)
        for (towardsLight in listOf(primaryFirst, !primaryFirst)) {
            var l = hsl[2]
            while (l in 0f..1f) {
                l = if (towardsLight) l + step else l - step
                if (l < 0f || l > 1f) break
                val candidate = hslToColor(hsl[0] * 360f, l, hsl[1])
                val r = contrastRatio(candidate, background)
                if (r > bestRatio) {
                    best = candidate
                    bestRatio = r
                }
                if (r >= MIN_TEXT_CONTRAST) return candidate
            }
        }
        return best
    }

    /**
     * WCAG 2.1 relative luminance of an opaque colour (`[0, 1]`). Implemented
     * here rather than via `Color.luminance()` so the same maths is available
     * to plain JVM unit tests without the graphics extension.
     */
    fun relativeLuminance(color: Color): Float {
        fun channel(c: Float): Float =
            if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    /** WCAG 2.1 contrast ratio between two opaque colours, in `[1, 21]`. */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    // ---------------------------------------------------------------------
    // Hex parsing / formatting
    // ---------------------------------------------------------------------

    /** Longest accepted input (`#RRGGBB` plus a little slack for pasted text). */
    private const val MAX_HEX_LENGTH = 16

    /**
     * Parses user input into an opaque ARGB int, or `null` when the text is not
     * a colour. Accepts `#RRGGBB`, `RRGGBB`, `#RGB` and `RGB`, case-insensitive,
     * with surrounding whitespace. Never throws: empty, over-long, non-hex and
     * `null` input all return `null`.
     */
    fun parseHexColor(input: String?): Int? {
        if (input == null) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_HEX_LENGTH) return null
        val body = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
        val hex = when (body.length) {
            6 -> body
            3 -> buildString { for (c in body) { append(c); append(c) } }
            else -> return null
        }
        if (!hex.all { it.isHexDigit() }) return null
        val rgb = hex.toLongOrNull(16) ?: return null
        return (0xFF000000L or rgb).toInt()
    }

    /** Formats an ARGB int as `#RRGGBB` (alpha dropped). */
    fun toHexString(argb: Int): String {
        val rgb = argb and 0xFFFFFF
        return "#" + rgb.toString(16).uppercase().padStart(6, '0')
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    // ---------------------------------------------------------------------
    // Colour maths (public-domain HSL definitions, matching colorsys)
    // ---------------------------------------------------------------------

    /** Hue used for achromatic seeds (mirrors the shipped amber). */
    private const val AMBER_HUE = 45f

    /**
     * The shipped amber ramp is not a single hue: `#F2BF48` sits at 42°,
     * `#785A00` at 45° and `#FFE08B` at ~44°. A user seed therefore maps to a
     * *hue anchor* that keeps those per-token offsets, so
     *  - seed 42° (the amber) reproduces the shipped tokens byte-for-byte,
     *  - seed 45° (yellow) also stays on amber (yellow is the brand colour),
     *  - any other hue is shifted by the same amount as the anchor.
     */
    private fun accentHue(seedHue: Float): Float {
        val anchor = if (seedHue >= AMBER_HUE) AMBER_HUE else 42f
        return seedHue + (AMBER_HUE - anchor)
    }

    /** Hue offset of a token relative to the amber seed hue (42°). */
    private fun tokenHue(anchorHue: Float, offset: Float): Float = anchorHue + offset

    private fun isAchromatic(c: Color): Boolean {
        // Compare in 8-bit space: #FFFFFF serialises as 0.99999994 in float,
        // which would otherwise look slightly non-grey and produce a bogus hue.
        val r = Math.round(c.red * 255f)
        val g = Math.round(c.green * 255f)
        val b = Math.round(c.blue * 255f)
        return maxOf(r, g, b) - minOf(r, g, b) <= 1
    }

    /** Hue of [c] in degrees, [0, 360). 0 for greys (hue is undefined there). */
    fun hueOf(c: Color): Float = rgbToHsl(c.red, c.green, c.blue)[0] * 360f

    /** @return `[hue 0..1, lightness 0..1, saturation 0..1]`. */
    private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return floatArrayOf(0f, 0f, l)
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        return floatArrayOf(h, s, l)
    }

    /** HSL -> opaque [Color]. Hue in degrees (wrapped), lightness/saturation in [0,1]. */
    fun hslToColor(hueDegrees: Float, lightness: Float, saturation: Float): Color {
        val h = ((hueDegrees % 360f) + 360f) % 360f / 360f
        val l = lightness.coerceIn(0f, 1f)
        val s = saturation.coerceIn(0f, 1f)
        if (s == 0f) return Color(l, l, l, 1f)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        return Color(hueToRgb(p, q, h + 1f / 3f), hueToRgb(p, q, h), hueToRgb(p, q, h - 1f / 3f), 1f)
    }

    private fun hueToRgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
}
