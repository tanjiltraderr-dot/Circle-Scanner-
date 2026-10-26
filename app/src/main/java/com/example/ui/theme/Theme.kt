package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Default: Premium White Light Color Scheme
private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = PureWhite,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = BrandBlue,
    secondary = BrandBlue,
    onSecondary = PureWhite,
    secondaryContainer = LightCardBg,
    onSecondaryContainer = TextDarkPrimary,
    background = PureWhite,
    onBackground = TextDarkPrimary,
    surface = PureWhite,
    onSurface = TextDarkPrimary,
    surfaceVariant = LightCardBg,
    onSurfaceVariant = TextDarkSecondary,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder
)

// Reading Mode Color Scheme (Warm Off-White & Soft Cream)
private val ReadingColorScheme = lightColorScheme(
    primary = ReadingBlue,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFFE8DFCD),
    onPrimaryContainer = ReadingBlue,
    secondary = ReadingBlue,
    onSecondary = PureWhite,
    secondaryContainer = ReadingSurface,
    onSecondaryContainer = ReadingTextPrimary,
    background = ReadingBackground,
    onBackground = ReadingTextPrimary,
    surface = ReadingBackground,
    onSurface = ReadingTextPrimary,
    surfaceVariant = ReadingSurface,
    onSurfaceVariant = ReadingTextSecondary,
    outline = ReadingBorder,
    outlineVariant = ReadingBorder
)

// Dark Mode Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = DarkBlue,
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = DarkBlue,
    onSecondary = Color(0xFF002F6C),
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder
)

@Composable
fun UtilityHubTheme(
    themeMode: String = "LIGHT",
    content: @Composable () -> Unit
) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode.uppercase()) {
        "DARK" -> true
        "SYSTEM", "SYSTEM DEFAULT" -> isSystemDark
        else -> false
    }
    val colorScheme = when (themeMode.uppercase()) {
        "READING" -> ReadingColorScheme
        "DARK" -> DarkColorScheme
        "SYSTEM", "SYSTEM DEFAULT" -> if (isSystemDark) DarkColorScheme else LightColorScheme
        else -> LightColorScheme
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
