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

/** Acceso a la tabla `folders`. Sigue las mismas reglas de sincronización que [NoteDao]. */
@Dao
interface FolderDao {

    /** Carpetas no eliminadas, ordenadas por nombre sin distinguir mayúsculas. */
    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getAllFoldersFlow(): Flow<List<FolderEntity>>

    /** Lee una carpeta por su id, aunque esté marcada como eliminada. */
    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): FolderEntity?

    /** Todas las filas, incluidas las pendientes de borrado (uso interno de sincronización). */
    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    /** Carpetas con el estado de sincronización indicado. */
    @Query("SELECT * FROM folders WHERE syncStatus = :syncStatus")
    suspend fun getFoldersBySyncStatus(syncStatus: SyncStatus): List<FolderEntity>

    /** Inserta la carpeta o la sustituye si ya existe. */
    @Upsert
    suspend fun upsertFolder(folder: FolderEntity)

    /** Cambia nombre, color y carpeta padre, y la deja pendiente de subir. */
    @Query("UPDATE folders SET name = :name, color = :color, parentId = :parentId, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun updateFolderDetails(id: String, name: String, color: String, parentId: String?, updatedAt: Long = System.currentTimeMillis())

    /** Marca la carpeta para borrado; la sincronización subirá la lápida y eliminará la fila. */
    @Query("UPDATE folders SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAsDeleted(id: String, updatedAt: Long = System.currentTimeMillis())

    /** Reasigna las subcarpetas de una carpeta eliminada a su carpeta padre. */
    @Query("UPDATE folders SET parentId = :newParentId, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE parentId = :folderId AND isDeleted = 0")
    suspend fun reparentChildren(folderId: String, newParentId: String?, updatedAt: Long = System.currentTimeMillis())

    /** Igual que [NoteDao.markSynced]: devuelve 0 si la carpeta cambió mientras se subía. */
    @Query("UPDATE folders SET syncStatus = 'SYNCED', remoteEtag = :etag WHERE id = :id AND updatedAt = :updatedAt AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun markSynced(id: String, updatedAt: Long, etag: String?): Int

    /** Guarda el ETag de la versión del servidor sin cambiar el estado de sincronización. */
    @Query("UPDATE folders SET remoteEtag = :etag WHERE id = :id")
    suspend fun setRemoteEtag(id: String, etag: String?)

    /** Cambia solo el estado de sincronización. */
    @Query("UPDATE folders SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    /** Elimina la fila; se usa cuando el borrado ya está confirmado en el servidor. */
    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deletePermanently(id: String)

    /** Como [NoteDao.resetSyncMetadata]: lo sincronizado pasa a pendiente de subir. */
    @Query(
        "UPDATE folders SET remoteEtag = NULL, " +
            "syncStatus = CASE WHEN syncStatus = 'SYNCED' THEN 'PENDING_UPLOAD' ELSE syncStatus END"
    )
    suspend fun resetSyncMetadata()
}
