package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003865),
    onPrimaryContainer = Color(0xFFD0E8FF),
    secondary = IosLabelSecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = IosSurfaceVariantDark,
    onSecondaryContainer = IosLabelPrimaryDark,
    tertiary = IosHeartRed,
    background = IosBackgroundDark,
    onBackground = IosLabelPrimaryDark,
    surface = IosSurfaceDark,
    onSurface = IosLabelPrimaryDark,
    surfaceVariant = IosSurfaceVariantDark,
    onSurfaceVariant = IosLabelSecondaryDark,
    outline = IosSeparatorDark,
    outlineVariant = Color(0xFF262629),
    error = IosRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = IosBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF004085),
    secondary = IosLabelSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = IosSecondaryBackgroundLight,
    onSecondaryContainer = IosLabelPrimaryLight,
    tertiary = IosHeartRed,
    background = IosBackgroundLight,
    onBackground = IosLabelPrimaryLight,
    surface = IosSurfaceLight,
    onSurface = IosLabelPrimaryLight,
    surfaceVariant = IosSecondaryBackgroundLight,
    onSurfaceVariant = IosLabelSecondaryLight,
    outline = IosSeparatorLight,
    outlineVariant = Color(0xFFEAECEF),
    error = IosRed,
    onError = Color.White
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    GalleryTheme(darkTheme = darkTheme, content = content)
}
