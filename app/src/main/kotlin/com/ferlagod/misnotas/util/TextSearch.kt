// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.util

import java.text.Normalizer
import java.util.Locale

/**
 * Búsqueda tolerante: sin distinguir mayúsculas ni acentos ("cancion" encuentra "Canción")
 * y con varias palabras en cualquier orden.
 */
object TextSearch {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    fun normalize(text: String): String =
        DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    /** Palabras de la consulta ya normalizadas. */
    fun terms(query: String): List<String> =
        normalize(query).trim().split(WHITESPACE).filter { it.isNotEmpty() }

    /**
     * Rangos de [text] que coinciden con alguno de [terms] (para resaltar resultados).
     * Funciona porque quitar los acentos en NFD no cambia la posición de las letras base
     * cuando el texto original está en NFC, que es lo habitual.
     */
    fun matchRanges(text: String, terms: List<String>): List<IntRange> {
        if (terms.isEmpty()) return emptyList()
        val normalized = normalize(text)
        if (normalized.length != text.length) return emptyList()
        val ranges = mutableListOf<IntRange>()
        for (term in terms) {
            var from = 0
            while (true) {
                val idx = normalized.indexOf(term, from)
                if (idx < 0) break
                ranges += idx until idx + term.length
                from = idx + term.length
            }
        }
        return ranges.sortedBy { it.first }
    }
}
