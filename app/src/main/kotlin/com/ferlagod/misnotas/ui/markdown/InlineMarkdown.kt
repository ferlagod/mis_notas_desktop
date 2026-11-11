// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

/*
 * Análisis de Markdown compartido por el lector y el editor visual.
 *
 * Las notas se guardan siempre como Markdown, pero el usuario no tiene por qué verlo: el editor
 * oculta los marcadores (`**`, `# `, `- [ ] `…) y muestra el texto con su formato. Para ello este
 * fichero describe, sobre el texto original, qué tramos se ocultan o se sustituyen ([Replacement])
 * y qué estilo lleva cada tramo ([StyleRange]). Son funciones puras para poder probarlas.
 */

/** Tipos de elemento en línea: negrita, cursiva, tachado, código, enlace, imagen y URL suelta. */
enum class InlineKind { BOLD, ITALIC, STRIKE, CODE, LINK, IMAGE, AUTOLINK }

/**
 * Elemento en línea en [start, end). El contenido visible está en [contentStart, contentEnd);
 * el resto son marcadores. En enlaces e imágenes [url] es el destino.
 */
data class InlineToken(
    val kind: InlineKind,
    val start: Int,
    val end: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val url: String? = null
)

/** Analizador de los elementos en línea de Markdown. */
object InlineMarkdown {

    /** Elementos en línea de text[from, to), incluidos los anidados (negrita dentro de enlace…). */
    fun parse(text: String, from: Int = 0, to: Int = text.length): List<InlineToken> {
        val out = mutableListOf<InlineToken>()
        parseRange(text, from.coerceIn(0, text.length), to.coerceIn(0, text.length), out)
        return out
    }

    /**
     * Recorre text[from, to) y añade a [out] los elementos que encuentra; entra en los que admiten
     * anidación (negrita, cursiva, tachado y enlaces). Una `\` escapa el carácter siguiente.
     */
    private fun parseRange(text: String, from: Int, to: Int, out: MutableList<InlineToken>) {
        var i = from
        while (i < to) {
            val token = when (text[i]) {
                '\\' -> {
                    i += 2
                    continue
                }
                '`' -> code(text, i, to)
                '!' -> if (i + 1 < to && text[i + 1] == '[') link(text, i, to, image = true) else null
                '[' -> link(text, i, to, image = false)
                '*' -> (if (text.startsWith("**", i)) delimited(text, i, to, "**", InlineKind.BOLD) else null)
                    ?: delimited(text, i, to, "*", InlineKind.ITALIC)
                '_' -> if (i > 0 && text[i - 1].isLetterOrDigit()) null
                else (if (text.startsWith("__", i)) delimited(text, i, to, "__", InlineKind.BOLD) else null)
                    ?: delimited(text, i, to, "_", InlineKind.ITALIC)
                '~' -> if (text.startsWith("~~", i)) delimited(text, i, to, "~~", InlineKind.STRIKE) else null
                'h' -> autolink(text, i, from, to)
                else -> null
            }
            if (token == null) {
                i++
                continue
            }
            out += token
            if (token.kind == InlineKind.BOLD || token.kind == InlineKind.ITALIC ||
                token.kind == InlineKind.STRIKE || token.kind == InlineKind.LINK
            ) {
                parseRange(text, token.contentStart, token.contentEnd, out)
            }
            i = token.end
        }
    }

    /**
     * `**x**`, `*x*`, `~~x~~`, `__x__`, `_x_`. El contenido no puede empezar por espacio (así
     * `2 * 3` no es cursiva), pero sí terminar en uno: mientras se escribe en negrita el texto
     * pasa por "**hola **" y no debe parpadear mostrando los asteriscos.
     */
    private fun delimited(text: String, i: Int, to: Int, marker: String, kind: InlineKind): InlineToken? {
        val single = marker.length == 1
        val mc = marker[0]
        val cs = i + marker.length
        if (cs >= to) return null
        val first = text[cs]
        if (first.isWhitespace()) return null
        if (single && first == mc) return null
        var j = cs + 1
        while (j + marker.length <= to) {
            val ch = text[j]
            if (ch == '`') {
                // Lo que hay dentro de un código no cierra nada.
                val close = text.indexOf('`', j + 1)
                if (close in (j + 2) until to) {
                    j = close + 1
                    continue
                }
            }
            if (single && j + 1 < to && ch == mc && text[j + 1] == mc) {
                // Dentro de una cursiva, "**" es una negrita anidada, no el cierre.
                var k = j
                while (k < to && text[k] == mc) k++
                j = k
                continue
            }
            if (text.startsWith(marker, j)) {
                val after = text.getOrNull(j + marker.length)
                // En una racha "***" el cierre es el último par.
                if (!single && after == mc && j + marker.length < to) {
                    j++
                    continue
                }
                if (mc == '_' && after != null && after.isLetterOrDigit()) {
                    j++
                    continue
                }
                return InlineToken(kind, i, j + marker.length, cs, j)
            }
            j++
        }
        return null
    }

    /** Código en línea `` `x` `` que empieza en [i]; `null` si no se cierra antes de [to] o está vacío. */
    private fun code(text: String, i: Int, to: Int): InlineToken? {
        val close = text.indexOf('`', i + 1)
        if (close == -1 || close >= to || close == i + 1) return null
        return InlineToken(InlineKind.CODE, i, close + 1, i + 1, close)
    }

    /**
     * Enlace `[texto](url)` o imagen `![texto](url)` que empieza en [i]. La URL no puede llevar
     * espacios y el texto de un enlace no puede estar vacío ni ocupar varias líneas.
     */
    private fun link(text: String, i: Int, to: Int, image: Boolean): InlineToken? {
        val open = if (image) i + 1 else i
        val close = text.indexOf(']', open + 1)
        if (close == -1 || close + 1 >= to || text[close + 1] != '(') return null
        if (!image && close == open + 1) return null
        if (text.indexOf('\n', open).let { it in open until close }) return null
        val urlStart = close + 2
        var p = urlStart
        while (p < to && text[p] != ')' && !text[p].isWhitespace()) p++
        if (p >= to || text[p] != ')' || p == urlStart) return null
        return InlineToken(
            if (image) InlineKind.IMAGE else InlineKind.LINK,
            i, p + 1, open + 1, close, text.substring(urlStart, p)
        )
    }

    /** URL suelta `http(s)://…` que empieza en [i]. No incluye la puntuación final (".", ","…). */
    private fun autolink(text: String, i: Int, from: Int, to: Int): InlineToken? {
        if (!text.startsWith("http://", i) && !text.startsWith("https://", i)) return null
        if (i > from && text[i - 1].isLetterOrDigit()) return null
        var end = i
        while (end < to && !text[end].isWhitespace() && text[end] !in "<>()[]") end++
        while (end > i && text[end - 1] in ".,;:!?'\"") end--
        if (end - i <= "https://".length) return null
        return InlineToken(InlineKind.AUTOLINK, i, end, i, end, text.substring(i, end))
    }
}

// ----------------------------------------------------------------------
// Presentación
// ----------------------------------------------------------------------

/** Tipo de tramo sustituido, que decide cómo se comporta al borrar. */
enum class ReplacementKind {
    /** Marcador de bloque al principio de línea (`# `, `- `, `- [ ] `…). Borrarlo quita el formato de la línea. */
    BLOCK,
    /** Marcador en línea oculto (`**`, `[`, `](url)`…). */
    INLINE,
    /** Elemento que se ve como una unidad (imagen, separador) y se borra entero. */
    ATOMIC
}

/** El tramo original [start, end) se muestra como [display] ("" = oculto). */
data class Replacement(val start: Int, val end: Int, val display: String, val kind: ReplacementKind)

/** Estilos visuales que el editor aplica a los tramos del texto. */
enum class MdStyle { BOLD, ITALIC, STRIKE, CODE, LINK, HEADING, MARKER, TASK_DONE, QUOTE, CODE_BLOCK, FENCE, RULE, IMAGE }

/** Estilo sobre el tramo original [start, end). [level] es el nivel de título; [url] el de un enlace. */
data class StyleRange(val start: Int, val end: Int, val style: MdStyle, val level: Int = 0, val url: String? = null)

/** Una casilla de tarea en la línea que empieza en [lineStart]. */
data class TaskBox(val lineStart: Int, val markerStart: Int, val markerEnd: Int, val checked: Boolean)

/**
 * Resultado de analizar un texto: qué se oculta o sustituye, qué estilo lleva cada tramo,
 * los elementos en línea y las casillas de tarea.
 */
class MarkdownPresentation(
    val replacements: List<Replacement>,
    val styles: List<StyleRange>,
    val inlineTokens: List<InlineToken>,
    val tasks: List<TaskBox>
) {
    /** Reemplazo que contiene el carácter original [index], si lo hay. */
    fun replacementAt(index: Int): Replacement? =
        replacements.firstOrNull { index >= it.start && index < it.end }
}

/** Viñeta, casilla o número al principio de línea (con su sangría). */
private val BLOCK_PREFIX = Regex("""^(\s*)([-*+] \[[ xX]] |[-*+] |\d{1,9}[.)] )""")
/** Título `# ` … `###### `. */
private val HEADING_PREFIX = Regex("""^(#{1,6}) """)
/** Cita `> `. */
private val QUOTE_PREFIX = Regex("""^> ?""")
/** Línea que solo contiene una imagen. */
private val WHOLE_LINE_IMAGE = Regex("""^\s*!\[(.*?)]\((.*?)\)\s*$""")
/** Separador horizontal (`---`, `***`, `___`). */
private val RULE_LINE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

/** Calcula cómo se muestra un texto Markdown (ver [MarkdownPresentation]). */
object MarkdownPresenter {

    /** Solo formato en línea (lo usa el lector para cada párrafo, celda, elemento de lista…). */
    fun inline(text: String, imageLabel: String): MarkdownPresentation {
        val reps = mutableListOf<Replacement>()
        val styles = mutableListOf<StyleRange>()
        val tokens = presentInline(text, 0, text.length, imageLabel, reps, styles)
        return MarkdownPresentation(reps.sortedBy { it.start }, styles, tokens, emptyList())
    }

    /** Documento completo, línea a línea (lo usa el editor visual). */
    fun document(text: String, imageLabel: String): MarkdownPresentation {
        val reps = mutableListOf<Replacement>()
        val styles = mutableListOf<StyleRange>()
        val tokens = mutableListOf<InlineToken>()
        val tasks = mutableListOf<TaskBox>()
        var inFence = false
        var ls = 0
        while (ls <= text.length) {
            val le = text.indexOf('\n', ls).let { if (it == -1) text.length else it }
            val line = text.substring(ls, le)
            when {
                line.trimStart().startsWith("```") -> {
                    styles += StyleRange(ls, le, MdStyle.FENCE)
                    inFence = !inFence
                }
                inFence -> styles += StyleRange(ls, le, MdStyle.CODE_BLOCK)
                RULE_LINE.matches(line) -> {
                    reps += Replacement(ls, le, "─".repeat(12), ReplacementKind.ATOMIC)
                    styles += StyleRange(ls, le, MdStyle.RULE)
                }
                WHOLE_LINE_IMAGE.matches(line) -> {
                    tokens += presentInline(text, ls, le, imageLabel, reps, styles)
                }
                else -> presentLine(text, ls, le, line, imageLabel, reps, styles, tokens, tasks)
            }
            if (le == text.length) break
            ls = le + 1
        }
        return MarkdownPresentation(reps.sortedBy { it.start }, styles, tokens, tasks)
    }

    /**
     * Analiza una línea [ls, le): marcador de bloque (título, cita, lista, tarea), imagen o separador
     * de línea completa, y después el formato en línea del resto.
     */
    private fun presentLine(
        text: String,
        ls: Int,
        le: Int,
        line: String,
        imageLabel: String,
        reps: MutableList<Replacement>,
        styles: MutableList<StyleRange>,
        tokens: MutableList<InlineToken>,
        tasks: MutableList<TaskBox>
    ) {
        var contentStart = ls
        HEADING_PREFIX.find(line)?.let { m ->
            val level = m.groupValues[1].length
            reps += Replacement(ls, ls + m.value.length, "", ReplacementKind.BLOCK)
            contentStart = ls + m.value.length
            styles += StyleRange(contentStart, le, MdStyle.HEADING, level = level)
        } ?: QUOTE_PREFIX.find(line)?.let { m ->
            reps += Replacement(ls, ls + m.value.length, "▍ ", ReplacementKind.BLOCK)
            styles += StyleRange(ls, ls + m.value.length, MdStyle.MARKER)
            contentStart = ls + m.value.length
            styles += StyleRange(contentStart, le, MdStyle.QUOTE)
        } ?: BLOCK_PREFIX.find(line)?.let { m ->
            val ms = ls + m.groups[1]!!.value.length
            val me = ls + m.value.length
            val marker = m.groups[2]!!.value
            val display = when {
                marker.length == 6 && marker[3] == ' ' -> "☐ "
                marker.length == 6 -> "☑ "
                marker.length == 2 && marker[0] in "-*+" -> "• "
                else -> marker
            }
            reps += Replacement(ms, me, display, ReplacementKind.BLOCK)
            styles += StyleRange(ms, me, MdStyle.MARKER)
            contentStart = me
            if (marker.length == 6) {
                val checked = marker[3] != ' '
                tasks += TaskBox(ls, ms, me, checked)
                if (checked) styles += StyleRange(contentStart, le, MdStyle.TASK_DONE)
            }
        }
        tokens += presentInline(text, contentStart, le, imageLabel, reps, styles)
    }

    /**
     * Oculta los marcadores en línea de text[from, to) y añade sus estilos. Las imágenes se sustituyen
     * por una etiqueta ([imageLabel] y su tamaño). Devuelve los elementos encontrados.
     */
    private fun presentInline(
        text: String,
        from: Int,
        to: Int,
        imageLabel: String,
        reps: MutableList<Replacement>,
        styles: MutableList<StyleRange>
    ): List<InlineToken> {
        val tokens = InlineMarkdown.parse(text, from, to)
        for (t in tokens) {
            when (t.kind) {
                InlineKind.IMAGE -> {
                    val alt = text.substring(t.contentStart, t.contentEnd).trim()
                    val percent = t.url?.let { ImageSizing.widthPercent(it) } ?: ImageSizing.DEFAULT_PERCENT
                    val size = if (percent < ImageSizing.DEFAULT_PERCENT) " · $percent%" else ""
                    reps += Replacement(t.start, t.end, "🖼 " + alt.ifEmpty { imageLabel } + size, ReplacementKind.ATOMIC)
                    styles += StyleRange(t.start, t.end, MdStyle.IMAGE)
                }
                InlineKind.AUTOLINK -> styles += StyleRange(t.start, t.end, MdStyle.LINK, url = t.url)
                else -> {
                    reps += Replacement(t.start, t.contentStart, "", ReplacementKind.INLINE)
                    reps += Replacement(t.contentEnd, t.end, "", ReplacementKind.INLINE)
                    styles += StyleRange(
                        t.contentStart, t.contentEnd,
                        when (t.kind) {
                            InlineKind.BOLD -> MdStyle.BOLD
                            InlineKind.ITALIC -> MdStyle.ITALIC
                            InlineKind.STRIKE -> MdStyle.STRIKE
                            InlineKind.CODE -> MdStyle.CODE
                            else -> MdStyle.LINK
                        },
                        url = t.url
                    )
                }
            }
        }
        // Las imágenes ocultan cualquier otro marcador que haya dentro de ellas.
        val atomics = reps.filter { it.kind == ReplacementKind.ATOMIC }
        reps.removeAll { r -> r.kind != ReplacementKind.ATOMIC && atomics.any { a -> r.start >= a.start && r.end <= a.end } }
        return tokens
    }
}

// ----------------------------------------------------------------------
// Texto mostrado y correspondencia de posiciones
// ----------------------------------------------------------------------

/**
 * Texto tal como se ve y correspondencia de posiciones con el original.
 *
 * Reglas del cursor: dentro de un marcador oculto el cursor queda detrás de él; dentro de un
 * marcador de bloque (viñeta, casilla…) queda al principio del contenido; una imagen se trata
 * como un solo carácter.
 */
class DisplayMapping(
    val display: String,
    val originalToDisplay: IntArray,
    val displayToOriginal: IntArray
) {
    /** Posición en el texto mostrado que corresponde a [offset] del original. */
    fun toDisplay(offset: Int): Int = originalToDisplay[offset.coerceIn(0, originalToDisplay.size - 1)]
    /** Posición en el texto original que corresponde a [offset] del mostrado. */
    fun toOriginal(offset: Int): Int = displayToOriginal[offset.coerceIn(0, displayToOriginal.size - 1)]

    companion object {
        /**
         * Construye el texto mostrado aplicando [replacements] a [source], con la correspondencia de posiciones.
         */
        fun build(source: String, replacements: List<Replacement>): DisplayMapping {
            val len = source.length
            val sb = StringBuilder(len)
            val o2t = IntArray(len + 1)
            val t2o = ArrayList<Int>(len + 1)
            var i = 0
            var r = 0
            val reps = replacements.filter { it.start in 0..len && it.end in it.start..len }
            while (i <= len) {
                while (r < reps.size && reps[r].start < i) r++ // solapados: se ignoran
                val rep = reps.getOrNull(r)?.takeIf { it.start == i && it.end > it.start }
                if (rep != null) {
                    val tStart = sb.length
                    for (k in rep.display.indices) {
                        t2o += if (k == 0 && rep.kind == ReplacementKind.ATOMIC) rep.start else rep.end
                        sb.append(rep.display[k])
                    }
                    val tEnd = sb.length
                    o2t[rep.start] = tStart
                    for (x in rep.start + 1 until rep.end) o2t[x] = tEnd
                    i = rep.end
                    r++
                    continue
                }
                o2t[i] = sb.length
                if (i < len) {
                    t2o += i
                    sb.append(source[i])
                }
                i++
            }
            t2o += len
            return DisplayMapping(sb.toString(), o2t, t2o.toIntArray())
        }
    }
}
