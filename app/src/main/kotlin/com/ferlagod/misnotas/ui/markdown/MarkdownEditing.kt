// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

/**
 * Resultado de una edición: texto nuevo y selección (inicio/fin, en caracteres).
 *
 * Son funciones puras sobre cadenas para poder probarlas sin Android ni Compose.
 */
data class TextEdit(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

/** Tipos de prefijo de línea que ofrece la barra de formato. */
enum class LinePrefix(val marker: String) {
    HEADING1("# "),
    HEADING2("## "),
    HEADING3("### "),
    BULLET("- "),
    TASK("- [ ] "),
    NUMBERED("1. "),
    QUOTE("> ")
}

/** Cualquier prefijo de bloque reconocido al principio de una línea (con su sangría). */
private val ANY_PREFIX = Regex("""^(\s*)(#{1,6} |[-*+] \[[ xX]] |[-*+] |\d{1,9}[.)] |> ?)""")

private val LIST_ITEM = Regex("""^(\s*)([-*+] \[[ xX]] |[-*+] |(\d{1,9})([.)]) |> )(.*)$""")

/**
 * Aplica o quita [prefix] en todas las líneas tocadas por la selección.
 *
 * - Si todas las líneas ya lo tienen, se quita (alternar).
 * - Si no, se sustituye cualquier otro prefijo de bloque por el nuevo (p. ej. viñeta → tarea).
 * - Las listas numeradas se numeran de forma consecutiva.
 */
fun toggleLinePrefix(text: String, selStart: Int, selEnd: Int, prefix: LinePrefix): TextEdit {
    val start = selStart.coerceIn(0, text.length)
    val end = selEnd.coerceIn(start, text.length)
    val firstLineStart = text.lastIndexOf('\n', start - 1) + 1
    val lastLineEnd = text.indexOf('\n', end).let { if (it == -1) text.length else it }

    val lines = text.substring(firstLineStart, lastLineEnd).split('\n')
    // Con varias líneas seleccionadas, las vacías separan bloques: no reciben marcador ni cuentan.
    fun skip(line: String) = lines.size > 1 && line.isBlank()
    val allHave = lines.filterNot(::skip).let { it.isNotEmpty() && it.all { line -> lineHasPrefix(line, prefix) } }

    var number = 1
    val newLines = lines.map { line ->
        if (skip(line)) return@map line
        val match = ANY_PREFIX.find(line)
        val indent = match?.groupValues?.get(1) ?: line.takeWhile { it == ' ' || it == '\t' }
        val body = if (match != null) line.substring(match.range.last + 1) else line.substring(indent.length)
        when {
            allHave -> indent + body
            prefix == LinePrefix.NUMBERED -> "$indent${number++}. $body"
            else -> indent + prefix.marker + body
        }
    }
    val replaced = newLines.joinToString("\n")
    val newText = text.substring(0, firstLineStart) + replaced + text.substring(lastLineEnd)

    // Con una sola línea y el cursor sin selección, el cursor queda al final de la línea editada.
    return if (start == end && lines.size == 1) {
        val delta = replaced.length - lines[0].length
        TextEdit(newText, (start + delta).coerceIn(firstLineStart, firstLineStart + replaced.length))
    } else {
        TextEdit(newText, firstLineStart, firstLineStart + replaced.length)
    }
}

private fun lineHasPrefix(line: String, prefix: LinePrefix): Boolean {
    val trimmed = line.trimStart()
    return when (prefix) {
        LinePrefix.NUMBERED -> Regex("""^\d{1,9}[.)] """).containsMatchIn(trimmed)
        LinePrefix.TASK -> Regex("""^[-*+] \[[ xX]] """).containsMatchIn(trimmed)
        LinePrefix.BULLET -> Regex("""^[-*+] (?!\[[ xX]] )""").containsMatchIn(trimmed)
        LinePrefix.HEADING1 -> trimmed.startsWith("# ")
        LinePrefix.HEADING2 -> trimmed.startsWith("## ")
        LinePrefix.HEADING3 -> trimmed.startsWith("### ")
        LinePrefix.QUOTE -> trimmed.startsWith(">")
    }
}

/** Quita cualquier formato de bloque (título, lista, cita…) de las líneas tocadas por la selección. */
fun clearLinePrefix(text: String, selStart: Int, selEnd: Int): TextEdit {
    val start = selStart.coerceIn(0, text.length)
    val end = selEnd.coerceIn(start, text.length)
    val firstLineStart = text.lastIndexOf('\n', start - 1) + 1
    val lastLineEnd = text.indexOf('\n', end).let { if (it == -1) text.length else it }
    val lines = text.substring(firstLineStart, lastLineEnd).split('\n')
    val newLines = lines.map { line ->
        val match = ANY_PREFIX.find(line) ?: return@map line
        match.groupValues[1] + line.substring(match.range.last + 1)
    }
    val replaced = newLines.joinToString("\n")
    val newText = text.substring(0, firstLineStart) + replaced + text.substring(lastLineEnd)
    val delta = replaced.length - (lastLineEnd - firstLineStart)
    return if (start == end && lines.size == 1) {
        TextEdit(newText, (start + delta).coerceIn(firstLineStart, firstLineStart + replaced.length))
    } else {
        TextEdit(newText, firstLineStart, firstLineStart + replaced.length)
    }
}

/**
 * Envuelve la selección con [prefix]/[suffix] (negrita, cursiva…). Si ya estaba envuelta, la
 * desenvuelve. Sin selección inserta los marcadores y deja el cursor en medio.
 */
fun toggleWrap(text: String, selStart: Int, selEnd: Int, prefix: String, suffix: String = prefix): TextEdit {
    val start = selStart.coerceIn(0, text.length)
    val end = selEnd.coerceIn(start, text.length)
    val selected = text.substring(start, end)

    // Marcadores por fuera de la selección: **[texto]**
    if (start >= prefix.length && end + suffix.length <= text.length &&
        text.startsWith(prefix, start - prefix.length) && text.startsWith(suffix, end)
    ) {
        val newText = text.removeRange(end, end + suffix.length).removeRange(start - prefix.length, start)
        return TextEdit(newText, start - prefix.length, end - prefix.length)
    }
    // Marcadores dentro de la selección: [**texto**]
    if (selected.length >= prefix.length + suffix.length && selected.startsWith(prefix) && selected.endsWith(suffix)) {
        val inner = selected.substring(prefix.length, selected.length - suffix.length)
        return TextEdit(text.replaceRange(start, end, inner), start, start + inner.length)
    }
    val newText = text.replaceRange(start, end, prefix + selected + suffix)
    return if (start == end) {
        TextEdit(newText, start + prefix.length)
    } else {
        TextEdit(newText, start + prefix.length, end + prefix.length)
    }
}

/** Inserta [snippet] en la posición del cursor (sustituyendo la selección). */
fun insertAtCursor(text: String, selStart: Int, selEnd: Int, snippet: String): TextEdit {
    val start = selStart.coerceIn(0, text.length)
    val end = selEnd.coerceIn(start, text.length)
    return TextEdit(text.replaceRange(start, end, snippet), start + snippet.length)
}

/**
 * Continúa una lista al pulsar Intro, como en cualquier editor de notas moderno.
 *
 * Detecta que entre [oldText] y [newText] solo se ha insertado un salto de línea en [cursor]
 * (posición tras el salto en el texto nuevo). Si la línea anterior es un elemento de lista:
 * - con contenido: añade el mismo marcador en la línea nueva (las tareas empiezan sin marcar
 *   y los números se incrementan);
 * - vacío: termina la lista quitando el marcador.
 *
 * Devuelve null si no hay nada que hacer.
 */
fun continueList(oldText: String, newText: String, cursor: Int): TextEdit? {
    if (newText.length != oldText.length + 1) return null
    if (cursor < 1 || cursor > newText.length || newText[cursor - 1] != '\n') return null
    if (newText.removeRange(cursor - 1, cursor) != oldText) return null

    val newlinePos = cursor - 1
    val lineStart = newText.lastIndexOf('\n', newlinePos - 1) + 1
    val previousLine = newText.substring(lineStart, newlinePos)
    val match = LIST_ITEM.matchEntire(previousLine) ?: return null

    val indent = match.groupValues[1]
    val marker = match.groupValues[2]
    val content = match.groupValues[5]

    if (content.isBlank()) {
        // Elemento vacío: se sale de la lista (se borra el marcador y el salto recién escrito).
        val withoutNewline = newText.removeRange(newlinePos, newlinePos + 1)
        val cleaned = withoutNewline.removeRange(lineStart, lineStart + previousLine.length)
        return TextEdit(cleaned, lineStart)
    }

    val nextMarker = when {
        match.groupValues[3].isNotEmpty() -> {
            val n = match.groupValues[3].toLongOrNull()?.plus(1) ?: 1
            "$n${match.groupValues[4]} "
        }
        marker.matches(Regex("""[-*+] \[[ xX]] """)) -> marker.substring(0, 2) + "[ ] "
        else -> marker
    }
    val insertion = indent + nextMarker
    // Al partir un elemento por la mitad, el texto que pasa a la línea nueva no arrastra espacios.
    var rest = cursor
    while (rest < newText.length && (newText[rest] == ' ' || newText[rest] == '\t')) rest++
    return TextEdit(newText.substring(0, cursor) + insertion + newText.substring(rest), cursor + insertion.length)
}

/** Número de tareas hechas y totales de una nota (para la barra de progreso de las tarjetas). */
fun taskProgress(markdown: String): Pair<Int, Int> {
    var done = 0
    var total = 0
    for (line in markdown.lineSequence()) {
        val m = TASK_LINE.find(line) ?: continue
        total++
        if (m.groupValues[1].isNotBlank()) done++
    }
    return done to total
}

private val TASK_LINE = Regex("""^\s*[-*+] \[( |x|X)] """)
