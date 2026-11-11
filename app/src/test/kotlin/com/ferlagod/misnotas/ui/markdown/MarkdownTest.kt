// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {

    @Test
    fun togglesTaskLines() {
        val md = "Lista\n- [ ] uno\n  * [x] dos"
        assertEquals("Lista\n- [x] uno\n  * [x] dos", toggleTaskLine(md, 1))
        assertEquals("Lista\n- [ ] uno\n  * [ ] dos", toggleTaskLine(md, 2))
        assertEquals(md, toggleTaskLine(md, 0))
        assertEquals(md, toggleTaskLine(md, 99))
    }

    @Test
    fun previewStripsSyntax() {
        val md = "# Título\n![img](attachment://a)\n- [x] hecho\n- [ ] pendiente\n**negrita** y [enlace](https://x.y)"
        assertEquals("Título\n☑ hecho\n☐ pendiente\nnegrita y enlace", markdownPreview(md))
    }

    @Test
    fun underscoresInsideWordsAreNotItalic() {
        assertEquals("mi_fichero_final.txt", inline("mi_fichero_final.txt").text)
        assertEquals("cursiva", inline("_cursiva_").text)
    }

    @Test
    fun bareUrlsBecomeLinks() {
        val text = inline("Mira https://example.com/a?b=1.")
        assertEquals("Mira https://example.com/a?b=1.", text.text)
        assertEquals(1, text.getLinkAnnotations(0, text.length).size)
    }

    @Test
    fun plainTextForSharing() {
        val md = "# Compra\n- [x] pan\n![img](attachment://abc)\n**ya** [web](https://x.y)"
        assertEquals("Compra\n☑ pan\nya web (https://x.y)", markdownPlainText(md))
    }

    @Test
    fun previewSkipsTableSeparator() {
        assertEquals("| a | b |\n| 1 | 2 |", markdownPreview("| a | b |\n|---|---|\n| 1 | 2 |"))
    }
}
