// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.model

/**
 * Estado de sincronización para sincronización local-first con WebDAV.
 */
enum class SyncStatus {
    /**
     * El elemento está completamente sincronizado con el servidor WebDAV / Nextcloud.
     */
    SYNCED,

    /**
     * Creado o modificado localmente; requiere subirse al servidor WebDAV.
     */
    PENDING_UPLOAD,

    /**
     * Marcado para eliminación; requiere eliminarse del servidor WebDAV antes de borrado definitivo.
     */
    PENDING_DELETE
}
