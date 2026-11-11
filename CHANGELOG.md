# Cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones según [SemVer](https://semver.org/lang/es/).

## [Sin publicar]

Primera versión de escritorio (Windows, macOS y Linux), a partir de [Mis Notas para Android](https://github.com/ferlagod/mis_notas) 1.0.0 con todas sus correcciones posteriores. Usa el mismo formato de sincronización (v2) y el mismo esquema de base de datos, así que se sincroniza con la app de Android a través del mismo Nextcloud.

### Añadido
- Instaladores `.dmg` (Apple Silicon e Intel), `.msi`/`.exe` y `.deb`/`.rpm`, con Java incluido.
- Barra de menús (Archivo → Nueva nota, Sincronizar ahora, Ajustes, Bloquear, Salir) y atajos de teclado: Ctrl/Cmd + N, R, `,`, L; Ctrl/Cmd + B, I y K en el editor; deshacer y rehacer; Esc para volver.
- Clic derecho para seleccionar notas y para cambiar el tamaño de una imagen (sustituye a la pulsación larga).
- "Guardar como Markdown…" para exportar una nota a un fichero `.md` (sustituye a "Compartir").
- La ventana recuerda su tamaño, posición y si estaba maximizada.
- Confianza en los certificados raíz del sistema, además de los de Java: funciona un Nextcloud con autoridad propia instalada en el sistema.

### Cambiado respecto a Android
- **Bloqueo de la app** con una contraseña propia (en lugar de huella, cara o PIN del móvil). Se pide al abrir la app y tras un minuto con la ventana en segundo plano.
- Credenciales y clave guardadas en un fichero cifrado cuya clave está en el llavero del sistema (Llavero de macOS, Administrador de credenciales de Windows, Secret Service en Linux), en lugar de Android Keystore.
- Sincronización periódica cada 30 minutos mientras la app está abierta (en lugar de WorkManager), y al volver a la ventana.
- La copia de seguridad previa a conectar una cuenta se guarda en la carpeta Descargas/MisNotas del usuario.
- Imágenes elegidas con el diálogo de ficheros del sistema; se siguen comprimiendo a WebP respetando la orientación EXIF.
- Fechas relativas ("Hace 5 min", "Ayer") con textos propios en los cinco idiomas.
