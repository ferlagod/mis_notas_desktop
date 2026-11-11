// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Edición de Markdown: continuación de listas y formato de bloque. */
class MarkdownEditingTest {

    // --- Continuar listas al pulsar Intro ---

    private fun enter(text: String, cursor: Int): TextEdit? {
        val newText = text.substring(0, cursor) + "\n" + text.substring(cursor)
        return continueList(text, newText, cursor + 1)
    }

    @Test
    fun continuesBulletList() {
        val edit = enter("- leche", 7)!!
        assertEquals("- leche\n- ", edit.text)
        assertEquals(10, edit.selectionStart)
    }

    @Test
    fun continuesTaskListUnchecked() {
        assertEquals("- [x] hecho\n- [ ] ", enter("- [x] hecho", 11)!!.text)
    }

    @Test
    fun incrementsNumberedList() {
        assertEquals("  9) nueve\n  10) ", enter("  9) nueve", 10)!!.text)
    }

    @Test
    fun emptyItemEndsList() {
        val edit = enter("- uno\n- ", 8)!!
        assertEquals("- uno\n", edit.text)
        assertEquals(6, edit.selectionStart)
    }

    @Test
    fun ignoresNormalTextAndPastes() {
        assertNull(enter("hola", 4))
        assertNull(continueList("- a", "- a\nb", 4))
    }

    @Test
    fun splitsItemInTheMiddle() {
        // Intro en mitad de "- leche fresca" lleva "fresca" a un elemento nuevo.
        assertEquals("- leche\n- fresca", enter("- leche fresca", 7)!!.text)
    }

    // --- Prefijos de línea ---

    @Test
    fun togglesPrefixOnCurrentLine() {
        val added = toggleLinePrefix("hola\nmundo", 7, 7, LinePrefix.BULLET)
        assertEquals("hola\n- mundo", added.text)
        val removed = toggleLinePrefix(added.text, 8, 8, LinePrefix.BULLET)
        assertEquals("hola\nmundo", removed.text)
    }

    @Test
    fun replacesOtherPrefix() {
        assertEquals("- [ ] tarea", toggleLinePrefix("- tarea", 0, 0, LinePrefix.TASK).text)
        assertEquals("## Título", toggleLinePrefix("# Título", 3, 3, LinePrefix.HEADING2).text)
    }

    @Test
    fun numbersSelectedLines() {
        val edit = toggleLinePrefix("a\nb\nc", 0, 5, LinePrefix.NUMBERED)
        assertEquals("1. a\n2. b\n3. c", edit.text)
        assertEquals(0, edit.selectionStart)
        assertEquals(edit.text.length, edit.selectionEnd)
    }

    // --- Envolver selección ---

    @Test
    fun wrapsAndUnwrapsSelection() {
        val bold = toggleWrap("una palabra", 4, 11, "**")
        assertEquals("una **palabra**", bold.text)
        assertEquals(6, bold.selectionStart)
        assertEquals(13, bold.selectionEnd)
        assertEquals("una palabra", toggleWrap(bold.text, bold.selectionStart, bold.selectionEnd, "**").text)
    }

    @Test
    fun emptySelectionPlacesCursorInside() {
        val edit = toggleWrap("ab", 1, 1, "~~")
        assertEquals("a~~~~b", edit.text)
        assertEquals(3, edit.selectionStart)
    }

    @Test
    fun countsTasks() {
        assertEquals(1 to 3, taskProgress("- [x] a\n- [ ] b\n  * [ ] c\ntexto"))
        assertEquals(0 to 0, taskProgress("sin tareas"))
    }

    @Test
    fun multiLinePrefixSkipsBlankLines() {
        val numbered = toggleLinePrefix("a\n\nb", 0, 4, LinePrefix.NUMBERED)
        assertEquals("1. a\n\n2. b", numbered.text)
        // Las líneas vacías no impiden reconocer que todas tienen ya el formato (alternar).
        assertEquals("a\n\nb", toggleLinePrefix(numbered.text, 0, numbered.text.length, LinePrefix.NUMBERED).text)
        assertEquals("- a\n\n- b", toggleLinePrefix("a\n\nb", 0, 4, LinePrefix.BULLET).text)
    }

    @Test
    fun singleBlankLineStillGetsPrefix() {
        assertEquals("- ", toggleLinePrefix("", 0, 0, LinePrefix.BULLET).text)
    }
}
