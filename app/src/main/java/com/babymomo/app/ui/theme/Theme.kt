package com.babymomo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BabymomoColorScheme = darkColorScheme(
    primary = ElectricTeal,
    onPrimary = MidnightBlack,
    primaryContainer = SurfaceNavy,
    onPrimaryContainer = PureWhite,
    secondary = VividPurple,
    onSecondary = PureWhite,
    secondaryContainer = ElevatedNavy,
    onSecondaryContainer = PureWhite,
    tertiary = WarmCoral,
    onTertiary = PureWhite,
    background = MidnightBlack,
    onBackground = PureWhite,
    surface = DeepNavy,
    onSurface = PureWhite,
    surfaceVariant = SurfaceNavy,
    onSurfaceVariant = MutedBlue,
    outline = DividerBlue,
    outlineVariant = DimBlue,
    error = ErrorRed,
    onError = PureWhite,
    errorContainer = ErrorRed.copy(alpha = 0.15f),
    onErrorContainer = ErrorRed
)

@Composable
fun BabymomoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BabymomoColorScheme,
        typography = BabymomoTypography,
        shapes = BabymomoShapes,
        content = content
    )
}
