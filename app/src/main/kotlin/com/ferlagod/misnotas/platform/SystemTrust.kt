// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Certificados de confianza para HTTPS: los de Java **y** los del sistema operativo.
 *
 * Así funciona, como en Android, un Nextcloud con una autoridad propia que el usuario ya ha
 * instalado en su sistema (Llavero de macOS, almacén de Windows o /etc/ssl en Linux). No se
 * aceptan certificados que no estén en ninguno de los dos almacenes.
 */
object SystemTrust {

    val trustManager: X509TrustManager by lazy {
        val managers = listOfNotNull(defaultManager(), systemManager())
        if (managers.size == 1) managers.first() else CompositeTrustManager(managers)
    }

    val socketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
    }

    private fun managerFor(keyStore: KeyStore?): X509TrustManager? =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()

    private fun defaultManager(): X509TrustManager? = managerFor(null)

    private fun systemManager(): X509TrustManager? = try {
        val store = when (Os.current) {
            Os.WINDOWS -> KeyStore.getInstance("Windows-ROOT").apply { load(null, null) }
            Os.MAC -> KeyStore.getInstance("KeychainStore").apply { load(null, null) }
            Os.LINUX -> linuxBundle()
        }
        store?.takeIf { it.size() > 0 }?.let(::managerFor)
    } catch (e: Exception) {
        Log.w("SystemTrust", "No se pudieron leer los certificados del sistema", e)
        null
    }

    /** Paquete de certificados de la distribución (Debian/Ubuntu, Fedora/RHEL, openSUSE, Arch…). */
    private fun linuxBundle(): KeyStore? {
        val bundle = listOf(
            "/etc/ssl/certs/ca-certificates.crt",
            "/etc/pki/tls/certs/ca-bundle.crt",
            "/etc/ssl/ca-bundle.pem",
            "/etc/ca-certificates/extracted/tls-ca-bundle.pem"
        ).map(::File).firstOrNull { it.isFile } ?: return null
        val certificates = bundle.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
        return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            certificates.forEachIndexed { i, cert -> setCertificateEntry("system-$i", cert) }
        }
    }

    /** Acepta un certificado si lo acepta cualquiera de los almacenes. */
    private class CompositeTrustManager(private val managers: List<X509TrustManager>) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            check { it.checkClientTrusted(chain, authType) }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
            check { it.checkServerTrusted(chain, authType) }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            managers.flatMap { it.acceptedIssuers.asList() }.toTypedArray()

        private fun check(block: (X509TrustManager) -> Unit) {
            var last: CertificateException? = null
            for (manager in managers) {
                try {
                    block(manager)
                    return
                } catch (e: CertificateException) {
                    last = e
                }
            }
            throw last ?: CertificateException("Certificado no válido")
        }
    }
}
