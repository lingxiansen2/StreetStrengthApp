package com.codex.streetstrength.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD5B37C),
    onPrimary = Color(0xFF111315),
    primaryContainer = Color(0xFF3E3425),
    onPrimaryContainer = Color(0xFFFFDEAA),
    secondary = Color(0xFF8FA479),
    onSecondary = Color(0xFF12170E),
    secondaryContainer = Color(0xFF273220),
    onSecondaryContainer = Color(0xFFD9E9C4),
    tertiary = Color(0xFFCE7C5E),
    onTertiary = Color(0xFF21110B),
    tertiaryContainer = Color(0xFF422016),
    onTertiaryContainer = Color(0xFFFFD3C2),
    background = Color(0xFF111315),
    surface = Color(0xFF171A1D),
    surfaceVariant = Color(0xFF22262B),
    onBackground = Color(0xFFF6F3EC),
    onSurface = Color(0xFFF6F3EC),
    onSurfaceVariant = Color(0xFFC9C2B6),
    outline = Color(0xFF555B61),
    outlineVariant = Color(0xFF2D3338),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF31100D),
    errorContainer = Color(0xFF5F1F17),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun StreetStrengthTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
