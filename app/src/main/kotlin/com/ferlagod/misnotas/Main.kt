// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferlagod.misnotas.data.local.pref.AppSettings
import com.ferlagod.misnotas.data.local.pref.ThemeMode
import com.ferlagod.misnotas.i18n.Strings
import com.ferlagod.misnotas.i18n.stringResource
import com.ferlagod.misnotas.platform.AppLifecycle
import com.ferlagod.misnotas.platform.BackDispatcher
import com.ferlagod.misnotas.platform.Drawables
import com.ferlagod.misnotas.platform.FileDialogs
import com.ferlagod.misnotas.platform.LocalBackDispatcher
import com.ferlagod.misnotas.platform.Log
import com.ferlagod.misnotas.platform.Os
import com.ferlagod.misnotas.platform.SingleInstance
import com.ferlagod.misnotas.platform.painterResource
import com.ferlagod.misnotas.ui.screen.AppLockScreen
import com.ferlagod.misnotas.ui.theme.MisNotasTheme
import com.ferlagod.misnotas.ui.viewmodel.MainViewModel
import com.ferlagod.misnotas.ui.viewmodel.PendingNavigation
import com.ferlagod.misnotas.util.AppLock
import com.ferlagod.misnotas.util.LanguageManager
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.system.exitProcess

/** Tras volver a la app, no se sincroniza otra vez si la última vez fue hace menos de esto. */
private const val FOCUS_SYNC_MIN_INTERVAL_MS = 60_000L

fun main() {
    // macOS: barra de menús en la parte superior y título de ventana acorde al tema del sistema.
    if (Os.current == Os.MAC) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "Mis Notas")
        System.setProperty("apple.awt.application.appearance", "system")
    }
    Thread.setDefaultUncaughtExceptionHandler { _, e -> Log.e("MisNotas", "Error no controlado", e) }
    // Linux: diálogos de ficheros (Swing) con el aspecto del escritorio en lugar de "Metal".
    if (Os.current == Os.LINUX) {
        runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
    }
    LanguageManager.apply()

    // Una sola instancia: dos procesos compartiendo la base de datos lanzarían sincronizaciones
    // simultáneas contra el mismo Nextcloud.
    if (!SingleInstance.acquire()) {
        javax.swing.JOptionPane.showMessageDialog(
            null,
            Strings.get(R.string.already_running),
            "Mis Notas",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        )
        exitProcess(0)
    }

    val viewModel = MainViewModel()
    val settingsStore = AppSettings.store

    application {
        val windowState = rememberWindowState(
            placement = if (settingsStore.getBoolean(KEY_WIN_MAXIMIZED, false)) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = DpSize(settingsStore.getInt(KEY_WIN_W, 1180).dp, settingsStore.getInt(KEY_WIN_H, 780).dp),
            position = settingsStore.getInt(KEY_WIN_X, Int.MIN_VALUE).let { x ->
                val y = settingsStore.getInt(KEY_WIN_Y, Int.MIN_VALUE)
                // Si la posición guardada cae en un monitor que ya no está conectado, se centra.
                if (x == Int.MIN_VALUE || y == Int.MIN_VALUE || !isOnScreen(x, y)) WindowPosition(Alignment.Center)
                else WindowPosition(x.dp, y.dp)
            }
        )
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }

        fun requestClose() {
            if (closing) return
            closing = true
            // Guarda lo que se esté escribiendo y espera a que llegue a la base de datos.
            AppLifecycle.notifyBackground()
            scope.launch {
                try {
                    viewModel.awaitPendingWrites()
                } finally {
                    exitApplication()
                    exitProcess(0)
                }
            }
        }

        // Recuerda el tamaño y la posición de la ventana para la próxima vez.
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                .debounce(500)
                .collect { (size, position, placement) ->
                    settingsStore.edit {
                        putBoolean(KEY_WIN_MAXIMIZED, placement == WindowPlacement.Maximized)
                        if (placement == WindowPlacement.Floating) {
                            putInt(KEY_WIN_W, size.width.value.toInt())
                            putInt(KEY_WIN_H, size.height.value.toInt())
                            if (position is WindowPosition.Absolute) {
                                putInt(KEY_WIN_X, position.x.value.toInt())
                                putInt(KEY_WIN_Y, position.y.value.toInt())
                            }
                        }
                    }
                }
        }

        val backDispatcher = remember { BackDispatcher() }

        // macOS: "Salir" (Cmd+Q) y "Ajustes…" del menú de la app pasan por el mismo camino.
        DisposableEffect(Unit) {
            if (Os.current == Os.MAC && java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                runCatching {
                    desktop.setQuitHandler { _, response ->
                        response.cancelQuit()
                        requestClose()
                    }
                }
                runCatching {
                    desktop.setPreferencesHandler { viewModel.requestNavigation(PendingNavigation.Settings) }
                }
            }
            onDispose { }
        }

        Window(
            onCloseRequest = ::requestClose,
            state = windowState,
            title = "Mis Notas",
            icon = painterResource(Drawables.LOGO_ROUND),
            onKeyEvent = { event ->
                // Esc = "atrás" (cerrar la nota, salir de la selección, volver a la pantalla anterior).
                event.type == KeyEventType.KeyDown && event.key == Key.Escape && backDispatcher.dispatch()
            }
        ) {
            window.minimumSize = java.awt.Dimension(480, 560)
            LaunchedEffect(Unit) { FileDialogs.owner = window }

            CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
                MisNotasWindowContent(viewModel = viewModel, onQuit = ::requestClose)
            }
        }
    }
}

@Composable
private fun FrameWindowScope.MisNotasWindowContent(viewModel: MainViewModel, onQuit: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Leer el idioma vuelve a componer la ventana entera al cambiarlo en Ajustes.
    val locale = Strings.locale

    /** La app está tapada por la pantalla de bloqueo. */
    var appLocked by remember { mutableStateOf(settings.appLock && AppLock.isAvailable()) }
    var backgroundSince by remember { mutableStateOf(0L) }
    var lastFocusSync by remember { mutableStateOf(System.currentTimeMillis()) }

    // Segundo plano = la ventana pierde el foco (otra app, minimizada…). Equivale a onStop/onStart.
    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent?) {
                AppLifecycle.notifyBackground()
                // El foco pasa a un diálogo de la propia app (abrir/guardar fichero): no es segundo plano.
                if (e?.oppositeWindow == null) backgroundSince = System.currentTimeMillis()
            }

            override fun windowGainedFocus(e: WindowEvent?) {
                val now = System.currentTimeMillis()
                val lockEnabled = viewModel.settings.value.appLock && AppLock.isAvailable()
                if (lockEnabled && backgroundSince > 0 && now - backgroundSince > AppLock.GRACE_PERIOD_MS) appLocked = true
                backgroundSince = 0
                // Trae los cambios de otros dispositivos al volver a la app.
                if (now - lastFocusSync > FOCUS_SYNC_MIN_INTERVAL_MS) {
                    lastFocusSync = now
                    viewModel.triggerSync()
                }
            }
        }
        window.addWindowFocusListener(listener)
        onDispose { window.removeWindowFocusListener(listener) }
    }

    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val canNavigate = !appLocked && (!isConfigured || isUnlocked)

    MenuBar {
        Menu(stringResource(R.string.menu_file), mnemonic = 'F') {
            Item(
                stringResource(R.string.cd_new_note),
                enabled = canNavigate,
                shortcut = shortcut(Key.N),
                onClick = { viewModel.requestNavigation(PendingNavigation.NewNote) }
            )
            Item(
                stringResource(R.string.sync_now),
                enabled = canNavigate && isConfigured,
                shortcut = shortcut(Key.R),
                onClick = viewModel::triggerSync
            )
            Separator()
            Item(
                stringResource(R.string.settings_title),
                enabled = canNavigate,
                shortcut = shortcut(Key.Comma),
                onClick = { viewModel.requestNavigation(PendingNavigation.Settings) }
            )
            if (settings.appLock && AppLock.isAvailable()) {
                Item(
                    stringResource(R.string.menu_lock_app),
                    enabled = !appLocked,
                    shortcut = shortcut(Key.L),
                    onClick = { appLocked = true; AppLifecycle.notifyBackground() }
                )
            }
            Separator()
            Item(stringResource(R.string.menu_quit), shortcut = shortcut(Key.Q), onClick = onQuit)
        }
    }

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MisNotasTheme(darkTheme = darkTheme, amoled = settings.amoled) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.runtime.key(locale) {
                    AppNavigationHost(
                        viewModel = viewModel,
                        appLocked = appLocked,
                        currentLanguage = LanguageManager.current(),
                        onLanguageSelected = { language ->
                            LanguageManager.set(language)
                            // La navegación se rehace con el idioma nuevo: se vuelve a Ajustes.
                            viewModel.requestNavigation(PendingNavigation.Settings)
                        },
                        onAppLockChange = { enabled, password ->
                            if (enabled && password != null) {
                                AppLock.setPassword(password)
                                viewModel.setAppLock(true)
                            } else {
                                viewModel.setAppLock(false)
                                AppLock.clearPassword()
                            }
                        }
                    )
                }
                AnimatedVisibility(visible = appLocked, enter = fadeIn(), exit = fadeOut()) {
                    AppLockScreen(onUnlock = { password ->
                        AppLock.verify(password).also { if (it) appLocked = false }
                    })
                }
            }
        }
    }
}

/** True si la esquina superior izquierda de la ventana (con margen para la barra de título) se ve en algún monitor. */
private fun isOnScreen(x: Int, y: Int): Boolean = try {
    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { device ->
        device.defaultConfiguration.bounds.intersects(java.awt.Rectangle(x, y, 120, 40))
    }
} catch (_: Exception) {
    false
}

/** Ctrl + tecla en Windows/Linux, Cmd + tecla en macOS. */
private fun shortcut(key: Key) =
    if (Os.current == Os.MAC) KeyShortcut(key, meta = true) else KeyShortcut(key, ctrl = true)

private const val KEY_WIN_W = "window_width"
private const val KEY_WIN_H = "window_height"
private const val KEY_WIN_X = "window_x"
private const val KEY_WIN_Y = "window_y"
private const val KEY_WIN_MAXIMIZED = "window_maximized"
