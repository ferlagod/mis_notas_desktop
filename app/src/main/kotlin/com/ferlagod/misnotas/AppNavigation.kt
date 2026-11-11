// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.i18n.Strings
import com.ferlagod.misnotas.i18n.stringResource
import com.ferlagod.misnotas.platform.BackHandler
import com.ferlagod.misnotas.ui.components.EmptyState
import com.ferlagod.misnotas.ui.screen.FoldersScreen
import com.ferlagod.misnotas.ui.screen.LoginScreen
import com.ferlagod.misnotas.ui.screen.NoteEditorScreen
import com.ferlagod.misnotas.ui.screen.NotesActions
import com.ferlagod.misnotas.ui.screen.NotesScreen
import com.ferlagod.misnotas.ui.screen.SettingsScreen
import com.ferlagod.misnotas.ui.viewmodel.MainViewModel
import com.ferlagod.misnotas.ui.viewmodel.NotesFilter
import com.ferlagod.misnotas.ui.viewmodel.PendingNavigation
import com.ferlagod.misnotas.ui.viewmodel.UiEvent
import com.ferlagod.misnotas.util.AppLanguage
import kotlinx.coroutines.launch
import java.util.UUID

/** Rutas de las pantallas. */
object AppDestinations {
    /** Conexión con Nextcloud o desbloqueo de la bóveda. */
    const val LOGIN = "login"
    /** Pantalla principal: carpetas y, en ventanas anchas, la lista de notas al lado. */
    const val FOLDERS = "folders"
    /** Lista de notas del filtro actual. */
    const val NOTES = "notes"
    /** Ajustes. */
    const val SETTINGS = "settings"
    /** Editor; todos los argumentos son opcionales (ver [editorRoute]). */
    const val EDITOR = "editor?noteId={noteId}&folderId={folderId}&pin={pin}"

    /** Ruta del editor. Sin [noteId] crea una nota nueva, en [folderId] y fijada si [pin] es `true`. */
    fun editorRoute(noteId: String? = null, folderId: String? = null, pin: Boolean = false): String =
        "editor?noteId=${noteId.orEmpty()}&folderId=${folderId.orEmpty()}&pin=$pin"
}

/**
 * Raíz de la interfaz: conecta el ViewModel con las pantallas, muestra los avisos (snackbars)
 * y decide a qué pantalla ir según el estado de la cuenta y de la bóveda.
 *
 * @param appLocked la pantalla de bloqueo de la app está encima; no se navega mientras tanto.
 * @param onAppLockChange activa (con contraseña) o desactiva el bloqueo de la app.
 */
@Composable
internal fun AppNavigationHost(
    viewModel: MainViewModel,
    appLocked: Boolean,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onAppLockChange: (enabled: Boolean, password: String?) -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val folders by viewModel.allFolders.collectAsStateWithLifecycle()
    val notes by viewModel.displayedNotes.collectAsStateWithLifecycle()
    val allActiveNotes by viewModel.allActiveNotes.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalActiveNotesCount.collectAsStateWithLifecycle()
    val pinnedCount by viewModel.pinnedNotesCount.collectAsStateWithLifecycle()
    val trashNotesCount by viewModel.trashNotesCount.collectAsStateWithLifecycle()
    val foldersMap by viewModel.foldersMap.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pendingNavigation by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    // Avisos del ViewModel: "Movida a la papelera · Deshacer", resultados de exportar…
    // Cada aviso sustituye al anterior en lugar de esperar a que termine.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                when (event) {
                    is UiEvent.NotesTrashed -> {
                        val result = snackbarHostState.showSnackbar(
                            message = Strings.quantity(R.plurals.notes_moved_to_trash, event.ids.size),
                            actionLabel = Strings.get(R.string.undo),
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreNotes(event.ids)
                    }
                    is UiEvent.Message -> snackbarHostState.showSnackbar(event.text, withDismissAction = true)
                }
            }
        }
    }

    // Si la bóveda se bloquea, se vuelve a la pantalla de contraseña.
    LaunchedEffect(isUnlocked, isConfigured) {
        if (isConfigured && !isUnlocked) {
            navController.navigate(AppDestinations.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Menú de la ventana y atajos de teclado (solo cuando la app está desbloqueada).
    LaunchedEffect(pendingNavigation, isUnlocked, appLocked) {
        val target = pendingNavigation ?: return@LaunchedEffect
        if (appLocked || (isConfigured && !isUnlocked)) return@LaunchedEffect
        viewModel.consumeNavigation()
        when (target) {
            PendingNavigation.NewNote -> navController.navigate(AppDestinations.editorRoute())
            PendingNavigation.Settings ->
                if (navController.currentDestination?.route != AppDestinations.SETTINGS) navController.navigate(AppDestinations.SETTINGS)
            is PendingNavigation.OpenNote -> navController.navigate(AppDestinations.editorRoute(noteId = target.noteId))
        }
    }

    /** Va a la pantalla principal y quita la de conexión de la pila. */
    fun NavHostController.goHome() = navigate(AppDestinations.FOLDERS) {
        popUpTo(AppDestinations.LOGIN) { inclusive = true }
    }

    // Esc sin otro manejador activo: vuelve a la pantalla anterior.
    val backStackEntry by navController.currentBackStackEntryAsState()
    BackHandler(enabled = backStackEntry != null && navController.previousBackStackEntry != null) {
        navController.popBackStack()
    }

    NavHost(
        navController = navController,
        startDestination = if (isConfigured && !isUnlocked) AppDestinations.LOGIN else AppDestinations.FOLDERS
    ) {
        composable(AppDestinations.LOGIN) {
            LoginScreen(
                isConfigured = isConfigured,
                onSetupAndUnlock = { serverUrl, user, appPass, masterPass, onError ->
                    viewModel.setupAndUnlock(serverUrl, user, appPass, masterPass, onSuccess = { navController.goHome() }, onError = onError)
                },
                onUnlock = { masterPass, onError ->
                    viewModel.unlockWithPassword(masterPass, onSuccess = { navController.goHome() }, onError = onError)
                },
                onResetConfig = {
                    viewModel.logout()
                    navController.goHome()
                },
                onSkip = { navController.goHome() }
            )
        }

        composable(AppDestinations.FOLDERS) {
            FoldersScreen(
                isConfigured = isConfigured,
                folders = folders,
                notes = allActiveNotes,
                totalNotesCount = totalCount,
                pinnedNotesCount = pinnedCount,
                trashNotesCount = trashNotesCount,
                syncState = syncState,
                snackbarHostState = snackbarHostState,
                onSelectFilter = { filter ->
                    viewModel.setFilter(filter)
                    navController.navigate(AppDestinations.NOTES)
                },
                onSearch = { query ->
                    viewModel.setFilter(NotesFilter.All)
                    viewModel.setSearchQuery(query)
                    navController.navigate(AppDestinations.NOTES)
                },
                onNewNote = { navController.navigate(AppDestinations.editorRoute()) },
                onCreateFolder = viewModel::createFolder,
                onUpdateFolder = viewModel::updateFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onTriggerSync = viewModel::triggerSync,
                onLockApp = viewModel::lock,
                onOpenSettings = { navController.navigate(AppDestinations.SETTINGS) }
            )
        }

        composable(AppDestinations.SETTINGS) {
            SettingsScreen(
                isConfigured = isConfigured,
                currentCredentials = remember(isConfigured) { viewModel.getCredentials() },
                syncState = syncState,
                lastSync = remember(isConfigured, syncState) { viewModel.getLastSync() },
                settings = settings,
                busy = busy,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onSaveNextcloud = viewModel::setupAndUnlock,
                onDisconnectNextcloud = viewModel::disconnectNextcloud,
                onTriggerSync = viewModel::triggerSync,
                onThemeChange = viewModel::setThemeMode,
                onAmoledChange = viewModel::setAmoled,
                onGridLayoutChange = { if (it != settings.gridLayout) viewModel.toggleLayoutMode() },
                onSortOrderChange = viewModel::setSortOrder,
                onAutoPurgeChange = viewModel::setAutoPurgeTrash,
                onAppLockChange = onAppLockChange,
                onExport = viewModel::exportBackup,
                onImport = viewModel::importBackup,
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected
            )
        }

        composable(AppDestinations.NOTES) {
            val filter = currentFilter
            val newNoteFolder = (filter as? NotesFilter.Folder)?.folderId
            val newNotePinned = filter is NotesFilter.Pinned

            // Nota abierta en el panel derecho (ventanas anchas). Una nota nueva recibe su id al
            // abrirse, así sigue siendo la misma nota si se estrecha la ventana mientras se escribe.
            var paneNoteId by rememberSaveable { mutableStateOf<String?>(null) }
            var paneIsNew by rememberSaveable { mutableStateOf(false) }

            fun openInPane(noteId: String?) {
                paneIsNew = noteId == null
                paneNoteId = noteId ?: UUID.randomUUID().toString()
            }

            fun closePane() {
                paneNoteId = null
                paneIsNew = false
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH

                // Al estrechar la ventana con una nota abierta, la nota sigue abierta a pantalla completa.
                LaunchedEffect(twoPane) {
                    val noteId = paneNoteId
                    if (!twoPane && noteId != null) {
                        val isNew = paneIsNew
                        closePane()
                        // Si la nota nueva aún no se había guardado, conserva carpeta y fijado.
                        navController.navigate(
                            AppDestinations.editorRoute(
                                noteId = noteId,
                                folderId = newNoteFolder.takeIf { isNew },
                                pin = isNew && newNotePinned
                            )
                        )
                    }
                }

                val notesList = @Composable { modifier: Modifier ->
                    Box(modifier = modifier) {
                        NotesScreen(
                            notes = notes,
                            folders = folders,
                            foldersMap = foldersMap,
                            currentFilter = filter,
                            searchQuery = searchQuery,
                            isGridLayout = settings.gridLayout,
                            sortOrder = settings.sortOrder,
                            autoPurgeTrash = settings.autoPurgeTrash,
                            snackbarHostState = snackbarHostState,
                            activeNoteId = if (twoPane) paneNoteId else null,
                            actions = NotesActions(
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onToggleLayout = viewModel::toggleLayoutMode,
                                onSortOrderChange = viewModel::setSortOrder,
                                onBack = { navController.popBackStack() },
                                onOpenNote = { noteId ->
                                    if (twoPane) {
                                        openInPane(noteId)
                                    } else {
                                        navController.navigate(
                                            AppDestinations.editorRoute(
                                                noteId = noteId,
                                                folderId = newNoteFolder,
                                                pin = noteId == null && newNotePinned
                                            )
                                        )
                                    }
                                },
                                onTogglePin = viewModel::togglePin,
                                onTrash = viewModel::moveNotesToTrash,
                                onRestore = viewModel::restoreNotes,
                                onDeletePermanently = viewModel::deleteNotesPermanently,
                                onEmptyTrash = viewModel::emptyTrash,
                                onSetPinned = viewModel::setNotesPinned,
                                onSetColor = viewModel::setNotesColor,
                                onMove = viewModel::moveNotes
                            )
                        )
                    }
                }

                if (!twoPane) {
                    notesList(Modifier.fillMaxSize())
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        notesList(
                            Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Box(
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                        ) {
                            val currentPaneNote = paneNoteId
                            if (currentPaneNote == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyState(
                                        icon = Icons.Rounded.EditNote,
                                        title = stringResource(R.string.pane_no_note_title),
                                        message = stringResource(R.string.pane_no_note_message)
                                    )
                                }
                            } else {
                                // Snackbars propios: los avisos generales ya los muestra la lista.
                                val paneSnackbar = remember { SnackbarHostState() }
                                key(currentPaneNote) {
                                    EditorHost(
                                        viewModel = viewModel,
                                        noteId = currentPaneNote,
                                        isNewNote = paneIsNew,
                                        folderId = newNoteFolder.takeIf { paneIsNew },
                                        initialPinned = paneIsNew && newNotePinned,
                                        folders = folders,
                                        snackbarHostState = paneSnackbar,
                                        onClose = ::closePane,
                                        onOpenNote = { id -> openInPane(id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composable(
            route = AppDestinations.EDITOR,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType; defaultValue = "" },
                navArgument("folderId") { type = NavType.StringType; defaultValue = "" },
                navArgument("pin") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val noteIdArg = backStackEntry.savedStateHandle.get<String>("noteId")?.takeIf { it.isNotBlank() }
            EditorHost(
                viewModel = viewModel,
                noteId = noteIdArg,
                folderId = backStackEntry.savedStateHandle.get<String>("folderId")?.takeIf { it.isNotBlank() },
                initialPinned = backStackEntry.savedStateHandle.get<Boolean>("pin") ?: false,
                folders = folders,
                snackbarHostState = snackbarHostState,
                onClose = { navController.popBackStack() },
                onOpenNote = { id ->
                    navController.navigate(AppDestinations.editorRoute(noteId = id)) {
                        popUpTo(AppDestinations.EDITOR) { inclusive = true }
                    }
                }
            )
        }
    }
}

/** Ancho a partir del cual la lista de notas y el editor se muestran lado a lado (casi siempre en escritorio). */
private val TWO_PANE_MIN_WIDTH = 600.dp

/**
 * Editor con la carga de la nota desde la base de datos. Se usa a pantalla completa
 * y en el panel derecho de las pantallas anchas.
 */
@Composable
private fun EditorHost(
    viewModel: MainViewModel,
    noteId: String?,
    folderId: String?,
    initialPinned: Boolean,
    folders: List<FolderEntity>,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onOpenNote: (String) -> Unit,
    isNewNote: Boolean = noteId == null
) {
    // Se lee una sola vez de la base de datos (incluye notas en papelera).
    var existingNote by remember(noteId) { mutableStateOf<NoteEntity?>(null) }
    var noteLoaded by remember(noteId) { mutableStateOf(noteId == null) }
    LaunchedEffect(noteId) {
        if (noteId != null) {
            existingNote = viewModel.getNoteById(noteId)
            noteLoaded = true
        }
    }
    // Versión en vivo: si llega un cambio de otro dispositivo y no se ha tocado la nota, se muestra.
    val liveNoteFlow = remember(noteId) { noteId?.let(viewModel::observeNote) ?: kotlinx.coroutines.flow.flowOf(null) }
    val liveNote by liveNoteFlow.collectAsState(initial = null)

    NoteEditorScreen(
        noteId = noteId,
        isNewNote = isNewNote,
        folderId = folderId,
        initialPinned = initialPinned,
        existingNote = existingNote,
        liveNote = liveNote,
        noteLoaded = noteLoaded,
        onBaseLoaded = viewModel::registerEditorBase,
        folders = folders,
        snackbarHostState = snackbarHostState,
        onBack = onClose,
        onSaveNote = { id, title, content, folder, color, isPinned, sync, closing ->
            viewModel.saveNote(id, title, content, folder, color, isPinned, sync, closing)
        },
        onDeleteNote = viewModel::deleteNote,
        onDuplicateNote = viewModel::duplicateNote,
        onOpenNote = onOpenNote,
        onAddImage = viewModel::addImageAttachment
    )
}
