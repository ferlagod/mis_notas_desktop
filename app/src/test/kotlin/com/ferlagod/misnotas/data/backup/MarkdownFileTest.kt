// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFileTest {

    @Test
    fun roundTripKeepsMetadata() {
        val raw = MarkdownFile.write("Lista \"especial\"", "- [ ] pan\n- [x] leche", isPinned = true, color = "YELLOW", updatedAt = 1_762_600_000_000)
        val parsed = MarkdownFile.parse(raw, "fallback")
        assertEquals("Lista \"especial\"", parsed.title)
        assertEquals("- [ ] pan\n- [x] leche", parsed.content)
        assertTrue(parsed.isPinned)
        assertEquals("YELLOW", parsed.color)
        assertEquals(1_762_600_000_000, parsed.updatedAt)
    }

    @Test
    fun plainMarkdownUsesFileName() {
        val parsed = MarkdownFile.parse("﻿# Hola\r\nmundo", "nota")
        assertEquals("nota", parsed.title)
        assertEquals("# Hola\nmundo", parsed.content)
        assertFalse(parsed.isPinned)
        assertNull(parsed.updatedAt)
    }

    @Test
    fun safeFileNames() {
        assertEquals("a_b_c", MarkdownFile.safeFileName("a/b:c"))
        assertEquals("nota", MarkdownFile.safeFileName(" .. "))
        assertEquals(80, MarkdownFile.safeFileName("x".repeat(200)).length)
    }

    @Test
    fun uniqueNames() {
        val taken = mutableSetOf<String>()
        assertEquals("Nota.md", MarkdownFile.uniqueName("Nota", ".md", taken))
        assertEquals("Nota (2).md", MarkdownFile.uniqueName("Nota", ".md", taken))
        assertEquals("nota (3).md", MarkdownFile.uniqueName("nota", ".md", taken))
    }

    @Test
    fun zipPathsCannotEscape() {
        assertEquals("a/c", BackupManager.normalizePath("a/./b/../c"))
        assertEquals("x/y.md", BackupManager.normalizePath("\\x\\y.md"))
        assertNull(BackupManager.normalizePath("../etc/passwd"))
        assertNull(BackupManager.normalizePath("a/../../b"))
    }
}
