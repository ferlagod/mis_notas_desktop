// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lápida remota ya procesada (fichero de una nota/carpeta borrada definitivamente).
 * Sirve para no volver a descargarla en cada sincronización.
 */
@Entity(tableName = "remote_tombstones")
data class RemoteTombstoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "fileName")
    val fileName: String,

    @ColumnInfo(name = "etag")
    val etag: String?
)
