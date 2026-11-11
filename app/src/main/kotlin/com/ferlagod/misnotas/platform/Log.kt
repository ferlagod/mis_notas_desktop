// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Registro mínimo (sustituye a `android.util.Log`): escribe en la salida de errores. */
object Log {
    /** Formato de la hora de cada línea. */
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** Escribe una línea `hora nivel/etiqueta: mensaje` y, si hay error, su traza. */
    private fun print(level: Char, tag: String, message: String, error: Throwable?) {
        System.err.println("${LocalDateTime.now().format(time)} $level/$tag: $message")
        error?.printStackTrace()
    }

    /** Depuración: solo se escribe si la app se arranca con `-Dmisnotas.debug`. */
    fun d(tag: String, message: String) {
        if (System.getProperty("misnotas.debug") != null) print('D', tag, message, null)
    }

    /** Información. */
    fun i(tag: String, message: String) = print('I', tag, message, null)
    /** Aviso (algo falló pero la app puede seguir). */
    fun w(tag: String, message: String, error: Throwable? = null) = print('W', tag, message, error)
    /** Error. */
    fun e(tag: String, message: String, error: Throwable? = null) = print('E', tag, message, error)
}
