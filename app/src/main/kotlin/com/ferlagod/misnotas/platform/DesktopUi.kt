// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// -------------------------------------------------------------------------------------------
// "Atrás": en escritorio lo dispara la tecla Esc (o el botón "atrás" del ratón).
// -------------------------------------------------------------------------------------------

/** Pila de manejadores de "atrás"; gana el último registrado y activo (como en Android). */
class BackDispatcher {
    /** Manejador registrado; [enabled] se actualiza en cada recomposición. */
    internal class Entry(var enabled: Boolean, var onBack: () -> Unit)

    /** Manejadores en orden de registro. */
    private val entries = CopyOnWriteArrayList<Entry>()

    /** Registra un manejador (queda el último, con prioridad). */
    internal fun add(entry: Entry) = entries.add(entry)
    /** Quita un manejador. */
    internal fun remove(entry: Entry) = entries.remove(entry)

    /** Devuelve true si algún manejador ha atendido el "atrás". */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

/** Pila de "atrás" de la ventana; la ventana principal la conecta con la tecla Esc. */
val LocalBackDispatcher = staticCompositionLocalOf { BackDispatcher() }

/** Equivalente a `BackHandler` de androidx.activity. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val dispatcher = LocalBackDispatcher.current
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { currentOnBack() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(dispatcher) {
        dispatcher.add(entry)
        onDispose { dispatcher.remove(entry) }
    }
}

// -------------------------------------------------------------------------------------------
// Ciclo de vida de la ventana
// -------------------------------------------------------------------------------------------

/**
 * Avisos de la ventana a las pantallas: la ventana pierde el foco, se minimiza o se va a
 * cerrar. El editor se registra aquí para guardar lo escrito (equivale a ON_STOP en Android).
 */
object AppLifecycle {
    /** Funciones a llamar cuando la app pasa a segundo plano. */
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Registra una función que se llamará al pasar a segundo plano. */
    fun addBackgroundListener(listener: () -> Unit) = listeners.add(listener)
    /** Quita una función registrada con [addBackgroundListener]. */
    fun removeBackgroundListener(listener: () -> Unit) = listeners.remove(listener)

    /** Avisa a todos los registrados; lo llama la ventana al perder el foco, minimizarse o cerrarse. */
    fun notifyBackground() = listeners.forEach { it() }
}

/** Ejecuta [onBackground] cuando la app pasa a segundo plano o se cierra. */
@Composable
fun OnAppBackground(onBackground: () -> Unit) {
    val current by rememberUpdatedState(onBackground)
    DisposableEffect(Unit) {
        val listener = { current() }
        AppLifecycle.addBackgroundListener(listener)
        onDispose { AppLifecycle.removeBackgroundListener(listener) }
    }
}

// -------------------------------------------------------------------------------------------
// Recursos
// -------------------------------------------------------------------------------------------

/** Imágenes de recursos ya decodificadas, por ruta. */
private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

/** Carga una imagen de `resources/` (p. ej. "drawable/ic_logo_round.png"). */
fun loadResourceImage(path: String): ImageBitmap = imageCache.getOrPut(path) {
    val bytes = AppDirs::class.java.getResourceAsStream("/$path")?.use { it.readBytes() }
        ?: error("Recurso no encontrado: $path")
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

/** Equivalente a `painterResource` de Android para imágenes de `resources/`. */
@Composable
fun painterResource(path: String): Painter = remember(path) { BitmapPainter(loadResourceImage(path)) }

/** Rutas de las imágenes incluidas en `resources/`. */
object Drawables {
    /** Logotipo redondo (pantalla de conexión y "Acerca de"). */
    const val LOGO_ROUND = "drawable/ic_logo_round.png"
    /** Icono de la app sin fondo. */
    const val LAUNCHER_FOREGROUND = "drawable/ic_launcher_foreground.png"
}

// -------------------------------------------------------------------------------------------
// Portapapeles
// -------------------------------------------------------------------------------------------

/** Portapapeles del sistema. */
object Clipboard {
    /** Copia [text] al portapapeles; si falla, solo lo anota en el registro. */
    fun copyText(text: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            Log.w("Clipboard", "No se pudo copiar al portapapeles", e)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Ratón
// -------------------------------------------------------------------------------------------

/**
 * Clic derecho: en escritorio sustituye a la pulsación larga de Android (seleccionar notas,
 * opciones de una carpeta…).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun androidx.compose.ui.Modifier.onSecondaryClick(action: () -> Unit): androidx.compose.ui.Modifier =
    this.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press) { event ->
        if (event.buttons.isSecondaryPressed) {
            event.changes.forEach { it.consume() }
            action()
        }
    }
