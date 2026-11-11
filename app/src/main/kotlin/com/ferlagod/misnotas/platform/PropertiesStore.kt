// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Preferencias en un fichero `.properties` (sustituye a SharedPreferences).
 * Cada cambio se escribe de forma atómica (temporal + renombrado).
 */
class PropertiesStore(private val file: File) {

    private val props = Properties()

    init {
        if (file.isFile) {
            try {
                file.inputStream().use { props.load(it) }
            } catch (e: Exception) {
                Log.w("PropertiesStore", "No se pudo leer ${file.name}; se empieza de cero", e)
            }
        }
    }

    @Synchronized
    fun getString(key: String, default: String? = null): String? = props.getProperty(key) ?: default

    @Synchronized
    fun getBoolean(key: String, default: Boolean): Boolean = props.getProperty(key)?.toBooleanStrictOrNull() ?: default

    @Synchronized
    fun getInt(key: String, default: Int): Int = props.getProperty(key)?.toIntOrNull() ?: default

    @Synchronized
    fun getLong(key: String, default: Long): Long = props.getProperty(key)?.toLongOrNull() ?: default

    /** Aplica varios cambios y los guarda de una vez. */
    @Synchronized
    fun edit(block: Editor.() -> Unit) {
        Editor().block()
        save()
    }

    inner class Editor {
        fun putString(key: String, value: String?) {
            if (value == null) props.remove(key) else props.setProperty(key, value)
        }
        fun putBoolean(key: String, value: Boolean) = props.setProperty(key, value.toString())
        fun putInt(key: String, value: Int) = props.setProperty(key, value.toString())
        fun putLong(key: String, value: Long) = props.setProperty(key, value.toString())
        fun remove(key: String) = props.remove(key)
        fun clear() = props.clear()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { props.store(it, null) }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Log.e("PropertiesStore", "No se pudo guardar ${file.name}", e)
        }
    }
}
