# NightWatch Protocol

Aplicación Android para aprender ciberseguridad. La persona se registra o inicia sesión, hace un cuestionario de
nivelación (BAJO, MEDIO o ALTO) y administra su perfil y sus ajustes. Está hecha en Kotlin con Jetpack Compose y
Firebase, siguiendo los patrones del curso *Android Basics with Compose* (MVVM, `UiState`, `ViewModel`, inyección
de dependencias manual) y el diseño de Figma del proyecto.

> **Importante:** el archivo `app/google-services.json` **no está en el repositorio**. Cada integrante debe
> descargar el suyo desde Firebase Console antes de compilar. Mira la sección
> [Configurar Firebase](#configurar-firebase).

## Tecnologías

| Área | Qué se usa |
|---|---|
| Lenguaje | Kotlin 2.2.10 |
| Interfaz | Jetpack Compose (BOM 2026.02.01) con Material 3 |
| Arquitectura | MVVM: pantalla con estado + `ViewModel` con `StateFlow<UiState>` y eventos por `Channel`; repositorios con interfaz y *fakes* para las pruebas; DI manual en `AppContainer` (sin Hilt) |
| Navegación | Navigation Compose 2.9.7 con rutas tipadas `@Serializable` |
| Backend | Firebase (BoM 34.19.0): **Authentication** (correo y contraseña, y Google) y **Cloud Firestore** |
| Inicio de sesión con Google | Credential Manager 1.5.0 + `googleid` 1.1.1 |
| Preferencias locales | DataStore 1.2.1 |
| Segundo plano | WorkManager 2.12.0 (recordatorios locales) |
| Biometría | `androidx.biometric` 1.1.0 (bloqueo de la app) |
| Serialización | kotlinx.serialization 1.8.0 |
| Pruebas | JUnit 4, Turbine 1.2.1, `kotlinx-coroutines-test`, Compose UI Test |
| Compilación | Android Gradle Plugin 9.3.3, Gradle 9.5.0 (wrapper incluido) |

## Módulos y estado

### Módulo 1: Autenticación, onboarding y quiz de nivelación — terminado
- Pantalla de arranque (*splash*) con el logo, inicio de sesión, registro, recuperar contraseña e inicio de sesión con Google.
- Reglas de contraseña alineadas con la política de Firebase y errores individuales por campo.
- Quiz de nivelación: cada intento sortea 11 preguntas de un banco local de 20 (`quiz_questions.json`), calcula el nivel
  y las categorías débiles y guarda el resultado en Firestore. Una puerta de entrada (`PostAuthGate`) decide si hay que
  hacer el quiz o ir al menú principal.

### Módulo 2: Perfil y ajustes — implementado (23 pantallas del Figma + 1 extra)
Funcional:
- **Menú de usuario**, **Mi Perfil** (nombre, correo, nivel, racha, miembro desde, género, fecha de nacimiento y correo
  de recuperación) y **Editar Perfil** (nombre, contraseña, correo, avatar, género y fecha de nacimiento).
- **Avatar**: foto elegida con el selector del sistema, guardada en Firestore (Base64) y en caché local.
- **Cambiar contraseña** (con los mismos requisitos del registro) y **Cambiar correo** con el enlace de verificación de
  Firebase. La pantalla "Confirma tu identidad" pide la contraseña cuando Firebase exige un inicio de sesión reciente.
- **Correo de recuperación** y **Descargar mis datos** (archivo JSON).
- **Privacidad y seguridad**: bloqueo biométrico real y "ocultar mi progreso público" (solo guarda el interruptor).
- **Notificaciones**: recordatorios locales reales (lección diaria y racha en riesgo) con permiso y canales.
- **Dispositivos**: registro propio en Firestore en modo cooperativo (ver la limitación abajo).

Solo visual o con datos fijos: Apariencia, Idioma (no cambian el tema ni el idioma), Logros e insignias, Ayuda y
soporte, Acerca de, y los avisos de logros, laboratorios y novedades (solo guardan el interruptor).

Limitación de **Dispositivos**: "Cerrar sesión" en otro dispositivo solo marca `revoked = true` en Firestore y esa app
cierra su sesión la próxima vez que se abre. **No revoca tokens de Firebase** (el cliente no puede hacerlo).

El detalle de decisiones, riesgos y pruebas manuales está en
[`docs/superpowers/reports/modulo-2-diagnostico.md`](docs/superpowers/reports/modulo-2-diagnostico.md).

## Requisitos
- **Android Studio** reciente.
- **JDK 25 para ejecutar Gradle.** `gradle/gradle-daemon-jvm.properties` fija `toolchainVersion=25`, y el resolver
  *foojay* de `settings.gradle.kts` puede descargarlo solo si no lo tienes. AGP 9.3 exige como mínimo JDK 17, pero ese
  mínimo no cuenta mientras el proyecto pida 25. El código se compila para Java 11 (`compileOptions` en
  `app/build.gradle.kts`).
- **Android SDK 37** (`compileSdk` y `targetSdk` = 37). `minSdk` = **24** (Android 7.0).
- Un dispositivo o emulador con Android 7.0 o superior. Para probar el bloqueo biométrico hace falta una huella
  registrada, y para el inicio de sesión con Google, un dispositivo con Google Play Services.
- Un proyecto de **Firebase** propio o compartido (ver la siguiente sección).

## Configurar Firebase

El archivo de configuración contiene datos de tu proyecto de Firebase, por eso está en el `.gitignore` y **no se sube**.
Cada integrante hace lo siguiente una vez:

1. Entra a [Firebase Console](https://console.firebase.google.com/) y abre el proyecto del equipo (o crea uno).
2. Ve a **Configuración del proyecto** (el engranaje) > **General** > **Tus apps**. Si la app Android no existe,
   agrégala con el nombre de paquete **`com.example.nightwatchprotocol`**.
3. En esa misma app, agrega la **huella SHA-1** de tu llave de depuración (la necesita el inicio de sesión con Google).
   Para obtenerla ejecuta `./gradlew signingReport` y copia el SHA-1 de la variante `debug`.
4. Pulsa **Descargar google-services.json** y colócalo en **`app/google-services.json`**.
   No lo subas al repositorio ni lo compartas por canales públicos: Git ya lo ignora.
5. En **Authentication > Método de acceso**, activa **Correo electrónico/contraseña** y **Google**.
   La app distingue "correo no registrado" de "contraseña incorrecta"; eso requiere tener **desactivada** la opción
   "protección contra enumeración de correos" de Authentication.
6. En **Firestore Database**, crea la base de datos y **publica las reglas** de [`firestore.rules`](firestore.rules).

### Reglas de Firestore
El archivo [`firestore.rules`](firestore.rules) tiene el conjunto completo de reglas que usa la app
(`users/{uid}/placement`, `users/{uid}/profile` y `users/{uid}/devices`). **Se publican a mano**: abre
**Firestore Database > Reglas**, reemplaza todo el contenido por el del archivo y pulsa **Publicar**.
Si cambias las reglas en el repositorio, vuelve a publicarlas en la consola. Si no están publicadas, la app tolera
el error pero no puede guardar ni leer esos datos.

## Cómo correr la app
1. Abre la carpeta del proyecto en Android Studio y deja que sincronice Gradle.
2. Coloca tu `app/google-services.json` (sección anterior).
3. Elige un dispositivo o emulador y pulsa **Run**.

Desde la terminal:

```
./gradlew assembleDebug      # compila el APK de depuración
./gradlew installDebug       # lo instala en el dispositivo conectado
```

## Cómo correr las pruebas

```
./gradlew testDebugUnitTest                  # pruebas unitarias (JVM, con fakes; no necesitan dispositivo)
./gradlew compileDebugAndroidTestKotlin      # comprueba que las pruebas instrumentadas compilan
./gradlew connectedDebugAndroidTest          # pruebas instrumentadas (necesitan un dispositivo o emulador)
```

Hay 375 pruebas unitarias. Las pruebas de ViewModel usan `MainDispatcherRule` y Turbine; los repositorios se
sustituyen por *fakes* (`FakeAuthRepository`, `FakeProfileRepository`, etc.). Las pruebas instrumentadas de Compose
solo se ejecutan con un dispositivo.

## Estructura de carpetas

```
NightWatchProtocol/
├── app/
│   ├── build.gradle.kts              # configuración del módulo (minSdk 24, compileSdk 37, dependencias)
│   ├── google-services.json          # NO está en el repo: lo descarga cada integrante
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/quiz_questions.json    # banco de 20 preguntas del quiz
│       │   ├── res/                          # íconos, fuentes (Iceland, Inter), imágenes, textos
│       │   └── java/com/example/nightwatchprotocol/
│       │       ├── MainActivity.kt           # FragmentActivity: navegación, bloqueo y sesión del dispositivo
│       │       ├── NightWatchApplication.kt  # contenedor de dependencias, canales y recordatorios
│       │       ├── di/                       # AppContainer (DI manual)
│       │       ├── data/                     # repositorios e interfaces
│       │       │   ├── auth/  account/  session/  quiz/
│       │       │   ├── profile/  avatar/  preferences/
│       │       │   ├── biometric/  notifications/  device/  firestore/
│       │       └── ui/                       # pantallas (Compose) y ViewModels
│       │           ├── auth/  quiz/  postauth/  mainmenu/  navigation/  theme/
│       │           ├── components/           # componentes compartidos
│       │           ├── profile/              # las pantallas del Módulo 2
│       │           └── applock/  biometric/  session/
│       ├── test/                             # pruebas unitarias (JVM)
│       └── androidTest/                      # pruebas instrumentadas (dispositivo)
├── docs/superpowers/
│   ├── specs/                        # diseños de cada módulo
│   ├── plans/                        # planes de implementación
│   └── reports/                      # informe de cierre del Módulo 2
├── firestore.rules                   # reglas de Firestore (se publican a mano)
├── gradle/libs.versions.toml         # catálogo de versiones
├── build.gradle.kts  settings.gradle.kts  gradle.properties
└── gradlew  gradlew.bat              # wrapper de Gradle
```

## Control de versiones
El `.gitignore` excluye lo generado y lo sensible: `build/`, `.gradle/`, `.idea/`, `local.properties`,
`google-services.json`, `*.jks`, `*.keystore`, `*.apk` y `*.aab`. No subas credenciales, llaves de firma ni
archivos de configuración de tu proyecto de Firebase.

## Cómo trabajamos en equipo

- Nadie sube cambios directo a `main`. Todo entra por Pull Request con al menos 1 aprobación de otro integrante.
- Una rama por tarea, con un nombre descriptivo (por ejemplo `modulo-3-...`, `mantenimiento/...` o `experimento/...`).
- Nunca se usa `git push --force`.
- `google-services.json` y `local.properties` nunca se suben al repositorio. Cada integrante descarga su propio
  `google-services.json` desde la consola de Firebase (**Configuración del proyecto > Tus apps**) y lo pone en `app/`.
- No se actualizan AGP, Gradle ni Kotlin desde el Upgrade Assistant sin acordarlo antes en equipo.

Esto es un acuerdo entre nosotros, no una protección automática: las reglas de protección de ramas de GitHub
requieren un plan de pago en repositorios privados, así que nada impide técnicamente subir a `main`. Depende de que
cada quien lo cumpla.
