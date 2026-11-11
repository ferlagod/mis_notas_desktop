// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Proceso auxiliar: intenta adquirir el bloqueo e imprime el resultado. */
object SingleInstanceProbe {
    @JvmStatic
    fun main(args: Array<String>) = println(SingleInstance.acquire(File(args[0])))
}

/** Bloqueo de instancia única, comprobado desde un segundo proceso. */
class SingleInstanceTest {

    private fun otherProcessAcquires(dir: File): Boolean {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), SingleInstanceProbe::class.java.name, dir.path)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim().lines().last()
        process.waitFor()
        return output.toBooleanStrict()
    }

    @Test
    fun secondProcessIsRejected() {
        val dir = Files.createTempDirectory("misnotas-lock").toFile()
        try {
            // Sin nadie más, otro proceso puede abrir la app.
            assertEquals(true, otherProcessAcquires(dir))
            // Con la app "abierta" en este proceso, otro no.
            assertTrue(SingleInstance.acquire(dir))
            assertEquals(false, otherProcessAcquires(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
