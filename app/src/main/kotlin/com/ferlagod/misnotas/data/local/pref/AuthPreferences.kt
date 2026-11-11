// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.pref

import com.ferlagod.misnotas.platform.AppDirs
import com.ferlagod.misnotas.platform.SecureStore
import com.ferlagod.misnotas.security.CryptoManager
import javax.crypto.SecretKey

/**
 * Credenciales de Nextcloud / ownCloud.
 */
data class NextcloudCredentials(
    /** URL del servidor tal como la escribió el usuario (sin `/` final). */
    val serverUrl: String,
    /** Usuario de Nextcloud. */
    val username: String,
    /** Contraseña de aplicación generada en Nextcloud (no la contraseña principal). */
    val appPassword: String
)

/**
 * Almacén seguro (fichero cifrado con una clave guardada en el llavero del sistema, ver
 * [SecureStore]) para credenciales, parámetros de derivación y la clave de cifrado desbloqueada.
 */
class AuthPreferences internal constructor(private val prefs: SecureStore) {

    companion object {
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_APP_PASSWORD = "key_app_password"
        private const val KEY_SALT_BASE64 = "key_salt_base64"
        private const val KEY_ITERATIONS = "key_kdf_iterations"
        private const val KEY_CHECK = "key_check"
        private const val KEY_DERIVED_KEY_BASE64 = "key_derived_key_base64"
        private const val KEY_LAST_SYNC = "key_last_sync"
        /** Carpeta del servidor donde se guardan los datos de la app. */
        const val DEFAULT_FOLDER_NAME = "MisNotas"

        @Volatile
        private var INSTANCE: AuthPreferences? = null

        /** Almacén compartido con el bloqueo de la app (ver AppLock). */
        val secureStore: SecureStore by lazy { SecureStore(AppDirs.dataDir) }

        /** Instancia única de la app. */
        fun getInstance(): AuthPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthPreferences(secureStore).also { INSTANCE = it }
            }
    }

    // ------------------------------------------------------------------
    // Credenciales
    // ------------------------------------------------------------------

    /** Guarda las credenciales normalizadas (sin espacios y sin `/` final en la URL). */
    fun saveCredentials(serverUrl: String, username: String, appPassword: String) {
        prefs.edit {
            this[KEY_SERVER_URL] = serverUrl.trim().trimEnd('/')
            this[KEY_USERNAME] = username.trim()
            this[KEY_APP_PASSWORD] = appPassword.trim()
        }
    }

    /** Credenciales guardadas, o `null` si falta alguno de los tres datos. */
    fun getCredentials(): NextcloudCredentials? {
        val server = prefs.getString(KEY_SERVER_URL)?.takeIf { it.isNotBlank() } ?: return null
        val user = prefs.getString(KEY_USERNAME)?.takeIf { it.isNotBlank() } ?: return null
        val pass = prefs.getString(KEY_APP_PASSWORD)?.takeIf { it.isNotBlank() } ?: return null
        return NextcloudCredentials(server, user, pass)
    }

    /** `true` si hay una cuenta conectada. */
    fun hasCredentials(): Boolean = getCredentials() != null

    /** Desconecta la cuenta (borra credenciales y fecha de la última sincronización). */
    fun clearCredentials() {
        prefs.edit {
            remove(KEY_SERVER_URL)
            remove(KEY_USERNAME)
            remove(KEY_APP_PASSWORD)
            remove(KEY_LAST_SYNC)
        }
    }

    // ------------------------------------------------------------------
    // Parámetros de la bóveda (salt, iteraciones, verificador)
    // ------------------------------------------------------------------

    /** Salt de la derivación de la clave, o `null` si no hay bóveda configurada o está dañada. */
    fun getSalt(): ByteArray? {
        val saltBase64 = prefs.getString(KEY_SALT_BASE64) ?: return null
        return try {
            CryptoManager.saltFromBase64(saltBase64)
        } catch (_: Exception) {
            null
        }
    }

    /** Iteraciones PBKDF2; las bóvedas antiguas que no las guardaban usan el valor heredado. */
    fun getIterations(): Int = prefs.getInt(KEY_ITERATIONS, CryptoManager.LEGACY_PBKDF2_ITERATIONS)

    /** Verificador cifrado que permite comprobar si una contraseña de cifrado es correcta. */
    fun getKeyCheck(): String? = prefs.getString(KEY_CHECK)

    /** Guarda los parámetros de la bóveda (los mismos que hay en el servidor). */
    fun saveVaultParams(salt: ByteArray, iterations: Int, keyCheck: String?) {
        prefs.edit {
            this[KEY_SALT_BASE64] = CryptoManager.saltToBase64(salt)
            this[KEY_ITERATIONS] = iterations.toString()
            if (keyCheck != null) this[KEY_CHECK] = keyCheck else remove(KEY_CHECK)
        }
    }

    /** Olvida los parámetros de la bóveda. */
    fun clearVaultParams() {
        prefs.edit {
            remove(KEY_SALT_BASE64)
            remove(KEY_ITERATIONS)
            remove(KEY_CHECK)
        }
    }

    // ------------------------------------------------------------------
    // Clave desbloqueada
    // ------------------------------------------------------------------

    /** Guarda la clave derivada para no pedir la contraseña de cifrado en cada arranque. */
    fun saveDerivedKey(secretKey: SecretKey) {
        prefs.edit { this[KEY_DERIVED_KEY_BASE64] = CryptoManager.keyToBase64(secretKey) }
    }

    /** Clave de cifrado desbloqueada, o `null` si hay que pedir la contraseña. */
    fun getDerivedKey(): SecretKey? {
        val keyBase64 = prefs.getString(KEY_DERIVED_KEY_BASE64) ?: return null
        return try {
            CryptoManager.keyFromBase64(keyBase64)
        } catch (_: Exception) {
            null
        }
    }

    /** Olvida la clave desbloqueada (la próxima vez se pedirá la contraseña de cifrado). */
    fun clearDerivedKey() {
        prefs.edit { remove(KEY_DERIVED_KEY_BASE64) }
    }

    /** `true` si la clave de cifrado está disponible. */
    fun isKeyUnlocked(): Boolean = getDerivedKey() != null

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    /** Guarda la fecha de la última sincronización correcta. */
    fun setLastSync(timestamp: Long) {
        prefs.edit { this[KEY_LAST_SYNC] = timestamp.toString() }
    }

    /** Fecha de la última sincronización correcta, o `null` si nunca se ha sincronizado. */
    fun getLastSync(): Long? = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    /**
     * URL WebDAV de la carpeta de la app:
     * https://servidor/remote.php/dav/files/<usuario>/MisNotas/
     */
    fun getWebDavRootUrl(): String? = getCredentials()?.let { buildWebDavRootUrl(it) }

    /**
     * Construye la URL WebDAV de la carpeta de la app. Si el usuario ya escribió una URL
     * WebDAV completa (`.../remote.php/dav/files/...`) se usa tal cual.
     */
    fun buildWebDavRootUrl(creds: NextcloudCredentials): String {
        val rawServer = creds.serverUrl.trim().trimEnd('/')
        if (rawServer.contains("/remote.php/dav/files/")) return "$rawServer/"
        val user = java.net.URLEncoder.encode(creds.username, "UTF-8").replace("+", "%20")
        return "$rawServer/remote.php/dav/files/$user/$DEFAULT_FOLDER_NAME/"
    }

    /** Borra todo: cuenta, parámetros de la bóveda, clave y fecha de sincronización. */
    fun clearAll() {
        prefs.edit {
            listOf(
                KEY_SERVER_URL, KEY_USERNAME, KEY_APP_PASSWORD, KEY_SALT_BASE64,
                KEY_ITERATIONS, KEY_CHECK, KEY_DERIVED_KEY_BASE64, KEY_LAST_SYNC
            ).forEach { remove(it) }
        }
    }
}
