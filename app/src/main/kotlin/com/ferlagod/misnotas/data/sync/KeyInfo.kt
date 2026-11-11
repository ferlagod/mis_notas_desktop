// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.sync

import com.ferlagod.misnotas.security.CryptoManager
import org.json.JSONObject
import javax.crypto.SecretKey

/**
 * Contenido de `keyinfo.json` en el servidor: parámetros públicos para derivar la clave
 * en cualquier dispositivo y un verificador cifrado para comprobar la contraseña maestra.
 */
data class KeyInfo(
    /** Salt de PBKDF2. */
    val salt: ByteArray,
    /** Iteraciones de PBKDF2. */
    val iterations: Int,
    /** [CHECK_PLAINTEXT] cifrado con la clave; si se descifra, la contraseña es correcta. */
    val check: String
) {
    /** Serializa a JSON legible (con sangría) para subirlo al servidor. */
    fun toJson(): String = JSONObject().apply {
        put("format", "misnotas-keyinfo")
        put("version", 1)
        put("kdf", CryptoManager.KDF_ALGORITHM)
        put("iterations", iterations)
        put("salt", CryptoManager.saltToBase64(salt))
        put("check", check)
    }.toString(2)

    /** `true` si [key] descifra el verificador, es decir, si la contraseña maestra es correcta. */
    fun verify(key: SecretKey): Boolean = CryptoManager.verify(check, CHECK_PLAINTEXT, key, CHECK_AAD)

    override fun equals(other: Any?): Boolean =
        other is KeyInfo && salt.contentEquals(other.salt) && iterations == other.iterations && check == other.check

    override fun hashCode(): Int = 31 * (31 * salt.contentHashCode() + iterations) + check.hashCode()

    companion object {
        /** Nombre del fichero en el servidor. */
        const val FILE_NAME = "keyinfo.json"
        /** Texto conocido que se cifra como verificador. */
        const val CHECK_PLAINTEXT = "misnotas-key-check-v1"
        /** Datos asociados (AAD) del verificador. */
        const val CHECK_AAD = "keyinfo"

        /** Crea los parámetros de una bóveda nueva con su verificador cifrado con [key]. */
        fun create(salt: ByteArray, iterations: Int, key: SecretKey) =
            KeyInfo(salt, iterations, CryptoManager.encrypt(CHECK_PLAINTEXT, key, CHECK_AAD))

        /**
         * Lee y valida `keyinfo.json`. Lanza [IllegalArgumentException] si el formato, el algoritmo,
         * el salt o las iteraciones no son válidos.
         */
        fun parse(raw: String): KeyInfo {
            val json = JSONObject(raw)
            require(json.optString("format") == "misnotas-keyinfo") { "keyinfo.json no válido" }
            require(json.optString("kdf", CryptoManager.KDF_ALGORITHM) == CryptoManager.KDF_ALGORITHM) {
                "Algoritmo de derivación no soportado"
            }
            val salt = CryptoManager.saltFromBase64(json.getString("salt"))
            val iterations = json.getInt("iterations")
            val check = json.getString("check")
            require(salt.size >= CryptoManager.MIN_SALT_LENGTH_BYTES) { "Salt demasiado corto" }
            require(iterations in CryptoManager.MIN_PBKDF2_ITERATIONS..CryptoManager.MAX_PBKDF2_ITERATIONS) {
                "Iteraciones fuera de rango"
            }
            require(check.isNotBlank()) { "Verificador vacío" }
            return KeyInfo(salt = salt, iterations = iterations, check = check)
        }
    }
}

/** La contraseña maestra no coincide con la de la bóveda. */
class WrongMasterPasswordException(val reason: Reason = Reason.WRONG) : Exception("Wrong master password: $reason") {
    /** Por qué no se acepta la contraseña. */
    enum class Reason {
        /** La contraseña no verifica contra keyinfo.json. */
        WRONG,
        /** No descifra las notas v1.0 ya existentes en el servidor. */
        MISMATCH_EXISTING,
        /** La bóveda del servidor cambió y la clave local ya no sirve. */
        CHANGED_ON_SERVER
    }
}

/** Estado de la bóveda que impide continuar. */
class VaultException(val reason: Reason) : Exception("Vault error: $reason") {
    /** Qué impide continuar. */
    enum class Reason {
        /** Hay ficheros v1.0 en el servidor y este dispositivo no tiene el salt original. */
        LEGACY_NO_SALT,
        /** No hay parámetros de cifrado guardados en el dispositivo. */
        NO_LOCAL_DATA,
        /** Falta keyinfo.json en el servidor. */
        KEYINFO_MISSING
    }
}
