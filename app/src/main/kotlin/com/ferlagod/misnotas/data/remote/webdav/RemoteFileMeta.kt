// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.remote.webdav

/**
 * Metadatos de un archivo remoto obtenido mediante WebDAV PROPFIND.
 */
data class RemoteFileMeta(
    /** Nombre del fichero (último segmento de la ruta). */
    val name: String,
    /** Ruta completa decodificada, tal como la devuelve el servidor. */
    val path: String,
    /** Siempre `false`: el listado descarta los directorios. */
    val isDirectory: Boolean,
    /** Fecha de modificación en milisegundos (0 si el servidor no la envía). */
    val lastModified: Long,
    /** Tamaño en bytes (0 si el servidor no lo envía). */
    val contentLength: Long,
    /** ETag normalizado (sin comillas ni prefijo W/). */
    val etag: String?
)
