// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.i18n

import com.ferlagod.misnotas.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class StringsTest {

    private val original = Locale.getDefault()

    @After
    fun restore() = Strings.applyLocale(original)

    @Test
    fun unescapeFollowsAndroidRules() {
        assertEquals("It's \"ok\"", Strings.unescape("""It\'s \"ok\""""))
        assertEquals("a\nb", Strings.unescape("""a\nb"""))
        assertEquals("a b", Strings.unescape("  a   \n  b  "))
        assertEquals("  kept  ", Strings.unescape("\"  kept  \""))
    }

    @Test
    fun formatsAndFallsBackToEnglish() {
        Strings.applyLocale(Locale.forLanguageTag("es"))
        assertEquals("Error del servidor (500)", Strings.get(R.string.err_server, 500))
        Strings.applyLocale(Locale.forLanguageTag("pt"))
        assertEquals("Server error (500)", Strings.get(R.string.err_server, 500))
    }

    @Test
    fun pluralRules() {
        Strings.applyLocale(Locale.ENGLISH)
        assertEquals("1 word", Strings.quantity(R.plurals.word_count, 1))
        assertEquals("2 words", Strings.quantity(R.plurals.word_count, 2))
        Strings.applyLocale(Locale.FRENCH)
        // En francés el 0 usa la forma singular.
        assertEquals(Strings.quantity(R.plurals.word_count, 1).replace("1", "0"), Strings.quantity(R.plurals.word_count, 0))
    }

    /** Todos los idiomas tienen las mismas claves y los mismos marcadores que el inglés. */
    @Test
    fun translationsAreComplete() {
        val base = load("values")
        for (lang in listOf("es", "fr", "de", "it")) {
            val other = load("values-$lang")
            for ((key, value) in base) {
                val translated = other[key]
                assertTrue("Falta $key en $lang", translated != null)
                assertEquals("Marcadores distintos en $key ($lang)", placeholders(value), placeholders(translated!!))
            }
        }
    }

    private fun placeholders(s: String) = Regex("""%(\d+\$)?[sd]""").findAll(s).map { it.value }.sorted().toList()

    private fun load(folder: String): Map<String, String> {
        val doc = javaClass.getResourceAsStream("/i18n/$folder/strings.xml")!!.use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val out = HashMap<String, String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val el = strings.item(i) as Element
            if (el.getAttribute("translatable") != "false") out[el.getAttribute("name")] = el.textContent
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val el = plurals.item(i) as Element
            val items = el.getElementsByTagName("item")
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                if (item.getAttribute("quantity") == "other") out["plurals:" + el.getAttribute("name")] = item.textContent
            }
        }
        return out
    }
}
