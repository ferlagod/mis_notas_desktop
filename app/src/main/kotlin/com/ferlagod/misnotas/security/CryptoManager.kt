// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Motor de cifrado de extremo a extremo (E2EE).
 *
 * - Derivación de clave: PBKDF2WithHmacSHA256 (iteraciones configurables, salt aleatorio, 256 bits).
 * - Cifrado autenticado: AES-256/GCM/NoPadding con IV aleatorio de 12 bytes por operación.
 * - Formato binario: [IV (12 bytes) | CipherText | AuthTag (16 bytes)]. En texto se codifica en Base64.
 * - AAD opcional: permite vincular un cifrado a su identificador (p. ej. el id de la nota), de modo que
 *   el servidor no pueda intercambiar el contenido de dos ficheros sin que el descifrado falle.
 */
object CryptoManager {

    /** Algoritmo de las claves. */
    private const val ALGORITHM_AES = "AES"
    /** Modo de cifrado: AES-GCM sin relleno. */
    private const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"
    /** Algoritmo de derivación de clave; se guarda en `keyinfo.json`. */
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Iteraciones usadas por la versión 1.0 de la app (se mantienen para cuentas antiguas). */
    const val LEGACY_PBKDF2_ITERATIONS = 65_536

    /** Iteraciones para cuentas nuevas (recomendación OWASP para PBKDF2-HMAC-SHA256: >= 600.000). */
    const val DEFAULT_PBKDF2_ITERATIONS = 600_000

    /** Longitud de la clave AES. */
    const val KEY_LENGTH_BITS = 256
    /** Longitud del IV, nuevo y aleatorio en cada cifrado. */
    const val GCM_IV_LENGTH_BYTES = 12
    /** Longitud de la etiqueta de autenticación GCM. */
    const val GCM_TAG_LENGTH_BITS = 128
    /** Longitud del salt de las bóvedas nuevas. */
    const val DEFAULT_SALT_LENGTH_BYTES = 16

    /** Límites aceptados al leer parámetros del servidor (evita bloqueos con valores absurdos). */
    const val MIN_PBKDF2_ITERATIONS = 10_000
    /** Máximo de iteraciones aceptado. */
    const val MAX_PBKDF2_ITERATIONS = 10_000_000
    /** Longitud mínima de salt aceptada. */
    const val MIN_SALT_LENGTH_BYTES = 8

    private val secureRandom = SecureRandom()

    /**
     * Formas equivalentes de una misma contraseña, de más a menos probable.
     *
     * Distintos teclados (o sistemas) pueden escribir "ñ" o "á" como un solo carácter (NFC) o
     * como letra + tilde combinable (NFD), y el autocompletado a veces añade un espacio final.
     * Para el usuario es la misma contraseña, pero PBKDF2 derivaría claves distintas. Al
     * desbloquear se prueba cada variante contra el verificador cifrado, así que una
     * contraseña correcta nunca deja al usuario fuera. Las bóvedas nuevas usan la forma NFC.
     */
    fun passwordVariants(password: String): List<String> {
        val nfc = java.text.Normalizer.normalize(password, java.text.Normalizer.Form.NFC)
        val nfd = java.text.Normalizer.normalize(password, java.text.Normalizer.Form.NFD)
        return listOf(password, nfc, nfd, password.trim(), nfc.trim(), nfd.trim())
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /** Forma canónica con la que se crean las bóvedas nuevas. */
    fun canonicalPassword(password: String): String =
        java.text.Normalizer.normalize(password, java.text.Normalizer.Form.NFC)

    /** Salt aleatorio criptográficamente seguro. */
    fun generateSalt(sizeBytes: Int = DEFAULT_SALT_LENGTH_BYTES): ByteArray {
        val salt = ByteArray(sizeBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Deriva una clave AES-256 a partir de la contraseña maestra y el salt.
     */
    fun deriveKey(
        masterPassword: CharArray,
        salt: ByteArray,
        iterations: Int = LEGACY_PBKDF2_ITERATIONS
    ): SecretKey {
        require(masterPassword.isNotEmpty()) { "La contraseña maestra no puede estar vacía" }
        require(salt.isNotEmpty()) { "El salt no puede estar vacío" }
        require(iterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) { "Número de iteraciones fuera de rango" }

        val keySpec = PBEKeySpec(masterPassword, salt, iterations, KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val keyBytes = factory.generateSecret(keySpec).encoded
            return SecretKeySpec(keyBytes, ALGORITHM_AES)
        } finally {
            keySpec.clearPassword()
        }
    }

    /** Como la variante con [CharArray], para contraseñas en [String]. */
    fun deriveKey(
        masterPassword: String,
        salt: ByteArray,
        iterations: Int = LEGACY_PBKDF2_ITERATIONS
    ): SecretKey = deriveKey(masterPassword.toCharArray(), salt, iterations)

    // ------------------------------------------------------------------
    // Binario
    // ------------------------------------------------------------------

    /** Cifra bytes y devuelve [IV | CipherText + Tag]. */
    fun encryptBytes(plain: ByteArray, secretKey: SecretKey, aad: String? = null): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (aad != null) cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val cipherBytes = cipher.doFinal(plain)

        return iv + cipherBytes
    }

    /** Descifra un bloque [IV | CipherText + Tag]. Lanza excepción si la clave o el AAD no coinciden. */
    fun decryptBytes(combined: ByteArray, secretKey: SecretKey, aad: String? = null): ByteArray {
        val minLength = GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)
        require(combined.size >= minLength) {
            "El tamaño del payload cifrado es menor al mínimo requerido (IV + Tag)"
        }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (aad != null) cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(cipherBytes)
    }

    // ------------------------------------------------------------------
    // Texto (Base64)
    // ------------------------------------------------------------------

    /** Cifra texto UTF-8 y devuelve `IV | CipherText + Tag` en Base64. */
    fun encrypt(plainText: String, secretKey: SecretKey, aad: String? = null): String {
        val combined = encryptBytes(plainText.toByteArray(Charsets.UTF_8), secretKey, aad)
        return Base64.getEncoder().encodeToString(combined)
    }

    /** Descifra el resultado de [encrypt]. Lanza excepción si la clave o el AAD no coinciden. */
    fun decrypt(cipherText: String, secretKey: SecretKey, aad: String? = null): String {
        require(cipherText.isNotBlank()) { "El texto cifrado no puede estar vacío" }
        val combined = Base64.getDecoder().decode(cipherText)
        return String(decryptBytes(combined, secretKey, aad), Charsets.UTF_8)
    }

    /** Devuelve true si [cipherText] se puede descifrar con [secretKey] y su contenido es [expected]. */
    fun verify(cipherText: String, expected: String, secretKey: SecretKey, aad: String? = null): Boolean {
        return try {
            decrypt(cipherText, secretKey, aad) == expected
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Utilidades de serialización
    // ------------------------------------------------------------------

    /** Codifica un salt en Base64. */
    fun saltToBase64(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)

    /** Decodifica un salt en Base64. */
    fun saltFromBase64(saltBase64: String): ByteArray = Base64.getDecoder().decode(saltBase64)

    /** Codifica la clave en Base64 para guardarla en el almacén seguro. */
    fun keyToBase64(key: SecretKey): String = Base64.getEncoder().encodeToString(key.encoded)

    /** Reconstruye una clave AES a partir de su Base64. */
    fun keyFromBase64(keyBase64: String): SecretKey =
        SecretKeySpec(Base64.getDecoder().decode(keyBase64), ALGORITHM_AES)
}
