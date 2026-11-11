// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.remote.webdav

/**
 * Metadatos de un archivo remoto obtenido mediante WebDAV PROPFIND.
 */
data class RemoteFileMeta(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val contentLength: Long,
    /** ETag normalizado (sin comillas ni prefijo W/). */
    val etag: String?
)
