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
    primary = Amber, onPrimary = Color.White, primaryContainer = AmberSoft, onPrimaryContainer = TextPrimaryLight,
    secondary = Sage, onSecondary = Color.White, tertiary = Sky, onTertiary = Color.White,
    error = Rust, onError = Color.White, background = Cream, onBackground = TextPrimaryLight,
    surface = CreamSurface, onSurface = TextPrimaryLight, surfaceVariant = CardLight, onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight, outlineVariant = TextTertiaryLight,
)

private val DarkColors = darkColorScheme(
    primary = AmberDark, onPrimary = Charcoal, primaryContainer = AmberDeepDark, onPrimaryContainer = TextPrimaryDark,
    secondary = SageDark, onSecondary = Charcoal, tertiary = SkyDark, onTertiary = Charcoal,
    error = RustDark, onError = Charcoal, background = Charcoal, onBackground = TextPrimaryDark,
    surface = CharcoalSurface, onSurface = TextPrimaryDark, surfaceVariant = CardDark, onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark, outlineVariant = TextTertiaryDark,
)

@Composable
fun BABYMOMOTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = BABYMOMOTypography, shapes = BABYMOMOShapes, content = content)
}
