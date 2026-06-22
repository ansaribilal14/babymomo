package com.babymomo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color.White,
    primaryContainer = AmberSoft,
    onPrimaryContainer = AmberOnContainer,
    inversePrimary = AmberDeep,

    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = SageSoft,
    onSecondaryContainer = Color(0xFF1F3A14),

    tertiary = Sky,
    onTertiary = Color.White,
    tertiaryContainer = SkySoft,
    onTertiaryContainer = Color(0xFF0C2436),

    error = Rust,
    onError = Color.White,
    errorContainer = RustSoft,
    onErrorContainer = Color(0xFF4A1407),

    background = Cream,
    onBackground = TextPrimaryLight,
    surface = CreamSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CreamSurfaceHigh,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = Amber,
    inverseSurface = TextPrimaryLight,
    inverseOnSurface = Cream,
    outline = DividerLight,
    outlineVariant = TextTertiaryLight,
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = AmberDark,
    onPrimary = Color(0xFF3A1E04),
    primaryContainer = AmberSoftDark,
    onPrimaryContainer = AmberOnContainerDark,
    inversePrimary = Amber,

    secondary = SageDark,
    onSecondary = Color(0xFF14260A),
    secondaryContainer = Color(0xFF3A4A2E),
    onSecondaryContainer = Color(0xFFDDE7CB),

    tertiary = SkyDark,
    onTertiary = Color(0xFF0C2436),
    tertiaryContainer = Color(0xFF243A4D),
    onTertiaryContainer = Color(0xFFD2E2EF),

    error = RustDark,
    onError = Color(0xFF4A1407),
    errorContainer = Color(0xFF5A2415),
    onErrorContainer = Color(0xFFFAD6CC),

    background = Charcoal,
    onBackground = TextPrimaryDark,
    surface = CharcoalSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CharcoalSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = AmberDark,
    inverseSurface = Cream,
    inverseOnSurface = TextPrimaryLight,
    outline = DividerDark,
    outlineVariant = TextTertiaryDark,
    scrim = Color(0xCC000000),
)

@Composable
fun BABYMOMOTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = BABYMOMOTypography, shapes = BABYMOMOShapes, content = content)
}
