# Perfil y Ajustes (Módulo 2) — Spec de diseño

> **Documento histórico.** Describe el proyecto cuando usaba Firebase (versión 1.x): el cambio de correo por enlace y el
> registro de dispositivos en Firestore ya no existen. La app hoy usa solo Supabase: ver
> [`migracion-supabase.md`](../reports/migracion-supabase.md).

Figma: https://www.figma.com/design/wd3fQTzF5NqsfGfdR4I1lb/NightWatch-Protocol--copia-?node-id=0-1
(fileKey `wd3fQTzF5NqsfGfdR4I1lb`; pantallas oscuras, nodos `2068:*`, `2087:*`, `2106:*`, `2123:*`, `2287:*`, `2288:*`, `2289:*`, `2290:*`).

Estado: **borrador v3 para aprobación. Cambios respecto a la v2: bloqueo biométrico real (3c), "Mi cuenta" sin quitar nada del Main Menu (P4), pantalla "Confirma tu identidad" (3d, con P12 pendiente) y cambio de correo solo con enlace (P3). No se programa nada hasta que lo confirmes.**

## 1. Alcance

**Dentro:** las 23 pantallas oscuras de Perfil y Ajustes. Son 20 rutas, porque 3 de las 23 son estados de una misma pantalla (código incorrecto, error de contraseña actual y error de no coinciden). Se suma una pantalla que Figma no tiene, "Confirma tu identidad" (sección 3d): 21 rutas en total.

**Fuera:** las versiones "(Claro)", Core Learning (mapa, lecciones, labs, examen), Social, y cualquier cambio de diseño o lógica en Login, Registro, Recuperar contraseña, Quiz y Resultado (salvo lo mínimo para conectar este módulo).

**Reglas de trabajo (de tu brief):** MVVM, un `UiState` por pantalla, `StateFlow`, eventos por `Channel`, rutas tipadas `@Serializable`, DI manual en `AppContainer`, repositorios con interfaz y fakes, `AuthErrorMapper`. Se reutiliza `NightWatchTextField` y el sistema de diseño. Los textos siguen en el código. Plan Spark: sin Cloud Functions ni servidores.

## 2. Decisiones ya tomadas (resumen)

| # | Decisión |
|---|---|
| 1 | Sin backend. Solo lo que Firebase hace solo. |
| 2 | **Cambio de correo SOLO con el enlace de Firebase** (`verifyBeforeUpdateEmail`), sin campo de código. Se conservan las 4 pantallas; "Verifica tu correo" se adapta (sección 4b). |
| 3 | Apariencia e Idioma se ven como en Figma, pero "Claro", "Automático" y "English" no hacen nada. La app sigue oscura y en español. |
| 4 | Dispositivos, racha (15 días) e insignias (3 de 9) son datos fijos. |
| 5 | Correo de recuperación en Firestore `users/{uid}/...`. Se amplían las reglas (sección 6) sin tocar `placement`. |
| 6 | Switches de Notificaciones y Privacidad en DataStore (`user_preferences`, por dispositivo). **"Bloqueo Biométrico" SÍ funciona** (sección 3c). "Ocultar mi progreso público" solo guarda el switch. "Descargar mis datos" exporta perfil + resultado del quiz. |
| 7 | Cuentas Google: aviso en vez del formulario de contraseña y del cambio de correo. |
| 8 | `versionName = "1.4.0"` (menú y Acerca de). |
| 9 | "Nivel actual" = BAJO / MEDIO / ALTO desde Firestore; "Sin evaluar" si no hay quiz. |
| 10 | "Cerrar Sesión" al final del Menú de usuario (encabezado fijo, resto con scroll, botón de solo borde con ícono de puerta, y debajo "Nightwatch Protocol · v1.4.0"). **El Main Menu conserva su "Cerrar sesión" y suma "Mi cuenta"**; los dos usan el mismo `authRepository.signOut()` y limpian la pila hacia Login. Sin diálogo. |
| 11 | Cambiar contraseña real, con 4 estados. "La contraseña actual no es correcta" sale de reautenticar. "No coinciden" se valida local. La nueva debe cumplir `PasswordRules`. En Editar Perfil la nueva contraseña y su verificación también son reales; el error de "inicio de sesión reciente" se maneja de forma clara. |
| 12 | **Avatar real (sección 3b):** muestra la inicial por defecto; "Cambiar Avatar" abre el selector de fotos del sistema y la foto se guarda en Firestore como Base64. Sin Storage, sin librerías nuevas. |
| 13 | **Bloqueo biométrico real (sección 3c):** `androidx.biometric`, `MainActivity` pasa a `FragmentActivity` (impacto en 3c, pendiente de tu confirmación P14). |
| 14 | **Pantalla "Confirma tu identidad" (sección 3d)** cuando Firebase exige login reciente. Solo contraseña. La huella para reautenticar (P12) NO se implementa hasta que la apruebes. |

## 3. Decisiones pendientes (necesito tu respuesta antes de la Etapa 1)

Cada una trae un valor por defecto. Si no dices nada, uso el valor por defecto.

- **P1 — Avatar: RESUELTA.** Ver sección 3b. Quedan dos detalles menores, P10 y P11, al final de esta lista.
- **P2 — Inicio de sesión reciente: RESUELTA** en una pantalla propia, "Confirma tu identidad" (sección 3d). La parte de huella queda en P12.
- **P3 — Pantalla "Verifica tu Correo": RESUELTA.** Solo enlace, sin campo de código (sección 4b).
- **P4 — Entrada al módulo: RESUELTA.** El Main Menu conserva "Cerrar sesión" y suma "Mi cuenta", que abre el Menú de usuario. No se elimina nada de la pantalla actual. El Menú de usuario también tiene "Cerrar sesión" abajo. Ambos usan `authRepository.signOut()` y navegan a Login limpiando la pila.
- **P5 — Menú de usuario.** En Figma parece un panel lateral con fondo oscurecido. *Por defecto:* pantalla completa con el mismo contenido y la "X" para cerrar.
- **P6 — "Miembro desde".** *Por defecto:* fecha real de creación de la cuenta (Auth), con formato "Ago. 2026".
- **P7 — Dispositivos.** *Por defecto:* tocar el dispositivo que no es el actual, o "Cerrar sesión en todos", abre la pantalla estática "¡Sesión cerrada!" sin efecto real. Ver riesgo R6.
- **P8 — Switches.** *Por defecto:* se guardan por dispositivo, no por cuenta (DataStore `user_preferences`).
- **P9 — Descargar mis datos.** *Por defecto:* el sistema pide dónde guardar un archivo `.json` (sin permisos extra) y se muestra un aviso breve de confirmación (texto que no está en Figma).

- **P10 — Cuándo se guarda la foto.** *Por defecto:* en cuanto la persona elige la imagen (no espera a "GUARDAR CAMBIOS"), porque el avatar tiene su propia caché y su propio estado de error. *Alternativa:* dejarla pendiente hasta guardar.
- **P11 — "Quitar foto" y aviso de error.** Figma no tiene ninguna de las dos cosas para el avatar. *Por defecto:* un segundo enlace "Quitar foto" debajo de "Cambiar Avatar", visible solo si hay foto, con el mismo estilo; y un texto corto en rojo bajo el avatar para los errores (estilo `#FF2E16`, el de los campos). Dime si el "aviso de error de guardado ya diseñado" que mencionaste es otro, porque no lo encuentro en Figma.

- **P12 — Huella para "Confirma tu identidad": PENDIENTE DE TU APROBACIÓN. No se implementa hasta que la apruebes.** Descripción, riesgos y alternativa en la sección 3d. *Mi recomendación:* la alternativa (sin huella en esa pantalla).
- **P13 — Cuándo se vuelve a pedir la huella.** *Por defecto:* siempre al iniciar la app desde cero (proceso nuevo) y al volver de segundo plano si pasaron **30 segundos o más**. Menos de 30 s no vuelve a pedirla (cambiar de app un momento o abrir la cámara para el avatar no te bloquea). *Alternativas:* 0 s (siempre), 1 min, 5 min. **Confirma el valor.**
- **P14 — `MainActivity` pasa a `FragmentActivity`.** `BiometricPrompt` lo exige. Impacto completo en la sección 3c. **No toco `MainActivity` hasta que lo confirmes.**
- **P15 — Detalles del bloqueo que Figma no define.** (a) Pantalla de bloqueo: fondo y logo, y un botón "Desbloquear" por si se cancela el aviso. (b) Textos del aviso del sistema: título "Desbloquear NightWatch", subtítulo "Usa tu huella para continuar". (c) Si el teléfono pierde la huella (el bloqueo sigue activo pero ya no hay biometría), el bloqueo se **desactiva solo** para no dejarte fuera de tu cuenta, y la próxima vez que abras Privacidad y Seguridad lo ves apagado. (d) En Android 10 o menor no existe el respaldo de PIN junto con huella fuerte; ahí el aviso solo ofrece huella y un botón "Cancelar". Valores por defecto en la sección 3c.

## 3b. Avatar (cambio P1)

**Mostrar.** `UserAvatar` es el único componente que dibuja el avatar y se usa en Menú de usuario, Mi Perfil, Perfil actualizado y Editar Perfil. Si hay foto la muestra; si no, la inicial del usuario (primera letra del nombre en mayúscula; si no hay nombre, la del correo; si no, "?"). La decisión "foto o inicial" es una función pura con pruebas; la UI solo la dibuja. La foto se decodifica fuera del hilo principal.

**Elegir.** "Cambiar Avatar" abre `ActivityResultContracts.PickVisualMedia` solo para imágenes. No se piden permisos de almacenamiento ni de cámara.

**Procesar (en `Dispatchers.IO`).**
1. Leer solo las dimensiones y decodificar con `BitmapFactory` y `inSampleSize` (sin cargar la imagen completa).
2. Aplicar la orientación EXIF con `android.media.ExifInterface` (viene con el sistema; con `minSdk 24` se puede leer desde un `InputStream`), porque `BitmapFactory` la ignora y las fotos de cámara saldrían rotadas.
3. Recorte cuadrado centrado y reducción a máximo 256x256.
4. Comprimir a JPEG calidad 80. Si pesara más de 100 KB, se baja la calidad por pasos (60, 40) hasta que entre. Una foto de 256x256 normalmente pesa 10 a 30 KB.
5. Si la imagen no se puede leer (dañada, formato raro, sin memoria): error corto bajo el avatar ("No pudimos leer esa imagen") y se conserva el avatar anterior.

**Guardar.**
- Firestore: campo `avatarBase64` del documento `users/{uid}/profile/main`, escrito con merge (no pisa `recoveryEmail`). 100 KB en Base64 son unos 137 000 caracteres, muy por debajo del límite de 1 MiB del documento.
- Caché local: un archivo en `filesDir` con nombre por usuario (`avatar_<uid>.jpg`), **no** DataStore, para no cargar 100 KB cada vez que se lee una preferencia. Al guardar se escribe primero la caché local (se ve al instante y sin conexión) y después Firestore.
- Firestore sin conexión no falla: deja la escritura en cola y la tarea queda pendiente (el mismo problema que tuvimos con el quiz). Por eso la escritura lleva un tiempo límite de 5 s. Si se agota o falla, la foto local se conserva, se muestra el aviso de error y Firestore sigue intentando sincronizar en segundo plano.
- Al abrir la app: se muestra la caché local y luego se refresca desde Firestore; si cambió, se actualiza la caché.

**Quitar foto.** Borra el campo (`FieldValue.delete()`) y el archivo local, y `UserAvatar` vuelve a la inicial. Si Firestore falla, igual se quita la local y se muestra el aviso.

**Estado compartido.** Un `AvatarRepository` único (en `AppContainer`) expone la foto actual como `StateFlow`. Así Menú, Perfil y Editar Perfil se actualizan juntos. Se vacía en memoria al cerrar sesión; el archivo queda con el uid en el nombre, así que otra cuenta en el mismo teléfono nunca ve la foto de la anterior.

## 3c. Bloqueo biométrico (reemplaza la decisión de "fila inerte")

**Interruptor.** En Privacidad y Seguridad, "Bloqueo Biométrico". Se guarda en DataStore `user_preferences` (clave `biometric_lock_enabled`, por dispositivo, apagado por defecto).

**Al activarlo.**
1. Se consulta `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`. Si no hay huella registrada, no hay lector o no está disponible, el interruptor queda apagado, no se abre ningún aviso y aparece un texto corto en rojo (`#FF2E16`) bajo la fila ("No hay una huella registrada en este teléfono", "Este teléfono no tiene lector de huella" o "El lector no está disponible ahora, inténtalo más tarde").
2. Si hay huella, se abre un aviso de prueba ("Activar bloqueo / Verifica tu huella para activarlo"). Si la verificación sale bien, el interruptor queda encendido y se guarda. Si se cancela o falla, queda apagado con el texto rojo "No se pudo verificar tu huella".
3. Para activarlo se exige huella (biometría fuerte); el respaldo de PIN solo existe en los desbloqueos posteriores, porque si no, un teléfono sin huella podría "activar" el bloqueo con solo el PIN.

**Al apagarlo.** Se apaga directo (para llegar ahí ya pasaste el bloqueo).

**Cuándo se pide la huella (con sesión iniciada y el bloqueo activo).**
- Al iniciar la app desde cero (proceso nuevo): antes de mostrar nada. El `NavHost` ni siquiera se compone hasta desbloquear. El valor del interruptor se lee de forma síncrona en el arranque, igual que `NightWatchApplication` ya lee `rememberSession`, para que no aparezca contenido ni un instante.
- Al volver de segundo plano: solo si pasaron **30 segundos o más** (P13, por confirmar). Se mide con `SystemClock.elapsedRealtime()` (monótono, no se engaña cambiando la hora) y el estado vive en un `ViewModel` para que girar la pantalla no cuente como segundo plano.
- Sin sesión (Login, Registro) nunca se bloquea. Al cerrar sesión, el estado de bloqueo se reinicia.
- El botón Atrás en la pantalla de bloqueo sale de la app.

**Autenticadores.** Android 11 o mayor: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (huella con respaldo de PIN/patrón del teléfono). Android 7 a 10: la librería no permite esa combinación, así que solo `BIOMETRIC_STRONG` con botón "Cancelar" (P15d).

**Si el teléfono pierde la huella con el bloqueo activo.** El bloqueo se desactiva solo (P15c) para no dejarte fuera de tu cuenta. Si prefieres lo contrario (obligar a iniciar sesión de nuevo), dímelo.

**Arquitectura (mismo patrón que el inicio de sesión con Google: el ViewModel decide y la pantalla ejecuta el aviso del sistema).**
- `BiometricCapabilities`: interfaz con la disponibilidad (disponible, sin huella registrada, sin hardware, no disponible, no soportado) y su implementación real.
- `AppLockPolicy`: función pura que decide si bloquear (arranque, sesión, interruptor, tiempo en segundo plano).
- `AppLockViewModel` (de la actividad): estado Bloqueado / Desbloqueado y marcas de tiempo.
- `AppLockScreen`: fondo, logo y botón "Desbloquear".
- `BiometricPrompter`: ayudante de UI que envuelve `BiometricPrompt` (necesita `FragmentActivity`).
- `PrivacySecurityViewModel`: lógica del interruptor.

**Impacto de pasar `MainActivity` a `FragmentActivity` (P14, pendiente de tu confirmación).**
1. **Superclase:** `FragmentActivity` extiende `ComponentActivity`. `setContent`, `enableEdgeToEdge()`, `installSplashScreen()` y `registerForActivityResult` (el selector de fotos del avatar) siguen funcionando igual. Las pruebas actuales con `createAndroidComposeRule<MainActivity>()` siguen siendo válidas.
2. **Dependencias:** `androidx.biometric:biometric` en `gradle/libs.versions.toml` y `app/build.gradle.kts` (arrastra `androidx.fragment`). Verifico la última versión estable en Maven antes de fijarla.
3. **Permiso:** la librería fusiona `USE_BIOMETRIC` en el manifest. Es un permiso normal de instalación: no hay diálogo ni petición en tiempo de ejecución. No hace falta tocar `AndroidManifest.xml`.
4. **Arranque:** `setContent` envuelve el `NavHost` con el contenedor de bloqueo. La decisión de pantalla inicial (Login o PostAuthGate) y el grafo de navegación no cambian. Se agregan `onStart` y `onStop` para marcar el tiempo en segundo plano.
5. **Aviso del sistema:** en Android 9 o mayor lo dibuja el sistema; en 7 y 8 lo dibuja la librería con un diálogo propio (R16).
6. **Alcance:** es el único cambio estructural fuera de `MainMenuScreen`. No se toca el tema, el splash ni el manifest.

## 3d. "Confirma tu identidad" (reemplaza el campo en línea de P2)

**Cuándo aparece.** Cuando Firebase lanza `FirebaseAuthRecentLoginRequiredException` (se mapea a `AuthError.RecentLoginRequired`): al cambiar la contraseña desde Editar Perfil y al pedir el cambio de correo. "Cambiar contraseña" no la necesita (ya pide la contraseña actual). Las cuentas de Google nunca llegan aquí (ven el aviso existente).

**Pantalla.** Ruta `ConfirmIdentity` (no está en Figma; usa el sistema de diseño): `ProfileTopBar` con la "X", título "Confirma tu identidad", texto "Por seguridad, ingresa tu contraseña actual para continuar.", un `NightWatchTextField` "Contraseña actual" y el botón "CONTINUAR".

**Resultado.** Reautentica con la contraseña. Si es correcta, vuelve a la pantalla anterior y esa pantalla reintenta la operación pendiente (el resultado se pasa por el `savedStateHandle` de la entrada anterior). Errores: "La contraseña actual no es correcta", "Sin conexión a internet", o un mensaje genérico (demasiados intentos, error inesperado).

### P12 — Huella para confirmar la identidad (NO se implementa hasta que la apruebes)

**Qué haría.** Si el bloqueo biométrico está activo, la pantalla ofrecería además "USAR HUELLA". Firebase solo acepta una credencial (la contraseña), no una huella, así que habría que guardar la contraseña cifrada en el teléfono.

**Cómo.**
- Clave AES-256/GCM en el Android Keystore con `setUserAuthenticationRequired(true)` (huella en cada uso, con `CryptoObject`), solo `BIOMETRIC_STRONG` y `setInvalidatedByBiometricEnrollment(true)` (la clave muere si cambian las huellas registradas).
- La contraseña cifrada y su IV se guardan en un archivo privado, por uid.
- Se guardaría solo después de que la persona escriba su contraseña correcta en esta pantalla con el bloqueo activo y marque una casilla nueva "Recordar con huella" (desmarcada por defecto).
- Se borra al cerrar sesión, al apagar el bloqueo, al cambiar la contraseña, si la clave se invalida o si la reautenticación con ese secreto falla.

**Riesgos.**
1. Deja en el teléfono un equivalente de credencial. Está cifrado con una clave que exige la huella, pero sigue siendo un secreto guardado.
2. Un teléfono comprometido o con root es más peligroso con este secreto que sin él.
3. Si cambian o agregan huellas, la clave se invalida (es lo que se busca, pero sorprende).
4. Si la contraseña cambia desde otro dispositivo, el secreto queda viejo: falla una vez, se borra y se pide la contraseña.
5. En Android 10 o menor, la configuración de la clave es distinta y no hay respaldo de PIN en este camino.
6. La contraseña pasa por memoria como `String` (inmutable) hasta que el recolector la limpia.
7. Mucho código para algo que se usa rara vez (≈5 archivos más). El cifrado real no se puede probar en JVM; se prueba con un `SecretVault` falso.

**Alternativa (mi recomendación).** La huella queda solo como bloqueo de la app, y "Confirma tu identidad" pide siempre la contraseña. No se guarda ninguna credencial en el teléfono. Perdemos la comodidad en un flujo que se usa pocas veces (cambiar contraseña o correo desde Editar Perfil).

## 4. Pantallas y rutas

Nuevas rutas en `NightWatchDestinations.kt`, todas `@Serializable`. Los nombres siguen el estilo actual (`Login`, `ForgotPassword`...).

| # | Pantalla Figma (nodo) | Ruta | Lógica |
|---|---|---|---|
| 1 | Menú de usuario (2068:30) | `UserMenu` | ViewModel (nombre, cierre de sesión) |
| 2 | Mi Perfil (2068:51) | `Profile` | ViewModel |
| 3 | Perfil actualizado (2087:104) | `ProfileUpdated` | estática |
| 4 | Editar Perfil (2106:30) | `EditProfile` | ViewModel |
| 5 | Confirmar cambio de correo (2106:50) | `ChangeEmail(newEmail)` | ViewModel |
| 6 | Verificar código (2106:61) + Código incorrecto (2106:71), adaptadas a enlace (sección 4b) | `VerifyEmailLink(newEmail)` | ViewModel (2 estados) |
| 7 | Correo actualizado (2106:82) | `EmailUpdated` | estática |
| 8 | Correo de recuperación (2123:30) | `RecoveryEmail` | ViewModel |
| 9 | Correo guardado (2123:41) | `RecoveryEmailSaved` | estática |
| 10 | Administrar dispositivos (2123:49) | `Devices` | estática (datos fijos) |
| 11 | Dispositivo desconectado (2123:65) | `DeviceSignedOut` | estática |
| 12 | Logros e insignias (2287:100) | `Achievements` | estática (3 de 9) |
| 13 | Notificaciones (2287:129) | `NotificationSettings` | ViewModel (DataStore) |
| 14 | Apariencia (2287:163) | `Appearance` | estática (inerte) |
| 15 | Idioma (2287:181) | `Language` | estática (inerte) |
| 16 | Privacidad y seguridad (2288:105) | `PrivacySecurity` | ViewModel (DataStore, bloqueo biométrico, exportar) |
| 17 | Cambiar contraseña + error actual + no coinciden (2289:107/122/138) | `ChangePassword` | ViewModel (3 estados) |
| 18 | Contraseña actualizada (2289:154) | `PasswordUpdated` | estática |
| 19 | Ayuda y soporte (2290:139) | `Help` | estática (4 preguntas) |
| 20 | Acerca de (2290:161) | `About` | estática |
| 21 | *(no está en Figma)* Confirma tu identidad (sección 3d) | `ConfirmIdentity` | ViewModel |

**Flujo de Editar Perfil → "GUARDAR CAMBIOS":**
1. Si cambió el nombre: `updateProfile`.
2. Si hay nueva contraseña: validar `PasswordRules` + coincidencia y cambiarla. Si Firebase pide login reciente, va a `ConfirmIdentity` y reintenta al volver.
3. Si cambió el correo: va a `ChangeEmail(newEmail)` en lugar de guardarlo directo (que también puede pasar por `ConfirmIdentity`).
4. Si no hubo cambio de correo, muestra `ProfileUpdated`.

**Menú y cierre de sesión.** El Main Menu conserva su botón "Cerrar sesión" y suma "Mi cuenta" (abre `UserMenu`). `UserMenu` tiene su propio "Cerrar sesión" al final. Los dos llaman a `authRepository.signOut()` y navegan a Login con `popUpTo<MainMenu> { inclusive = true }`, que limpia también todas las pantallas de Perfil apiladas encima.

## 4b. "Verifica tu correo" adaptada al enlace

Se conserva la pantalla de Figma (barra superior, título "Verifica tu Correo", ícono del sobre, botones) y se cambia solo lo que depende del código de 6 dígitos:
- Texto: "Enviamos un enlace de verificación a {correo nuevo}. Revisa tu correo y toca el enlace."
- El campo de 6 dígitos y su rótulo "Ingrese el código de 6 dígitos" se quitan (no hay nada que escribir).
- El botón pasa de "CONFIRMAR CÓDIGO" a "YA VERIFIQUÉ MI CORREO": vuelve a consultar a Firebase (`reload()` y comparar el correo).
- El enlace de abajo pasa de "Reenviar el código de 6 dígitos" a "Reenviar el enlace" (vuelve a llamar a `verifyBeforeUpdateEmail`).
- Si la consulta no ve el cambio, se muestra el estado del frame "Código incorrecto" con el texto "El enlace venció o no se ha verificado" y el botón "REINTENTAR", que vuelve a consultar.
- Si la consulta sí ve el correo nuevo, navega a "¡Correo actualizado!".

**Cuentas Google** (sin proveedor `password` en Auth): en Editar Perfil los campos de contraseña y correo se reemplazan por un aviso ("Tu cuenta usa Google: la contraseña y el correo se gestionan desde tu cuenta de Google"). `ChangePassword` y `ChangeEmail` muestran el mismo aviso en lugar del formulario.

## 5. Datos que se leen y escriben

| Dato | Dónde | Lee / escribe |
|---|---|---|
| Nombre | Firebase Auth `displayName` | lee; escribe con `updateProfile` |
| Correo | Auth `email` | lee; cambia con `verifyBeforeUpdateEmail` y se confirma con `reload()` |
| Contraseña | Auth | escribe: reautenticar con la actual + `updatePassword` |
| Miembro desde | Auth `metadata.creationTimestamp` | lee |
| ¿Cuenta con contraseña? | Auth `providerData` | lee |
| Nivel actual | Firestore `users/{uid}/placement/result` (ya existe, `QuizResultRepository`) | lee |
| Correo de recuperación | Firestore `users/{uid}/profile/main` `{ recoveryEmail, avatarBase64, updatedAt }` (nuevo) | lee / escribe |
| Avatar (JPEG ≤ 256x256, ≤ 100 KB, Base64) | Firestore `users/{uid}/profile/main.avatarBase64` + copia local `filesDir/avatar_<uid>.jpg` | lee / escribe / borra |
| Switches de Notificaciones (5), "Ocultar mi progreso público" y "Bloqueo Biométrico" (`biometric_lock_enabled`) | DataStore `user_preferences` (nuevo, separado de `session_preferences`) | lee / escribe |
| Disponibilidad de biometría | `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` | lee |
| Marca de tiempo al pasar a segundo plano | memoria (`ViewModel` de la actividad, `elapsedRealtime`) | lee / escribe |
| Contraseña cifrada con huella *(solo si apruebas P12)* | Android Keystore + archivo privado por uid | lee / escribe / borra |
| Racha 15 días, insignias 3 de 9, dispositivos | constantes en el código | solo lectura |
| Exportar datos | arma un JSON con nombre, correo, correo de recuperación, miembro desde, nivel y categorías débiles, fecha de exportación | lee (Auth + Firestore) |

Valores por defecto de los switches (de Figma): recordatorio diario, logros y racha en riesgo **encendidos**; nuevos laboratorios, novedades y "ocultar progreso" **apagados**.

## 6. Reglas de Firestore que hay que ampliar

Hoy solo existe la regla de `placement`. Se **agrega** un bloque para `users/{uid}/profile`. El bloque de `placement` queda idéntico.

**Corrección respecto a la v1 del spec:** el borrador anterior solo permitía los campos `recoveryEmail` y `updatedAt` y exigía `recoveryEmail` en toda escritura, así que habría rechazado el avatar. Este bloque permite también `avatarBase64` y trata los dos campos como opcionales (así se puede guardar solo el avatar, solo el correo, o quitar el avatar). Como las reglas aún no estaban publicadas, es una sola publicación. Pega esto completo en Firebase Console → Firestore → Reglas → Publicar:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // EXISTENTE — sin cambios
    match /users/{userId}/placement/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // NUEVO — correo de recuperación y avatar
    match /users/{userId}/profile/{document=**} {
      allow read, delete: if request.auth != null && request.auth.uid == userId;
      allow create, update: if request.auth != null && request.auth.uid == userId
        && request.resource.data.keys().hasOnly(['recoveryEmail', 'avatarBase64', 'updatedAt'])
        && (!('recoveryEmail' in request.resource.data)
            || (request.resource.data.recoveryEmail is string
                && request.resource.data.recoveryEmail.size() <= 254))
        && (!('avatarBase64' in request.resource.data)
            || (request.resource.data.avatarBase64 is string
                && request.resource.data.avatarBase64.size() <= 150000));
    }
  }
}
```

El límite de 150 000 caracteres deja margen sobre los ~137 000 que ocupan 100 KB en Base64.

Es un paso manual en la consola (yo no puedo publicarlo). **Hay que publicarlas antes de la Etapa 1**, porque Mi Perfil ya lee `profile/main`; sin ellas esa lectura falla con "permiso denegado" (la app lo tolera: muestra "—" y la inicial, pero no podrás probar nada real). Después de publicarlas, verifícalas en el Rules Playground con una lectura y una escritura de tu propio uid y otra de un uid ajeno.

## 7. Archivos

**Crear — datos y DI**
- `data/account/AccountRepository.kt` + `FirebaseAccountRepository.kt`: perfil leído de Auth, `updateDisplayName`, `changePassword(actual, nueva)`, `requestEmailChange(nuevo)`, `isEmailChangeVerified(nuevo)` (con `reload`), `reauthenticate(actual)`, `hasPassword`.
- `data/profile/ProfileRepository.kt` + `FirestoreProfileRepository.kt`: leer y guardar `recoveryEmail`, y leer, guardar y borrar `avatarBase64` (merge, con tiempo límite de 5 s).
- `data/avatar/AvatarRepository.kt` (+ implementación): une `ProfileRepository` con la caché local y expone el avatar actual como `StateFlow`; funciones `load`, `save`, `remove`.
- `data/avatar/AvatarCache.kt`: interfaz + `FileAvatarCache` (archivo en `filesDir` por uid).
- `data/avatar/AvatarImageProcessor.kt`: interfaz + `BitmapAvatarImageProcessor` (`BitmapFactory` + `ExifInterface` del sistema, recorte, reducción y JPEG en `Dispatchers.IO`).
- `data/avatar/AvatarDisplay.kt`: función pura que decide foto o inicial.
- `data/preferences/UserPreferences.kt`: switches y `biometric_lock_enabled` en DataStore, con fábrica `inMemory()` para pruebas.
- `data/biometric/BiometricCapabilities.kt`: interfaz + `AndroidBiometricCapabilities` (`BiometricManager`) y el enum de disponibilidad.
- `data/biometric/AppLockPolicy.kt`: función pura que decide si bloquear.
- `ui/applock/AppLockViewModel.kt`, `AppLockScreen.kt` y `ui/biometric/BiometricPrompter.kt`: estado de bloqueo, pantalla de bloqueo y ayudante del aviso del sistema.
- `ui/profile/confirmidentity/`: `ConfirmIdentityScreen`, `ConfirmIdentityViewModel`, `ConfirmIdentityUiState`.
- *Solo si apruebas P12:* `data/security/SecretVault.kt` (interfaz + implementación con Keystore) y su uso en `ConfirmIdentityViewModel`.
- `data/profile/ProfileDataExporter.kt`: arma el JSON de exportación.
- `ui/components/`: `ProfileTopBar` (logo + "Nightwatch Protocol" + "X" + línea blanca), `SettingsRow` (tarjeta con emoji, título, subtítulo, flecha o switch), `SectionLabel`, `ConfirmationContent` (las 5 pantallas "¡... actualizado!"), `UserAvatar` (foto o inicial; sección 3b).

**Crear — pantallas** (`ui/profile/...`): una subcarpeta por pantalla con ViewModel, `UiState` y `Screen` donde hay lógica; solo `Screen` en las estáticas.

**Crear — pruebas:** `FakeAccountRepository`, `FakeProfileRepository`, y un test por cada ViewModel y repositorio nuevo.

**Modificar (mínimo)**
- `AuthError.kt` y `AuthErrorMapper.kt`: añadir `RecentLoginRequired` (código `ERROR_REQUIRES_RECENT_LOGIN`, que existe en el SDK 24.2.0). `AuthErrorMapperTest` se amplía.
- `NightWatchDestinations.kt` y `NightWatchNavHost.kt`: las 21 rutas.
- `AppContainer.kt`: `accountRepository`, `profileRepository`, `avatarRepository`, `userPreferences`, `biometricCapabilities`.
- `MainMenuScreen.kt`: **solo se agrega** el botón "Mi cuenta". Su "Cerrar sesión" actual no se toca ni se quita.
- `MainActivity.kt`: pasa a `FragmentActivity` y envuelve el `NavHost` con el contenedor de bloqueo (impacto en 3c; **no se toca hasta que confirmes P14**).
- `gradle/libs.versions.toml` y `app/build.gradle.kts`: dependencia `androidx.biometric`, y `versionName = "1.4.0"` (con `versionCode = 2`).

**No se toca:** Login, Registro, Recuperar contraseña, Quiz, Resultado, `NightWatchTextField`, `NightWatchButton`, tema, manifest.

## 8. Etapas (aprobación al final de cada una)

0. **Antes de empezar (tú):** publicar las reglas de Firestore de la sección 6.
1. **Cimientos, menú y Mi Perfil.** Versión 1.4.0, componentes compartidos (incluido `UserAvatar` con inicial o foto), `AccountRepository` (lectura), `ProfileRepository` (lectura), `AvatarRepository` (carga: caché local y luego Firestore), rutas `UserMenu` y `Profile`. En el Main Menu **solo se suma** "Mi cuenta"; su "Cerrar sesión" queda igual. `UserMenu` agrega el suyo con la misma lógica. Nivel BAJO/MEDIO/ALTO/"Sin evaluar". *Prueba tuya:* abrir menú desde "Mi cuenta", ver perfil con la inicial, cerrar sesión desde el Main Menu y desde el Menú de usuario.
2. **Editar Perfil + Perfil actualizado + avatar + "Confirma tu identidad".** Nombre real, contraseña real, aviso para cuentas Google, la pantalla `ConfirmIdentity` con solo contraseña (sección 3d) y el flujo completo del avatar: selector de fotos, procesado, guardado en Firestore y caché, "Quitar foto" y errores. *Prueba tuya:* cambiar nombre y contraseña (te pedirá confirmar identidad si tu sesión es vieja); elegir una foto y verla en las 4 pantallas; reabrir la app sin conexión y verla; quitarla.
3. **Privacidad y seguridad + Cambiar contraseña + Bloqueo biométrico.** Se hace en dos partes, cada una con su aprobación:
   - **3A:** `PrivacySecurity` con `UserPreferences` (Ocultar progreso) y `ChangePassword` (3 estados + "actualizada"). La fila "Bloqueo Biométrico" se dibuja pero todavía no actúa. *Prueba tuya:* contraseña actual mala, no coinciden, éxito, y volver a entrar con la nueva.
   - **3B:** bloqueo biométrico real: dependencia `androidx.biometric`, `MainActivity` a `FragmentActivity` (solo con tu P14 confirmada), `AppLockPolicy`, `AppLockViewModel`, pantalla de bloqueo y el interruptor funcional. *Prueba tuya:* activar con y sin huella registrada, cerrar y abrir la app, probar el tiempo de P13, y desactivar.
   - **3C (solo si apruebas P12):** "Usar huella" en `ConfirmIdentity`.
4. **Correo de recuperación + Descargar mis datos.** *Prueba tuya:* guardar el correo, verlo en Mi Perfil, exportar el JSON.
5. **Cambio de correo** (4 pantallas, solo enlace de Firebase, con la pantalla "Verifica tu correo" de la sección 4b). *Prueba tuya:* abrir el enlace desde el celular y confirmar la actualización (ver R2).
6. **Pantallas restantes:** Notificaciones (DataStore), Apariencia, Idioma, Logros, Dispositivos + Dispositivo desconectado, Ayuda, Acerca de.
7. **Cierre:** comparación contra las 23 pantallas de Figma, pruebas unitarias completas, revisión final.

Cada etapa termina con compilación, pruebas unitarias en verde y tu aprobación antes de la siguiente. No hago commits sin que me lo pidas.

## 9. Riesgos

- **R1 — Login reciente.** `updatePassword` y `verifyBeforeUpdateEmail` fallan con sesiones antiguas. Se resuelve con la pantalla "Confirma tu identidad" (3d); sin ella, Editar Perfil casi nunca funcionaría.
- **R2 — Cambio de correo con enlace.** Al confirmarse el cambio, Firebase puede invalidar la sesión actual: el siguiente `reload()` puede fallar con "token expirado" y la persona tendría que volver a iniciar sesión. Hay que probarlo en un dispositivo real y decidir el mensaje en esa etapa. El enlace abre una página de Firebase.
- **R3 — Cuentas Google.** Detectar "sin contraseña" por `providerData`. Si una cuenta tiene Google y contraseña, se trata como cuenta con contraseña.
- **R4 — Reglas de Firestore manuales.** Sin publicarlas, el correo de recuperación falla.
- **R5 — Figma fiel.** Son 23 pantallas con muchos detalles (emojis como íconos, tarjetas, switches). Cada etapa extrae el diseño de sus nodos antes de programar. La paleta clara no se construye.
- **R6 — Funciones que parecen reales pero no lo son.** "Cerrar sesión en todos" y la lista de dispositivos no cierran ninguna sesión; Apariencia e Idioma no cambian nada. Es lo que pediste, pero un usuario puede creer que sí funcionan. Conviene revisarlo antes de publicar la app. (El bloqueo biométrico ya no entra en esta lista: ahora es real.)
- **R7 — Preferencias por dispositivo.** Si otra persona inicia sesión en el mismo teléfono, hereda los switches. Mitigación posible: limpiarlos al cerrar sesión (no incluido salvo que lo pidas).
- **R8 — "Sin evaluar".** Si la lectura del nivel falla por red, también se mostrará "Sin evaluar" (no se distingue de "nunca hizo el quiz").
- **R9 — Verificación en dispositivo.** Los tests instrumentados no se ejecutan en el entorno de trabajo; las comprobaciones de pantalla y de Firebase dependen de que las pruebes tú en el celular al final de cada etapa.
- **R11 — Avatar en Firestore.** Guardar la foto como Base64 en el mismo documento que el correo de recuperación significa que cada lectura del perfil descarga hasta ~137 KB. Es aceptable para una foto por usuario en el plan Spark, pero no escalaría si se guardaran muchas imágenes por usuario.
- **R12 — Escrituras en cola.** Sin conexión, Firestore deja la escritura pendiente y la sincroniza después. Si la persona quita la foto estando sin conexión, el orden de las escrituras se respeta, pero el aviso de error puede aparecer aunque el cambio llegue más tarde.
- **R13 — Fotos grandes o raras.** Una foto muy grande se decodifica reducida (`inSampleSize`) para no agotar la memoria; formatos que el sistema no decodifica (algunos HEIC en versiones viejas) dan el error "No pudimos leer esa imagen".
- **R14 — Dos escritores sobre el mismo documento.** El correo de recuperación y el avatar escriben con merge en `profile/main`; no se pisan entre sí, y las reglas aceptan cualquiera de los dos campos solos.
- **R10 — Emojis.** Los íconos de Figma son emojis; su aspecto varía según el fabricante del teléfono.
- **R15 — Quedarte fuera de tu propia app.** Si el bloqueo está activo y el teléfono pierde las huellas, o el lector falla, no podrías pasar el bloqueo. Mitigación: el bloqueo se desactiva solo cuando ya no hay biometría disponible (P15c) y el respaldo de PIN en Android 11 o mayor. En Android 10 o menor no hay respaldo de PIN; ahí la salida es ese mismo desactivado automático.
- **R16 — Aviso del sistema en versiones viejas.** En Android 7 y 8 el aviso lo dibuja la librería con un diálogo propio y su aspecto con nuestro tema no está verificado. Hay que probarlo en un dispositivo con esa versión; si no tienes uno, queda sin probar.
- **R17 — `MainActivity` es el punto de arranque de toda la app.** Cambiar su superclase y envolver el `NavHost` afecta a todas las pantallas. Es un cambio pequeño y compatible, pero cualquier error aquí rompe todo el arranque, por eso la Etapa 3B tiene su propia aprobación y no se hace antes de P14.
- **R18 — Contenido visible en "aplicaciones recientes".** El bloqueo no oculta la miniatura de la app en el selector de apps recientes (eso requeriría `FLAG_SECURE`, que además bloquea capturas de pantalla). No está incluido; lo digo para que no se asuma protección que no existe.
- **R19 — Estado de bloqueo y rotación.** El estado vive en un `ViewModel` de la actividad: sobrevive a girar la pantalla, pero no a que Android mate el proceso. En ese caso la siguiente apertura cuenta como inicio desde cero y vuelve a pedir la huella (es lo deseado).
- **R20 — Si se aprueba P12,** se suman sus ocho riesgos (sección 3d).

## 10. Pruebas

- Unitarias (JVM) con fakes: `AccountRepository`, `ProfileRepository`, `UserPreferences` en memoria.
- ViewModels: Menú (cierre de sesión), Mi Perfil (nivel, "Sin evaluar", cuentas Google), Editar Perfil (nombre, contraseña, login reciente, ir a cambio de correo), Cambiar contraseña (actual incorrecta, no coinciden, reglas, éxito, Google), Correo de recuperación (válido/ inválido/ error de permisos), Cambio de correo y Verificar (enviar, reenviar, ya verificado, vencido), Notificaciones y Privacidad (persistencia), exportación (contenido del JSON).
- Avatar, con fakes (`FakeProfileRepository` con fallos configurables, `FakeAvatarCache`, `FakeAvatarImageProcessor`):
  - guardar avatar (caché local y Firestore escritos, el `StateFlow` se actualiza);
  - quitar avatar (campo y caché borrados, vuelve la inicial);
  - falla o tiempo límite de Firestore: se conserva la copia local y se informa el error;
  - imagen ilegible: error corto y se conserva el avatar anterior;
  - tamaño: si el JPEG supera 100 KB se baja la calidad por pasos;
  - `AvatarDisplay`: con foto muestra la foto; sin foto muestra la inicial (nombre, o correo, o "?").
  El procesado real con `Bitmap` no se prueba en JVM (necesita Android); se prueba la lógica de decisión, no la UI.
- Bloqueo biométrico, con `FakeBiometricCapabilities` (disponibilidad configurable) y `UserPreferences` en memoria:
  - `AppLockPolicy` (función pura): inicio desde cero con sesión y bloqueo activo → bloquea; bloqueo apagado → no; sin sesión → no; vuelta de segundo plano con menos de 30 s → no; con exactamente 30 s o más → sí.
  - Estado de biometría (`PrivacySecurityViewModel`): disponible → pide la verificación de prueba; éxito → queda activado y se guarda; cancelado o fallido → queda apagado con el texto rojo.
  - **Sin huella registrada:** `NoneEnrolled` → el interruptor queda apagado, no se pide ningún aviso y se muestra el texto rojo; lo mismo para `NoHardware` y `TemporarilyUnavailable`, cada una con su mensaje.
  - Apagar el interruptor se guarda.
  - `AppLockViewModel`: desbloqueo correcto → desbloqueado; cancelado → sigue bloqueado; la biometría desaparece con el bloqueo activo → se desactiva solo; cerrar sesión reinicia el estado.
- "Confirma tu identidad", con `FakeAccountRepository`:
  - **sin biometría:** contraseña vacía; contraseña incorrecta ("La contraseña actual no es correcta", tanto con `WrongPassword` como con `InvalidCredentials`); sin conexión; éxito → emite "confirmado" para que la pantalla anterior reintente.
  - **con biometría (solo si apruebas P12), con `FakeSecretVault`:** huella disponible y secreto guardado → ofrece "USAR HUELLA"; sin secreto o con el bloqueo apagado → no la ofrece; reautenticación por huella correcta; secreto inválido o contraseña cambiada → se borra y cae a la contraseña; clave invalidada por cambio de huellas → se borra el secreto.
- "Verifica tu correo": consulta que ve el correo nuevo → "Correo actualizado"; que no lo ve → estado de error con "REINTENTAR"; reenviar el enlace llama a `verifyBeforeUpdateEmail`.
- `AuthErrorMapperTest`: nuevo código `ERROR_REQUIRES_RECENT_LOGIN`.
- Instrumentadas: se escriben pruebas de Compose para las pantallas con lógica, pero solo se compilan aquí.
