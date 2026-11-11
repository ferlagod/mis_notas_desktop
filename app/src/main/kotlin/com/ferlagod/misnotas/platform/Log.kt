// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Registro mínimo (sustituye a `android.util.Log`): escribe en la salida de errores. */
object Log {
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun print(level: Char, tag: String, message: String, error: Throwable?) {
        System.err.println("${LocalDateTime.now().format(time)} $level/$tag: $message")
        error?.printStackTrace()
    }

    fun d(tag: String, message: String) {
        if (System.getProperty("misnotas.debug") != null) print('D', tag, message, null)
    }

    fun i(tag: String, message: String) = print('I', tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = print('W', tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = print('E', tag, message, error)
}
