// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.util

import com.ferlagod.misnotas.data.attachment.AttachmentStore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageCompressorTest {

    private fun jpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    /** Inserta un segmento EXIF mínimo con la orientación indicada justo después del SOI. */
    private fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8, // cabecera big-endian, IFD en 8
            0, 1,                                                     // 1 entrada
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0, // Orientation, SHORT
            0, 0, 0, 0                                                // sin más IFD
        )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        return image.width to image.height
    }

    @Test
    fun producesWebpAndLimitsSize() {
        val result = ImageCompressor.compressBytes(jpeg(3200, 1600))
        assertEquals("image/webp", AttachmentStore.detectMimeType(result, fallback = "?"))
        assertEquals(1600 to 800, size(result))
    }

    @Test
    fun appliesExifRotation() {
        // Orientación 6: la foto se guardó tumbada y hay que girarla 90°.
        val result = ImageCompressor.compressBytes(withOrientation(jpeg(400, 200), 6))
        assertEquals(200 to 400, size(result))
    }

    @Test
    fun keepsNormalOrientation() {
        val result = ImageCompressor.compressBytes(withOrientation(jpeg(400, 200), 1))
        assertEquals(400 to 200, size(result))
    }
}
