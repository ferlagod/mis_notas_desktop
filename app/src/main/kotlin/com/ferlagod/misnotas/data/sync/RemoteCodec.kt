// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.sync

import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.model.SyncStatus
import com.ferlagod.misnotas.security.CryptoManager
import org.json.JSONObject
import javax.crypto.SecretKey

/** Registro remoto descodificado: o bien la entidad viva, o bien una lápida de borrado. */
sealed interface RemoteRecord<out T> {
    data class Live<T>(val entity: T) : RemoteRecord<T>
    /**
     * Lápida de borrado. [verified] es true si lleva una prueba cifrada con la clave de la bóveda
     * (la generan las versiones actuales). Una lápida sin prueba puede venir de una versión
     * antigua... o del propio servidor, así que no se usa para borrar notas de forma definitiva.
     */
    data class Tombstone(val id: String, val deletedAt: Long, val verified: Boolean = false) : RemoteRecord<Nothing>
}

/**
 * Formato de los ficheros en Nextcloud (ver docs/SYNC_FORMAT.md).
 *
 * v2 (actual):
 * ```
 * { "format": "misnotas", "version": 2, "type": "note", "id": "<uuid>", "payload": "<base64 AES-GCM>" }
 * { "format": "misnotas", "version": 2, "type": "note", "id": "<uuid>", "deleted": true, "deletedAt": 0, "auth": "<base64 AES-GCM>" }
 * ```
 * El payload es un JSON cifrado con AAD = "<type>:<id>", así que todos los metadatos (carpeta,
 * color, fechas…) quedan ocultos al servidor y un fichero no puede hacerse pasar por otro.
 *
 * v1 (MisNotas 1.0, solo lectura): JSON plano con title/content/name cifrados por separado.
 */
object RemoteCodec {

    const val FORMAT = "misnotas"
    const val VERSION = 2
    const val TYPE_NOTE = "note"
    const val TYPE_FOLDER = "folder"

    fun aad(type: String, id: String) = "$type:$id"

    // ------------------------------------------------------------------
    // Notas
    // ------------------------------------------------------------------

    fun encodeNote(note: NoteEntity, key: SecretKey): String {
        val payload = JSONObject().apply {
            put("folderId", note.folderId ?: JSONObject.NULL)
            put("title", note.title)
            put("content", note.content)
            put("isPinned", note.isPinned)
            put("color", note.color)
            put("updatedAt", note.updatedAt)
            put("isTrashed", note.isDeleted)
        }
        return envelope(TYPE_NOTE, note.id)
            .put("payload", CryptoManager.encrypt(payload.toString(), key, aad(TYPE_NOTE, note.id)))
            .toString()
    }

    fun decodeNote(raw: String, expectedId: String, key: SecretKey): RemoteRecord<NoteEntity> {
        val json = JSONObject(raw)
        if (!json.has("format")) return RemoteRecord.Live(decodeLegacyNote(json, key))

        val id = checkEnvelope(json, TYPE_NOTE, expectedId)
        tombstoneOrNull(json, TYPE_NOTE, id, key)?.let { return it }

        val payload = JSONObject(CryptoManager.decrypt(json.getString("payload"), key, aad(TYPE_NOTE, id)))
        return RemoteRecord.Live(
            NoteEntity(
                id = id,
                folderId = payload.optStringOrNull("folderId"),
                title = payload.optString("title", ""),
                content = payload.optString("content", ""),
                isPinned = payload.optBoolean("isPinned", false),
                color = payload.optString("color", "DEFAULT"),
                updatedAt = payload.optLong("updatedAt", 0L),
                isDeleted = payload.optBoolean("isTrashed", false),
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    private fun decodeLegacyNote(json: JSONObject, key: SecretKey): NoteEntity = NoteEntity(
        id = json.getString("id"),
        folderId = json.optStringOrNull("folderId"),
        title = CryptoManager.decrypt(json.getString("title"), key),
        content = CryptoManager.decrypt(json.getString("content"), key),
        isPinned = json.optBoolean("isPinned", false),
        color = json.optString("color", "DEFAULT"),
        updatedAt = json.optLong("updatedAt", 0L),
        isDeleted = json.optBoolean("isDeleted", false),
        syncStatus = SyncStatus.SYNCED
    )

    // ------------------------------------------------------------------
    // Carpetas
    // ------------------------------------------------------------------

    fun encodeFolder(folder: FolderEntity, key: SecretKey): String {
        val payload = JSONObject().apply {
            put("parentId", folder.parentId ?: JSONObject.NULL)
            put("name", folder.name)
            put("color", folder.color)
            put("updatedAt", folder.updatedAt)
        }
        return envelope(TYPE_FOLDER, folder.id)
            .put("payload", CryptoManager.encrypt(payload.toString(), key, aad(TYPE_FOLDER, folder.id)))
            .toString()
    }

    fun decodeFolder(raw: String, expectedId: String, key: SecretKey): RemoteRecord<FolderEntity> {
        val json = JSONObject(raw)
        if (!json.has("format")) return RemoteRecord.Live(decodeLegacyFolder(json, key))

        val id = checkEnvelope(json, TYPE_FOLDER, expectedId)
        tombstoneOrNull(json, TYPE_FOLDER, id, key)?.let { return it }

        val payload = JSONObject(CryptoManager.decrypt(json.getString("payload"), key, aad(TYPE_FOLDER, id)))
        return RemoteRecord.Live(
            FolderEntity(
                id = id,
                parentId = payload.optStringOrNull("parentId"),
                name = payload.optString("name", ""),
                color = payload.optString("color", "DEFAULT"),
                updatedAt = payload.optLong("updatedAt", 0L),
                isDeleted = false,
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    private fun decodeLegacyFolder(json: JSONObject, key: SecretKey): FolderEntity = FolderEntity(
        id = json.getString("id"),
        parentId = json.optStringOrNull("parentId"),
        name = CryptoManager.decrypt(json.getString("name"), key),
        color = json.optString("color", "DEFAULT"),
        updatedAt = json.optLong("updatedAt", 0L),
        isDeleted = false,
        syncStatus = SyncStatus.SYNCED
    )

    // ------------------------------------------------------------------
    // Lápidas y adjuntos
    // ------------------------------------------------------------------

    /**
     * Lápida de borrado definitivo. Incluye `auth`: el instante del borrado cifrado con la clave
     * y AAD = "tombstone:<type>:<id>", para que el servidor no pueda fabricar borrados.
     */
    fun encodeTombstone(type: String, id: String, deletedAt: Long, key: SecretKey): String =
        envelope(type, id)
            .put("deleted", true)
            .put("deletedAt", deletedAt)
            .put("auth", CryptoManager.encrypt(tombstoneProof(deletedAt), key, tombstoneAad(type, id)))
            .toString()

    /** true si [raw] es una lápida (no contiene nada cifrado con la clave de las notas). */
    fun isTombstone(raw: String): Boolean = try {
        JSONObject(raw).optBoolean("deleted", false)
    } catch (_: Exception) {
        false
    }

    private fun tombstoneAad(type: String, id: String) = "tombstone:$type:$id"
    private fun tombstoneProof(deletedAt: Long) = "deleted:$deletedAt"

    fun encryptAttachment(bytes: ByteArray, id: String, key: SecretKey): ByteArray =
        CryptoManager.encryptBytes(bytes, key, aad("attachment", id))

    fun decryptAttachment(bytes: ByteArray, id: String, key: SecretKey): ByteArray =
        CryptoManager.decryptBytes(bytes, key, aad("attachment", id))

    // ------------------------------------------------------------------

    private fun envelope(type: String, id: String) = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("type", type)
        put("id", id)
    }

    private fun checkEnvelope(json: JSONObject, type: String, expectedId: String): String {
        require(json.optString("format") == FORMAT) { "Formato desconocido" }
        require(json.optInt("version", 0) <= VERSION) {
            "Fichero creado con una versión más nueva de Mis Notas; actualiza la app"
        }
        require(json.optString("type") == type) { "Tipo inesperado" }
        val id = json.getString("id")
        require(id == expectedId) { "El id del fichero no coincide con su nombre" }
        return id
    }

    /**
     * Devuelve la lápida si [json] lo es. Si trae prueba y no es válida (falsificada o de otra
     * bóveda) lanza una excepción de cifrado y el fichero se ignora.
     */
    private fun tombstoneOrNull(json: JSONObject, type: String, id: String, key: SecretKey): RemoteRecord.Tombstone? {
        if (!json.optBoolean("deleted", false)) return null
        val deletedAt = json.optLong("deletedAt", 0L)
        val auth = json.optString("auth", "")
        if (auth.isEmpty()) return RemoteRecord.Tombstone(id, deletedAt, verified = false)
        val proof = CryptoManager.decrypt(auth, key, tombstoneAad(type, id))
        if (proof != tombstoneProof(deletedAt)) throw javax.crypto.AEADBadTagException("Lápida no válida")
        return RemoteRecord.Tombstone(id, deletedAt, verified = true)
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name).takeIf { it.isNotBlank() }
}
