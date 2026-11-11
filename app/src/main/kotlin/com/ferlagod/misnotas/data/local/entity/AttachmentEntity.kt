// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Adjunto binario (imagen) referenciado desde el Markdown de una nota como `attachment://<id>`.
 *
 * Los adjuntos son inmutables: nunca cambian de contenido, solo se crean. El fichero
 * se guarda sin cifrar en el almacenamiento privado de la app y se sube cifrado al servidor.
 */
@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "mimeType")
    val mimeType: String,

    @ColumnInfo(name = "size")
    val size: Long,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "isUploaded")
    val isUploaded: Boolean = false
)
