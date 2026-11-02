# Módulo 5 · Personalización (tema claro/oscuro e idioma inglés) — Especificación y plan

Fecha: 2026-11-02 · Estado: **BORRADOR para aprobación del grupo** · Rama de documentación: `docs/modulo-5-spec`
Base: v2.2.0 (módulos 1 a 4 en `main`). Versiones objetivo: **v2.3.0** (5A, tema) y **v2.4.0** (5B, idioma).

Fuentes: `modulo-5-figma-referencia.md` (Figma `wd3fQTzF5NqsfGfdR4I1lb`), `inventario-app-modulo-5.md` (estado real del código), `CLAUDE.md`,
`2026-10-09-profile-settings-design.md` (módulo 2), `docs/superpowers/reports/revision-codigo-modulo-4.md` (R-01) y el código citado.
Convención: **PROPUESTA** = valor que no está en el Figma ni en el código; va a la lista de aprobación (sección 10). Las cifras de código salen de
`grep` y son aproximadas donde se dice (la heurística de literales incluye bloques `@Preview`).

---

## 1. Objetivo, alcance y decisiones

**Objetivo.** Que la persona elija el aspecto (oscuro, claro o automático) y el idioma (español o inglés) de **toda** la app, y que la elección
se conserve. El español sigue siendo el idioma base y el oscuro el aspecto por defecto.

**Dentro (IN).** Tema oscuro, claro y automático (sigue al sistema) en toda la app. Inglés en toda la app: interfaz, notificaciones, fechas y
formatos, mensajes de error y, como última etapa, el contenido JSON (lecciones, labs, examen, quiz).

**Fuera (OUT).** Tamaño de fuente, color de acento, Material You/color dinámico, preguntas de batalla y retos de ulti (viven en Postgres, `0009`;
futuro módulo), preferencia por cuenta (se guarda por dispositivo), hacer funcionales Ayuda y Acerca de (solo se traducen y se les da tema).

### Reglas de prioridad (ya decididas)
1. **Modo oscuro:** lo que existe hoy no cambia. Si se encuentra una diferencia visual real en oscuro, se **reporta** (sección 2.2) y no se corrige.
2. **Datos, textos y funciones:** gana la app (versión real por `PackageManager`, niveles BAJO/MEDIO/ALTO, filas extra del perfil, textos actuales). Los datos de muestra del Figma se ignoran.
3. **Estilo del modo claro:** gana el Figma en las 23 pantallas que tienen versión clara.
4. **Pantallas sin versión clara:** paleta derivada con las reglas de la sección 4, marcada PROPUESTA.

### Decisiones (el grupo puede vetar cualquiera; ver sección 10)
| Id | Decisión | Justificación breve |
|---|---|---|
| D1 | Alcance IN/OUT de arriba | Entregable en dos versiones; lo que depende del servidor queda fuera |
| D2 | Por defecto: tema Oscuro, idioma Español; el idioma no tiene opción "seguir sistema" | El Figma solo dibuja Español/English; hoy la app es oscura y en español |
| D3 | Material 3 con `darkColorScheme` y `lightColorScheme` completos + tokens semánticos propios por `CompositionLocal`; las constantes `NightWatch*` pasan a accesores sensibles al tema; los 10 duplicados de #3E6E71 y 13 de #162523 pasan a un token único; `NightWatchGold` sale de `ui/components` a `ui/theme` | Hoy solo 2 archivos usan `MaterialTheme.colorScheme` y 55 usan tokens directos; sin esto el claro sería 40 parches |
| D4 | Paleta clara del Figma (sección 4) | Es lo único dibujado; los 23 frames coinciden en ella |
| D5 | Cada color suelto del código recibe token y valor claro propuesto (sección 4.3) | No dejar ninguno sin dueño |
| D6 | Contraste: texto ≥ 4.5:1 (≥ 3:1 si es grande: 18 sp o 14 sp en negrita); componentes de UI ≥ 3:1; test unitario que calcula los pares | Accesibilidad medible y no discutible |
| D7 | Dos guardas en pruebas: (a) falla si aparece `Color(0x…)`, `Color.Black` o `Color.White` fuera de `ui/theme` (lista blanca justificada); (b) paridad de strings `values` vs `values-en` (mismas claves y mismos placeholders) | Evita que el tema o la traducción se desarmen en silencio |
| D8 | La preferencia de tema se lee **antes del primer frame**; splash, `themes.xml`, iconos de barra de estado y de navegación coherentes | Sin parpadeo oscuro→claro |
| D9 | Idioma con AppCompat: `AppCompatDelegate.setApplicationLocales`, `locales_config.xml` (`localeConfig` en el manifiesto) y `values-en`. **R-01 se verifica antes del idioma** (ver 2.2: ya está corregido) | API estándar y compatible con los ajustes del sistema de Android 13+ |
| D10 | Terminar F-09 (textos fijos a recursos) **sin cambio visual**; la capa `data` emite tipos (enums, ids, sealed) y la UI los traduce, con `UiText` para los ViewModels | `data`/dominio no dependen de recursos Android; sigue el patrón de `AuthError` |
| D11 | Notificaciones en el idioma elegido; los canales se vuelven a registrar al cambiar de idioma; solo existen 2 `ReminderKind` reales | Hoy título, texto y nombre del canal están fijos en Kotlin |
| D12 | Fechas y formatos se localizan para mostrar; la zona `America/Bogota` de las reglas de negocio (racha, XP, parejas) **no cambia** con el idioma ni el sistema | Dos cosas distintas: cómo se ve y qué día cuenta |
| D13 | El contenido JSON va en un archivo por idioma, con pruebas de paridad (mismos ids, mismas respuestas correctas del examen, mismos comandos de laboratorio); es la última etapa y puede cerrarse en una versión posterior | Es el volumen mayor y el que más revisión humana necesita |
| D14 | Etapas 0 a 10 de la sección 7, cada una con `./gradlew assembleDebug testDebugUnitTest lintDebug` y mergeable por sí sola | Un PR revisable por etapa |

### Decisiones adicionales tomadas al escribir esta spec (también vetables)
| Id | Decisión | Motivo |
|---|---|---|
| A1 | En **oscuro** el texto de error de los campos sigue como hoy (rojo `#FF2E16` de `errorSupportingTextColor`); la regla "texto de error con el color de texto + icono ⚠" de D4 aplica **solo al claro** | D4 en oscuro cambiaría lo que existe hoy; choca con la regla 1 |
| A2 | La selección clara usa `#926A1B` (ajuste mínimo del `#B2842C` del Figma, que da 2.39:1 y 2.70:1) para el borde de 3 px **y** el ✓ | D6 exige ≥ 3:1 en componentes; el Figma dibuja el ✓ en `#1C2B29`, ver 2.1-f |
| A3 | El idioma se guarda en un solo lugar: el de `AppCompatDelegate` (en Android 13+ lo comparte con los ajustes del sistema); el DataStore guarda solo el tema y un indicador "primer arranque ya resuelto" | Evita dos fuentes de verdad |
| A4 | Al primer arranque, si no hay idioma guardado, la app fija **español** de forma explícita, aunque el teléfono esté en inglés | Con `values-en` presente, un teléfono en inglés mostraría inglés por sí solo y rompería D2 |
| A5 | Las preguntas de batalla y los retos de ulti seguirán en español en la app en inglés (D1) | Se acepta la mezcla; ver riesgo 8.9 y pregunta 11.2 |
| A6 | El splash conserva `#2C595D` en ambos aspectos | El splash de Android 12+ lo dibuja el sistema antes de leer la preferencia |
| A7 | El interruptor (toggle) claro es PROPUESTA: el Figma solo trae un SVG (`2286:106`) sin colores definidos | Ver 4.2 |

---

## 2. Diferencias Figma ↔ app

### 2.1 Las ocho previstas y su resolución
| | Diferencia | Evidencia | Resolución |
|---|---|---|---|
| a | Versión: Figma "v1.4.0" vs app 2.2.0 | Figma 2068:30; `build.gradle.kts:34`; `UserMenuScreen.kt:68` | **Gana la app** (lee `PackageManager`). Dato de muestra ignorado |
| b | Filas extra del Perfil: Género, Fecha de nacimiento, Cambiar/Quitar avatar, Quitar fecha; el Figma no las dibuja | `MyProfileScreen.kt:190-192`, `EditProfileScreen.kt:144-238` | **Gana la app**; en claro usan el estilo de las filas/campos de su pantalla (info `#F7F4EC`) |
| c | Los 23 frames claros cubren Perfil y Ajustes; **no** hay frame claro para **Confirmar identidad** (`ConfirmIdentity`, pantalla de perfil de la app), el diálogo de recuperación de contraseña ni el resto de la app (login, quiz, inicio, ruta, labs, lecciones, examen, Social) | Referencia §2 y §3 | Paleta **derivada** (sección 4.3), PROPUESTA |
| d | Confirmaciones claras: el frame tiene fondo `#AEF5D5` pero un rectángulo blanco 412×844 desde top 72/73 | Capturas y código de 2458:624, 642, 675 (leídos en esta tarea) | **Verificado:** el área es **blanca**, la cabecera queda verde y las ruedas dentadas decorativas van al 25 %. Token `confirmationPanel` claro = `#FFFFFF` (equivale al `#447579` oscuro, `ConfirmationContent.kt:40`) |
| e | Tres rojos: `#FF2E16` (oscuro y borde del código incorrecto en claro), `#B85947` (borde de contraseña en claro) | Referencia §5 | Un solo token `error`: oscuro `#FF2E16` (como hoy), claro `#B85947` en bordes (4.19:1 sobre `#F7F4EC`). Texto de error: color de texto + ⚠ (solo claro, A1) |
| f | Dorado: borde de selección `#B2842C` (claro) vs `#E3B54F` (oscuro); el ✓ es `#1C2B29` en claro (Figma) y dorado en oscuro | Referencia §5; contrastes de 4.2 | Token `selection`: oscuro `#E3B54F`, claro **`#926A1B`** (ajuste mínimo, A2) para borde y ✓. **El Figma pinta el ✓ claro en `#1C2B29`: se pide al grupo elegir** (pregunta 11.1) |
| g | Botón blanco radio 20 (47×299) vs pill `#D9D9D9` (48×372) | **Nuevo:** blanco en 2458:515, 642, 675 y en 2472:259 ("Guardar cambios"); `#D9D9D9` en 2458:624 ("Listo"), 2458:559 y "Contactar a soporte" | D4: CTA primario claro = blanco con texto `#1C2B29`; oscuro se queda `#D9D9D9` (`NightWatchButton.kt:37`). Filas y tarjetas de opción siguen `#D9D9D9` en ambos |
| h | Figma: "Reenviar el código de 6 digitos" (sin tilde); app: "Ingrese el código de 6 dígitos" | `VerifyCodeScreen.kt:235` | **Gana la app** (con tilde) |

### 2.2 Diferencias nuevas halladas
1. **R-01 ya está corregido en el código.** `LeaveScreenEffect` (`SocialComponents.kt:123-132`) no llama a `onLeft()` si `activity.isChangingConfigurations`, y lo usan `BattlesScreen.kt:51` y `QuickSearchScreen.kt:52` (commit `6863b9c`, informe `revision-codigo-modulo-4.md` §6). El informe aclara que se probó **por razonamiento**, sin prueba de Activity. D9 pedía "corregir R-01 antes del idioma": la etapa 7 pasa a **verificarlo** (prueba automática y manual con rotación, tema e idioma) y a auditar otros `DisposableEffect`/`onDispose` con efectos secundarios (`ArenaScreen` y los demás).
2. **`MainActivity` es `FragmentActivity`** (por `androidx.biometric`) y no `AppCompatActivity`; `themes.xml` hereda de `android:Theme.Material.Light.NoActionBar` y el splash apunta a él (`postSplashScreenTheme`). `AppCompatDelegate.setApplicationLocales` y `AppCompatActivity` exigen un tema AppCompat (si no, lanza `IllegalStateException`). `appcompat` **no** está en `libs.versions.toml`. Cambiar a `AppCompatActivity` es compatible (extiende `FragmentActivity`).
3. **`enableEdgeToEdge()` sin argumentos** (`MainActivity.kt:58`) usa el estilo automático, que sigue al **sistema**, no a la app: con la app en claro y el sistema en oscuro los iconos de la barra de estado saldrían claros sobre fondo claro. Hay que pasar `SystemBarStyle` según el tema elegido.
4. **Diferencias visuales en oscuro (solo se reportan, no se corrigen).** Valores calculados (WCAG):
   - Campos de texto (`NightWatchTextField.kt:77-85`): texto **blanco** sobre `#A1A1A1` = **2.58:1**.
   - Texto de error `#FF2E16` sobre `#2C595D` = **2.10:1** (y sobre `#A1A1A1` 1.44:1).
   - `#4CAF50` (éxito) sobre `#2C595D` = **2.80:1**.
   - Divisor `#808080` sobre `#D9D9D9` = 2.80:1 (decorativo).
   - Blanco sobre `#94A6A7` = 2.54:1 (ya listado en la referencia).
   - ✓ dorado sobre tarjeta `#D9D9D9` = 1.35:1 (la selección depende del borde).
   - Dorado sobre `#2C595D` = 4.08:1 (11 sp: < 4.5).
5. **El Figma es incoherente entre frames claros** (se resuelve con D4): fondo de campos de contraseña `#A1A1A1` (2458:559) vs `#A9B8AE` (2472:259, 2472:243) vs `#F7F4EC` (resto); botón "Guardar cambios" gris en 2458:559 y **blanco** en 2472:259; "Listo" gris en 2458:624 y los demás blancos. Hay dos SVG distintos de la línea de cabecera en frames claros (`5f2d9` en 2458:624/2472:259 y `1f95e` en 2458:515/642/675): comprobar su color al exportar.
6. **ToggleSwitch (2286:106)** son dos imágenes SVG (On/Off) de 46×26 sin colores en el código de diseño. El interruptor claro es PROPUESTA (A7).
7. **Con `values-en` presente, un teléfono en inglés mostrará inglés sin que la persona elija** (A4).
8. **`colors.xml` conserva los colores de la plantilla** (`purple_200`… `teal_700`, `black`, `white`) sin usar: candidatos a borrar en la etapa 4.
9. **Los textos de `LessonScreen.kt` con acentos son de una `@Preview`** (`:326-332`); el contenido real de las lecciones está en `learning_lessons.json`. Esto corrige una afirmación del informe anterior de esta conversación: las lecciones **no** están en Kotlin.
10. **`MainActivity.kt` tiene un `AlertDialog` con textos fijos** ("Entendido", "Sesión cerrada", "Se cerró tu sesión porque lo pediste desde otro dispositivo.").
11. **Quiz:** las categorías son un `enum QuizCategory` y las preguntas llevan `id`, `category`, `difficulty` y `correctIndex`; el plan de estudio (`StudyPlan.kt`) se indexa por enum. Traducir el JSON no rompe la lógica.

---

## 3. Arquitectura del tema y de las preferencias

### 3.1 Modelo
```kotlin
enum class ThemeMode { DARK, LIGHT, SYSTEM }          // por defecto DARK
enum class AppLanguage(val tag: String) { ES("es"), EN("en") }   // por defecto ES

interface AppearanceRepository {                       // data/preferences
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
```
- **Tema:** clave `theme_mode` (cadena) en el DataStore **existente** `user_preferences` (`UserPreferences.kt`), pero en una clase aparte: `UserSetting` solo maneja booleanos y no conviene extenderlo. Un *fake* en memoria (`InMemoryPreferencesDataStore`) para pruebas, como ya hacen las demás clases.
- **Idioma:** se guarda en `AppCompatDelegate` (almacén propio de AppCompat con `autoStoreLocales`, y los ajustes del sistema en Android 13+). Un `LanguageRepository` (interfaz) envuelve `getApplicationLocales()` y `setApplicationLocales()`; en pruebas se usa un fake. DataStore solo guarda `language_initialized` (A3, A4).
- **Por dispositivo, no por cuenta** (D1): el cierre de sesión **no** borra tema ni idioma (hoy `user_preferences` está separado de `session_preferences`; confirmarlo en la etapa 2 con una prueba).
- `AppContainer` gana `appearanceRepository` y `languageRepository` (DI manual, interfaz + fake).

### 3.2 Lectura inicial sin parpadeo (D8)
1. `StartupViewModel` ya mantiene el splash (`setKeepOnScreenCondition { startRoute.value == null }`, `MainActivity.kt:60`). Se amplía: el splash se libera cuando **ruta de inicio y `themeMode` estén leídos** (`dataStore.data.first()`, milisegundos).
2. `MainActivity` coloca el valor leído como estado inicial de un `StateFlow<ThemeMode>` y lo pasa a `NightWatchProtocolTheme(themeMode)`: el primer frame ya sale con el tema correcto.
3. Antes de liberar el splash se fija el color de fondo de la ventana (`window.setBackgroundDrawable(ColorDrawable(bg))`) con el `bg/base` del tema resuelto, para que no haya un destello entre splash y Compose.
4. `Theme.NightWatchProtocol.Splash` mantiene `windowSplashScreenBackground #2C595D` (A6). `windowBackground` de `themes.xml` queda como `bg/base` oscuro (valor por defecto antes de leer la preferencia).

### 3.3 API de tokens
```kotlin
@Immutable class NightWatchColors(/* ver sección 4: bgBase, bgElevated, onElevated, textPrimary, textOnLight, ... */)
val LocalNightWatchColors = staticCompositionLocalOf { DarkNightWatchColors }
object NightWatchTheme { val colors: NightWatchColors @Composable @ReadOnlyComposable get() = LocalNightWatchColors.current }

@Composable fun NightWatchProtocolTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) { DARK -> true; LIGHT -> false; SYSTEM -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalNightWatchColors provides if (dark) DarkNightWatchColors else LightNightWatchColors) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Typography, content = content)
    }
}
```
- **Material 3:** ambos esquemas definen todos los roles que usan los componentes M3 en la app (primary/onPrimary, background/onBackground, surface/onSurface, surfaceVariant, outline, error/onError, inverseSurface/inverseOnSurface para `Snackbar`, `secondaryContainer`, etc.); ningún rol queda con el valor por defecto de Material.
- **Tokens propios** (`NightWatchTheme.colors`) para lo que Material no cubre: bordes de campo, selección, panel de confirmación, divisores, éxito, enlaces, tarjetas elevadas, interruptor, scrims.
- **Migración de constantes:** las constantes `NightWatch*` de nivel superior dejan de existir como `Color` fijo. Usos **no composables** a revisar (verificado con `grep`): `ArenaScreen.kt:55-56` y `SocialAvatar.kt:61-62` (`private val` de nivel superior que copian un token → pasar a leer el token dentro de la composición), 3 `@Preview(backgroundColor = 0x…)` (el valor de la anotación es una constante: queda como constante de preview justificada en la lista blanca), el dibujo de bitmap de `SocialAvatar.kt:172` (`#B07A4F`, independiente del tema; lista blanca) y la plantilla `colors.xml`. Los 42 `Color(0x…)` sueltos son todos `val` de nivel superior o literales en línea: pasan a leer `NightWatchTheme.colors`.
- **Dialogs, hojas inferiores, snackbars y menús:** los `CompositionLocal` se propagan al contenido de `Dialog`, `AlertDialog`, `DropdownMenu` y `Popup` porque viven en la misma composición. Conteo de uso hoy (`grep`, aproximado): 10 `AlertDialog`, 3 `DatePickerDialog` + 1 `DatePicker`, 1 `DropdownMenu`, 2 `SnackbarHost`, y 23 llamadas `…Dialog(` (incluye diálogos propios como los de Reportar y Bloquear). No hay `ModalBottomSheet` (la hoja de `HomeScreen.kt:71` es propia). Se definen `colors` explícitos para `DatePickerDefaults` y `Snackbar` (usan `inverseSurface`), y una prueba de que ningún diálogo usa colores por defecto de Material.
- **Barra de estado y de navegación:** dentro de `setContent`, un `DisposableEffect(dark)` llama a `enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(...)/light/dark, navigationBarStyle = …)` con iconos oscuros sobre fondo claro e iconos claros sobre oscuro (D8). Se prueba con tema distinto al del sistema.
- **Recursos que no son Compose:** `themes.xml` pasa a heredar de un tema AppCompat sin barra de acción (**no** `DayNight`: el tema lo decide la app, no `uiMode`; `AppCompatDelegate.setDefaultNightMode` no se usa). Los permisos y diálogos del sistema siguen al sistema.
- **Imágenes decorativas** (ilustraciones, línea de cabecera, ruedas dentadas al 25 %): se exportan versión clara y oscura desde el Figma y se eligen por tema (carpeta `drawable` + `drawable-night` **no** sirve: el tema no depende de `uiMode`; se seleccionan en código con `painterResource` por tema). Los URL de assets del MCP dan 403; se exportan con `download_assets` o a mano.

---

## 4. Tokens

### 4.1 Paleta del Figma (oscuro y claro) y contraste
Valores del oscuro = los de hoy (`ui/theme/Color.kt`, Figma). Valores claros = Figma (sección 5 de la referencia). Contraste = WCAG calculado sobre el fondo indicado.

| Token | Rol | Oscuro (hoy) | Claro | Contraste oscuro | Contraste claro |
|---|---|---|---|---|---|
| `bgBase` | fondo de pantalla | `#2C595D` | `#AEF5D5` | — | — |
| `textPrimary` | texto sobre `bgBase` | `#FFFFFF` | `#1C2B29` | 7.79 | 11.79 |
| `bgElevated` | tarjetas sobre `bgBase` (labs, examen, Social) | `#3E6E71` | **`#F7F4EC` (PROPUESTA)** | — | — |
| `onElevated` | texto sobre `bgElevated` | `#FFFFFF` | `#1C2B29` | 5.72 | 13.39 |
| `surfaceButton` | filas y tarjetas de opción, botones secundarios | `#D9D9D9` | `#D9D9D9` | — | — |
| `surfaceButtonMuted` | fondo de campo (oscuro), interruptor apagado | `#A1A1A1` | `#A1A1A1` | — | — |
| `fieldSurface` | campos y tarjetas de información | `#A1A1A1` (campos hoy) | `#F7F4EC` (unifica los tres fondos del Figma) | texto blanco 2.58 (reportado) | 13.39 |
| `textOnLight` | texto sobre `surfaceButton`/blanco/`fieldSurface` | `#162523` | `#1C2B29` | 11.25 (sobre `#D9D9D9`) | 10.43 / 13.39 / 14.72 |
| `onButton` | texto del CTA | `#000000` | `#1C2B29` | 14.88 (sobre `#D9D9D9`) | 14.72 (sobre blanco) |
| `ctaSurface` | botón primario | `#D9D9D9` | `#FFFFFF` | — | — |
| `borderAccent` | borde de campos y tarjetas | `#597F82` | `#597F82` | 1.78 s/ `#2C595D` (decorativo) | 3.52 s/ `#AEF5D5`; 3.99 s/ `#F7F4EC` |
| `selection` | borde de selección (3 px) y ✓ | `#E3B54F` | **`#926A1B` (ajuste de `#B2842C`)** | 4.08 s/ `#2C595D`; 1.35 s/ `#D9D9D9` | 3.46 s/ `#D9D9D9`; 3.91 s/ `#AEF5D5` (Figma `#B2842C`: 2.39 y 2.70) |
| `divider` | divisores | `#808080` (hoy, `MyProfileScreen.kt:50`) | `#3F4A44` | 2.80 s/ `#D9D9D9` (decorativo) | 7.40 s/ `#AEF5D5` |
| `error` | borde y énfasis de error | `#FF2E16` | `#B85947` | 2.10 s/ `#2C595D` | 4.19 s/ `#F7F4EC`; 3.69 s/ `#AEF5D5` |
| `textError` | texto de error | `#FF2E16` (A1) | `#1C2B29` + icono ⚠ | 2.10 (reportado) | 13.39 |
| `success` | requisitos cumplidos | `#4CAF50` | **`#1B6B2A` (PROPUESTA)** | 2.80 s/ `#2C595D` (reportado) | 5.28 s/ `#AEF5D5` (el `#4CAF50` no pasa) |
| `confirmationPanel` | panel de las confirmaciones | `#447579` | `#FFFFFF` | 5.16 (blanco encima) | 14.72 |
| `menuPill` | pastilla de cabecera del menú de usuario | `#99D9D9D9` (hoy) | `#99FFFFFF` (Figma: blanco 60 %) | — | — |
| `headerPanel` | banda de cabecera de Ver perfil | `#CAD5D7` | `#F7F4EC` (Figma) | 10.59 (texto `#162523`) | 13.39 |

### 4.2 Interruptor (PROPUESTA; el Figma no define colores)
| Token | Oscuro (hoy, `SettingsSwitch.kt:20-21`) | Claro (PROPUESTA) |
|---|---|---|
| `switchOn` | `#E3B54F` | `#926A1B` (mismo criterio que `selection`) |
| `switchOff` | `#A1A1A1` | `#A1A1A1` con borde `#597F82` |
| pulgar | (hoy) | blanco, con borde `#597F82` |

### 4.3 Colores sueltos del código (D5): token y valor claro
Contraste **oscuro** = el de hoy; contraste **claro** = del valor propuesto. "PROPUESTA" = no está en el Figma.

| Archivo:línea | Oscuro actual | Token | Claro propuesto | Contraste (claro) |
|---|---|---|---|---|
| `components/ConfirmationContent.kt:40` | `#447579` | `confirmationPanel` | `#FFFFFF` (Figma) | texto `#1C2B29` 14.72 |
| `components/ConfirmationContent.kt:150`, `components/SettingsRow.kt:35`, `exam/ExamIntroScreen.kt:42`, `examresult/ExamResultScreen.kt:54`, `lab/LabScreen.kt:73`, `labreport/LabReportScreen.kt:57`, `labs/LabsScreen.kt:66`, `lesson/LessonScreen.kt:64`, `home/NightWatchBottomBar.kt:38`, `profile/achievements/AchievementsScreen.kt:48`, `profile/changepassword/ChangePasswordScreen.kt:200`, `profile/help/HelpAndAboutScreens.kt:69`, `social/SocialComponents.kt:75` (13 usos) | `#162523` | `textOnLight` | `#1C2B29` (Figma) | 10.43 s/ `#D9D9D9` |
| `components/ProfileTopBar.kt:28`, `home/HomeTopBar.kt:27` | `#DDDDDD` (5.74 s/ `#2C595D`) | `topBarText` | `#1C2B29` (Figma: título de cabecera) | 11.79 s/ `#AEF5D5` |
| `components/SettingsRow.kt:32` (`NightWatchGold`), `components/SettingsSwitch.kt:20` | `#E3B54F` | `selection` / `switchOn` | `#926A1B` (PROPUESTA, ver 4.1) | 3.46 s/ `#D9D9D9` |
| `components/SettingsSwitch.kt:21` | `#A1A1A1` | `switchOff` | `#A1A1A1` + borde (PROPUESTA) | — |
| `exam/ExamIntroScreen.kt:41`, `lesson/LessonScreen.kt:63`, `path/LearningPathScreen.kt:68`, `profile/achievements/AchievementsScreen.kt:47` | `#3E6E71` | `bgElevated` | `#F7F4EC` + borde `#597F82` (PROPUESTA) | borde 3.99 |
| `exam/ExamScreen.kt:52`, `examresult/ExamResultScreen.kt:51`, `lab/LabScreen.kt:70`, `labreport/LabReportScreen.kt:54`, `labs/LabsScreen.kt:63`, `social/SocialComponents.kt:69` | `#3E6E71` | `bgElevated` (+ `onElevated` para su texto) | `#F7F4EC` (PROPUESTA) | texto 13.39 |
| `home/HomeScreen.kt:71` | `#204143` (1.42 s/ `#2C595D`) | `sheetBorder` | `#597F82` (PROPUESTA) | 3.52 s/ `#AEF5D5` |
| `home/Notices.kt:9` | `#94A6A7` (texto `#162523` 6.25) | `noticeSurface` | `#F7F4EC` + borde `#597F82` (PROPUESTA) | 13.39 |
| `social/SocialComponents.kt:72` | `#94A6A7` | `cardTint` | `#D9D9D9` (PROPUESTA; es el `surface/button` del Figma) | 10.43 |
| `profile/devices/DevicesScreen.kt:50`, `profile/myprofile/MyProfileScreen.kt:49` | `#404040` (7.35 s/ `#D9D9D9`) | `textSecondaryOnLight` | `#404040` | 9.43 s/ `#F7F4EC`; 7.35 s/ `#D9D9D9` |
| `profile/myprofile/MyProfileScreen.kt:160` | `#5E5E5E` (4.59 s/ `#D9D9D9`) | `textSecondaryOnLight` (variante suave) | `#5E5E5E` | 5.90 s/ `#F7F4EC` |
| `profile/myprofile/MyProfileScreen.kt:50` | `#808080` | `divider` | `#3F4A44` (Figma) | 7.40 s/ `#AEF5D5` |
| `profile/usermenu/UserMenuScreen.kt:176` | `#99D9D9D9` | `menuPill` | `#99FFFFFF` (Figma) | — |
| `social/ProfileSections.kt:95` | `#73000000` | `scrimSoft` | igual (superpone imágenes de fondo; **no** depende del tema) | — |
| `social/ProfileSections.kt:511` | `#CC333333` | `overlayDark` | igual | texto blanco sobre `#333333` ≈ 12:1 |
| `social/ProfileSections.kt:548` | `#8C8C8C` | `pickerSurface` | igual (PROPUESTA; panel pequeño del selector de fondos) | comprobar |
| `social/SocialAvatar.kt:172` | `#B07A4F` | — (se dibuja en un bitmap) | **lista blanca** | — |
| `social/SocialComponents.kt:78` | `#C0503E` | `failAccent` | `#A13A2A` (PROPUESTA) | 6.06 s/ `#F7F4EC` |
| `ui/theme/Color.kt` `NightWatchLinkAccent` | `#A2DBFF` (5.24 s/ `#2C595D`) | `link` | `#0B5A8A` (PROPUESTA) | 5.92 s/ `#AEF5D5`; 7.38 s/ blanco |
| `ui/theme/Color.kt` `NightWatchHeaderPanel` | `#CAD5D7` | `headerPanel` | `#F7F4EC` (Figma, banda de Ver perfil) | 13.39 |
| `ui/theme/Color.kt` `NightWatchError` / `Success` | `#FF2E16` / `#4CAF50` | `error` / `success` | `#B85947` / `#1B6B2A` | 4.19 / 5.28 |
| `Color.Black` (27 usos), `Color.White` (11 usos) | — | revisar uno a uno: texto/relleno → token; sombras y máscaras → `scrim*` | según uso | — |

### 4.4 Estados no definidos en el Figma (PROPUESTA)
Deshabilitado, pulsado y foco: se derivan de los de Material 3 (`onSurface` al 12 %/38 %); foco visible con borde `selection`. Se anotan en la lista de aprobación.

---

## 5. Estrategia de idioma

- **Mecanismo (D9):** dependencia `androidx.appcompat` (versión estable fijada, **no** actualizar AGP/Kotlin: CLAUDE.md §2); `MainActivity : AppCompatActivity`; `themes.xml` sobre tema AppCompat; `res/xml/locales_config.xml` con `es` y `en`; `android:localeConfig="@xml/locales_config"` en el manifiesto; `AppLocalesMetadataHolderService` (con `autoStoreLocales`) para Android < 13. `setApplicationLocales` recrea la actividad: se confía en el estado ya guardado (por eso importa R-01).
- **Base:** `res/values/` queda en **español** (idioma base); `res/values-en/` trae el inglés. Un idioma de sistema distinto de es/en cae en español.
- **Dónde viven los strings por módulo** (se mantiene el esquema actual; archivos nuevos con el mismo nombre en `values-en/`):
  | Módulo | Archivo (`values/`) | Prefijo de clave | Estado hoy |
  |---|---|---|---|
  | 1 y 2 autenticación, quiz, verificación, perfil y ajustes | `strings_auth.xml`, `strings_quiz.xml`, `strings_perfil.xml` | `auth_`, `quiz_`, `profile_`, `settings_` | **No existe** (F-09) |
  | 3 aprendizaje | `strings_aprendizaje.xml` | `learning_` | existe (146) |
  | 4 Social | `strings_social*.xml` (4 archivos) | `social_` | existe (331) |
  | Comunes, notificaciones, insignias, plan, errores | `strings_comunes.xml`, `strings_notificaciones.xml` | `common_`, `notif_`, `badge_`, `plan_`, `error_` | **No existe** |
- **Nomenclatura:** `<prefijo>_<pantalla>_<elemento>` en minúsculas con guion bajo (ej. `profile_edit_save`, `auth_login_error_network`). Sin texto en las claves.
- **`UiText` (D10):** `sealed interface UiText { Resource(@StringRes id, args), Plural(@PluralsRes id, count, args), Dynamic(String) }` en `ui/common`, con `asString()` composable y de contexto. Los **ViewModels** exponen `UiText` en lugar de `String`. La **capa `data` no usa recursos**: emite enums o ids que la UI traduce (`AuthError` ya funciona así). Aplicación: `BadgeDefinition` pasa de `name: String` a `id: BadgeId` y la UI traduce (`Badges.kt`); `StudyPlan` pasa de cadenas a una función `(level, category)` → clave que la UI traduce; `ProfileValidation.kt` (`MESSAGE_*`) pasa a `UiText`; `ReminderKind` deja de tener `title`/`text`/`channelName` y guarda ids de recurso resueltos por el `Notifier`.
- **Plurales:** `<plurals>` para los conteos ("N dispositivos activos", "N de 7 islas", insignias, amigos, días de racha). Español e inglés usan `one`/`other` (el español además puede requerir `many` en lint). Ya hay 8 `plurals`/arrays en recursos.
- **Formatos (D12):** `BirthDate.display()` (hoy `%02d/%02d/%04d` con `Locale.ROOT`, `BirthDate.kt:20`) pasa a un formateador por idioma (PROPUESTA: español `dd/MM/yyyy`; inglés `MM/dd/yyyy`); el valor guardado sigue siendo ISO (`iso()`). `ProfileDataExporter` (fecha ISO UTC con `Locale.US`) **no cambia**. Los usos de `Locale.ROOT` en código (`DeviceIdentity.kt:54`, `LabTerminal.kt:97`, `LabReportViewModel.kt:157`) son de lógica o mayúsculas, no de visualización: se revisan sin cambiar el comportamiento (el `"de"` de `LabReportViewModel` es español: hay que reescribirlo por idioma). `ColombiaZone` y `colombiaClock()` (`data/learning/Streak.kt:12-15`) **no se tocan**.
- **Notificaciones (D11):** `ReminderNotifier` construye título y texto con el **contexto con el idioma elegido** (no el del sistema); como el `Worker` corre sin Activity, se resuelve el idioma desde `LanguageRepository` y se usa `createConfigurationContext`. Al cambiar de idioma se vuelve a registrar el canal con el mismo id (Android actualiza el nombre) y se reprograman los 2 recordatorios (`DAILY_PRACTICE` 19:00, `STREAK_AT_RISK` 21:00).
- **Contenido JSON (D13):** `assets/learning_lessons.json` (6 lecciones), `learning_labs.json` (6 labs), `learning_exam.json` (6 preguntas) y `quiz_questions.json` (20 preguntas, ~38 KB en total, ≈ 6 000 palabras PROPUESTA de estimación). Se agregan `assets/en/…` o sufijo `_en`, elegidos por `LearningContentRepository` según el idioma. Paridad probada: mismos `id`/`topic`, mismo `correctIndex` y misma cantidad de `options`; **los comandos de laboratorio no se traducen** (los valida `LabTerminal`).
- **Servidor (OUT):** los códigos de rechazo de Social ya se traducen en la app (`ui/social/SocialErrorText.kt`, que concentra 29 referencias a rechazos), por lo que quedan cubiertos. No lo están las 72 preguntas de batalla y los 12 retos de ulti (A5). Los nombres de usuario son texto libre y no se traducen.
- **Accesibilidad:** las `contentDescription` también se traducen (hoy hay varias fijas, p. ej. `VerifyCodeScreen.kt:303`, `ChangePasswordScreen.kt:170`).

---

## 6. Inventario de trabajo (cifras del código real)

| Zona | Archivos `.kt` en `ui/` | Con tokens `NightWatch*` | Colores literales (incl. `Color.Black/White`) | Literales de texto (heurística, incluye `@Preview`) |
|---|---|---|---|---|
| Módulos 1 y 2 (`auth`, `quiz`, `verifycode`, `profile`, `applock`, `biometric`, `postauth`, `startup`, `session`) | 56 | 20 | 25 | **≈ 201** |
| Módulo 3 (`home`, `path`, `labs`, `lab`, `labreport`, `lesson`, `exam`, `examresult`) | 29 | 11 | 29 | ≈ 26 (casi todos de `@Preview`; ya usan `strings_aprendizaje.xml`) |
| Módulo 4 / Social (`social`) | 37 | 14 | 11 | ≈ 17 (casi todos de `@Preview`; ya usan `strings_social*.xml`) |
| Comunes (`components`, `navigation`, `theme`) | 22 | 11 | 23 (10 son `ui/theme`) | ≈ 4 |
| `data/` y `di/` | — | — | — | ≈ 107, de los que llegan a la pantalla: insignias (14), plan de estudio (12), recordatorios (6), `AuthError` (19), `SupabaseAuthRepository` (9) y otros; el resto son mensajes internos de excepción |
| `MainActivity` | — | — | — | 1 diálogo con 3 textos |
| **Total `ui/`** | **144** (44 pantallas `*Screen`) | **55** | **88** | |

- Recursos hoy: 6 archivos `strings*.xml`, **478 `<string>`** (1 + 146 + 33 + 32 + 236 + 30) y 8 `plurals`/arrays; **no existe `values-en`**.
- Textos por traducir (estimación PROPUESTA): 478 existentes + ≈ 300 por migrar (F-09) ≈ **780 cadenas**, más ≈ 6 000 palabras de JSON.
- Pruebas: 1399 unitarias (105 archivos de prueba) y 12 instrumentadas; **47 archivos** de prueba comparan texto con literales en español (85 líneas de `assertEquals` con acentos, p. ej. en `data/supabase`, `data/learning`, `data/social`, `data/profile`, `ui/social`, `ui/profile/*`).
- Diálogos: 10 `AlertDialog`, 3 `DatePickerDialog`, 1 `DropdownMenu`, 2 `SnackbarHost`.

### Estimación de esfuerzo por etapa (PROPUESTA; días de trabajo asistido, incluye pruebas, sin la revisión visual del grupo)
| Etapa | Esfuerzo |
|---|---|
| 1 spec y paleta | 0.5 |
| 2 preferencias | 1–1.5 |
| 3 F-09 | 3–4 |
| 4 tema | 3 |
| 5 migrar pantallas | 5–6 |
| 6 cierre 5A | 1 |
| 7 R-01 + base de idioma | 1–1.5 |
| 8 `values-en` | 3 |
| 9 contenido JSON en inglés | 3–4 |
| 10 notificaciones, fechas y cierre 5B | 2 |
| **Total** | **≈ 23–27** |

---

## 7. Plan por etapas

Cada etapa: rama propia, un commit por tema, PR con 1 aprobación y "Create a merge commit"; antes de cada commit `./gradlew assembleDebug testDebugUnitTest lintDebug` en 0 errores (y `compileDebugAndroidTestKotlin` si se tocan pruebas instrumentadas). **No se actualizan AGP, Gradle, Kotlin, supabase-kt ni Ktor**; la única dependencia nueva prevista es `androidx.appcompat` (etapa 7).

### Etapa 0 · PR de `CLAUDE.md` — hecho aparte.

### Etapa 1 · Spec y paleta aprobada
- Archivos: este documento y `modulo-5-figma-referencia.md`.
- Hecho cuando: el grupo aprueba la sección 10 (paleta propuesta, vetos a D1–D14) y responde las preguntas 11.1 a 11.3.
- Riesgo: que el claro de pantallas sin Figma se deba rediseñar; se mitiga con la lista de aprobación.

### Etapa 2 · Preferencias
- Nuevos: `data/preferences/ThemeMode.kt`, `AppearancePreferences.kt` (`AppearanceRepository` + implementación DataStore + fake), `data/language/LanguageRepository.kt` (interfaz + fake; la implementación con AppCompat llega en la etapa 7), `ui/profile/appearance/AppearanceViewModel.kt`.
- Editados: `di/AppContainer.kt` (añade los repositorios).
- Pruebas nuevas: valor por defecto `DARK`; persistencia; que cerrar sesión no borra `theme_mode`; `AppearanceViewModel` con Turbine y `MainDispatcherRule`.
- Hecho cuando: pruebas verdes y **ningún cambio visual**.
- Riesgo: bajo.

### Etapa 3 · F-09 sin cambio visual
- Archivos: los ≈ 56 de módulos 1 y 2 con literales (principalmente `ui/profile/*`, `ui/auth/*`, `ui/components/*`, `ui/quiz/*`, `ui/verifycode/*`, `ui/applock`, `ui/biometric`, `MainActivity.kt`), `data/learning/Badges.kt`, `data/quiz/StudyPlan.kt`, `data/auth/AuthError.kt` (la UI lo traduce), `ui/profile/ProfileValidation.kt`, `data/notifications/ReminderKind.kt`. Nuevos: `res/values/strings_auth.xml`, `strings_quiz.xml`, `strings_perfil.xml`, `strings_comunes.xml`, `strings_notificaciones.xml`, `ui/common/UiText.kt`.
- Pruebas: ajustar las ≈ 47 pruebas que comparan texto para comparar **claves o `UiText`**; test de que los textos resueltos en español siguen siendo **idénticos** a los actuales (captura de los literales originales).
- Hecho cuando: cero literales de texto en `ui/` fuera de `@Preview` (guarda por `grep` en una prueba) y la app se ve igual.
- Riesgos: volumen; plurales en español; pruebas que dependen del texto. Se puede dividir en dos PR (perfil/ajustes; auth/quiz/resto).

### Etapa 4 · Tema
- Nuevos: `ui/theme/NightWatchColors.kt` (tokens oscuro y claro), `ui/theme/Theme.kt` (reescrito con `themeMode`), tests `ContrastTest`, `NoHardcodedColorsTest`.
- Editados: `ui/theme/Color.kt`, `MainActivity.kt` (lee el tema inicial, `enableEdgeToEdge` por tema), `StartupViewModel`, `themes.xml` (AppCompat se pospone a la etapa 7; aquí solo `windowBackground`), `ui/profile/appearance/AppearanceAndLanguageScreens.kt` (**Apariencia funcional**: Oscuro/Claro/Automático con ✓ y borde `selection`; **Idioma** sigue inerte hasta la etapa 7), componentes de `ui/components/*`, las pantallas de Perfil y Ajustes (23 con frame claro).
- Pruebas: `ContrastTest` calcula todos los pares de 4.1/4.3 (texto ≥ 4.5, grande ≥ 3, componente ≥ 3); `NoHardcodedColorsTest` falla con `Color(0x…)`, `Color.Black`, `Color.White` fuera de `ui/theme` (lista blanca: `SocialAvatar.kt:172`, 3 `@Preview`); prueba de que `THEME` por defecto es oscuro; prueba instrumentada opcional del cambio sin parpadeo.
- Hecho cuando: Perfil y Ajustes se ven como el Figma en claro y como hoy en oscuro; Apariencia cambia el tema en vivo y se conserva al reiniciar.
- Riesgos: 55 archivos con tokens directos; diálogos y fechas con colores por defecto; iconos de barras.

### Etapa 5 · Migrar pantallas por módulo (con revisión visual)
Un PR por bloque: (5a) autenticación y verificación, (5b) quiz y onboarding, (5c) Inicio, Ruta, Labs, Lección, Lab, Informe, Examen, (5d) Social. Archivos: el resto de los 144 de `ui/` y los 42 `Color(0x…)` de 4.3; imágenes decorativas por tema.
- Pruebas: `NoHardcodedColorsTest` se endurece (la lista blanca no crece sin justificación); capturas de revisión del grupo.
- Hecho cuando: la lista visual (sección 9) pasa para claro/oscuro en el bloque.
- Riesgo: **paleta clara PROPUESTA** de pantallas sin Figma (la aprueba el grupo); ilustraciones con transparencia sobre fondo claro.

### Etapa 6 · Cierre 5A (v2.3.0)
- Informe `docs/superpowers/reports/modulo-5a-informe.md`, lista visual, `versionName` 2.3.0 / `versionCode` 6, actualización de `CLAUDE.md`. Etiqueta y Release las hace el usuario.
- Hecho cuando: PR aprobado con la lista visual del grupo.

### Etapa 7 · Verificar R-01 + base de idioma
- Editados: `gradle/libs.versions.toml` y `app/build.gradle.kts` (`appcompat`), `MainActivity.kt` (`AppCompatActivity`), `themes.xml` (tema AppCompat sin `DayNight`), `AndroidManifest.xml` (`localeConfig`), `res/xml/locales_config.xml`, `LanguageRepository` (implementación AppCompat), pantalla de Idioma funcional (Español/English con ✓), `StartupViewModel` (arranque fija español, A4).
- **R-01:** prueba que simula `isChangingConfigurations` en `LeaveScreenEffect`; auditoría de `DisposableEffect`/`onDispose` con efectos en `ui/social/*` (p. ej. `ArenaScreen`); prueba manual: reto pendiente + rotar + cambiar tema + cambiar idioma + fuente grande sin perder el reto.
- Hecho cuando: cambiar de idioma no pierde estado ni cancela retos y no hay `IllegalStateException` de tema.
- Riesgos: **tema AppCompat vs Material.Light**; recreación; `FragmentActivity` → `AppCompatActivity` con `androidx.biometric`.

### Etapa 8 · `values-en` para toda la UI
- Nuevos: `res/values-en/` con los mismos 11 archivos de strings; test de paridad (claves y placeholders `%1$s`, `%d`, plurales); revisión de traducción por el grupo.
- Pruebas: paridad `values`/`values-en`; prueba de que ninguna cadena en inglés queda vacía o igual al español sin motivo (lista blanca: nombres propios como "NightWatch Protocol", emojis).
- Hecho cuando: la UI completa se ve en inglés sin truncamientos (lista visual, fuente grande).
- Riesgo: **calidad y volumen de traducción** (≈ 780 cadenas); textos más largos que rompan diseños.

### Etapa 9 · Contenido JSON en inglés
- Nuevos: JSON en inglés de lecciones, labs, examen y quiz; `LearningContentRepository` y el cargador del quiz eligen por idioma; pruebas de paridad (ids, `correctIndex`, número de opciones, comandos de laboratorio idénticos). Se puede cerrar en una versión posterior.
- Riesgo: que una traducción cambie el sentido de una respuesta correcta (revisión humana obligatoria).

### Etapa 10 · Notificaciones, recordatorios, fechas y cierre 5B (v2.4.0)
- Editados: `data/notifications/ReminderNotifier.kt`, `ReminderScheduler.kt`, `ReminderCoordinator.kt` (re-registro de canales y reprogramación al cambiar idioma), `data/profile/BirthDate.kt`, `LabReportViewModel.kt`; pruebas de formato por idioma y de que `ColombiaZone` no cambia.
- Informe `modulo-5b-informe.md`, `versionName` 2.4.0 / `versionCode` 7, `CLAUDE.md`.
- Hecho cuando: lista visual completa y notificaciones comprobadas en teléfono con ambos idiomas.

---

## 8. Riesgos y mitigaciones

| # | Riesgo | Mitigación |
|---|---|---|
| 8.1 | **R-01** (ya corregido por razonamiento, sin prueba de Activity) | Etapa 7: prueba automática + manual y auditoría de otros `onDispose` |
| 8.2 | **Tema AppCompat vs `Material.Light`**: `setApplicationLocales` exige tema AppCompat; el splash apunta a `Theme.NightWatchProtocol` | Cambiar `themes.xml` a AppCompat sin `DayNight` en la etapa 7; probar arranque en frío, rotación y Android 8 (minSdk 26) y 13+ |
| 8.3 | **55 archivos con `NightWatch*` directos y solo 2 con `colorScheme`** | Tokens por `CompositionLocal` y migración por bloques con `NoHardcodedColorsTest` |
| 8.4 | **Volumen y calidad de traducción** (≈ 780 cadenas + ≈ 6 000 palabras) | Revisión del grupo por módulo; paridad automática; glosario PROPUESTA de términos de ciberseguridad |
| 8.5 | **Paleta clara sin Figma** para casi toda la app | Reglas derivadas de los 23 frames; el grupo la aprueba con capturas por bloque |
| 8.6 | **Recreación de actividad** al cambiar idioma o tema | Estado en ViewModels/DataStore; sondeo de Social ya protegido; probar los flujos largos (registro con código, examen, laboratorio) |
| 8.7 | **Contraste del Figma** (selección dorada 2.39:1, ✓, rojos) y del oscuro actual | Ajustes mínimos marcados (A2) y test de contraste; lo del oscuro solo se reporta |
| 8.8 | **Rotación y fuente grande** con textos en inglés más largos | Lista visual con fuente grande y rotación en las pantallas críticas |
| 8.9 | **Social en inglés con preguntas en español** | Aceptado (A5); aviso a los jugadores; futuro módulo para traducir el banco |
| 8.10 | **Las 1399 pruebas, ≈ 47 archivos con literales en español** | Etapa 3 cambia las aserciones a claves/`UiText` y fija una prueba que compara los textos resueltos con los originales |
| 8.11 | **Parpadeo de tema al arrancar** | Lectura previa al splash (3.2) y color de ventana fijado antes del primer frame |
| 8.12 | **Idioma del sistema inglés ⇒ app en inglés sin elegir** | A4: fijar español en el primer arranque |
| 8.13 | **Notificaciones fuera de Activity** no heredan el idioma de la app | Contexto de configuración explícito en `ReminderNotifier` (D11) |
| 8.14 | **Copia de seguridad / `data_extraction_rules`**: el idioma y el tema podrían restaurarse en otro teléfono | Revisar `backup_rules.xml` y `data_extraction_rules.xml` en la etapa 2 (`allowBackup` ya es `false`) |

---

## 9. Lista de verificación visual para el grupo

**Ejes:** aspecto {oscuro, claro, automático con sistema oscuro, automático con sistema claro} × idioma {es, en} × fuente {normal, grande (escala 1.3 del sistema)} × orientación {vertical, horizontal en las pantallas marcadas ↻}.
**Combinaciones mínimas por pantalla:** (1) oscuro/es/normal (**debe verse idéntico a v2.2.0**); (2) claro/es/normal; (3) claro/en/grande; (4) oscuro/en/normal. Rotación ↻ y "automático" en las pantallas críticas.
**Qué mirar:** contraste legible, nada cortado ni solapado, iconos de barra de estado y de navegación coherentes, sin parpadeo al arrancar ni al cambiar, diálogos/menús/snackbars con el tema, imágenes decorativas visibles, TalkBack lee los textos en el idioma elegido.

| Grupo | Pantallas (rutas) |
|---|---|
| Autenticación | `Login` (y errores y diálogo de recuperación), `Register`, `VerifyCode` (↻), `NewPassword`, `PasswordReset` |
| Quiz | `PostAuthGate`, `PlacementQuiz` (↻), `PlacementResultRoute` |
| Contenedor | `Home` pestañas Inicio, Ruta y Labs, hoja inferior, avisos "Próximamente" |
| Aprendizaje | `Lesson` (↻), `Lab` (↻, terminal), `LabReport` (éxito y error), `ExamIntro`, `Exam`, `ExamResult` |
| Social | `Friends`, `AddFriend`, `FriendRequestsRoute`, `BlockedPeopleRoute`, `PairStreaksRoute`, `PairDetailRoute`, `BattlesRoute` ↻, `QuickSearchRoute` ↻, `BattleRoute` (arena) ↻, `BattleResultRoute`, `FriendProfileRoute`, `MyProfileRoute`, `EditMyProfileRoute`, diálogos Reportar y Bloquear |
| Perfil y Ajustes | `UserMenu`, `Profile`, `EditProfile` (selector de fecha), `Achievements`, `NotificationSettings`, `Appearance`, `Language`, `PrivacySecurity`, `Help`, `About`, `ChangePassword` (y sus 2 errores), `RecoveryEmail`, `Devices`, `ConfirmIdentity`, `ChangeEmail`, `ProfileUpdated`, `EmailUpdated`, `PasswordUpdated`, `RecoveryEmailSaved`, `DeviceSignedOut` |
| Sistema | Splash, bloqueo biométrico (`AppLockScreen`), diálogo "Sesión cerrada", notificaciones de recordatorio (es/en), permiso de notificaciones |

**Comprobaciones de comportamiento:** cambiar de idioma con un reto pendiente en Batallas (R-01); cambiar tema con el teclado abierto; reiniciar la app y conservar tema e idioma; cerrar sesión y volver a entrar (se conservan); teléfono en inglés en el primer arranque (debe abrir en español, A4); fechas y plurales por idioma.

---

## 10. Lista de aprobaciones del grupo

1. **Paleta propuesta**: `bgElevated` claro `#F7F4EC`, `cardTint` `#D9D9D9`, `noticeSurface`, `sheetBorder` `#597F82`, `failAccent` `#A13A2A`, `link` `#0B5A8A`, `success` `#1B6B2A`, `pickerSurface`, interruptor claro (4.2), estados deshabilitado/pulsado/foco (4.4).
2. **Ajuste del dorado** `#B2842C` → `#926A1B` (A2) y el color del ✓ claro (pregunta 11.1).
3. **"Contactar a soporte"** en claro: blanco como el resto de CTA (D4) o `#D9D9D9` como dibuja el Figma.
4. **Traducciones** del inglés (≈ 780 cadenas, JSON de contenido) y el glosario de términos de ciberseguridad.
5. **Formato de fecha en inglés** (`MM/dd/yyyy` PROPUESTA).
6. **A1 a A7** (decisiones adicionales de la sección 1).
7. **Vetos a D1–D14**: cada integrante puede vetar una decisión indicando cuál y por qué; sin veto antes de la etapa 2 se consideran aprobadas.
8. **Calendario**: dos versiones (v2.3.0 y v2.4.0) y la posibilidad de cerrar la etapa 9 después.

---

## 11. Preguntas abiertas (solo lo que no se puede decidir con lo leído)

1. **Color del ✓ de la selección clara:** el Figma lo pinta `#1C2B29` (contraste 10.43:1 sobre `#D9D9D9`); D4 decía `#B2842C` y A2 lo ajusta a `#926A1B` (3.46:1). ¿Cuál se usa? *(Recomendación: `#1C2B29`, igual que el Figma, y dejar el borde en `#926A1B`.)*
2. **Social en inglés con preguntas en español (A5):** ¿se acepta para v2.4.0 o se agrega un aviso visible en la arena? ¿Y se prioriza un módulo posterior para el banco de preguntas?
3. **Confirmar identidad y el diálogo de recuperación no tienen frame claro:** ¿el grupo las revisa con capturas en la etapa 5a o prefiere pedir el diseño al Figma antes?
4. **Idioma por defecto con teléfono en inglés (A4):** ¿fijar español de forma explícita en el primer arranque (propuesto) o respetar el idioma del sistema aunque contradiga D2?
5. **Quién traduce y revisa** el inglés y el contenido de los labs (¿persona del grupo con nivel suficiente?). Condiciona el calendario de las etapas 8 y 9.
