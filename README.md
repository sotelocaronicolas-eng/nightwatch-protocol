# NightWatch Protocol

Aplicación Android para aprender ciberseguridad (**versión 2.2.0**). La persona se registra (con un código de 6 dígitos
que llega a su correo) o inicia sesión (también con Google), hace un cuestionario de nivelación (BAJO, MEDIO o ALTO),
recorre una ruta de aprendizaje con lecciones, laboratorios y un examen final, se conecta con amigos (rachas en pareja y
batallas) y administra su perfil y sus ajustes. Está
hecha en Kotlin con Jetpack Compose y **Supabase**, siguiendo los patrones del curso
*Android Basics with Compose* (MVVM, `UiState`, `ViewModel`, inyección de dependencias manual) y el diseño de Figma del
proyecto.

> **Importante:** la app **no compila ni arranca sin tu `local.properties`** (no está en el repositorio). Mira
> [Puesta en marcha](#puesta-en-marcha-para-un-integrante-nuevo).

## Tecnologías

| Área | Qué se usa |
|---|---|
| Lenguaje | Kotlin 2.2.10 |
| Interfaz | Jetpack Compose (BOM 2026.02.01) con Material 3 |
| Arquitectura | MVVM: pantalla con estado + `ViewModel` con `StateFlow<UiState>` y eventos por `Channel`; repositorios con interfaz y *fakes* para las pruebas; DI manual en `AppContainer` (sin Hilt) |
| Navegación | Navigation Compose 2.9.7 con rutas tipadas `@Serializable` |
| Backend | **Supabase** (supabase-kt 3.2.4, Ktor 3.3.0): **Auth** (correo y contraseña, Google, códigos de un solo uso), **Postgres** (con RLS), **Storage** (foto de perfil) y **Edge Functions** (código del correo de recuperación) |
| Correos | **Brevo**: SMTP para los correos de Auth y API transaccional para la Edge Function |
| Inicio de sesión con Google | Credential Manager 1.5.0 + `googleid` 1.1.1 (flujo nativo con ID token y *nonce*) |
| Preferencias locales | DataStore 1.2.1 |
| Segundo plano | WorkManager 2.12.0 (recordatorios locales) |
| Biometría | `androidx.biometric` 1.1.0 (bloqueo de la app) |
| Serialización | kotlinx.serialization 1.8.0 |
| Pruebas | JUnit 4, Turbine 1.2.1, `kotlinx-coroutines-test`, Compose UI Test |
| Compilación | Android Gradle Plugin 9.3.3, Gradle 9.5.0 (wrapper incluido) |

## Arquitectura

```
Pantalla (Compose)  →  ViewModel (UiState + eventos)  →  Repositorio (interfaz)  →  Supabase
       ▲                                                        │
       └──────────── UiState (StateFlow) ◄──────────────────────┘
```

- Las pantallas y los ViewModels **solo conocen interfaces** (`AuthRepository`, `AccountRepository`,
  `ProfileRepository`, `DeviceRepository`, `QuizResultRepository`, `LearningContentRepository`,
  `LearningProgressRepository`). Solo `di/AppContainer` y las clases `Supabase*` conocen la librería de Supabase; en las
  pruebas se usan *fakes*.
- El progreso de la ruta de aprendizaje se guarda en tres tablas pequeñas y **todo lo demás se deriva de él** (islas,
  desbloqueo, "lección diaria", racha e insignias): son funciones puras en `data/learning`, con pruebas.
- Un solo flujo de **código de 6 dígitos** (`ui/verifycode`) sirve para el registro, el cambio de correo, la
  recuperación de contraseña y la verificación del correo de recuperación.
- La sesión vive en supabase-kt; la fila de cada usuario se identifica siempre por la sesión, nunca por un dato de la
  pantalla. La seguridad real está en la base de datos (RLS y privilegios mínimos), no en la app.

## Funciones

- **Cuenta:** registro con código por correo, inicio de sesión con correo o Google, recuperación de contraseña con
  código, cambio de correo con código, cambio de contraseña, "Confirma tu identidad".
- **Quiz de nivelación** (20 preguntas locales, 11 por intento) con el resultado guardado en Supabase.
- **Perfil:** nombre, género, fecha de nacimiento, foto (Supabase Storage), correo de recuperación verificado con código
  y descarga de los datos en JSON.
- **Seguridad:** bloqueo biométrico de la app, dispositivos con cierre de sesión (marca cooperativa + revocación real de
  las demás sesiones).
- **Recordatorios** locales (lección diaria y racha en riesgo).
- **Ruta de aprendizaje (módulo 3):**
  - **Inicio y barra inferior** (Inicio, Ruta, Labs, Social, Perfil): chapa con nombre, nivel y **racha real**; "Lección diaria".
  - **Ruta:** mapa de 6 islas y la Prueba Final; una isla se completa con la lección vista y el laboratorio completado, y se
    desbloquean en orden.
  - **6 lecciones** y **6 laboratorios** con una terminal simulada (solo reconoce los comandos del diseño), informe de éxito o
    de "Laboratorio no completado" y precisión.
  - **Examen Final** de 6 preguntas (se aprueba con 5 o más aciertos, sin límite de intentos), con el resultado guardado; no
    cambia el nivel del quiz.
  - **Logros e insignias reales** (9) y racha actual, calculadas del progreso guardado.
  - "Concepto nuevo" y "Prepárate con CiberMynd" siguen sin hacerse.
- **Social (módulo 4):**
  - **Amigos:** búsqueda por nombre de usuario exacto o por código (`NIGHT-XXXXXX`), solicitudes, perfil del amigo (racha,
    insignias e historial entre los dos; se puede ocultar el progreso) y foto de perfil.
  - **Racha en pareja:** con un amigo, hasta 6 días (hay 6 islas), calculada por el servidor con hora de Colombia.
  - **Batallas:** partida rápida contra desconocidos o reto a un amigo, preguntas por turnos, "Ulti" con un reto de
    terminal, XP y nivel de combatiente. **El servidor decide todo** (aciertos, daño, turnos y plazos); la app solo sondea
    el estado cada 2 a 3 segundos mientras la arena está visible.
  - **Reportar y bloquear** personas, y "Personas bloqueadas" en Amigos. No hay pantalla de moderación: el equipo revisa los
    reportes en el panel de Supabase.
- Solo visuales o con datos fijos: Apariencia, Idioma, Ayuda, Acerca de; los interruptores de notificaciones de "Logros" y
  "Nuevos laboratorios" no tienen efecto (necesitan notificaciones push).

La migración desde Firebase, las decisiones y los problemas encontrados están en
[`docs/superpowers/reports/migracion-supabase.md`](docs/superpowers/reports/migracion-supabase.md) y la guía técnica de
Supabase en [`docs/superpowers/supabase-configuracion.md`](docs/superpowers/supabase-configuracion.md).

## Requisitos

- **Android Studio** reciente.
- **JDK 25 para ejecutar Gradle.** `gradle/gradle-daemon-jvm.properties` fija `toolchainVersion=25` y el resolver
  *foojay* de `settings.gradle.kts` puede descargarlo solo si no lo tienes. El código se compila para Java 11.
- **Android SDK 37** (`compileSdk` y `targetSdk` = 37). `minSdk` = **26** (Android 8.0).
- **Emulador recomendado:** API 34 o 35 con imagen **Google APIs** (necesita Google Play Services para el login con
  Google), arquitectura **x86_64** y **sin** "16 KB Page Size". También sirve un teléfono real con Android 8.0 o superior.
  Para probar el bloqueo biométrico hace falta una huella registrada en el dispositivo.

## Puesta en marcha para un integrante nuevo

1. **Clona** el repositorio.
2. **Pide acceso a la organización de Supabase** con el rol **Developer** a quien sea dueño del proyecto (ver la
   [tabla de responsables](#responsables)).
3. **Crea tu `local.properties`** en la raíz copiando `local.properties.example`, y completa:
   - `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY`: en el panel de Supabase, botón **Connect** del proyecto (la clave es la
     *publishable*; **nunca** pongas la `secret` ni la `service_role` en la app).
   - `GOOGLE_WEB_CLIENT_ID`: el ID de cliente OAuth de tipo **Web** que usa el proveedor Google de Supabase
     (*Authentication > Sign In / Providers > Google*), con la forma `NNNN.apps.googleusercontent.com`.

   `local.properties` está en el `.gitignore` y **nunca se sube**. Si falta una de las tres claves, la app se detiene al
   iniciar con un mensaje que dice cuál.
4. **Registra la huella SHA-1 de tu computador** para que funcione el login con Google (cada computador tiene la suya):
   1. Ejecuta `./gradlew signingReport` y copia el **SHA-1** de la variante `debug`.
   2. Entra a [console.cloud.google.com](https://console.cloud.google.com/), elige el proyecto de Google Cloud del
      equipo y ve a **APIs y servicios > Credenciales > Crear credenciales > ID de cliente de OAuth**.
   3. Tipo de aplicación **Android**, paquete **`com.example.nightwatchprotocol`** y tu **SHA-1**. Guarda.
5. **Sincroniza Gradle** (*Gradle Sync*) y pulsa **Run** en el emulador o dispositivo.

Desde la terminal:

```
./gradlew assembleDebug      # compila el APK de depuración
./gradlew installDebug       # lo instala en el dispositivo conectado
```

## Base de datos y funciones del servidor

Todo el servidor está en `supabase/` y **se despliega a mano** desde el panel (no se usa la CLI de Supabase):

- **Migraciones** (`supabase/migrations/0001` a `0011`; `0007` crea las tablas de progreso del módulo 3 y `0008` a `0011` las de
  Social: tablas y funciones de amigos, parejas y batallas, el banco de preguntas, las fotos y los reportes y bloqueos): se
  ejecutan **en orden numérico, una sola vez por proyecto**, a mano en el **SQL Editor** del panel. **Una migración ya
  aplicada no se edita**: cualquier cambio va en una migración nueva con el siguiente número (`0012`...). Cada archivo
  explica qué hace y trae consultas de verificación comentadas al final.
- **Guiones de comprobación** (`supabase/verificacion/0008_escenarios.sql` y `0011_escenarios.sql`): no son migraciones. Se
  corren a mano con dos cuentas de prueba y dejan una tabla de comprobaciones (OK o FALLA); se limpian solos.
- **Edge Function** `send-recovery-email-code` (`supabase/functions/`): envía el código del correo de recuperación por la
  API de Brevo. Su `README.md` explica cómo desplegarla (*Edge Functions > Deploy a new function*) y los tres secretos
  que necesita (`BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME`).
- **Ajustes del panel** (SMTP de Brevo, plantillas de correo con `{{ .Token }}`, longitud y vencimiento del código,
  proveedor Google, *Confirm email*, *Allow manual linking*): lista exacta en
  [`docs/superpowers/supabase-configuracion.md`](docs/superpowers/supabase-configuracion.md).

## Responsables

Aquí van **roles**, no contraseñas ni claves (esas se piden en persona o por un canal privado).

| Servicio | Quién lo administra | Qué hay que saber |
|---|---|---|
| **Supabase** (organización y proyecto) | La persona dueña de la organización; los demás son **Developers** | Un solo proyecto compartido. Los Developers ven y usan el panel (SQL Editor, Edge Functions, Auth) pero no gestionan la organización |
| **Brevo** | Una sola cuenta del equipo (plan gratuito, **un solo usuario**) | De ahí salen la clave SMTP (correos de Auth) y la API key (Edge Function). Quien necesite cambiar algo lo pide a esa persona |
| **Google Cloud** (proyecto con el cliente OAuth de Google) | La persona dueña del proyecto | Cada integrante agrega su propio ID de cliente Android con su SHA-1. El proyecto lo creó en su momento Firebase y se conserva por el cliente OAuth |
| **GitHub** | La persona dueña del repositorio | Pull Requests con aprobación (ver "Cómo trabajamos en equipo") |

## Limitaciones del plan gratuito

- **Supabase pausa el proyecto tras 7 días sin actividad.** La app y el login dejan de funcionar hasta reactivarlo:
  entra al panel, abre el proyecto y pulsa **Restore project** (tarda unos minutos). Un proyecto pausado se puede
  restaurar durante unos **90 días**; pasado ese plazo ya no se restaura desde el panel (solo se puede descargar una
  copia). Usar la app o el panel de vez en cuando evita la pausa.
- **Límite de correos.** El SMTP integrado de Supabase permite muy pocos correos por hora, por eso se usa Brevo, cuyo
  plan gratuito permite unos **300 correos al día** en total. Los correos de la Edge Function (verificación del correo de
  recuperación) salen por la API de Brevo y **cuentan en ese mismo límite**, no en el de Supabase. Demostraciones con
  muchas altas seguidas pueden agotarlo.
- **Remitente `@gmail.com`.** Los correos enviados desde una dirección de Gmail por Brevo pueden caer en **spam** o ser
  rechazados por DMARC. Para una demostración, pide que revisen la carpeta de spam; la solución real es un dominio
  propio con SPF y DKIM.
- **Sin modo sin conexión.** Lo que se guarda sin internet falla como "sin conexión"; el género, la fecha y la foto
  conservan una copia local hasta el siguiente guardado.

## Cómo correr las pruebas

```
./gradlew testDebugUnitTest                  # pruebas unitarias (JVM, con fakes; no necesitan dispositivo)
./gradlew compileDebugAndroidTestKotlin      # comprueba que las pruebas instrumentadas compilan
./gradlew connectedDebugAndroidTest          # pruebas instrumentadas (necesitan un dispositivo o emulador)
./gradlew lintDebug                          # revisión estática (debe quedar en 0 errores)
```

Todas las pruebas unitarias deben pasar antes de abrir un Pull Request. Las pruebas de ViewModel usan
`MainDispatcherRule` y Turbine; los repositorios se sustituyen por *fakes* (`FakeAuthRepository`,
`FakeProfileRepository`, etc.). Las pruebas instrumentadas de Compose solo se ejecutan con un dispositivo.

## Estructura de carpetas

```
NightWatchProtocol/
├── app/
│   ├── build.gradle.kts              # configuración del módulo (minSdk 26, compileSdk 37, BuildConfig desde local.properties)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/                       # quiz_questions.json (20 preguntas) y learning_*.json (lecciones, labs, examen)
│       │   ├── res/                          # íconos, fuentes (Iceland, Inter), imágenes, textos
│       │   └── java/com/example/nightwatchprotocol/
│       │       ├── MainActivity.kt           # FragmentActivity: navegación, bloqueo y sesión del dispositivo
│       │       ├── NightWatchApplication.kt  # contenedor de dependencias, canales y recordatorios
│       │       ├── di/                       # AppContainer (DI manual) y validación de local.properties
│       │       ├── data/                     # repositorios e interfaces
│       │       │   ├── auth/  account/  session/  quiz/  profile/  avatar/  device/  learning/  social/
│       │       │   ├── supabase/             # errores y llamadas comunes a Supabase
│       │       │   ├── common/  preferences/  biometric/  notifications/
│       │       └── ui/                       # pantallas (Compose) y ViewModels
│       │           ├── auth/  verifycode/  quiz/  postauth/  navigation/  startup/  theme/
│       │           ├── home/  path/  lesson/  labs/  lab/  labreport/  exam/  examresult/   # módulo 3
│       │           ├── social/                # módulo 4: amigos, parejas, batallas, reportar y bloquear
│       │           ├── components/           # componentes compartidos
│       │           ├── profile/              # las pantallas de perfil y ajustes
│       │           └── applock/  biometric/  session/
│       ├── test/                             # pruebas unitarias (JVM)
│       └── androidTest/                      # pruebas instrumentadas (dispositivo)
├── supabase/
│   ├── migrations/                   # 0001 a 0011: SQL que se ejecuta a mano, en orden y una sola vez
│   ├── verificacion/                 # guiones de escenarios de 0008 y 0011 (no son migraciones)
│   └── functions/send-recovery-email-code/   # Edge Function (Deno/TypeScript) y su README
├── docs/superpowers/
│   ├── supabase-configuracion.md     # guía técnica: ajustes del panel, flujos, limitaciones
│   ├── specs/  plans/                # diseños y planes (los módulos 1 y 2 son históricos; las specs de los módulos 3 y 4 están vigentes)
│   └── reports/                      # informes: migración a Supabase, revisión de código y cierres de módulo (módulos 3 y 4)
├── local.properties.example          # plantilla de tu local.properties
├── gradle/libs.versions.toml         # catálogo de versiones
├── build.gradle.kts  settings.gradle.kts  gradle.properties
└── gradlew  gradlew.bat              # wrapper de Gradle
```

## Control de versiones

El `.gitignore` excluye lo generado y lo sensible: `build/`, `.gradle/`, `.idea/`, `local.properties`, `*.jks`,
`*.keystore`, `*.apk` y `*.aab`. No subas credenciales, llaves de firma ni archivos con claves.

## Cómo trabajamos en equipo

- Nadie sube cambios directo a `main`. Todo entra por Pull Request con al menos 1 aprobación de otro integrante.
- Una rama por tarea, con un nombre descriptivo (por ejemplo `modulo-3-...`, `mantenimiento/...` o `experimento/...`).
- Nunca se usa `git push --force`.
- `local.properties` nunca se sube al repositorio: cada integrante crea el suyo desde `local.properties.example`. Las
  claves `secret` y `service_role` de Supabase y las claves de Brevo no van en la app ni en el repositorio.
- Las migraciones de `supabase/migrations/` no se editan una vez aplicadas: un cambio va en una migración nueva.
- No se actualizan AGP, Gradle, Kotlin, supabase-kt ni Ktor desde el Upgrade Assistant sin acordarlo antes en equipo
  (supabase-kt 3.8.0 exige un Kotlin más nuevo que el del proyecto).

Esto es un acuerdo entre nosotros, no una protección automática: las reglas de protección de ramas de GitHub
requieren un plan de pago en repositorios privados, así que nada impide técnicamente subir a `main`. Depende de que
cada quien lo cumpla.
