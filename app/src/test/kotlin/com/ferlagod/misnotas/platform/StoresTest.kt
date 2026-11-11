// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StoresTest {

    @Test
    fun propertiesPersist() {
        val dir = Files.createTempDirectory("misnotas-prefs").toFile()
        try {
            val file = File(dir, "settings.properties")
            PropertiesStore(file).edit {
                putBoolean("grid", false)
                putString("lang", "es")
                putInt("w", 900)
            }
            val again = PropertiesStore(file)
            assertFalse(again.getBoolean("grid", true))
            assertEquals("es", again.getString("lang"))
            assertEquals(900, again.getInt("w", 0))
            assertNull(again.getString("nope"))
        } finally {
            dir.deleteRecursively()
        }
    }
}

class SystemTrustTest {
    @Test
    fun includesJavaAndSystemRoots() {
        val issuers = SystemTrust.trustManager.acceptedIssuers
        // Al menos los certificados raíz de Java.
        org.junit.Assert.assertTrue(issuers.size > 50)
    }
}
