// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ferlagod.misnotas.data.local.converter.Converters
import com.ferlagod.misnotas.data.local.dao.AttachmentDao
import com.ferlagod.misnotas.data.local.dao.FolderDao
import com.ferlagod.misnotas.data.local.dao.NoteDao
import com.ferlagod.misnotas.data.local.dao.RemoteTombstoneDao
import com.ferlagod.misnotas.data.local.entity.AttachmentEntity
import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.entity.RemoteTombstoneEntity
import com.ferlagod.misnotas.platform.AppDirs
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Base de datos local (Room + SQLite incluido en la app). Es la fuente de verdad de la app;
 * Nextcloud es una réplica cifrada. Mismo esquema (v2) que la versión de Android.
 */
@Database(
    entities = [
        FolderEntity::class,
        NoteEntity::class,
        AttachmentEntity::class,
        RemoteTombstoneEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun remoteTombstoneDao(): RemoteTombstoneDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(AppDirs.databaseFile).also { INSTANCE = it }
            }
        }

        fun build(file: File): AppDatabase {
            file.parentFile?.mkdirs()
            return Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}

/** Equivalente multiplataforma de `androidx.room.withTransaction`. */
suspend fun <R> AppDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
