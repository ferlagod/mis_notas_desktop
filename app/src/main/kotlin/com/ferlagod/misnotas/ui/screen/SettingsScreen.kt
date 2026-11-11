// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.screen

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import com.ferlagod.misnotas.platform.Drawables
import com.ferlagod.misnotas.platform.painterResource
import com.ferlagod.misnotas.i18n.pluralStringResource
import com.ferlagod.misnotas.i18n.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferlagod.misnotas.BuildConfig
import com.ferlagod.misnotas.platform.FileDialogs
import com.ferlagod.misnotas.util.DateFormats
import java.io.File
import com.ferlagod.misnotas.R
import com.ferlagod.misnotas.data.local.pref.AppSettings
import com.ferlagod.misnotas.data.local.pref.AppSettingsState
import com.ferlagod.misnotas.data.local.pref.NextcloudCredentials
import com.ferlagod.misnotas.data.local.pref.SortOrder
import com.ferlagod.misnotas.data.local.pref.ThemeMode
import com.ferlagod.misnotas.data.sync.SyncState
import com.ferlagod.misnotas.ui.theme.AccentGreen
import com.ferlagod.misnotas.ui.theme.AccentRed
import com.ferlagod.misnotas.util.AppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ajustes: aspecto, privacidad (bloqueo de la app), notas y papelera, copia de seguridad,
 * sincronización con Nextcloud, idioma e información de la app.
 *
 * @param busy hay una importación o exportación en curso (los botones se desactivan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isConfigured: Boolean,
    currentCredentials: NextcloudCredentials?,
    syncState: SyncState,
    lastSync: Long?,
    settings: AppSettingsState,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSaveNextcloud: (serverUrl: String, user: String, appPass: String, masterPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDisconnectNextcloud: () -> Unit,
    onTriggerSync: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onGridLayoutChange: (Boolean) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onAutoPurgeChange: (Boolean) -> Unit,
    /** Activar el bloqueo trae la contraseña nueva; desactivarlo, null. */
    onAppLockChange: (enabled: Boolean, password: String?) -> Unit,
    onExport: (File) -> Unit,
    onImport: (List<File>) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val exportTitle = stringResource(R.string.export_notes)
    val importTitle = stringResource(R.string.import_notes)
    var showSetAppPassword by remember { mutableStateOf(false) }

    if (showSetAppPassword) {
        SetAppPasswordDialog(
            onDismiss = { showSetAppPassword = false },
            onConfirm = { password ->
                showSetAppPassword = false
                onAppLockChange(true, password)
            }
        )
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = READABLE_MAX_WIDTH)
                .padding(horizontal = 16.dp)
        ) {
            // --- Apariencia ---
            SectionTitle(stringResource(R.string.section_appearance))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    RowLabel(Icons.Rounded.DarkMode, stringResource(R.string.theme))
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = ThemeMode.entries
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                            ) {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                        }
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Divider()
                SwitchRow(
                    icon = Icons.Rounded.Contrast,
                    title = stringResource(R.string.amoled_mode),
                    subtitle = stringResource(R.string.amoled_mode_desc),
                    checked = settings.amoled,
                    onCheckedChange = onAmoledChange
                )
                Divider()
                SwitchRow(
                    icon = Icons.Rounded.GridView,
                    title = stringResource(R.string.grid_view),
                    subtitle = stringResource(R.string.grid_view_desc),
                    checked = settings.gridLayout,
                    onCheckedChange = onGridLayoutChange
                )
                Divider()
                SortRow(current = settings.sortOrder, onSelect = onSortOrderChange)
            }

            // --- Privacidad ---
            SectionTitle(stringResource(R.string.section_privacy))
            SettingsCard {
                SwitchRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.app_lock),
                    subtitle = stringResource(R.string.app_lock_desc),
                    checked = settings.appLock,
                    onCheckedChange = { enabled ->
                        if (enabled) showSetAppPassword = true else onAppLockChange(false, null)
                    }
                )
            }

            // --- Notas ---
            SectionTitle(stringResource(R.string.section_notes))
            SettingsCard {
                SwitchRow(
                    icon = Icons.Rounded.AutoDelete,
                    title = stringResource(R.string.auto_purge_trash),
                    subtitle = stringResource(R.string.auto_purge_trash_desc, AppSettings.TRASH_RETENTION_DAYS),
                    checked = settings.autoPurgeTrash,
                    onCheckedChange = onAutoPurgeChange
                )
            }

            // --- Copia de seguridad ---
            SectionTitle(stringResource(R.string.section_backup))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { FileDialogs.saveFile(exportTitle, backupFileName(), "zip")?.let(onExport) },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.export_notes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                FileDialogs.openFiles(importTitle, FileDialogs.IMPORT_EXTENSIONS)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let(onImport)
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.import_notes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    AnimatedVisibility(visible = busy) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_in_progress), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // --- Sincronización ---
            SectionTitle(stringResource(R.string.section_sync))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    NextcloudSection(
                        isConfigured = isConfigured,
                        currentCredentials = currentCredentials,
                        syncState = syncState,
                        lastSync = lastSync,
                        onSaveNextcloud = onSaveNextcloud,
                        onDisconnectNextcloud = onDisconnectNextcloud,
                        onTriggerSync = onTriggerSync
                    )
                }
            }

            // --- Idioma ---
            SectionTitle(stringResource(R.string.section_language))
            SettingsCard {
                LanguageSection(current = currentLanguage, onSelected = onLanguageSelected)
            }

            // --- Acerca de ---
            SectionTitle(stringResource(R.string.section_about))
            SettingsCard { AboutSection() }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Nombre propuesto para la copia de seguridad: `MisNotas-AAAA-MM-DD.zip`. */
private fun backupFileName(): String =
    "MisNotas-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".zip"

/** Título de una sección de ajustes. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = 8.dp)
    )
}

/** Tarjeta que agrupa filas de ajustes. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

/** Separador entre filas de una tarjeta. */
@Composable
private fun Divider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 56.dp)
    )
}

/** Icono y texto de una fila. */
@Composable
private fun RowLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingIcon(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
    }
}

/** Icono de una fila de ajustes, dentro de su fondo redondeado. */
@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

/** Fila con un interruptor; toda la fila es pulsable. */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Fila con un menú para elegir el orden de las notas. */
@Composable
private fun SortRow(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = false, role = Role.DropdownList, onClick = { expanded = true })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIcon(Icons.AutoMirrored.Rounded.Sort)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(stringResource(current.labelRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes())) },
                    leadingIcon = { RadioButton(selected = order == current, onClick = null) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Nextcloud
// ----------------------------------------------------------------------

/**
 * Sección de la cuenta: sin cuenta, formulario para conectarla; con cuenta, servidor, usuario,
 * estado y fecha de la última sincronización, y botones para sincronizar o desconectar.
 */
@Composable
private fun NextcloudSection(
    isConfigured: Boolean,
    currentCredentials: NextcloudCredentials?,
    syncState: SyncState,
    lastSync: Long?,
    onSaveNextcloud: (serverUrl: String, user: String, appPass: String, masterPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDisconnectNextcloud: () -> Unit,
    onTriggerSync: () -> Unit
) {
    val msgRequiredFields = stringResource(R.string.error_required_fields)
    val msgMasterRequired = stringResource(R.string.error_master_required)
    val msgConnected = stringResource(R.string.account_connected_syncing)
    val msgSyncStarted = stringResource(R.string.sync_started)
    val msgDisconnected = stringResource(R.string.disconnected_message)
    val primary = MaterialTheme.colorScheme.primary
    val focusManager = LocalFocusManager.current

    var serverUrl by remember(currentCredentials) { mutableStateOf(currentCredentials?.serverUrl ?: "https://") }
    var username by remember(currentCredentials) { mutableStateOf(currentCredentials?.username ?: "") }
    var appPassword by remember(currentCredentials) { mutableStateOf(currentCredentials?.appPassword ?: "") }
    var masterPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var masterPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    // Con la cuenta ya conectada el formulario se pliega para no estorbar.
    var editing by remember(isConfigured) { mutableStateOf(!isConfigured) }

    // Estado de la conexión
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isConfigured) AccentGreen.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isConfigured) Icons.Rounded.CloudSync else Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = if (isConfigured) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isConfigured) stringResource(R.string.nextcloud_connected) else stringResource(R.string.local_mode),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = if (isConfigured) "${currentCredentials?.username.orEmpty()} • ${currentCredentials?.serverUrl.orEmpty()}"
                else stringResource(R.string.local_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isConfigured) {
                val statusText = when (syncState) {
                    is SyncState.Syncing -> stringResource(syncState.stage.labelRes)
                    is SyncState.Error -> stringResource(R.string.status_error, syncState.message)
                    is SyncState.Success -> stringResource(R.string.status_synced, formatSyncTime(syncState.timestamp)) +
                        (if (syncState.warnings > 0) " " + pluralStringResource(R.plurals.sync_warnings, syncState.warnings, syncState.warnings) else "") +
                        (if (syncState.protectedNotes > 0) " " + pluralStringResource(R.plurals.sync_protected_notes, syncState.protectedNotes, syncState.protectedNotes) else "")
                    SyncState.Idle -> lastSync?.let { stringResource(R.string.status_last_sync, formatSyncTime(it)) }
                        ?: stringResource(R.string.status_pending)
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncState is SyncState.Error) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (isConfigured) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    onTriggerSync()
                    successMessage = msgSyncStarted
                },
                enabled = syncState !is SyncState.Syncing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.sync_now), maxLines = 1)
            }
            OutlinedButton(
                onClick = { showDisconnectConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.disconnect), maxLines = 1)
            }
        }
        if (!editing) {
            TextButton(onClick = { editing = true }) {
                Text(stringResource(R.string.edit_account), color = primary)
            }
        }

        AnimatedVisibility(visible = showDisconnectConfirm) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(AccentRed.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.disconnect_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccentRed
                )
                Text(text = stringResource(R.string.disconnect_message), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showDisconnectConfirm = false }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showDisconnectConfirm = false
                            onDisconnectNextcloud()
                            serverUrl = "https://"
                            username = ""
                            appPassword = ""
                            masterPassword = ""
                            errorMessage = null
                            successMessage = msgDisconnected
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.disconnect_confirm))
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = editing) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; errorMessage = null; successMessage = null },
                placeholder = stringResource(R.string.server_hint),
                leadingIcon = Icons.Rounded.Storage,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null; successMessage = null },
                placeholder = stringResource(R.string.username_hint),
                leadingIcon = Icons.Rounded.Person,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsTextField(
                value = appPassword,
                onValueChange = { appPassword = it; errorMessage = null; successMessage = null },
                placeholder = stringResource(R.string.app_password_hint),
                leadingIcon = Icons.Rounded.Key,
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.section_e2ee),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.e2ee_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsTextField(
                value = masterPassword,
                onValueChange = { masterPassword = it; errorMessage = null; successMessage = null },
                placeholder = stringResource(R.string.master_password_hint),
                leadingIcon = Icons.Rounded.Lock,
                isPassword = true,
                passwordVisible = masterPasswordVisible,
                onTogglePassword = { masterPasswordVisible = !masterPasswordVisible },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (serverUrl.isBlank() || serverUrl.trim() == "https://" || username.isBlank() || appPassword.isBlank()) {
                        errorMessage = msgRequiredFields
                        return@Button
                    }
                    if (masterPassword.isBlank()) {
                        errorMessage = msgMasterRequired
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    successMessage = null
                    onSaveNextcloud(
                        serverUrl.trim(),
                        username.trim(),
                        appPassword.trim(),
                        masterPassword,
                        {
                            isLoading = false
                            masterPassword = ""
                            successMessage = msgConnected
                        },
                        { error ->
                            isLoading = false
                            errorMessage = error
                        }
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (isConfigured) stringResource(R.string.update_and_sync) else stringResource(R.string.connect_and_encrypt),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }

    errorMessage?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    successMessage?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = it, style = MaterialTheme.typography.bodySmall, color = AccentGreen)
    }
}

/** Fecha y hora de una sincronización. */
private fun formatSyncTime(timestamp: Long): String = DateFormats.dateTime(timestamp)

/** Pide la contraseña nueva (dos veces) al activar el bloqueo de la app. */
@Composable
private fun SetAppPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val mismatch = repeat.isNotEmpty() && repeat != password
    val valid = password.length >= MIN_APP_PASSWORD && password == repeat
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.app_lock_set_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.app_lock_set_password_message, MIN_APP_PASSWORD),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.app_lock_password)) },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text(stringResource(R.string.app_lock_password_repeat)) },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.app_lock_password_mismatch)) }
                    } else null,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(password) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Longitud mínima de la contraseña de bloqueo. */
private const val MIN_APP_PASSWORD = 4

// ----------------------------------------------------------------------
// Idioma y Acerca de
// ----------------------------------------------------------------------

/** Selector del idioma de la app. */
@Composable
private fun LanguageSection(current: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        AppLanguage.entries.forEachIndexed { index, language ->
            val label = if (language == AppLanguage.SYSTEM) stringResource(R.string.language_system) else language.nativeName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = language == current,
                        onClick = { if (language != current) onSelected(language) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 0) SettingIcon(Icons.Rounded.Language) else Spacer(modifier = Modifier.width(26.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                )
                RadioButton(selected = language == current, onClick = null)
            }
        }
    }
}

/** Información de la app: versión, licencia y enlace al código fuente. */
@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Drawables.LAUNCHER_FOREGROUND),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row {
            TextButton(onClick = { uriHandler.openUri(REPO_URL) }) {
                Text(stringResource(R.string.source_code))
            }
            TextButton(onClick = { uriHandler.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }) {
                Text(stringResource(R.string.license))
            }
        }
    }
}

/** Repositorio con el código fuente. */
private const val REPO_URL = "https://github.com/ferlagod/mis_notas_desktop"

/**
 * Campo de texto de los ajustes con icono; si [isPassword], oculta el texto y añade el botón de mostrarlo.
 */
@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(R.string.cd_toggle_password),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth()
    )
}
