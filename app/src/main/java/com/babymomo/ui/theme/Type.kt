package com.babymomo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * BABYMOMO typography — system fonts only (no Google Fonts dependency).
 *
 * v0.5.3 FIX: v0.5.2 used Google Fonts (Fraunces + Inter) via the
 * androidx.compose.ui:ui-text-google-fonts library, which requires Google Play
 * Services on the device. On devices without GMS (or with an outdated GMS),
 * the GoogleFont.Provider crashes at composition time, causing the app to
 * force-close on launch.
 *
 * Fix: use system FontFamily.Serif (for headings) and FontFamily.SansSerif
 * (for body). These are always available, never crash, and look clean.
 *
 * The visual difference is minimal — modern Android system fonts (Roboto +
 * Noto Serif) are high quality. The warm color palette + spacing carry the
 * design personality, not the specific font files.
 */

private val headingFamily = FontFamily.Serif
private val bodyFamily = FontFamily.SansSerif

val BABYMOMOTypography = Typography(
    displayLarge = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
