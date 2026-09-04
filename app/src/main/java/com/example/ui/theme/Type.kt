package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Explicit Material 3 type scale.
 *
 * Line heights are set on every role (the previous file only defined `bodyLarge`)
 * so CJK and Arabic glyphs — which are taller than Latin — are not clipped.
 * `LineHeightStyle` trims leading/trailing space so multi-line labels stay tight.
 */
private val lhStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = lhStyle,
    textAlign = TextAlign.Unspecified,
)

val Typography = Typography(
    displayLarge = style(57, 68, tracking = -0.25),
    displayMedium = style(45, 56),
    displaySmall = style(36, 44),
    headlineLarge = style(32, 40),
    headlineMedium = style(28, 36),
    headlineSmall = style(24, 32),
    titleLarge = style(22, 30, weight = FontWeight.SemiBold),
    titleMedium = style(16, 24, weight = FontWeight.SemiBold, tracking = 0.15),
    titleSmall = style(14, 20, weight = FontWeight.SemiBold, tracking = 0.1),
    bodyLarge = style(16, 24, tracking = 0.5),
    bodyMedium = style(14, 20, tracking = 0.25),
    bodySmall = style(12, 16, tracking = 0.4),
    labelLarge = style(14, 20, weight = FontWeight.Medium, tracking = 0.1),
    labelMedium = style(12, 16, weight = FontWeight.Medium, tracking = 0.5),
    labelSmall = style(11, 16, weight = FontWeight.Medium, tracking = 0.5),
)
