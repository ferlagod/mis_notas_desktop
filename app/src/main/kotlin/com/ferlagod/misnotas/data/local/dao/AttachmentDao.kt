// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ferlagod.misnotas.data.local.entity.AttachmentEntity

@Dao
interface AttachmentDao {

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE isUploaded = 0")
    suspend fun getPendingUpload(): List<AttachmentEntity>

    @Query("UPDATE attachments SET isUploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: String)

    @Query("UPDATE attachments SET isUploaded = 0")
    suspend fun markAllPendingUpload()

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)
}
