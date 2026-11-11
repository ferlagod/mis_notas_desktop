// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.ui.screen

import com.ferlagod.misnotas.platform.BackHandler
import com.ferlagod.misnotas.platform.Clipboard
import com.ferlagod.misnotas.platform.FileDialogs
import com.ferlagod.misnotas.platform.Log
import com.ferlagod.misnotas.platform.OnAppBackground
import com.ferlagod.misnotas.util.DateFormats
import java.io.File
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import com.ferlagod.misnotas.i18n.pluralStringResource
import com.ferlagod.misnotas.i18n.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferlagod.misnotas.R
import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.backup.MarkdownFile
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.ui.components.ColorPalette
import com.ferlagod.misnotas.ui.components.FolderPickerDialog
import com.ferlagod.misnotas.ui.components.ZoomableImageDialog
import com.ferlagod.misnotas.data.local.model.SyncStatus
import com.ferlagod.misnotas.ui.markdown.ImageSizing
import com.ferlagod.misnotas.ui.markdown.InlineKind
import com.ferlagod.misnotas.ui.markdown.NoteImage
import com.ferlagod.misnotas.ui.markdown.rememberImageModel
import com.ferlagod.misnotas.ui.markdown.InlineToken
import com.ferlagod.misnotas.ui.markdown.LinePrefix
import com.ferlagod.misnotas.ui.markdown.LivePreviewTransformation
import com.ferlagod.misnotas.ui.markdown.MarkdownColors
import com.ferlagod.misnotas.ui.markdown.MarkdownDocumentCache
import com.ferlagod.misnotas.ui.markdown.activeInlineKinds
import com.ferlagod.misnotas.ui.markdown.applyLink
import com.ferlagod.misnotas.ui.markdown.applyPendingStyles
import com.ferlagod.misnotas.ui.markdown.clearLinePrefix
import com.ferlagod.misnotas.ui.markdown.lineBlockAt
import com.ferlagod.misnotas.ui.markdown.linkAt
import com.ferlagod.misnotas.ui.markdown.newlineBeforeBlock
import com.ferlagod.misnotas.ui.markdown.normalizeMarkdown
import com.ferlagod.misnotas.ui.markdown.reconcileEdit
import com.ferlagod.misnotas.ui.markdown.removeLink
import com.ferlagod.misnotas.ui.markdown.toggleInlineStyle
import com.ferlagod.misnotas.ui.markdown.toggleTaskBox
import com.ferlagod.misnotas.ui.markdown.MarkdownReader
import com.ferlagod.misnotas.ui.markdown.TextEdit
import com.ferlagod.misnotas.ui.markdown.continueList
import com.ferlagod.misnotas.ui.markdown.insertAtCursor
import com.ferlagod.misnotas.ui.markdown.markdownPlainText
import com.ferlagod.misnotas.ui.markdown.toggleLinePrefix
import com.ferlagod.misnotas.ui.markdown.toggleTaskLine
import com.ferlagod.misnotas.ui.markdown.toggleWrap
import com.ferlagod.misnotas.ui.theme.AccentOrange
import com.ferlagod.misnotas.ui.theme.AccentRed
import com.ferlagod.misnotas.ui.theme.NoteColor
import com.ferlagod.misnotas.ui.theme.getAccentColor
import com.ferlagod.misnotas.ui.theme.isAppInDarkTheme
import com.ferlagod.misnotas.ui.theme.toEditorBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Historial de deshacer/rehacer del cuerpo de la nota. Agrupa lo escrito seguido en un solo paso
 * (se corta tras una pausa, un espacio o un salto de línea), como los editores de texto habituales.
 */
private class EditHistory {
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastRecord = 0L

    /** Cambia con cada operación para que la interfaz se recomponga. */
    var version by mutableIntStateOf(0)
        private set

    val canUndo get() = version >= 0 && undoStack.isNotEmpty()
    val canRedo get() = version >= 0 && redoStack.isNotEmpty()

    fun record(before: TextFieldValue, after: TextFieldValue, force: Boolean = false) {
        if (before.text == after.text) return
        val now = System.nanoTime() / 1_000_000
        val delta = after.text.length - before.text.length
        val boundary = force || undoStack.isEmpty() || now - lastRecord > 1200 || delta !in -1..1 ||
            after.text.getOrNull(after.selection.start - 1)?.isWhitespace() == true
        if (boundary) {
            // Sin la composición del teclado: restaurarla después confundiría al IME.
            undoStack.addLast(before.copy(composition = null))
            if (undoStack.size > MAX) undoStack.removeFirst()
        }
        lastRecord = now
        redoStack.clear()
        version++
    }

    fun undo(current: TextFieldValue): TextFieldValue? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current.copy(composition = null))
        lastRecord = 0
        version++
        return previous
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current.copy(composition = null))
        lastRecord = 0
        version++
        return next
    }

    private companion object {
        const val MAX = 200
    }
}

private fun TextFieldValue.apply(edit: TextEdit) =
    TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String?,
    folderId: String?,
    initialPinned: Boolean,
    existingNote: NoteEntity?,
    noteLoaded: Boolean,
    folders: List<FolderEntity>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSaveNote: (id: String, title: String, content: String, folderId: String?, color: NoteColor, isPinned: Boolean, sync: Boolean, closing: Boolean) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDuplicateNote: suspend (String) -> String?,
    onOpenNote: (String) -> Unit,
    onAddImage: suspend (File) -> String,
    /** Versión en vivo de la nota (cambia si la sincronización trae cambios de otro dispositivo). */
    liveNote: NoteEntity? = null,
    /** Avisa de la versión de la nota que tiene el editor, para detectar cambios simultáneos. */
    onBaseLoaded: (id: String, title: String, content: String, folderId: String?, color: NoteColor, isPinned: Boolean) -> Unit =
        { _, _, _, _, _, _ -> },
    /** Nota recién creada (con id ya asignado o sin él): el cursor va directo al texto. */
    isNewNote: Boolean = noteId == null
) {
    val isDark = isAppInDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val imageAlt = stringResource(R.string.image_alt)
    val imageAddFailed = stringResource(R.string.image_add_failed)
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    val pickImagesTitle = stringResource(R.string.fmt_image)
    val saveMarkdownTitle = stringResource(R.string.save_as_markdown)
    val untitledName = stringResource(R.string.untitled)

    // Id estable: una nota nueva conserva el mismo id en todos los guardados.
    val stableId = rememberSaveable { noteId ?: UUID.randomUUID().toString() }

    var title by rememberSaveable { mutableStateOf("") }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var selectedColor by rememberSaveable { mutableStateOf(NoteColor.DEFAULT) }
    var isPinned by rememberSaveable { mutableStateOf(initialPinned) }
    var selectedFolderId by rememberSaveable { mutableStateOf(folderId) }
    var updatedAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    // true cuando el contenido inicial ya se ha cargado (para no guardar una nota vacía encima).
    var loaded by rememberSaveable { mutableStateOf(noteId == null) }
    var deleted by remember { mutableStateOf(false) }
    // Tras el primer guardado de una nota nueva ya se puede borrar, duplicar, etc.
    var dirty by remember { mutableStateOf(false) }

    var isReadingMode by rememberSaveable { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<Any?>(null) }
    /** Imagen cuyo tamaño se está cambiando (tramo que ocupa en el Markdown). */
    var imageResize by remember { mutableStateOf<ImageResizeState?>(null) }

    val history = remember { EditHistory() }
    val bodyFocus = remember { FocusRequester() }

    // Versión de la nota tal como se cargó (o se recibió de otro dispositivo sin haberla tocado).
    var baseTitle by rememberSaveable { mutableStateOf("") }
    var baseContent by rememberSaveable { mutableStateOf("") }

    // Editor visual: el texto es Markdown pero se ve con formato y sin marcadores.
    val markdownColors = MarkdownColors(
        accent = primary,
        text = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        rule = MaterialTheme.colorScheme.outlineVariant
    )
    val docCache = remember(markdownColors, imageAlt) { MarkdownDocumentCache(imageAlt, markdownColors) }
    val livePreview = remember(docCache) { LivePreviewTransformation(docCache) }
    var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    /** Formatos activados sin selección: se aplican a lo próximo que se escriba. */
    var pendingStyles by remember { mutableStateOf(emptySet<InlineKind>()) }
    var showHeadingMenu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf<LinkDialogState?>(null) }

    // Carga inicial (solo una vez: una sincronización posterior no pisa lo que se está escribiendo)
    LaunchedEffect(existingNote, noteLoaded) {
        if (loaded) return@LaunchedEffect
        if (existingNote != null) {
            title = existingNote.title
            selectedColor = NoteColor.fromKey(existingNote.color)
            isPinned = existingNote.isPinned
            selectedFolderId = existingNote.folderId
            updatedAt = existingNote.updatedAt
            textValue = TextFieldValue(existingNote.content)
            baseTitle = existingNote.title
            baseContent = existingNote.content
            onBaseLoaded(stableId, existingNote.title, existingNote.content, selectedFolderId, selectedColor, isPinned)
            loaded = true
        } else if (noteLoaded) {
            loaded = true
        }
    }

    // Llega una versión nueva de otro dispositivo: si aquí no se ha tocado el texto, se muestra.
    // Si ya se había editado, se conserva lo escrito y la otra versión queda como copia "(conflicto)".
    LaunchedEffect(liveNote) {
        val note = liveNote ?: return@LaunchedEffect
        if (!loaded || deleted || note.syncStatus != SyncStatus.SYNCED) return@LaunchedEffect
        if (note.title == baseTitle && note.content == baseContent) return@LaunchedEffect
        if (title.trim() != baseTitle.trim() || textValue.text.trimEnd() != baseContent.trimEnd()) return@LaunchedEffect
        baseTitle = note.title
        baseContent = note.content
        // Carpeta, color y fijado no se adoptan aquí: al guardar, el ViewModel solo aplica los
        // que el usuario cambie en el editor, así que los recibidos no se pisan.
        onBaseLoaded(stableId, note.title, note.content, selectedFolderId, selectedColor, isPinned)
        title = note.title
        val refreshed = TextFieldValue(note.content, TextRange(textValue.selection.start.coerceAtMost(note.content.length)))
        history.record(textValue, refreshed, force = true)
        textValue = refreshed
        updatedAt = note.updatedAt
    }

    // Nota nueva: el cursor va directo al cuerpo para escribir sin un toque extra.
    LaunchedEffect(Unit) {
        if (isNewNote) {
            delay(150)
            runCatching { bodyFocus.requestFocus() }
        }
    }

    fun persistChanges(sync: Boolean, closing: Boolean = false) {
        if (!loaded || deleted) return
        onSaveNote(stableId, title, normalizeMarkdown(textValue.text), selectedFolderId, selectedColor, isPinned, sync, closing)
    }

    fun close() {
        persistChanges(sync = true, closing = true)
        onBack()
    }

    // Autoguardado local mientras se escribe
    LaunchedEffect(title, textValue.text, selectedColor, isPinned, selectedFolderId, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(1000)
        persistChanges(sync = false)
        if (title.isNotBlank() || textValue.text.isNotBlank()) {
            if (dirty || existingNote == null) updatedAt = System.currentTimeMillis()
            dirty = true
        }
    }

    // Guardar al salir con el gesto/botón "atrás" del sistema y al pasar a segundo plano
    BackHandler { close() }
    // Al minimizar la ventana, perder el foco o cerrar la app.
    OnAppBackground { persistChanges(sync = true) }
    // Al cerrar el editor sin pasar por "atrás" (p. ej. cambiar de nota en el panel lateral
    // de un plegable) se guarda también lo escrito en el último segundo.
    DisposableEffect(Unit) { onDispose { persistChanges(sync = true) } }

    fun updateBody(newValue: TextFieldValue, forceHistory: Boolean = false) {
        history.record(textValue, newValue, forceHistory)
        textValue = newValue
    }

    fun applyEdit(edit: TextEdit) = updateBody(textValue.apply(edit), forceHistory = true)

    fun onBodyChange(newValue: TextFieldValue) {
        val old = textValue
        if (newValue.text == old.text) {
            // Solo se movió el cursor (o cambió la composición del teclado).
            if (newValue.selection != old.selection) pendingStyles = emptySet()
            textValue = newValue
            return
        }
        if (newValue.selection.collapsed) {
            val cursor = newValue.selection.start
            val presentation = docCache.get(old.text).presentation
            // 1. Que borrar o sustituir texto no deje marcadores sueltos a la vista.
            reconcileEdit(old.text, newValue.text, cursor, presentation)?.let {
                updateBody(newValue.apply(it))
                return
            }
            // 2. Intro al principio de un título o elemento de lista: abre una línea encima.
            newlineBeforeBlock(old.text, newValue.text, cursor, presentation)?.let {
                updateBody(newValue.apply(it))
                return
            }
            // 3. Negrita/cursiva… activadas sin selección: lo que se escribe lleva ese formato.
            applyPendingStyles(old.text, newValue.text, cursor, pendingStyles)?.let { (edit, shift) ->
                val insertStart = cursor - (newValue.text.length - old.text.length)
                val composition = newValue.composition
                    ?.takeIf { it.min >= insertStart }
                    ?.let { TextRange(it.start + shift, it.end + shift) }
                pendingStyles = emptySet()
                updateBody(TextFieldValue(edit.text, TextRange(edit.selectionStart), composition))
                return
            }
            // 4. Intro en una lista: continúa la lista.
            continueList(old.text, newValue.text, cursor)?.let {
                updateBody(newValue.apply(it))
                return
            }
        }
        updateBody(newValue)
    }

    fun toggleInline(kind: InlineKind) {
        val sel = textValue.selection
        val presentation = docCache.get(textValue.text).presentation
        val edit = toggleInlineStyle(textValue.text, sel.min, sel.max, kind, presentation)
        if (edit != null) {
            pendingStyles = emptySet()
            if (edit.text == textValue.text) {
                textValue = textValue.copy(selection = TextRange(edit.selectionStart, edit.selectionEnd))
            } else {
                applyEdit(edit)
            }
        } else {
            pendingStyles = if (kind in pendingStyles) pendingStyles - kind else pendingStyles + kind
        }
        runCatching { bodyFocus.requestFocus() }
    }

    fun setHeading(prefix: LinePrefix?) {
        val sel = textValue.selection
        val current = lineBlockAt(textValue.text, sel.min)
        applyEdit(
            when {
                prefix == null -> clearLinePrefix(textValue.text, sel.min, sel.max)
                current == prefix -> clearLinePrefix(textValue.text, sel.min, sel.max)
                else -> toggleLinePrefix(textValue.text, sel.min, sel.max, prefix)
            }
        )
    }

    fun openLinkDialog() {
        val sel = textValue.selection
        val presentation = docCache.get(textValue.text).presentation
        val existing = linkAt(presentation, sel.min)
        linkDialog = if (existing != null) {
            LinkDialogState(
                label = textValue.text.substring(existing.contentStart, existing.contentEnd),
                url = existing.url.orEmpty(),
                existing = existing
            )
        } else {
            LinkDialogState(label = textValue.text.substring(sel.min, sel.max), url = "", existing = null)
        }
    }

    /** Abre el diálogo de tamaño para la imagen Markdown del tramo [start, end) del texto. */
    fun openImageResize(start: Int, end: Int) {
        val text = textValue.text
        if (start < 0 || end > text.length || start >= end) return
        val snippet = text.substring(start, end)
        val source = Regex("""!\[.*?]\((.*?)\)""").find(snippet)?.groupValues?.get(1) ?: return
        imageResize = ImageResizeState(start, snippet, source)
    }

    /** Toque en la casilla de una tarea (la marca o desmarca) o en una imagen (cambia su tamaño). */
    fun onBodyTap(position: Offset): Boolean {
        val layout = bodyLayout ?: return false
        val rendered = docCache.get(textValue.text)
        val displayLength = layout.layoutInput.text.length
        for (token in rendered.presentation.inlineTokens) {
            if (token.kind != InlineKind.IMAGE) continue
            val dStart = rendered.mapping.toDisplay(token.start)
            val dEnd = rendered.mapping.toDisplay(token.end)
            if (dEnd <= dStart || dEnd > displayLength) continue
            val offset = layout.getOffsetForPosition(position)
            val last = layout.getBoundingBox(dEnd - 1)
            val inside = offset in dStart until dEnd ||
                (offset == dEnd && position.x <= last.right && position.y in last.top..last.bottom)
            if (inside) {
                openImageResize(token.start, token.end)
                return true
            }
        }
        val slop = 12f * density.density
        for (box in rendered.presentation.tasks) {
            val displayOffset = rendered.mapping.toDisplay(box.markerStart)
            if (displayOffset >= layout.layoutInput.text.length) continue
            val rect = layout.getBoundingBox(displayOffset)
            if (position.x in (rect.left - slop)..(rect.right + slop) && position.y in rect.top..rect.bottom) {
                updateBody(TextFieldValue(toggleTaskBox(textValue.text, box), textValue.selection), forceHistory = true)
                return true
            }
        }
        return false
    }

    fun linePrefix(prefix: LinePrefix) {
        val sel = textValue.selection
        applyEdit(toggleLinePrefix(textValue.text, sel.min, sel.max, prefix))
    }

    fun wrap(prefix: String, suffix: String = prefix) {
        val sel = textValue.selection
        applyEdit(toggleWrap(textValue.text, sel.min, sel.max, prefix, suffix))
    }

    fun insert(snippet: String) {
        val sel = textValue.selection
        applyEdit(insertAtCursor(textValue.text, sel.min, sel.max, snippet))
    }

    fun addImages(uris: List<File>) {
        if (uris.isEmpty()) return
        coroutineScope.launch {
            val references = mutableListOf<String>()
            for (uri in uris) {
                try {
                    references += onAddImage(uri)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("NoteEditor", "No se pudo añadir la imagen ${uri.name}", e)
                    launch { snackbarHostState.showSnackbar(imageAddFailed, withDismissAction = true) }
                }
            }
            if (references.isNotEmpty()) {
                val before = textValue.text.substring(0, textValue.selection.min)
                val lead = if (before.isEmpty() || before.endsWith("\n")) "" else "\n"
                insert(lead + references.joinToString("\n") { "![$imageAlt]($it)" } + "\n")
            }
        }
    }

    val editorBackground = selectedColor.toEditorBackground(isDark)
    val wordCount = remember(textValue.text) {
        textValue.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
    val charCount = textValue.text.length
    val readingMinutes = (wordCount + 199) / 200
    val currentFolder = remember(selectedFolderId, folders) { folders.firstOrNull { it.id == selectedFolderId } }
    val canManage = existingNote != null || dirty

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val formattedDate = remember(updatedAt) {
                        DateFormats.dateTime(updatedAt)
                    }
                    Text(
                        text = stringResource(R.string.edited_at, formattedDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { close() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isReadingMode = !isReadingMode }) {
                        Icon(
                            imageVector = if (isReadingMode) Icons.Rounded.Edit else Icons.Rounded.Visibility,
                            contentDescription = if (isReadingMode) stringResource(R.string.cd_edit_mode) else stringResource(R.string.cd_read_mode),
                            tint = if (isReadingMode) primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = stringResource(if (isPinned) R.string.unpin else R.string.cd_pin_note),
                            tint = if (isPinned) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.note_color)) },
                                leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showColorPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_to_folder)) },
                                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showFolderPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_as_markdown)) },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                enabled = title.isNotBlank() || textValue.text.isNotBlank(),
                                onClick = {
                                    showMenu = false
                                    saveNoteAsMarkdown(saveMarkdownTitle, title.ifBlank { untitledName }, title, textValue.text)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_text)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                enabled = title.isNotBlank() || textValue.text.isNotBlank(),
                                onClick = {
                                    showMenu = false
                                    copyNote(title, textValue.text)
                                    coroutineScope.launch { snackbarHostState.showSnackbar(copiedMessage, withDismissAction = true) }
                                }
                            )
                            if (canManage) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    leadingIcon = { Icon(Icons.Rounded.FileCopy, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        persistChanges(sync = false)
                                        coroutineScope.launch {
                                            onDuplicateNote(stableId)?.let(onOpenNote)
                                        }
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_to_trash), color = AccentRed) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = AccentRed) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = editorBackground)
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = !isReadingMode) {
                val sel = textValue.selection
                val presentation = docCache.get(textValue.text).presentation
                val activeInline = activeInlineKinds(presentation, sel.min, sel.max) + pendingStyles
                val activeBlock = lineBlockAt(textValue.text, sel.min)
                FormattingToolbar(
                    canUndo = history.canUndo,
                    canRedo = history.canRedo,
                    activeInline = activeInline,
                    activeBlock = activeBlock,
                    showHeadingMenu = showHeadingMenu,
                    onHeadingMenu = { showHeadingMenu = it },
                    onUndo = { history.undo(textValue)?.let { textValue = it } },
                    onRedo = { history.redo(textValue)?.let { textValue = it } },
                    onHeading = { setHeading(it) },
                    onInline = { toggleInline(it) },
                    onBullet = { linePrefix(LinePrefix.BULLET) },
                    onNumbered = { linePrefix(LinePrefix.NUMBERED) },
                    onTask = { linePrefix(LinePrefix.TASK) },
                    onQuote = { linePrefix(LinePrefix.QUOTE) },
                    onCode = {
                        if (textValue.selection.collapsed || !textValue.text.substring(textValue.selection.min, textValue.selection.max).contains('\n')) toggleInline(InlineKind.CODE)
                        else wrap("```\n", "\n```")
                    },
                    onLink = { openLinkDialog() },
                    onRule = { insert(if (textValue.text.substring(0, textValue.selection.min).let { it.isEmpty() || it.endsWith("\n") }) "---\n" else "\n---\n") },
                    onImage = {
                        addImages(FileDialogs.openFiles(pickImagesTitle, FileDialogs.IMAGE_EXTENSIONS).take(MAX_IMAGES_AT_ONCE))
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = editorBackground
    ) { paddingValues ->
        // En pantallas anchas (plegables abiertos, tablets) el texto no pasa de un ancho cómodo de lectura.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = READABLE_MAX_WIDTH)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaChip(
                    icon = Icons.Rounded.Folder,
                    tint = currentFolder?.let { f ->
                        NoteColor.fromKey(f.color).takeIf { it != NoteColor.DEFAULT }?.getAccentColor(isDark)
                    } ?: primary,
                    label = currentFolder?.name ?: stringResource(R.string.no_folder),
                    onClick = { showFolderPicker = true },
                    modifier = Modifier.weight(1f, fill = false)
                )
                MetaChip(
                    icon = Icons.Rounded.Palette,
                    tint = if (selectedColor == NoteColor.DEFAULT) MaterialTheme.colorScheme.onSurfaceVariant else selectedColor.getAccentColor(isDark),
                    label = stringResource(R.string.note_color),
                    onClick = { showColorPicker = true }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            BasicTextField(
                value = title,
                onValueChange = { title = it.replace("\n", " ") },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                readOnly = isReadingMode,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            text = stringResource(R.string.title_hint),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Crossfade(targetState = isReadingMode, label = "editor_mode_crossfade") { reading ->
                if (reading) {
                    if (textValue.text.isBlank()) {
                        Text(
                            text = stringResource(R.string.empty_note),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                                .clickable { isReadingMode = false }
                        )
                    } else {
                        MarkdownReader(
                            markdownText = textValue.text,
                            primaryBlue = primary,
                            onToggleChecklist = { lineIndex ->
                                updateBody(TextFieldValue(toggleTaskLine(textValue.text, lineIndex)), forceHistory = true)
                            },
                            onImageClick = { viewerImage = it },
                            onImageLongClick = { lineIndex, _ ->
                                val text = textValue.text
                                var lineStart = 0
                                var ok = true
                                repeat(lineIndex) {
                                    val nl = text.indexOf('\n', lineStart)
                                    if (nl == -1) ok = false else lineStart = nl + 1
                                }
                                if (ok) {
                                    val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
                                    openImageResize(lineStart, lineEnd)
                                }
                            }
                        )
                    }
                } else {
                    BasicTextField(
                        value = textValue,
                        onValueChange = ::onBodyChange,
                        visualTransformation = livePreview,
                        onTextLayout = { bodyLayout = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        ),
                        cursorBrush = SolidColor(primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 350.dp)
                            .focusRequester(bodyFocus)
                            .onPreviewKeyEvent { event ->
                                // Atajos de escritorio: Ctrl/Cmd + Z, Mayús+Z / Y, B, I, K.
                                if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return@onPreviewKeyEvent false
                                when {
                                    event.key == Key.Z && !event.isShiftPressed -> { history.undo(textValue)?.let { textValue = it }; true }
                                    (event.key == Key.Z && event.isShiftPressed) || event.key == Key.Y -> { history.redo(textValue)?.let { textValue = it }; true }
                                    event.key == Key.B -> { toggleInline(InlineKind.BOLD); true }
                                    event.key == Key.I -> { toggleInline(InlineKind.ITALIC); true }
                                    event.key == Key.K -> { openLinkDialog(); true }
                                    else -> false
                                }
                            }
                            .pointerInput(Unit) {
                                // Se observa el toque sin consumirlo: el cursor se coloca igual.
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                                    if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                                        onBodyTap(up.position)
                                    }
                                }
                            },
                        decorationBox = { innerTextField ->
                            if (textValue.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.note_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    lineHeight = 24.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(
                    R.string.word_count_separator,
                    pluralStringResource(R.plurals.word_count, wordCount, wordCount),
                    pluralStringResource(R.plurals.char_count, charCount, charCount)
                ) + if (wordCount >= 200) "  •  " + pluralStringResource(R.plurals.reading_minutes, readingMinutes, readingMinutes) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.move_to_folder),
            folders = folders,
            selectedId = selectedFolderId,
            onDismiss = { showFolderPicker = false },
            onSelect = {
                selectedFolderId = it
                showFolderPicker = false
            }
        )
    }

    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text(stringResource(R.string.note_color), fontWeight = FontWeight.Bold) },
            text = {
                ColorPalette(
                    selected = selectedColor,
                    onSelect = {
                        selectedColor = it
                        showColorPicker = false
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.move_to_trash_title),
            message = stringResource(R.string.move_to_trash_message),
            confirmLabel = stringResource(R.string.move_to_trash),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                // Guardar antes los últimos cambios para que queden en la papelera
                persistChanges(sync = false)
                deleted = true
                onDeleteNote(stableId)
                showDeleteConfirm = false
                onBack()
            }
        )
    }

    viewerImage?.let { model ->
        ZoomableImageDialog(model = model, onDismiss = { viewerImage = null })
    }

    imageResize?.let { state ->
        ImageSizeDialog(
            source = state.source,
            onDismiss = { imageResize = null },
            onConfirm = { percent ->
                imageResize = null
                val text = textValue.text
                // Por si el texto cambió mientras el diálogo estaba abierto (p. ej. una sincronización).
                val start = if (text.startsWith(state.snippet, state.start)) state.start else text.indexOf(state.snippet)
                if (start >= 0) {
                    val resized = ImageSizing.resizeAt(text, start, start + state.snippet.length, percent)
                    if (resized != text) {
                        val cursor = textValue.selection.start.coerceAtMost(resized.length)
                        updateBody(TextFieldValue(resized, TextRange(cursor)), forceHistory = true)
                    }
                }
            }
        )
    }

    linkDialog?.let { state ->
        LinkDialog(
            state = state,
            onDismiss = { linkDialog = null },
            onRemove = state.existing?.let { link ->
                {
                    applyEdit(removeLink(textValue.text, link))
                    linkDialog = null
                }
            },
            onConfirm = { label, url ->
                val sel = textValue.selection
                applyEdit(applyLink(textValue.text, sel.min, sel.max, label, url, state.existing))
                linkDialog = null
                runCatching { bodyFocus.requestFocus() }
            }
        )
    }
}

/** Imagen cuyo tamaño se cambia: dónde empieza, su Markdown completo y su dirección. */
private data class ImageResizeState(val start: Int, val snippet: String, val source: String)

/**
 * Diálogo para cambiar el ancho de una imagen de la nota, con vista previa. El 100 % la adapta
 * al ancho de la pantalla (el tamaño inicial de todas las imágenes).
 */
@Composable
private fun ImageSizeDialog(
    source: String,
    onDismiss: () -> Unit,
    onConfirm: (percent: Int) -> Unit
) {
    val model = rememberImageModel(source)
    var percent by remember { mutableIntStateOf(ImageSizing.widthPercent(source)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_size_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model != null) {
                    NoteImage(
                        model = model,
                        contentDescription = null,
                        widthPercent = percent,
                        maxHeightFraction = 0.35f
                    )
                }
                Text(
                    text = if (percent >= ImageSizing.DEFAULT_PERCENT) stringResource(R.string.image_size_fit) else "$percent %",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Slider(
                    value = percent.toFloat(),
                    onValueChange = { percent = (it / 5f).roundToInt() * 5 },
                    valueRange = ImageSizing.MIN_PERCENT.toFloat()..ImageSizing.DEFAULT_PERCENT.toFloat(),
                    steps = (ImageSizing.DEFAULT_PERCENT - ImageSizing.MIN_PERCENT) / 5 - 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(25, 50, 75, 100).forEach { preset ->
                        TextButton(onClick = { percent = preset }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(
                                text = "$preset %",
                                fontWeight = if (percent == preset) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(percent) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Datos del diálogo de enlace: texto, dirección y el enlace que se edita (si ya existía). */
private data class LinkDialogState(val label: String, val url: String, val existing: InlineToken?)

@Composable
private fun LinkDialog(
    state: LinkDialogState,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)?,
    onConfirm: (label: String, url: String) -> Unit
) {
    var label by remember { mutableStateOf(state.label) }
    var url by remember { mutableStateOf(state.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fmt_link), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.replace("\n", " ") },
                    label = { Text(stringResource(R.string.link_text)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text(stringResource(R.string.link_url)) },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.link_remove), color = AccentRed) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label, url) }, enabled = url.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Ancho máximo del texto de la nota, para que las líneas no sean incómodas de leer. */
val READABLE_MAX_WIDTH = 760.dp

private fun noteAsPlainText(title: String, content: String): String =
    listOf(title.trim(), markdownPlainText(content).trim()).filter { it.isNotEmpty() }.joinToString("\n\n")

/** Guarda la nota en un fichero `.md` elegido por el usuario (en escritorio sustituye a "Compartir"). */
private fun saveNoteAsMarkdown(dialogTitle: String, fileName: String, title: String, content: String) {
    val file = FileDialogs.saveFile(dialogTitle, MarkdownFile.safeFileName(fileName) + ".md", "md") ?: return
    val body = buildString {
        if (title.isNotBlank()) append("# ").append(title.trim()).append("\n\n")
        append(content.trimEnd()).append('\n')
    }
    try {
        file.writeText(body, Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w("NoteEditor", "No se pudo guardar ${file.path}", e)
    }
}

private fun copyNote(title: String, content: String) {
    Clipboard.copyText(noteAsPlainText(title, content))
}

/** Imágenes que se pueden añadir de una vez (como el selector de fotos de Android). */
private const val MAX_IMAGES_AT_ONCE = 10

@Composable
private fun MetaChip(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FormattingToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    activeInline: Set<InlineKind>,
    activeBlock: LinePrefix?,
    showHeadingMenu: Boolean,
    onHeadingMenu: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHeading: (LinePrefix?) -> Unit,
    onInline: (InlineKind) -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    onTask: () -> Unit,
    onQuote: () -> Unit,
    onCode: () -> Unit,
    onLink: () -> Unit,
    onRule: () -> Unit,
    onImage: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val isHeading = activeBlock == LinePrefix.HEADING1 || activeBlock == LinePrefix.HEADING2 || activeBlock == LinePrefix.HEADING3
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), primary, onUndo, enabled = canUndo)
            ToolbarButton(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.redo), primary, onRedo, enabled = canRedo)
            ToolbarDivider()
            Box {
                ToolbarButton(Icons.Rounded.Title, stringResource(R.string.fmt_heading), primary, { onHeadingMenu(true) }, active = isHeading)
                DropdownMenu(expanded = showHeadingMenu, onDismissRequest = { onHeadingMenu(false) }) {
                    HeadingMenuItem(stringResource(R.string.fmt_heading_1), 22.sp, activeBlock == LinePrefix.HEADING1) {
                        onHeadingMenu(false); onHeading(LinePrefix.HEADING1)
                    }
                    HeadingMenuItem(stringResource(R.string.fmt_heading_2), 19.sp, activeBlock == LinePrefix.HEADING2) {
                        onHeadingMenu(false); onHeading(LinePrefix.HEADING2)
                    }
                    HeadingMenuItem(stringResource(R.string.fmt_heading_3), 17.sp, activeBlock == LinePrefix.HEADING3) {
                        onHeadingMenu(false); onHeading(LinePrefix.HEADING3)
                    }
                    HeadingMenuItem(stringResource(R.string.fmt_normal_text), 15.sp, !isHeading, bold = false) {
                        onHeadingMenu(false); onHeading(null)
                    }
                }
            }
            ToolbarButton(Icons.Rounded.FormatBold, stringResource(R.string.fmt_bold), primary, { onInline(InlineKind.BOLD) }, active = InlineKind.BOLD in activeInline)
            ToolbarButton(Icons.Rounded.FormatItalic, stringResource(R.string.fmt_italic), primary, { onInline(InlineKind.ITALIC) }, active = InlineKind.ITALIC in activeInline)
            ToolbarButton(Icons.Rounded.FormatStrikethrough, stringResource(R.string.fmt_strike), primary, { onInline(InlineKind.STRIKE) }, active = InlineKind.STRIKE in activeInline)
            ToolbarDivider()
            ToolbarButton(Icons.Rounded.CheckBox, stringResource(R.string.fmt_task), primary, onTask, active = activeBlock == LinePrefix.TASK)
            ToolbarButton(Icons.AutoMirrored.Rounded.FormatListBulleted, stringResource(R.string.fmt_bullet), primary, onBullet, active = activeBlock == LinePrefix.BULLET)
            ToolbarButton(Icons.Rounded.FormatListNumbered, stringResource(R.string.fmt_numbered), primary, onNumbered, active = activeBlock == LinePrefix.NUMBERED)
            ToolbarButton(Icons.Rounded.FormatQuote, stringResource(R.string.fmt_quote), primary, onQuote, active = activeBlock == LinePrefix.QUOTE)
            ToolbarDivider()
            ToolbarButton(Icons.Rounded.Image, stringResource(R.string.fmt_image), primary, onImage)
            ToolbarButton(Icons.Rounded.Link, stringResource(R.string.fmt_link), primary, onLink, active = InlineKind.LINK in activeInline)
            ToolbarButton(Icons.Rounded.Code, stringResource(R.string.fmt_code), primary, onCode, active = InlineKind.CODE in activeInline)
            ToolbarButton(Icons.Rounded.HorizontalRule, stringResource(R.string.fmt_rule), primary, onRule)
        }
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(modifier = Modifier
        .height(24.dp)
        .padding(horizontal = 4.dp))
}

@Composable
private fun HeadingMenuItem(label: String, size: androidx.compose.ui.unit.TextUnit, selected: Boolean, bold: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontSize = size,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = onClick
    )
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .padding(2.dp)
            .background(if (active) tint.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(10.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp)
        )
    }
}
