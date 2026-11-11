// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla `notes`.
 * Toda modificación hecha por el usuario deja la nota en `PENDING_UPLOAD` y actualiza `updatedAt`,
 * para que la siguiente sincronización la suba al servidor.
 */
@Dao
interface NoteDao {

    /** Notas visibles (fuera de la papelera), primero las fijadas y luego por fecha de edición. */
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotesFlow(): Flow<List<NoteEntity>>

    /** Notas de la papelera, sin las que ya están pendientes de borrado definitivo. */
    @Query("SELECT * FROM notes WHERE isDeleted = 1 AND syncStatus != 'PENDING_DELETE' ORDER BY updatedAt DESC")
    fun getTrashNotesFlow(): Flow<List<NoteEntity>>

    /** Observa una nota concreta; emite `null` si no existe o se elimina. */
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteByIdFlow(id: String): Flow<NoteEntity?>

    /** Lee una nota por su id, esté donde esté (también en la papelera). */
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    /** Todas las filas, incluidas papelera y borrados pendientes (uso interno de sincronización). */
    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    /** Notas con el estado de sincronización indicado. */
    @Query("SELECT * FROM notes WHERE syncStatus = :syncStatus")
    suspend fun getNotesBySyncStatus(syncStatus: SyncStatus): List<NoteEntity>

    /** Contenido de todas las notas no purgadas; sirve para saber qué adjuntos siguen en uso. */
    @Query("SELECT content FROM notes WHERE syncStatus != 'PENDING_DELETE'")
    suspend fun getAllLiveContents(): List<String>

    /** Notas con el formato antiguo (JSON con lista `images`) que aún no se han migrado a Markdown. */
    @Query("SELECT * FROM notes WHERE content LIKE '{%' AND content LIKE '%\"images\"%'")
    suspend fun getNotesWithLegacyImages(): List<NoteEntity>

    /** Inserta la nota o la sustituye si ya existe. */
    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    /** Inserta o sustituye varias notas en una sola transacción. */
    @Upsert
    suspend fun upsertNotes(notes: List<NoteEntity>)

    /** Fija o desfija una nota. */
    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    /** Fija o desfija varias notas; solo toca las que cambian de estado. */
    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id IN (:ids) AND isPinned != :isPinned AND syncStatus != 'PENDING_DELETE'")
    suspend fun setPinnedMany(ids: List<String>, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    /** Cambia el color de varias notas; solo toca las que tienen otro color. */
    @Query("UPDATE notes SET color = :color, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id IN (:ids) AND color != :color AND syncStatus != 'PENDING_DELETE'")
    suspend fun setColor(ids: List<String>, color: String, updatedAt: Long = System.currentTimeMillis())

    /** Mueve varias notas a [folderId] (`null` = sin carpeta); solo toca las que cambian de carpeta. */
    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id IN (:ids) AND (folderId IS NOT :folderId) AND syncStatus != 'PENDING_DELETE'")
    suspend fun moveToFolder(ids: List<String>, folderId: String?, updatedAt: Long = System.currentTimeMillis())

    /** Notas en la papelera desde antes de [before] (para el vaciado automático). */
    @Query("SELECT id FROM notes WHERE isDeleted = 1 AND syncStatus != 'PENDING_DELETE' AND updatedAt < :before")
    suspend fun getTrashIdsOlderThan(before: Long): List<String>

    /** Mueve la nota a la papelera (se sincroniza como nota en papelera). */
    @Query("UPDATE notes SET isDeleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, updatedAt: Long = System.currentTimeMillis())

    /** Saca la nota de la papelera. */
    @Query("UPDATE notes SET isDeleted = 0, syncStatus = 'PENDING_UPLOAD', updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreFromTrash(id: String, updatedAt: Long = System.currentTimeMillis())

    /** Marca la nota para borrado definitivo; la sincronización subirá una lápida y eliminará la fila. */
    @Query("UPDATE notes SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markForPurge(id: String, updatedAt: Long = System.currentTimeMillis())

    /** Vaciar papelera: marca todas sus notas para borrado definitivo. */
    @Query("UPDATE notes SET syncStatus = 'PENDING_DELETE' WHERE isDeleted = 1")
    suspend fun markTrashForPurge()

    /** Elimina las filas de la papelera sin pasar por la sincronización (solo sin cuenta conectada). */
    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun deleteTrashPermanently()

    /** Saca las notas de una carpeta eliminada y las deja en "Todas las notas". */
    @Query("UPDATE notes SET folderId = NULL, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE folderId = :folderId AND syncStatus != 'PENDING_DELETE'")
    suspend fun detachFromFolder(folderId: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Marca como sincronizada la versión subida, pero solo si no ha cambiado mientras se subía.
     * Devuelve el número de filas actualizadas (0 si el usuario editó entretanto).
     */
    @Query("UPDATE notes SET syncStatus = 'SYNCED', remoteEtag = :etag WHERE id = :id AND updatedAt = :updatedAt AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun markSynced(id: String, updatedAt: Long, etag: String?): Int

    /** Guarda el ETag de la versión del servidor sin cambiar el estado de sincronización. */
    @Query("UPDATE notes SET remoteEtag = :etag WHERE id = :id")
    suspend fun setRemoteEtag(id: String, etag: String?)

    /**
     * Borrado remoto no verificable: la nota pasa a la papelera local (recuperable) y queda
     * sincronizada con la lápida. Si ya estaba en la papelera conserva su fecha.
     */
    @Query(
        "UPDATE notes SET isDeleted = 1, syncStatus = 'SYNCED', remoteEtag = :etag, " +
            "updatedAt = CASE WHEN isDeleted = 1 THEN updatedAt ELSE :now END WHERE id = :id"
    )
    suspend fun trashFromRemote(id: String, etag: String?, now: Long = System.currentTimeMillis())

    /** La nota desapareció del servidor: a la papelera y pendiente de volver a subirse. */
    @Query(
        "UPDATE notes SET isDeleted = 1, syncStatus = 'PENDING_UPLOAD', remoteEtag = NULL, " +
            "updatedAt = CASE WHEN isDeleted = 1 THEN updatedAt ELSE :now END WHERE id = :id"
    )
    suspend fun trashForReupload(id: String, now: Long = System.currentTimeMillis())

    /** Cambia solo el estado de sincronización. */
    @Query("UPDATE notes SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    /** Elimina la fila; se usa cuando el borrado ya está confirmado en el servidor. */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deletePermanently(id: String)

    /**
     * Olvida el estado remoto (al conectar una cuenta o al restaurar la bóveda). Lo que estaba
     * sincronizado pasa a pendiente de subir: tras reconectar, el dispositivo no sabe si su
     * versión coincide con la del servidor, así que nunca la acepta a ciegas. Si el servidor
     * tiene otra versión se conservan ambas ("conflicto"); si tiene una lápida, la nota local
     * vuelve a subirse en lugar de borrarse.
     */
    @Query(
        "UPDATE notes SET remoteEtag = NULL, " +
            "syncStatus = CASE WHEN syncStatus = 'SYNCED' THEN 'PENDING_UPLOAD' ELSE syncStatus END"
    )
    suspend fun resetSyncMetadata()

    /** Notas visibles (fuera de la papelera y sin borrado pendiente). */
    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0 AND syncStatus != 'PENDING_DELETE'")
    suspend fun countLive(): Int
}
