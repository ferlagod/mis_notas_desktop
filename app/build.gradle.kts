// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/** Versión de la app (instaladores y pantalla "Acerca de"). Formato MAJOR.MINOR.PATCH, MAJOR > 0. */
val appVersion = "1.0.0"

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.coroutines.swing)

    // Base de datos local (SQLite incluido en la app, sin depender del sistema)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)

    // Sincronización WebDAV
    implementation(libs.okhttp)
    implementation(libs.org.json)

    // Imágenes
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Llavero del sistema (Keychain de macOS, Credential Manager de Windows, Secret Service de Linux)
    implementation(libs.java.keyring)

    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------------------------
// Textos: los strings.xml (mismo formato que en Android) se leen en tiempo de ejecución desde
// resources/i18n. Esta tarea genera las claves `R.string.*` / `R.plurals.*` para que el
// compilador detecte claves inexistentes, y `BuildConfig` con la versión.
// ---------------------------------------------------------------------------------------------
abstract class GenerateAppSources : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringsFile: RegularFileProperty

    @get:Input
    abstract val versionName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile.get().asFile)
        fun names(tag: String): List<String> {
            val nodes = doc.getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
        }
        val dir = outputDir.get().asFile.resolve("com/ferlagod/misnotas")
        dir.mkdirs()
        dir.resolve("R.kt").writeText(buildString {
            appendLine("// Generado por la tarea generateAppSources a partir de i18n/values/strings.xml. No editar.")
            appendLine("package com.ferlagod.misnotas")
            appendLine()
            appendLine("@Suppress(\"ClassName\", \"ObjectPropertyName\")")
            appendLine("object R {")
            appendLine("    object string {")
            names("string").forEach { appendLine("        const val $it = \"$it\"") }
            appendLine("    }")
            appendLine("    object plurals {")
            names("plurals").forEach { appendLine("        const val $it = \"$it\"") }
            appendLine("    }")
            appendLine("}")
        })
        dir.resolve("BuildConfig.kt").writeText(
            """
            |// Generado por la tarea generateAppSources. No editar.
            |package com.ferlagod.misnotas
            |
            |object BuildConfig {
            |    const val VERSION_NAME = "${versionName.get()}"
            |    const val APPLICATION_ID = "com.ferlagod.misnotas"
            |}
            |""".trimMargin()
        )
    }
}

val generateAppSources = tasks.register<GenerateAppSources>("generateAppSources") {
    stringsFile.set(layout.projectDirectory.file("src/main/resources/i18n/values/strings.xml"))
    versionName.set(appVersion)
    outputDir.set(layout.buildDirectory.dir("generated/appSources/kotlin"))
}

sourceSets.main {
    kotlin.srcDir(generateAppSources)
}

// ---------------------------------------------------------------------------------------------
// Empaquetado: ./gradlew packageDistributionForCurrentOS
//   macOS → .dmg · Windows → .msi / .exe · Linux → .deb / .rpm
// Cada instalador se genera en su propio sistema (jpackage no compila de forma cruzada).
// ---------------------------------------------------------------------------------------------
compose.desktop {
    application {
        mainClass = "com.ferlagod.misnotas.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx768m")

        buildTypes.release.proguard {
            // R8/ProGuard no aporta mucho en escritorio y complica Room, JNA y OkHttp.
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Mis Notas"
            packageVersion = appVersion
            description = "Notas en Markdown con carpetas, cifrado de extremo a extremo y sincronización con Nextcloud"
            copyright = "© 2026 ferlagod. GPL-3.0-or-later"
            vendor = "ferlagod"
            licenseFile.set(rootProject.file("LICENSE"))

            modules(
                "java.instrument",
                "java.management",
                "java.naming",       // OkHttp (resolución de nombres)
                "java.sql",
                "jdk.crypto.ec",     // TLS con curvas elípticas (HTTPS de Nextcloud)
                "jdk.security.auth",
                "jdk.unsupported"    // JNA (llavero del sistema) y SQLite incluido
            )
            // Certificados raíz del almacén de Windows (solo existe en el JDK de Windows).
            if (System.getProperty("os.name").startsWith("Windows")) modules("jdk.crypto.mscapi")

            macOS {
                bundleID = "com.ferlagod.misnotas"
                appCategory = "public.app-category.productivity"
                dockName = "Mis Notas"
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                menu = true
                menuGroup = "Mis Notas"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fijo para siempre: permite que las versiones nuevas actualicen la instalada.
                upgradeUuid = "6F0B5B1C-3E2A-4C55-9D7E-2B8A41C9E7F3"
                iconFile.set(project.file("icons/icon.ico"))
            }
            linux {
                packageName = "mis-notas"
                appCategory = "Office"
                menuGroup = "Office"
                shortcut = true
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
