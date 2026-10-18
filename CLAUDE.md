# CLAUDE.md: memoria del proyecto NightWatch Protocol

Léelo completo antes de tocar nada. Si algo de aquí contradice el código, **no lo arregles por tu cuenta: repórtalo**.

## 1. Qué es y en qué estado está

- App Android para aprender ciberseguridad. La persona se registra o inicia sesión (también con Google), hace un quiz de
  nivelación (BAJO, MEDIO, ALTO) y administra su perfil y sus ajustes. Kotlin + Jetpack Compose + **Supabase**, con el
  diseño de Figma del proyecto y los patrones del curso *Android Basics with Compose*.
- **Módulos 1 y 2 terminados** (autenticación, onboarding, quiz de nivelación, perfil y ajustes).
- **v2.0.0** (`versionName` 2.0.0, `versionCode` 3): la app migró de Firebase a Supabase. **Firebase ya no existe**: no hay
  código, dependencias ni plugin, y `google-services.json` ya no se usa. El historial de la migración está en
  `docs/superpowers/reports/migracion-supabase.md`.
- **Siguiente: módulo 3**, en la rama `modulo-3` (se renombrará con `git branch -m` cuando se defina el nombre del módulo).

## 2. Stack y versiones

| Qué | Versión / dato |
|---|---|
| Kotlin / AGP / Gradle | 2.2.10 / 9.3.3 / 9.5.0 (wrapper) |
| Compose | BOM 2026.02.01, Material 3 |
| Navegación | Navigation Compose 2.9.7, rutas `@Serializable` |
| Backend | **supabase-kt 3.2.4** y **Ktor 3.3.0** (Auth, Postgres, Storage, Edge Functions) |
| Google | Credential Manager 1.5.0 + `googleid` 1.1.1 (ID token con *nonce*) |
| Otros | DataStore 1.2.1, WorkManager 2.12.0, `androidx.biometric` 1.1.0, kotlinx.serialization 1.8.0 |
| Pruebas | JUnit 4, Turbine 1.2.1, `kotlinx-coroutines-test`, Compose UI Test |
| SDK | `minSdk` **26**, `compileSdk` y `targetSdk` 37; el código se compila para Java 11 |
| JDK | Gradle corre con **JDK 25** (lo fija `gradle/gradle-daemon-jvm.properties`; el resolver *foojay* puede descargarlo) |

- **Por qué supabase-kt 3.2.4 y no la 3.8.0:** la 3.8.0 exige un Kotlin más nuevo que el 2.2.10 del proyecto y no compila.
  `minSdk` 26 también lo exige supabase-kt.
- **No actualices AGP, Gradle, Kotlin, supabase-kt ni Ktor** (ni con el Upgrade Assistant) sin acuerdo previo del equipo:
  subirlos es una tarea aparte.

## 3. Arquitectura y convenciones

- **MVVM:** pantalla con estado + `ViewModel` con `StateFlow<UiState>`; eventos de una sola vez por `Channel`
  (`receiveAsFlow`); navegación con rutas tipadas `@Serializable`.
- **DI manual** en `di/AppContainer.kt` (sin Hilt). Los repositorios tienen interfaz y se sustituyen por *fakes* en las pruebas.
- **Las pantallas y los ViewModels NO importan Supabase ni `BuildConfig`.** Solo `AppContainer` y las clases `Supabase*`
  (carpeta `data/supabase`) conocen la librería; `BuildConfig` solo se lee en `di/`. Los errores se mapean a tipos del
  dominio (`AuthError`, `DataAccessException` en `data/common`) y la UI los traduce a texto. Nunca guardes ni muestres la
  excepción de la librería (su mensaje puede incluir cabeceras con el token).
- **Un solo flujo de código de 6 dígitos** (`ui/verifycode`: `VerifyCodeViewModel`, `VerifyCodeScreen`, `CodeFlow`) sirve
  para el registro, el cambio de correo, la recuperación de contraseña y el correo de recuperación. **Reutilízalo; no
  crees otro composable de verificación.**
- Reutiliza los componentes compartidos de `ui/components` (`NightWatchTextField`, `SettingsRow`, `ProfileTopBar`,
  `ProfileButton`, `NightWatchScreenBackground`, etc.) antes de crear uno nuevo. Colores y tipografía vienen de `ui/theme`.
- La fila de cada usuario se identifica siempre por la sesión, nunca por un dato que venga de la pantalla.
- **Pruebas:** `MainDispatcherRule` (en `src/test`) y Turbine para ViewModels y eventos; relojes y alcances inyectables
  (tiempo virtual para las cuentas regresivas). Usa `assertTrue`/`assertFalse`/`assertEquals`; **nunca `assert()` suelto**.
- Textos de la interfaz en español con tildes correctas. Hoy ningún texto está en `strings.xml` (pendiente, ver sección 8).
- Archivos con herramientas de edición; evita scripts de shell o Python con comillas (ya se perdieron comillas simples así).

## 4. Supabase

Todo el servidor vive en `supabase/` y **se despliega a mano** desde el panel (no se usa la CLI de Supabase).

| Migración | Qué hace |
|---|---|
| `0001_esquema_inicial.sql` | Tablas `placement`, `profile`, `devices`, RLS, políticas y `GRANT` explícitos |
| `0002_minimo_privilegio.sql` | Quita a `authenticated` `TRUNCATE`, `REFERENCES` y `TRIGGER` |
| `0003_revoca_rls_auto_enable.sql` | Revoca `EXECUTE` de `rls_auto_enable()` |
| `0004_storage_avatares.sql` | Bucket privado `avatars` y sus políticas en `storage.objects` |
| `0005_estado_de_cuenta.sql` | Función `account_status(p_email)` para errores de login y registro individualizados |
| `0006_verificacion_correo_recuperacion.sql` | Tabla `recovery_email_codes`, cierra `profile.recovery_email` al cliente y crea `verify_recovery_email_code(p_code)` |

- **Una migración ya aplicada NO se edita.** Cualquier cambio va en una migración nueva con el siguiente número (`0007`...).
- **RLS en todas las tablas, solo del dueño** (`user_id = auth.uid()`); `anon` no tiene acceso a nada. **`GRANT` explícito**
  a `authenticated` (el proyecto tiene "Automatically expose new tables" desactivado y "Automatic RLS" activado). Mínimo
  privilegio por columna; funciones `SECURITY DEFINER` con `search_path` vacío.
- **Edge Function `send-recovery-email-code`** (`supabase/functions/`, Deno/TypeScript): envía el código del correo de
  recuperación por la API de Brevo. Su `README.md` explica el despliegue a mano. Los correos de Auth salen por el SMTP de
  Brevo; los ajustes del panel (SMTP, plantillas con `{{ .Token }}`, OTP de 6 dígitos y 600 s, proveedor Google, *Confirm
  email*, *Allow manual linking*) están en `docs/superpowers/supabase-configuracion.md` y no los cambia el código.
- **Claude Code NO se conecta a Supabase ni a Brevo y NO ejecuta SQL.** Escribes o revisas el SQL en archivos del repo; el
  usuario lo ejecuta a mano en el *SQL Editor* (y despliega la función a mano). Nunca pidas ni uses claves de esos servicios.

## 5. Configuración local

- `local.properties` (en la raíz, **no versionado**) lee tres claves: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` y
  `GOOGLE_WEB_CLIENT_ID`. La plantilla es `local.properties.example`. Si falta una, la app compila pero **se detiene al
  iniciar** con un mensaje que dice cuál (`appConfigError` en `di/AppConfig.kt`).
- **No leas `local.properties`, no imprimas sus valores y no lo commitees.** Nunca pongas valores, URLs de proyectos,
  claves, correos personales ni rutas de la computadora en un archivo versionado.
- Solo la clave *publishable* va en la app. La `secret`/`service_role` de Supabase y las claves de Brevo viven solo en el
  panel; no van en la app, en el repo ni en un chat.
- `google-services.json` **ya no se usa** (sigue ignorado por si alguien aún lo tiene en `app/`).
- Lo específico de una computadora (huella SHA-1, rutas, usuario del sistema) va en `CLAUDE.local.md`, que está en el
  `.gitignore`, nunca aquí.

## 6. Flujo de git

- **Una rama por tarea**, PR hacia `main` con **1 aprobación** de otro integrante y **"Create a merge commit"**.
- Es un **acuerdo de equipo, no una protección técnica**: el plan gratuito de GitHub no permite proteger ramas en repos
  privados, así que nada impide subir a `main`. Depende de que se cumpla.
- **Regla actual (módulo 3):** se trabaja en la rama `modulo-3` (o la que la reemplace) con **un commit por etapa**.
  **Claude Code NUNCA hace push.** El push de la rama lo hace el usuario a mano al terminar cada etapa. **Jamás se
  commitea ni se sube a `main` directamente:** todo entra a `main` por Pull Request con 1 aprobación.
- Nunca `git push --force`. `local.properties` nunca se sube.

## 7. Reglas para Claude Code

1. Trabaja siempre en una **rama explícita** (hoy `modulo-3`); verifica con `git branch` antes de empezar. Si estás en
   `main`, detente y avisa. **Nunca toques `main`.**
2. **Sin push, sin `--force`, sin tocar ramas remotas.**
3. `git add` **por nombre de archivo** (nunca `.` ni `-A`); revisa `git status` antes de cada commit.
4. **Un commit por etapa**, en español, terminando con `Co-Authored-By: Claude <noreply@anthropic.com>`.
5. **Antes de cada commit valida:** `./gradlew assembleDebug testDebugUnitTest lintDebug` (y
   `compileDebugAndroidTestKotlin` si tocaste pruebas instrumentadas). `lintDebug` debe quedar en 0 errores. Todas las
   pruebas existentes deben seguir pasando; no las borres para "arreglar" un fallo.
6. No toques lo que la tarea no pide ni módulos terminados, salvo lo mínimo para conectar el cambio.
7. Ante secretos en cualquier archivo, **detente y avisa sin imprimirlos**.
8. **Diagnóstico final siempre:** qué cambió, qué no pudiste verificar (nada se ve en un teléfono desde aquí), cuántas
   pruebas pasan, estado de compilación y confirmación de que no hubo push ni se tocó `main`.

## 8. Limitaciones aceptadas y pendientes

Aceptado por decisión del equipo:
- **El plan gratuito de Supabase pausa el proyecto tras 7 días sin actividad:** la app y el login dejan de funcionar hasta
  pulsar *Restore project* (se restaura durante unos 90 días).
- **Sin modo sin conexión:** una escritura sin internet falla como "sin conexión". Solo el género, la fecha y la foto
  conservan copia local hasta el siguiente guardado.
- **Revocación de sesión:** `signOut(OTHERS)` invalida los *refresh tokens*, pero el access token ya emitido vale hasta
  que expira (~1 h). Cerrar un dispositivo concreto sigue siendo solo una marca cooperativa (`revoked`).
- **Enumeración de cuentas** (`account_status`): compromiso aceptado por requisito del curso; el Security Advisor la marca.
- **Tokens de sesión sin cifrar** (SharedPreferences de supabase-kt, fuera de respaldos): mejora pendiente (F-05).
- "Incorrecto" y "expirado" son iguales para el servidor; la app decide "expiró" con su reloj (600 s).
- **Remitente `@gmail.com` por Brevo:** puede caer en spam; límite de unos 300 correos al día, compartido con la Edge Function.
- **Datos fijos o solo visuales:** racha (15), insignias, Apariencia, Idioma, Ayuda y Acerca de.

Pendiente (IDs de `docs/superpowers/reports/revision-codigo-experimento-supabase.md`):
- **Textos en `strings.xml`** (F-09): hoy ninguno está en recursos; es una tarea de todo el proyecto.
- F-03: no hay pruebas automáticas de la Edge Function, de `0006` ni de las llamadas reales; se verifican a mano.
- F-06: la Edge Function no tiene un límite global de envíos. F-08: el flujo de Google sigue duplicado en Login y Registro.
- F-12: el aviso de "Correo actualizado" viaja como texto en la ruta. F-15: pruebas de repositorio con `Proxy` sobre `Auth`
  y sin pruebas de Compose de las pantallas nuevas. F-23, F-24 (requiere `0007`) y F-25: ver el informe.
- Residuo del avatar en Base64 (`RemoteProfile.avatarBase64`); `0001` y `0005` conservan comentarios que mencionan Firebase
  (no se editan: ya se ejecutaron).

## 9. Documentos para leer primero

1. `README.md`: tecnologías, puesta en marcha, responsables, límites del plan gratuito y cómo trabajamos.
2. `docs/superpowers/supabase-configuracion.md`: ajustes del panel, flujos de autenticación y limitaciones.
3. `docs/superpowers/reports/migracion-supabase.md`: decisiones y problemas de la migración.
4. `docs/superpowers/reports/revision-codigo-experimento-supabase.md`: hallazgos F-01 a F-27 y su estado (sección 10).
5. `supabase/migrations/` y `supabase/functions/send-recovery-email-code/README.md`.
6. `docs/superpowers/specs/` y `plans/` de los módulos 1 y 2 y `reports/modulo-2-diagnostico.md`: históricos (nombran Firebase).
