# Seguridad

## Informar de una vulnerabilidad

**No abras una incidencia pública** para fallos de seguridad. Usa el aviso privado de GitHub:
pestaña **Security → Report a vulnerability** del repositorio
([enlace directo](https://github.com/ferlagod/mis_notas_desktop/security/advisories/new)).

Indica la versión de la app, los pasos para reproducirlo y el impacto. Intentaré responder en un plazo de 7 días.

Este es un proyecto personal mantenido en el tiempo libre y **no ha pasado una auditoría de seguridad independiente**.

## Modelo de amenazas

Mis Notas protege el contenido de tus notas frente a **quien controla o accede al servidor** (el administrador de Nextcloud, un atacante que lo comprometa, o quien intercepte el tráfico).

| Protegido | No protegido |
|-----------|--------------|
| Título, texto, imágenes, carpetas, colores, fechas, si está fijada o en la papelera | Número de notas y carpetas, tamaño aproximado de cada fichero |
| Integridad: el servidor no puede modificar, mezclar ni intercambiar ficheros sin que la app lo detecte | Cuándo se crea, modifica o borra cada fichero (metadatos de WebDAV) |
| Credenciales y clave en el ordenador (fichero cifrado con una clave en el llavero del sistema) | Una sesión de usuario abierta y comprometida (malware, alguien con acceso a tu cuenta) |
| | Que el servidor borre ficheros o devuelva versiones antiguas (*rollback*) |

En el ordenador la base de datos de notas **no está cifrada por la app**: se guarda en la carpeta de datos del usuario y depende de los permisos del sistema y del cifrado de disco (FileVault, BitLocker, LUKS). Cualquier programa que se ejecute con tu usuario puede leerla.

## Diseño criptográfico

### Derivación de clave

- `PBKDF2WithHmacSHA256`, 600.000 iteraciones (las cuentas creadas con la v1.0 conservan 65.536), salt aleatorio de 16 bytes, clave de 256 bits.
- El salt y el número de iteraciones se publican en `keyinfo.json` dentro de la carpeta de Nextcloud, para que cualquier dispositivo derive la misma clave.
- `keyinfo.json` contiene también un **verificador**: la cadena fija `misnotas-key-check-v1` cifrada con la clave (AAD `keyinfo`). Sirve para comprobar la contraseña sin descargar notas y evita que un dispositivo con una contraseña equivocada suba datos ilegibles.

### Cifrado

- `AES/GCM/NoPadding`, clave de 256 bits, IV aleatorio de 12 bytes por operación, etiqueta de 128 bits.
- Formato binario: `IV (12) ‖ texto cifrado ‖ etiqueta (16)`; en JSON va en Base64.
- **Datos asociados (AAD)**: `note:<id>`, `folder:<id>` o `attachment:<id>`. Un bloque cifrado solo se descifra en el fichero al que pertenece.

### Almacenamiento local

- Credenciales de Nextcloud, salt, verificador y clave derivada: fichero `secure.dat` cifrado con AES-256-GCM. Su clave (256 bits aleatorios) se guarda en el llavero del sistema: Llavero de macOS, Administrador de credenciales de Windows o Secret Service (GNOME Keyring, KWallet) en Linux.
- Si el sistema no tiene llavero disponible (p. ej. un Linux mínimo), la clave se guarda en `secure.key` con permisos solo para el usuario y se avisa en el registro. En ese caso el fichero cifrado solo protege frente a copias parciales de la carpeta.
- Bloqueo de la app: se guarda un hash PBKDF2-HMAC-SHA256 (210.000 iteraciones, salt aleatorio) de la contraseña de la app, nunca la contraseña. Es una barrera frente a miradas curiosas con la sesión abierta, no un cifrado de las notas.
- "Bloquear" borra la clave derivada del dispositivo; hace falta la contraseña maestra para volver a abrir la app con la cuenta conectada.

### Transporte

- HTTPS con validación de certificados. Se confía en los certificados raíz del Java incluido en la app **y** en los del sistema (Llavero de macOS, almacén de Windows, `/etc/ssl` en Linux), así que una autoridad propia instalada en el sistema funciona. La app no acepta tráfico HTTP sin cifrar ni certificados que no estén en ninguno de esos almacenes.
- Autenticación con *contraseña de aplicación* de Nextcloud (HTTP Basic sobre TLS).

## Limitaciones conocidas

- No se puede cambiar la contraseña maestra ni rotar la clave.
- No hay protección contra *rollback*: un servidor malicioso podría servir una versión anterior, válida, de una nota.
- Las lápidas de borrado (`"deleted": true`) no van cifradas: revelan el identificador y la fecha de borrado de una nota, pero no su contenido.
