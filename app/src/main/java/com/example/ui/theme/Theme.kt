package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OjasRoyalBlueLight,
    onPrimary = Color.White,
    secondary = OjasVibrantOrange,
    onSecondary = Color.White,
    tertiary = OjasVibrantGold,
    onTertiary = Color.Black,
    background = OjasDarkBackground,
    onBackground = OjasTextPrimaryDark,
    surface = OjasDarkSurface,
    onSurface = OjasTextPrimaryDark,
    surfaceVariant = OjasSlate800,
    onSurfaceVariant = OjasTextSecondaryDark,
    outline = OjasSlate700
)

private val LightColorScheme = lightColorScheme(
    primary = OjasRoyalBlue,
    onPrimary = Color.White,
    secondary = OjasVibrantOrange,
    onSecondary = Color.White,
    tertiary = OjasVibrantGold,
    onTertiary = Color.Black,
    background = OjasOffWhite,
    onBackground = OjasSlate900,
    surface = OjasSurfaceLight,
    onSurface = OjasSlate900,
    surfaceVariant = OjasSlate100,
    onSurfaceVariant = OjasSlate500,
    outline = OjasSlate200
)

@Composable
fun OjasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    OjasTheme(darkTheme = darkTheme, content = content)
}
