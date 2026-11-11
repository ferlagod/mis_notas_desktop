// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.w3c.dom.Element
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Textos de la app. Lee los mismos `strings.xml` que la versión de Android (en
 * `resources/i18n/values-<idioma>/`), con sus reglas de escape, `plurals` y marcadores
 * (`%1$s`, `%d`). `values/` es el inglés por defecto.
 */
object Strings {

    /** Textos de un idioma: cadenas simples y plurales (por categoría: `one`, `other`…). */
    private class Table(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    /** Tablas ya cargadas, por carpeta (`values`, `values-es`…). */
    private val cache = ConcurrentHashMap<String, Table>()

    /** Idioma activo; al cambiarlo, la interfaz se vuelve a componer. */
    var locale: Locale by mutableStateOf(Locale.getDefault())
        private set

    /** Cambia el idioma de la app (también el `Locale` por defecto, que usan fechas y números). */
    fun applyLocale(value: Locale) {
        Locale.setDefault(value)
        locale = value
    }

    /** Tabla de una carpeta; la carga la primera vez. */
    private fun table(folder: String): Table = cache.getOrPut(folder) { load(folder) }

    /** Tablas en orden de búsqueda: idioma concreto y luego inglés. */
    private fun tables(locale: Locale): List<Table> {
        val lang = locale.language
        return if (lang.isNullOrEmpty() || lang == "en") listOf(table("values"))
        else listOf(table("values-$lang"), table("values"))
    }

    /**
     * Texto de [key] en el idioma activo (o en inglés si falta), con [args] sustituidos.
     * Si la clave no existe devuelve la propia clave.
     */
    fun get(key: String, vararg args: Any?): String {
        val loc = locale
        val raw = tables(loc).firstNotNullOfOrNull { it.strings[key] } ?: return key
        return format(raw, loc, args)
    }

    /** Texto plural de [key] para [count]. Si no se pasan [args], se usa [count] como argumento. */
    fun quantity(key: String, count: Int, vararg args: Any?): String {
        val loc = locale
        val category = pluralCategory(loc, count)
        val raw = tables(loc).firstNotNullOfOrNull { t ->
            t.plurals[key]?.let { it[category] ?: it["other"] }
        } ?: return key
        return format(raw, loc, if (args.isEmpty()) arrayOf(count) else args)
    }

    /**
     * Sustituye los marcadores; si el texto traducido tiene marcadores incorrectos, lo devuelve sin formatear.
     */
    private fun format(raw: String, locale: Locale, args: Array<out Any?>): String =
        if (args.isEmpty()) raw else try {
            String.format(locale, raw, *args)
        } catch (_: Exception) {
            raw
        }

    /** Reglas CLDR simplificadas para los idiomas de la app. */
    private fun pluralCategory(locale: Locale, n: Int): String = when (locale.language) {
        "fr" -> if (n == 0 || n == 1) "one" else "other"
        else -> if (n == 1) "one" else "other"
    }

    /** Lee el `strings.xml` de [folder]; tabla vacía si no existe. */
    private fun load(folder: String): Table {
        val stream = Strings::class.java.getResourceAsStream("/i18n/$folder/strings.xml")
            ?: return Table(emptyMap(), emptyMap())
        val doc = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as Element
            strings[el.getAttribute("name")] = unescape(el.textContent)
        }
        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as Element
            val items = el.getElementsByTagName("item")
            plurals[el.getAttribute("name")] = (0 until items.length).associate { j ->
                val item = items.item(j) as Element
                item.getAttribute("quantity") to unescape(item.textContent)
            }
        }
        return Table(strings, plurals)
    }

    /**
     * Interpreta el texto como Android: `\n`, `\t`, `\'`, `\"`, `\\`, `\uXXXX`; las comillas
     * dobles sin escapar delimitan tramos donde se conservan los espacios; fuera de ellas los
     * espacios seguidos se reducen a uno y se recortan los extremos.
     */
    internal fun unescape(raw: String): String {
        val out = StringBuilder()
        var inQuotes = false
        var i = 0
        var pendingSpace = false
        fun flushSpace() {
            if (pendingSpace && out.isNotEmpty()) out.append(' ')
            pendingSpace = false
        }
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> {
                    flushSpace()
                    when (val n = raw[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> if (i + 5 < raw.length) {
                            raw.substring(i + 2, i + 6).toIntOrNull(16)?.let { out.append(it.toChar()) }
                            i += 4
                        }
                        else -> out.append(n)
                    }
                    i += 2
                    continue
                }
                c == '"' -> inQuotes = !inQuotes
                c.isWhitespace() && !inQuotes -> pendingSpace = true
                else -> {
                    flushSpace()
                    out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }
}

/** Equivalente a `stringResource` de Android. */
@Composable
fun stringResource(key: String, vararg formatArgs: Any?): String {
    // Leer el idioma suscribe la composición a sus cambios.
    Strings.locale
    return Strings.get(key, *formatArgs)
}

/** Equivalente a `pluralStringResource` de Android. */
@Composable
fun pluralStringResource(key: String, count: Int, vararg formatArgs: Any?): String {
    Strings.locale
    return Strings.quantity(key, count, *formatArgs)
}
