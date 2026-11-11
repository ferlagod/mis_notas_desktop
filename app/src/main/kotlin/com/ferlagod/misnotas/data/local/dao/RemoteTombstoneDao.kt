// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ferlagod.misnotas.data.local.entity.RemoteTombstoneEntity

/** Lápidas remotas ya procesadas, para no descargarlas de nuevo en cada sincronización. */
@Dao
interface RemoteTombstoneDao {

    /** Guarda o actualiza una lápida. */
    @Upsert
    suspend fun upsert(tombstone: RemoteTombstoneEntity)

    /** Todas las lápidas conocidas. */
    @Query("SELECT * FROM remote_tombstones")
    suspend fun getAll(): List<RemoteTombstoneEntity>

    /** Olvida la lápida del fichero indicado (vuelve a procesarse si sigue en el servidor). */
    @Query("DELETE FROM remote_tombstones WHERE fileName = :fileName")
    suspend fun delete(fileName: String)

    /** Olvida todas las lápidas (al cambiar de cuenta o restaurar la bóveda). */
    @Query("DELETE FROM remote_tombstones")
    suspend fun clear()
}
