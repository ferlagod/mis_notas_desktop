// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.remote.webdav

import java.io.IOException

/** Error HTTP devuelto por el servidor WebDAV. */
open class WebDavException(val code: Int, message: String, val reason: Reason = Reason.HTTP) : IOException(message) {
    enum class Reason { HTTP, PATH_NOT_FOUND }
}

/** 401/403: usuario o contraseña de aplicación incorrectos. */
class WebDavAuthException(code: Int) :
    WebDavException(code, "Usuario o contraseña de aplicación incorrectos ($code)")

/** 412: el fichero remoto cambió desde la última lectura (control de concurrencia optimista). */
class WebDavPreconditionFailedException(fileName: String) :
    WebDavException(412, "El fichero $fileName cambió en el servidor durante la sincronización")
