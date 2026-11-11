// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Impide abrir dos veces la app sobre la misma carpeta de datos. Usa un bloqueo del sistema
 * sobre `instance.lock`, que se libera solo aunque la app se cierre de golpe.
 */
object SingleInstance {

    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun acquire(dir: File = AppDirs.dataDir): Boolean {
        if (lock != null) return true
        return try {
            val ch = RandomAccessFile(File(dir, "instance.lock"), "rw").channel
            val l = ch.tryLock()
            if (l == null) {
                ch.close()
                false
            } else {
                channel = ch
                lock = l
                true
            }
        } catch (e: Exception) {
            // Si no se puede comprobar (sistema de ficheros raro), mejor abrir que bloquear al usuario.
            Log.w("SingleInstance", "No se pudo comprobar si la app ya está abierta", e)
            true
        }
    }
}
