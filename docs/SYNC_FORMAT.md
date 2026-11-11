# Formato de sincronización (v2)

Este documento describe cómo guarda Mis Notas los datos en Nextcloud, para quien quiera escribir otro cliente o herramientas de exportación.

## Carpeta remota

Por defecto: `https://<servidor>/remote.php/dav/files/<usuario>/MisNotas/`. Todos los ficheros están en el mismo nivel.

| Fichero | Contenido |
|---------|-----------|
| `keyinfo.json` | Parámetros de derivación y verificador de la contraseña (sin cifrar) |
| `note_<uuid>.json` | Una nota (o su lápida) |
| `folder_<uuid>.json` | Una carpeta (o su lápida) |
| `att_<uuid>.bin` | Un adjunto binario cifrado (imagen WebP) |

## keyinfo.json

```json
{
  "format": "misnotas-keyinfo",
  "version": 1,
  "kdf": "PBKDF2WithHmacSHA256",
  "iterations": 600000,
  "salt": "<base64, 16 bytes>",
  "check": "<base64 AES-GCM de 'misnotas-key-check-v1' con AAD 'keyinfo'>"
}
```

Clave = `PBKDF2-HMAC-SHA256(contraseña, salt, iterations, 256 bits)`.

## Cifrado

`AES-256-GCM`; bloque = `IV (12 bytes) ‖ texto cifrado ‖ etiqueta (16 bytes)`, codificado en Base64 dentro de los JSON. El AAD es la cadena UTF-8 `<tipo>:<id>`.

## Nota

```json
{ "format": "misnotas", "version": 2, "type": "note", "id": "<uuid>", "payload": "<base64>" }
```

`payload` descifrado (AAD `note:<uuid>`):

```json
{
  "folderId": "<uuid> | null",
  "title": "Texto",
  "content": "Markdown",
  "isPinned": false,
  "color": "DEFAULT | YELLOW | BLUE | GREEN | ORANGE | RED | PURPLE | TEAL | INDIGO",
  "updatedAt": 1730000000000,
  "isTrashed": false
}
```

Las imágenes se referencian en el Markdown como `![texto](attachment://<uuid>)`.

## Carpeta

Igual que la nota con `"type": "folder"` y AAD `folder:<uuid>`. Payload:

```json
{ "parentId": "<uuid> | null", "name": "Trabajo", "color": "INDIGO", "updatedAt": 1730000000000 }
```

## Lápidas

Cuando una nota o carpeta se borra definitivamente, su fichero se sustituye por:

```json
{ "format": "misnotas", "version": 2, "type": "note", "id": "<uuid>", "deleted": true, "deletedAt": 1730000000000 }
```

Las lápidas no se eliminan, para que los dispositivos que estaban sin conexión también se enteren del borrado.

## Adjuntos

`att_<uuid>.bin` = bytes en bruto `IV ‖ texto cifrado ‖ etiqueta` (sin Base64), AAD `attachment:<uuid>`. Son inmutables.

## Reglas del cliente

1. Antes de sincronizar, descarga `keyinfo.json` y verifica la clave. Si falta o no coincide, aborta.
2. Lista la carpeta con `PROPFIND Depth: 1` y compara cada `getetag` con el último ETag conocido.
3. Descarga solo los ficheros cuyo ETag cambió y fusiona:
   - Sin cambios locales → se acepta la versión remota.
   - Con cambios locales y contenido distinto → gana la de `updatedAt` más reciente y la otra se guarda como nota nueva con el sufijo " (conflicto)".
   - Lápida → se borra en local, salvo que haya cambios locales sin subir.
4. Sube con `If-Match: "<etag>"` (o `If-None-Match: *` si es nuevo). Si el servidor responde `412`, vuelve al paso 2.
5. Un fichero conocido que desaparece del servidor se considera borrado.

## Compatibilidad con v1

Los ficheros de la v1.0 no tienen el campo `format` y cifran por separado `title`, `content` (notas) y `name` (carpetas), sin AAD, con 65.536 iteraciones y un salt que solo existía en el dispositivo. La v2 los lee y los reescribe en el formato nuevo en la siguiente subida.
