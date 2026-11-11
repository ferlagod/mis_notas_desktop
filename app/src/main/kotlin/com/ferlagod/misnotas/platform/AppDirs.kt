// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import java.io.File

/** Sistema operativo en el que se ejecuta la app. */
enum class Os { MAC, WINDOWS, LINUX;

    companion object {
        val current: Os by lazy {
            val name = System.getProperty("os.name").orEmpty().lowercase()
            when {
                name.contains("mac") || name.contains("darwin") -> MAC
                name.contains("win") -> WINDOWS
                else -> LINUX
            }
        }
    }
}

/**
 * Carpetas de la app según las convenciones de cada sistema:
 * - macOS: `~/Library/Application Support/Mis Notas`
 * - Windows: `%APPDATA%\Mis Notas`
 * - Linux: `$XDG_DATA_HOME/mis-notas` (por defecto `~/.local/share/mis-notas`)
 *
 * Se puede forzar otra carpeta con la propiedad `-Dmisnotas.dataDir=…` o la variable de
 * entorno `MISNOTAS_DATA_DIR` (útil para una instalación portátil o para pruebas).
 */
object AppDirs {

    private val home: File get() = File(System.getProperty("user.home"))

    val dataDir: File by lazy {
        val override = System.getProperty("misnotas.dataDir") ?: System.getenv("MISNOTAS_DATA_DIR")
        val dir = when {
            !override.isNullOrBlank() -> File(override)
            Os.current == Os.MAC -> File(home, "Library/Application Support/Mis Notas")
            Os.current == Os.WINDOWS -> File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: home.path, "Mis Notas")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".local/share").path, "mis-notas")
        }
        dir.apply { mkdirs() }
    }

    /** Equivalente al `filesDir` de Android. */
    val filesDir: File by lazy { File(dataDir, "files").apply { mkdirs() } }

    val databaseFile: File get() = File(dataDir, "misnotas.db")

    /** Carpeta de descargas del usuario (para la copia de seguridad previa a sincronizar). */
    val downloadsDir: File by lazy {
        if (Os.current == Os.LINUX) xdgUserDir("XDG_DOWNLOAD_DIR")?.let { return@lazy it }
        val profile = if (Os.current == Os.WINDOWS) System.getenv("USERPROFILE")?.let(::File) ?: home else home
        File(profile, "Downloads")
    }

    /** Lee `~/.config/user-dirs.dirs` (carpetas traducidas de Linux, p. ej. "Descargas"). */
    private fun xdgUserDir(key: String): File? = try {
        val config = File(System.getenv("XDG_CONFIG_HOME") ?: File(home, ".config").path, "user-dirs.dirs")
        config.takeIf { it.isFile }?.readLines()
            ?.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')?.trim()?.trim('"')
            ?.replace("\$HOME", home.path)
            ?.let(::File)
    } catch (_: Exception) {
        null
    }
}
