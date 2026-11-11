// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenContainer,
    onPrimaryContainer = BrandGreenOnContainer,
    secondary = IndigoSecondary,
    onSecondary = Color.White,
    tertiary = AccentGreen,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = TextTertiaryLight,
    error = AccentRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandGreenLight,
    onPrimary = Color(0xFF1C2E08),
    primaryContainer = BrandGreenDarkContainer,
    onPrimaryContainer = Color.White,
    secondary = IndigoSecondaryDark,
    onSecondary = Color(0xFF283420),
    tertiary = AccentGreen,
    onTertiary = Color.Black,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = TextTertiaryDark,
    error = AccentRed,
    onError = Color.White
)

/** Negro puro: apaga los píxeles en pantallas OLED. */
private val AmoledColorScheme = DarkColorScheme.copy(
    background = BackgroundAmoled,
    surface = SurfaceAmoled,
    surfaceVariant = SurfaceVariantAmoled,
    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF1C1C1E)
)

/** Indica si la app está en modo oscuro (según el tema elegido en Ajustes, no solo el del sistema). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Sustituto de `isSystemInDarkTheme()` que respeta el tema elegido por el usuario. */
@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(): Boolean = LocalDarkTheme.current

@Composable
fun MisNotasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        !darkTheme -> LightColorScheme
        amoled -> AmoledColorScheme
        else -> DarkColorScheme
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}