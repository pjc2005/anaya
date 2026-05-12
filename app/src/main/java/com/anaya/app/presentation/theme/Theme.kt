package com.anaya.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber80,
    onTertiaryContainer = Color(0xFF261900),
    error = Red30,
    background = Gray99,
    onBackground = Gray10,
    surface = Gray99,
    onSurface = Gray10,
    surfaceVariant = Gray95,
    onSurfaceVariant = Gray20,
    outline = Color(0xFF74777F)
)

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = DarkGreen20,
    primaryContainer = DarkGreen30,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    onSecondary = Color(0xFF003731),
    secondaryContainer = DarkTeal40,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = DarkAmber40,
    onTertiaryContainer = Color(0xFFFFF8E1),
    error = Red80,
    background = Gray10,
    onBackground = Gray90,
    surface = Gray10,
    onSurface = Gray90,
    surfaceVariant = Gray20,
    onSurfaceVariant = Gray90,
    outline = Color(0xFF8E9099)
)

@Composable
fun AnayaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
