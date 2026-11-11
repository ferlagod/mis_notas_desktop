// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun ignoresAccentsAndCase() {
        assertTrue(TextSearch.normalize("Canción ÑANDÚ").contains("cancion"))
        assertEquals(listOf("cafe", "rapido"), TextSearch.terms("  Café   RÁPIDO "))
    }

    @Test
    fun matchRangesPointToOriginalText() {
        val text = "Mi canción favorita"
        val ranges = TextSearch.matchRanges(text, TextSearch.terms("cancion"))
        assertEquals(listOf(3..9), ranges)
        assertEquals("canción", text.substring(ranges[0]))
    }
}
