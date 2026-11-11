// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.converter

import androidx.room.TypeConverter
import com.ferlagod.misnotas.data.local.model.SyncStatus

/**
 * Conversores de tipos para la base de datos Room.
 */
class Converters {

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String {
        return (status ?: SyncStatus.PENDING_UPLOAD).name
    }

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus {
        if (value.isNullOrBlank()) return SyncStatus.PENDING_UPLOAD
        return try {
            SyncStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            SyncStatus.PENDING_UPLOAD
        }
    }
}
