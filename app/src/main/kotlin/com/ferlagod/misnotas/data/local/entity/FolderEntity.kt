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
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "parentId")
    val parentId: String? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: String = NoteColor.DEFAULT.key,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,

    @ColumnInfo(name = "remoteEtag")
    val remoteEtag: String? = null
)
