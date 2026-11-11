// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editor visual: análisis en línea, ocultación de marcadores, cursor y borrado. */
class RichEditingTest {

    private fun doc(text: String) = MarkdownPresenter.document(text, "imagen")
    private fun shown(text: String) = DisplayMapping.build(text, doc(text).replacements).display
    private fun kinds(text: String) = InlineMarkdown.parse(text).map { it.kind }

    // ------------------------------------------------------------------
    // Analizador
    // ------------------------------------------------------------------

    @Test
    fun parsesBasicAndNestedInline() {
        assertEquals(listOf(InlineKind.BOLD), kinds("a **b** c"))
        assertEquals(listOf(InlineKind.BOLD, InlineKind.ITALIC), kinds("**a *b* c**"))
        assertEquals(listOf(InlineKind.BOLD, InlineKind.ITALIC), kinds("***x***"))
        assertEquals(listOf(InlineKind.ITALIC, InlineKind.BOLD), kinds("*a **b** c*"))
        assertEquals(listOf(InlineKind.STRIKE), kinds("~~no~~"))
        assertEquals(listOf(InlineKind.CODE), kinds("usa `**x**` así"))
        assertEquals(listOf(InlineKind.LINK, InlineKind.BOLD), kinds("[**web**](https://a.b)"))
        assertEquals(listOf(InlineKind.IMAGE), kinds("![foto](attachment://abc)"))
        assertEquals(listOf(InlineKind.AUTOLINK), kinds("ver https://example.com."))
    }

    @Test
    fun doesNotFormatThingsThatAreNotFormatting() {
        assertTrue(kinds("2 * 3 * 4").isEmpty())
        assertTrue(kinds("snake_case_name").isEmpty())
        assertTrue(kinds("**sin cerrar").isEmpty())
        assertTrue(kinds("\\*literal\\*").isEmpty())
    }

    // ------------------------------------------------------------------
    // Lo que ve el usuario
    // ------------------------------------------------------------------

    @Test
    fun hidesMarkdownSyntax() {
        assertEquals("hola mundo", shown("hola **mundo**"))
        assertEquals("Título", shown("# Título"))
        assertEquals("• uno\n☐ dos\n☑ tres", shown("- uno\n- [ ] dos\n- [x] tres"))
        assertEquals("1. uno", shown("1. uno"))
        assertEquals("▍ cita", shown("> cita"))
        assertEquals("web", shown("[web](https://a.b)"))
        assertEquals("🖼 foto", shown("![foto](attachment://abc)"))
        assertEquals("🖼 imagen", shown("![](attachment://abc)"))
        assertEquals("**literal en código**", shown("```\n**literal en código**\n```").lines()[1])
    }

    @Test
    fun cursorMappingStaysInBounds() {
        val samples = listOf("", "**", "# ", "- [ ] ", "**a**", "a\n- b\n## c **d** [e](f)\n![x](y)\n---", "***x***")
        for (s in samples) {
            val m = DisplayMapping.build(s, doc(s).replacements)
            for (o in 0..s.length) assertTrue(m.toDisplay(o) in 0..m.display.length)
            for (t in 0..m.display.length) assertTrue(m.toOriginal(t) in 0..s.length)
        }
    }

    @Test
    fun cursorAfterBoldWordTypesOutsideAndBeforeTypesInside() {
        val text = "**bold** x"
        val m = DisplayMapping.build(text, doc(text).replacements)
        assertEquals(8, m.toOriginal(4)) // tras "bold" -> después del cierre
        assertEquals(2, m.toOriginal(0)) // antes de "bold" -> dentro
    }

    @Test
    fun cursorInsideBulletGoesToContent() {
        val text = "- item"
        val m = DisplayMapping.build(text, doc(text).replacements)
        assertEquals(2, m.toOriginal(0))
        assertEquals(2, m.toOriginal(1))
    }

    // ------------------------------------------------------------------
    // Borrado
    // ------------------------------------------------------------------

    private fun backspace(text: String, cursor: Int): TextEdit {
        val new = text.removeRange(cursor - 1, cursor)
        return reconcileEdit(text, new, cursor - 1, doc(text)) ?: TextEdit(new, cursor - 1)
    }

    @Test
    fun backspaceOverHiddenClosingMarkerDeletesVisibleChar() {
        assertEquals(TextEdit("**bol** x", 7), backspace("**bold** x", 8))
    }

    @Test
    fun deletingLastCharOfBoldLeavesNoMarkers() {
        assertEquals(TextEdit("a  b", 2), backspace("a **x** b", 5))
        // Retroceso justo detrás de la negrita (cursor tras el cierre oculto): borra la "x".
        assertEquals("a  b", backspace("a **x** b", 7).text)
    }

    @Test
    fun backspaceAtStartOfBulletRemovesTheBullet() {
        assertEquals(TextEdit("item", 0), backspace("- item", 2))
        assertEquals(TextEdit("tarea", 0), backspace("- [ ] tarea", 6))
        assertEquals(TextEdit("a\nTítulo", 2), backspace("a\n## Título", 5))
    }

    @Test
    fun backspaceDeletesWholeImage() {
        val text = "a\n![x](attachment://id)"
        assertEquals(TextEdit("a\n", 2), backspace(text, text.length))
    }

    @Test
    fun deletingSelectionAcrossMarkersKeepsFormattingIntact() {
        // Selección visual "bold" -> en el original [2, 8): contenido + cierre.
        val text = "**bold** x"
        val edit = reconcileEdit(text, text.removeRange(2, 8), 2, doc(text))!!
        assertEquals(" x", edit.text)
        // Sustituir parte de una negrita y algo de fuera no deja asteriscos sueltos.
        val t2 = "**abc** def"
        val replaced = reconcileEdit(t2, t2.substring(0, 4) + "Z" + t2.substring(9), 5, doc(t2))!!
        assertEquals("**ab**Zef", replaced.text)
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    @Test
    fun pendingBoldWrapsTypedText() {
        val (edit, shift) = applyPendingStyles("ab", "aXb", 2, setOf(InlineKind.BOLD))!!
        assertEquals(TextEdit("a**X**b", 4), edit)
        assertEquals(2, shift)
        assertNull(applyPendingStyles("ab", "a b", 2, setOf(InlineKind.BOLD)))
        val (both, _) = applyPendingStyles("", "X", 1, setOf(InlineKind.ITALIC, InlineKind.BOLD))!!
        assertEquals("***X***", both.text)
    }

    @Test
    fun enterAtStartOfHeadingOpensLineAbove() {
        val old = "## Título"
        val edit = newlineBeforeBlock(old, "## \nTítulo", 4, doc(old))!!
        assertEquals(TextEdit("\n## Título", 4), edit)
    }

    // ------------------------------------------------------------------
    // Botones de la barra
    // ------------------------------------------------------------------

    private fun toggle(text: String, s: Int, e: Int, kind: InlineKind = InlineKind.BOLD) =
        toggleInlineStyle(text, s, e, kind, doc(text))

    @Test
    fun boldButtonWrapsAndUnwrapsSelection() {
        assertEquals(TextEdit("a **bc** d", 4, 6), toggle("a bc d", 2, 4))
        assertEquals(TextEdit("a bc d", 2, 4), toggle("a **bc** d", 4, 6))
        // La selección con espacios alrededor no deja "** bc **".
        assertEquals("a **bc** d", toggle("a bc d", 1, 5)!!.text)
    }

    @Test
    fun boldButtonOnPartOfBoldTextRemovesItOnlyThere() {
        assertEquals("**ab**cd**ef**", toggle("**abcdef**", 4, 6)!!.text)
        assertEquals("ab**cd**", toggle("**abcd**", 2, 4)!!.text)
        assertEquals("**ab**cd", toggle("**abcd**", 4, 6)!!.text)
    }

    @Test
    fun boldButtonAbsorbsOverlappingBold() {
        assertEquals("**a b c**", toggle("a **b** c", 0, 9)!!.text)
    }

    @Test
    fun boldButtonPerLineAndSkipsListMarkers() {
        assertEquals("- **uno**\n- **dos**", toggle("- uno\n- dos", 0, 11)!!.text)
    }

    @Test
    fun boldButtonWithoutSelection() {
        assertNull(toggle("abc", 1, 1)) // queda pendiente para lo que se escriba
        assertEquals(TextEdit("**ab** c", 6), toggle("**ab** c", 4, 4)) // al final: sale del formato
        assertEquals("ab c", toggle("**ab** c", 3, 3)!!.text) // en medio: lo quita
    }

    @Test
    fun activeStylesForToolbar() {
        val text = "**a *b* c**"
        assertEquals(setOf(InlineKind.BOLD, InlineKind.ITALIC), activeInlineKinds(doc(text), 5, 5))
        assertEquals(setOf(InlineKind.BOLD), activeInlineKinds(doc(text), 2, 2))
        assertEquals(LinePrefix.HEADING2, lineBlockAt("x\n## y", 4))
        assertEquals(LinePrefix.TASK, lineBlockAt("- [ ] y", 7))
    }

    @Test
    fun linksAndTasks() {
        assertEquals("[web](https://ejemplo.com)", applyLink("web", 0, 3, "web", "ejemplo.com", null).text)
        val link = linkAt(doc("[a](https://x.y)"), 2)!!
        assertEquals("a", removeLink("[a](https://x.y)", link).text)
        val box = doc("- [ ] tarea").tasks.single()
        assertEquals("- [x] tarea", toggleTaskBox("- [ ] tarea", box))
    }

    @Test
    fun normalizeMovesTrailingSpacesOutOfMarkers() {
        assertEquals("**hola** mundo", normalizeMarkdown("**hola **mundo"))
        assertEquals("*a*  ", normalizeMarkdown("*a  *"))
        assertEquals("```\n**x **\n```", normalizeMarkdown("```\n**x **\n```"))
        assertEquals("sin cambios", normalizeMarkdown("sin cambios"))
    }
}
