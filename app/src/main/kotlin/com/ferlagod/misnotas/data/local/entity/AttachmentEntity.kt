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
    /** Identificador único; es el que aparece en `attachment://<id>`. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Tipo MIME de la imagen (p. ej. `image/jpeg`). */
    @ColumnInfo(name = "mimeType")
    val mimeType: String,

    /** Tamaño del fichero local en bytes. */
    @ColumnInfo(name = "size")
    val size: Long,

    /** Fecha de creación (milisegundos desde la época Unix). */
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    /** `true` cuando la copia cifrada ya está en el servidor. */
    @ColumnInfo(name = "isUploaded")
    val isUploaded: Boolean = false
)
