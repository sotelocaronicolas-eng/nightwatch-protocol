# Inventario de la app para el Módulo 5 (solo lectura)

Fecha: 2026-11-02. Rama: `docs/estado-modulo-4`. Generado leyendo el código; no se ejecutó la app.
Método: `grep` y lectura de los archivos de `app/src/main/java/com/example/nightwatchprotocol`. Las rutas de abajo son relativas a esa carpeta.
Los textos salen de comillas literales encontradas con `grep`; **no leí cada archivo de pantalla completo**, así que puede haber textos de error o de accesibilidad que no estén aquí (se indica "no revisado" donde aplica).

## 1. Colores

### 1.1 `ui/theme/Color.kt` (contenido completo)

| Constante | Valor |
|---|---|
| `NightWatchBackground` | `Color(0xFF2C595D)` → #2C595D |
| `NightWatchSurfaceButton` | `Color(0xFFD9D9D9)` → #D9D9D9 |
| `NightWatchSurfaceButtonMuted` | `Color(0xFFA1A1A1)` → #A1A1A1 |
| `NightWatchBorderAccent` | `Color(0xFF597F82)` → #597F82 |
| `NightWatchLinkAccent` | `Color(0xFFA2DBFF)` → #A2DBFF |
| `NightWatchHeaderPanel` | `Color(0xFFCAD5D7)` → #CAD5D7 |
| `NightWatchOnBackground` | `Color.White` → #FFFFFF |
| `NightWatchOnButton` | `Color.Black` → #000000 |
| `NightWatchError` | `Color(0xFFFF2E16)` → #FF2E16 |
| `NightWatchSuccess` | `Color(0xFF4CAF50)` → #4CAF50 (requisitos de contraseña cumplidos) |

### 1.2 `ui/theme/Theme.kt`

- Un solo esquema: `darkColorScheme(...)`. No existe `lightColorScheme`, ni `isSystemInDarkTheme`, ni color dinámico.
- Roles definidos (7): `background` = NightWatchBackground, `surface` = NightWatchBackground, `primary` = NightWatchSurfaceButton, `onPrimary` = NightWatchOnButton, `onBackground` = NightWatchOnBackground, `onSurface` = NightWatchOnBackground, `error` = NightWatchError.
- El resto de roles (secondary, tertiary, surfaceVariant, outline, container, onError, etc.) toman los valores por defecto de `darkColorScheme`.
- `NightWatchProtocolTheme(content)` no recibe parámetros (sin parámetro de tema). Usa `Typography` de `ui/theme`.
- Fuera del tema (otro archivo): `NightWatchGold` está en `ui/components/SettingsRow.kt:32`.
- `res/values/themes.xml`: `Theme.NightWatchProtocol` hereda de `android:Theme.Material.Light.NoActionBar`; el splash usa `#2C595D` fijo.

### 1.3 `Color(0x…)` sueltos fuera de `ui/theme` (42)

| Archivo:línea | Valor | Nombre / uso |
|---|---|---|
| ui/components/ConfirmationContent.kt:40 | 0xFF447579 | ConfirmationPanelColor |
| ui/components/ConfirmationContent.kt:150 | 0xFF162523 | color de texto (en línea) |
| ui/components/ProfileTopBar.kt:28 | 0xFFDDDDDD | TopBarTextColor |
| ui/components/SettingsRow.kt:32 | 0xFFE3B54F | NightWatchGold |
| ui/components/SettingsRow.kt:35 | 0xFF162523 | RowTextColor |
| ui/components/SettingsSwitch.kt:20 | 0xFFE3B54F | SwitchOnColor |
| ui/components/SettingsSwitch.kt:21 | 0xFFA1A1A1 | SwitchOffColor |
| ui/exam/ExamIntroScreen.kt:41 | 0xFF3E6E71 | IconRing |
| ui/exam/ExamIntroScreen.kt:42 | 0xFF162523 | TextOnLight |
| ui/exam/ExamScreen.kt:52 | 0xFF3E6E71 | ElevatedColor |
| ui/examresult/ExamResultScreen.kt:51 | 0xFF3E6E71 | ElevatedColor |
| ui/examresult/ExamResultScreen.kt:54 | 0xFF162523 | TextOnLight |
| ui/home/HomeScreen.kt:71 | 0xFF204143 | SheetBorderColor |
| ui/home/HomeTopBar.kt:27 | 0xFFDDDDDD | TopBarTextColor |
| ui/home/NightWatchBottomBar.kt:38 | 0xFF162523 | LabelColor |
| ui/home/Notices.kt:9 | 0xFF94A6A7 | PillColor |
| ui/lab/LabScreen.kt:70 | 0xFF3E6E71 | ElevatedColor |
| ui/lab/LabScreen.kt:73 | 0xFF162523 | TextOnLight |
| ui/labreport/LabReportScreen.kt:54 | 0xFF3E6E71 | ElevatedColor |
| ui/labreport/LabReportScreen.kt:57 | 0xFF162523 | TextOnLight |
| ui/labs/LabsScreen.kt:63 | 0xFF3E6E71 | ElevatedColor |
| ui/labs/LabsScreen.kt:66 | 0xFF162523 | TextOnLight |
| ui/lesson/LessonScreen.kt:63 | 0xFF3E6E71 | IconRing |
| ui/lesson/LessonScreen.kt:64 | 0xFF162523 | TextOnLight |
| ui/path/LearningPathScreen.kt:68 | 0xFF3E6E71 | CompletedRing |
| ui/profile/achievements/AchievementsScreen.kt:47 | 0xFF3E6E71 | LockedCircle |
| ui/profile/achievements/AchievementsScreen.kt:48 | 0xFF162523 | BadgeTextColor |
| ui/profile/changepassword/ChangePasswordScreen.kt:200 | 0xFF162523 | color de texto (en línea) |
| ui/profile/devices/DevicesScreen.kt:50 | 0xFF404040 | DeviceLabelColor |
| ui/profile/help/HelpAndAboutScreens.kt:69 | 0xFF162523 | texto "Contactar a soporte" |
| ui/profile/myprofile/MyProfileScreen.kt:49 | 0xFF404040 | LabelColor |
| ui/profile/myprofile/MyProfileScreen.kt:50 | 0xFF808080 | DividerColor |
| ui/profile/myprofile/MyProfileScreen.kt:160 | 0xFF5E5E5E | color en línea |
| ui/profile/usermenu/UserMenuScreen.kt:176 | 0x99D9D9D9 | fondo del botón Cerrar Sesión (con alfa) |
| ui/social/ProfileSections.kt:95 | 0x73000000 | BackgroundScrim |
| ui/social/ProfileSections.kt:511 | 0xCC333333 | fondo en línea |
| ui/social/ProfileSections.kt:548 | 0xFF8C8C8C | fondo del selector de fondos |
| ui/social/SocialAvatar.kt:172 | 0xFFB07A4F | relleno del avatar generado |
| ui/social/SocialComponents.kt:69 | 0xFF3E6E71 | SocialElevated |
| ui/social/SocialComponents.kt:72 | 0xFF94A6A7 | SocialCardTint |
| ui/social/SocialComponents.kt:75 | 0xFF162523 | SocialOnLight |
| ui/social/SocialComponents.kt:78 | 0xFFC0503E | SocialFail |

Además hay 27 usos de `Color.Black` y 11 de `Color.White` en el código. Solo 2 archivos usan `MaterialTheme.colorScheme` (`ui/components/NightWatchBackground.kt`, `ui/verifycode/VerifyCodeScreen.kt`).

## 2. Pantallas de Perfil y Ajustes

Tipografía y color de todas: tokens `NightWatch*` y fuentes `NightWatch…FontFamily`. Todos los textos están **en el código**, no en `strings.xml`, salvo Logros (usa `strings_aprendizaje.xml`).

| Pantalla (ruta) | Archivo | Textos que muestra | Opciones | Estado |
|---|---|---|---|---|
| **Menú de usuario** (`UserMenu`) | ui/profile/usermenu/UserMenuScreen.kt | Secciones CUENTA, PROGRESO, PREFERENCIAS, PRIVACIDAD Y SEGURIDAD, SOPORTE. Filas: Ver Perfil, Editar Perfil, Logros e Insignias, Notificaciones, Apariencia, Idioma, Privacidad y Seguridad, Ayuda y Soporte, Acerca de. Botón "🚪  Cerrar Sesión". Pie: "Nightwatch Protocol · v<versión>" | 9 filas de navegación + cerrar sesión | **Funcional** (navega; cierra sesión) |
| **Ver perfil** (`Profile`) | ui/profile/myprofile/MyProfileScreen.kt | "Mi Perfil", "Mi Avatar", "EDITAR PERFIL"; filas: Correo electrónico, Nivel actual, Racha activa, Miembro desde, Género, Fecha de nacimiento, Correo de recuperación | Botón editar | **Funcional** (datos reales: Supabase, resultado del quiz, racha calculada) |
| **Editar perfil** (`EditProfile`) | ui/profile/editprofile/EditProfileScreen.kt | "Editar Perfil", "Cambiar Avatar", "Quitar foto", campos: Nombre completo, Correo electrónico, Nueva contraseña, Verificar contraseña, Género (menú, "Sin definir"), Fecha de nacimiento ("Quitar fecha"), "GUARDAR CAMBIOS"; aviso Google: "Tu cuenta usa Google: la contraseña y el correo se gestionan desde tu cuenta de Google." | Formulario | **Funcional** |
| **Logros e Insignias** (`Achievements`) | ui/profile/achievements/AchievementsScreen.kt | Título "Logros e Insignias"; contador "N de 9"; 9 insignias (ver 2.1); mensajes de carga, error, sin conexión y "Reintentar" (recursos `learning_badges_*`) | Solo lectura + Reintentar | **Funcional** (derivado del progreso real) |
| **Notificaciones** (`NotificationSettings`) | ui/profile/notifications/NotificationSettingsScreen.kt | Título "Notificaciones"; 5 interruptores (ver sección 5); nota: "Los avisos de logros, laboratorios y novedades se activarán cuando esas funciones existan en la app."; mensaje de permiso | 5 toggles | **Mixto**: los 5 guardan en DataStore; solo "Recordatorio de lección diaria" y "Racha en riesgo" generan avisos reales. Los otros 3 son inertes. Pide permiso `POST_NOTIFICATIONS` |
| **Apariencia** (`Appearance`) | ui/profile/appearance/AppearanceAndLanguageScreens.kt:39 | "Apariencia"; "Elige cómo se ve NightWatch Protocol en tu dispositivo."; tarjetas: 🌙 Oscuro ("Fondo oscuro, ideal de noche", marcada con ✓), ☀️ Claro ("Fondo claro de alto contraste"), ⚙️ Automático ("Sigue el ajuste del sistema") | 3 tarjetas | **Inerte**: ninguna tiene `onClick` (solo la acción vacía por defecto); "Oscuro" tiene el ✓ fijo |
| **Idioma** (`Language`) | mismo archivo:69 | "Idioma"; 🇪🇸 Español (✓ fijo), 🇺🇸 English | 2 tarjetas | **Inerte** (igual) |
| **Privacidad y Seguridad** (`PrivacySecurity`) | ui/profile/privacy/PrivacySecurityScreen.kt | "Privacidad y Seguridad"; SEGURIDAD DE LA CUENTA; filas: Cambiar Contraseña; Correo de Recuperación (subtítulo = correo o "Agregar correo de recuperación"); Administrar Dispositivos ("Administra tus sesiones" / "1 dispositivo activo" / "N dispositivos activos"); Bloqueo Biométrico ("Pide huella o rostro al abrir la app"; diálogo "Activar bloqueo"/"Verifica tu huella para activarlo"); Ocultar mi progreso público ("Otros no verán tu nivel ni tus islas"); Descargar mis datos ("Exporta una copia de tu información") | 3 navegaciones, 2 toggles, 1 exportación | **Funcional** (biométrico real; "ocultar progreso" se guarda en el servidor y local; exporta `nightwatch-mis-datos.json`) |
| **Ayuda y Soporte** (`Help`) | ui/profile/help/HelpAndAboutScreens.kt:44 | "Ayuda y Soporte"; filas: "¿Cómo funciona la Ruta de Aprendizaje?", "¿Por qué perdí mi racha?", "¿Los laboratorios afectan mi progreso?", "¿Cómo reporto un error en la app?"; botón "Contactar a soporte" | 4 filas + botón | **Inerte** (las filas no tienen acción; el botón tiene `clickable {}` vacío) |
| **Acerca de** (`About`) | mismo archivo:87 | "Acerca de"; "Nightwatch Protocol"; "Versión <versión>"; filas: Términos y Condiciones, Política de Privacidad, Calificar la app, Reportar un problema; "Hecho con 🛡️ para futuros defensores de la ciberseguridad." | 4 filas | **Inerte** salvo la versión (real) |
| **Cambiar contraseña** (`ChangePassword`, `PasswordUpdated`) | ui/profile/changepassword/ChangePasswordScreen.kt | "Cambiar Contraseña"; campos Contraseña actual, Nueva contraseña, Confirmar nueva contraseña; lista de requisitos (cumplido/pendiente); botón "Guardar cambios"; errores en `ui/profile/ProfileValidation.kt:10-20` ("La contraseña actual no es correcta", "La contraseña no cumple con los requerimientos", "Las contraseñas no coinciden", "Ingresa tu contraseña actual", "Se produjo un error inesperado. Intenta de nuevo.", "Sin conexión a internet") | Formulario | **Funcional** |
| **Correo de recuperación** (`RecoveryEmail`, `RecoveryEmailSaved`) | ui/profile/recoveryemail/RecoveryEmailScreen.kt | "Correo de Recuperación"; "Agrega un correo alterno para recuperar tu cuenta si pierdes acceso a la principal."; campo "Correo de recuperación"; "GUARDAR CORREO" | Formulario + código | **Funcional** (usa la pantalla de código de 6 dígitos y la Edge Function) |
| **Dispositivos** (`Devices`, `DeviceSignedOut`) | ui/profile/devices/DevicesScreen.kt | "Administrar Dispositivos"; "Sesiones activas en tu cuenta"; "Cargando dispositivos…"; tarjetas "📱  <nombre>"; botón "CERRAR SESIÓN EN TODOS" | Lista + cerrar | **Funcional con límite**: "cerrar sesión en todos" invalida los refresh tokens de otras sesiones; cerrar un dispositivo concreto es solo una marca cooperativa (`revoked`) |
| **Confirmar identidad** (`ConfirmIdentity`) | ui/profile/confirmidentity/ConfirmIdentityScreen.kt | "Confirma tu identidad"; "Por seguridad, ingresa tu contraseña actual para continuar."; campo "Contraseña actual"; "CONTINUAR" | Formulario | **Funcional** |
| **Cambiar correo** (`ChangeEmail`) | ui/profile/changeemail/ChangeEmailScreens.kt | "Confirmar Correo"; "Escribe tu nuevo correo. Te enviaremos un código de verificación para confirmarlo."; "Nuevo correo electrónico"; "ENVIAR CÓDIGO" | Formulario | **Funcional** |
| **Verificar código** (`VerifyCode`) | ui/verifycode/VerifyCodeScreen.kt (+ `CodeFlow.kt`) | "Verifica tu Correo"; "Enviamos un código de 6 dígitos a <correo>"; "Ingrese el código de 6 dígitos"; botones "CONFIRMAR CÓDIGO" / "REINTENTAR"; variante diálogo: "Hemos enviado un código a <correo>. Ingresa el código recibido para verificar tu identidad y continuar con el proceso de restablecimiento de tu contraseña.", "Volver al menú", "Confirmar"/"Reintentar"; `MESSAGE_GOOGLE_UNLINK_FAILED` = "Tu cuenta de Google anterior sigue vinculada a este perfil." | Código de 6 dígitos | **Funcional**. `CodePurpose`: SIGN_UP, EMAIL_CHANGE, RECOVERY_EMAIL (el enum puede tener más valores; vi solo estos tres en `CodeFlow.kt:15-23`) |
| **Confirmaciones** | ui/profile/confirmation/ConfirmationScreens.kt | "¡Perfil actualizado!" / "Tus datos se guardaron correctamente." / VOLVER A MI PERFIL · "¡Correo actualizado!" / "Tu correo electrónico fue verificado y actualizado con éxito." / VOLVER A EDITAR PERFIL · "¡Correo guardado!" / "Tu correo de recuperación fue guardado con éxito." / VOLVER A CUENTAS · "¡Contraseña actualizada!" / "Usa tu nueva contraseña la próxima vez que inicies sesión." / Listo · "¡Sesión cerrada!" / "Ese dispositivo cerrará su sesión la próxima vez que abra NightWatch. No revoca de inmediato el acceso de tu cuenta." / VOLVER A DISPOSITIVOS | Un botón | **Funcional** |

### 2.1 Las 9 insignias (`data/learning/Badges.kt`, `BadgeCatalog`)

🥇 Primeros pasos · 🔥 Racha de 7 días · 🔑 Maestro de Contraseñas · 🎣 Cazador de Phishing · 🌐 Guardián de Redes · 🌟 Leyenda NightWatch · 📞 Detective de Ingeniería Social · 🔥 Racha de 30 días · 🎓 Graduado NightWatch. Los nombres están en el código, no en `strings`.

## 3. Versión que muestra "Acerca de"

- Texto: `"Versión $versionName"` (`ui/profile/help/HelpAndAboutScreens.kt:102`). El Menú de usuario muestra `"Nightwatch Protocol · v$versionName"` (`ui/profile/usermenu/UserMenuScreen.kt:153`).
- Origen: **no sale de `BuildConfig`**. Se lee en tiempo de ejecución con `context.packageManager.getPackageInfo(context.packageName, 0).versionName` (HelpAndAboutScreens.kt:83-84; UserMenuScreen.kt:68-69), con cadena vacía si falla.
- Valor actual: `versionName = "2.2.0"`, `versionCode = 5` en `app/build.gradle.kts:33-34`.
- `BuildConfig` solo se usa para `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` y `GOOGLE_WEB_CLIENT_ID` (en `di/AppContainer.kt`).

## 4. Niveles de usuario que muestra el perfil

- Valores reales de "Nivel actual" en "Mi Perfil": `BAJO`, `MEDIO`, `ALTO` (enum `PlacementLevel`, `data/quiz/PlacementLevel.kt`), más `"Sin evaluar"` (`LEVEL_NOT_ASSESSED`, `data/profile/ProfileFormatters.kt:11`) si no hay resultado o falló la lectura, y `"…"` mientras carga (`MyProfileScreen.kt:184`).
- Umbrales del quiz: BAJO hasta 40 % (`PlacementScorer.kt:6`); los de MEDIO y ALTO no los revisé.
- Aparte existe el nivel de luchador de Social (1 a 20, `fighter_level`), que **no** aparece en "Mi Perfil" sino en las pantallas sociales.

## 5. Notificaciones configurables

Interruptores (`data/preferences/UserPreferences.kt`, `UserSetting`; valor por defecto entre paréntesis) y textos de pantalla:

| Interruptor | Subtítulo | Por defecto | ¿Genera aviso real? |
|---|---|---|---|
| 📘 Recordatorio de lección diaria (`DAILY_REMINDER`) | "Un aviso a las 7:00 p.m. si no has practicado" | activado | Sí (`ReminderKind.DAILY_PRACTICE`) |
| 🏆 Logros e insignias (`ACHIEVEMENT_NOTIFICATIONS`) | "Cuando desbloquees un nuevo logro" | activado | No existe `ReminderKind` |
| 🔥 Racha en riesgo (`STREAK_AT_RISK`) | "Si tu racha está por romperse" | activado | Sí (`ReminderKind.STREAK_AT_RISK`) |
| 🧪 Nuevos laboratorios (`NEW_LABS`) | "Cuando se publique un lab nuevo" | desactivado | No existe `ReminderKind` |
| 📣 Novedades de NightWatch (`NEWS_AND_UPDATES`) | "Anuncios y noticias del producto" | desactivado | No existe `ReminderKind` |

`ReminderKind` (`data/notifications/ReminderKind.kt`), con solo 2 valores:

| Valor | Canal (id / nombre) | Hora | Id notificación | Título | Texto |
|---|---|---|---|---|---|
| `DAILY_PRACTICE` | `daily_reminder` / "Recordatorio de lección diaria" | 19:00 | 1001 | "Es hora de practicar" | "Una lección corta mantiene tu ritmo. Entra a NightWatch." |
| `STREAK_AT_RISK` | `streak_at_risk` / "Racha en riesgo" | 21:00 | 1002 | "Tu racha está en riesgo" | "Practica hoy para no perder tu racha." |

Otros `UserSetting` que no son notificaciones: `HIDE_PUBLIC_PROGRESS`, `BIOMETRIC_LOCK`. Mensaje de la nota al pie en `NOTIFICATIONS_FOOTNOTE` (`NotificationSettingsScreen.kt:26`).

## 6. Pantallas y rutas de navegación

Definidas en `ui/navigation/NightWatchDestinations.kt`; registradas en `NightWatchNavHost.kt` (inicio: `Login` por defecto). Total: 48 rutas (`object` / `data class`) y 48 `composable<>`, una por ruta.

**Autenticación y quiz:** `Login`, `Register`, `NewPassword`, `PasswordReset`, `VerifyCode(email, purpose)`, `PostAuthGate`, `PlacementQuiz`, `PlacementResultRoute(level, weakCategories, saveFailed)`.
**Contenedor y aprendizaje:** `Home(tab)` (pestañas Inicio, Ruta y Labs), `Lesson(topic)`, `Lab(topic)`, `LabReport(topic, completedObjectives)`, `ExamIntro`, `Exam`, `ExamResult(answers)`.
**Social:** `Friends`, `AddFriend`, `FriendRequestsRoute`, `BlockedPeopleRoute`, `PairStreaksRoute`, `PairDetailRoute(friendCode)`, `BattlesRoute`, `QuickSearchRoute`, `BattleRoute(battleId)`, `BattleResultRoute(battleId)`, `FriendProfileRoute(friendCode)`, `MyProfileRoute`, `EditMyProfileRoute`.
**Perfil y ajustes:** `UserMenu`, `Profile`, `ProfileUpdated`, `EditProfile`, `ConfirmIdentity`, `ChangeEmail(newEmail)`, `EmailUpdated(notice)`, `PrivacySecurity`, `ChangePassword`, `PasswordUpdated`, `RecoveryEmail`, `RecoveryEmailSaved`, `NotificationSettings`, `Appearance`, `Language`, `Achievements`, `Devices`, `DeviceSignedOut`, `Help`, `About`.

Reportar y Bloquear son diálogos, no rutas.

## 7. Colores oscuros del Figma frente al código

Valores del Figma leídos con `get_variable_defs` (nodos `2287:163`, `2254:40`). No existe un conjunto de variables de modo claro: el nodo `2458:383` ("APARIENCIA (Claro)") solo devolvió radius/card, surface/button y border/accent.

| Token Figma | Hex | ¿Existe en el código? | ¿Coincide? |
|---|---|---|---|
| bg/base | #2c595d | Sí: `NightWatchBackground` (ui/theme/Color.kt) | Sí, idéntico |
| bg/elevated | #3e6e71 | **No existe en el tema.** Hay 10 copias sueltas `Color(0xFF3E6E71)` con distintos nombres (ver 1.3) | El valor coincide; no hay constante única |
| surface/button | #d9d9d9 | Sí: `NightWatchSurfaceButton` | Sí |
| surface/button-muted | #a1a1a1 | Sí: `NightWatchSurfaceButtonMuted` (y `SwitchOffColor` suelto) | Sí |
| border/accent | #597f82 | Sí: `NightWatchBorderAccent` | Sí |
| accent/gold | #e3b54f | Existe, pero **fuera del tema**: `NightWatchGold` en ui/components/SettingsRow.kt:32 (y `SwitchOnColor`) | Sí en valor |
| text/on-dark | #ffffff | Sí: `NightWatchOnBackground = Color.White` | Sí |
| text/on-light | #162523 | **No existe en el tema.** 13 copias sueltas `Color(0xFF162523)` (TextOnLight, RowTextColor, LabelColor, BadgeTextColor, SocialOnLight, etc.) | El valor coincide; no hay constante única |

## 8. Diferencias y observaciones

1. **Versión:** "Acerca de" no usa `BuildConfig`, usa `PackageManager`. El informe del Módulo 2 (`docs/superpowers/reports/modulo-2-diagnostico.md:252`) habla de "versión 1.4.0"; hoy se muestra 2.2.0.
2. **Tema:** `Theme.kt` tiene solo `darkColorScheme` con 7 roles, aunque `themes.xml` hereda de `Material.Light`. No existe esquema claro ni parámetro de tema.
3. **Tokens sin hogar:** #3E6E71 (bg/elevated), #162523 (text/on-light) y #E3B54F (gold) están en el Figma como tokens, pero en el código viven como copias privadas por archivo; `NightWatchGold` está en `ui/components`, no en `ui/theme`.
4. **Colores en el código que no están en la lista del Figma que me diste:** #447579, #DDDDDD, #204143, #94A6A7, #404040, #808080, #5E5E5E, #8C8C8C, #333333 (con alfa), #C0503E, #B07A4F, y del tema #A2DBFF, #CAD5D7, #FF2E16, #4CAF50.
5. **Apariencia e Idioma son inertes** y muestran selección fija ("Oscuro" y "Español" con ✓). Ayuda y Acerca de también son casi todos inertes (filas sin acción; "Contactar a soporte" con clic vacío). En Notificaciones, 3 de los 5 interruptores no producen avisos (la propia pantalla lo avisa en su nota).
6. **Figma tiene variantes "(Claro)"** de las pantallas de Perfil y Ajustes (23 frames, ids `2458:199` a `2458:693`, `2472:243`, `2472:259`); el código no tiene ninguna.
7. **Textos:** los de estas pantallas están fijos en Kotlin (F-09), salvo Logros (`strings_aprendizaje.xml`). Los nombres de insignias y los textos de los recordatorios también están en Kotlin.
8. **Niveles:** el perfil solo muestra BAJO/MEDIO/ALTO/"Sin evaluar"; el nivel social 1 a 20 no se ve ahí.
9. **No revisado a fondo:** los textos de error y de accesibilidad dentro de cada pantalla, el detalle de Devices (más allá de los textos listados) y los umbrales de MEDIO y ALTO.
10. **Git:** este archivo queda sin seguimiento (no está en `.gitignore`); no lo añadí al índice.
