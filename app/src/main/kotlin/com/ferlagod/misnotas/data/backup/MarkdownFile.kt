// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.backup

import java.time.Instant
import java.time.format.DateTimeParseException

/** Nota leída de un fichero Markdown con cabecera YAML opcional. */
data class ParsedMarkdownNote(
    /** Título de la cabecera, o el nombre del fichero si no tiene. */
    val title: String,
    /** Cuerpo Markdown, sin la cabecera. */
    val content: String,
    /** `pinned: true` en la cabecera. */
    val isPinned: Boolean = false,
    /** Clave del color en mayúsculas, o `null` si no viene. */
    val color: String? = null,
    /** Fecha `updated` en milisegundos, o `null` si falta o no es válida. */
    val updatedAt: Long? = null
)

/**
 * Formato de los ficheros `.md` de la copia de seguridad: Markdown normal con una cabecera
 * ("front matter") que conserva los metadatos de la nota. Lo entienden Obsidian, Joplin, etc.
 *
 * ```
 * ---
 * title: "Lista de la compra"
 * pinned: true
 * color: YELLOW
 * updated: 2026-11-08T10:15:00Z
 * ---
 *
 * - [ ] Leche
 * ```
 */
object MarkdownFile {

    /** Marca de orden de bytes que algunos editores añaden al principio de los ficheros UTF-8. */
    private val BOM = 0xFEFF.toChar().toString()

    /**
     * Genera el fichero `.md` de una nota: cabecera con los metadatos y después el contenido.
     * `pinned` y `color` solo se escriben si no tienen el valor por defecto.
     */
    fun write(title: String, content: String, isPinned: Boolean, color: String, updatedAt: Long): String = buildString {
        append("---\n")
        append("title: ").append(quote(title)).append('\n')
        if (isPinned) append("pinned: true\n")
        if (color != "DEFAULT") append("color: ").append(color).append('\n')
        append("updated: ").append(Instant.ofEpochMilli(updatedAt)).append('\n')
        append("---\n\n")
        append(content)
        if (!content.endsWith("\n")) append('\n')
    }

    /** Lee un fichero; si no tiene cabecera, el título es [fallbackTitle] y el texto entero es el contenido. */
    fun parse(raw: String, fallbackTitle: String): ParsedMarkdownNote {
        val text = raw.removePrefix(BOM).replace("\r\n", "\n")
        if (!text.startsWith("---\n")) return ParsedMarkdownNote(fallbackTitle, text.trimEnd())

        val end = text.indexOf("\n---", startIndex = 3)
        if (end == -1) return ParsedMarkdownNote(fallbackTitle, text.trimEnd())

        val header = text.substring(4, end)
        val bodyStart = text.indexOf('\n', end + 4).let { if (it == -1) text.length else it + 1 }
        val body = text.substring(bodyStart).trimStart('\n').trimEnd()

        val fields = header.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) null else line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
            }
            .toMap()

        return ParsedMarkdownNote(
            title = fields["title"]?.let(::unquote)?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            content = body,
            isPinned = fields["pinned"].equals("true", ignoreCase = true),
            color = fields["color"]?.let(::unquote)?.uppercase()?.takeIf { it.isNotBlank() },
            updatedAt = fields["updated"]?.let(::unquote)?.let { parseInstant(it) }
        )
    }

    /** Nombre de fichero seguro en cualquier sistema (sin barras, caracteres reservados ni ".."). */
    fun safeFileName(name: String, fallback: String = "nota", maxLength: Int = 80): String {
        val cleaned = name
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim()
            .trim('.')
            .take(maxLength)
            .trim()
        return cleaned.ifEmpty { fallback }
    }

    /** Devuelve [base] + [extension] sin repetir ninguno de [taken] ("Nota.md", "Nota (2).md"…). */
    fun uniqueName(base: String, extension: String, taken: MutableSet<String>): String {
        var candidate = base + extension
        var n = 2
        while (!taken.add(candidate.lowercase())) {
            candidate = "$base ($n)$extension"
            n++
        }
        return candidate
    }

    /** Entrecomilla un valor de la cabecera, escapando `\` y `"` y sustituyendo saltos de línea. */
    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    /** Quita las comillas dobles o simples de un valor de la cabecera y deshace el escape. */
    private fun unquote(value: String): String {
        val v = value.trim()
        return when {
            v.length >= 2 && v.startsWith('"') && v.endsWith('"') ->
                v.substring(1, v.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            v.length >= 2 && v.startsWith('\'') && v.endsWith('\'') ->
                v.substring(1, v.length - 1).replace("''", "'")
            else -> v
        }
    }

    /** Lee una fecha ISO-8601 (`2026-11-08T10:15:00Z`); `null` si no es válida. */
    private fun parseInstant(value: String): Long? = try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
