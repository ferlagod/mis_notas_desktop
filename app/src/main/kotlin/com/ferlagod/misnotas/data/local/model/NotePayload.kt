// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.local.model

import org.json.JSONObject

/**
 * Estructura de contenido de una nota para mantener el texto Markdown limpio
 * y separar las cadenas Base64 pesadas en un diccionario interno indexado.
 */
data class NotePayload(
    /** Texto Markdown de la nota. */
    val text: String,
    /** Formato antiguo: imágenes en Base64 indexadas por clave. Vacío en las notas actuales. */
    val images: Map<String, String> = emptyMap()
)

/** Conversión entre el contenido guardado de una nota y [NotePayload]. */
object NoteContentParser {

    /**
     * Parsea el contenido en bruto de Room o WebDAV.
     * Si contiene un JSON estructurado con {"text": "...", "images": {...}}, lo extrae.
     * Si es texto plano (notas existentes), lo carga directamente.
     */
    fun parse(rawContent: String): NotePayload {
        val trimmed = rawContent.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"text\"")) {
            return try {
                val json = JSONObject(trimmed)
                val text = json.optString("text", "")
                val imagesObj = json.optJSONObject("images")
                val images = mutableMapOf<String, String>()
                if (imagesObj != null) {
                    val keys = imagesObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        images[key] = imagesObj.getString(key)
                    }
                }
                NotePayload(text, images)
            } catch (_: Exception) {
                NotePayload(rawContent, emptyMap())
            }
        }
        return NotePayload(rawContent, emptyMap())
    }

    /**
     * Serializa el contenido de la nota para almacenamiento en Room / WebDAV.
     */
    fun serialize(payload: NotePayload): String {
        if (payload.images.isEmpty()) {
            return payload.text
        }
        val json = JSONObject().apply {
            put("text", payload.text)
            val imagesObj = JSONObject()
            payload.images.forEach { (key, value) ->
                imagesObj.put(key, value)
            }
            put("images", imagesObj)
        }
        return json.toString()
    }
}
