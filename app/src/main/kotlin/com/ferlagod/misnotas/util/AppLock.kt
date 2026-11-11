// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.util

import com.ferlagod.misnotas.data.local.pref.AuthPreferences
import com.ferlagod.misnotas.platform.SecureStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Bloqueo de la app con una contraseña propia (en escritorio no hay una API común de huella
 * o PIN del sistema). La contraseña no se guarda: solo un hash PBKDF2 con salt, dentro del
 * almacén seguro.
 *
 * Se pide al abrir la app y al volver a ella tras [GRACE_PERIOD_MS] en segundo plano
 * (ventana minimizada o sin foco).
 */
object AppLock {

    /** Tiempo en segundo plano tras el que se vuelve a pedir el desbloqueo. */
    const val GRACE_PERIOD_MS = 60_000L

    private const val KEY_HASH = "app_lock_hash"
    private const val KEY_SALT = "app_lock_salt"
    /** Iteraciones de PBKDF2 del hash de la contraseña de bloqueo. */
    private const val ITERATIONS = 210_000

    /** Almacén cifrado compartido con las credenciales. */
    private val store: SecureStore get() = AuthPreferences.secureStore

    /** Hay una contraseña configurada (requisito para el bloqueo). */
    fun isAvailable(): Boolean = store.getString(KEY_HASH) != null

    /** Guarda (o cambia) la contraseña de bloqueo, con un salt nuevo. */
    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        store.edit {
            this[KEY_SALT] = encoder.encodeToString(salt)
            this[KEY_HASH] = encoder.encodeToString(hash(password, salt))
        }
    }

    /** Elimina la contraseña de bloqueo. */
    fun clearPassword() = store.edit {
        remove(KEY_HASH)
        remove(KEY_SALT)
    }

    /** `true` si [password] es la contraseña de bloqueo. Compara en tiempo constante. */
    fun verify(password: String): Boolean {
        val decoder = Base64.getDecoder()
        val salt = store.getString(KEY_SALT)?.let(decoder::decode) ?: return false
        val expected = store.getString(KEY_HASH)?.let(decoder::decode) ?: return false
        return MessageDigest.isEqual(hash(password, salt), expected)
    }

    /** Hash PBKDF2-HMAC-SHA256 de 256 bits. */
    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
