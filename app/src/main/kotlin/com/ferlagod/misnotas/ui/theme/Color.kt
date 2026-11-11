// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// ==========================================
// Identidad de marca (familia "Mis Apps")
// ==========================================
// Como Mis Podcast usa el azul de su logo, Mis Notas usa el verde del suyo (#809B54),
// ajustado para que el texto y los iconos tengan buen contraste en claro y en oscuro.
val LogoLeafGreen = Color(0xFF809B54)        // Verde exacto del logo
val BrandGreen = Color(0xFF557331)           // Primario en modo claro (contraste 5,4:1 sobre blanco)
val BrandGreenLight = Color(0xFFA8C47E)      // Primario en modo oscuro
val BrandGreenContainer = Color(0xFFDDEBC8)
val BrandGreenOnContainer = Color(0xFF142106)
val BrandGreenDarkContainer = Color(0xFF4E6B2C)
val BrandCream = Color(0xFFFFFBF5)           // Fondo del logo

// Alias históricos: el resto de la app sigue usando estos nombres para el color de acento.
val RoyalBlueLight = BrandGreen
val RoyalBlueDark = BrandGreenLight
val IndigoSecondary = Color(0xFF56624B)
val IndigoSecondaryDark = Color(0xFFBDCBAE)

// ==========================================
// Tema claro (fondos agrupados estilo iOS, compartidos con Mis Podcast)
// ==========================================
val BackgroundLight = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE5E5EA)
val TextPrimaryLight = Color(0xFF000000)
val TextSecondaryLight = Color(0xFF8E8E93)
val TextTertiaryLight = Color(0xFFC7C7CC)
val OutlineLight = Color(0xFFD1D1D6)

// ==========================================
// Tema oscuro (gris elegante) y AMOLED (negro puro), como en Mis Podcast
// ==========================================
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceVariantDark = Color(0xFF2C2C2E)
val BackgroundAmoled = Color(0xFF000000)
val SurfaceAmoled = Color(0xFF000000)
val SurfaceVariantAmoled = Color(0xFF121214)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF98989D)
val TextTertiaryDark = Color(0xFF48484A)
val OutlineDark = Color(0xFF38383A)

// ==========================================
// Acentos semánticos
// ==========================================
val AccentGreen = Color(0xFF34C759)
val AccentOrange = Color(0xFFFF9500)
val AccentRed = Color(0xFFFF3B30)

// ==========================================
// Paleta de notas y carpetas
// ==========================================
val NoteBlue = Color(0xFF007AFF)
val NoteBlueDark = Color(0xFF0A84FF)
val AccentYellow = Color(0xFFFFCC00)
val AccentYellowDark = Color(0xFFFFD60A)
val AccentTeal = Color(0xFF30B0C7)
val AccentTealDark = Color(0xFF40C8E0)
val AccentPurple = Color(0xFFAF52DE)
val AccentPurpleDark = Color(0xFFBF5AF2)
val NoteIndigo = Color(0xFF5856D6)
val NoteIndigoDark = Color(0xFF5E5CE6)

/** Colores que se pueden asignar a notas y carpetas. [key] es el valor que se guarda y se sincroniza. */
enum class NoteColor(val key: String) {
    DEFAULT("DEFAULT"),
    YELLOW("YELLOW"),
    BLUE("BLUE"),
    GREEN("GREEN"),
    ORANGE("ORANGE"),
    RED("RED"),
    PURPLE("PURPLE"),
    TEAL("TEAL"),
    INDIGO("INDIGO");

    companion object {
        /** Color de una clave guardada (sin distinguir mayúsculas); [DEFAULT] si falta o no se reconoce. */
        fun fromKey(key: String?): NoteColor {
            if (key == null) return DEFAULT
            return entries.firstOrNull {
                it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}

/**
 * Obtiene el color de acento correspondiente a la nota según el tema activo.
 */
fun NoteColor.getAccentColor(isDark: Boolean): Color = when (this) {
    NoteColor.DEFAULT -> if (isDark) SurfaceDark else SurfaceLight
    NoteColor.YELLOW -> if (isDark) AccentYellowDark else AccentYellow
    NoteColor.BLUE -> if (isDark) NoteBlueDark else NoteBlue
    NoteColor.GREEN -> AccentGreen
    NoteColor.ORANGE -> AccentOrange
    NoteColor.RED -> AccentRed
    NoteColor.PURPLE -> if (isDark) AccentPurpleDark else AccentPurple
    NoteColor.TEAL -> if (isDark) AccentTealDark else AccentTeal
    NoteColor.INDIGO -> if (isDark) NoteIndigoDark else NoteIndigo
}

/**
 * Fondo para tarjetas de notas:
 * Devuelve SurfaceLight/SurfaceDark para DEFAULT.
 * Para el resto, combina el color de acento al 12% en claro y 18% en oscuro sobre la superficie base.
 */
@Composable
fun NoteColor.toCardBackground(isDark: Boolean = isAppInDarkTheme()): Color {
    val surfaceBase = MaterialTheme.colorScheme.surface
    if (this == NoteColor.DEFAULT) return surfaceBase
    val accent = getAccentColor(isDark)
    val opacity = if (isDark) 0.18f else 0.12f
    return accent.copy(alpha = opacity).compositeOver(surfaceBase)
}

/**
 * Borde para tarjetas de notas:
 * Devuelve OutlineLight/OutlineDark para DEFAULT.
 * Para notas con color, devuelve el acento al 35% de opacidad.
 */
@Composable
fun NoteColor.toCardBorder(isDark: Boolean = isAppInDarkTheme()): Color {
    if (this == NoteColor.DEFAULT) {
        return MaterialTheme.colorScheme.outline
    }
    val accent = getAccentColor(isDark)
    return accent.copy(alpha = 0.35f)
}

/**
 * Fondo sutil para la pantalla del editor de notas:
 * Devuelve BackgroundLight/BackgroundDark para DEFAULT.
 * Para colores con acento, aplica un tinte muy sutil (6% en claro, 9% en oscuro) sobre el fondo base.
 */
@Composable
fun NoteColor.toEditorBackground(isDark: Boolean = isAppInDarkTheme()): Color {
    val bgBase = MaterialTheme.colorScheme.background
    if (this == NoteColor.DEFAULT) return bgBase
    val accent = getAccentColor(isDark)
    val opacity = if (isDark) 0.09f else 0.06f
    return accent.copy(alpha = opacity).compositeOver(bgBase)
}