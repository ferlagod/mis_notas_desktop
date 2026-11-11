// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.misnotas.R
import com.ferlagod.misnotas.data.attachment.AttachmentStore
import com.ferlagod.misnotas.data.backup.BackupManager
import com.ferlagod.misnotas.data.local.AppDatabase
import com.ferlagod.misnotas.data.local.entity.AttachmentEntity
import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.model.FolderTree
import com.ferlagod.misnotas.data.local.model.SyncStatus
import com.ferlagod.misnotas.data.local.pref.AppSettings
import com.ferlagod.misnotas.data.local.pref.AppSettingsState
import com.ferlagod.misnotas.data.local.pref.NextcloudCredentials
import com.ferlagod.misnotas.data.local.pref.SortOrder
import com.ferlagod.misnotas.data.local.pref.ThemeMode
import com.ferlagod.misnotas.data.local.withTransaction
import com.ferlagod.misnotas.i18n.Strings
import com.ferlagod.misnotas.data.sync.SyncMerger
import com.ferlagod.misnotas.data.sync.SyncRepository
import com.ferlagod.misnotas.data.sync.SyncState
import com.ferlagod.misnotas.data.sync.SyncWorker
import com.ferlagod.misnotas.ui.theme.NoteColor
import com.ferlagod.misnotas.util.ImageCompressor
import com.ferlagod.misnotas.util.TextSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Filtro de navegación para la vista de notas.
 */
sealed interface NotesFilter {
    /** Todas las notas fuera de la papelera. */
    data object All : NotesFilter
    /** Solo las fijadas. */
    data object Pinned : NotesFilter
    /** La papelera. */
    data object Trash : NotesFilter
    /** Las notas de una carpeta (sin las de sus subcarpetas). */
    data class Folder(val folderId: String, val folderName: String) : NotesFilter
}

/** Avisos puntuales para la interfaz (snackbars). */
sealed interface UiEvent {
    /** Se movieron notas a la papelera: la interfaz ofrece "Deshacer". */
    data class NotesTrashed(val ids: List<String>) : UiEvent
    /** Mensaje ya traducido para mostrar tal cual. */
    data class Message(val text: String) : UiEvent
}

/** Navegación pedida desde fuera de la pantalla actual (menú de la ventana, atajos de teclado). */
sealed interface PendingNavigation {
    /** Abrir el editor con una nota nueva. */
    data object NewNote : PendingNavigation
    /** Abrir una nota existente. */
    data class OpenNote(val noteId: String) : PendingNavigation
    /** Abrir los ajustes. */
    data object Settings : PendingNavigation
}

/**
 * ViewModel principal: sesión/cifrado, sincronización, ajustes, carpetas y notas.
 */
class MainViewModel : ViewModel() {

    private val database = AppDatabase.getInstance()
    private val folderDao = database.folderDao()
    private val noteDao = database.noteDao()
    private val attachmentDao = database.attachmentDao()
    private val attachmentStore = AttachmentStore()
    private val backupManager = BackupManager()

    private val syncRepository = SyncRepository.getInstance()
    private val authPreferences = syncRepository.authPreferences
    private val appSettings = AppSettings.getInstance()

    /** Estado de la sincronización. */
    val syncState: StateFlow<SyncState> = syncRepository.syncState
    /** Preferencias de aspecto y comportamiento. */
    val settings: StateFlow<AppSettingsState> = appSettings.state

    /** Serializa las escrituras de notas (p. ej. autoguardado seguido de "mover a papelera"). */
    private val noteWriteMutex = Mutex()

    /**
     * Última versión de cada nota que conoce el editor. Si al guardar la base de datos tiene otra
     * cosa, es que cambió mientras la nota estaba abierta (sincronización, o acciones de la lista
     * en el panel lateral de una pantalla ancha):
     * - Texto: en vez de pisar lo recibido se conserva como copia "(conflicto)".
     * - Carpeta, color y fijado: solo se aplican los que el usuario cambió en el editor.
     */
    private data class EditorBase(
        val title: String,
        val content: String,
        val folderId: String?,
        val color: String,
        val isPinned: Boolean
    )

    /** Versión de partida de cada nota abierta en el editor, por id (ver [EditorBase]). */
    private val editorBases = java.util.concurrent.ConcurrentHashMap<String, EditorBase>()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    /** Avisos puntuales; cada uno se entrega una sola vez. */
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _pendingNavigation = MutableStateFlow<PendingNavigation?>(null)
    /** Navegación pendiente; la pantalla la ejecuta y llama a [consumeNavigation]. */
    val pendingNavigation: StateFlow<PendingNavigation?> = _pendingNavigation.asStateFlow()

    private val _isConfigured = MutableStateFlow(authPreferences.hasCredentials())
    /** Hay una cuenta de Nextcloud conectada. */
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isUnlocked = MutableStateFlow(
        if (authPreferences.hasCredentials()) authPreferences.isKeyUnlocked() else true
    )
    /** La clave de cifrado está disponible. Sin cuenta conectada siempre es `true`. */
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _currentFilter = MutableStateFlow<NotesFilter>(NotesFilter.All)
    /** Qué notas se están viendo. */
    val currentFilter: StateFlow<NotesFilter> = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    /** Texto de búsqueda actual. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** Hay una importación/exportación en curso. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Todas las notas fuera de la papelera. */
    val allActiveNotes = noteDao.getAllNotesFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Notas de la papelera. */
    val trashNotes = noteDao.getTrashNotesFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Todas las carpetas, por nombre. */
    val allFolders = folderDao.getAllFoldersFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Carpetas indexadas por id. */
    val foldersMap: StateFlow<Map<String, FolderEntity>> = allFolders
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Índice de búsqueda (texto normalizado sin acentos), recalculado solo cuando cambian las notas. */
    private val searchIndex = combine(allActiveNotes, trashNotes) { active, trash ->
        (active + trash).associate { it.id to TextSearch.normalize(it.title + "\n" + it.content) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Notas que muestra la lista: filtro actual, búsqueda (todas las palabras, sin acentos) y orden
     * elegido, con las fijadas primero. La papelera se ordena siempre por fecha.
     */
    val displayedNotes: StateFlow<List<NoteEntity>> = combine(
        combine(allActiveNotes, trashNotes, ::Pair),
        _currentFilter,
        _searchQuery,
        settings.map { it.sortOrder }.distinctUntilChanged(),
        searchIndex
    ) { (activeNotes, trashList), filter, query, sortOrder, index ->
        val filtered = when (filter) {
            is NotesFilter.All -> activeNotes
            is NotesFilter.Pinned -> activeNotes.filter { it.isPinned }
            is NotesFilter.Trash -> trashList
            is NotesFilter.Folder -> activeNotes.filter { it.folderId == filter.folderId }
        }
        val terms = TextSearch.terms(query)
        val matching = if (terms.isEmpty()) filtered else filtered.filter { note ->
            val haystack = index[note.id] ?: TextSearch.normalize(note.title + "\n" + note.content)
            terms.all { haystack.contains(it) }
        }
        if (filter is NotesFilter.Trash) matching.sortedByDescending { it.updatedAt }
        else matching.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.then(sortOrder.comparator()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Número de notas fuera de la papelera. */
    val totalActiveNotesCount = allActiveNotes.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Número de notas fijadas. */
    val pinnedNotesCount = allActiveNotes.map { notes -> notes.count { it.isPinned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Número de notas en la papelera. */
    val trashNotesCount = trashNotes.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Texto traducido. */
    private fun text(id: String, vararg args: Any): String = Strings.get(id, *args)

    /** Texto plural traducido, con [count] como argumento. */
    private fun quantity(id: String, count: Int): String = Strings.quantity(id, count, count)

    /** Guardados de notas en curso (se esperan antes de cerrar la app). */
    private val pendingWrites = java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()

    init {
        viewModelScope.launch {
            syncRepository.migrateLegacyImages()
            purgeOldTrash()
        }
        if (_isConfigured.value && _isUnlocked.value) {
            SyncWorker.schedule()
            triggerSync()
        }
    }

    // ==========================================
    // Ajustes
    // ==========================================

    /** Cambia entre cuadrícula y lista. */
    fun toggleLayoutMode() = appSettings.setGridLayout(!settings.value.gridLayout)
    /** Cambia el orden de las notas. */
    fun setSortOrder(order: SortOrder) = appSettings.setSortOrder(order)
    /** Cambia el tema. */
    fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)
    /** Activa o desactiva el negro puro en modo oscuro. */
    fun setAmoled(enabled: Boolean) = appSettings.setAmoled(enabled)
    /** Activa o desactiva el bloqueo al abrir. */
    fun setAppLock(enabled: Boolean) = appSettings.setAppLock(enabled)

    /** Activa o desactiva el vaciado automático de la papelera; al activarlo se aplica enseguida. */
    fun setAutoPurgeTrash(enabled: Boolean) {
        appSettings.setAutoPurgeTrash(enabled)
        if (enabled) viewModelScope.launch { purgeOldTrash() }
    }

    /** Vacía de la papelera las notas que llevan más de 30 días en ella (si está activado). */
    private suspend fun purgeOldTrash() {
        if (!settings.value.autoPurgeTrash) return
        val before = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(AppSettings.TRASH_RETENTION_DAYS.toLong())
        val ids = noteDao.getTrashIdsOlderThan(before)
        if (ids.isEmpty()) return
        noteWriteMutex.withLock {
            database.withTransaction { ids.forEach { purgeLocked(it) } }
        }
        triggerSync()
    }

    // ==========================================
    // Cuenta y cifrado
    // ==========================================

    /**
     * Conecta una cuenta de Nextcloud y desbloquea (o crea) su bóveda cifrada.
     * Exige todos los campos y una URL `https://`. Llama a [onError] con un mensaje traducido si falla.
     */
    fun setupAndUnlock(
        serverUrl: String,
        username: String,
        appPassword: String,
        masterPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (serverUrl.isBlank() || username.isBlank() || appPassword.isBlank() || masterPassword.isBlank()) {
            onError(text(R.string.err_all_fields))
            return
        }
        val url = serverUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            onError(text(R.string.err_https_required))
            return
        }
        viewModelScope.launch {
            val credentials = NextcloudCredentials(url, username.trim(), appPassword.trim())
            syncRepository.connectAccount(credentials, masterPassword)
                .onSuccess {
                    _isConfigured.value = true
                    _isUnlocked.value = true
                    SyncWorker.schedule()
                    triggerSync()
                    onSuccess()
                }
                .onFailure { onError(it.message ?: text(R.string.err_connect_failed)) }
        }
    }

    /** Desbloquea la bóveda con la contraseña de cifrado y arranca la sincronización. */
    fun unlockWithPassword(
        masterPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val key = syncRepository.vault.unlockLocal(masterPassword)
                authPreferences.saveDerivedKey(key)
                _isUnlocked.value = true
                SyncWorker.schedule()
                triggerSync()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(syncRepository.friendlyMessage(e))
            }
        }
    }

    /** Olvida la clave de cifrado: la próxima vez se pedirá la contraseña. Sin cuenta no hace nada. */
    fun lock() {
        if (!_isConfigured.value) return
        authPreferences.clearDerivedKey()
        _isUnlocked.value = false
    }

    /** Desconecta la cuenta pero conserva los parámetros de la bóveda y las notas locales. */
    fun disconnectNextcloud() {
        SyncWorker.cancel()
        authPreferences.clearCredentials()
        authPreferences.clearDerivedKey()
        syncRepository.resetSyncState()
        _isConfigured.value = false
        _isUnlocked.value = true
    }

    /** Desconecta la cuenta y olvida también los parámetros de la bóveda. Las notas locales se conservan. */
    fun logout() {
        SyncWorker.cancel()
        authPreferences.clearAll()
        syncRepository.resetSyncState()
        _isConfigured.value = false
        _isUnlocked.value = true
    }

    /** Lee la nota después de cualquier guardado pendiente (p. ej. al pasar del panel a pantalla completa). */
    suspend fun getNoteById(id: String): NoteEntity? = noteWriteMutex.withLock { noteDao.getNoteById(id) }

    /** Nota en vivo, para que el editor muestre cambios de otros dispositivos si no se ha tocado. */
    fun observeNote(id: String): Flow<NoteEntity?> = noteDao.getNoteByIdFlow(id)

    /** Credenciales de la cuenta conectada, o `null`. */
    fun getCredentials(): NextcloudCredentials? = authPreferences.getCredentials()

    /** Fecha de la última sincronización correcta, o `null`. */
    fun getLastSync(): Long? = authPreferences.getLastSync()

    // ==========================================
    // Sincronización
    // ==========================================

    /** Sincroniza en segundo plano si hay cuenta y la bóveda está desbloqueada. */
    fun triggerSync() {
        if (!_isConfigured.value || !_isUnlocked.value) return
        viewModelScope.launch { syncRepository.synchronize() }
    }

    // ==========================================
    // Navegación y búsqueda
    // ==========================================

    /** Cambia el filtro de notas y borra la búsqueda. */
    fun setFilter(filter: NotesFilter) {
        _currentFilter.value = filter
        _searchQuery.value = ""
    }

    /** Cambia el texto de búsqueda. */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Pide una navegación desde fuera de la pantalla actual. */
    fun requestNavigation(navigation: PendingNavigation) {
        _pendingNavigation.value = navigation
    }

    /** Marca la navegación pendiente como hecha. */
    fun consumeNavigation() {
        _pendingNavigation.value = null
    }

    // ==========================================
    // Carpetas
    // ==========================================

    /** Crea una carpeta (no hace nada si el nombre está vacío). */
    fun createFolder(name: String, color: NoteColor, parentId: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            folderDao.upsertFolder(
                FolderEntity(
                    id = UUID.randomUUID().toString(),
                    parentId = parentId,
                    name = name.trim(),
                    color = color.key,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING_UPLOAD
                )
            )
            triggerSync()
        }
    }

    /**
     * Cambia nombre, color y carpeta padre. Si el padre crearía un ciclo, la carpeta pasa al nivel superior.
     */
    fun updateFolder(id: String, name: String, color: NoteColor, parentId: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            // Evita ciclos: una carpeta no puede colgar de sí misma ni de una de sus subcarpetas.
            val folders = folderDao.getAll().filter { !it.isDeleted }
            val safeParent = parentId?.takeUnless { FolderTree.wouldCreateCycle(folders, id, it) }
            folderDao.updateFolderDetails(id, name.trim(), color.key, safeParent)
            triggerSync()
        }
    }

    /**
     * Elimina una carpeta: sus notas pasan a "Todas las notas" y sus subcarpetas suben un nivel.
     */
    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            database.withTransaction {
                val folder = folderDao.getFolderById(folderId) ?: return@withTransaction
                noteDao.detachFromFolder(folderId)
                folderDao.reparentChildren(folderId, folder.parentId)
                if (_isConfigured.value) folderDao.markAsDeleted(folderId)
                else folderDao.deletePermanently(folderId)
            }
            if (_currentFilter.value.let { it is NotesFilter.Folder && it.folderId == folderId }) {
                _currentFilter.value = NotesFilter.All
            }
            triggerSync()
        }
    }

    // ==========================================
    // Notas
    // ==========================================

    /**
     * Guarda una nota. No hace nada si no hay cambios reales (así abrir y cerrar una nota no
     * altera su fecha ni provoca subidas). Conserva el estado remoto y de papelera existentes.
     *
     * @param sync si es false solo guarda en local (autoguardado mientras se escribe).
     * @param closing true al salir del editor: una nota que se ha quedado vacía se descarta.
     */
    fun saveNote(
        id: String,
        title: String,
        content: String,
        folderId: String? = null,
        color: NoteColor = NoteColor.DEFAULT,
        isPinned: Boolean = false,
        sync: Boolean = true,
        closing: Boolean = false
    ) {
        val job = viewModelScope.launch {
            noteWriteMutex.withLock { saveNoteLocked(id, title, content, folderId, color, isPinned, closing) }
            if (sync) triggerSync()
        }
        pendingWrites += job
        job.invokeOnCompletion { pendingWrites -= job }
    }

    /** Espera a que terminen los guardados pendientes (al cerrar la ventana). */
    suspend fun awaitPendingWrites() {
        pendingWrites.toList().joinAll()
        noteWriteMutex.withLock { }
    }

    /** El editor ha cargado (o adoptado) esta versión de la nota y muestra estos datos. */
    fun registerEditorBase(id: String, title: String, content: String, folderId: String?, color: NoteColor, isPinned: Boolean) {
        editorBases[id] = EditorBase(title, content, folderId, color.key, isPinned)
    }

    /** [saveNoteInTransaction] dentro de una transacción. Se llama con [noteWriteMutex] tomado. */
    private suspend fun saveNoteLocked(
        id: String,
        title: String,
        content: String,
        folderId: String?,
        color: NoteColor,
        isPinned: Boolean,
        closing: Boolean
    ) = database.withTransaction {
        saveNoteInTransaction(id, title, content, folderId, color, isPinned, closing)
    }

    /**
     * Lógica de [saveNote]: crea, actualiza o descarta la nota comparando con lo guardado y con
     * la versión de partida del editor, para no pisar cambios recibidos mientras estaba abierta.
     */
    private suspend fun saveNoteInTransaction(
        id: String,
        title: String,
        content: String,
        folderId: String?,
        color: NoteColor,
        isPinned: Boolean,
        closing: Boolean
    ) {
        val existing = noteDao.getNoteById(id)
        var cleanTitle = title.trim()
        var cleanContent = content.trimEnd()

        val base = editorBases[id]
        // Carpeta, color y fijado: si el editor no los ha cambiado, se respeta lo que haya en la
        // base de datos (quizá lo cambió otro dispositivo o la lista mientras la nota estaba abierta).
        val finalFolderId = if (existing != null && base != null && folderId == base.folderId) existing.folderId else folderId
        val finalColor = if (existing != null && base != null && color.key == base.color) existing.color else color.key
        val finalPinned = if (existing != null && base != null && isPinned == base.isPinned) existing.isPinned else isPinned
        val editorMeta = { t: String, c: String -> EditorBase(t, c, folderId, color.key, isPinned) }

        if (existing != null && base != null && (existing.title != base.title || existing.content != base.content)) {
            if (cleanTitle == base.title.trim() && cleanContent == base.content.trimEnd()) {
                // El usuario no ha tocado el texto: gana la versión recibida (solo se aplican
                // los cambios de color, carpeta o fijado que haya hecho en el editor).
                cleanTitle = existing.title
                cleanContent = existing.content
            } else if (existing.title != cleanTitle || existing.content != cleanContent) {
                // Ambos lados cambiaron el texto: la versión recibida se guarda como copia.
                noteDao.upsertNote(
                    SyncMerger.conflictCopy(
                        existing,
                        UUID.randomUUID().toString(),
                        text(R.string.conflict_suffix),
                        text(R.string.untitled)
                    )
                )
            }
            editorBases[id] = editorMeta(cleanTitle, cleanContent)
        }

        if (cleanTitle.isEmpty() && cleanContent.isBlank()) {
            // Como en Keep o Apple Notas: una nota vacía no se guarda. Si antes tenía contenido
            // (el autoguardado no guarda vacíos, así que sigue en la base de datos) va a la
            // papelera con opción de deshacer, por si se borró todo sin querer.
            if (existing != null && closing && !existing.isDeleted) {
                if (existing.title.isBlank() && existing.content.isBlank()) {
                    purgeLocked(id)
                } else {
                    noteDao.moveToTrash(id)
                    _events.trySend(UiEvent.NotesTrashed(listOf(id)))
                }
            }
            return
        }

        if (existing != null &&
            existing.title == cleanTitle &&
            existing.content == cleanContent &&
            existing.folderId == finalFolderId &&
            existing.color == finalColor &&
            existing.isPinned == finalPinned
        ) {
            if (base != null) editorBases[id] = editorMeta(base.title, base.content)
            return
        }

        val entity = (existing ?: NoteEntity(id = id, title = "", content = "")).copy(
            folderId = finalFolderId,
            title = cleanTitle,
            content = cleanContent,
            isPinned = finalPinned,
            color = finalColor,
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (existing?.syncStatus == SyncStatus.PENDING_DELETE) SyncStatus.PENDING_DELETE else SyncStatus.PENDING_UPLOAD
        )
        noteDao.upsertNote(entity)
        editorBases[id] = editorMeta(cleanTitle, cleanContent)
    }

    /** Borrado definitivo (con lápida en el servidor si hay cuenta). Requiere el cerrojo de escritura. */
    private suspend fun purgeLocked(id: String) {
        if (_isConfigured.value) noteDao.markForPurge(id) else noteDao.deletePermanently(id)
    }

    /** Crea una nota nueva (p. ej. con texto compartido desde otra app) y devuelve su id. */
    suspend fun createNote(title: String, content: String, folderId: String? = null): String {
        val note = NoteEntity(
            title = title.trim(),
            content = content.trimEnd(),
            folderId = folderId,
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        noteWriteMutex.withLock { noteDao.upsertNote(note) }
        triggerSync()
        return note.id
    }

    /** Crea una nota a partir de texto o imágenes (p. ej. arrastrados a la ventana) y la abre. */
    fun createNoteFromShare(subject: String?, sharedText: String?, imageUris: List<File>) {
        viewModelScope.launch {
            val images = imageUris.mapNotNull { uri ->
                try {
                    "![${text(R.string.image_alt)}](${addImageAttachment(uri)})"
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            // Algunas apps comparten texto con saltos de línea de Windows ("\r\n"): el editor y las
            // listas de tareas trabajan con "\n".
            val cleanText = sharedText?.replace("\r\n", "\n")?.replace('\r', '\n')?.trim()
            val body = listOfNotNull(cleanText?.takeIf { it.isNotEmpty() }, images.joinToString("\n").ifEmpty { null })
                .joinToString("\n\n")
            if (body.isBlank() && subject.isNullOrBlank()) {
                if (imageUris.isNotEmpty()) _events.trySend(UiEvent.Message(text(R.string.image_add_failed)))
                return@launch
            }
            val id = createNote(subject.orEmpty(), body)
            requestNavigation(PendingNavigation.OpenNote(id))
        }
    }

    /** Duplica una nota y devuelve el id de la copia. */
    suspend fun duplicateNote(noteId: String): String? {
        // Dentro del cerrojo: así se copia también el último autoguardado pendiente.
        val copy = noteWriteMutex.withLock {
            val original = noteDao.getNoteById(noteId) ?: return null
            original.copy(
                id = UUID.randomUUID().toString(),
                title = (original.title.ifBlank { text(R.string.untitled) } + " " + text(R.string.copy_suffix)).trim(),
                isPinned = false,
                isDeleted = false,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_UPLOAD,
                remoteEtag = null
            ).also { noteDao.upsertNote(it) }
        }
        triggerSync()
        return copy.id
    }

    /** Fija o desfija una nota. */
    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            noteWriteMutex.withLock {
                noteDao.setPinned(note.id, !note.isPinned)
            }
            triggerSync()
        }
    }

    /** Mueve la nota a la papelera. */
    fun deleteNote(noteId: String) = moveNotesToTrash(listOf(noteId))

    /** Mueve varias notas a la papelera y ofrece deshacer. */
    fun moveNotesToTrash(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            noteWriteMutex.withLock {
                database.withTransaction { ids.forEach { noteDao.moveToTrash(it) } }
            }
            _events.trySend(UiEvent.NotesTrashed(ids.toList()))
            triggerSync()
        }
    }

    /** Saca una nota de la papelera. */
    fun restoreNote(noteId: String) = restoreNotes(listOf(noteId))

    /** Saca varias notas de la papelera (también es el "Deshacer" de [moveNotesToTrash]). */
    fun restoreNotes(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            noteWriteMutex.withLock {
                database.withTransaction { ids.forEach { noteDao.restoreFromTrash(it) } }
            }
            triggerSync()
        }
    }

    /** Vacía la papelera. Con cuenta, las notas se borran del servidor en la siguiente sincronización. */
    fun emptyTrash() {
        viewModelScope.launch {
            noteWriteMutex.withLock {
                if (_isConfigured.value) noteDao.markTrashForPurge() else noteDao.deleteTrashPermanently()
            }
            triggerSync()
        }
    }

    /** Borra una nota de forma definitiva. */
    fun deleteNotePermanently(noteId: String) = deleteNotesPermanently(listOf(noteId))

    /** Borra varias notas de forma definitiva. */
    fun deleteNotesPermanently(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            noteWriteMutex.withLock {
                database.withTransaction { ids.forEach { purgeLocked(it) } }
            }
            triggerSync()
        }
    }

    /** Fija o desfija varias notas. */
    fun setNotesPinned(ids: Collection<String>, pinned: Boolean) = bulkUpdate { noteDao.setPinnedMany(ids.toList(), pinned) }

    /** Cambia el color de varias notas. */
    fun setNotesColor(ids: Collection<String>, color: NoteColor) = bulkUpdate { noteDao.setColor(ids.toList(), color.key) }

    /** Mueve varias notas a una carpeta (`null` = sin carpeta). */
    fun moveNotes(ids: Collection<String>, folderId: String?) = bulkUpdate { noteDao.moveToFolder(ids.toList(), folderId) }

    /** Ejecuta un cambio en bloque con el cerrojo de escritura y luego sincroniza. */
    private fun bulkUpdate(block: suspend () -> Unit) {
        viewModelScope.launch {
            noteWriteMutex.withLock { block() }
            triggerSync()
        }
    }

    /**
     * Comprime la imagen, la guarda como adjunto y devuelve la referencia Markdown
     * (`attachment://<id>`) para insertarla en la nota.
     */
    suspend fun addImageAttachment(file: File): String {
        val bytes = ImageCompressor.compressImage(file)
        val id = UUID.randomUUID().toString()
        // La escritura del fichero no debe bloquear el hilo principal (se llama desde la UI).
        withContext(Dispatchers.IO) { attachmentStore.write(id, bytes) }
        attachmentDao.upsert(
            AttachmentEntity(id = id, mimeType = ImageCompressor.MIME_TYPE, size = bytes.size.toLong())
        )
        return AttachmentStore.reference(id)
    }

    // ==========================================
    // Copia de seguridad
    // ==========================================

    /** Exporta la copia de seguridad ZIP a [file] y avisa del resultado. */
    fun exportBackup(file: File) = runBusy {
        val count = backupManager.exportZip(file, text(R.string.untitled))
        _events.trySend(UiEvent.Message(quantity(R.plurals.export_done, count)))
    }

    /** Importa ZIP o ficheros Markdown y avisa de cuántas notas se han importado. */
    fun importBackup(uris: List<File>) {
        if (uris.isEmpty()) return
        runBusy {
            val result = backupManager.import(uris)
            val message = if (result.notes == 0) text(R.string.import_nothing) else quantity(R.plurals.import_done, result.notes)
            _events.trySend(UiEvent.Message(message))
            if (result.notes > 0) triggerSync()
        }
    }

    /**
     * Ejecuta una tarea larga marcando [busy]; si ya hay una en curso no hace nada.
     * Los errores se muestran como mensaje.
     */
    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend(UiEvent.Message(text(R.string.backup_failed, e.localizedMessage ?: e.javaClass.simpleName)))
            } finally {
                _busy.value = false
            }
        }
    }
}

/** Comparador de notas para cada criterio de orden. */
fun SortOrder.comparator(): Comparator<NoteEntity> = when (this) {
    SortOrder.MODIFIED_DESC -> compareByDescending { it.updatedAt }
    SortOrder.MODIFIED_ASC -> compareBy { it.updatedAt }
    SortOrder.TITLE_ASC -> compareBy<NoteEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.content } }
        .thenByDescending { it.updatedAt }
    SortOrder.TITLE_DESC -> compareByDescending<NoteEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.content } }
        .thenByDescending { it.updatedAt }
}
