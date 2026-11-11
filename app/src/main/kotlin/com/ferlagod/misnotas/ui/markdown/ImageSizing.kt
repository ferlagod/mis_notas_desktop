// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

/**
 * Tamaño de las imágenes dentro de una nota.
 *
 * El ancho se guarda como porcentaje del ancho disponible en un fragmento al final de la
 * dirección de la imagen: `![imagen](attachment://<id>#w=50)`. Sin fragmento la imagen se
 * adapta al ancho de la pantalla (100 %). Al ser relativo, se ve proporcionada en cualquier
 * dispositivo, y los demás editores Markdown ignoran el fragmento.
 */
object ImageSizing {
    const val DEFAULT_PERCENT = 100
    const val MIN_PERCENT = 20

    private val SIZE_SUFFIX = Regex("""#w=(\d{1,3})$""")
    private val IMAGE_MARKDOWN = Regex("""!\[(.*?)]\((.*?)\)""")

    /** Ancho (en % del disponible) indicado en [source]; [DEFAULT_PERCENT] si no lo indica. */
    fun widthPercent(source: String): Int =
        SIZE_SUFFIX.find(source)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(MIN_PERCENT, DEFAULT_PERCENT)
            ?: DEFAULT_PERCENT

    /** [source] sin la indicación de tamaño (la dirección real de la imagen). */
    fun stripSize(source: String): String = source.replace(SIZE_SUFFIX, "")

    /** [source] con el ancho [percent]; al 100 % se quita el fragmento (adaptada a la pantalla). */
    fun withSize(source: String, percent: Int): String {
        val base = stripSize(source)
        val p = percent.coerceIn(MIN_PERCENT, DEFAULT_PERCENT)
        return if (p >= DEFAULT_PERCENT) base else "$base#w=$p"
    }

    /**
     * Cambia el tamaño de la imagen Markdown que ocupa el tramo [start, end) de [text].
     * Devuelve el texto sin cambios si en ese tramo no hay una imagen.
     */
    fun resizeAt(text: String, start: Int, end: Int, percent: Int): String {
        if (start < 0 || end > text.length || start >= end) return text
        val match = IMAGE_MARKDOWN.find(text.substring(start, end)) ?: return text
        val replaced = "![${match.groupValues[1]}](${withSize(match.groupValues[2], percent)})"
        val absStart = start + match.range.first
        val absEnd = start + match.range.last + 1
        return text.substring(0, absStart) + replaced + text.substring(absEnd)
    }
}
