// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.model

import com.ferlagod.misnotas.data.local.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Árbol de carpetas: descendientes, ciclos, aplanado y rutas. */
class FolderTreeTest {

    private fun folder(id: String, parent: String? = null, name: String = id) = FolderEntity(id = id, parentId = parent, name = name)

    private val folders = listOf(
        folder("a", name = "Trabajo"),
        folder("b", "a", "Proyectos"),
        folder("c", "b", "2026"),
        folder("d", name = "Casa")
    )

    @Test
    fun descendants() {
        assertEquals(setOf("b", "c"), FolderTree.descendantIds(folders, "a"))
        assertEquals(emptySet<String>(), FolderTree.descendantIds(folders, "d"))
    }

    @Test
    fun detectsCycles() {
        assertTrue(FolderTree.wouldCreateCycle(folders, "a", "c"))
        assertTrue(FolderTree.wouldCreateCycle(folders, "a", "a"))
        assertFalse(FolderTree.wouldCreateCycle(folders, "c", "d"))
        assertFalse(FolderTree.wouldCreateCycle(folders, "c", null))
    }

    @Test
    fun flattenSortsAndIndents() {
        val flat = FolderTree.flatten(folders).map { it.first.id to it.second }
        assertEquals(listOf("d" to 0, "a" to 0, "b" to 1, "c" to 2), flat)
    }

    @Test
    fun flattenSurvivesCyclesAndOrphans() {
        val broken = listOf(folder("x", "y"), folder("y", "x"), folder("z", "missing"))
        val ids = FolderTree.flatten(broken).map { it.first.id }
        assertEquals(setOf("x", "y", "z"), ids.toSet())
        assertEquals(3, ids.size)
    }

    @Test
    fun pathStopsOnCycles() {
        val map = folders.associateBy { it.id }
        assertEquals(listOf("a", "b", "c"), FolderTree.path(map, "c").map { it.id })
        val cyclic = listOf(folder("x", "y"), folder("y", "x")).associateBy { it.id }
        assertEquals(2, FolderTree.path(cyclic, "x").size)
    }
}
