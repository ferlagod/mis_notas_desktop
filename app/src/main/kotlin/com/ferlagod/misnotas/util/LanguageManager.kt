// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.util

import com.ferlagod.misnotas.data.local.pref.AppSettings
import com.ferlagod.misnotas.i18n.Strings
import java.util.Locale

/**
 * Idiomas disponibles en la app. [nativeName] se muestra siempre en el propio idioma.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    /** El idioma del sistema operativo. */
    SYSTEM("", ""),
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    ITALIAN("it", "Italiano");

    companion object {
        /** Idioma de una etiqueta guardada ("es", "en-GB"…); [SYSTEM] si está vacía o no se reconoce. */
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            val language = Locale.forLanguageTag(tag).language
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }
    }
}

/**
 * Idioma de la app elegido por el usuario. Se guarda en las preferencias y se aplica al
 * instante (la interfaz se vuelve a componer con los textos nuevos).
 */
object LanguageManager {

    /** Clave del idioma en `settings.properties`. */
    private const val KEY_LANGUAGE = "app_language"

    /** Idioma del sistema, capturado al arrancar (antes de cambiar el `Locale` por defecto). */
    private val systemLocale: Locale = Locale.getDefault()

    /** Idioma elegido (o [AppLanguage.SYSTEM]). */
    fun current(): AppLanguage = AppLanguage.fromTag(AppSettings.store.getString(KEY_LANGUAGE))

    /** Aplica el idioma guardado; se llama al arrancar. */
    fun apply() = Strings.applyLocale(localeFor(current()))

    /** Guarda el idioma y lo aplica al instante. */
    fun set(language: AppLanguage) {
        AppSettings.store.edit { putString(KEY_LANGUAGE, language.tag) }
        Strings.applyLocale(localeFor(language))
    }

    /** `Locale` que corresponde a [language]. */
    private fun localeFor(language: AppLanguage): Locale =
        if (language == AppLanguage.SYSTEM) systemLocale else Locale.forLanguageTag(language.tag)
}
