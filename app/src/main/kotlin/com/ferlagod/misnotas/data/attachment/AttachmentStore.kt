// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.attachment

import com.ferlagod.misnotas.platform.AppDirs
import java.io.File

/**
 * Almacén de adjuntos en la carpeta de datos de la app (`files/attachments/<id>`).
 *
 * En el Markdown de las notas los adjuntos se referencian como `![texto](attachment://<id>)`.
 */
class AttachmentStore(baseDir: File = AppDirs.filesDir) {

    private val dir: File = File(baseDir, DIR_NAME).apply { mkdirs() }

    fun file(id: String): File = File(dir, sanitize(id))

    fun exists(id: String): Boolean = file(id).let { it.exists() && it.length() > 0 }

    fun read(id: String): ByteArray = file(id).readBytes()

    /** Escritura atómica: primero a un temporal y luego renombrado. */
    fun write(id: String, bytes: ByteArray) {
        val target = file(id)
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun delete(id: String) {
        file(id).delete()
    }

    companion object {
        const val DIR_NAME = "attachments"
        const val SCHEME = "attachment://"

        private val REF_REGEX = Regex("""attachment://([A-Za-z0-9_-]+)""")

        fun reference(id: String): String = "$SCHEME$id"

        fun referencedIds(markdown: String): Set<String> =
            REF_REGEX.findAll(markdown).map { it.groupValues[1] }.toSet()

        fun firstReferencedId(markdown: String): String? =
            REF_REGEX.find(markdown)?.groupValues?.get(1)

        fun fileFor(id: String): File =
            File(File(AppDirs.filesDir, DIR_NAME), sanitize(id))

        /**
         * Tipo de imagen según sus primeros bytes (PNG, JPEG, GIF o WebP); [fallback] si no se
         * reconoce. Sirve para los adjuntos que llegan del servidor, que no llevan su tipo.
         */
        fun detectMimeType(bytes: ByteArray, fallback: String = "image/webp"): String {
            fun startsWith(vararg sig: Int, offset: Int = 0) =
                bytes.size >= offset + sig.size && sig.indices.all { bytes[offset + it] == sig[it].toByte() }
            return when {
                startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
                startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
                startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
                startsWith(0x52, 0x49, 0x46, 0x46) && startsWith(0x57, 0x45, 0x42, 0x50, offset = 8) -> "image/webp"
                else -> fallback
            }
        }

        private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
