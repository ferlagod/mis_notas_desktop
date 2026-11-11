// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local

import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Room con el SQLite incluido: esquema, consultas, flujos y transacciones. */
class DatabaseTest {

    private fun <T> withDb(block: suspend (AppDatabase) -> T): T {
        val dir = Files.createTempDirectory("misnotas-db").toFile()
        val db = AppDatabase.build(dir.resolve("test.db"))
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun insertQueryAndFlow() = withDb { db ->
        val dao = db.noteDao()
        dao.upsertNote(NoteEntity(id = "a", title = "Uno", content = "x", updatedAt = 1))
        dao.upsertNote(NoteEntity(id = "b", title = "Dos", content = "y", isPinned = true, updatedAt = 2))
        val notes = dao.getAllNotesFlow().first()
        assertEquals(listOf("b", "a"), notes.map { it.id })

        dao.moveToTrash("a")
        assertEquals(listOf("a"), dao.getTrashNotesFlow().first().map { it.id })
        assertEquals(1, dao.countLive())
    }

    @Test
    fun markSyncedOnlyIfUnchanged() = withDb { db ->
        val dao = db.noteDao()
        dao.upsertNote(NoteEntity(id = "a", title = "t", content = "c", updatedAt = 10))
        assertEquals(0, dao.markSynced("a", updatedAt = 9, etag = "e1"))
        assertEquals(1, dao.markSynced("a", updatedAt = 10, etag = "e1"))
        assertEquals(SyncStatus.SYNCED, dao.getNoteById("a")!!.syncStatus)
    }

    @Test
    fun transactionRollsBackOnError() = withDb { db ->
        val dao = db.noteDao()
        try {
            db.withTransaction {
                dao.upsertNote(NoteEntity(id = "a", title = "t", content = "c"))
                error("fallo")
            }
        } catch (_: IllegalStateException) {
        }
        assertNull(dao.getNoteById("a"))

        db.withTransaction { dao.upsertNote(NoteEntity(id = "b", title = "t", content = "c")) }
        assertTrue(dao.getNoteById("b") != null)
    }
}
