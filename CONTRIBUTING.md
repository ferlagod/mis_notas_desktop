# Cómo contribuir

¡Gracias por tu interés en Mis Notas para escritorio!

## Incidencias

- **Fallos**: indica la versión de la app y tu sistema operativo (Windows, macOS o la distribución de Linux), los pasos para reproducirlo y qué esperabas que pasara. Si es un error de sincronización, incluye el mensaje que aparece en Ajustes (sin contraseñas ni la URL de tu servidor si no quieres compartirla).
- **Ideas**: describe el problema que resolverían antes de proponer una solución concreta.
- **Vulnerabilidades**: no las publiques; sigue [SECURITY.md](SECURITY.md).

## Pull requests

1. Haz un *fork* y crea una rama desde `main`.
2. Mantén los cambios pequeños y centrados en un tema.
3. Antes de enviar, comprueba que todo pasa:
   ```bash
   ./gradlew :app:test packageDistributionForCurrentOS
   ```
4. Si cambias la base de datos, sube `version` en `AppDatabase`, añade una `Migration` y versiona el JSON que se genera en `app/schemas/`. Nunca uses migraciones destructivas: se perderían notas.
5. Si cambias el formato remoto, actualiza [docs/SYNC_FORMAT.md](docs/SYNC_FORMAT.md) y mantén la lectura de los formatos antiguos.
6. Los cambios en `data/sync` o `security` deben venir con tests.
7. El formato de sincronización y el esquema de la base de datos son los mismos que en [la app de Android](https://github.com/ferlagod/mis_notas). Si cambian aquí, tienen que cambiar también allí (y al revés), o los dispositivos dejarán de entenderse.

Al contribuir aceptas que tu código se publique bajo la licencia **GPL v3 o posterior**.

## Estilo

- Kotlin con el estilo oficial (`kotlin.code.style=official`).
- Comentarios del código en español.
- Ningún texto visible escrito en el código: todo va en `app/src/main/resources/i18n/values*/strings.xml` (mismo formato que en Android) y se usa con `stringResource(R.string.…)`. Si añades un texto, añádelo en **los cinco** idiomas (`values/` es inglés, más `values-es`, `values-fr`, `values-de` y `values-it`). El test `StringsTest` falla si falta alguna traducción o no coinciden los marcadores.
- Solo dependencias libres, sin rastreadores ni servicios de terceros.
- Lo que dependa del sistema operativo va en `platform/` y debe funcionar en Windows, macOS y Linux.

## Traducciones

- Para corregir una traducción, edita el `strings.xml` de ese idioma.
- Para añadir un idioma nuevo, crea `app/src/main/resources/i18n/values-<código>/strings.xml` con todas las claves, añade la entrada en `AppLanguage` (`util/LanguageManager.kt`) y, si sus plurales no siguen la regla "uno / otros", ajústala en `i18n/Strings.kt`.
- Usa `plurals` para textos con números y respeta los marcadores (`%1$s`, `%d`).

## Publicar una versión

1. Actualiza `appVersion` en `app/build.gradle.kts` (formato `MAJOR.MINOR.PATCH`, con `MAJOR` mayor que 0, como exige macOS) y añade la entrada en [CHANGELOG.md](CHANGELOG.md).
2. Crea y sube una etiqueta: `git tag v1.0.0 && git push origin v1.0.0`.
3. El flujo [`desktop.yml`](.github/workflows/desktop.yml) pasa los tests, genera los instaladores en cada sistema (`.dmg` para Apple Silicon e Intel, `.msi`/`.exe`, `.deb`/`.rpm`) y crea un **borrador** de *release* con todos ellos. Revísalo y publícalo.

### Firma de los instaladores (opcional)

Sin firmar, macOS pide abrir la app con clic derecho → Abrir la primera vez y Windows muestra el aviso de SmartScreen. Para firmar:

- **macOS**: hace falta un certificado *Developer ID Application* y notarizar. Compose Multiplatform lo admite con `macOS { signing { … }; notarization { … } }` en `app/build.gradle.kts` (ver la [documentación](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Signing_and_notarization_on_macOS/README.md)). Guarda las credenciales como *secrets* del repositorio, nunca en el código.
- **Windows**: firma el `.msi`/`.exe` con `signtool` y un certificado de firma de código tras generarlo.
