// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ferlagod.misnotas.data.local.model.SyncStatus
import com.ferlagod.misnotas.ui.theme.NoteColor
import java.util.UUID

/**
 * Nota almacenada localmente (arquitectura local-first).
 *
 * - [isDeleted]: la nota está en la papelera (se sincroniza y se puede restaurar).
 * - [syncStatus] = PENDING_DELETE: borrado definitivo pendiente de propagar al servidor.
 * - [remoteEtag]: ETag del fichero remoto la última vez que local y servidor coincidían.
 *
 * No se usa clave foránea hacia carpetas a propósito: durante la sincronización una nota
 * puede llegar antes que su carpeta, y una FK con ON DELETE SET NULL alteraría notas
 * sin marcarlas para subir.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["isPinned"]),
        Index(value = ["syncStatus"]),
        Index(value = ["isDeleted"]),
        Index(value = ["updatedAt"])
    ]
)
data class NoteEntity(
    /** Identificador único (UUID); también da nombre al fichero remoto. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    /** Carpeta que contiene la nota; `null` = sin carpeta ("Todas las notas"). */
    @ColumnInfo(name = "folderId")
    val folderId: String? = null,

    /** Título (puede estar vacío). */
    @ColumnInfo(name = "title")
    val title: String,

    /** Cuerpo en Markdown. Las imágenes se referencian como `attachment://<id>`. */
    @ColumnInfo(name = "content")
    val content: String,

    /** Fijada al principio de la lista. */
    @ColumnInfo(name = "isPinned")
    val isPinned: Boolean = false,

    /** Clave del color (ver `NoteColor`). */
    @ColumnInfo(name = "color")
    val color: String = NoteColor.DEFAULT.key,

    /** Última modificación (milisegundos desde la época Unix); decide qué versión gana al sincronizar. */
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,

    @ColumnInfo(name = "remoteEtag")
    val remoteEtag: String? = null
)
