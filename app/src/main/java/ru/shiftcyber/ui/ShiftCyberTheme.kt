package ru.shiftcyber.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class CyberPalette(
    val purple: Color,
    val cyan: Color,
    val green: Color,
    val danger: Color,
    val gradientEnd: Color
)

val LocalCyberPalette = staticCompositionLocalOf {
    CyberPalette(
        purple = Color(0xFFB49AFF),
        cyan = Color(0xFF65E7F2),
        green = Color(0xFF72F0B2),
        danger = Color(0xFFFF7F9C),
        gradientEnd = Color(0xFF12172A)
    )
}

private val DarkCyberScheme = darkColorScheme(
    primary = Color(0xFFB49AFF),
    onPrimary = Color(0xFF150B2E),
    primaryContainer = Color(0xFF2D2152),
    onPrimaryContainer = Color(0xFFEDE6FF),
    secondary = Color(0xFF65E7F2),
    onSecondary = Color(0xFF042429),
    secondaryContainer = Color(0xFF143842),
    onSecondaryContainer = Color(0xFFD6FAFE),
    tertiary = Color(0xFF72F0B2),
    onTertiary = Color(0xFF042918),
    tertiaryContainer = Color(0xFF133D2B),
    onTertiaryContainer = Color(0xFFD5FBE6),
    background = Color(0xFF0B0D16),
    onBackground = Color(0xFFF6F3FF),
    surface = Color(0xFF171A29),
    onSurface = Color(0xFFF6F3FF),
    surfaceVariant = Color(0xFF22263C),
    onSurfaceVariant = Color(0xFFC7C2DF),
    surfaceContainer = Color(0xFF1B1F32),
    surfaceContainerHigh = Color(0xFF20253B),
    surfaceContainerHighest = Color(0xFF292F4A),
    outline = Color(0xFF565178),
    outlineVariant = Color(0xFF33304D),
    error = Color(0xFFFF7F9C),
    onError = Color(0xFF380012)
)

private val LightCyberScheme = lightColorScheme(
    primary = Color(0xFF6B3FD4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DFFF),
    onPrimaryContainer = Color(0xFF22085E),
    secondary = Color(0xFF007B8C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFF4FA),
    onSecondaryContainer = Color(0xFF00272E),
    tertiary = Color(0xFF0E7D4D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1F7E3),
    onTertiaryContainer = Color(0xFF002914),
    background = Color(0xFFF4F3FA),
    onBackground = Color(0xFF171426),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171426),
    surfaceVariant = Color(0xFFEAE7F6),
    onSurfaceVariant = Color(0xFF4F496B),
    surfaceContainer = Color(0xFFEFECF8),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE4E0F2),
    outline = Color(0xFF968FB5),
    outlineVariant = Color(0xFFD7D2EB),
    error = Color(0xFFC9234B),
    onError = Color(0xFFFFFFFF)
)

private val DarkCyberPalette = CyberPalette(
    purple = Color(0xFFB49AFF),
    cyan = Color(0xFF65E7F2),
    green = Color(0xFF72F0B2),
    danger = Color(0xFFFF7F9C),
    gradientEnd = Color(0xFF12172A)
)

private val LightCyberPalette = CyberPalette(
    purple = Color(0xFF6B3FD4),
    cyan = Color(0xFF007B8C),
    green = Color(0xFF0E7D4D),
    danger = Color(0xFFC9234B),
    gradientEnd = Color(0xFFE9E6F7)
)

@Composable
fun ShiftCyberTheme(
    dark: Boolean,
    content: @Composable () -> Unit
) {
    val scheme = if (dark) DarkCyberScheme else LightCyberScheme
    val palette = if (dark) DarkCyberPalette else LightCyberPalette
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = scheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = scheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalCyberPalette provides palette) {
        MaterialTheme(colorScheme = scheme) {
            ProvideTextStyle(
                value = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                content = content
            )
        }
    }
}
