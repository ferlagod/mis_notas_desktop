// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.model

import com.ferlagod.misnotas.data.local.entity.FolderEntity

/**
 * Operaciones sobre la jerarquía de carpetas. Todas toleran datos inconsistentes
 * (padres inexistentes o ciclos llegados por sincronización) sin colgarse.
 */
object FolderTree {

    /** Ids de todas las subcarpetas (a cualquier profundidad) de [folderId], sin incluirla. */
    fun descendantIds(folders: List<FolderEntity>, folderId: String): Set<String> {
        val children = folders.groupBy { it.parentId }
        val result = linkedSetOf<String>()
        val pending = ArrayDeque(listOf(folderId))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            for (child in children[current].orEmpty()) {
                if (child.id != folderId && result.add(child.id)) pending.addLast(child.id)
            }
        }
        return result
    }

    /** True si asignar [newParentId] como padre de [folderId] crearía un ciclo. */
    fun wouldCreateCycle(folders: List<FolderEntity>, folderId: String, newParentId: String?): Boolean =
        newParentId != null && (newParentId == folderId || newParentId in descendantIds(folders, folderId))

    /**
     * Árbol aplanado en orden de visualización con su nivel de sangría. Las carpetas cuyo
     * padre no existe (o que forman un ciclo) se muestran en el nivel superior.
     */
    fun flatten(folders: List<FolderEntity>, comparator: Comparator<FolderEntity> = byName): List<Pair<FolderEntity, Int>> {
        val ids = folders.mapTo(HashSet()) { it.id }
        val children = folders.groupBy { it.parentId }.mapValues { (_, list) -> list.sortedWith(comparator) }
        val result = mutableListOf<Pair<FolderEntity, Int>>()
        val visited = HashSet<String>()

        fun add(folder: FolderEntity, level: Int) {
            if (!visited.add(folder.id)) return
            result += folder to level
            children[folder.id].orEmpty().forEach { add(it, level + 1) }
        }

        folders.filter { it.parentId == null || it.parentId !in ids }.sortedWith(comparator).forEach { add(it, 0) }
        // Restos de un ciclo: se cuelgan del nivel superior para que no desaparezcan.
        folders.sortedWith(comparator).forEach { add(it, 0) }
        return result
    }

    /** Ruta desde la raíz hasta [folderId] (incluida). Vacía si no existe. */
    fun path(foldersById: Map<String, FolderEntity>, folderId: String?): List<FolderEntity> {
        val result = mutableListOf<FolderEntity>()
        val seen = HashSet<String>()
        var current = folderId?.let { foldersById[it] }
        while (current != null && seen.add(current.id)) {
            result.add(0, current)
            current = current.parentId?.let { foldersById[it] }
        }
        return result
    }

    private val byName = compareBy<FolderEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
}
