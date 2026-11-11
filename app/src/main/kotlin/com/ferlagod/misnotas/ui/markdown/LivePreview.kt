// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/** Colores con los que se pinta el Markdown. */
data class MarkdownColors(
    val accent: Color,
    val text: Color,
    val muted: Color,
    val codeBackground: Color,
    val rule: Color
)

/** Texto ya pintado y su correspondencia de posiciones con el Markdown original. */
class RenderedMarkdown(
    val source: String,
    val presentation: MarkdownPresentation,
    val text: AnnotatedString,
    val mapping: DisplayMapping
)

/** Tamaño de letra de cada nivel de título en el editor. */
private fun headingSize(level: Int) = when (level) {
    1 -> 26.sp
    2 -> 22.sp
    3 -> 19.sp
    else -> 17.sp
}

/**
 * Pinta [presentation] sobre [source]: oculta o sustituye marcadores y aplica estilos.
 * Con [clickableLinks] los enlaces se abren al pulsarlos (lector); en el editor solo se colorean.
 */
fun renderMarkdown(
    source: String,
    presentation: MarkdownPresentation,
    colors: MarkdownColors,
    clickableLinks: Boolean
): RenderedMarkdown {
    val mapping = DisplayMapping.build(source, presentation.replacements)
    val builder = AnnotatedString.Builder(mapping.display)
    for (range in presentation.styles) {
        val start = mapping.toDisplay(range.start)
        val end = mapping.toDisplay(range.end)
        if (end <= start) continue
        when (range.style) {
            MdStyle.BOLD -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            MdStyle.ITALIC -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            MdStyle.STRIKE -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            MdStyle.CODE -> builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground), start, end
            )
            MdStyle.LINK -> {
                val style = SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)
                val url = range.url
                if (clickableLinks && url != null &&
                    (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:"))
                ) {
                    builder.addLink(LinkAnnotation.Url(url, TextLinkStyles(style)), start, end)
                } else {
                    builder.addStyle(style, start, end)
                }
            }
            MdStyle.HEADING -> {
                val size = headingSize(range.level)
                builder.addStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold), start, end)
                builder.addStyle(ParagraphStyle(lineHeight = size * 1.3f), start, end)
            }
            MdStyle.MARKER -> builder.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), start, end)
            MdStyle.TASK_DONE -> builder.addStyle(
                SpanStyle(color = colors.muted, textDecoration = TextDecoration.LineThrough), start, end
            )
            MdStyle.QUOTE -> builder.addStyle(SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic), start, end)
            MdStyle.CODE_BLOCK -> builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground), start, end
            )
            MdStyle.FENCE -> builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = colors.muted), start, end)
            MdStyle.RULE -> builder.addStyle(SpanStyle(color = colors.rule), start, end)
            MdStyle.IMAGE -> builder.addStyle(
                SpanStyle(color = colors.accent, background = colors.accent.copy(alpha = 0.10f)), start, end
            )
        }
    }
    return RenderedMarkdown(source, presentation, builder.toAnnotatedString(), mapping)
}

/**
 * Guarda el último documento analizado y pintado: el editor lo necesita varias veces por
 * pulsación (para pintar, para corregir ediciones y para la barra de formato).
 */
class MarkdownDocumentCache(private val imageLabel: String, private val colors: MarkdownColors) {
    private var last: RenderedMarkdown? = null
    private var previous: RenderedMarkdown? = null

    fun get(source: String): RenderedMarkdown {
        last?.let { if (it.source == source) return it }
        previous?.let {
            if (it.source == source) {
                previous = last
                last = it
                return it
            }
        }
        val rendered = renderMarkdown(source, MarkdownPresenter.document(source, imageLabel), colors, clickableLinks = false)
        previous = last
        last = rendered
        return rendered
    }
}

/**
 * Editor visual: el campo de texto contiene el Markdown, pero se ve con formato y sin marcadores.
 */
class LivePreviewTransformation(private val cache: MarkdownDocumentCache) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rendered = cache.get(text.text)
        val mapping = rendered.mapping
        return TransformedText(
            rendered.text,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = mapping.toDisplay(offset)
                override fun transformedToOriginal(offset: Int): Int = mapping.toOriginal(offset)
            }
        )
    }

    override fun equals(other: Any?): Boolean = other is LivePreviewTransformation && other.cache === cache
    override fun hashCode(): Int = System.identityHashCode(cache)
}
