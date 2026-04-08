package com.example.kinetiq.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MoovePrimary,
    onPrimary = MooveOnPrimary,
    primaryContainer = MoovePrimaryContainer,
    onPrimaryContainer = MooveOnPrimaryContainer,
    background = MooveBackground,
    onBackground = MooveOnBackground,
    surface = MooveSurface,
    onSurface = MooveOnSurface,
    surfaceVariant = MooveSurfaceVariant,
    onSurfaceVariant = MooveOnSurfaceVariant,
    secondary = MooveSecondary,
    tertiary = MooveTertiary,
    surfaceTint = MoovePrimary // Using olive primary to warm elevated surfaces
)

@Composable
fun MooveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Applying the warm Material3 palette. 
    // Note: Dark theme is currently mapped to the same palette to maintain the specific warm aesthetic requested.
    val colorScheme = LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
