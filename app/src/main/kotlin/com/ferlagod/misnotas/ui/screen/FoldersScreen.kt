// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Note
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import com.ferlagod.misnotas.platform.Drawables
import com.ferlagod.misnotas.platform.painterResource
import com.ferlagod.misnotas.i18n.pluralStringResource
import com.ferlagod.misnotas.i18n.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferlagod.misnotas.R
import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.model.FolderTree
import com.ferlagod.misnotas.data.sync.SyncState
import com.ferlagod.misnotas.ui.components.ColorPalette
import com.ferlagod.misnotas.ui.components.FolderPickerDialog
import com.ferlagod.misnotas.ui.theme.AccentGreen
import com.ferlagod.misnotas.ui.theme.AccentOrange
import com.ferlagod.misnotas.ui.theme.AccentRed
import com.ferlagod.misnotas.ui.theme.NoteColor
import com.ferlagod.misnotas.ui.theme.getAccentColor
import com.ferlagod.misnotas.ui.theme.isAppInDarkTheme
import com.ferlagod.misnotas.ui.viewmodel.NotesFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    isConfigured: Boolean,
    folders: List<FolderEntity>,
    notes: List<NoteEntity>,
    totalNotesCount: Int,
    pinnedNotesCount: Int,
    trashNotesCount: Int,
    syncState: SyncState,
    snackbarHostState: SnackbarHostState,
    onSelectFilter: (NotesFilter) -> Unit,
    onSearch: (String) -> Unit,
    onNewNote: () -> Unit,
    onCreateFolder: (name: String, color: NoteColor, parentId: String?) -> Unit,
    onUpdateFolder: (id: String, name: String, color: NoteColor, parentId: String?) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onTriggerSync: () -> Unit,
    onLockApp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var parentForNewFolder by remember { mutableStateOf<String?>(null) }
    var folderToEdit by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val tree = remember(folders) { FolderTree.flatten(folders) }
    val countByFolder = remember(notes) { notes.groupingBy { it.folderId }.eachCount() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewNote,
                icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                text = { Text(stringResource(R.string.cd_new_note), fontWeight = FontWeight.SemiBold) },
                containerColor = primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                expanded = fabExpanded
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        // En pantallas anchas el contenido se centra con un ancho cómodo; el desplazamiento
        // sigue funcionando en toda la pantalla.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sidePadding = maxOf(16.dp, (maxWidth - READABLE_MAX_WIDTH) / 2)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 4.dp, bottom = 104.dp)
            ) {
                // Cabecera editorial común a la familia "Mis Apps" (como el inicio de Mis Podcast)
                item(key = "header") {
                    HomeHeader(
                        totalNotes = totalNotesCount,
                        folderCount = folders.size,
                        actions = {
                            SyncStatusIconButton(
                                isConfigured = isConfigured,
                                syncState = syncState,
                                onClick = { if (isConfigured) onTriggerSync() else onOpenSettings() }
                            )
                            if (isConfigured) {
                                IconButton(onClick = onLockApp) {
                                    Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.cd_lock), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                item(key = "search") {
                    GlobalSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {
                            focusManager.clearFocus()
                            onSearch(query.trim())
                            query = ""
                        }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                item(key = "quick") {
                    GroupLabel(stringResource(R.string.quick_views))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            QuickAccessRow(
                                title = stringResource(R.string.all_notes),
                                icon = Icons.AutoMirrored.Rounded.Note,
                                iconTint = primary,
                                count = totalNotesCount,
                                onClick = { onSelectFilter(NotesFilter.All) }
                            )
                            RowDivider(start = 62.dp)
                            QuickAccessRow(
                                title = stringResource(R.string.pinned),
                                icon = Icons.Rounded.PushPin,
                                iconTint = AccentOrange,
                                count = pinnedNotesCount,
                                onClick = { onSelectFilter(NotesFilter.Pinned) }
                            )
                            RowDivider(start = 62.dp)
                            QuickAccessRow(
                                title = stringResource(R.string.trash),
                                icon = Icons.Rounded.Delete,
                                iconTint = AccentRed,
                                count = trashNotesCount,
                                onClick = { onSelectFilter(NotesFilter.Trash) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item(key = "folders_header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GroupLabel(stringResource(R.string.my_folders), modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            parentForNewFolder = null
                            showCreateFolderDialog = true
                        }) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.new_folder), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (tree.isEmpty()) {
                    item(key = "no_folders") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            onClick = {
                                parentForNewFolder = null
                                showCreateFolderDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.no_folders),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(tree, key = { _, (folder, _) -> folder.id }) { index, (folder, level) ->
                        val shape = when {
                            tree.size == 1 -> RoundedCornerShape(14.dp)
                            index == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                            index == tree.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            Column {
                                FolderRow(
                                    name = folder.name,
                                    tint = NoteColor.fromKey(folder.color).let { c ->
                                        if (c == NoteColor.DEFAULT) primary else c.getAccentColor(isDark)
                                    },
                                    count = countByFolder[folder.id] ?: 0,
                                    indentationLevel = level,
                                    onClick = { onSelectFilter(NotesFilter.Folder(folder.id, folder.name)) },
                                    onAddSubfolder = {
                                        parentForNewFolder = folder.id
                                        showCreateFolderDialog = true
                                    },
                                    onEdit = { folderToEdit = folder },
                                    onDelete = { folderToDelete = folder }
                                )
                                if (index < tree.lastIndex) RowDivider(start = (58 + level * 18).dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        FolderDialog(
            title = if (parentForNewFolder != null) stringResource(R.string.new_subfolder) else stringResource(R.string.new_folder),
            initialName = "",
            initialColor = NoteColor.DEFAULT,
            initialParentId = parentForNewFolder,
            existingFolders = folders,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, color, parentId ->
                onCreateFolder(name, color, parentId)
                showCreateFolderDialog = false
            }
        )
    }

    folderToEdit?.let { folder ->
        FolderDialog(
            title = stringResource(R.string.edit_folder),
            initialName = folder.name,
            initialColor = NoteColor.fromKey(folder.color),
            initialParentId = folder.parentId,
            existingFolders = folders,
            currentFolderId = folder.id,
            onDismiss = { folderToEdit = null },
            onConfirm = { name, color, parentId ->
                onUpdateFolder(folder.id, name, color, parentId)
                folderToEdit = null
            }
        )
    }

    folderToDelete?.let { folder ->
        ConfirmDestructiveDialog(
            title = stringResource(R.string.delete_folder_title),
            message = stringResource(R.string.delete_folder_message, folder.name),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { folderToDelete = null },
            onConfirm = {
                onDeleteFolder(folder.id)
                folderToDelete = null
            }
        )
    }
}

/**
 * Cabecera de la pantalla principal: nombre de la app en pequeño, saludo según la hora,
 * resumen y logo redondo. Mismo esquema que el inicio de Mis Podcast.
 */
@Composable
private fun HomeHeader(
    totalNotes: Int,
    folderCount: Int,
    actions: @Composable () -> Unit
) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 6..12 -> R.string.greeting_morning
            in 13..20 -> R.string.greeting_afternoon
            else -> R.string.greeting_night
        }
    )
    val summary = pluralStringResource(R.plurals.home_notes_count, totalNotes, totalNotes) +
        "  ·  " + pluralStringResource(R.plurals.home_folders_count, folderCount, folderCount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, top = 4.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Image(
                painter = painterResource(Drawables.LOGO_ROUND),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 12.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun RowDivider(start: androidx.compose.ui.unit.Dp) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = start)
    )
}

@Composable
private fun GlobalSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_everywhere_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clear_search), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickAccessRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(text = count.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FolderRow(
    name: String,
    tint: Color,
    count: Int,
    indentationLevel: Int,
    onClick: () -> Unit,
    onAddSubfolder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (16 + indentationLevel * 18).dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_subfolder)) },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onAddSubfolder()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_folder)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_folder), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncStatusIconButton(
    isConfigured: Boolean,
    syncState: SyncState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotate")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "angle"
    )

    val data = if (!isConfigured) {
        SyncIconData(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), false, stringResource(R.string.sync_configure))
    } else {
        when (syncState) {
            is SyncState.Idle -> SyncIconData(Icons.Rounded.Sync, MaterialTheme.colorScheme.onSurfaceVariant, false, stringResource(R.string.sync_now))
            is SyncState.Syncing -> SyncIconData(Icons.Rounded.Sync, AccentOrange, true, stringResource(R.string.sync_in_progress))
            is SyncState.Success -> SyncIconData(Icons.Rounded.CheckCircle, AccentGreen, false, stringResource(R.string.sync_done))
            is SyncState.Error -> SyncIconData(Icons.Rounded.ErrorOutline, AccentRed, false, stringResource(R.string.sync_error))
        }
    }

    IconButton(onClick = onClick) {
        Icon(
            imageVector = data.icon,
            contentDescription = data.description,
            tint = data.tint,
            modifier = Modifier
                .size(22.dp)
                .rotate(if (data.isRotating) angle else 0f)
        )
    }
}

private data class SyncIconData(
    val icon: ImageVector,
    val tint: Color,
    val isRotating: Boolean,
    val description: String
)

@Composable
private fun FolderDialog(
    title: String,
    initialName: String,
    initialColor: NoteColor,
    initialParentId: String?,
    existingFolders: List<FolderEntity>,
    currentFolderId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: NoteColor, parentId: String?) -> Unit
) {
    var folderName by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedParentId by remember { mutableStateOf(initialParentId) }
    var showParentPicker by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    // Una carpeta no puede moverse dentro de sí misma ni de sus subcarpetas (crearía un ciclo).
    val excluded = remember(existingFolders, currentFolderId) {
        currentFolderId?.let { FolderTree.descendantIds(existingFolders, it) + it }.orEmpty()
    }
    val parentName = existingFolders.firstOrNull { it.id == selectedParentId }?.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.replace("\n", "") },
                    placeholder = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (folderName.isNotBlank()) onConfirm(folderName, selectedColor, selectedParentId)
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus)
                )
                // Al crear una carpeta el teclado aparece directamente en el nombre.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (initialName.isEmpty()) {
                        kotlinx.coroutines.delay(120)
                        runCatching { nameFocus.requestFocus() }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.folder_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPalette(selected = selectedColor, onSelect = { selectedColor = it }, swatchSize = 30.dp)

                if (existingFolders.any { it.id !in excluded }) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.parent_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { showParentPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parentName ?: stringResource(R.string.parent_none),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderName, selectedColor, selectedParentId) },
                enabled = folderName.isNotBlank()
            ) {
                Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )

    if (showParentPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.parent_folder),
            folders = existingFolders,
            selectedId = selectedParentId,
            excludedIds = excluded,
            noneLabel = stringResource(R.string.parent_none),
            onDismiss = { showParentPicker = false },
            onSelect = {
                selectedParentId = it
                showParentPicker = false
            }
        )
    }
}
