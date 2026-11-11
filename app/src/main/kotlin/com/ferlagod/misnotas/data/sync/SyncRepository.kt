// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.sync

import com.ferlagod.misnotas.R
import com.ferlagod.misnotas.data.local.withTransaction
import com.ferlagod.misnotas.i18n.Strings
import com.ferlagod.misnotas.platform.Log
import java.util.Base64
import com.ferlagod.misnotas.data.attachment.AttachmentStore
import com.ferlagod.misnotas.data.backup.BackupManager
import com.ferlagod.misnotas.data.local.AppDatabase
import com.ferlagod.misnotas.data.local.entity.AttachmentEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.entity.RemoteTombstoneEntity
import com.ferlagod.misnotas.data.local.model.NoteContentParser
import com.ferlagod.misnotas.data.local.model.SyncStatus
import com.ferlagod.misnotas.data.local.pref.AuthPreferences
import com.ferlagod.misnotas.data.local.pref.NextcloudCredentials
import com.ferlagod.misnotas.data.remote.webdav.RemoteFileMeta
import com.ferlagod.misnotas.data.remote.webdav.WebDavAuthException
import com.ferlagod.misnotas.data.remote.webdav.WebDavClient
import com.ferlagod.misnotas.data.remote.webdav.WebDavException
import com.ferlagod.misnotas.data.remote.webdav.WebDavPreconditionFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.SecretKey
import javax.net.ssl.SSLException

enum class SyncStage(val labelRes: String) {
    CONNECTING(R.string.sync_stage_connecting),
    DOWNLOADING(R.string.sync_stage_downloading),
    UPLOADING(R.string.sync_stage_uploading)
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val stage: SyncStage = SyncStage.CONNECTING) : SyncState
    /**
     * @param warnings ficheros remotos que no se pudieron leer.
     * @param protectedNotes notas que el servidor quería retirar en bloque y se han conservado
     *   (ver [SyncMerger.isMassRemoval]).
     */
    data class Success(
        val timestamp: Long = System.currentTimeMillis(),
        val warnings: Int = 0,
        val protectedNotes: Int = 0
    ) : SyncState
    data class Error(val message: String, val cause: Throwable? = null) : SyncState
}

/**
 * Sincronización bidireccional local-first con Nextcloud (WebDAV) y cifrado E2EE.
 *
 * Cada ronda:
 * 1. Comprueba credenciales y que la clave local coincide con `keyinfo.json`.
 * 2. Lista los ficheros remotos con sus ETags.
 * 3. Descarga lo que cambió en remoto y lo fusiona (ver [SyncMerger]).
 * 4. Sube adjuntos, carpetas y notas pendientes usando If-Match / If-None-Match.
 *
 * Es un singleton para que la app y la sincronización periódica compartan el mismo cerrojo.
 */
class SyncRepository private constructor() {

    private val database = AppDatabase.getInstance()
    private val folderDao = database.folderDao()
    private val noteDao = database.noteDao()
    private val attachmentDao = database.attachmentDao()
    private val tombstoneDao = database.remoteTombstoneDao()
    private val attachmentStore = AttachmentStore()

    val authPreferences = AuthPreferences.getInstance()
    val vault = VaultManager(authPreferences)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val syncMutex = Mutex()

    @Volatile
    private var rerunRequested = false
    private var warnings = 0
    private var protectedNotes = 0

    companion object {
        private const val TAG = "SyncRepository"
        const val FOLDER_PREFIX = "folder_"
        const val NOTE_PREFIX = "note_"
        const val ATTACHMENT_PREFIX = "att_"
        const val JSON_EXT = ".json"
        const val BIN_EXT = ".bin"
        private const val MAX_ROUNDS = 3
        private const val ORPHAN_ATTACHMENT_GRACE_MS = 24L * 60 * 60 * 1000

        @Volatile
        private var INSTANCE: SyncRepository? = null

        fun getInstance(): SyncRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncRepository().also { INSTANCE = it }
            }

        private fun noteFile(id: String) = "$NOTE_PREFIX$id$JSON_EXT"
        private fun folderFile(id: String) = "$FOLDER_PREFIX$id$JSON_EXT"
        private fun attachmentFile(id: String) = "$ATTACHMENT_PREFIX$id$BIN_EXT"

    }

    /** Traduce excepciones técnicas a mensajes comprensibles en el idioma de la app. */
    fun friendlyMessage(e: Throwable): String {
        val res = Strings
        return when (e) {
            is WrongMasterPasswordException -> res.get(
                when (e.reason) {
                    WrongMasterPasswordException.Reason.WRONG -> R.string.err_wrong_master
                    WrongMasterPasswordException.Reason.MISMATCH_EXISTING -> R.string.err_master_mismatch_existing
                    WrongMasterPasswordException.Reason.CHANGED_ON_SERVER -> R.string.err_master_changed
                }
            )
            is VaultException -> res.get(
                when (e.reason) {
                    VaultException.Reason.LEGACY_NO_SALT -> R.string.err_legacy_no_salt
                    VaultException.Reason.NO_LOCAL_DATA -> R.string.err_no_local_vault
                    VaultException.Reason.KEYINFO_MISSING -> R.string.err_keyinfo_missing
                }
            )
            is WebDavAuthException -> res.get(R.string.err_auth)
            is UnknownHostException -> res.get(R.string.err_unknown_host)
            is SocketTimeoutException -> res.get(R.string.err_timeout)
            is SSLException -> res.get(R.string.err_ssl)
            is WebDavException ->
                if (e.reason == WebDavException.Reason.PATH_NOT_FOUND) res.get(R.string.err_path_not_found)
                else res.get(R.string.err_server, e.code)
            is java.io.IOException -> res.get(
                R.string.err_network,
                e.localizedMessage ?: res.get(R.string.err_no_connection)
            )
            else -> e.localizedMessage ?: res.get(R.string.err_unknown)
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun createClient(credentials: NextcloudCredentials): WebDavClient = WebDavClient(
        baseUrl = authPreferences.buildWebDavRootUrl(credentials),
        username = credentials.username,
        appPassword = credentials.appPassword
    )

    // ==================================================================
    // Conexión de cuenta
    // ==================================================================

    /**
     * Conecta una cuenta: valida credenciales, desbloquea o crea la bóveda y prepara los datos
     * locales para subirse completos en la primera sincronización.
     *
     * Antes de tocar nada guarda una copia ZIP de las notas (ver [BackupManager.exportSafetyCopy]).
     * El estado local se prepara antes de guardar las credenciales y con el cerrojo de la
     * sincronización tomado, para que ninguna ronda pueda ver la cuenta nueva con el estado viejo.
     */
    suspend fun connectAccount(credentials: NextcloudCredentials, masterPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = createClient(credentials)
                client.ensureRootFolder()
                val (key, _) = vault.unlockRemote(client, masterPassword)

                try {
                    val untitled = Strings.get(R.string.untitled)
                    BackupManager().exportSafetyCopy(untitled)
                } catch (e: Exception) {
                    // La copia es una red de seguridad; si falla, la sincronización sigue protegida
                    // por las reglas de fusión y el freno de borrados masivos.
                    Log.w(TAG, "No se pudo guardar la copia previa a la sincronización", e)
                }

                syncMutex.withLock {
                    // Puede ser otra cuenta u otra bóveda: se olvida el estado remoto previo.
                    database.withTransaction {
                        noteDao.resetSyncMetadata()
                        folderDao.resetSyncMetadata()
                        attachmentDao.markAllPendingUpload()
                        tombstoneDao.clear()
                    }
                    authPreferences.saveCredentials(credentials.serverUrl, credentials.username, credentials.appPassword)
                    authPreferences.saveDerivedKey(key)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "connectAccount", e)
                Result.failure(Exception(friendlyMessage(e), e))
            }
        }

    // ==================================================================
    // Sincronización
    // ==================================================================

    suspend fun synchronize(): Result<Unit> = withContext(Dispatchers.IO) {
        val credentials = authPreferences.getCredentials()
        val key = authPreferences.getDerivedKey()
        if (credentials == null || key == null) {
            _syncState.value = SyncState.Idle
            return@withContext Result.success(Unit)
        }

        if (!syncMutex.tryLock()) {
            rerunRequested = true
            return@withContext Result.success(Unit)
        }

        try {
            val client = createClient(credentials)
            warnings = 0
            protectedNotes = 0
            var rounds = 0
            do {
                rerunRequested = false
                runRound(client, key)
                rounds++
            } while (rerunRequested && rounds < MAX_ROUNDS)

            val now = System.currentTimeMillis()
            authPreferences.setLastSync(now)
            _syncState.value = SyncState.Success(now, warnings, protectedNotes)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "synchronize", e)
            _syncState.value = SyncState.Error(friendlyMessage(e), e)
            Result.failure(e)
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun runRound(client: WebDavClient, key: SecretKey) {
        _syncState.value = SyncState.Syncing(SyncStage.CONNECTING)
        client.ensureRootFolder()
        if (vault.verifyRemoteKey(client, key)) {
            // La bóveda se ha restaurado en una carpeta vacía o incompleta: se vuelve a subir
            // todo lo que tiene este dispositivo, sin interpretar lo que falta como borrados.
            database.withTransaction {
                noteDao.resetSyncMetadata()
                folderDao.resetSyncMetadata()
                attachmentDao.markAllPendingUpload()
                tombstoneDao.clear()
            }
        }

        val remote = client.listRemoteFiles().associateBy { it.name }

        _syncState.value = SyncState.Syncing(SyncStage.DOWNLOADING)
        pullFolders(client, key, remote)
        pullNotes(client, key, remote)
        migrateLegacyImages()
        downloadMissingAttachments(client, key, remote)

        _syncState.value = SyncState.Syncing(SyncStage.UPLOADING)
        pushFolders(client, key, remote)
        pushAttachments(client, key)
        pushNotes(client, key, remote)

        cleanupOrphanAttachments()
        tombstoneDao.getAll().filter { it.fileName !in remote }.forEach { tombstoneDao.delete(it.fileName) }
    }

    // ------------------------------------------------------------------
    // Descarga
    // ------------------------------------------------------------------

    private fun idsWithPrefix(remote: Map<String, RemoteFileMeta>, prefix: String, ext: String) =
        remote.values.filter { it.name.startsWith(prefix) && it.name.endsWith(ext) }
            .associateBy { it.name.removePrefix(prefix).removeSuffix(ext) }

    private suspend fun pullFolders(client: WebDavClient, key: SecretKey, remote: Map<String, RemoteFileMeta>) {
        val remoteFolders = idsWithPrefix(remote, FOLDER_PREFIX, JSON_EXT)
        val knownTombstones = tombstoneDao.getAll().associate { it.fileName to it.etag }

        for ((id, meta) in remoteFolders) {
            val local = folderDao.getFolderById(id)
            if (local?.remoteEtag != null && local.remoteEtag == meta.etag) continue
            if (local == null && meta.etag != null && knownTombstones[meta.name] == meta.etag) continue

            val record = downloadRecord(meta.name) { RemoteCodec.decodeFolder(client.downloadText(meta.name), id, key) }
                ?: continue

            if (record is RemoteRecord.Tombstone) tombstoneDao.upsert(RemoteTombstoneEntity(meta.name, meta.etag))

            database.withTransaction {
                val current = folderDao.getFolderById(id)
                when (val action = SyncMerger.resolveFolder(current, record, meta.etag)) {
                    is MergeAction.SaveRemote -> folderDao.upsertFolder(action.entity)
                    is MergeAction.KeepLocal -> folderDao.setRemoteEtag(id, action.etag)
                    MergeAction.DeleteLocal, is MergeAction.TrashLocal -> folderDao.deletePermanently(id)
                    else -> Unit
                }
            }
        }

        for (local in folderDao.getAll()) {
            if (local.id in remoteFolders) continue
            when (SyncMerger.resolveMissingRemote(local.syncStatus, local.remoteEtag)) {
                MissingRemoteAction.DELETE_LOCAL -> folderDao.deletePermanently(local.id)
                MissingRemoteAction.MARK_FOR_UPLOAD -> folderDao.updateSyncStatus(local.id, SyncStatus.PENDING_UPLOAD)
                MissingRemoteAction.FORGET_ETAG -> folderDao.setRemoteEtag(local.id, null)
                MissingRemoteAction.NOTHING, MissingRemoteAction.TRASH_AND_REUPLOAD -> Unit
            }
        }
    }

    private suspend fun pullNotes(client: WebDavClient, key: SecretKey, remote: Map<String, RemoteFileMeta>) {
        val remoteNotes = idsWithPrefix(remote, NOTE_PREFIX, JSON_EXT)
        val res = Strings
        val conflictSuffix = res.get(R.string.conflict_suffix)
        val untitled = res.get(R.string.untitled)
        val knownTombstones = tombstoneDao.getAll().associate { it.fileName to it.etag }

        // Retiradas de notas visibles: se aplican al final, y solo si no son un borrado en bloque.
        val pendingRemovals = mutableListOf<PendingRemoval>()

        for ((id, meta) in remoteNotes) {
            val local = noteDao.getNoteById(id)
            if (local?.remoteEtag != null && local.remoteEtag == meta.etag) continue
            if (local == null && meta.etag != null && knownTombstones[meta.name] == meta.etag) continue

            val record = downloadRecord(meta.name) { RemoteCodec.decodeNote(client.downloadText(meta.name), id, key) }
                ?: continue

            if (record is RemoteRecord.Tombstone) tombstoneDao.upsert(RemoteTombstoneEntity(meta.name, meta.etag))

            database.withTransaction {
                val current = noteDao.getNoteById(id)
                val action = SyncMerger.resolveNote(current, record, meta.etag, { UUID.randomUUID().toString() }, conflictSuffix, untitled)
                if (current != null && !current.isDeleted && (action is MergeAction.DeleteLocal || action is MergeAction.TrashLocal)) {
                    pendingRemovals += PendingRemoval.Tombstone(id, record, meta.etag)
                } else {
                    applyNoteAction(id, action)
                }
            }
        }

        for (local in noteDao.getAll()) {
            if (local.id in remoteNotes) continue
            when (SyncMerger.resolveMissingRemoteNote(local.syncStatus, local.remoteEtag)) {
                MissingRemoteAction.TRASH_AND_REUPLOAD ->
                    if (local.isDeleted) noteDao.trashForReupload(local.id)
                    else pendingRemovals += PendingRemoval.Missing(local.id)
                MissingRemoteAction.DELETE_LOCAL -> noteDao.deletePermanently(local.id)
                MissingRemoteAction.MARK_FOR_UPLOAD -> noteDao.updateSyncStatus(local.id, SyncStatus.PENDING_UPLOAD)
                MissingRemoteAction.FORGET_ETAG -> noteDao.setRemoteEtag(local.id, null)
                MissingRemoteAction.NOTHING -> Unit
            }
        }

        if (pendingRemovals.isEmpty()) return

        if (SyncMerger.isMassRemoval(pendingRemovals.size, noteDao.countLive())) {
            Log.w(TAG, "El servidor retiraría ${pendingRemovals.size} notas a la vez; se conservan y se vuelven a subir")
            database.withTransaction {
                for (removal in pendingRemovals) {
                    val current = noteDao.getNoteById(removal.id) ?: continue
                    if (current.syncStatus == SyncStatus.PENDING_DELETE) continue
                    // Con el ETag de la lápida, la subida la sobrescribe; sin fichero, se crea de nuevo.
                    val etag = (removal as? PendingRemoval.Tombstone)?.etag
                    noteDao.setRemoteEtag(removal.id, etag)
                    noteDao.updateSyncStatus(removal.id, SyncStatus.PENDING_UPLOAD)
                }
            }
            protectedNotes += pendingRemovals.size
            return
        }

        for (removal in pendingRemovals) {
            database.withTransaction {
                // Se vuelve a decidir con el estado actual: la nota pudo editarse durante la descarga.
                val current = noteDao.getNoteById(removal.id) ?: return@withTransaction
                when (removal) {
                    is PendingRemoval.Tombstone -> applyNoteAction(
                        removal.id,
                        SyncMerger.resolveNote(current, removal.record, removal.etag, { UUID.randomUUID().toString() }, conflictSuffix, untitled)
                    )
                    is PendingRemoval.Missing ->
                        if (SyncMerger.resolveMissingRemoteNote(current.syncStatus, current.remoteEtag) == MissingRemoteAction.TRASH_AND_REUPLOAD) {
                            Log.w(TAG, "La nota ${current.id} desapareció del servidor sin lápida; se mueve a la papelera")
                            noteDao.trashForReupload(current.id)
                        }
                }
            }
        }
    }

    /** Nota visible que el servidor quiere retirar; se decide al final de [pullNotes]. */
    private sealed interface PendingRemoval {
        val id: String
        class Tombstone(override val id: String, val record: RemoteRecord<NoteEntity>, val etag: String?) : PendingRemoval
        class Missing(override val id: String) : PendingRemoval
    }

    private suspend fun applyNoteAction(id: String, action: MergeAction<NoteEntity>) {
        when (action) {
            is MergeAction.SaveRemote -> noteDao.upsertNote(action.entity)
            is MergeAction.KeepLocal -> noteDao.setRemoteEtag(id, action.etag)
            is MergeAction.Conflict -> {
                noteDao.upsertNote(action.main)
                noteDao.upsertNote(action.copy)
            }
            MergeAction.DeleteLocal -> noteDao.deletePermanently(id)
            is MergeAction.TrashLocal -> noteDao.trashFromRemote(id, action.etag)
            MergeAction.Skip -> Unit
        }
    }

    /**
     * Descarga y descodifica un fichero. Un fichero corrupto o de una versión futura se omite
     * (se cuenta como aviso) en lugar de detener toda la sincronización.
     */
    private suspend fun <T> downloadRecord(name: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: WebDavException) {
        if (e.code == 404) null else throw e
    } catch (e: GeneralSecurityException) {
        Log.w(TAG, "No se pudo descifrar $name", e); warnings++; null
    } catch (e: JSONException) {
        Log.w(TAG, "JSON no válido en $name", e); warnings++; null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Fichero no válido $name", e); warnings++; null
    }

    private suspend fun downloadMissingAttachments(
        client: WebDavClient,
        key: SecretKey,
        remote: Map<String, RemoteFileMeta>
    ) {
        val referenced = noteDao.getAllLiveContents().flatMapTo(mutableSetOf()) { AttachmentStore.referencedIds(it) }
        for (id in referenced) {
            if (attachmentStore.exists(id)) continue
            val name = attachmentFile(id)
            if (name !in remote) continue
            val bytes = downloadRecord(name) { RemoteCodec.decryptAttachment(client.download(name), id, key) } ?: continue
            attachmentStore.write(id, bytes)
            attachmentDao.upsert(
                AttachmentEntity(id = id, mimeType = AttachmentStore.detectMimeType(bytes), size = bytes.size.toLong(), isUploaded = true)
            )
        }
    }

    // ------------------------------------------------------------------
    // Subida
    // ------------------------------------------------------------------

    private suspend fun pushFolders(client: WebDavClient, key: SecretKey, remote: Map<String, RemoteFileMeta>) {
        for (folder in folderDao.getFoldersBySyncStatus(SyncStatus.PENDING_UPLOAD)) {
            val name = folderFile(folder.id)
            val etag = putGuarded(client, name, RemoteCodec.encodeFolder(folder, key), folder.remoteEtag) ?: continue
            if (folderDao.markSynced(folder.id, folder.updatedAt, etag.value) == 0) {
                folderDao.setRemoteEtag(folder.id, etag.value)
            }
        }

        for (folder in folderDao.getFoldersBySyncStatus(SyncStatus.PENDING_DELETE)) {
            val name = folderFile(folder.id)
            val knownEtag = folder.remoteEtag ?: remote[name]?.etag
            if (knownEtag == null) {
                folderDao.deletePermanently(folder.id)
                continue
            }
            val etag = putGuarded(client, name, RemoteCodec.encodeTombstone(RemoteCodec.TYPE_FOLDER, folder.id, folder.updatedAt, key), knownEtag)
                ?: continue
            tombstoneDao.upsert(RemoteTombstoneEntity(name, etag.value))
            folderDao.deletePermanently(folder.id)
        }
    }

    private suspend fun pushAttachments(client: WebDavClient, key: SecretKey) {
        for (attachment in attachmentDao.getPendingUpload()) {
            if (!attachmentStore.exists(attachment.id)) {
                attachmentDao.delete(attachment.id)
                continue
            }
            val encrypted = RemoteCodec.encryptAttachment(attachmentStore.read(attachment.id), attachment.id, key)
            try {
                client.upload(attachmentFile(attachment.id), encrypted, "application/octet-stream", ifNoneMatch = true)
            } catch (_: WebDavPreconditionFailedException) {
                // Ya existe en el servidor: los adjuntos son inmutables.
            }
            attachmentDao.markUploaded(attachment.id)
        }
    }

    private suspend fun pushNotes(client: WebDavClient, key: SecretKey, remote: Map<String, RemoteFileMeta>) {
        for (note in noteDao.getNotesBySyncStatus(SyncStatus.PENDING_UPLOAD)) {
            val name = noteFile(note.id)
            val etag = putGuarded(client, name, RemoteCodec.encodeNote(note, key), note.remoteEtag) ?: continue
            if (noteDao.markSynced(note.id, note.updatedAt, etag.value) == 0) {
                // El usuario editó la nota mientras se subía: sigue pendiente, pero con el ETag nuevo.
                noteDao.setRemoteEtag(note.id, etag.value)
            }
        }

        for (note in noteDao.getNotesBySyncStatus(SyncStatus.PENDING_DELETE)) {
            val name = noteFile(note.id)
            val knownEtag = note.remoteEtag ?: remote[name]?.etag
            if (knownEtag == null) {
                noteDao.deletePermanently(note.id)
                continue
            }
            val etag = putGuarded(client, name, RemoteCodec.encodeTombstone(RemoteCodec.TYPE_NOTE, note.id, note.updatedAt, key), knownEtag)
                ?: continue
            tombstoneDao.upsert(RemoteTombstoneEntity(name, etag.value))
            noteDao.deletePermanently(note.id)
        }
    }

    private class Etag(val value: String?)

    /**
     * PUT con control de concurrencia. Devuelve null si el servidor rechazó la subida porque
     * el fichero cambió (se pedirá otra ronda para fusionar primero).
     */
    private suspend fun putGuarded(client: WebDavClient, name: String, body: String, knownEtag: String?): Etag? {
        return try {
            val etag = client.uploadText(name, body, ifMatch = knownEtag, ifNoneMatch = knownEtag == null)
            Etag(etag ?: client.fetchEtag(name))
        } catch (_: WebDavPreconditionFailedException) {
            rerunRequested = true
            null
        }
    }

    // ------------------------------------------------------------------
    // Mantenimiento
    // ------------------------------------------------------------------

    /**
     * Convierte las imágenes Base64 incrustadas de la v1.0 en adjuntos independientes.
     */
    suspend fun migrateLegacyImages() = withContext(Dispatchers.IO) {
        for (note in noteDao.getNotesWithLegacyImages()) {
            val payload = NoteContentParser.parse(note.content)
            if (payload.images.isEmpty()) continue

            var text = payload.text
            for ((legacyKey, dataUri) in payload.images) {
                val base64 = dataUri.substringAfter("base64,", missingDelimiterValue = "")
                if (base64.isEmpty()) continue
                val bytes = try {
                    Base64.getMimeDecoder().decode(base64)
                } catch (_: IllegalArgumentException) {
                    continue
                }
                val id = UUID.randomUUID().toString()
                attachmentStore.write(id, bytes)
                attachmentDao.upsert(AttachmentEntity(id = id, mimeType = "image/webp", size = bytes.size.toLong()))
                text = text.replace("($legacyKey)", "(${AttachmentStore.reference(id)})")
            }
            noteDao.upsertNote(
                note.copy(
                    content = text,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = if (note.syncStatus == SyncStatus.PENDING_DELETE) note.syncStatus else SyncStatus.PENDING_UPLOAD
                )
            )
        }
    }

    /** Borra en local los adjuntos que ya no usa ninguna nota (tras un periodo de gracia). */
    private suspend fun cleanupOrphanAttachments() {
        val referenced = noteDao.getAllLiveContents().flatMapTo(mutableSetOf()) { AttachmentStore.referencedIds(it) }
        val threshold = System.currentTimeMillis() - ORPHAN_ATTACHMENT_GRACE_MS
        for (attachment in attachmentDao.getAll()) {
            if (attachment.id !in referenced && attachment.createdAt < threshold) {
                attachmentStore.delete(attachment.id)
                attachmentDao.delete(attachment.id)
            }
        }
    }
}
