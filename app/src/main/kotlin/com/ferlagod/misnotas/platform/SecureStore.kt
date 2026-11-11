// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import com.ferlagod.misnotas.security.CryptoManager
import com.github.javakeyring.Keyring
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Almacén cifrado para secretos (sustituye a EncryptedSharedPreferences).
 *
 * Los valores se guardan en `secure.dat`, cifrado con AES-256-GCM. La clave de ese fichero
 * vive en el llavero del sistema:
 * - macOS: Llavero (Keychain)
 * - Windows: Administrador de credenciales
 * - Linux: Secret Service (GNOME Keyring, KWallet…)
 *
 * Si el sistema no tiene llavero disponible (p. ej. un Linux mínimo sin Secret Service), la
 * clave se guarda en `secure.key` con permisos solo para el usuario y se avisa en el registro.
 * Si la clave se pierde, el fichero es ilegible y se reinicia (como en Android al perder el Keystore).
 */
class SecureStore(private val dir: File) {

    private val dataFile = File(dir, DATA_FILE)
    private val keyFile = File(dir, KEY_FILE)
    private val values = LinkedHashMap<String, String>()
    private val key: SecretKey = loadOrCreateKey()

    init {
        load()
    }

    @Synchronized
    fun getString(name: String): String? = values[name]

    @Synchronized
    fun getInt(name: String, default: Int): Int = values[name]?.toIntOrNull() ?: default

    @Synchronized
    fun getLong(name: String, default: Long): Long = values[name]?.toLongOrNull() ?: default

    @Synchronized
    fun edit(block: MutableMap<String, String>.() -> Unit) {
        values.block()
        save()
    }

    @Synchronized
    fun clear() {
        values.clear()
        save()
    }

    // ------------------------------------------------------------------

    private fun load() {
        if (!dataFile.isFile) return
        try {
            val plain = CryptoManager.decryptBytes(dataFile.readBytes(), key, AAD)
            val json = JSONObject(String(plain, Charsets.UTF_8))
            json.keys().forEach { values[it] = json.getString(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Almacén seguro ilegible (¿se perdió la clave del llavero?); se reinicia", e)
            dataFile.delete()
            values.clear()
        }
    }

    private fun save() {
        try {
            dir.mkdirs()
            val json = JSONObject()
            values.forEach { (k, v) -> json.put(k, v) }
            val encrypted = CryptoManager.encryptBytes(json.toString().toByteArray(Charsets.UTF_8), key, AAD)
            val tmp = File(dir, "$DATA_FILE.tmp")
            tmp.writeBytes(encrypted)
            restrictToOwner(tmp)
            Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo guardar el almacén seguro", e)
        }
    }

    /**
     * Orden: un `secure.key` ya existente (se usó como alternativa) → llavero del sistema →
     * clave nueva, guardada en el llavero si es posible o en `secure.key` si no.
     */
    private fun loadOrCreateKey(): SecretKey {
        if (keyFile.isFile) {
            decodeKey(keyFile.readText().trim())?.let { return it }
        }

        val keyring = try {
            Keyring.create()
        } catch (e: Throwable) {
            Log.w(TAG, "Llavero del sistema no disponible", e)
            null
        }

        if (keyring != null) {
            try {
                decodeKey(keyring.getPassword(KEYRING_SERVICE, KEYRING_ACCOUNT))?.let { return it }
            } catch (_: Throwable) {
                // No existe todavía.
            }
        }

        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.getEncoder().encodeToString(raw)
        val storedInKeyring = keyring != null && try {
            keyring.setPassword(KEYRING_SERVICE, KEYRING_ACCOUNT, encoded)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo guardar la clave en el llavero", e)
            false
        }
        if (!storedInKeyring) {
            Log.w(TAG, "La clave del almacén seguro se guarda en ${keyFile.path} (solo legible por el usuario)")
            dir.mkdirs()
            keyFile.writeText(encoded)
            restrictToOwner(keyFile)
        }
        // Un fichero cifrado con una clave anterior ya no se podrá leer.
        dataFile.delete()
        return SecretKeySpec(raw, "AES")
    }

    private fun decodeKey(encoded: String?): SecretKey? = try {
        encoded?.let { Base64.getDecoder().decode(it) }?.takeIf { it.size == 32 }?.let { SecretKeySpec(it, "AES") }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun restrictToOwner(file: File) {
        try {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            // Windows: el perfil del usuario ya está protegido por sus permisos.
            file.setReadable(false, false); file.setReadable(true, true)
            file.setWritable(false, false); file.setWritable(true, true)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "SecureStore"
        const val DATA_FILE = "secure.dat"
        const val KEY_FILE = "secure.key"
        const val AAD = "misnotas-secure-store-v1"
        const val KEYRING_SERVICE = "Mis Notas"
        const val KEYRING_ACCOUNT = "local-storage-key"
    }
}
