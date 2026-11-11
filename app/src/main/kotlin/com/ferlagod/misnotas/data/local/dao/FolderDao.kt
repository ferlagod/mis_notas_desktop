// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getAllFoldersFlow(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE syncStatus = :syncStatus")
    suspend fun getFoldersBySyncStatus(syncStatus: SyncStatus): List<FolderEntity>

    @Upsert
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name, color = :color, parentId = :parentId, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun updateFolderDetails(id: String, name: String, color: String, parentId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE folders SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAsDeleted(id: String, updatedAt: Long = System.currentTimeMillis())

    /** Reasigna las subcarpetas de una carpeta eliminada a su carpeta padre. */
    @Query("UPDATE folders SET parentId = :newParentId, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE parentId = :folderId AND isDeleted = 0")
    suspend fun reparentChildren(folderId: String, newParentId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE folders SET syncStatus = 'SYNCED', remoteEtag = :etag WHERE id = :id AND updatedAt = :updatedAt AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun markSynced(id: String, updatedAt: Long, etag: String?): Int

    @Query("UPDATE folders SET remoteEtag = :etag WHERE id = :id")
    suspend fun setRemoteEtag(id: String, etag: String?)

    @Query("UPDATE folders SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deletePermanently(id: String)

    /** Como [NoteDao.resetSyncMetadata]: lo sincronizado pasa a pendiente de subir. */
    @Query(
        "UPDATE folders SET remoteEtag = NULL, " +
            "syncStatus = CASE WHEN syncStatus = 'SYNCED' THEN 'PENDING_UPLOAD' ELSE syncStatus END"
    )
    suspend fun resetSyncMetadata()
}
