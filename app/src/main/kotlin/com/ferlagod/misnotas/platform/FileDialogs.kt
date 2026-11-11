// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Diálogos nativos para abrir y guardar ficheros (sustituyen al selector de documentos y al
 * de fotos de Android). En macOS y Windows usan el diálogo del sistema; en Linux, el de Swing,
 * que se integra mejor que el de AWT.
 */
object FileDialogs {

    /** Ventana principal, para que los diálogos salgan centrados sobre ella. */
    var owner: Frame? = null

    /** Extensiones que se aceptan al insertar imágenes. */
    val IMAGE_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    /** Extensiones que se aceptan al importar notas. */
    val IMPORT_EXTENSIONS = listOf("zip", "md", "markdown", "txt")

    /** Pide uno o varios ficheros con alguna de [extensions]. Devuelve una lista vacía si se cancela. */
    fun openFiles(title: String, extensions: List<String>, multiple: Boolean = true): List<File> {
        if (Os.current == Os.LINUX) {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                isMultiSelectionEnabled = multiple
                fileFilter = FileNameExtensionFilter(extensions.joinToString(", "), *extensions.toTypedArray())
            }
            if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return emptyList()
            return if (multiple) chooser.selectedFiles.toList() else listOfNotNull(chooser.selectedFile)
        }
        val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
            isMultipleMode = multiple
            setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
            // Windows ignora el filtro anterior; este sí lo respeta.
            if (Os.current == Os.WINDOWS) file = extensions.joinToString(";") { "*.$it" }
        }
        dialog.isVisible = true
        return dialog.files.orEmpty().filter { file -> extensions.any { file.name.endsWith(".$it", ignoreCase = true) } }
    }

    /** Pide dónde guardar un fichero; añade [extension] si el usuario no la escribe. */
    fun saveFile(title: String, defaultName: String, extension: String): File? {
        val file = if (Os.current == Os.LINUX) {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                selectedFile = File(defaultName)
                fileFilter = FileNameExtensionFilter(extension, extension)
            }
            if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return null
            chooser.selectedFile
        } else {
            val dialog = FileDialog(owner, title, FileDialog.SAVE).apply { file = defaultName }
            dialog.isVisible = true
            val name = dialog.file ?: return null
            File(dialog.directory, name)
        }
        return if (file.name.endsWith(".$extension", ignoreCase = true)) file else File(file.parentFile, "${file.name}.$extension")
    }
}
