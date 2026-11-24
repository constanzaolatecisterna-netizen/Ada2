package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AdaDarkColorScheme = darkColorScheme(
    primary = AdaColors.SkyPrimary,
    onPrimary = Color(0xFF03101F),
    primaryContainer = Color(0xFF0C2D4A),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = AdaColors.IndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E1B4B),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = AdaColors.PinkAccent,
    onTertiary = Color(0xFF1F0614),
    background = AdaColors.Background,
    onBackground = AdaColors.TextPrimary,
    surface = AdaColors.Surface,
    onSurface = AdaColors.TextPrimary,
    surfaceVariant = AdaColors.SurfaceElevated,
    onSurfaceVariant = AdaColors.TextMuted,
    outline = AdaColors.BorderStrong,
    outlineVariant = AdaColors.Border,
    error = AdaColors.RoseAlert,
    onError = Color.White,
)

private val AdaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AdaAlianzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdaDarkColorScheme,
        typography = AdaTypography,
        shapes = AdaShapes,
        content = content,
    )
}
