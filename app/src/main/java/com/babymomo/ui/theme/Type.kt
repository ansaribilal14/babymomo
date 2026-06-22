package com.babymomo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.Font as GoogleFontFamily
import com.babymomo.R

/**
 * BABYMOMO typography.
 *
 *  - **Fraunces** (variable serif, friendly, optical sizing) for display + headline slots.
 *    Fraunces has a "soft" axis that gives it a warmer feel than the default Material serif.
 *  - **Inter** (sans, clean, the de-facto app-UI font) for title / body / label slots.
 *
 * Fonts are downloaded at runtime via the Google Fonts provider (no APK bloat). If the device
 * is offline at first launch, the system serif/sans fallbacks are used silently.
 *
 * All Material 3 slots are defined with deliberate sizes, weights, and line heights so that
 * spacing reads as "designed" rather than "default Material template".
 */

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val fraunces = GoogleFont("Fraunces")
private val inter = GoogleFont("Inter")

val FrauncesFontFamily: FontFamily = FontFamily(
    GoogleFontFamily(googleFont = fraunces, fontProvider = provider, weight = FontWeight.Normal),
    GoogleFontFamily(googleFont = fraunces, fontProvider = provider, weight = FontWeight.Medium),
    GoogleFontFamily(googleFont = fraunces, fontProvider = provider, weight = FontWeight.SemiBold),
    GoogleFontFamily(googleFont = fraunces, fontProvider = provider, weight = FontWeight.Bold)
)

val InterFontFamily: FontFamily = FontFamily(
    GoogleFontFamily(googleFont = inter, fontProvider = provider, weight = FontWeight.Normal),
    GoogleFontFamily(googleFont = inter, fontProvider = provider, weight = FontWeight.Medium),
    GoogleFontFamily(googleFont = inter, fontProvider = provider, weight = FontWeight.SemiBold),
    GoogleFontFamily(googleFont = inter, fontProvider = provider, weight = FontWeight.Bold)
)

private val display = FrauncesFontFamily
private val body = InterFontFamily

val BABYMOMOTypography = Typography(
    displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.sp),

    headlineLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = 0.sp),

    titleLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp),

    labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)
