// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.remote.webdav

import com.ferlagod.misnotas.platform.SystemTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cliente WebDAV mínimo para Nextcloud / ownCloud (RFC 4918).
 *
 * Usa ETags para detectar cambios y `If-Match` / `If-None-Match` en las subidas para no
 * sobrescribir nunca un cambio remoto que no hayamos visto.
 */
class WebDavClient(
    baseUrl: String,
    username: String,
    appPassword: String,
    private val okHttpClient: OkHttpClient = sharedHttpClient
) {

    private val rootUrl: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val authHeader: String = Credentials.basic(username, appPassword, Charsets.UTF_8)

    companion object {
        private const val USER_AGENT = "MisNotas-Desktop"
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

        /** Propiedades que se piden en cada PROPFIND. */
        private val PROPFIND_XML_BODY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:getlastmodified/>
                    <d:getetag/>
                    <d:resourcetype/>
                    <d:getcontentlength/>
                </d:prop>
            </d:propfind>
        """.trimIndent()

        /**
         * Cliente HTTP compartido. Confía en los certificados del sistema además de los de Java (ver `SystemTrust`).
         */
        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .sslSocketFactory(SystemTrust.socketFactory, SystemTrust.trustManager)
                .build()
        }

        /** Quita espacios, el prefijo `W/` y las comillas de un ETag; `null` si queda vacío. */
        fun normalizeEtag(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return raw.trim().removePrefix("W/").trim('"').takeIf { it.isNotBlank() }
        }
    }

    /** Petición con la autenticación y el User-Agent ya puestos. */
    private fun baseRequest(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", authHeader)
        .header("User-Agent", USER_AGENT)

    /** Lanza [WebDavAuthException] si el servidor rechaza las credenciales. */
    private fun Response.throwIfAuthError() {
        if (code == 401 || code == 403) throw WebDavAuthException(code)
    }

    /**
     * Comprueba credenciales y asegura que el directorio raíz de la app exista (lo crea con MKCOL).
     */
    suspend fun ensureRootFolder(): Unit = withContext(Dispatchers.IO) {
        val check = baseRequest(rootUrl)
            .method("PROPFIND", PROPFIND_XML_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .build()

        okHttpClient.newCall(check).execute().use { response ->
            response.throwIfAuthError()
            if (response.isSuccessful) return@withContext
            if (response.code != 404) {
                throw WebDavException(response.code, "No se pudo acceder a la carpeta en Nextcloud (${response.code})")
            }
        }

        val mkcol = baseRequest(rootUrl).method("MKCOL", null).build()
        okHttpClient.newCall(mkcol).execute().use { response ->
            response.throwIfAuthError()
            // 405 = ya existe
            if (response.isSuccessful || response.code == 405) return@withContext
            if (response.code == 409) {
                throw WebDavException(409, "WebDAV path not found", WebDavException.Reason.PATH_NOT_FOUND)
            }
            throw WebDavException(response.code, "No se pudo crear la carpeta en Nextcloud (${response.code})")
        }
    }

    /**
     * Sube un fichero.
     *
     * @param ifMatch si no es null, solo se sobrescribe si el ETag remoto coincide.
     * @param ifNoneMatch si es true, solo se crea si el fichero NO existe.
     * @return el nuevo ETag si el servidor lo devuelve.
     * @throws WebDavPreconditionFailedException si la precondición falla (412).
     */
    suspend fun upload(
        fileName: String,
        content: ByteArray,
        contentType: String,
        ifMatch: String? = null,
        ifNoneMatch: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val builder = baseRequest(fileUrl(fileName)).put(content.toRequestBody(contentType.toMediaType()))
        if (ifMatch != null) builder.header("If-Match", "\"$ifMatch\"")
        else if (ifNoneMatch) builder.header("If-None-Match", "*")

        okHttpClient.newCall(builder.build()).execute().use { response ->
            response.throwIfAuthError()
            if (response.code == 412) throw WebDavPreconditionFailedException(fileName)
            if (!response.isSuccessful) {
                throw WebDavException(response.code, "Error al subir $fileName (${response.code})")
            }
            normalizeEtag(response.header("OC-ETag") ?: response.header("ETag"))
        }
    }

    /** Sube texto UTF-8 como JSON. Mismos parámetros y errores que [upload]. */
    suspend fun uploadText(
        fileName: String,
        content: String,
        ifMatch: String? = null,
        ifNoneMatch: Boolean = false
    ): String? = upload(fileName, content.toByteArray(Charsets.UTF_8), "application/json; charset=utf-8", ifMatch, ifNoneMatch)

    /** Descarga un fichero; devuelve null si no existe (404). */
    suspend fun downloadOrNull(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = baseRequest(fileUrl(fileName)).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            response.throwIfAuthError()
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) {
                throw WebDavException(response.code, "Error al descargar $fileName (${response.code})")
            }
            response.body.bytes()
        }
    }

    /** Descarga un fichero; lanza [WebDavException] con código 404 si no existe. */
    suspend fun download(fileName: String): ByteArray =
        downloadOrNull(fileName) ?: throw WebDavException(404, "No existe $fileName en el servidor")

    /** Descarga un fichero y lo decodifica como UTF-8. */
    suspend fun downloadText(fileName: String): String = String(download(fileName), Charsets.UTF_8)

    /** Borrado idempotente (404 se considera éxito). */
    suspend fun delete(fileName: String): Unit = withContext(Dispatchers.IO) {
        val request = baseRequest(fileUrl(fileName)).delete().build()
        okHttpClient.newCall(request).execute().use { response ->
            response.throwIfAuthError()
            if (response.isSuccessful || response.code == 404) return@withContext
            throw WebDavException(response.code, "Error al eliminar $fileName (${response.code})")
        }
    }

    /** Obtiene el ETag actual de un fichero (o null si no existe). */
    suspend fun fetchEtag(fileName: String): String? = withContext(Dispatchers.IO) {
        val request = baseRequest(fileUrl(fileName))
            .method("PROPFIND", PROPFIND_XML_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.throwIfAuthError()
            if (response.code == 404 || !response.isSuccessful) return@withContext null
            parsePropfindResponse(response.body.string()).firstOrNull()?.etag
        }
    }

    /** Lista los ficheros (no directorios) del directorio raíz con PROPFIND Depth: 1. */
    suspend fun listRemoteFiles(): List<RemoteFileMeta> = withContext(Dispatchers.IO) {
        val request = baseRequest(rootUrl)
            .method("PROPFIND", PROPFIND_XML_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .build()

        val xmlBody = okHttpClient.newCall(request).execute().use { response ->
            response.throwIfAuthError()
            if (!response.isSuccessful) {
                throw WebDavException(response.code, "Error al listar ficheros en Nextcloud (${response.code})")
            }
            response.body.string()
        }

        if (xmlBody.isBlank()) emptyList() else parsePropfindResponse(xmlBody)
    }

    /** URL completa de un fichero dentro de la carpeta de la app. */
    private fun fileUrl(fileName: String): String = rootUrl + fileName.removePrefix("/")

    /**
     * Extrae los ficheros de una respuesta PROPFIND (multistatus). Ignora los directorios y
     * desactiva DTD y entidades externas para evitar ataques XXE.
     */
    internal fun parsePropfindResponse(xml: String): List<RemoteFileMeta> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Sin entidades externas ni DTD: la respuesta viene de la red.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

        fun Element.children(name: String): List<Element> {
            val nodes = getElementsByTagNameNS("*", name)
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }
        fun Element.text(name: String): String? = children(name).firstOrNull()?.textContent

        val result = mutableListOf<RemoteFileMeta>()
        for (response in doc.documentElement.children("response")) {
            val href = response.text("href") ?: continue
            if (response.children("collection").isNotEmpty()) continue
            val path = URLDecoder.decode(href.trim().replace("+", "%2B"), "UTF-8").trimEnd('/')
            val name = path.substringAfterLast('/')
            if (name.isBlank()) continue
            result += RemoteFileMeta(
                name = name,
                path = path,
                isDirectory = false,
                lastModified = parseHttpDate(response.text("getlastmodified")),
                contentLength = response.text("getcontentlength")?.trim()?.toLongOrNull() ?: 0L,
                etag = normalizeEtag(response.text("getetag"))
            )
        }
        return result
    }

    /** Convierte una fecha HTTP (RFC 1123) a milisegundos; 0 si falta o no es válida. */
    private fun parseHttpDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
