// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.sync

import com.ferlagod.misnotas.data.local.pref.AuthPreferences
import com.ferlagod.misnotas.data.remote.webdav.RemoteFileMeta
import com.ferlagod.misnotas.data.remote.webdav.WebDavClient
import com.ferlagod.misnotas.data.remote.webdav.WebDavPreconditionFailedException
import com.ferlagod.misnotas.platform.Log
import com.ferlagod.misnotas.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

/**
 * Gestiona la "bóveda" cifrada: derivación de la clave a partir de la contraseña maestra,
 * creación de `keyinfo.json` en el servidor y verificación de la contraseña.
 *
 * Garantías:
 * - Nunca se acepta una clave que no se haya comprobado contra algo cifrado con la clave real
 *   (el verificador de `keyinfo.json`, el guardado en el dispositivo o una nota del servidor).
 *   Así una contraseña equivocada no puede llegar a cifrar ni subir nada.
 * - Una contraseña correcta siempre desbloquea: se prueban sus formas Unicode equivalentes
 *   (ver [CryptoManager.passwordVariants]) y, si falta `keyinfo.json`, los parámetros guardados.
 */
class VaultManager(private val prefs: AuthPreferences) {

    private companion object {
        const val TAG = "VaultManager"
        /** Notas/carpetas del servidor que se intentan descifrar para validar una contraseña. */
        const val MAX_SAMPLES = 5
    }

    /**
     * Desbloquea (o crea) la bóveda remota con [masterPassword].
     *
     * - Si existe `keyinfo.json`: deriva la clave con su salt e iteraciones y verifica la contraseña.
     * - Si no existe pero hay notas en el servidor (bóveda de la v1.0, o `keyinfo.json` borrado):
     *   usa los parámetros guardados en el dispositivo, comprueba que descifran esas notas y
     *   vuelve a crear `keyinfo.json`.
     * - Si la carpeta está vacía: crea una bóveda nueva.
     *
     * @return la clave y si la bóveda se acaba de crear.
     */
    suspend fun unlockRemote(client: WebDavClient, masterPassword: String): Pair<SecretKey, Boolean> {
        require(masterPassword.isNotEmpty())
        val existing = readKeyInfo(client)
        if (existing != null) {
            val key = deriveFirstValid(masterPassword, existing.salt, listOf(existing.iterations)) { existing.verify(it) }
                ?: throw WrongMasterPasswordException()
            prefs.saveVaultParams(existing.salt, existing.iterations, existing.check)
            return key to false
        }

        val dataFiles = client.listRemoteFiles().filter {
            (it.name.startsWith(SyncRepository.NOTE_PREFIX) || it.name.startsWith(SyncRepository.FOLDER_PREFIX)) &&
                it.name.endsWith(SyncRepository.JSON_EXT)
        }

        val salt: ByteArray
        val iterations: Int
        val key: SecretKey
        if (dataFiles.isEmpty()) {
            salt = CryptoManager.generateSalt()
            iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
            key = derive(CryptoManager.canonicalPassword(masterPassword), salt, iterations)
        } else {
            // Hay datos cifrados pero falta keyinfo.json. La clave solo se puede reconstruir con
            // el salt que este dispositivo guardó en su día.
            salt = prefs.getSalt() ?: throw VaultException(VaultException.Reason.LEGACY_NO_SALT)
            val candidates = listOf(
                prefs.getIterations(),
                CryptoManager.DEFAULT_PBKDF2_ITERATIONS,
                CryptoManager.LEGACY_PBKDF2_ITERATIONS
            ).distinct()
            val localCheck = prefs.getKeyCheck()
            val samples = loadSamples(client, dataFiles)

            var found: Pair<SecretKey, Int>? = null
            for (iter in candidates) {
                val k = deriveFirstValid(masterPassword, salt, listOf(iter)) { k ->
                    val checkOk = localCheck?.let { CryptoManager.verify(it, KeyInfo.CHECK_PLAINTEXT, k, KeyInfo.CHECK_AAD) }
                    when {
                        checkOk == false -> false
                        // Solo hay lápidas: no existe nada cifrado que pueda quedar ilegible.
                        samples.isEmpty() -> true
                        // AES-GCM autentica: si descifra una sola muestra, la clave es la correcta.
                        // (No se exigen todas: un fichero dañado no debe bloquear una contraseña buena.)
                        else -> samples.any { it(k) }
                    }
                }
                if (k != null) {
                    found = k to iter
                    break
                }
            }
            val (k, iter) = found ?: throw WrongMasterPasswordException(WrongMasterPasswordException.Reason.MISMATCH_EXISTING)
            key = k
            iterations = iter
        }

        val info = KeyInfo.create(salt, iterations, key)
        try {
            client.uploadText(KeyInfo.FILE_NAME, info.toJson(), ifNoneMatch = true)
        } catch (_: WebDavPreconditionFailedException) {
            // Otro dispositivo creó la bóveda a la vez: usamos la suya.
            return unlockRemote(client, masterPassword)
        }
        prefs.saveVaultParams(salt, iterations, info.check)
        return key to dataFiles.isEmpty()
    }

    /**
     * Desbloqueo sin conexión con los parámetros guardados en el dispositivo.
     */
    suspend fun unlockLocal(masterPassword: String): SecretKey {
        val salt = prefs.getSalt()
            ?: throw VaultException(VaultException.Reason.NO_LOCAL_DATA)
        val check = prefs.getKeyCheck()
        return deriveFirstValid(masterPassword, salt, listOf(prefs.getIterations())) { key ->
            check == null || CryptoManager.verify(check, KeyInfo.CHECK_PLAINTEXT, key, KeyInfo.CHECK_AAD)
        } ?: throw WrongMasterPasswordException()
    }

    /**
     * Comprueba en cada sincronización que la clave local sigue siendo la de la bóveda remota.
     *
     * Si `keyinfo.json` ha desaparecido del servidor (carpeta vaciada, restaurada…) y este
     * dispositivo tiene un verificador válido para su clave, lo vuelve a crear en lugar de
     * dejar la sincronización bloqueada para siempre.
     *
     * @return true si se ha tenido que recrear la bóveda (el servidor puede haber perdido datos).
     */
    suspend fun verifyRemoteKey(client: WebDavClient, key: SecretKey): Boolean {
        val info = readKeyInfo(client)
        if (info == null) {
            val salt = prefs.getSalt()
            val check = prefs.getKeyCheck()
            if (salt == null || check == null ||
                !CryptoManager.verify(check, KeyInfo.CHECK_PLAINTEXT, key, KeyInfo.CHECK_AAD)
            ) {
                throw VaultException(VaultException.Reason.KEYINFO_MISSING)
            }
            val restored = KeyInfo(salt, prefs.getIterations(), check)
            return try {
                client.uploadText(KeyInfo.FILE_NAME, restored.toJson(), ifNoneMatch = true)
                Log.w(TAG, "keyinfo.json no estaba en el servidor; restaurado desde el dispositivo")
                true
            } catch (_: WebDavPreconditionFailedException) {
                // Otro dispositivo lo restauró a la vez.
                verifyRemoteKey(client, key)
            }
        }
        if (!info.verify(key)) {
            throw WrongMasterPasswordException(WrongMasterPasswordException.Reason.CHANGED_ON_SERVER)
        }
        if (prefs.getKeyCheck() == null) prefs.saveVaultParams(info.salt, info.iterations, info.check)
        return false
    }

    /** Lee `keyinfo.json`; null si no existe. Un fichero corrupto es un error (no se sobrescribe). */
    private suspend fun readKeyInfo(client: WebDavClient): KeyInfo? =
        client.downloadOrNull(KeyInfo.FILE_NAME)?.let { KeyInfo.parse(String(it, Charsets.UTF_8)) }

    /**
     * Descarga hasta [MAX_SAMPLES] notas/carpetas con contenido (las lápidas no sirven: no
     * llevan nada cifrado con la clave) y devuelve una comprobación por cada una.
     */
    private suspend fun loadSamples(
        client: WebDavClient,
        files: List<RemoteFileMeta>
    ): List<(SecretKey) -> Boolean> {
        val samples = mutableListOf<(SecretKey) -> Boolean>()
        for (file in files.sortedByDescending { it.contentLength }) {
            if (samples.size >= MAX_SAMPLES) break
            val raw = client.downloadOrNull(file.name)?.let { String(it, Charsets.UTF_8) } ?: continue
            val isNote = file.name.startsWith(SyncRepository.NOTE_PREFIX)
            val id = if (isNote) idOf(file.name, SyncRepository.NOTE_PREFIX) else idOf(file.name, SyncRepository.FOLDER_PREFIX)
            if (RemoteCodec.isTombstone(raw)) continue
            samples += { key ->
                try {
                    if (isNote) RemoteCodec.decodeNote(raw, id, key) else RemoteCodec.decodeFolder(raw, id, key)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return samples
    }

    /**
     * Prueba cada variante de la contraseña (y cada número de iteraciones) y devuelve la primera
     * clave que pasa [isValid], o null si ninguna.
     */
    private suspend fun deriveFirstValid(
        password: String,
        salt: ByteArray,
        iterations: List<Int>,
        isValid: (SecretKey) -> Boolean
    ): SecretKey? {
        for (iter in iterations) {
            for (variant in CryptoManager.passwordVariants(password)) {
                val key = derive(variant, salt, iter)
                if (isValid(key)) return key
            }
        }
        return null
    }

    /** Deriva la clave fuera del hilo principal (PBKDF2 es costoso a propósito). */
    private suspend fun derive(password: String, salt: ByteArray, iterations: Int): SecretKey =
        withContext(Dispatchers.Default) { CryptoManager.deriveKey(password, salt, iterations) }

    /** Id de una nota o carpeta a partir del nombre de su fichero remoto. */
    private fun idOf(fileName: String, prefix: String) =
        fileName.removePrefix(prefix).removeSuffix(SyncRepository.JSON_EXT)
}
