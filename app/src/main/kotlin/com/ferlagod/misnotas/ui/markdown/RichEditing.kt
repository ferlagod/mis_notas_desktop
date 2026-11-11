// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

/*
 * Edición visual sobre Markdown. El editor muestra el texto con formato y sin marcadores; estas
 * funciones traducen lo que hace el usuario (escribir, borrar, pulsar "Negrita"…) a cambios en el
 * Markdown, sin dejar nunca marcadores sueltos a la vista. Son puras para poder probarlas.
 */

/** Marcador Markdown de cada formato en línea que ofrece la barra. */
val InlineKind.marker: String
    get() = when (this) {
        InlineKind.BOLD -> "**"
        InlineKind.ITALIC -> "*"
        InlineKind.STRIKE -> "~~"
        InlineKind.CODE -> "`"
        else -> ""
    }

/** Orden en el que se anidan los marcadores al aplicar varios formatos a la vez. */
private val NESTING_ORDER = listOf(InlineKind.BOLD, InlineKind.ITALIC, InlineKind.STRIKE, InlineKind.CODE)

private val EMPTY_PAIR_MARKERS = listOf("**", "~~", "__", "*", "_", "`")

/**
 * Corrige una edición del teclado para que no rompa el formato oculto.
 *
 * @param cursor posición del cursor (sin selección) en [new].
 * @return la edición corregida, o null si la del teclado ya es correcta.
 */
fun reconcileEdit(old: String, new: String, cursor: Int, presentation: MarkdownPresentation): TextEdit? {
    if (old == new) return null
    val p = cursor.coerceIn(0, new.length)
    val b = old.length - (new.length - p)
    if (b < 0 || b > old.length || !old.regionMatches(b, new, p, new.length - p)) return null
    var a = 0
    val maxA = minOf(p, b)
    while (a < maxA && old[a] == new[a]) a++
    if (a == b) return null // inserción pura: la gestionan las reglas de escritura
    val inserted = new.substring(a, p)

    // Retroceso de un solo carácter
    if (inserted.isEmpty() && b - a == 1) {
        val rep = presentation.replacementAt(a)
        if (rep != null) {
            when (rep.kind) {
                // Borrar una viñeta, casilla o título quita el formato de la línea; una imagen se borra entera.
                ReplacementKind.BLOCK, ReplacementKind.ATOMIC ->
                    return TextEdit(old.removeRange(rep.start, rep.end), rep.start)
                ReplacementKind.INLINE -> {
                    // El marcador no se ve: se borra el carácter visible anterior.
                    var q = rep.start - 1
                    while (q >= 0) {
                        val r2 = presentation.replacementAt(q) ?: break
                        if (r2.kind != ReplacementKind.INLINE) {
                            val removed = old.removeRange(r2.start, r2.end)
                            return TextEdit(removed, b - (r2.end - r2.start))
                        }
                        q = r2.start - 1
                    }
                    if (q < 0) return TextEdit(old, b)
                    return removeEmptyPairs(old.removeRange(q, q + 1), q, b - 1)
                }
            }
        }
        val cleaned = removeEmptyPairs(new, a, p)
        return if (cleaned.text == new && cleaned.selectionStart == p) null else cleaned
    }

    // Borrado o sustitución de un tramo: los marcadores de formatos que siguen fuera del tramo se
    // conservan (si no, quedarían asteriscos sueltos a la vista).
    var start = a
    var end = b
    for (r in presentation.replacements) {
        if (r.kind == ReplacementKind.ATOMIC && r.start < end && r.end > start) {
            start = minOf(start, r.start)
            end = maxOf(end, r.end)
        }
    }
    val closings = StringBuilder()
    val openings = StringBuilder()
    for (t in presentation.inlineTokens) {
        if (t.kind == InlineKind.IMAGE || t.kind == InlineKind.AUTOLINK) continue
        if (t.start >= start && t.end <= end) continue
        for (x in maxOf(t.start, start) until minOf(t.contentStart, end)) openings.append(old[x])
        for (x in maxOf(t.contentEnd, start) until minOf(t.end, end)) closings.append(old[x])
    }
    if (closings.isEmpty() && openings.isEmpty() && start == a && end == b) {
        val cleaned = if (inserted.isEmpty()) removeEmptyPairs(new, a, p) else null
        return cleaned?.takeIf { it.text != new }
    }
    val result = old.substring(0, start) + closings + inserted + openings + old.substring(end)
    var edit = TextEdit(result, start + closings.length + inserted.length)
    // Puntos de unión donde puede haber quedado una pareja vacía ("**" + "**"), de derecha a izquierda.
    val junctions = listOf(
        start + closings.length + inserted.length + openings.length,
        start + closings.length + inserted.length,
        start + closings.length,
        start
    ).distinct()
    for (j in junctions) {
        if (j > edit.text.length) continue
        edit = removeEmptyPairs(edit.text, j, edit.selectionStart)
    }
    return edit
}

/**
 * Quita parejas de marcadores vacías que se tocan en [at] (`**|**`, `[|](url)`…) y ajusta el cursor.
 */
fun removeEmptyPairs(text: String, at: Int, cursor: Int): TextEdit {
    var t = text
    var pos = at.coerceIn(0, t.length)
    var cur = cursor.coerceIn(0, t.length)
    var changed = true
    while (changed) {
        changed = false
        for (m in EMPTY_PAIR_MARKERS) {
            val l = m.length
            if (pos >= l && t.startsWith(m, pos - l) && t.startsWith(m, pos)) {
                // "***" no es una pareja vacía de "*": hay que mirar la racha completa.
                if (l == 1 && (t.getOrNull(pos - 2) == m[0] || t.getOrNull(pos + 1) == m[0])) continue
                t = t.removeRange(pos - l, pos + l)
                cur = when {
                    cur >= pos + l -> cur - 2 * l
                    cur > pos - l -> pos - l
                    else -> cur
                }
                pos -= l
                changed = true
                break
            }
        }
        if (!changed && pos >= 1 && t[pos - 1] == '[' && t.startsWith("](", pos) && (pos < 2 || t[pos - 2] != '!')) {
            val close = t.indexOf(')', pos + 2)
            if (close != -1 && t.indexOf('\n', pos).let { it == -1 || it > close }) {
                t = t.removeRange(pos - 1, close + 1)
                cur = if (cur > close) cur - (close + 2 - pos) else if (cur >= pos - 1) pos - 1 else cur
                pos -= 1
                changed = true
            }
        }
    }
    return TextEdit(t, cur)
}

/**
 * Escritura con formato "pendiente": el usuario pulsó Negrita sin seleccionar nada y empieza a
 * escribir. El texto insertado se envuelve con los marcadores y el cursor queda dentro, así que
 * lo que siga escribiendo mantiene el formato.
 *
 * @return la edición y el desplazamiento que hay que aplicar a la composición del teclado,
 *   o null si no es una inserción simple (o es solo espacio, que no puede abrir un formato).
 */
fun applyPendingStyles(old: String, new: String, cursor: Int, pending: Set<InlineKind>): Pair<TextEdit, Int>? {
    if (pending.isEmpty() || new.length <= old.length) return null
    val p = cursor.coerceIn(0, new.length)
    val k = new.length - old.length
    val a = p - k
    if (a < 0 || !new.regionMatches(0, old, 0, a) || !new.regionMatches(p, old, a, old.length - a)) return null
    val inserted = new.substring(a, p)
    if (inserted.isBlank() || inserted.contains('\n')) return null
    val kinds = NESTING_ORDER.filter { it in pending }
    val open = kinds.joinToString("") { it.marker }
    val close = kinds.reversed().joinToString("") { it.marker }
    val text = old.substring(0, a) + open + inserted + close + old.substring(a)
    return TextEdit(text, a + open.length + inserted.length) to open.length
}

/**
 * Intro justo al principio del contenido de una línea con formato de bloque (título, viñeta…):
 * se abre la línea nueva por encima en lugar de partir el marcador.
 */
fun newlineBeforeBlock(old: String, new: String, cursor: Int, presentation: MarkdownPresentation): TextEdit? {
    if (new.length != old.length + 1) return null
    val p = cursor.coerceIn(1, new.length)
    if (new[p - 1] != '\n') return null
    val a = p - 1
    if (!new.regionMatches(0, old, 0, a) || !new.regionMatches(p, old, a, old.length - a)) return null
    val rep = presentation.replacements.firstOrNull { it.kind == ReplacementKind.BLOCK && it.end == a } ?: return null
    if (a >= old.length || old[a] == '\n') return null
    val lineStart = old.lastIndexOf('\n', rep.start - 1) + 1
    return TextEdit(old.substring(0, lineStart) + "\n" + old.substring(lineStart), a + 1)
}

/**
 * Botón de formato en línea (negrita, cursiva, tachado, código).
 *
 * - Con texto seleccionado: lo aplica, o lo quita si ya lo tenía (también a una parte).
 * - Sin selección dentro de un texto con ese formato: al final, "sale" del formato; en medio, lo quita.
 *
 * @return null si no hay selección ni formato que quitar: el editor lo deja "pendiente" para lo
 *   próximo que se escriba.
 */
fun toggleInlineStyle(
    text: String,
    selStart: Int,
    selEnd: Int,
    kind: InlineKind,
    presentation: MarkdownPresentation
): TextEdit? {
    val marker = kind.marker
    if (marker.isEmpty()) return null
    val s = selStart.coerceIn(0, text.length)
    val e = selEnd.coerceIn(s, text.length)
    val ofKind = presentation.inlineTokens.filter { it.kind == kind }

    if (s == e) {
        val t = ofKind.filter { s >= it.contentStart && s <= it.contentEnd }.minByOrNull { it.end - it.start } ?: return null
        if (s == t.contentEnd) return TextEdit(text, t.end)
        return unwrap(text, t, s, e)
    }

    var a = s
    var b = e
    while (a < b && text[a].isWhitespace()) a++
    while (b > a && text[b - 1].isWhitespace()) b--
    if (a == b) return TextEdit(text, s, e)

    val container = ofKind.filter { a >= it.contentStart && b <= it.contentEnd }.minByOrNull { it.end - it.start }
    if (container != null) {
        if (a == container.contentStart && b == container.contentEnd) return unwrap(text, container, a, b)
        // Quitar el formato solo a lo seleccionado: **ab[cd]ef** -> **ab**cd**ef**
        val l = marker.length
        val split = text.substring(0, a) + marker + text.substring(a, b) + marker + text.substring(b)
        // Si la selección tocaba un extremo quedan parejas vacías: se limpian (primero la de la derecha).
        val right = removeEmptyPairs(split, b + 2 * l, b + l)
        val left = removeEmptyPairs(right.text, a, a + l)
        val shift = right.text.length - left.text.length
        return TextEdit(left.text, left.selectionStart, right.selectionStart - shift)
    }

    // Aplicar el formato, línea a línea (el formato en línea no cruza saltos de línea).
    data class Segment(var start: Int, var end: Int)
    val segments = mutableListOf<Segment>()
    var ls = text.lastIndexOf('\n', a - 1) + 1
    while (ls <= b) {
        val le = text.indexOf('\n', ls).let { if (it == -1) text.length else it }
        val contentStart = presentation.replacements
            .firstOrNull { it.kind == ReplacementKind.BLOCK && it.start >= ls && it.end <= le }?.end ?: ls
        var ss = maxOf(a, contentStart)
        var se = minOf(b, le)
        while (ss < se && text[ss].isWhitespace()) ss++
        while (se > ss && text[se - 1].isWhitespace()) se--
        if (ss < se) {
            // Un formato igual que se solapa a medias se absorbe en el nuevo.
            for (t in ofKind) {
                if (t.start < se && t.end > ss && !(t.start >= ss && t.end <= se) && t.start >= ls && t.end <= le) {
                    ss = minOf(ss, t.start)
                    se = maxOf(se, t.end)
                }
            }
            segments += Segment(ss, se)
        }
        if (le >= text.length) break
        ls = le + 1
    }
    if (segments.isEmpty()) return TextEdit(text, s, e)

    var result = text
    var firstStart = segments.first().start
    var lastEnd = segments.last().end
    var totalDelta = 0
    for (seg in segments.asReversed()) {
        var segText = result.substring(seg.start, seg.end)
        // Quita los marcadores del mismo formato que haya dentro (no se anidan negritas en negritas).
        val inner = ofKind.filter { it.start >= seg.start && it.end <= seg.end }.sortedByDescending { it.start }
        for (t in inner) {
            val rs = t.start - seg.start
            segText = segText.substring(0, rs) +
                segText.substring(t.contentStart - seg.start, t.contentEnd - seg.start) +
                segText.substring(t.end - seg.start)
        }
        val replacement = marker + segText + marker
        result = result.substring(0, seg.start) + replacement + result.substring(seg.end)
        totalDelta += replacement.length - (seg.end - seg.start)
        if (seg === segments.first()) firstStart = seg.start + marker.length
    }
    lastEnd = lastEnd + totalDelta - marker.length
    return TextEdit(result, firstStart, maxOf(firstStart, lastEnd))
}

/** Quita los marcadores de [t] (deja solo su contenido) y recoloca la selección [s, e). */
private fun unwrap(text: String, t: InlineToken, s: Int, e: Int): TextEdit {
    val openLen = t.contentStart - t.start
    val newText = text.substring(0, t.start) + text.substring(t.contentStart, t.contentEnd) + text.substring(t.end)
    fun map(x: Int) = when {
        x <= t.start -> x
        x <= t.contentStart -> t.start
        x <= t.contentEnd -> x - openLen
        x <= t.end -> t.contentEnd - openLen
        else -> x - (t.end - t.start - (t.contentEnd - t.contentStart))
    }
    return TextEdit(newText, map(s), map(e))
}

/** Formatos en línea activos en la selección (para resaltar los botones de la barra). */
fun activeInlineKinds(presentation: MarkdownPresentation, selStart: Int, selEnd: Int): Set<InlineKind> {
    val s = minOf(selStart, selEnd)
    val e = maxOf(selStart, selEnd)
    return presentation.inlineTokens
        .filter { s >= it.contentStart && e <= it.contentEnd && (s < it.contentEnd || s == e) }
        .map { if (it.kind == InlineKind.AUTOLINK) InlineKind.LINK else it.kind }
        .toSet()
}

/** Enlace que contiene la posición [pos], si lo hay. */
fun linkAt(presentation: MarkdownPresentation, pos: Int): InlineToken? =
    presentation.inlineTokens.firstOrNull { it.kind == InlineKind.LINK && pos >= it.contentStart && pos <= it.contentEnd }

/** Tipo de bloque de la línea donde está [pos] (para resaltar la barra). */
fun lineBlockAt(text: String, pos: Int): LinePrefix? {
    val p = pos.coerceIn(0, text.length)
    val ls = text.lastIndexOf('\n', p - 1) + 1
    val le = text.indexOf('\n', p).let { if (it == -1) text.length else it }
    val line = text.substring(ls, le)
    val trimmed = line.trimStart()
    return when {
        line.startsWith("### ") -> LinePrefix.HEADING3
        line.startsWith("## ") -> LinePrefix.HEADING2
        line.startsWith("# ") -> LinePrefix.HEADING1
        line.startsWith(">") -> LinePrefix.QUOTE
        Regex("""^[-*+] \[[ xX]] """).containsMatchIn(trimmed) -> LinePrefix.TASK
        Regex("""^[-*+] """).containsMatchIn(trimmed) -> LinePrefix.BULLET
        Regex("""^\d{1,9}[.)] """).containsMatchIn(trimmed) -> LinePrefix.NUMBERED
        else -> null
    }
}

/** Marca o desmarca la casilla de la tarea cuyo marcador está en [markerStart, markerEnd). */
fun toggleTaskBox(text: String, box: TaskBox): String {
    val marker = text.substring(box.markerStart, box.markerEnd)
    val bracket = marker.indexOf('[')
    if (bracket == -1 || bracket + 2 >= marker.length) return text
    val pos = box.markerStart + bracket + 1
    val replacement = if (box.checked) ' ' else 'x'
    return text.substring(0, pos) + replacement + text.substring(pos + 1)
}

/** Inserta o sustituye un enlace: [label](url). Si [existing] no es null se reemplaza ese enlace. */
fun applyLink(text: String, selStart: Int, selEnd: Int, label: String, url: String, existing: InlineToken?): TextEdit {
    val cleanLabel = label.replace('\n', ' ').replace("]", "").trim().ifEmpty { url }
    val markdown = "[$cleanLabel](${normalizeUrl(url)})"
    val start = existing?.start ?: minOf(selStart, selEnd).coerceIn(0, text.length)
    val end = existing?.end ?: maxOf(selStart, selEnd).coerceIn(start, text.length)
    return TextEdit(text.substring(0, start) + markdown + text.substring(end), start + markdown.length)
}

/** Quita el enlace dejando solo su texto. */
fun removeLink(text: String, link: InlineToken): TextEdit {
    val label = text.substring(link.contentStart, link.contentEnd)
    return TextEdit(text.substring(0, link.start) + label + text.substring(link.end), link.start + label.length)
}

/** Añade https:// si el usuario escribe solo "ejemplo.com" y quita espacios (rompen el Markdown). */
fun normalizeUrl(url: String): String {
    val u = url.trim().replace(" ", "%20").replace(")", "%29")
    if (u.isEmpty()) return u
    val hasScheme = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""").containsMatchIn(u)
    return if (hasScheme) u else "https://$u"
}

/**
 * Deja el Markdown limpio antes de guardarlo: mientras se escribe en negrita el texto puede
 * acabar en "**hola **"; otros programas no lo reconocerían, así que el espacio final se saca
 * fuera de los marcadores ("**hola** "). No toca bloques de código.
 */
fun normalizeMarkdown(markdown: String): String {
    var text = markdown
    repeat(200) {
        val fix = findTrailingSpaceToken(text) ?: return text
        val content = text.substring(fix.contentStart, fix.contentEnd)
        val trimmed = content.trimEnd()
        val spaces = content.substring(trimmed.length)
        val closing = text.substring(fix.contentEnd, fix.end)
        text = text.substring(0, fix.contentStart) + trimmed + closing + spaces + text.substring(fix.end)
    }
    return text
}

/**
 * Primer elemento en negrita, cursiva o tachado cuyo contenido acaba en espacio, fuera de los
 * bloques de código; `null` si no hay ninguno.
 */
private fun findTrailingSpaceToken(text: String): InlineToken? {
    var inFence = false
    var ls = 0
    while (ls <= text.length) {
        val le = text.indexOf('\n', ls).let { if (it == -1) text.length else it }
        val line = text.substring(ls, le)
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
        } else if (!inFence) {
            InlineMarkdown.parse(text, ls, le).firstOrNull {
                (it.kind == InlineKind.BOLD || it.kind == InlineKind.ITALIC || it.kind == InlineKind.STRIKE) &&
                    it.contentEnd > it.contentStart && text[it.contentEnd - 1].isWhitespace()
            }?.let { return it }
        }
        if (le == text.length) break
        ls = le + 1
    }
    return null
}
