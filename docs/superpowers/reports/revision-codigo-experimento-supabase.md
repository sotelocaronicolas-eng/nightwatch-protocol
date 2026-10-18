# Revisión de código: rama `experimento/supabase`

Fecha: 2026-10-17. Alcance: `git diff main...HEAD` (53 commits, 107 archivos, +9089 / −374) más el código existente que
esos cambios tocan. Revisión **solo de lectura**: no se modificó código, pruebas, recursos, migraciones ni funciones; este
informe es el único archivo nuevo.

Qué se ejecutó para apoyar la revisión (sin cambiar fuentes): `./gradlew assembleDebug testDebugUnitTest lintDebug`
(0 errores, **647 pruebas, 0 fallos**), `compileDebugAndroidTestKotlin` (compila) y un recorrido con `grep` de importaciones,
`!!`, `TODO`, logs y colores sueltos. **Lo que NO se pudo comprobar:** nada se probó en teléfono ni contra un proyecto
Supabase o Brevo real; `index.ts` no se tipó ni se ejecutó (no hay `deno` instalado); las pruebas instrumentadas
(`androidTest`) compilan pero no se ejecutaron. Los hallazgos marcados "plausible" salen de leer el código, no de
reproducirlos.

## 1. Resumen ejecutivo

1. **Estado general: bueno para un experimento.** La separación en capas se respeta (ninguna pantalla ni ViewModel importa
   Supabase, Firebase ni `BuildConfig`), no hay `!!`, `GlobalScope`, `assert()` ni logs, y los secretos no están en el
   código. 647 pruebas pasan. No encontré ningún hallazgo **Crítico**.
2. **Problema 1 (Alto): con `BACKEND=firebase` hay cambios de comportamiento que no están en la lista de cambios
   deliberados:** la contraseña ahora exige una minúscula también con Firebase (F-01) y `minSdk` pasó de 24 a 26, con
   `allowBackup=false`, mientras el README sigue diciendo 24 (F-02).
3. **Problema 2 (Alto): la pieza servidor de la verificación del correo de recuperación (Edge Function + `0006` + llamadas
   `functions-kt`/`rpc`) no tiene ninguna prueba que la ejecute** (F-03). Solo se prueba el mapeo de respuestas. Hasta
   desplegarla y probarla a mano, es la parte más arriesgada.
4. **Problema 3 (Medio): robustez y seguridad de borde:** el arranque puede quedar en el *splash* para siempre si falla
   la lectura de DataStore (F-04), los tokens de sesión de supabase-kt se guardan sin cifrar (F-05) y la Edge Function
   puede usarse para enviar correos a cualquier destinatario (F-06).
5. **Deuda de diseño (Medio):** interfaces de repositorio que crecen con banderas y métodos que lanzan `Unknown` por
   defecto (F-07), flujo de Google duplicado y atado a la composición (F-08), ningún texto en `strings.xml` (F-09,
   heredado de `main`) y documentación desactualizada (F-13). Nada de esto impide la prueba final.

## 2. Tabla de hallazgos

Gravedad: Crítico / Alto / Medio / Bajo. Esfuerzo: S (menos de 1 h), M (medio día), L (más de un día).

| ID | Gravedad | Categoría | Archivo : línea | Qué pasa | Por qué importa | Corrección sugerida | Esfuerzo |
|---|---|---|---|---|---|---|---|
| F-01 | Alto | Modo Firebase | `ui/auth/register/RegisterUiState.kt:3-25`, `RegisterScreen.kt` (fila "Incluye una minúscula"), `ChangePasswordScreen.kt`, `NewPasswordScreen.kt` | `PasswordRules.allSatisfied` ahora exige `a-z`. Con Firebase, que no lo exige, el registro muestra un cuarto requisito y rechaza contraseñas que antes eran válidas (p. ej. `ABC12345!`). Lo documenta `supabase-configuracion.md`, pero no está en la lista de cambios deliberados. | Cambia el comportamiento de `main` si esto se lleva allí; la política debería depender del backend, no ser común. | Decidir: (a) aceptarlo para ambos y decirlo en el PR, o (b) que `PasswordRules` reciba la política del repositorio (`AuthRepository.requiresLowercasePassword`) y que Firebase mantenga 3 reglas. | S |
| F-02 | Alto | Modo Firebase / Docs | `app/build.gradle.kts:32` (`minSdk = 26`), `AndroidManifest.xml:10` (`allowBackup=false`), `res/xml/backup_rules.xml`, `data_extraction_rules.xml`, `README.md` (Requisitos) | `minSdk` subió de 24 a 26 (supabase-kt) y se desactivó el respaldo; son cambios de producto que afectan a Firebase. El README sigue diciendo "`minSdk` = 24 (Android 7.0)". Parte viene de `mantenimiento/calidad-main` (Etapa 0), por lo que desaparecerían del diff si esa rama entra primero a `main`. | Se dejan sin soporte teléfonos Android 7.x y la documentación miente. El informe de evaluación (Etapa 0) ya lo marcaba como "decisión del equipo". | Acordarlo con el equipo; corregir el README (minSdk 26, Android 8.0). Si no se acepta, desugaring o mantener 24 en una rama sin Supabase. | S |
| F-03 | Alto | Pruebas / Servidor | `data/supabase/RecoveryEmailCodeApi.kt:35-62`, `supabase/functions/send-recovery-email-code/index.ts`, `0006_*.sql` | `SupabaseRecoveryEmailCodeApi` (`functions(...)` + `rpc`) no tiene pruebas; `RecoveryEmailCodeApiTest` solo cubre funciones puras. La Edge Function y la función SQL no tienen pruebas ni se tiparon/ejecutaron. Contratos asumidos sin verificar contra un servidor: `RestException.error` trae el cuerpo JSON, `rpc(...).data` es un string JSON, `Content-Type` + `setBody(String)`, formato del hash idéntico en TS y SQL. | Es el único flujo cuyo fallo no se vería en las 647 pruebas. Un desajuste en el hash o en el JSON rompe la verificación entera. | Antes de la prueba final: desplegar y probar de punta a punta (checklist en la sección 8); correr `deno check index.ts`; añadir una prueba de contrato del hash (un vector fijo `userId:código` → hex esperado, en TS y en SQL con la consulta f) y, si se acepta una dependencia de pruebas, Ktor `MockEngine` para `SupabaseRecoveryEmailCodeApi`. | M |
| F-04 | Medio | Corrutinas / Robustez | `ui/startup/StartupViewModel.kt:31-47` | La corrutina de arranque no tiene `try/catch`. Si `sessionPreferences.isRecoveryInProgress()` (lectura de DataStore) o `setRecoveryInProgress(false)` lanzan `IOException`/`CorruptionException`, `startRoute` nunca se asigna y `setKeepOnScreenCondition { startRoute == null }` deja el *splash* para siempre. | Un fallo raro de disco deja la app inutilizable hasta borrar datos. Antes de C3 el arranque no tenía esa dependencia. | `try { ... } catch (e: Exception) { _startRoute.value = Login }` (y cerrar sesión por seguridad si había marca). Añadir una prueba. | S |
| F-05 | Medio | Seguridad | `di/AppContainer.kt:100-106` (`install(Auth)` sin `sessionManager` propio) | supabase-kt guarda el *refresh token* con su `SettingsSessionManager` por defecto, es decir en `SharedPreferences` **sin cifrar**. Está protegido por el *sandbox* y por `allowBackup=false`, pero no por Keystore. | Un teléfono con root o un respaldo ADB de un teléfono *debuggable* expone la sesión completa. Es la práctica por defecto de la librería, no un descuido, pero la guía de seguridad de Android pide cifrar credenciales. | Un `SessionManager` propio sobre `EncryptedSharedPreferences`/Keystore (`androidx.security`, requiere dependencia: preguntar). Mientras tanto, documentarlo como limitación. | M |
| F-06 | Medio | Seguridad / Edge Function | `index.ts` (validación del correo y límites), `0006` (`delete from recovery_email_codes` en `verify_...`) | La función envía un correo de marca NightWatch a **cualquier** dirección que escriba un usuario autenticado; los límites (60 s, 5 por hora) son por usuario, y crear cuentas es gratis. Además los contadores viven en la misma fila del código: se borran al verificar con éxito y al fallar Brevo, así que quien controla su propio buzón puede reiniciarlos. | Se puede usar como relé de spam/phishing y agotar el límite diario de Brevo (300/día en el plan gratuito, compartido con Auth). | Límite global por día en la función (contador en otra tabla o `count` de envíos en la última hora), no borrar los contadores al verificar (guardar `sent_count`/ventana aparte o dejar la fila con `code_hash` anulado). Aceptable para la demo del curso; documentarlo. | M |
| F-07 | Medio | Arquitectura | `data/auth/AuthRepository.kt:19,34,40,51,57,60`, `data/account/AccountRepository.kt:43-49`, `data/profile/ProfileRepository.kt:34-51` | Las interfaces crecieron con banderas de capacidad (`usesGoogleNonce`, `recoversPasswordWithCode`, `changesEmailWithCode`, `verifiesRecoveryEmailWithCode`) y métodos con cuerpo `throw AuthError.Unknown`. `usesGoogleNonce` además significa dos cosas ("hay nonce" y "distingue cancelar de error"). | Violan Interface Segregation y Liskov (Firebase "implementa" operaciones que lanzan). Un llamador que olvide mirar la bandera obtiene `Unknown`. La guía de arquitectura prefiere contratos pequeños. | Separar en interfaces por capacidad (`CodeSignUp`, `CodeRecovery`, `GoogleNonceSignIn`) y que el contenedor entregue `null`/un objeto `Unsupported`; o, como mínimo, separar `usesGoogleNonce` en dos propiedades. | M |
| F-08 | Medio | Arquitectura / Compose | `ui/auth/login/LoginScreen.kt:97-106`, `ui/auth/register/RegisterScreen.kt` (bloque `onGoogleSignInClicked`), `LoginViewModel.kt:101-129`, `RegisterViewModel.kt:82-110`, `data/auth/GoogleNonce.kt:36-50` | El flujo de Google (begin → Credential Manager → finish → repositorio) está copiado en dos pantallas y dos ViewModels, y la pantalla orquesta pasos de negocio (nonce) con `rememberCoroutineScope`. Si el usuario rota el teléfono con el selector abierto, la corrutina se cancela y el resultado se pierde; el nonce pendiente queda en el ViewModel. | Duplicación y lógica en la UI; un cambio en el flujo hay que hacerlo dos veces. | Un `GoogleSignInController` (o un `rememberGoogleSignIn(onResult)`) compartido, y que el ViewModel reciba el resultado ya clasificado (`GoogleCredentialResult`). | M |
| F-09 | Medio | Recursos (heredado) | todo `ui/`; `data/auth/AuthError.kt`, `ui/verifycode/VerifyCodeViewModel.kt:28-52`, `RecoveryEmailViewModel.kt:24-47` | Ningún texto está en `strings.xml` (solo `app_name`; igual en `main`). El código nuevo añade más de 100 textos, y algunos viven en la capa de datos (`AuthError`) y en ViewModels (`VerifyCodeError`, `MESSAGE_*`). Existe una pantalla "Idioma" pero nada es traducible. | La guía pide recursos de cadena; texto en ViewModels/datos impide localizar y acopla capas. | Migrar por pantalla a `stringResource`, y que los ViewModels expongan *tipos* de error (ya lo hacen en `VerifyCodeError`/`RecoveryEmailError`) que la UI traduce. Es una tarea del proyecto entero, no solo de esta rama. | L |
| F-10 | Medio | Arquitectura / DI | `di/AppContainer.kt:139,145,151,176`, `data/supabase/SupabaseErrors.kt:6,121`, `SupabaseSessionUser.kt` | `if (supabaseAuthRepository != null) … else …` se repite 4 veces con un `supabaseAuthRepository` anulable como interruptor; el código de Supabase lanza `FirestoreAccessException` (nombre y paquete de Firebase). | Fácil de olvidar un `else`; el nombre confunde y acopla Supabase a un tipo de Firestore. | `private val backend`-based `when` que construya un `Repositories` por backend; renombrar a `DataAccessException`/`data.common`. | M |
| F-11 | Medio | Corrutinas / Sesión | `ui/auth/forgotpassword/PasswordRecoveryDialog.kt:68-79`, `ui/verifycode/CodeFlow.kt:70-80` | **Plausible, no reproducido:** si la persona cierra el diálogo ("Volver al menú") mientras `verifyRecoveryCode` está en vuelo, la verificación termina, abre una sesión de recuperación y el evento `Verified` no tiene a nadie que lo consuma: queda una sesión abierta sobre el Login. La marca de C3 la cierra en el siguiente arranque, no antes. | Ventana corta pero es una sesión sin contraseña tecleada. | En `onDismiss`, cancelar la verificación o, si la sesión llegó a abrirse, cerrar sesión y borrar la marca; o deshabilitar "Volver al menú" mientras `isConfirming`. | S |
| F-12 | Medio | Arquitectura | `ui/navigation/NightWatchDestinations.kt:62` (`EmailUpdated(notice: String?)`), `ui/verifycode/CodeFlow.kt:47-53` | Un texto de interfaz en español viaja dentro de la ruta de navegación, y `EmailChangeCodeFlow` guarda estado mutable (`completionNotice`) que el ViewModel lee después de `confirm` (acoplamiento temporal). | Texto en rutas se serializa en la URL interna y no se puede localizar; el estado en una estrategia es frágil si se reutiliza. | Pasar un booleano/`enum` (`googleUnlinkFailed`) y que `confirm` devuelva un resultado (`CodeResult(notice: Notice?)`) en vez de dejar estado. | S |
| F-13 | Medio | Documentación | `README.md` (Requisitos: minSdk 24 y Android 7.0; sin mención de Supabase, `BACKEND` ni `local.properties`), `docs/superpowers/supabase-configuracion.md` ("este bloque no envía ninguno", tabla del Bloque A "Perfil: correo de recuperación… Funciona"), `docs/superpowers/reports/supabase-evaluacion.md` (informe de la Etapa 0 sin aviso de ser histórico), `specs/2026-10-09-profile-settings-design.md` ("cambio de correo solo con enlace") | Varias frases ya no describen lo que hace el código. | Quien lea el README no sabe que existe `BACKEND`; el doc de configuración se contradice entre bloques. | Actualizar README (minSdk, sección Supabase, nº de pruebas), corregir esas dos frases del doc, y poner un encabezado "histórico" en el informe de la Etapa 0 y una nota en el spec. | S |
| F-14 | Medio | Compose / UX (lint) | `ui/auth/GoogleSignIn.kt:42-54` | Lint `CredentialManagerMisuse`: `NoCredentialException` (el teléfono no tiene cuenta de Google) cae en `Failed` y muestra el diálogo genérico en vez de guiar a agregar una cuenta. | Es el caso más común en un emulador sin cuenta; el mensaje no ayuda. | Capturar `NoCredentialException` y devolver un resultado propio (`NoAccount`) con un texto específico. | S |
| F-15 | Medio | Pruebas | `data/account/SupabaseAccountRepositoryTest.kt:54-72`, `data/auth/SupabaseAuthRepositoryTest.kt` (mismo patrón), `data/supabase/RecoveryEmailCodeApiTest.kt` (`theNamesMatchTheServerSide`) | Los repositorios se prueban con un `java.lang.reflect.Proxy` sobre `Auth` que enruta por nombre de método y por índice de argumento (`args[2]`); `theNamesMatchTheServerSide` compara una constante con su propio literal. No hay pruebas de las pantallas nuevas ni de teclado/diseño (C1, E1, E2, F1, F2); las `androidTest` no se ejecutan en la validación. | El `Proxy` se rompe en silencio si la librería cambia una firma; algunas pruebas no prueban lo que dicen. Usan bien Turbine y `MainDispatcherRule`, y no hay `assert()`. | Envolver `Auth` en una interfaz propia pequeña (`AuthApi`) y usar un fake normal; quitar o reemplazar la prueba tautológica; añadir 2-3 pruebas de Compose para `VerifyCodeContent` y `BottomActionScrollLayout`. | M |
| F-16 | Bajo | Accesibilidad | `ui/verifycode/VerifyCodeScreen.kt:243-254` (`ResendLink`), `:270-343` (`CodeInput`) | El enlace "Reenviar" mide unos 35 dp de alto (13 sp + 16 dp), por debajo de los 48 dp; el campo del código no tiene etiqueta ni descripción para lectores de pantalla (en el estilo de casillas el campo real mide 0 dp). | Guía de accesibilidad: objetivos táctiles ≥ 48 dp y campos con nombre. | `Modifier.minimumInteractiveComponentSize()` y `semantics { contentDescription = "Código de 6 dígitos" }`; opcional `ContentType.SmsOtpCode` para autocompletar. | S |
| F-17 | Bajo | Textos | `VerifyCodeScreen.kt:232`, `VerifyCodeViewModel.kt:75` | "Ingrese el codigo de 6 digitos" y "Reenviar el código de 6 digitos": faltan tildes. (Pueden venir del diseño, pero en la app se ven mal.) | Calidad percibida. | Corregir y ajustar las pruebas que comparan el texto. | S |
| F-18 | Bajo | Compose (heredado) | `LoginScreen.kt:65,68`, `RegisterScreen.kt:69`, `PlacementQuizScreen.kt:54`, `PlacementResultScreen.kt:43`, `ForgotPasswordScreen.kt:34` | Cinco pantallas usan `collectAsState()` (sin ciclo de vida) mientras las nuevas usan `collectAsStateWithLifecycle()`; `dialogKind` del Login usa `remember` (no sobrevive a la rotación) mientras `recoveryPrefill` usa `rememberSaveable`. | Inconsistencia; el estado se sigue recolectando en segundo plano. | Cambiar a `collectAsStateWithLifecycle()` y a `rememberSaveable` donde corresponda. | S |
| F-19 | Bajo | Kotlin | `LoginScreen.kt` (`MaterialTheme`), `RegisterScreen.kt` (`clickable`, `R`), `test/.../SupabaseErrorsTest.kt` (`AuthErrorCode`) | Importaciones sin usar. | Ruido; `R` quedó sin uso al centralizar el web client id. | Borrarlas (Optimize Imports). | S |
| F-20 | Bajo | Capas | `data/auth/SupabaseAuthRepository.kt:73` | `val currentUser: UserInfo?` es público y expone un tipo de la librería (lo usa `SupabaseAccountRepository`). No llega a las interfaces ni a la UI. | Fuga menor de la librería. | Marcarlo `internal` y mapear a un `AccountProfile`. | S |
| F-21 | Bajo | Kotlin / Compose | `ui/components/KeepVisibleWithKeyboard.kt:25`, `ChangeEmailScreens.kt:131-133` | El modificador usa `composed` (la guía actual recomienda `Modifier.Node`); las constantes `internal val EMAIL_FIELD_TOP_PADDING…` van en MAYÚSCULAS sin ser `const`. | Convenciones de Compose y de Kotlin. | `Modifier.Node` o dejarlo con un comentario; renombrar a camelCase. | S |
| F-22 | Bajo | Duplicación | `PasswordRecoveryViewModel.kt:35`, `ChangeEmailViewModel.kt:131`, `RecoveryEmailViewModel.kt:47`; `RegisterScreen.kt:257` y `ChangePasswordScreen.kt:170` | El texto "Espera un momento antes de pedir otro código." está en 3 sitios; el verde `0xFF4CAF50` en 2; `PasswordRuleRow` (registro) y `PasswordRequirements` (cambiar contraseña) son casi iguales. | Un cambio hay que hacerlo varias veces. | Una constante/composable compartido y un color de tema (`NightWatchSuccess`). | S |
| F-23 | Bajo | Compose / Ciclo de vida | `PasswordRecoveryDialog.kt:53-56,68-71` | Cada apertura del diálogo crea `PasswordRecoveryViewModel` y `VerifyCodeViewModel` con una clave nueva bajo la entrada de Login; se acumulan hasta que se sale del Login (sus cuentas regresivas terminan solas). | Fuga pequeña de memoria. | Una sola clave fija y un `reset()` al abrir, o hospedar el estado en un ViewModel del Login. | S |
| F-24 | Bajo | SQL | `0006` y `0001` | (a) `profile.avatar_path` no valida en el servidor que apunte a la carpeta del usuario (la app lo valida y Storage protege la lectura); (b) los `recovery_email` guardados antes de `0006` no se re-verifican; (c) `user_id` en el `GRANT UPDATE` es una desviación documentada de la lista pedida. | Defensa en profundidad. | (a) `CHECK (avatar_path like user_id::text || '/%')`; (b) aceptarlo o limpiarlos en una migración; (c) confirmar con la prueba D-8. | S |
| F-25 | Bajo | Corrutinas | `ui/verifycode/VerifyCodeViewModel.kt:102-107`, `ui/startup/StartupViewModel.kt`, `NightWatchApplication.kt` | Tras una muerte del proceso en la pantalla del código, la cuenta regresiva y el cálculo de "expiró" empiezan de nuevo; `NightWatchApplication` sigue usando `runBlocking` en el hilo principal (heredado de `main`). | Mensajes de expiración imprecisos y un bloqueo breve al arrancar. | Guardar `codeSentAtMillis` en `SavedStateHandle`; mover la lectura a una corrutina. | S |
| F-26 | Bajo | Modo Firebase | `ui/profile/devices/DevicesViewModel.kt:114-131` | En "Cerrar sesión en todos" sin otros dispositivos, antes el aviso salía al instante y ahora pasa por `isWorking` y se pierde si `currentUserUid` es nulo. Con Firebase `signOutOtherSessions()` no hace nada. | Cambio mínimo, no está en la lista de deliberados. | Aceptarlo o mostrar el aviso antes de `requireUid`. | S |
| F-27 | Bajo | UX / Compose | `ui/components/NightWatchBackground.kt` + manifest `adjustResize` | Con `enableEdgeToEdge()` y `imePadding()` en el contenedor común, en Android 8-10 (API 26-29) el teclado también redimensiona la ventana (`adjustResize`): hay que comprobar que no se sume el relleno dos veces. | Solo se verificó que compila. | Probar en un teléfono/emulador con Android 8-10; si se duplica, quitar `adjustResize`. | S |

## 3. Qué está bien hecho y conviene mantener

- **Capas respetadas:** ninguna pantalla ni ViewModel importa `io.github.jan.supabase`, `com.google.firebase` ni
  `BuildConfig` (verificado con `grep`). Solo `AppContainer` y los repositorios conocen las librerías, y la elección del
  backend vive en un solo sitio.
- **Flujo unidireccional:** `UiState` inmutable + `StateFlow`, eventos de una sola vez por `Channel`, navegación tipada
  con `@Serializable`. Los repositorios exponen tipos de dominio (`AuthError`, `RemoteProfile`, `DeviceRecord`).
- **Un solo componente de código de 6 dígitos** (`VerifyCodeViewModel` + `VerifyCodeContent` + `CodeFlow`) para registro,
  cambio de correo, recuperación y correo alterno, con pruebas del ViewModel y de cada estrategia.
- **Sesión bien tratada:** `signOut()` limpia la sesión local aunque falle el servidor, `awaitSessionReady()` tiene tiempo
  máximo, la recuperación guarda la marca *antes* de pedir la verificación y la cierra en tres sitios, y la revocación
  real (`signOut(OTHERS)`) va antes de marcar dispositivos.
- **Errores:** `mapSupabaseAuthError`/`supabaseCall` nunca guardan ni muestran la excepción (su mensaje incluye cabeceras con
  el token); `CancellationException` se relanza en todos los `catch`.
- **Seguridad del nonce:** 32 bytes de `SecureRandom`, SHA-256 en hexadecimal a Credential Manager y el nonce crudo a
  Supabase; no hay logs de tokens, códigos ni nonces (no hay ninguna llamada a `Log` en el módulo).
- **SQL y Edge Function:** RLS en todas las tablas, privilegios mínimos con `REVOKE` explícitos, funciones `SECURITY
  DEFINER` con `search_path` vacío, solo el *hash* del código (con el `user_id` dentro), intentos limitados en el servidor,
  la Edge Function no devuelve ni registra el código ni el hash y solo registra `error.code` de Postgres.
- **Pruebas:** 647, con `MainDispatcherRule`, Turbine, tiempo virtual para las cuentas regresivas, relojes inyectables y
  sin `assert()`. Se prueba el caso de borde de cada decisión (expiración, límite de envíos, marca de recuperación,
  desvínculo de Google que falla sin romper el cambio de correo).
- **Documentación de decisiones:** cada compromiso (enumeración de cuentas, "expirado" decidido en el cliente, token de
  acceso que sigue vivo tras `signOut(OTHERS)`, `GRANT UPDATE (user_id)`) está escrito junto al código y en
  `supabase-configuracion.md`.
- **Compose:** estado elevado en las pantallas nuevas, `collectAsStateWithLifecycle()`, `Modifier` en los componentes
  reutilizables de bajo nivel (`CodeInput`, `CodeHint`, `ResendLink`, `BottomActionScrollLayout`), un solo contenedor
  (`NightWatchScreenBackground`) para el teclado y `BottomActionScrollLayout` común para "pantalla con botón abajo".

## 4. Modo Firebase (`BACKEND=firebase`)

Resultado: **el flujo de datos de Firebase no cambió** (los repositorios Firebase solo ganaron parámetros ignorados:
`signInWithGoogle(idToken, nonce)`), pero hay **diferencias visibles** además de las deliberadas.

| Cambio | ¿Deliberado en tu lista? | Detalle |
|---|---|---|
| C1 (quiz y resultados), E1/F1 (teclado y botón), E2/F2 (Confirma tu identidad) | Sí | Solo interfaz, ambos backends. |
| Texto de Dispositivos sin "Firebase" | Sí | |
| **Regla de minúscula en la contraseña** | **No** | F-01. Registro, Cambiar contraseña y contraseña nueva. |
| **`minSdk` 26, `allowBackup=false`, reglas de respaldo** | **No** (viene de Etapa 0 y de `mantenimiento/calidad-main`) | F-02. |
| Arranque por `StartupViewModel` | No, pero equivalente | Con Firebase `awaitSessionReady()` no espera nada; solo añade una lectura de DataStore (ver F-04). La ruta inicial es la misma. |
| `NightWatchApplication` cierra sesión vía `authRepository.signOut()` | No, equivalente | Antes `FirebaseAuth.getInstance().signOut()`. |
| "Cerrar sesión en todos" sin otros dispositivos | No | F-26, diferencia mínima. |
| `EmailUpdated()` con parámetro y `ForgotPasswordScreen` reestructurada (scroll) | Parte de E1 | Misma apariencia sin teclado. |

## 5. Las advertencias de lint

`lintDebug` termina con **0 errores y 38 advertencias**, que el reporte SARIF agrupa en **11 reglas** (las "11" de la
petición). Ninguna es nueva respecto a lo que ya existía, salvo `CredentialManagerMisuse` (código nuevo) y `UnusedResources`
(plantillas de `colors.xml`).

| Regla (nº) | Qué es | ¿Vale la pena? |
|---|---|---|
| `CredentialManagerMisuse` (1) | No maneja `NoCredentialException` (`GoogleSignIn.kt:42`) | **Sí**: F-14. |
| `GradleDependency` (9) y `NewerVersionAvailable` (7) | Hay versiones nuevas (Compose BOM 2026.09, Navigation 2.10, supabase-kt 3.8.0, Ktor 3.6.0, Kotlin 2.4.21...) | **No ahora.** Kotlin, AGP, supabase-kt y Ktor están fijados a propósito (3.8.0 no compila con Kotlin 2.2.10). Planificar una actualización conjunta. |
| `AndroidGradlePluginVersion` (1) | AGP 9.4.1 disponible | No ahora (el README pide no actualizar AGP sin acordarlo). |
| `UnusedResources` (9) | `purple_*`, `teal_*`, `black`, `white` de `colors.xml` y `ic_launcher_background/foreground` | Fácil de limpiar (S). |
| `IconXmlAndPng` (3) | Los íconos `webp` duplican el ícono adaptativo XML | Con `minSdk 26` los `webp` sobran: se pueden borrar (S). |
| `UseKtx` (3) | `toUri`, `Bitmap.scale` | Opcional (S). |
| `ExifInterface` (2) | `android.media.ExifInterface` en `BitmapAvatarImageProcessor` | Opcional; la versión de AndroidX corrige fallos de seguridad antiguos (requiere dependencia). |
| `InlinedApi` (1) | `POST_NOTIFICATIONS` (API 33) en `NotificationSettingsScreen.kt:46` | Falso positivo en la práctica: el permiso solo se pide si `requiresRuntimePermission` (SDK ≥ 33). Se puede suprimir con `@RequiresApi`/`@SuppressLint` explicado. |
| `RedundantLabel` (1) | `android:label` repetido en `MainActivity` | Trivial (S). |
| `VectorRaster` (1) | `ic_confirm_check.xml` mide 203 dp (límite 200) | Trivial (S). |

## 6. Cobertura de la lista de revisión

1. **Arquitectura y capas:** ver sección 3 (bien) y F-07, F-08, F-10, F-20, F-22. Duplicación entre flujos: ya resuelta para
   los códigos; queda la de Google (F-08) y pequeñas (F-22).
2. **Compose:** F-08, F-18, F-21, F-23, F-27. `@Preview`: el proyecto no usa ninguna (ni en `main`), así que no se exige en
   las pantallas nuevas. El estado de los formularios vive en el ViewModel (sobrevive a rotación y tema, no a la muerte
   del proceso, lo cual es razonable para contraseñas).
3. **Recursos y accesibilidad:** F-09, F-16, F-17, F-22. Teclados correctos (`Email`, `Number`, `Password`) en todos los
   campos nuevos. Colores sueltos: varios `Color(0xFF…)` fuera del tema (heredados) más los dos duplicados de F-22.
   Ilustraciones decorativas con `contentDescription = null`.
4. **Corrutinas:** F-04, F-11, F-25. Sin `GlobalScope`; `viewModelScope` en todos los ViewModels; cuenta regresiva con
   `Job` cancelable; relojes y alcance inyectables.
5. **Kotlin:** F-19, F-21. Sin `!!`, sin `TODO` (solo el texto "CERRAR SESIÓN EN **TODOS**"), `when` exhaustivos.
6. **Seguridad:** F-05, F-06, F-11, F-24. Sin secretos en el código (solo la llave **publicable** en `BuildConfig`, que es
   pública por diseño; `local.properties` y `google-services.json` fuera del repositorio); sin logs de datos sensibles.
7. **SQL y Edge Function:** F-03, F-06, F-24. Coherentes con la app: mismo formato de hash (`userId:código`), mismos límites
   (5 intentos, 10 min, 60 s, 5 por hora) y mismos códigos de error que `RecoveryEmailCodeApi.kt`. Comentarios al día.
8. **Pruebas:** F-03, F-15. Ningún `assert()`; Turbine y `MainDispatcherRule` bien usados.
9. **Modo Firebase:** sección 4.
10. **Lint:** sección 5.
11. **Documentación:** F-13.

## 7. Riesgos aceptados que no son hallazgos

- **Enumeración de cuentas** (`account_status`, migración `0005`): compromiso del curso, documentado con sus mitigaciones.
- **Token de acceso de otras sesiones sigue válido hasta que expira** tras `signOut(OTHERS)`: limitación de Supabase, documentada.
- **"Incorrecto" y "expirado" indistinguibles para el servidor:** la app decide "expiró" con su reloj (600 s), documentado.
- **Remitente `@gmail.com` por Brevo:** puede caer en spam; documentado.

## 8. Recomendación

**Corregir antes de la prueba final (todo S salvo F-03):**

1. **F-03:** desplegar `0006` y la función y hacer la prueba de punta a punta (lista D de las pruebas en teléfono). Verificar en
   especial D-8 (que guardar género, fecha y foto sigue funcionando tras `0006`). Correr `deno check` sobre `index.ts`.
2. **F-01 y F-02:** decidir (contraseña por backend o aceptarla; `minSdk` 26) y reflejarlo en el README.
3. **F-04:** `try/catch` en el arranque. Una prueba.
4. **F-11:** cerrar la ventana del diálogo de recuperación durante la verificación.
5. **F-14:** mensaje para "sin cuenta de Google en el teléfono" (será el primer fallo del evaluador en un emulador).
6. **F-13, F-17, F-19:** README y frases del doc, tildes y importaciones sin usar.
7. **F-27:** probar el teclado en un Android 8-10 si hay uno disponible.

**Puede esperar (después de la entrega o en una rama aparte):** F-05 (cifrar la sesión), F-06 (límite global del correo), F-07 y
F-10 (rediseño de interfaces y del contenedor), F-08 (controlador de Google), F-09 (migrar a `strings.xml`, tarea del
proyecto entero), F-12, F-15 (envolver `Auth`), F-16, F-18, F-20 a F-26 y la limpieza de lint (`UnusedResources`,
`IconXmlAndPng`, versiones).
