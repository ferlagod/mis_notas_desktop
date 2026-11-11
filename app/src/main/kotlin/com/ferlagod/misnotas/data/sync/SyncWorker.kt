// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.sync

import com.ferlagod.misnotas.data.remote.webdav.WebDavAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Sincronización periódica mientras la app está abierta (cada 30 min; sustituye a WorkManager).
 * Solo actúa si hay una cuenta conectada y la bóveda está desbloqueada. Ante errores
 * transitorios (red) reintenta antes, con espera creciente.
 */
object SyncWorker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Intervalo entre sincronizaciones. */
    private val PERIOD = 30.minutes
    /** Primera espera tras un error transitorio; se duplica en cada reintento hasta [PERIOD]. */
    private val FIRST_RETRY = 2.minutes

    /**
     * Arranca la sincronización periódica (la primera, al cabo de [PERIOD]). No hace nada si ya está en marcha.
     * Los errores permanentes (credenciales o bóveda) no se reintentan antes de tiempo.
     */
    @Synchronized
    fun schedule() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(PERIOD)
            var retry = FIRST_RETRY
            while (isActive) {
                val error = SyncRepository.getInstance().synchronize().exceptionOrNull()
                val permanent = error is WebDavAuthException || error is WrongMasterPasswordException || error is VaultException
                if (error == null || permanent) {
                    retry = FIRST_RETRY
                    delay(PERIOD)
                } else {
                    delay(retry)
                    retry = (retry * 2).coerceAtMost(PERIOD)
                }
            }
        }
    }

    /** Detiene la sincronización periódica (p. ej. al desconectar la cuenta). */
    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
    }
}
