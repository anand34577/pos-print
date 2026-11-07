package com.posprint.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// A complete, hand-tuned teal palette so every surface, container and "on" colour stays consistent and readable.
private val Light = lightColorScheme(
    primary = Color(0xFF00696B), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F3), onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6364), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E9), onSecondaryContainer = Color(0xFF051F20),
    tertiary = Color(0xFF4B607C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FAFA), onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FAFA), onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E5), onSurfaceVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEEF5F5),
    surfaceContainer = Color(0xFFE8EFEF), surfaceContainerHigh = Color(0xFFE3E9E9), surfaceContainerHighest = Color(0xFFDDE4E4),
    outline = Color(0xFF6F7979), outlineVariant = Color(0xFFBEC9C8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF80D4D6), onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51), onPrimaryContainer = Color(0xFF9CF1F3),
    secondary = Color(0xFFB1CCCD), onSecondary = Color(0xFF1C3435),
    secondaryContainer = Color(0xFF334B4C), onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB3C8E8), onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863), onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1415), onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0E1415), onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceContainerLowest = Color(0xFF090F10), surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121), surfaceContainerHigh = Color(0xFF252B2B), surfaceContainerHighest = Color(0xFF303636),
    outline = Color(0xFF899392), outlineVariant = Color(0xFF3F4948),
)

// One low, consistent corner scale. Text fields use extraSmall, buttons/chips small, cards medium,
// FABs large, dialogs extraLarge: 8 / 8 / 12 / 16 / 20 dp, so nothing sits next to a much rounder neighbour.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun PosPrintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
