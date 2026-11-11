// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.screen

import com.ferlagod.misnotas.platform.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ferlagod.misnotas.i18n.pluralStringResource
import com.ferlagod.misnotas.i18n.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ferlagod.misnotas.platform.onSecondaryClick
import com.ferlagod.misnotas.util.DateFormats
import com.ferlagod.misnotas.R
import com.ferlagod.misnotas.data.attachment.AttachmentStore
import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.pref.AppSettings
import com.ferlagod.misnotas.data.local.pref.SortOrder
import com.ferlagod.misnotas.ui.components.ColorPalette
import com.ferlagod.misnotas.ui.components.EmptyState
import com.ferlagod.misnotas.ui.components.FolderPickerDialog
import com.ferlagod.misnotas.ui.markdown.markdownPreview
import com.ferlagod.misnotas.ui.markdown.taskProgress
import com.ferlagod.misnotas.ui.theme.AccentGreen
import com.ferlagod.misnotas.ui.theme.AccentOrange
import com.ferlagod.misnotas.ui.theme.AccentRed
import com.ferlagod.misnotas.ui.theme.NoteColor
import com.ferlagod.misnotas.ui.theme.getAccentColor
import com.ferlagod.misnotas.ui.theme.isAppInDarkTheme
import com.ferlagod.misnotas.ui.theme.toCardBackground
import com.ferlagod.misnotas.ui.theme.toCardBorder
import com.ferlagod.misnotas.ui.viewmodel.NotesFilter
import com.ferlagod.misnotas.util.TextSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Acciones de la pantalla de notas, agrupadas para no tener veinte parámetros sueltos. */
class NotesActions(
    val onSearchQueryChange: (String) -> Unit,
    val onToggleLayout: () -> Unit,
    val onSortOrderChange: (SortOrder) -> Unit,
    val onBack: () -> Unit,
    val onOpenNote: (noteId: String?) -> Unit,
    val onTogglePin: (NoteEntity) -> Unit,
    val onTrash: (Collection<String>) -> Unit,
    val onRestore: (Collection<String>) -> Unit,
    val onDeletePermanently: (Collection<String>) -> Unit,
    val onEmptyTrash: () -> Unit,
    val onSetPinned: (Collection<String>, Boolean) -> Unit,
    val onSetColor: (Collection<String>, NoteColor) -> Unit,
    val onMove: (Collection<String>, String?) -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    notes: List<NoteEntity>,
    folders: List<FolderEntity>,
    foldersMap: Map<String, FolderEntity>,
    currentFilter: NotesFilter,
    searchQuery: String,
    isGridLayout: Boolean,
    sortOrder: SortOrder,
    autoPurgeTrash: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: NotesActions,
    /** Nota abierta en el panel lateral (pantallas anchas), para resaltarla en la lista. */
    activeNoteId: String? = null
) {
    val isDark = isAppInDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val isTrashView = currentFilter is NotesFilter.Trash
    val haptics = LocalHapticFeedback.current

    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val selectionMode = selectedIds.isNotEmpty()
    // Si una nota seleccionada desaparece (sincronización, papelera…), sale de la selección.
    LaunchedEffect(notes) {
        val visible = notes.mapTo(HashSet()) { it.id }
        if (!visible.containsAll(selectedIds)) selectedIds = selectedIds intersect visible
    }

    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    val filterTitle = when (currentFilter) {
        is NotesFilter.All -> stringResource(R.string.all_notes)
        is NotesFilter.Pinned -> stringResource(R.string.pinned)
        is NotesFilter.Trash -> stringResource(R.string.trash)
        // El nombre se toma de la carpeta actual por si se ha renombrado o sincronizado.
        is NotesFilter.Folder -> foldersMap[currentFilter.folderId]?.name ?: currentFilter.folderName
    }

    val searchTerms = remember(searchQuery) { TextSearch.terms(searchQuery) }
    val pinnedNotes = if (isTrashView) emptyList() else notes.filter { it.isPinned }
    val otherNotes = if (isTrashView) notes else notes.filter { !it.isPinned }
    val showSections = pinnedNotes.isNotEmpty() && otherNotes.isNotEmpty()

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
            ) {
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "topbar"
                ) { selecting ->
                    if (selecting) {
                        SelectionBar(
                            count = selectedIds.size,
                            isTrashView = isTrashView,
                            allPinned = notes.filter { it.id in selectedIds }.all { it.isPinned },
                            onClose = { selectedIds = emptySet() },
                            onSelectAll = { selectedIds = notes.mapTo(HashSet()) { it.id } },
                            onPin = { pinned ->
                                actions.onSetPinned(selectedIds, pinned)
                                selectedIds = emptySet()
                            },
                            onColor = { showColorDialog = true },
                            onMove = { showMoveDialog = true },
                            onTrash = {
                                actions.onTrash(selectedIds)
                                selectedIds = emptySet()
                            },
                            onRestore = {
                                actions.onRestore(selectedIds)
                                selectedIds = emptySet()
                            },
                            onDeleteForever = { showDeleteSelectedConfirm = true }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = actions.onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back_to_folders),
                                    tint = primary
                                )
                            }
                            Text(
                                text = filterTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isTrashView) {
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                                            contentDescription = stringResource(R.string.sort_by),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SortMenu(
                                        expanded = showSortMenu,
                                        current = sortOrder,
                                        onDismiss = { showSortMenu = false },
                                        onSelect = {
                                            actions.onSortOrderChange(it)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                            IconButton(onClick = actions.onToggleLayout) {
                                Icon(
                                    imageVector = if (isGridLayout) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                                    contentDescription = stringResource(R.string.cd_toggle_layout),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                SearchField(
                    query = searchQuery,
                    hint = if (isTrashView) stringResource(R.string.search_trash_hint) else stringResource(R.string.search_notes_hint),
                    onQueryChange = actions.onSearchQueryChange
                )

                if (isTrashView) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TrashBanner(
                        count = notes.size,
                        autoPurge = autoPurgeTrash,
                        onEmpty = { showEmptyTrashConfirm = true }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isTrashView && !selectionMode) {
                FloatingActionButton(
                    onClick = { actions.onOpenNote(null) },
                    containerColor = primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_new_note),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                when {
                    searchQuery.isNotBlank() -> EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.no_results, searchQuery.trim())
                    )
                    isTrashView -> EmptyState(
                        icon = Icons.Rounded.DeleteOutline,
                        title = stringResource(R.string.trash_empty)
                    )
                    currentFilter is NotesFilter.Pinned -> EmptyState(
                        icon = Icons.Rounded.PushPin,
                        title = stringResource(R.string.no_pinned_title),
                        message = stringResource(R.string.no_pinned_message)
                    )
                    else -> EmptyState(
                        icon = Icons.Rounded.StickyNote2,
                        title = stringResource(R.string.no_notes),
                        message = stringResource(R.string.no_notes_message)
                    )
                }
            }
        } else {
            val gridState = rememberLazyStaggeredGridState()
            LazyVerticalStaggeredGrid(
                columns = if (isGridLayout) StaggeredGridCells.Adaptive(160.dp) else StaggeredGridCells.Fixed(1),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp
            ) {
                if (pinnedNotes.isNotEmpty()) {
                    if (showSections) {
                        item(key = "header_pinned", span = StaggeredGridItemSpan.FullLine) {
                            SectionHeader(title = stringResource(R.string.section_pinned))
                        }
                    }
                    items(pinnedNotes, key = { it.id }) { note ->
                        NoteCardItem(note, foldersMap, isDark, isTrashView, selectedIds, searchTerms, actions, ::toggleSelection, note.id == activeNoteId) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggleSelection(note.id)
                        }
                    }
                }
                if (otherNotes.isNotEmpty()) {
                    if (showSections) {
                        item(key = "header_others", span = StaggeredGridItemSpan.FullLine) {
                            SectionHeader(title = stringResource(R.string.section_others))
                        }
                    }
                    items(otherNotes, key = { it.id }) { note ->
                        NoteCardItem(note, foldersMap, isDark, isTrashView, selectedIds, searchTerms, actions, ::toggleSelection, note.id == activeNoteId) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggleSelection(note.id)
                        }
                    }
                }
            }
        }
    }

    if (showEmptyTrashConfirm) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.empty_trash_title),
            message = stringResource(R.string.empty_trash_message),
            confirmLabel = stringResource(R.string.empty_trash),
            onDismiss = { showEmptyTrashConfirm = false },
            onConfirm = {
                actions.onEmptyTrash()
                showEmptyTrashConfirm = false
            }
        )
    }

    if (showDeleteSelectedConfirm) {
        ConfirmDestructiveDialog(
            title = pluralStringResource(R.plurals.delete_forever_title, selectedIds.size, selectedIds.size),
            message = stringResource(R.string.delete_forever_message),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { showDeleteSelectedConfirm = false },
            onConfirm = {
                actions.onDeletePermanently(selectedIds)
                selectedIds = emptySet()
                showDeleteSelectedConfirm = false
            }
        )
    }

    if (showMoveDialog) {
        val commonFolder = notes.filter { it.id in selectedIds }.map { it.folderId }.distinct().singleOrNull()
        FolderPickerDialog(
            title = stringResource(R.string.move_to_folder),
            folders = folders,
            selectedId = commonFolder,
            onDismiss = { showMoveDialog = false },
            onSelect = { folderId ->
                actions.onMove(selectedIds, folderId)
                selectedIds = emptySet()
                showMoveDialog = false
            }
        )
    }

    if (showColorDialog) {
        val commonColor = notes.filter { it.id in selectedIds }.map { NoteColor.fromKey(it.color) }.distinct().singleOrNull()
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text(stringResource(R.string.note_color), fontWeight = FontWeight.Bold) },
            text = {
                ColorPalette(
                    selected = commonColor,
                    onSelect = { color ->
                        actions.onSetColor(selectedIds, color)
                        selectedIds = emptySet()
                        showColorDialog = false
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyStaggeredGridItemScope.NoteCardItem(
    note: NoteEntity,
    foldersMap: Map<String, FolderEntity>,
    isDark: Boolean,
    isTrashView: Boolean,
    selectedIds: Set<String>,
    searchTerms: List<String>,
    actions: NotesActions,
    onToggleSelection: (String) -> Unit,
    isActive: Boolean,
    onLongPress: () -> Unit
) {
    val selectionMode = selectedIds.isNotEmpty()
    NoteCard(
        note = note,
        folder = note.folderId?.let { foldersMap[it] },
        isDark = isDark,
        isTrashView = isTrashView,
        isSelected = note.id in selectedIds,
        isActive = isActive,
        searchTerms = searchTerms,
        onClick = {
            when {
                selectionMode -> onToggleSelection(note.id)
                isTrashView -> onToggleSelection(note.id)
                else -> actions.onOpenNote(note.id)
            }
        },
        onLongClick = onLongPress,
        onTogglePin = { actions.onTogglePin(note) },
        onDelete = { actions.onTrash(listOf(note.id)) },
        onRestore = { actions.onRestore(listOf(note.id)) },
        onDeletePermanently = { actions.onDeletePermanently(listOf(note.id)) },
        modifier = Modifier.animateItem()
    )
}

@Composable
private fun SelectionBar(
    count: Int,
    isTrashView: Boolean,
    allPinned: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPin: (Boolean) -> Unit,
    onColor: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_selection))
        }
        Text(
            text = pluralStringResource(R.plurals.selected_count, count, count),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onSelectAll) {
            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.select_all))
        }
        if (isTrashView) {
            IconButton(onClick = onRestore) {
                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = stringResource(R.string.restore), tint = AccentGreen)
            }
            IconButton(onClick = onDeleteForever) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = stringResource(R.string.delete_forever), tint = AccentRed)
            }
        } else {
            IconButton(onClick = { onPin(!allPinned) }) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = stringResource(if (allPinned) R.string.unpin else R.string.pin),
                    tint = if (allPinned) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onColor) {
                Icon(Icons.Rounded.Palette, contentDescription = stringResource(R.string.note_color))
            }
            IconButton(onClick = onMove) {
                Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = stringResource(R.string.move_to_folder))
            }
            IconButton(onClick = onTrash) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.move_to_trash), tint = AccentRed)
            }
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: SortOrder,
    onDismiss: () -> Unit,
    onSelect: (SortOrder) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sort_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        SortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(stringResource(order.labelRes())) },
                leadingIcon = { RadioButton(selected = order == current, onClick = null) },
                onClick = { onSelect(order) }
            )
        }
    }
}

fun SortOrder.labelRes(): String = when (this) {
    SortOrder.MODIFIED_DESC -> R.string.sort_modified_desc
    SortOrder.MODIFIED_ASC -> R.string.sort_modified_asc
    SortOrder.TITLE_ASC -> R.string.sort_title_asc
    SortOrder.TITLE_DESC -> R.string.sort_title_desc
}

@Composable
private fun SearchField(query: String, hint: String, onQueryChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashBanner(count: Int, autoPurge: Boolean, onEmpty: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AccentRed.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.trash_count, count, count),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (autoPurge) stringResource(R.string.trash_auto_purge_info, AppSettings.TRASH_RETENTION_DAYS)
                    else stringResource(R.string.trash_hint_select),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (count > 0) {
                TextButton(
                    onClick = onEmpty,
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.empty_action), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp)
    )
}

/** Resalta en [text] las palabras buscadas. */
@Composable
private fun highlighted(text: String, terms: List<String>): AnnotatedString {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    return remember(text, terms, highlight) {
        val ranges = TextSearch.matchRanges(text, terms)
        buildAnnotatedString {
            append(text)
            ranges.forEach { addStyle(SpanStyle(background = highlight, fontWeight = FontWeight.SemiBold), it.first, it.last + 1) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    folder: FolderEntity?,
    isDark: Boolean,
    isTrashView: Boolean,
    isSelected: Boolean,
    searchTerms: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val noteColor = NoteColor.fromKey(note.color)
    val cardBackground = noteColor.toCardBackground(isDark)
    val primary = MaterialTheme.colorScheme.primary
    val cardBorder by animateColorAsState(
        targetValue = if (isSelected || isActive) primary else noteColor.toCardBorder(isDark),
        label = "card_border"
    )

    var showCardMenu by remember { mutableStateOf(false) }

    val formattedDate = remember(note.updatedAt) { formatNoteDate(note.updatedAt) }
    val (tasksDone, tasksTotal) = remember(note.content) { taskProgress(note.content) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = BorderStroke(if (isSelected || isActive) 2.dp else 1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .onSecondaryClick(onLongClick)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = highlighted(note.title.ifBlank { "" }, searchTerms),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                when {
                    isSelected -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(22.dp)
                    )
                    !isTrashView -> Box {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (note.isPinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = stringResource(R.string.pinned),
                                    tint = AccentOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { showCardMenu = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showCardMenu,
                            onDismissRequest = { showCardMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (note.isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    onTogglePin()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select)) },
                                leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    onLongClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_note), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Miniatura de la primera imagen
            val firstImage = remember(note.content) {
                AttachmentStore.firstReferencedId(note.content)?.let { AttachmentStore.fileFor(it) }
                    ?: extractFirstDataUri(note.content)
            }
            if (firstImage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = firstImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            val preview = remember(note.content) { markdownPreview(note.content).trim() }
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = highlighted(preview, searchTerms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            } else if (note.title.isBlank() && firstImage == null) {
                Text(
                    text = stringResource(R.string.empty_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            if (tasksTotal > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.TaskAlt,
                        contentDescription = null,
                        tint = if (tasksDone == tasksTotal) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    LinearProgressIndicator(
                        progress = { tasksDone.toFloat() / tasksTotal },
                        color = AccentGreen,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        drawStopIndicator = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$tasksDone/$tasksTotal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (folder != null) {
                    val folderTint = NoteColor.fromKey(folder.color).let { c ->
                        if (c == NoteColor.DEFAULT) primary else c.getAccentColor(isDark)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = folderTint.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = folderTint, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = folderTint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }

            if (isTrashView && !isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onRestore,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.restore), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onDeletePermanently,
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentRed),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** "Hace 5 min", "Ayer"… para fechas recientes; fecha corta para el resto. */
private fun formatNoteDate(timestamp: Long): String = DateFormats.relative(timestamp)

private fun extractFirstDataUri(content: String): String? =
    Regex("data:image/[^;]+;base64,[A-Za-z0-9+/=]+").find(content)?.value
