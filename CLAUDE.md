# CLAUDE.md: memoria del proyecto NightWatch Protocol

Léelo completo antes de tocar nada. Si algo de aquí contradice el código, **no lo arregles por tu cuenta: repórtalo**.

## 1. Qué es y en qué estado está

- App Android para aprender ciberseguridad. La persona se registra o inicia sesión (también con Google), hace un quiz de
  nivelación (BAJO, MEDIO, ALTO), recorre una ruta de aprendizaje (lecciones, laboratorios y examen final) y administra su
  perfil y sus ajustes. Kotlin + Jetpack Compose + **Supabase**, con el diseño de Figma del proyecto y los patrones del
  curso *Android Basics with Compose*.
- **Módulos 1, 2, 3 y 4 terminados** (autenticación, onboarding, quiz de nivelación, perfil y ajustes; ruta de aprendizaje,
  lecciones, laboratorios, examen final, racha e insignias; Social: amigos, racha en pareja, batallas, fotos, reportar y bloquear).
  Los módulos 1 a 4 ya están en `main` (el módulo 4 y sus mejoras del perfil social, PR #10, merge `3db30b4`). **Módulo 5 = personalización: terminado**
  (5A tema y 5B idioma, entrega del curso de Android v2.4.0). **Después: módulo de IA "Prepárate con CyberMind" (v2.5.0)**, ver la sección 10.
- **Versión actual: v2.5.0** (módulo de IA; el tag y el Release los publica el usuario). **v2.4.0** (`versionName` 2.4.0, `versionCode` 7) es la entrega del curso de Android (módulos 1 a 5).
  Nota: la versión de la app (versionName 2.5.0,versionCode 8) está en app/build.gradle.kts.
- **v2.3.0** (`versionName` 2.3.0, `versionCode` 6; cierre de la parte 5A, histórico). Antes: v2.2.0 (`versionCode` 5; la v2.1.0 fue el módulo 3). La migración de Firebase a Supabase fue la v2.0.0: **Firebase ya no
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
- **Módulo 4 (Social), terminado y fusionado a `main` (v2.2.0).** Scripts `0008` a `0012` **aplicados y verificados**
  (guion de `0008`: 219 de 219; guion de `0011`: 187 de 187; Security Advisor: 0 errores; al cerrar `0011` eran 10 advertencias y 14 sugerencias); probado por el
  grupo en dos teléfonos. Informe de cierre: `docs/superpowers/reports/modulo-4-informe.md`; descripción del PR: `reports/modulo-4-pr.md`.
  - **Qué hay:** amigos (búsqueda exacta por nombre o código, solicitudes, perfil del amigo), racha en pareja, batallas
    (partida rápida y retos a un amigo, arena con ulti), fotos de perfil en Social, y reportar y bloquear. La spec es
    `docs/superpowers/specs/2026-10-21-modulo-4-design.md` (decisiones D1 a D20 y O1 a O15; **O15 fue reemplazada el 2026-10-22**).
  - **El servidor decide todo el juego** (aciertos, daño, turnos, plazos, rachas, XP) con la hora de la base de datos; la app solo
    pregunta y muestra. Las tablas de Social están cerradas y se usan por 5 funciones públicas: `social_friends`, `social_pairs`,
    `battle_lobby`, `battle_sync` y `battle_play` (más `social_set_avatar` para la foto), con la acción como parámetro.
  - **Sondeo, no Realtime:** `battle_sync` cada 2 a 3 s mientras la arena está visible (es el latido: más de 60 s sin llamadas
    pierde la batalla). Por eso **Reportar y Bloquear son diálogos y no rutas**: una ruta que tape la arena detiene el sondeo.
  - **Código:** `data/social` (modelos, parser, repositorios, sondeo, fotos), `data/supabase/Supabase*` (única parte que conoce la
    librería), `ui/social` (pantallas y ViewModels), textos en `strings_social*.xml`. `SocialBoundaryTest` vigila las capas.
  - **Pruebas:** **1334** pruebas unitarias (0 fallos) al cerrar el módulo base; **1399**, 0 fallos, tras el perfil social; lint en 0 errores.
  - **Perfil social editable (rama `social-perfil`, fusionada con el PR #10):** cada persona ve y edita su propio perfil social. Nueva tarjeta "Tu Perfil" en Inicio, pantallas
    "Tu perfil" y "Editar tu perfil" (`ui/social/MyProfile*`, `EditMyProfile*`), el perfil del amigo rediseñado (sin scroll general, botones fijos, Estadísticas, historial de 3) y un fondo
    elegible (`profile_bg_1` a `profile_bg_5`, o "sin fondo"; el servidor guarda solo un número 0 a 5) con ojos que ocultan Insignias, Estadísticas e Historial. El apartado compartido es
    `ui/social/ProfileSections.kt`. **El script `0012_perfil_social.sql` ya está APLICADO y verificado en Supabase** (según el equipo; su guion es
    `supabase/verificacion/0012_escenarios.sql`). **Ojo:** su encabezado todavía dice "BORRADOR sin aplicar"; no se edita (una migración aplicada no se toca), pero ya no es cierto.
    Spec: sección 13 de la spec del módulo 4. Contrato de `progress` en el perfil del amigo: `null` con `hide_progress`; si no, siempre `fighter_level`, `viewed_count` y `labs_count`, y los
    hechos de insignias solo con `show_badges`; `stats` e `history` solo con su ojo abierto (si no, la clave no existe). Fondos: `profile_bg_1` a `profile_bg_5` (archivos `.webp`) o "sin fondo" (0 a 5).
  - **Deuda técnica del módulo 4 (confirmada, no urgente):** (1) comentarios desactualizados que dicen que solo se puede actualizar `hide_progress`
    (`data/social/SocialRepositories.kt:28` y encabezado de `data/supabase/SupabaseSocialProfileRepository.kt:29-31`); (2) el código de rechazo `avatar_hidden` no está en
    `SocialRejection`, así que la app muestra un error genérico; (3) "hoy" sale del reloj del teléfono (`PairDetailViewModel.kt:121`, `MyProfileLoader`; la zona es `America/Bogota` en
    pareja y amigos, y la del dispositivo en la racha del perfil propio); (4) nivel y racha de batallas se calculan también en la app (`buildFighterSummary`), duplicando `sx_level`;
    (5) sin pruebas de Compose, sin `@Preview` completos ni pruebas con TalkBack en las pantallas sociales.
  - **Fuera del módulo 4:** la tabla de actividad por día para que repetir cuente en la racha (sería la migración `0013`), la corrección del
    examen en el servidor si hay ranking o XP, las notificaciones push y una pantalla de moderación.
- **Extras ya hechos (posteriores al módulo 4):** las 6 pantallas de lecciones según Figma ("LECCION UPDATE - <Módulo>", con scroll; rama `lecciones-update`, probada en el teléfono) y
  ajustes de la ruta ("Prueba final" a la izquierda y arriba, "Bahía del Phishing" hacia arriba). Un segundo compañero se sumó el 2026-10-23 para extras de los módulos 3 y 4.
- **Módulo 5 = personalización (5A y 5B hechos; v2.4.0).** Alcance acordado por el equipo: tema claro (claro/oscuro/automático), idioma inglés (español sigue siendo el actual) y el resto
  de personalización que esté en el plan o en el Figma. Spec y plan: `docs/superpowers/specs/2026-11-02-modulo-5-personalizacion-design.md` (etapas 0 a 10).
  - **5A (tema), hecho en la rama `modulo-5-tema-completo` (etapas 1 a 6, v2.3.0):** preferencias (etapa 2), textos en recursos con guarda (etapa 3), tokens `NightWatchColors`
    claro/oscuro y Apariencia funcional (etapa 4) y toda la app migrada al modo elegido (etapas 5 y 6). `NoHardcodedColorsTest` cubre todo `app/src/main/java` fuera de `ui/theme` (única excepción:
    el bitmap de `SocialAvatar`); el oscuro es idéntico al de v2.2.0 (`NightWatchColorsParityTest`); `ContrastTest` lleva la lista de pares oscuros bajo la meta, que no se corrige. Los valores claros
    sin frame en el Figma son PROPUESTA hasta que el grupo los apruebe. Informes: `reports/modulo-5a-cierre.md` y `reports/etapa-5-verificacion-visual.md` (lista para el emulador; nada se ha visto en un teléfono).
  - **5B (idioma), hecho (etapas 7 a 10, v2.4.0; ya en `main`; las menciones de abajo a ramas "sin fusionar" son históricas):** verificar R-01 y AppCompat (7), `values-en` para toda la UI (8), contenido JSON en inglés (9), notificaciones, fechas y cierre (10).
    **Etapa 7:** `MainActivity` es `AppCompatActivity` (appcompat 1.8.0), el tema base es `Theme.AppCompat.Light.NoActionBar` (NO DayNight), `res/xml/locales_config.xml` (es, en); el idioma vive en AppCompat
    (`AppCompatLanguageRepository`), nunca en DataStore (solo `language_initialized`); el primer arranque fija español (`LanguageBootstrap`); la pantalla Idioma ya funciona, pero **sin `values-en` elegir English no cambia los textos**.
    Los textos están en **7 archivos** (`strings.xml`, `_auth`, `_quiz`, `_perfil`, `_aprendizaje`, `_social`, `_social_pantallas`; 771 cadenas y 8 plurales). Decisiones D15 a D18 (banco de batallas traducido en Supabase, aviso de lo no traducible,
    procedimiento para nuevos idiomas) en la sección 13 de la spec; la migración SQL del banco **no existe aún**. Lista de verificación: `reports/etapa-7-verificacion.md`.
    **Etapa 8 (rama `modulo-5-ingles-ui`, sin fusionar):** `res/values-en/` con los 7 archivos de textos (770 cadenas y 8 plurales traducidos), `TranslationParityTest` (paridad archivo por archivo; un archivo nuevo en `values/` exige su gemelo en `values-en/`),
    aviso de Idioma (`settings_language_notice_text`, solo con English; PROPUESTA), tabla de revisión `docs/superpowers/translations/ui-es-en.csv` y lista `reports/etapa-8-verificacion.md`. El contenido JSON (etapa 9) y las preguntas de batalla siguen en español.
    **Etapa 9 (rama `modulo-5-ingles-contenido`, sin fusionar):** el contenido JSON vive en `assets/content/<es|en>/` (`learning_lessons`, `learning_labs`, `learning_exam`, `quiz_questions`); los repositorios leen la carpeta del idioma activo y caen al
    español si falta o falla un archivo (`data/content/LocalizedContent.kt`). El español no cambió (lo vigila `SpanishContentGoldenTest`); `ContentParityTest` exige mismos ids, estructura, respuestas correctas y comandos de labs. Tabla de revisión:
    `docs/superpowers/translations/contenido-es-en.csv`; informe y lista del emulador: `reports/etapa-9-verificacion.md` (nombres de islas y salidas en prosa de los labs son PROPUESTA).
  - **Etapa 10 (rama `modulo-5-ingles-final`, v2.4.0, sin fusionar):** migración `0013_batallas_ingles.sql` **APLICADA y verificada el 2026-11-05** (traducciones de las 72 preguntas y 12 retos de ulti; `p_lang` en `battle_sync`/`battle_play`; el idioma es el de quien mira) con reversa en
    `supabase/rollback/` y verificación en `supabase/verificacion/0013_verificacion.sql`. La app manda `p_lang` solo con inglés y reintenta sin él si el servidor aún no tiene `0013`. El aviso `social_arena_questions_spanish_only` nunca se creó: no hay nada que quitar.
    Notificaciones y fecha de nacimiento siguen el idioma activo; plantillas de correo bilingües en `docs/supabase/plantillas-correo-bilingue.md` (se pegan a mano); la Edge Function `send-recovery-email-code` acepta `lang`; plantillas y función ya desplegadas (2026-11-05; el equipo redespliega a mano la corrección de `pickLang`). Abierto: revisión del grupo de `ui-es-en.csv` y `contenido-es-en.csv`.
    Informe y lista del emulador: `reports/etapa-10-verificacion.md`. Tercer idioma: sección 13.6 de la spec.
- **Lecciones aprendidas:** los guiones SQL deben acotar los conteos a las cuentas de prueba; Brevo permite unos 300 correos al día; el login con Google falla en silencio si el SHA-1 de depuración
  no está registrado en un cliente OAuth Android de Google Cloud (paquete `com.example.nightwatchprotocol`); la regla de "solo Write/Edit" se rompió varias veces con `sed -i`: sigue vigente.

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
| `0008_social.sql` | **Aplicada y verificada (2026-10-21).** 14 tablas de Social (11 cerradas, sin políticas ni `GRANT`) y 5 funciones públicas `SECURITY DEFINER` más 20 internas `sx_*` |
| `0009_banco_batallas.sql` | **Aplicada y verificada (2026-10-21).** 72 preguntas de batalla, 12 retos de ulti y la curva de XP (20 niveles) |
| `0010_fotos_social.sql` | **Aplicada y verificada (2026-10-22).** `avatar_key` y `avatar_version` en `social_profile`, `social_set_avatar` y el bucket privado `social-avatars` con 4 políticas |
| `0011_reportes_bloqueos.sql` | **Aplicada y verificada (2026-10-23).** Tablas cerradas `social_reports` y `social_blocks`, columna `avatar_hidden` (solo la cambia el equipo), acciones `report`, `block`, `unblock` y `blocks` dentro de `social_friends`, `sx_is_blocked`. Su guion `supabase/verificacion/0011_escenarios.sql` dio 187 de 187. En un servidor sin ella, la app muestra "Función no disponible por ahora" |
| `0012_perfil_social.sql` | **Aplicada y verificada** (PR #10). Su encabezado aún dice "BORRADOR sin aplicar" y no se edita. Columnas `profile_bg` (0 a 5), `show_badges`, `show_stats` y `show_history` en `social_profile` (el cliente las actualiza por `GRANT UPDATE` de columna) y `social_friends` reemplazada: la acción `profile` devuelve fondo y ojos, y `stats` e `history` (3 últimas entre los dos), omitiendo como clave ausente lo que el amigo oculta; `progress` cambia el contrato de `0011` (contadores siempre, hechos de insignias solo con `show_badges`). Sin funciones ni tablas nuevas. Ya aplicada: no se edita |

- **Una migración ya aplicada NO se edita.** Cualquier cambio va en una migración nueva con el siguiente número (`0013`...).
  `0007` conserva en su encabezado la palabra "BORRADOR" y un comentario sobre la racha fija: es lo
  que se ejecutó; `0008` a `0012` tampoco se editan (el encabezado de `0012` aún dice "BORRADOR sin aplicar" aunque ya se aplicó).
- Guiones de comprobación con dos cuentas de prueba: `supabase/verificacion/0008_escenarios.sql` (219 de 219) y `0011_escenarios.sql` (187 de 187). Todas sus
  comprobaciones y cambios se limitan a esas dos cuentas.
- **Moderación manual:** no hay pantalla de moderación. El equipo revisa `social_reports` en el Table Editor y oculta una foto con
  `update public.social_profile set avatar_hidden = true where username = '...'` (consultas en el final de `0011`). Ningún número de reportes
  oculta nada solo.
- **RLS en todas las tablas, solo del dueño** (`user_id = auth.uid()`); `anon` no tiene acceso a nada. **`GRANT` explícito**
  a `authenticated` (el proyecto tiene "Automatically expose new tables" desactivado y "Automatic RLS" activado). Mínimo
  privilegio por columna; funciones `SECURITY DEFINER` con `search_path` vacío.
- **Security Advisor:** las advertencias aceptadas y su motivo están en la sección "Advertencias aceptadas del Security
  Advisor" de `docs/superpowers/supabase-configuracion.md`. Una advertencia nueva se corrige o se documenta ahí antes de
  fusionar a `main`. Las de Social (módulo 4, `0008` y `0010`; `0011` no suma advertencias de función; estado final: 10 advertencias, 0 errores, 14 sugerencias) están en "Advertencias aceptadas del Security Advisor (Social)" del
  mismo documento.
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
- **Regla actual:** cada módulo o tarea vive en su propia rama (el módulo 4 fue `modulo-4`; el 5 tendrá la suya), con **un commit por etapa o por tema**
  en español, y entra a `main` por un Pull Request con "Create a merge commit". El equipo marca cada versión con una etiqueta `vX.Y.Z` y un Release (lo hace el
  usuario a mano, no Claude Code). Un solo prompt consolidado por tarea.
  **Claude Code NUNCA hace push.** El push de la rama lo hace el usuario a mano. **Jamás se commitea ni se sube a `main`
  directamente:** todo entra a `main` por Pull Request con 1 aprobación.
- Nunca `git push --force`. `local.properties` nunca se sube.

## 7. Reglas para Claude Code

1. Trabaja siempre en una **rama explícita** (la de la tarea en curso; nunca `main`); verifica con `git branch` antes de empezar. Si estás en
   `main`, detente y avisa. **Nunca toques `main`.**
2. **Sin push, sin `--force`, sin tocar ramas remotas.** Sin tags ni releases.
3. `git add` **por nombre de archivo** (nunca `.` ni `-A`); revisa `git status` antes de cada commit.
4. **Un commit por etapa o por tema**, en español, terminando con `Co-Authored-By: Claude <noreply@anthropic.com>`.
5. **Antes de cada commit valida:** `./gradlew assembleDebug testDebugUnitTest lintDebug` (y
   `compileDebugAndroidTestKotlin` si tocaste pruebas instrumentadas). `lintDebug` debe quedar en 0 errores. Todas las
   pruebas existentes deben seguir pasando; no las borres para "arreglar" un fallo.
6. No toques lo que la tarea no pide ni módulos terminados, salvo lo mínimo para conectar el cambio.
7. Ante secretos en cualquier archivo, **detente y avisa sin imprimirlos**.
8. **Editar archivos solo con Write y Edit; nunca `sed`, `awk`, `echo`, `cat`, heredoc ni scripts** (con ellos ya se perdieron
   comillas y se rompieron archivos). Leer, con Read, Grep y Glob.
9. **Diagnóstico final siempre:** qué cambió, qué no pudiste verificar (nada se ve en un teléfono desde aquí), cuántas
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
  registrado no suma un día a la racha** (haría falta una tabla de actividad, `0012`); un intento de examen cuyo guardado
  expiró pero llegó puede quedar duplicado al reintentar; "Lección diaria" no menciona un lab pendiente (decisión Q3).
- **Módulo 4 (Social):**
  - La moderación es manual y mínima: reportar y bloquear (`0011`, aplicado) y ocultar una foto a mano; **no hay pantalla de
    moderación**, nadie recibe aviso de un reporte y un nombre de usuario ofensivo no se puede cambiar (O3). Los nombres son texto libre.
  - La partida rápida empareja desconocidos; ganar por abandono da +50 XP, así que dos cuentas podrían farmear XP (sin ranking, impacto bajo).
  - Las fotos de Social las puede leer y listar cualquier persona con sesión (claves aleatorias, sin ids de Auth); el CDN puede tardar en
    mostrar una foto reemplazada. Buscar por nombre permite comprobar que existe (límite de ritmo, coincidencia exacta).
  - "Hoy" en la tira de la semana de la pareja sale del reloj del teléfono (R-05, aceptado: todos los usuarios están en Colombia).
  - Sin pruebas de Compose de las pantallas de Social (R-15), sin vistas previas (R-14), y nada del servidor real ni de TalkBack se ha
    comprobado desde el código: lo hace el grupo con los guiones de `supabase/verificacion/`.
- **Perfil social editable (ya en `main`, PR #10):** el ojo de Insignias oculta SOLO las insignias (nivel, Ejercicios y Labs no dependen de él; "Ocultar mi progreso público" oculta todo). Contrato de `progress` en `0012`: `null` con `hide_progress`; si no, siempre `fighter_level`, `viewed_count` y `labs_count`, y con `show_badges` además los hechos de insignias de `0011` (ausentes con el ojo cerrado); las estadísticas
  y el historial propios reutilizan la lectura completa de `battle_results` que ya hace Inicio (sin límite de filas); el selector de fondo tiene casillas de unos 30 dp de alto; nada se ha visto en un teléfono.
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
9. `docs/superpowers/specs/2026-10-21-modulo-4-design.md`: la spec del módulo 4 (decisiones del grupo; O15 reemplazada el 2026-10-22).
10. `docs/superpowers/reports/revision-codigo-modulo-4.md`: revisión de código del módulo 4 (R-01 a R-22) y su estado.
11. `supabase/verificacion/`: guiones de escenarios de `0008` y `0011` (no son migraciones; no se aplican a un proyecto nuevo).
12. `docs/superpowers/reports/modulo-4-informe.md`: cierre del módulo 4 (alcance, decisiones, seguridad, pruebas, limitaciones y guion de prueba manual) y
    `modulo-4-pr.md`: la descripción del Pull Request.
13. `docs/superpowers/reports/modulo-ia-cybermind.md`: informe del módulo de IA (decisiones, límites, pruebas, tamaño del APK y pendientes; ver la sección 10).

## 10. Módulo de IA "Prepárate con CyberMind" (v2.5.0)

Un modelo GBDT exportado a ONNX corre **en el teléfono, sin internet**, y clasifica a cada estudiante, por tema, como `AVANCE` o `REFUERZO`. Informe completo: `docs/superpowers/reports/modulo-ia-cybermind.md`.
Repo del modelo y los experimentos: <https://github.com/CyberMind-UNI/CyberMind-ML> (enlaza de vuelta a este).

- **Dónde está:** lógica sin Android en `cybermind/` (`CyberMindDominio`, `CyberMindModelo`, `EvaluadorEstudiante`, `ValidadorCsv`, `ValidadorManual`, `PlantillaCsv`, `ArchivosCsv`, `ResultadosEnMemoria`);
  pantallas y ViewModels en `ui/cybermind/`; textos en `strings_cybermind.xml` (prefijo `cybermind_`, con su gemelo en `values-en`); rutas `CyberMind*Route` en `ui/navigation/`; entrada: tarjeta de Inicio.
- **Evaluador:** interfaz `EvaluadorEstudiante` con la implementación `OnnxEvaluadorEstudiante` y el `FakeEvaluadorEstudiante` de las pruebas. **Instancia única** en `AppContainer.evaluadorEstudiante`
  (sesión ONNX creada con `lazy` en la primera evaluación, en `Dispatchers.Default`). El modelo es `app/src/main/assets/cybermind_gbdt.onnx`; `MODEL_VERSION` = `"cybermind_gbdt-v1"` (`EvaluadorEstudiante.kt`).
  `CyberMindModelo.kt` es el envoltorio entregado por el equipo de IA: no se le cambia la lógica.
- **Plan B:** si el modelo no carga (se captura `Throwable`, no se reintenta por fila), se evalúa con la regla del 70 % y el resultado se marca `REGLA_RESPALDO` (confianza `null`); la pantalla lo indica.
- **CSV:** 9 columnas en orden (`id_estudiante, tema, experiencia_previa, aciertos_basico, aciertos_intermedio, aciertos_avanzado, puntaje_tema, puntaje_global, tiempo_promedio_seg`); rechaza la columna `decision`; máximo 500 filas, 100 estudiantes y 200 KB
  (tamaño comprobado antes de leer; UTF-8 estricto); punto decimal obligatorio. Reglas: rangos y valores permitidos, tema sin repetir por estudiante, `experiencia_previa` igual en las filas de un estudiante,
  `puntaje_tema` = `round(suma de aciertos / 9 * 100, 1)` (tolerancia 0,05) y `puntaje_global` igual al promedio con exactamente 5 filas (tolerancia 0,1; con menos solo igual entre filas). Se rechaza el archivo entero.
  Temas: `fundamentos_redes`, `criptografia`, `seguridad_web`, `ingenieria_social`, `gestion_vulnerabilidades`.
- **Manual:** 1 a 5 temas; el ID (`M00001`…), `puntaje_tema` y `puntaje_global` se calculan solos; aciertos, experiencia y tema se eligen de listas; acepta coma o punto al escribir decimales.
- **No se guardan datos de estudiantes:** nada va a disco, a Supabase ni a logs; los resultados viven en `ResultadosEnMemoria` y se vacían al cerrar la pantalla de resultados. Los datos de ejemplo son sintéticos.
  **No uses Supabase en este módulo.** Colores: sin colores fijos (`NoHardcodedColorsTest`); REFUERZO usa el token `reinforce`.
- **Pruebas:** 1625 unitarias, 0 fallos, al cerrar el módulo; la instrumentada `CyberMindModeloInstrumentedTest` solo se compila desde aquí (la corre el equipo en un teléfono).
- **Pendientes del informe:** el APK de depuración pesa ~99 MB y el de release ~91,6 MB por ONNX Runtime con 4 arquitecturas (decidir `abiFilters` o AAB es del equipo); sin pruebas de Compose, `@Preview` ni TalkBack.
