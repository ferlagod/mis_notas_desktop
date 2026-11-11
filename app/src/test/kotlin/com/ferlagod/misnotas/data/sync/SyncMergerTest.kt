// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.sync

import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerTest {

    private fun note(
        title: String = "t",
        content: String = "c",
        updatedAt: Long = 100L,
        status: SyncStatus = SyncStatus.SYNCED,
        etag: String? = "e1"
    ) = NoteEntity(id = "n1", title = title, content = content, updatedAt = updatedAt, syncStatus = status, remoteEtag = etag)

    private fun live(n: NoteEntity) = RemoteRecord.Live(n.copy(syncStatus = SyncStatus.SYNCED, remoteEtag = null))
    private val newId = { "copia" }

    @Test
    fun newRemoteNoteIsSaved() {
        val action = SyncMerger.resolveNote(null, live(note()), "e2", newId)
        assertEquals(MergeAction.SaveRemote(note(etag = "e2")), action)
    }

    @Test
    fun syncedLocalTakesRemoteChanges() {
        val action = SyncMerger.resolveNote(note(), live(note(content = "nuevo", updatedAt = 200)), "e2", newId)
        assertEquals(MergeAction.SaveRemote(note(content = "nuevo", updatedAt = 200, etag = "e2")), action)
    }

    @Test
    fun identicalPendingChangesOnlyUpdateEtag() {
        val local = note(status = SyncStatus.PENDING_UPLOAD, etag = null)
        assertEquals(MergeAction.KeepLocal("e2"), SyncMerger.resolveNote(local, live(note()), "e2", newId))
    }

    @Test
    fun concurrentEditsKeepBothVersions_remoteNewer() {
        val local = note(content = "local", updatedAt = 100, status = SyncStatus.PENDING_UPLOAD)
        val remote = note(content = "remoto", updatedAt = 200)
        val action = SyncMerger.resolveNote(local, live(remote), "e2", newId) as MergeAction.Conflict
        assertEquals("remoto", action.main.content)
        assertEquals(SyncStatus.SYNCED, action.main.syncStatus)
        assertEquals("local", action.copy.content)
        assertEquals("copia", action.copy.id)
        assertTrue(action.copy.title.endsWith(SyncMerger.CONFLICT_SUFFIX))
        assertEquals(SyncStatus.PENDING_UPLOAD, action.copy.syncStatus)
    }

    @Test
    fun concurrentEditsKeepBothVersions_localNewer() {
        val local = note(content = "local", updatedAt = 300, status = SyncStatus.PENDING_UPLOAD)
        val remote = note(content = "remoto", updatedAt = 200)
        val action = SyncMerger.resolveNote(local, live(remote), "e2", newId) as MergeAction.Conflict
        assertEquals("local", action.main.content)
        assertEquals("e2", action.main.remoteEtag)
        assertEquals(SyncStatus.PENDING_UPLOAD, action.main.syncStatus)
        assertEquals("remoto", action.copy.content)
    }

    @Test
    fun verifiedTombstoneDeletesSyncedNote() {
        assertEquals(
            MergeAction.DeleteLocal,
            SyncMerger.resolveNote(note(), RemoteRecord.Tombstone("n1", 1, verified = true), "e2", newId)
        )
    }

    @Test
    fun unverifiedTombstoneOnlyMovesToTrash() {
        assertEquals(
            MergeAction.TrashLocal("e2"),
            SyncMerger.resolveNote(note(), RemoteRecord.Tombstone("n1", 1, verified = false), "e2", newId)
        )
    }

    @Test
    fun missingRemoteNoteGoesToTrashInsteadOfBeingDeleted() {
        assertEquals(MissingRemoteAction.TRASH_AND_REUPLOAD, SyncMerger.resolveMissingRemoteNote(SyncStatus.SYNCED, "e1"))
        assertEquals(MissingRemoteAction.DELETE_LOCAL, SyncMerger.resolveMissingRemoteNote(SyncStatus.PENDING_DELETE, "e1"))
        assertEquals(MissingRemoteAction.MARK_FOR_UPLOAD, SyncMerger.resolveMissingRemoteNote(SyncStatus.SYNCED, null))
    }

    @Test
    fun tombstoneNeverDeletesUnsyncedEdits() {
        val local = note(status = SyncStatus.PENDING_UPLOAD)
        assertEquals(MergeAction.KeepLocal("e2"), SyncMerger.resolveNote(local, RemoteRecord.Tombstone("n1", 1), "e2", newId))
        assertEquals(
            MergeAction.KeepLocal("e2"),
            SyncMerger.resolveNote(local, RemoteRecord.Tombstone("n1", 1, verified = true), "e2", newId)
        )
    }

    @Test
    fun remoteEditAfterLocalPurgeResurrectsNote() {
        val local = note(updatedAt = 100, status = SyncStatus.PENDING_DELETE)
        val action = SyncMerger.resolveNote(local, live(note(updatedAt = 200)), "e2", newId)
        assertTrue(action is MergeAction.SaveRemote)
    }

    @Test
    fun missingRemoteRules() {
        assertEquals(MissingRemoteAction.DELETE_LOCAL, SyncMerger.resolveMissingRemote(SyncStatus.SYNCED, "e1"))
        assertEquals(MissingRemoteAction.MARK_FOR_UPLOAD, SyncMerger.resolveMissingRemote(SyncStatus.SYNCED, null))
        assertEquals(MissingRemoteAction.NOTHING, SyncMerger.resolveMissingRemote(SyncStatus.PENDING_UPLOAD, null))
        assertEquals(MissingRemoteAction.FORGET_ETAG, SyncMerger.resolveMissingRemote(SyncStatus.PENDING_UPLOAD, "e1"))
        assertEquals(MissingRemoteAction.DELETE_LOCAL, SyncMerger.resolveMissingRemote(SyncStatus.PENDING_DELETE, null))
    }

    @Test
    fun folderLastWriteWins() {
        val local = FolderEntity(id = "f", name = "local", updatedAt = 100, syncStatus = SyncStatus.PENDING_UPLOAD)
        val newer = RemoteRecord.Live(FolderEntity(id = "f", name = "remota", updatedAt = 200))
        val older = RemoteRecord.Live(FolderEntity(id = "f", name = "remota", updatedAt = 50))
        assertTrue(SyncMerger.resolveFolder(local, newer, "e") is MergeAction.SaveRemote)
        assertEquals(MergeAction.KeepLocal("e"), SyncMerger.resolveFolder(local, older, "e"))
    }

    // --- Tras reconectar (resetSyncMetadata deja lo sincronizado como PENDING_UPLOAD sin ETag) ---

    @Test
    fun afterReconnect_verifiedTombstoneDoesNotDeleteLocalNote() {
        val local = note(status = SyncStatus.PENDING_UPLOAD, etag = null)
        val action = SyncMerger.resolveNote(local, RemoteRecord.Tombstone("n1", 1, verified = true), "e2", newId)
        assertEquals(MergeAction.KeepLocal("e2"), action)
    }

    @Test
    fun afterReconnect_differentRemoteVersionKeepsBoth() {
        val local = note(content = "local", updatedAt = 100, status = SyncStatus.PENDING_UPLOAD, etag = null)
        val action = SyncMerger.resolveNote(local, live(note(content = "remoto", updatedAt = 200)), "e2", newId)
        assertTrue(action is MergeAction.Conflict)
    }

    @Test
    fun afterReconnect_missingRemoteNoteIsKept() {
        assertEquals(MissingRemoteAction.NOTHING, SyncMerger.resolveMissingRemoteNote(SyncStatus.PENDING_UPLOAD, null))
    }

    // --- Freno de borrados masivos ---

    @Test
    fun massRemoval_fewNotesNeverBlock() {
        assertFalse(SyncMerger.isMassRemoval(removed = 1, live = 1))
        assertFalse(SyncMerger.isMassRemoval(removed = 4, live = 4))
    }

    @Test
    fun massRemoval_largeShareBlocks() {
        assertTrue(SyncMerger.isMassRemoval(removed = 114, live = 114))
        assertTrue(SyncMerger.isMassRemoval(removed = 5, live = 20))
    }

    @Test
    fun massRemoval_smallShareOfManyNotesIsAllowed() {
        assertFalse(SyncMerger.isMassRemoval(removed = 5, live = 21))
        assertFalse(SyncMerger.isMassRemoval(removed = 49, live = 1000))
    }

    @Test
    fun massRemoval_absoluteLimitAlwaysBlocks() {
        assertTrue(SyncMerger.isMassRemoval(removed = 50, live = 10_000))
    }
}
