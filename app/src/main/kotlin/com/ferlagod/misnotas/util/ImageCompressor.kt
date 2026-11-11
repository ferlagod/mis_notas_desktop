// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresión y redimensionado de imágenes (con Skia, el motor gráfico de Compose).
 *
 * Convierte la imagen en WebP con pérdida (lado máximo 1600 px, calidad 75) respetando la
 * orientación EXIF, igual que la versión de Android, para que los adjuntos sean compatibles.
 */
object ImageCompressor {

    const val MAX_DIMENSION_PX = 1600
    const val COMPRESSION_QUALITY = 75
    const val MIME_TYPE = "image/webp"

    /** Límite de lectura: evita cargar ficheros absurdamente grandes en memoria. */
    private const val MAX_INPUT_BYTES = 80L * 1024 * 1024

    suspend fun compressImage(
        file: File,
        maxDimensionPx: Int = MAX_DIMENSION_PX,
        quality: Int = COMPRESSION_QUALITY
    ): ByteArray = withContext(Dispatchers.IO) {
        require(file.length() in 1..MAX_INPUT_BYTES) { "Imagen vacía o demasiado grande: ${file.name}" }
        compressBytes(file.readBytes(), maxDimensionPx, quality)
    }

    fun compressBytes(
        bytes: ByteArray,
        maxDimensionPx: Int = MAX_DIMENSION_PX,
        quality: Int = COMPRESSION_QUALITY
    ): ByteArray {
        // Skia ya aplica la orientación EXIF al decodificar: la imagen sale derecha.
        val source = Image.makeFromEncoded(bytes)
        try {
            val srcW = source.width
            val srcH = source.height
            require(srcW > 0 && srcH > 0) { "No se pudieron determinar las dimensiones de la imagen" }

            val scale = if (max(srcW, srcH) > maxDimensionPx) maxDimensionPx.toFloat() / max(srcW, srcH) else 1f
            val outW = (srcW * scale).roundToInt().coerceAtLeast(1)
            val outH = (srcH * scale).roundToInt().coerceAtLeast(1)

            val surface = Surface.makeRasterN32Premul(outW, outH)
            surface.canvas.drawImageRect(
                source,
                Rect.makeWH(srcW.toFloat(), srcH.toFloat()),
                Rect.makeWH(outW.toFloat(), outH.toFloat()),
                SamplingMode.MITCHELL,
                null,
                true
            )
            val result = surface.makeImageSnapshot()
            val encoded = result.encodeToData(EncodedImageFormat.WEBP, quality)
                ?: result.encodeToData(EncodedImageFormat.JPEG, quality)
                ?: error("No se pudo codificar la imagen")
            return encoded.bytes
        } finally {
            source.close()
        }
    }
}
