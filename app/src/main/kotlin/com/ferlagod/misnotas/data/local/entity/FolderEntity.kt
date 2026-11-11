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
 * Carpeta organizadora de notas (admite anidación mediante [parentId]).
 */
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["isDeleted"])
    ]
)
data class FolderEntity(
    /** Identificador único (UUID); también da nombre al fichero remoto. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    /** Carpeta padre; `null` si está en el nivel superior. */
    @ColumnInfo(name = "parentId")
    val parentId: String? = null,

    /** Nombre visible. */
    @ColumnInfo(name = "name")
    val name: String,

    /** Clave del color (ver `NoteColor`). */
    @ColumnInfo(name = "color")
    val color: String = NoteColor.DEFAULT.key,

    /** Última modificación (milisegundos desde la época Unix); decide qué versión gana al sincronizar. */
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    /** `true` si está marcada para borrado. */
    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    /** Qué falta por hacer en el servidor. */
    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,

    /** ETag del fichero remoto la última vez que local y servidor coincidían. */
    @ColumnInfo(name = "remoteEtag")
    val remoteEtag: String? = null
)
