// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.attachment

import org.junit.Assert.assertEquals
import org.junit.Test

/** Detección del tipo de imagen de los adjuntos por sus primeros bytes. */
class AttachmentStoreTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun detectsPng() = assertEquals("image/png", AttachmentStore.detectMimeType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A)))

    @Test
    fun detectsJpeg() = assertEquals("image/jpeg", AttachmentStore.detectMimeType(bytes(0xFF, 0xD8, 0xFF, 0xE0)))

    @Test
    fun detectsGif() = assertEquals("image/gif", AttachmentStore.detectMimeType("GIF89a".toByteArray()))

    @Test
    fun detectsWebp() = assertEquals("image/webp", AttachmentStore.detectMimeType("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))

    @Test
    fun unknownOrShortUsesFallback() {
        assertEquals("image/png", AttachmentStore.detectMimeType(bytes(0x00, 0x01), fallback = "image/png"))
        assertEquals("image/webp", AttachmentStore.detectMimeType(ByteArray(0)))
        // RIFF sin "WEBP" (p. ej. un WAV) no es una imagen WebP.
        assertEquals("image/jpeg", AttachmentStore.detectMimeType("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray(), fallback = "image/jpeg"))
    }
}
