# CLAUDE.md: memoria del proyecto NightWatch Protocol

Léelo completo antes de tocar nada. Si algo de aquí contradice el código, **no lo arregles por tu cuenta: repórtalo**.

## 1. Qué es y en qué estado está

- App Android para aprender ciberseguridad. La persona se registra o inicia sesión (también con Google), hace un quiz de
  nivelación (BAJO, MEDIO, ALTO), recorre una ruta de aprendizaje (lecciones, laboratorios y examen final) y administra su
  perfil y sus ajustes. Kotlin + Jetpack Compose + **Supabase**, con el diseño de Figma del proyecto y los patrones del
  curso *Android Basics with Compose*.
- **Módulos 1, 2 y 3 terminados** (autenticación, onboarding, quiz de nivelación, perfil y ajustes; ruta de aprendizaje,
  lecciones, laboratorios, examen final, racha e insignias). El módulo 3 está en la rama `modulo-3`, listo para abrir su
  Pull Request.
- **v2.1.0** (`versionName` 2.1.0, `versionCode` 4). La migración de Firebase a Supabase fue la v2.0.0: **Firebase ya no
  existe** (no hay código, dependencias ni plugin) y `google-services.json` no se usa. Historial de la migración:
  `docs/superpowers/reports/migracion-supabase.md`.
- **Módulo 3, lo que hay que saber:**
  - **Navegación:** `Home(tab)` (barra inferior con Inicio, Ruta y Labs; Perfil abre el Menú de usuario; Social atenuada con
    "Próximamente") y, a pantalla completa y sin barra, `Lesson`, `Lab`, `LabReport`, `ExamIntro`, `Exam` y `ExamResult`.
  - **Contenido** en JSON dentro de `app/src/main/assets/` (`learning_lessons.json`, `learning_labs.json`,
    `learning_exam.json`), leído por `LearningContentRepository`. Cambiarlo exige una versión nueva de la app.
  - **Progreso derivado:** se guarda solo en `lesson_progress`, `lab_progress` y `exam_attempts` (migración `0007`,
    **aplicada**). Las islas, el desbloqueo en orden, el contador "N de 7", la "Lección diaria", la racha y las insignias se
    **calculan** de ahí (`data/learning`: `deriveLearningProgress`, `Streak`, `Badges`, `ProgressSummaryProvider`); nada de eso
    se almacena. Las pantallas lo releen al volver (`ON_RESUME`).
  - **Funciones puras con pruebas:** `LabTerminal` (terminal simulada de los labs), `gradeExam` (corrección del examen,
    5 o más aciertos aprueban), `computeStreak` y `evaluateBadges`.
  - **Textos** en `app/src/main/res/values/strings_aprendizaje.xml` (prefijo `learning_`).
  - **Aprobar el examen NO cambia el nivel y nunca escribe en `placement`** ni en otra tabla que no sea `exam_attempts`.
  - **Reglas de la racha y de las insignias:** sección 15 de la spec del módulo 3. Un día de práctica es un día (zona
    horaria del dispositivo) con una lección vista, un lab completado o un intento de examen; la racha actual termina hoy o
    ayer; las insignias usan la **mejor** racha y datos históricos, así que nunca se vuelven a bloquear. Cambiar la
    condición de una insignia es editar una línea de `BadgeCatalog` (`data/learning/Badges.kt`).
  - **Pruebas:** **951** pruebas unitarias (0 fallos) al cerrar el módulo; el recuento y el detalle por etapa están en
    `docs/superpowers/reports/modulo-3-diagnostico.md`.
- **Pendiente para el módulo 4:** Social (17 pantallas de Figma, hoy solo la pestaña atenuada), una posible migración `0008`
  (tabla de actividad por día para que repetir cuente en la racha), la corrección del examen en el servidor si hay ranking o
  XP y las notificaciones push. Ver `docs/superpowers/specs/2026-10-18-modulo-4-social-analisis.md`.

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
- **Guardados con reintento (módulo 3):** el informe del lab y el resultado del examen se muestran aunque falle guardar, con
  un aviso y "Reintentar guardado"; un doble toque no guarda dos veces. Sigue ese patrón (`ui/labreport`, `ui/examresult`).
  Lección y lab se registran con un `upsert` que ignora duplicados (idempotente); un intento de examen es un `INSERT`.
- **Pruebas:** `MainDispatcherRule` (en `src/test`) y Turbine para ViewModels y eventos; relojes y alcances inyectables
  (tiempo virtual para las cuentas regresivas; `Clock` para la racha). Usa `assertTrue`/`assertFalse`/`assertEquals`;
  **nunca `assert()` suelto**. Crea los ViewModels **dentro** de la prueba (o con `by lazy`), no en campos de la clase: la
  regla del dispatcher se aplica después de construir los campos.
- **Textos de la interfaz** en español con tildes correctas. Los del módulo 3 están en `strings_aprendizaje.xml` (prefijo
  `learning_`); los de los módulos 1 y 2 siguen en el código (pendiente, ver sección 8).
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
| `0007_progreso_aprendizaje.sql` | Tablas `lesson_progress`, `lab_progress` y `exam_attempts` (`passed` generada: `correct_count >= 5`), RLS del dueño y `GRANT` por columna (solo `SELECT` e `INSERT`). **Aplicada y verificada a mano.** No toca `placement` ni tablas anteriores |

- **Una migración ya aplicada NO se edita.** Cualquier cambio va en una migración nueva con el siguiente número (`0008`...).
  `0007` conserva en su encabezado la palabra "BORRADOR" y un comentario sobre la racha fija: es lo que se ejecutó.
- **RLS en todas las tablas, solo del dueño** (`user_id = auth.uid()`); `anon` no tiene acceso a nada. **`GRANT` explícito**
  a `authenticated` (el proyecto tiene "Automatically expose new tables" desactivado y "Automatic RLS" activado). Mínimo
  privilegio por columna; funciones `SECURITY DEFINER` con `search_path` vacío.
- **Security Advisor:** las advertencias aceptadas y su motivo están en la sección "Advertencias aceptadas del Security
  Advisor" de `docs/superpowers/supabase-configuracion.md`. Una advertencia nueva se corrige o se documenta ahí antes de
  fusionar a `main`.
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
- **Regla actual:** el módulo 3 vive en la rama `modulo-3`, con **un commit por etapa o por tema**, y entra a `main` por un
  Pull Request (el borrador de su descripción está en el informe de cierre). El módulo 4 irá en su propia rama.
  **Claude Code NUNCA hace push.** El push de la rama lo hace el usuario a mano. **Jamás se commitea ni se sube a `main`
  directamente:** todo entra a `main` por Pull Request con 1 aprobación.
- Nunca `git push --force`. `local.properties` nunca se sube.

## 7. Reglas para Claude Code

1. Trabaja siempre en una **rama explícita** (hoy `modulo-3`); verifica con `git branch` antes de empezar. Si estás en
   `main`, detente y avisa. **Nunca toques `main`.**
2. **Sin push, sin `--force`, sin tocar ramas remotas.** Sin tags ni releases.
3. `git add` **por nombre de archivo** (nunca `.` ni `-A`); revisa `git status` antes de cada commit.
4. **Un commit por etapa o por tema**, en español, terminando con `Co-Authored-By: Claude <noreply@anthropic.com>`.
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
- **Sin modo sin conexión:** una escritura sin internet falla como "sin conexión" (el informe del lab y el resultado del
  examen ofrecen "Reintentar guardado"). Solo el género, la fecha y la foto conservan copia local hasta el siguiente guardado.
- **Revocación de sesión:** `signOut(OTHERS)` invalida los *refresh tokens*, pero el access token ya emitido vale hasta
  que expira (~1 h). Cerrar un dispositivo concreto sigue siendo solo una marca cooperativa (`revoked`).
- **Enumeración de cuentas** (`account_status`): compromiso aceptado por requisito del curso; el Security Advisor la marca.
- **Tokens de sesión sin cifrar** (SharedPreferences de supabase-kt, fuera de respaldos): mejora pendiente (F-05).
- "Incorrecto" y "expirado" son iguales para el servidor; la app decide "expiró" con su reloj (600 s).
- **Remitente `@gmail.com` por Brevo:** puede caer en spam; límite de unos 300 correos al día, compartido con la Edge Function.
- **Módulo 3:** el examen se corrige en el teléfono (un cliente modificado podría falsear un intento; sin ranking no importa);
  el desbloqueo en orden y el acceso al examen son reglas de la app, no de la base de datos; **repetir una lección o un lab ya
  registrado no suma un día a la racha** (haría falta una tabla de actividad, `0008`); un intento de examen cuyo guardado
  expiró pero llegó puede quedar duplicado al reintentar; "Lección diaria" no menciona un lab pendiente (decisión Q3).
- **Datos fijos o solo visuales:** Apariencia, Idioma, Ayuda y Acerca de, y los interruptores de notificaciones de "Logros" y
  "Nuevos laboratorios" (sin efecto: necesitan push). La racha y las insignias **ya son reales**.

Pendiente (IDs de `docs/superpowers/reports/revision-codigo-experimento-supabase.md`):
- **Textos en `strings.xml`** (F-09): los del módulo 3 ya están en `strings_aprendizaje.xml`; los de los módulos 1 y 2
  siguen en el código y migrarlos es una tarea de todo el proyecto.
- F-03: no hay pruebas automáticas de la Edge Function, de `0006`, de `0007` ni de las llamadas reales a Supabase; se
  verifican a mano.
- F-06: la Edge Function no tiene un límite global de envíos. F-08: el flujo de Google sigue duplicado en Login y Registro.
- F-12: el aviso de "Correo actualizado" viaja como texto en la ruta. F-15: pruebas de repositorio con `Proxy` sobre `Auth`
  y sin pruebas de Compose de las pantallas nuevas. F-23 y F-25: ver el informe. F-24 pedía una migración "0007": ese
  número ya lo usa el módulo 3, así que sería `0008`.
- Residuo del avatar en Base64 (`RemoteProfile.avatarBase64`); `0001`, `0005` y `0007` conservan comentarios que ya no
  corresponden (Firebase, racha fija; no se editan: ya se ejecutaron).
- La comparación visual con Figma de las pantallas del módulo 3 y las comprobaciones contra el servidor real las hace el
  grupo con la lista y el guion de `docs/superpowers/reports/modulo-3-diagnostico.md`.

## 9. Documentos para leer primero

1. `README.md`: tecnologías, puesta en marcha, responsables, límites del plan gratuito y cómo trabajamos.
2. `docs/superpowers/supabase-configuracion.md`: ajustes del panel, migraciones, flujos de autenticación y limitaciones.
3. `docs/superpowers/specs/2026-10-18-modulo-3-design.md`: la spec del módulo 3 con las decisiones del grupo (P1 a P15, Q1 a
   Q13), el estado final y la sección 15 (racha e insignias).
4. `docs/superpowers/reports/modulo-3-diagnostico.md`: cierre del módulo 3 (pantallas, diferencias con Figma, pruebas,
   lista de verificación visual, guion de prueba manual y borrador del Pull Request).
5. `docs/superpowers/reports/migracion-supabase.md`: decisiones y problemas de la migración.
6. `docs/superpowers/reports/revision-codigo-experimento-supabase.md`: hallazgos F-01 a F-27 y su estado (sección 10).
7. `supabase/migrations/` y `supabase/functions/send-recovery-email-code/README.md`.
8. `docs/superpowers/specs/` y `plans/` de los módulos 1 y 2 y `reports/modulo-2-diagnostico.md`: históricos (nombran Firebase).
   `2026-10-18-modulo-4-social-analisis.md`: análisis previo de Social (módulo 4).
