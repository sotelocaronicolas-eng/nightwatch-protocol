# Módulo 5 · Parte 5A (tema claro/oscuro/automático): cierre — v2.3.0

Rama `modulo-5-tema-completo` (etapas 5 y 6). Claude Code no ve el teléfono: **nada de lo visual está comprobado en pantalla**; lo hace el
grupo con `etapa-5-verificacion-visual.md`. Spec: `specs/2026-11-02-modulo-5-personalizacion-design.md`.

## 1. Qué se hizo

- Los 22 archivos con `Color(0x…)`, `Color.Black` o `Color.White` fuera de `ui/theme` pasaron a tokens de `NightWatchTheme.colors`
  (en cinco commits: autenticación, quiz/Inicio/Ruta, aprendizaje, Social, global).
- Se borró `ui/navigation/ThemedDestinations.kt`; las 20 rutas de Perfil y Ajustes y la de verificación de código usan `composable` otra vez.
  `MainActivity` pasa el modo elegido al tema raíz (`NightWatchProtocolTheme(chosenMode)`): diálogos, snackbars, hojas y el selector de
  fecha lo heredan. Se eliminó `LocalChosenThemeMode` (ya nadie lo leía).
- Ventana y barras del sistema: en cuanto se lee el modo guardado (antes del primer frame de la app) se aplican con el color de ese modo;
  después `SystemChromeEffect` las mantiene según el modo elegido (iconos oscuros en claro, claros en oscuro) en **todas** las rutas.
- `NoHardcodedColorsTest` ya no tiene lista pendiente: cubre todo `app/src/main/java` fuera de `ui/theme` y de las funciones `@Preview`,
  con una sola excepción permanente.

## 2. Tokens nuevos (`NightWatchColors`)

El valor oscuro es idéntico al color que había en el código (`NightWatchColorsParityTest`). Todos los claros son **PROPUESTA** (sin frame en el Figma)
salvo los que la spec 4.3 ya marcaba.

| Token | Oscuro (igual que hoy) | Claro (PROPUESTA) | Contraste claro | Se usa en |
|---|---|---|---|---|
| `sheetBorder` | `#204143` | `#597F82` | 3.52 sobre `#AEF5D5` | borde y asa de la hoja de Inicio |
| `noticeSurface` | `#94A6A7` | `#F7F4EC` | texto `#1C2B29` 13.39 | chapa del usuario, tarjeta de progreso de la Ruta, tarjetas de lección (`PillColor`) |
| `cardTint` | `#94A6A7` | `#D9D9D9` | texto `#1C2B29` 10.43 | tarjetas de código de amigo y estadísticas de Social |
| `failAccent` | `#C0503E` | `#A13A2A` | 6.06 sobre `#F7F4EC` | derrota, lado del rival, borde de respuesta incorrecta |
| `scrimSoft` | `#73000000` | igual | — | capa suave sobre las imágenes de fondo del perfil |
| `overlayDark` | `#CC333333` | igual | blanco encima, peor caso (sobre blanco) ≈ 6.7:1 | botón de cambiar fondo |
| `pickerSurface` | `#8C8C8C` | igual | — | panel pequeño del selector de fondos |

Las imágenes y sus capas no dependen del tema: por eso `scrimSoft`, `overlayDark` y `pickerSurface` son iguales en claro.

Reutilizados sin token nuevo: `bgElevated` (tarjetas, aros de los iconos), `textOnLight`, `onSurfaceButton` (texto y emoji negros sobre
superficies claras), `headerLine` (la línea blanca bajo las barras), `topBarText`, `onLink`, `fieldText` (el "?" de la búsqueda rápida).

## 3. Excepciones documentadas de la guarda de colores

| Archivo | Por qué |
|---|---|
| `ui/social/SocialAvatar.kt` (`#B07A4F`) | Relleno de un bitmap del avatar propio: es un dato dibujado, no depende del tema (spec 4.3). |

`@Preview` con `backgroundColor = 0x…` (constantes de anotación) no entran en la guarda; las funciones `@Preview` tampoco. Las vistas previas
siguen en el color oscuro: no se tocaron.

## 4. Decisiones PROPUESTA (el grupo las aprueba o cambia)

1. Los siete tokens de la sección 2 (valores claros).
2. **Chapa y tarjetas de lección en claro sin borde:** la spec 4.3 pide `#F7F4EC` + borde `#597F82`; no se añadió el borde porque cambiaría el layout
   (regla "no tocar layouts"). Contraste entre `#F7F4EC` y el fondo `#AEF5D5` bajo; si se ve plano, se añade el borde en una tarea aparte.
3. **Aro de los círculos dorados** (lección, intro del examen, isla completada): `bgElevated` (`#F7F4EC` en claro), como pedía la spec para `bgElevated`.
4. **Camino punteado de la Ruta en claro:** el drawable es dorado `#E3B54F` (1.2:1 sobre el fondo claro, casi invisible). En claro se tiñe con `selection`
   (`#926A1B`). En oscuro no hay filtro, así que sigue igual. Cambio en `LearningPathScreen.kt`.
5. **"?" de la búsqueda rápida:** usa `fieldText` (blanco en oscuro, como antes; `#1C2B29` en claro) sobre el círculo gris `#A1A1A1`.
6. **Sombrero del mago** (avatar de la arena): cuerpo con `textOnLight` (oscuro `#162523` igual que antes; claro `#1C2B29`).
7. **Chapa de texto negro sobre superficies claras:** todo `Color.Black` que era texto/emoji sobre `#D9D9D9`/cards pasó a `onSurfaceButton`
   (negro en oscuro, `#1C2B29` en claro).
8. **Splash del sistema:** sigue oscuro (`#2C595D`) aunque el modo sea claro; ver sección 6.

## 5. Lista oscura de contraste (deuda técnica, NO corregida: "el oscuro no cambia")

Las seis excepciones de la etapa 4 conservan sus valores: 2.58 (`fieldText/fieldSurface`), 2.10 (`textError/bgBase`, `error/bgBase`), 2.80
(`success/bgBase`, `divider/surfaceButton`), 2.54 (`textPrimary/menuPill`), 4.00 (`textMutedOnLight/fieldSurface`), 3.45
(`textSecondary/confirmationPanel`). La lista completa (con las demás: 1.44, 1.35, 1.78, 1.53, 1.70) sigue en `ContrastTest.knownDarkShortfalls`.

**Nuevas, halladas al ampliar el test a los tokens de la etapa 5** (colores que ya estaban en oscuro; se documentan, no se corrigen):

| Par oscuro | Contraste | Qué es |
|---|---|---|
| `sheetBorder/bgBase` | 1.42 | borde decorativo de la hoja de Inicio |
| `failAccent/bgBase` | 1.65 | rojo de derrota sobre el fondo |
| `failAccent/bgElevated` | 1.21 | el mismo rojo sobre las tarjetas `#3E6E71` |
| `failAccent/cardTint` | 1.86 | el mismo rojo sobre las tarjetas `#94A6A7` |
| `selection/bgElevated` | 2.99 | dorado sobre las tarjetas (a 0.01 de la meta) |
| `bgElevated ring/selection` | 2.99 | aro teal sobre el círculo dorado |

Todos los pares **claros** cumplen la meta (texto ≥ 4.5, grande ≥ 3, componente ≥ 3).

## 6. Recursos y fondos revisados (res/)

Revisados y **no cambiados** salvo el camino de la Ruta (ver 4.4). Reportados para que el grupo decida:

- `values/themes.xml`: `windowBackground` y `windowSplashScreenBackground` son `#2C595D` (oscuro). Un usuario con modo Claro ve la pantalla de inicio
  del sistema oscura un instante y luego la app clara (el modo guardado solo se conoce al leer DataStore). Arreglarlo exige leerlo antes de la actividad
  (p. ej. un tema de arranque por modo en el manifiesto) o aceptarlo. **No se tocó.**
- `drawable/ic_confirm_check.xml` (relleno `#2C595D`, trazo `#D9D9D9`): círculo con ✓ de las confirmaciones; sobre el panel blanco del claro es legible. Revisar a ojo.
- `drawable/ic_id_card.xml` (`#EAF9FA`, la tonalidad clara de la ilustración del sobre) en "Confirmar identidad": sobre fondo claro puede perder contraste. Revisar a ojo.
- `drawable/ic_notification.xml` (blanco): es de la barra de notificaciones del sistema, no depende del tema.
- `drawable-nodpi/` (`img_appearance_bg.png` con alfa 0.8, `img_language_bg.png` con alfa 0.7, `img_home_*`, `img_island_halo.png` con alfa 0.5, `img_devices`,
  `img_envelope`, `img_gear_*`, `img_support_mail`, `img_lab_report_failed`, `img_logo_nightwatch`): **no se pudo ver cómo quedan sobre `#AEF5D5`**; las que tienen
  transparencia son las más probables de verse mal. Rutas a mirar primero: `img_appearance_bg.png`, `img_language_bg.png`, `img_island_halo.png`, `img_home_exit.png`.
- `profile_bg_1.webp` … `profile_bg_5.webp`: tienen capa oscura suave (`scrimSoft`) y texto claro encima en ambos modos; revisar que el texto se lea sobre las cinco en claro.
- Canvas: solo `SocialAvatar` dibuja (sombrero, varita y destello); usa tokens (ver 4.6).

## 7. Pruebas

- Total de pruebas unitarias: **1458**, 0 fallos (antes 1456: +3 nuevas —paridad oscura de los tokens de la etapa 5, tokens iguales en claro, los tres modos en el
  ViewModel de Apariencia—, −1 la prueba de la transición "solo el código del registro queda oscuro", que dejó de tener sentido).
- `lintDebug`: 0 errores, 45 advertencias (ninguna supresión nueva).
- `compileDebugAndroidTestKotlin` compila (incluye `AppearanceAndLanguageScreensTest`). Las pruebas instrumentadas **no se ejecutaron** (no hay emulador).
- Pruebas de Compose: las ya existentes siguen compilando; no se agregó ninguna nueva (caras para esta etapa).

## 8. Riesgos para la etapa 7 (idioma)

- **R-01 con "Automático":** cambiar el tema del *sistema* recrea la actividad (no hay `configChanges` en el manifiesto). Cambiar el modo *dentro de la app* no recrea
  nada: es un valor nuevo en el tema raíz. Con AppCompat y cambio de idioma la actividad también se recreará: la prueba de R-01 de la etapa 7 debe cubrirlo.
- **AppCompat:** `MainActivity` sigue siendo `FragmentActivity`; cambiar a `AppCompatActivity` y a un tema AppCompat tocará `themes.xml` (hoy el padre es `android:Theme.Material.Light.NoActionBar`
  y el fondo de ventana lo fija el código). Hay que mantener `applyThemeChrome` en el primer frame.
- **Textos de los diálogos del sistema** (biometría, selector de fecha) dependen del idioma del sistema, no del de la app.

## 9. Lista de aprobaciones que debe dar el grupo

☐ Los siete tokens nuevos y sus valores claros (sección 2).
☐ Chapa y tarjetas de lección sin borde en claro (4.2).
☐ Camino punteado de la Ruta teñido en claro (4.4).
☐ El splash oscuro en modo claro (6) o abrir una tarea para un tema de arranque por modo.
☐ Las ilustraciones con transparencia sobre fondo claro (6): cuáles hay que rehacer.
☐ La lista oscura de contraste (5) sigue sin corregirse en este módulo.
☐ La lista visual de `etapa-5-verificacion-visual.md` pasada en emulador, oscuro y claro, fuente normal y grande, con rotación.

## 10. Arreglo de contraste posterior (probado el claro en el emulador)

- **Dorado como texto:** token `goldText` (oscuro `#E3B54F`, idéntico; claro **PROPUESTA** `#7A5612`: 5.31 sobre `#AEF5D5`, 6.03 sobre `#F7F4EC`, 4.70 sobre `#D9D9D9`).
  `selection` (`#926A1B`) queda para rellenos y bordes. Token `onGold` (texto sobre rellenos dorados: oscuro `#162523` idéntico; claro blanco, 5.2 sobre `#926A1B`) para las píldoras
  doradas, la opción elegida de la arena, la píldora de turno, la insignia de cuenta y el ✓ de la pareja.
- **Texto sobre imágenes de perfil:** `onBackdrop` (blanco en ambos modos) y `backdropAccent` (`#E3B54F` en ambos). Solo se aplican cuando el perfil tiene imagen
  (`LocalProfileBackdrop`): con "sin fondo" sigue el color del tema. No hizo falta `onBackdropMuted` ni `backdropDivider`: no hay texto secundario ni divisores sobre las imágenes.
- **Imágenes** (con la capa `scrimSoft` al 45 %, contraste del blanco): fondo 1 peor píxel 3.58 (0.2 % bajo 4.5, zona x 104–242, y 298–490 de 412 × 844); fondo 2 peor 4.61;
  fondo 3 peor 13.3; fondo 4 peor 8.26; fondo 5 peor 3.42 (1.9 % bajo 4.5, disperso). Todas las medianas superan 9.9. **PROPUESTA:** capa de ≈ 53 % (`0x87000000`) solo para el fondo 5
  llevaría su peor píxel a 4.5; no se aplicó.
- Los seis pares oscuros de dorado-como-texto (4.08, 2.99, 1.35, 1.33) se documentan en `ContrastTest.knownDarkShortfalls`; no se corrigen.
