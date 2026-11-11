// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ferlagod.misnotas.data.local.entity.AttachmentEntity

/** Registro de los adjuntos (imágenes) y de si ya se han subido al servidor. */
@Dao
interface AttachmentDao {

    /** Registra un adjunto o actualiza su fila. */
    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    /** Lee un adjunto por su id. */
    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AttachmentEntity?

    /** Todos los adjuntos registrados. */
    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>

    /** Adjuntos que aún no se han subido al servidor. */
    @Query("SELECT * FROM attachments WHERE isUploaded = 0")
    suspend fun getPendingUpload(): List<AttachmentEntity>

    /** Marca el adjunto como subido. */
    @Query("UPDATE attachments SET isUploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: String)

    /** Marca todos los adjuntos para volver a subirlos (al conectar otra cuenta o restaurar la bóveda). */
    @Query("UPDATE attachments SET isUploaded = 0")
    suspend fun markAllPendingUpload()

    /** Elimina el registro del adjunto (el fichero se borra aparte). */
    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)
}
