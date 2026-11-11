// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Derivación de clave, cifrado AES-GCM con AAD, detección de manipulaciones y variantes de contraseña. */
class CryptoManagerTest {

    @Test
    fun deriveEncryptDecrypt() {
        val salt = CryptoManager.generateSalt(16)
        val key = CryptoManager.deriveKey("MySuperSecretMasterPassword!2026", salt)
        assertEquals(32, key.encoded.size)

        val plain = "Nota privada con acentos y emojis: ñandú 🦤"
        val c1 = CryptoManager.encrypt(plain, key)
        val c2 = CryptoManager.encrypt(plain, key)
        assertNotEquals("IV aleatorio: cifrados distintos", c1, c2)
        assertEquals(plain, CryptoManager.decrypt(c1, key))
        assertEquals(plain, CryptoManager.decrypt(c2, key))
    }

    @Test
    fun derivationIsDeterministicAndDependsOnIterations() {
        val salt = CryptoManager.generateSalt()
        val a = CryptoManager.deriveKey("pw", salt, 20_000)
        val b = CryptoManager.deriveKey("pw", salt, 20_000)
        val c = CryptoManager.deriveKey("pw", salt, 30_000)
        assertTrue(a.encoded.contentEquals(b.encoded))
        assertFalse(a.encoded.contentEquals(c.encoded))
    }

    @Test
    fun aadMustMatch() {
        val key = CryptoManager.deriveKey("pw", CryptoManager.generateSalt())
        val c = CryptoManager.encrypt("hola", key, "note:1")
        assertTrue(CryptoManager.verify(c, "hola", key, "note:1"))
        assertFalse(CryptoManager.verify(c, "hola", key, "note:2"))
        assertFalse(CryptoManager.verify(c, "hola", key, null))
    }

    @Test
    fun tamperingIsDetected() {
        val key = CryptoManager.deriveKey("pw", CryptoManager.generateSalt())
        val bytes = CryptoManager.encryptBytes("hola".toByteArray(), key)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        try {
            CryptoManager.decryptBytes(bytes, key)
            throw AssertionError("Debería detectar la manipulación")
        } catch (_: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun saltAndKeySerialization() {
        val salt = CryptoManager.generateSalt()
        assertTrue(salt.contentEquals(CryptoManager.saltFromBase64(CryptoManager.saltToBase64(salt))))
        val key = CryptoManager.deriveKey("TestPass", salt)
        assertTrue(key.encoded.contentEquals(CryptoManager.keyFromBase64(CryptoManager.keyToBase64(key)).encoded))
    }

    @Test
    fun passwordVariantsCoverUnicodeFormsAndStraySpaces() {
        val nfc = "contrase\u00F1a"          // ñ como un solo carácter
        val nfd = "contrasen\u0303a"         // n + tilde combinable
        assertTrue(nfd in CryptoManager.passwordVariants(nfc))
        assertTrue(nfc in CryptoManager.passwordVariants(nfd))
        assertTrue("clave" in CryptoManager.passwordVariants("clave "))
        assertEquals("tal cual va primero", "clave ", CryptoManager.passwordVariants("clave ").first())
        assertEquals(listOf("abc"), CryptoManager.passwordVariants("abc"))
        // Mismo texto para el usuario, misma clave tras normalizar.
        val salt = CryptoManager.generateSalt()
        val a = CryptoManager.deriveKey(CryptoManager.canonicalPassword(nfc), salt, 10_000)
        val b = CryptoManager.deriveKey(CryptoManager.canonicalPassword(nfd), salt, 10_000)
        assertEquals(CryptoManager.keyToBase64(a), CryptoManager.keyToBase64(b))
    }

    @Test(expected = IllegalArgumentException::class)
    fun absurdIterationCountIsRejected() {
        CryptoManager.deriveKey("x", CryptoManager.generateSalt(), Int.MAX_VALUE)
    }
}
