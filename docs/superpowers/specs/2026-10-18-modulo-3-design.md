# Módulo 3: Ruta de aprendizaje, lecciones, laboratorios y examen final: spec de diseño

Estado: **v3, con las decisiones del grupo del 2026-10-18 (secciones 13 y 14). No queda ninguna pregunta abierta.** Ver la sección "Estado" más abajo.
Fecha: 2026-10-18. Rama: `modulo-3`. Figma: fileKey `wd3fQTzF5NqsfGfdR4I1lb` (una sola página, `0:1`).

Convención de este documento: cada afirmación lleva su **FUENTE** (archivo del repo, nodo de Figma `nombre (ID)` o "decisión del grupo P#"). Lo que no tiene fuente va marcado **SUPUESTO** o **PROPUESTA** y no está decidido.

## Estado

- **No queda ninguna pregunta abierta.** Las 15 preguntas de la v1 están resueltas en la sección 13 y las 8 propuestas de la v2 (Q1 a Q8) están confirmadas por el grupo en la sección 14.
- **Etapa 0 hecha:** `supabase/migrations/0007_progreso_aprendizaje.sql` ya está aplicada en Supabase y verificada (la ejecutó y verificó el usuario a mano). El archivo del repo conserva el encabezado "BORRADOR sin aplicar" tal como se aplicó y **no se edita más**: cualquier cambio va en `0008`.
- **Etapa 1 hecha (datos y contenido), sin pantallas ni UI:**
  - **Contenido** (JSON en `app/src/main/assets/`, solo lo de Figma, con el nodo de origen en el campo `source` de cada elemento): `learning_lessons.json` (6 lecciones, 2294:112 a 2294:202), `learning_labs.json` (6 laboratorios con 3 objetivos, comando y salida exacta, y los textos de sus informes) y `learning_exam.json` (6 preguntas, 2491:243 a 2491:328, con las respuestas verificadas por el grupo).
  - **Datos** (`data/learning/`): `LearningTopic`, `Lesson`, `Lab`, `ExamQuestion`; `LearningContentRepository` (+ `LocalLearningContentRepository`, que lee los JSON); `LearningProgressRepository`; `SupabaseLearningProgressRepository` en `data/supabase`; fakes y pruebas en `src/test`.
  - **Lógica pura:** `LabTerminal` (terminal simulada), `gradeExam` (corrección del examen) y `deriveLearningProgress` (islas, examen, contador y "siguiente lección pendiente").
  - **Registro idempotente:** lección vista y lab completado se escriben con un `upsert` que **ignora duplicados** (`on_conflict=user_id,topic` o `user_id,lab` y `Prefer: resolution=ignore-duplicates`), que PostgREST ejecuta como `INSERT ... ON CONFLICT DO NOTHING`: solo necesita el permiso `INSERT` de `0007` (no hay `UPDATE`) y repetir no es un error. El intento de examen es un `INSERT` simple. La fila del usuario sale siempre de la sesión.
  - **Validación:** `./gradlew assembleDebug testDebugUnitTest lintDebug` en verde: 677 pruebas, 76 nuevas, lint con 0 errores. No se tocó nada de módulos 1 y 2 salvo `AppContainer` (dos repositorios nuevos).
- **Etapa 2 hecha (contenedor, barra inferior e Inicio):**
  - **Tarea 0:** el texto del lab de correo quedó "esta es una imitación" (F15, E1-9 resuelto).
  - **Navegación:** `Home(tab: HomeTab)` reemplaza a `MainMenu` (que se eliminó junto con `MainMenuScreen`). `PostAuthGate` lleva a `Home(Inicio)`; "Continuar" del quiz lleva a `Home(Ruta)` (P4). El Menú de usuario cierra sesión con `popUpTo<Home>`.
  - **`ui/home/`:** `HomeScreen` (Scaffold + `SnackbarHost`), `HomeViewModel` (pestaña en `SavedStateHandle`, avisos por `Channel`, cerrar sesión, nivel, progreso), `NightWatchBottomBar` (5 pestañas: Inicio, Ruta y Labs son pestañas; Social atenuada con "Próximamente"; Perfil abre `UserMenu` y no se selecciona) e Inicio según Figma 49:15. Ruta y Labs son provisionales ("en construcción").
  - **"Lección diaria":** lee `learningProgressRepository` y `deriveLearningProgress`. Muestra "Siguiente: <lección>" o "No tienes lecciones pendientes"; carga y error de red (con "Reintentar") solo afectan esa línea. **La tarjeta aún no es clicable**: abrir la lección se conecta en la Etapa 3 (y entonces el toque sin lección pendiente mostrará el Snackbar de Q3).
  - **Textos:** `res/values/strings_aprendizaje.xml` (prefijo `learning_`), con los propuestos marcados en comentarios.
  - **Módulos 1 y 2 tocados:** `NightWatchNavHost`, `NightWatchDestinations` y `NightWatchNavHostStartDestinationTest` (pasó de `MainMenu` a `Home`). `PostAuthGate*` y `PlacementResult*` no cambiaron (el destino se decide en el `NavHost`; sus eventos conservan el nombre `NavigateToMainMenu`).
  - **Validación:** `assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin` en verde: 705 pruebas, 28 nuevas (`HomeViewModelTest`), lint con 0 errores. Se agregó `NightWatchBottomBarTest` (instrumentada, solo compilada: no se ejecutó).
- **Etapa 3 hecha (Ruta y lecciones):**
  - **Decisión nueva Q11 (sección 14):** la lección se marca como **vista al abrirse**, no al terminarla ni al tocar "Ir al laboratorio".
  - **Ruta** (`ui/path/`): `LearningPathViewModel` y `LearningPathTab` (Figma 2266:72). Encabezado "🧭 Tu nivel: …" y "N de 7 islas completadas" con barra; 6 islas en orden más la "Prueba Final", con estado completada, disponible o bloqueada derivado de `deriveLearningProgress`. Tocar una isla abierta o completada abre `Lesson(topic)`; una bloqueada muestra "Completa la isla anterior"; la Prueba Final bloqueada muestra "Completa las 6 islas" y la desbloqueada muestra **"Próximamente" de forma provisional hasta la Etapa 5**. Si el progreso no se lee, el mapa se muestra con la isla 1 abierta y un aviso con "Reintentar".
  - **Lección** (`ui/lesson/`): `Lesson(topic)` a pantalla completa y sin barra, con título, "MÓDULO N de 6", las 3 tarjetas del JSON, "X" y "Ir al laboratorio →". Guarda con `markLessonViewed` (idempotente) una sola vez por apertura; si falla, se puede leer igual y sale un aviso.
  - **"Ir al laboratorio →" es provisional:** lleva a `Home(Labs)` (que aún muestra "en construcción") hasta la Etapa 4.
  - **Inicio:** "Lección diaria" es clicable: abre la lección pendiente, o muestra "No tienes lecciones pendientes" (Q3). Sin lectura o con error, el toque no hace nada.
  - **Módulos 1 y 2:** solo `NightWatchNavHost` y `NightWatchDestinations` (ruta `Lesson` y su pantalla); el resto es código del módulo 3.
  - **Validación:** `assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin` en verde: 751 pruebas, 46 nuevas (26 de la Ruta, 16 de la lección y 4 de Inicio), lint con 0 errores. No se tocaron pruebas instrumentadas.
- **Etapa 4A hecha (lista de Labs y terminal, sin informes):**
  - **Labs** (`ui/labs/`): `LabsViewModel` y `LabsTab` (Figma 2271:84). Seis tarjetas (nombre, Fácil/Medio/Difícil y "✅ Completado", "Comenzar →" o atenuada al 60 %) y la tarjeta "Examen Final" ("Prueba"). Un lab está disponible si su isla está desbloqueada, sin exigir la lección (Q4). Bloqueado: Snackbar "Completa la isla anterior". Examen bloqueado: "Completa las 6 islas"; desbloqueado: **"Próximamente" de forma provisional hasta la Etapa 5**. Carga y error de red con "Reintentar", como la Ruta.
  - **Terminal** (`ui/lab/`): `Lab(topic)` a pantalla completa y sin barra (Figma 2374:273 y 2374:303). Tres objetivos con su comando (tocar lo copia al campo, no lo envía), campo, "Enviar", salida y ✓ en los cumplidos. Usa `LabTerminal` sin cambios. El intento (comandos enviados y texto del campo) vive en `SavedStateHandle` y la terminal se reconstruye de ahí: sobrevive a la rotación. "X" cierra sin guardar y sin diálogo.
  - **Provisional para la 4B:** "Finalizar laboratorio" está siempre habilitado pero solo muestra el Snackbar "Próximamente"; **la 4B lo conecta al informe** (y a `markLabCompleted`). Hasta entonces ningún lab se marca como completado desde la app.
  - **Lección:** "Ir al laboratorio →" abre `Lab(tema de la lección)` (antes iba a `Home(Labs)`); el evento pasó de `OpenLabs` a `OpenLab(topic)`.
  - **Verificación contra Figma:** las otras cinco terminales (2374:357/387, 2383:141/173, 2383:206/238, 2299:118/148, 2383:271/303) tienen la misma estructura que la de Contraseñas y sus títulos, objetivos y comandos coinciden con `learning_labs.json`.
  - **Módulos 1 y 2:** ninguno tocado. Sí `HomeTopBar` y `HomeScreen` (módulo 3), `NightWatchNavHost` y `NightWatchDestinations` (ruta `Lab`).
  - **Validación:** `assembleDebug testDebugUnitTest lintDebug` en verde: 791 pruebas, 40 nuevas (21 de Labs, 18 del Lab y 2 más en la lección, que sustituyen a 1), lint con 0 errores. No se tocaron pruebas instrumentadas.
- **Etapa 4B hecha (informes, precisión, guardado y progreso):**
  - **Informe** (`ui/labreport/`): `LabReport(topic, completedObjectives)` a pantalla completa y sin barra (Figma 2273:131, 2404:243, 2374:336, 2400:144; los otros cuatro tienen la misma estructura y sus textos coinciden con `learning_labs.json`). "Finalizar laboratorio" (siempre habilitado) abre el informe. 3 de 3: éxito con el título y la frase del lab; menos: **"Laboratorio no completado"** con la frase de fallo (P6) y el trofeo roto de Figma (`img_lab_report_failed`, reducido de 4096 px a 454 × 300 px). Cada objetivo lleva ✅ o ❌ (Q1).
  - **Precisión:** `round(100 × objetivos / 3)` con `LabTerminal.precisionPercent` (0, 33, 67, 100), calculada en la app y no guardada. En Contraseñas la caja muestra el tiempo de la contraseña más fuerte probada (`strongestEstimatedTime`, de las salidas de Figma) o "—" si no se probó ninguna (Q2); nunca se produce el "3 Horas" de Figma.
  - **Guardado:** solo con 3 de 3, `markLabCompleted` (idempotente, la fila sale de la sesión; sin cambios en `0007`, en el repositorio ni en el motor). Con menos, nada se escribe. Si falla (sin conexión, permisos u otro), el informe se muestra igual con aviso y **"Reintentar guardado"**; el lab no queda completado hasta guardarse y la pantalla solo recibe el tipo de fallo, nunca la excepción. El guardado es `NonCancellable`; una segunda llamada mientras se guarda no hace nada (doble toque) y repetir un lab ya completado no da error ni duplica.
  - **Botones (Figma):** "Reintentar" abre el mismo lab desde cero (saca el lab y el informe de la pila) y "Volver a la ruta" abre `Home(Ruta)`. Esperan a que termine el guardado (atenuados), para que el mapa que se abre ya lea el lab completado.
  - **Progreso al día:** sin mecanismo nuevo. Ruta, Labs e Inicio ya releían el progreso en `ON_RESUME` (`refresh()` y `refreshProgress()`); "Volver a la ruta" crea `Home` de nuevo y cada pestaña lee el progreso ya guardado. Si se vuelve con "atrás" al terminal y se cierra con la "X", `Home` se reanuda y relee igual.
  - **Módulos 1 y 2:** ninguno tocado. Sí `NightWatchNavHost` y `NightWatchDestinations` (rutas `LabReport` y el nuevo `onOpenReport` del lab) y `LabScreen`/`LabViewModel` (4A), que perdieron el Snackbar provisional.
  - **Validación:** `assembleDebug testDebugUnitTest lintDebug` en verde: 829 pruebas, 38 nuevas (29 del informe y el guardado, 7 de integración con Ruta, Labs e Inicio y 2 más del lab), lint con 0 errores. No se tocaron pruebas instrumentadas.
- **Sigue la Etapa 5** (examen). Lo que falta verificar a mano en el teléfono: E1-11 (el registro idempotente contra el servidor real, ahora con `lab_progress`), E3-8, E4-9 y E4B-8.
- Lo que sigue marcado como **SUPUESTO** en el texto (por ejemplo, "Tu nivel: Sin evaluar" si no se puede leer el resultado del quiz, o que los objetivos del lab se cumplan en cualquier orden) y los **textos propuestos de la pantalla de aprobado y de la introducción del examen (F7)** son detalles que no estaban en la lista de confirmaciones del grupo; no bloquean y se anotan en el informe de cierre (Etapa 6) para que el grupo los vea.

**SUPUESTOS y PENDIENTES que dejó la Etapa 1** (no bloquean la Etapa 2):

| # | Tipo | Detalle |
|---|---|---|
| E1-1 | SUPUESTO | **Normalización de la terminal:** se ignoran los espacios de los extremos y se juntan los espacios repetidos; la **primera palabra** (el nombre del comando) ignora mayúsculas y minúsculas, porque los teclados de teléfono capitalizan la primera letra; los **argumentos** distinguen mayúsculas (en el lab de contraseñas `Nightwatch2024` y `nightwatch2024` son contraseñas distintas). Figma no dice nada |
| E1-2 | SUPUESTO | Los 3 objetivos de un lab se pueden cumplir **en cualquier orden** |
| E1-3 | SUPUESTO | Repetir un comando ya cumplido **vuelve a mostrar su salida** y no suma al conteo |
| E1-4 | SUPUESTO (texto no de Figma) | Comando desconocido: **"Comando no reconocido. Toca un objetivo para usar su comando."** (también se agrega a la terminal) |
| E1-5 | SUPUESTO | El mapa de Figma muestra 🔒 (bloqueada) en vez del ícono de las islas "Islas de Redes Seguras" y "Fortaleza del Firewall"; se usan los de sus lecciones (📡 y 🧱) |
| E1-6 | SUPUESTO | `estimatedTime` del lab de contraseñas ("menos de 1 segundo", "4 horas", "300 años") sale del texto de cada salida; en el informe de éxito Figma lo escribe "300 Años" y la capitalización la decide la pantalla (Etapa 4) |
| E1-7 | SUPUESTO | Solo `cat access.log` (Redes) tiene una salida de 2 líneas; el resto es de una línea. La separación con línea en blanco entre comandos aparece en la terminal de Redes pero no en las otras (2299:148 frente a las demás): se trata como presentación y no se guarda |
| E1-8 | SUPUESTO | El rótulo "MÓDULO N de 6" de las lecciones se calcula por el orden del tema y no se guarda |
| E1-9 | RESUELTO (F15) | El texto de Figma de la salida del lab de correo decía "este es una imitación" (2383:173); el grupo pidió corregirlo y `learning_labs.json` dice ahora **"esta es una imitación"** |
| E1-10 | PENDIENTE | Los textos de informe de **fallo** ("No confirmaste que…") son los de las variantes "(ERROR)" de Figma; el título común es "Laboratorio no completado" (P6) y se pondrá en la pantalla (Etapa 4) |
| E1-11 | NO VERIFICADO | El `upsert` que ignora duplicados no se probó contra un servidor real desde aquí (no hay red ni SQL; F-03 de la revisión). Se comprueba a mano en el teléfono en la Etapa 3: ver una lección dos veces no debe dar error ni duplicar filas |
| E2-1 | SUPUESTO | Social se dibuja a 55 % × 60 % (≈33 %): Figma no tiene pestaña desactivada; 60 % es la opacidad de los labs bloqueados (2271:84) y 55 % la de una pestaña inactiva |
| E2-2 | SUPUESTO | La barra tiene 54 dp de contenido + 14 dp arriba + la barra de navegación del sistema; Figma mide 92 dp en un marco de 917 dp que incluye esa zona |
| E2-3 | SUPUESTO | Las tarjetas "Concepto nuevo" y CiberMynd se atenúan al 60 % (solo la tarjeta, no el rótulo) |
| E2-4 | SUPUESTO (texto no de Figma) | Línea "Nivel actual: BAJO/MEDIO/ALTO/Sin evaluar" dentro de la chapa (F16); misma regla que Mi Perfil (si la lectura falla o tarda más de 5 s: "Sin evaluar"; mientras tanto "…") |
| E2-5 | SUPUESTO (texto no de Figma) | Línea bajo "Lección diaria": "Siguiente: <título de la lección>", "No tienes lecciones pendientes" (Q3), "Cargando tu progreso…", "Sin conexión. No pudimos cargar tu progreso.", "No pudimos cargar tu progreso." y "Reintentar" |
| E2-6 | SUPUESTO | La descripción de accesibilidad del ícono de salida es "Cerrar sesión"; el aviso del toque es el Snackbar "Próximamente" (Q6, Q10) |
| E2-7 | SUPUESTO | Colores de la chapa (#94A6A7) y del borde y asa de la hoja (#204143) tomados de los nodos 108:16, 113:145 y 113:150, no están en el tema; quedan privados en `HomeScreen`. Oro (#E3B54F) y texto sobre claro (#162523) ya existían |
| E2-8 | DETALLE | Las ilustraciones de Inicio y el ícono de salida son los PNG de Figma (72:31, 72:30, 72:28 y 72:13), en `drawable-nodpi/img_home_*` |
| E2-9 | NO VERIFICADO | Nada se vio en un teléfono (rotación, TalkBack, Snackbar encima de la barra, lectura del progreso real). Se prueba a mano con la lista de la entrega |
| E3-1 | SUPUESTO | Las etiquetas de las islas se parten por ancho (98 dp) para dar las dos líneas de Figma ("Isla de las / Contraseñas", "Islas de Redes / Seguras"); no se guarda el salto. Hay que mirar que las seis salgan igual que en Figma |
| E3-2 | SUPUESTO | Figma solo dibuja una isla abierta con el aro dorado y el halo punteado (2266:109), una completada dorada (2266:98) y las bloqueadas grises con 🔒 (2266:114). La Prueba Final solo se dibuja bloqueada y con 📝; desbloqueada usa el aspecto de "abierta" y con un intento aprobado, el de "completada" |
| E3-3 | SUPUESTO | Una isla bloqueada muestra 🔒 y, al desbloquearse, su emoji de la lección (cierra E1-5: ya no se usan 📡 y 🧱 para las bloqueadas) |
| E3-4 | SUPUESTO | El mapa de 412 × 1440 dp se escala al ancho del teléfono (toques y texto incluidos); la línea punteada es el `RoutePath` de Figma convertido a `img_route_path.xml` |
| E3-5 | SUPUESTO | Mientras se hace la primera lectura el mapa no se dibuja (solo un indicador) y los toques se ignoran, para no mostrar candados falsos |
| E3-6 | SUPUESTO | Si falla una lectura posterior, el mapa conserva el último progreso conocido y muestra el aviso; si falla la primera, muestra la isla 1 abierta y las demás bloqueadas |
| E3-7 | SUPUESTO (textos no de Figma) | Avisos de error de lectura, "Reintentar", "Cargar la lección…", "No pudimos cargar la lección." y los dos avisos de guardado fallido ("Sin conexión: no se guardó tu avance, pero puedes seguir leyendo." / "No se pudo guardar tu avance, pero puedes seguir leyendo.") |
| E3-8 | NO VERIFICADO | Nada se vio en un teléfono (mapa, desplazamiento, etiquetas, TalkBack, rotación) ni se probó contra el servidor real el registro de la lección vista |
| E3-9 | DETALLE | La escritura de "vista" no se cancela si la persona sale de la lección enseguida (`NonCancellable`); es una sola petición corta |
| E3-10 | LÍMITE | Abrir una lección bloqueada no se impide en la base de datos ni en el ViewModel de la lección (solo la Ruta y la tarjeta de Inicio lo evitan): sin enlaces profundos no hay otra puerta, y es la misma limitación R6 |
| E4-1 | SUPUESTO | Figma pone el título "Laboratorios" a 25 dp (una línea) y los de Ruta e Inicio a 14 dp (dos líneas): `HomeTopBar` ganó el parámetro `titleTop` |
| E4-2 | SUPUESTO | La tarjeta de Figma es de 118 dp fijos; aquí es 118 dp como mínimo y crece si el texto lo pide. Toda la tarjeta es el toque (en Figma solo el botón "Comenzar →" y las completadas). La bloqueada conserva su "Comenzar →" atenuado como en 2271:113 |
| E4-3 | SUPUESTO | "Examen Final" desbloqueado dibuja "Comenzar →" (Figma solo lo dibuja bloqueado); con un intento aprobado dibuja "✅ Completado" |
| E4-4 | SUPUESTO | El tema de cada tarjeta es "completado" si su lab está guardado, aunque la isla no esté completa; el siguiente lab sigue bloqueado hasta completar la isla (lección y lab) |
| E4-5 | SUPUESTO (texto no de Figma) | Avisos de error de lectura de Labs, descripciones de accesibilidad de tarjetas y objetivos ("cumplido"/"pendiente"), "Cerrar laboratorio", "Cargando el laboratorio…", "No pudimos cargar el laboratorio." y la descripción "Comando" del campo |
| E4-6 | SUPUESTO | La fuente de la terminal es la monoespaciada del sistema: Figma usa IBM Plex Mono, que no está en el proyecto |
| E4-7 | DIFERENCIA CON FIGMA | El rótulo "OBJETIVOS · TOCA PARA USAR EL COMANDO" queda oculto detrás de la primera tarjeta en Figma (2374:278 en y=122, tarjeta en y=104); aquí se dibuja encima de las tarjetas, así que estas bajan unos 30 dp |
| E4-8 | SUPUESTO | El campo conserva su texto tras "Enviar" (así lo dibuja el cuadro "Resuelto" 2374:329); teclado sin mayúsculas ni autocorrección y acción "Enviar" del teclado. "Finalizar laboratorio" usa texto oscuro en vez del blanco sobre gris claro de Figma, que casi no se lee |
| E4-9 | NO VERIFICADO | Nada se vio en un teléfono (teclado sobre el campo y los botones, rotación, desplazamiento de la salida, TalkBack, Snackbar encima de la barra) |
| E4-10 | SUPUESTO | La lección se reemplaza por el lab al tocar "Ir al laboratorio →" (la "X" del lab vuelve a Home, no a la lección); desde Labs el lab se abre encima de Home y la "X" vuelve a Labs |
| E4B-1 | SUPUESTO | Figma usa tamaños distintos en el título, la frase y la lista: Misión 22/14/13 sp y Contraseñas 20/13/14 sp. Se usan los de Contraseñas (20/13/14) en todos |
| E4B-2 | SUPUESTO | El tiempo de Contraseñas se escribe con mayúscula en cada palabra salvo "de", como Figma ("300 Años", "3 Horas"): "Menos de 1 Segundo", "4 Horas", "300 Años" (cierra E1-6) |
| E4B-3 | SUPUESTO | "Volver a la ruta" abre `Home(Ruta)` (así lo dice Figma; el encargo hablaba de Labs). "Reintentar" repite el lab desde cero: Figma no dice qué hace |
| E4B-4 | SUPUESTO | El texto de "Volver a la ruta" va oscuro: Figma lo dibuja blanco sobre gris claro en algunos frames y oscuro en otros (2273:143) |
| E4B-5 | DIFERENCIA CON FIGMA | Las variantes ERROR repiten el título de éxito ("¡Misión completada!"); por decisión del grupo (P6) el fallo dice "Laboratorio no completado". El trofeo roto es la imagen de Figma 2404:259 |
| E4B-6 | SUPUESTO (texto no de Figma) | "Guardando tu avance…", "Avance guardado", "Sin conexión: el laboratorio aún no se guardó.", "No se pudo guardar el laboratorio.", "Reintentar guardado", "Cargando el informe…", "No pudimos cargar el informe." y "Trofeo roto" (accesibilidad) |
| E4B-7 | SUPUESTO | Los botones del informe se atenúan y no responden mientras se guarda (unos segundos como mucho: el guardado tiene el tiempo límite de las demás llamadas) |
| E4B-8 | NO VERIFICADO | Nada se vio en un teléfono ni se probó contra el servidor real la escritura en `lab_progress`, la repetición sin duplicados ni el reintento sin conexión (F-03). Se comprueba a mano con los pasos de la entrega |
| E4B-9 | LÍMITE | Si el guardado falla y la persona sale con "Volver a la ruta", el lab queda sin completar y tendrá que repetirlo: el intento no se conserva en el teléfono |
| E1-12 | DETALLE | Las 5 respuestas P2 a P6 del examen, además de verificadas por el grupo, coinciden con las líneas "Correcta:" de las pantallas de 1 error (2498:524 a 2498:572) |

## 1. Alcance

**Decisión del equipo:**

- **Dentro:** Ruta de Aprendizaje (mapa), Lecciones, Laboratorios (lista, terminal e informes), Examen Final y la barra inferior de navegación (Inicio, Ruta, Labs, Social, Perfil), que **reemplaza al Main Menu provisional**.
- **Fuera:** todo el contenido de Social (17 pantallas `2440:163` a `2451:209`). Es el **módulo 4**: ver `2026-10-18-modulo-4-social-analisis.md` (solo análisis, sin SQL).
- **Excepciones visuales:** la pestaña "Social" de la barra, y las tarjetas "Concepto nuevo" y "Prepárate con CiberMynd" de Inicio, **aparecen atenuadas y solo muestran el aviso "Próximamente"** (sección 7; decisión P7).
- **Niveles:** se mantiene BAJO/MEDIO/ALTO. "Aprendiz" (mapa) e "Intermedio" (Perfil, Figma) **no** se implementan.
- **Temas: son 6 y separados.** Cada tema tiene su isla, su lección, su laboratorio y su pregunta del examen. Nombres exactos de Figma:

| # | Código del tema (`topic`/`lab`) | Isla del mapa (2266:72) | Lección y su título (2294:*) | Etiqueta en el examen (2491:*) | Laboratorio: tarjeta en Labs (2271:84) | Título dentro de la terminal |
|---|---|---|---|---|---|---|
| 1 | `CONTRASENAS` | Isla de las Contraseñas | Contraseñas y Autenticación (2294:112) | 🔑 Contraseñas y Autenticación (2491:243) | Fuerza de Contraseñas | Fuerza de Contraseñas (2374:273) |
| 2 | `PHISHING` | Bahía del Phishing | Phishing (2294:130) | 🎣 Phishing (2491:260) | Detecta el Correo Falso | Correo Sospechoso (2383:141) |
| 3 | `MALWARE` | Arrecife del Malware | Malware (2294:148) | 🦠 Malware (2491:277) | Analiza el Archivo Sospechoso | Archivo Sospechoso (2374:357) |
| 4 | `INGENIERIA_SOCIAL` | Cueva del Engaño Social | Ingeniería Social (2294:166) | 🎭 Ingeniería Social (2491:294) | Simulación de Llamada | Simulación de Llamada (2383:206) |
| 5 | `REDES_SEGURAS` | Islas de Redes Seguras | Islas de Redes Seguras (2294:184) | 🔒 Redes Seguras (2491:311) | Seguridad en Redes | Seguridad en Redes (2299:118) |
| 6 | `FIREWALL` | Fortaleza del Firewall | Fortaleza del Firewall (2294:202) | 🧱 Firewall (2491:328) | Configura tu Firewall | Configura tu Firewall (2383:271) |
| 7 | *(examen)* | Prueba Final | Examen Final (2486:249) | n/a | tarjeta "Examen Final", etiqueta "Prueba" | n/a |

- El **orden de Figma** (lecciones "MÓDULO 1 a 6 de 6", islas y preguntas del examen) es el de la tabla: Contraseñas, **Phishing, Malware**, Ingeniería Social, Redes Seguras, Firewall. La lista del grupo enumeraba Malware antes que Phishing; se sigue Figma, confirmado en la sección 14 (Q7; ver sección 4, C12).
- Los títulos de las tarjetas de Labs y los de dentro de la terminal **no coinciden** en tres labs (p. ej. "Detecta el Correo Falso" frente a "Correo Sospechoso"). Se usa cada uno donde Figma lo usa (C13).
- **Sin cambios** en módulos 1 y 2, salvo lo mínimo para conectar (`CLAUDE.md` regla 7.6): el Main Menu y las rutas de salida del quiz.
- **Cero riesgos sobre datos anteriores (P5):** `0007` solo crea tablas nuevas; no referencia ni modifica `placement` ni ninguna tabla de módulos anteriores.

## 2. Fuentes consultadas

| Fuente | Qué aporta |
|---|---|
| `CLAUDE.md` | Estado, convenciones, reglas de git y de Supabase |
| `docs/superpowers/specs/2026-09-23-auth-onboarding-design.md` (líneas 15-19, 56-57, 71) | Los 4 subsistemas del Figma; dejan fuera quiz y Core learning |
| `docs/superpowers/specs/2026-09-24-placement-quiz-design.md` (líneas 12-15, 57-69) | Deja fuera "the real Core Learning map, navigable lessons, labs, and final exam"; el plan de estudio es una lista estática |
| `docs/superpowers/specs/2026-10-09-profile-settings-design.md` (línea 16, 29, 214, 217) | Deja fuera Core Learning y Social; racha 15 e insignias 3 de 9 fijas; switches de notificaciones |
| `docs/superpowers/reports/modulo-2-diagnostico.md` (líneas 97-98, 197-200) | "Logros, laboratorios y novedades solo guardan el switch (necesitan backend)"; la racha es fija |
| `README.md` (líneas 55-56) | Logros e insignias solo visuales |
| `app/src/main/java/.../data/profile/FixedProfileData.kt` | Racha fija |
| `app/src/main/java/.../data/profile/ProfileFormatters.kt`, `ui/quiz/PlacementResultScreen.kt:84` | Formato del nivel: "Tu nivel: ${level}" con BAJO/MEDIO/ALTO, y "Sin evaluar" |
| `app/src/main/java/.../ui/mainmenu/MainMenuScreen.kt`, `ui/navigation/NightWatchNavHost.kt`, `app/build.gradle.kts` | Main Menu actual, salidas del quiz, `versionCode 3` / `versionName 2.0.0` |
| `supabase/migrations/0001` a `0006` | Convenciones de SQL |
| Figma: mapa 2266:72, Labs 2271:84, lecciones 2294:*, `LAB - *`, `EXAMEN *`, `MAIN MENU` 49:15, `SOCIAL - *` | Pantallas y textos |
| Decisiones del grupo del 2026-10-18 | Sección 13 |

## 3. Lo ya definido, lo nuevo y lo que falta

**Ya definido (se respeta):**
- Core learning = "map, lessons, labs, final exam", subsistema aparte de Social. FUENTE: `2026-09-23-auth-onboarding-design.md:15-19`.
- El quiz entrega BAJO/MEDIO/ALTO y un plan de estudio estático. FUENTE: `2026-09-24-placement-quiz-design.md:57-61`.
- Racha 15 días e insignias 3 de 9 fijas. FUENTE: `profile-settings-design.md:27`; reafirmado por P9.
- Notificaciones de "Nuevos laboratorios" solo guardan el switch. FUENTE: `modulo-2-diagnostico.md:98`.
- Reglas de Supabase (RLS del dueño, GRANT explícito, mínimo privilegio, migraciones nuevas, SQL ejecutado por el usuario). FUENTE: `CLAUDE.md` sección 4.

**Nuevo (de Figma y de las decisiones del grupo):** mapa de 6 islas + Prueba Final con desbloqueo en orden, 6 lecciones, 6 laboratorios con terminal simulada, examen de 6 preguntas sin límites, barra inferior, avisos "Próximamente".

**Sin definir:** nada pendiente (secciones 13 y 14). Racha real e insignias pasan al módulo 4 (P9).

## 4. Contradicciones e inconsistencias (estado actual)

| # | Hallazgo | Fuentes | Estado |
|---|---|---|---|
| C1 | La brief decía "ruta + labs = social"; docs y Figma los separan | `2026-09-23-auth-onboarding-design.md:15-19` | Resuelta: Social es módulo 4 |
| C2 | Nivel: "Aprendiz" (mapa), "Intermedio" (Perfil Figma) y BAJO/MEDIO/ALTO (código) | Figma 2266:72, 2068:51, 2017:126 | Resuelta: BAJO/MEDIO/ALTO; formato "Tu nivel: BAJO" (P2) |
| C3 | "3 de 7 islas" frente a 2 labs completados en la lista | Figma 2266:72, 2271:84 | Resuelta: una isla = lección vista **y** lab completado (P3). Los datos de Figma son un ejemplo |
| C4 | El plan de estudio de Figma lista otros 6 temas (con "Buenas Prácticas" y "Privacidad de Datos") | Figma 2017:160 | Resuelta: los 6 temas de las lecciones; el plan de estudio ya implementado no cambia |
| C5 | Informes "(ERROR)" con título "completado" y 0% | Figma 2404:243, 2370:139, 2400:144 | Resuelta: título "Laboratorio no completado" (P6); es diferencia deliberada (F8) |
| C6 | El informe de contraseñas muestra un tiempo ("300 Años" / "3 Horas") y los demás un porcentaje; además "3 Horas" no coincide con ninguna salida de la terminal ("menos de 1 segundo", "4 horas", "300 años", Figma 2374:303) | Figma 2374:336, 2400:144, 2374:303 | Resuelta: se muestra el tiempo de las salidas reales de la terminal, y "—" si no hay ninguna (sección 14, Q2) |
| C7 | "Examen Final" aparece en Labs y como isla "Prueba Final" | Figma 2271:84, 2266:72 | Resuelta: se abre desde las dos, misma pantalla (P1) |
| C8 | "MÓDULO N de 6" usa "módulo" para un tema | Figma 2294:112 a 2294:202 | Se conserva el texto de Figma (solo lenguaje; no es una pregunta abierta) |
| C9 | Main Menu de Figma (ícono de salida, 3 tarjetas, sin "Mi cuenta") frente al del código (2 botones) | Figma 49:15; `MainMenuScreen.kt` | Resuelta: sección 7 |
| C10 | "CiberMynd" no aparece en ningún documento | Figma 49:15 | Resuelta: tarjeta atenuada con "Próximamente" (P7) |
| C11 | `modulo-3` ya existe en remoto y va 2 commits por delante de `b339374`; existe el worktree `worktree-auth-onboarding` | `git branch -vv` | Solo informativo |
| C12 | La lista del grupo da el orden "Contraseñas, Malware, Phishing…"; Figma ordena Phishing (2.º) y Malware (3.º) en lecciones, islas y examen | Figma 2294:130, 2294:148, 2266:72, 2491:260, 2491:277 | Resuelta: **se sigue el orden de Figma** (Q7, sección 14) |
| C13 | Los títulos de las tarjetas de Labs ("Detecta el Correo Falso", "Analiza el Archivo Sospechoso") difieren de los de la pantalla del lab ("Correo Sospechoso", "Archivo Sospechoso") | Figma 2271:84, 2383:141, 2374:357 | Se respeta cada uno donde aparece |
| C14 | El botón "Finalizar laboratorio" se ve **atenuado** incluso con los 3 objetivos cumplidos | Figma 2374:303 | La decisión P6 (siempre habilitado) difiere: diferencia deliberada F9 |
| C15 | La promesa "subes de nivel y desbloqueas contenido nuevo" no está solo en 2491:357: también en la introducción 2486:249 ("¿Qué gano?") y en las 6 variantes de 1 error (2498:512 a 2498:572) | Figma 2486:249, 2498:512…572 | La decisión P5 obliga a reemplazarla en **esos 8 frames**, no solo en 2491:357 (F7) |

## 5. Pantallas del módulo 3

"Funcional" = lee o escribe datos reales (Supabase) o aplica reglas reales. "Visual" = se ve como Figma sin efecto.

| # | Pantalla | Nodo(s) de Figma | Ruta propuesta | Interacciones | Datos | Funcional / Visual |
|---|---|---|---|---|---|---|
| 1 | Barra inferior | instancia en 49:15, 2266:72, 2271:84, 2440:163 | componente `NightWatchBottomBar` | Cambiar pestaña; Social atenuada con aviso (sección 7); Perfil abre `UserMenu` (P15) | pestaña activa | Funcional |
| 2 | Inicio (reemplaza Main Menu) | `MAIN MENU` 49:15 | `Home(tab)` (reemplaza `MainMenu`) | Ícono de salida = cerrar sesión. "Lección diaria": abre la siguiente lección pendiente (P7). "Concepto nuevo" y "Prepárate con CiberMynd": atenuadas con "Próximamente" (P7) | nombre y avatar (existen), racha fija | Mixto: racha **fija**; 2 tarjetas visuales |
| 3 | Ruta de Aprendizaje (mapa) | `RUTA DE APRENDIZAJE (MAPA)` 2266:72 | `LearningPath` (pestaña de `Home`) | Encabezado "Tu nivel: BAJO/MEDIO/ALTO" (P2). Tocar una isla desbloqueada abre su lección; bloqueadas atenuadas. Barra "N de 7 islas" | `lesson_progress`, `lab_progress`, `exam_attempts`, `placement` (solo lectura, ya existe) | Funcional |
| 4 | Lección (6) | 2294:112, 2294:130, 2294:148, 2294:166, 2294:184, 2294:202 | `Lesson(topic)` | Leer 3 tarjetas; "Ir al laboratorio →"; "X" | `lesson_progress` | Funcional |
| 5 | Laboratorios (lista) | `LABORATORIOS` 2271:84 | `Labs` (pestaña) | 6 tarjetas con dificultad y estado ("✅ Completado" / "Comenzar →" / atenuada si bloqueada); tarjeta "Examen Final" que abre el examen cuando se completaron las 6 islas | `lab_progress` y derivados | Funcional |
| 6 | Laboratorio, terminal (6 × 2 estados) | 2374:273/303, 2374:357/387, 2383:141/173, 2383:206/238, 2299:118/148, 2383:271/303 | `Lab(lab)` | 3 objetivos con comando (tocar un objetivo coloca su comando); campo + "Enviar"; salida en el cuadro; ✓ por objetivo; **"Finalizar laboratorio" siempre habilitado** (P6) | comandos y salidas de Figma | Simulación local |
| 7 | Informe del laboratorio (6 × éxito/no completado) | 2273:131/2404:243, 2299:181/2370:139, 2348:156/2404:261, 2348:201/2404:279, 2374:336/2400:144, 2374:420/2404:154 | `LabReport(lab, completedObjectives)` | 3 objetivos cumplidos: informe de éxito y lab completado. Menos: "Laboratorio no completado", sin marcar | `lab_progress` solo si 3/3 | Funcional |
| 8 | Examen Final: introducción | `EXAMEN FINAL` 2486:249 | `ExamIntro` | Se abre desde la isla "Prueba Final" y desde Labs (P1) solo con las 6 islas completadas (P3); "X"; botón **"Comenzar Examen →"** (Figma: instancia `GoToLabButton` 2486:263, texto verificado en la captura de 2486:249) | último intento | Funcional |
| 9 | Examen: preguntas 1 a 6 | 2491:243…2491:328; variantes `[estado …]` 2498:246…2498:498 | `ExamQuestion(index)` | Una pregunta con 4 opciones; las variantes son estados visuales: opción elegida y barra de progreso (P8). **No se indica si una respuesta es correcta durante el examen** | preguntas fijas | Funcional |
| 10 | Examen aprobado (0 o 1 error) | 2491:357; 2498:512…2498:572 | `ExamResult` | Con 1 error muestra tema y respuesta correcta (Figma). Texto de felicitación propuesto (F7). **No cambia el nivel** (P5) | `exam_attempts` | Funcional |
| 11 | Examen no aprobado | `EXAMEN - NO APROBADO` 2491:345 | `ExamResult` | Accesos a "Repasa la Ruta", "Practica en los Laboratorios", "Vuelve a intentar el examen" (sin límite, P11). No dice cuáles falló | `exam_attempts` | Funcional |
| 12 | Aviso "Próximamente" | *(no está en Figma)* | sin ruta | Snackbar al tocar Social, "Concepto nuevo" o CiberMynd | ninguno | Visual |

**Se reutilizan, sin tocarlas:** Logros e insignias (2287:100, estática 3 de 9), Notificaciones (switch de "Nuevos laboratorios" y "Logros" sin efecto real), Perfil y Menú de usuario.

**Conexión con lo que ya existe:**
- **PostAuthGate:** `onNavigateToMainMenu` (`NightWatchNavHost.kt:119-120`) pasa a `Home(tab = Inicio)` (retornos al abrir la app).
- **Quiz:** el botón "Continuar" del resultado (`NightWatchNavHost.kt:143`) y el "COMENZAR" del plan de estudio de Figma (2017:160, 2051:268, 2051:288) llevan a `Home(tab = Ruta)` (P4). Un solo botón en la pantalla actual: se conserva su texto; el texto "COMENZAR" de Figma es diferencia (F10).
- **Nivel:** el mapa lee `placement` solo para leer (ya existe): "Tu nivel: BAJO/MEDIO/ALTO". Si no hay resultado o no se puede leer, **SUPUESTO:** "Tu nivel: Sin evaluar" (mismo valor que Mi Perfil, `LEVEL_NOT_ASSESSED`).
- **Racha:** Inicio y Perfil siguen con `FixedProfileData.STREAK_DAYS` (P9).

## 6. Qué puede ser funcional y qué no (plan gratuito de Supabase)

| Función | Estado | Motivo |
|---|---|---|
| Progreso de lecciones, labs y examen por usuario | **Funcional** (3 tablas pequeñas, RLS del dueño) | Muy poco volumen |
| Lecciones, labs, preguntas del examen | **Contenido local** (JSON en `assets`) | Como el quiz; cambiarlo exige una versión nueva |
| Desbloqueo en orden, "examen tras las 6 islas", "lección diaria" | **Funcional, calculado en la app** a partir del progreso | Se deriva de las tres tablas; **no se hace cumplir en la BD** (SUPUESTO: no hay ranking que lo exija) |
| Terminal de los labs | **Simulación local** | Solo comandos de Figma; no ejecuta nada real |
| Examen corregido en el teléfono | **Funcional con límite** | Las respuestas viajan en el APK; un cliente modificado podría falsear un intento (R2) |
| Racha real, insignias reales | **Fijas (visual)** | Módulo 4 (P9) |
| Notificación "Nuevos laboratorios" | **Sin efecto** | Requiere push; aceptado en el módulo 2 |
| "Concepto nuevo", "CiberMynd", Social | **Visual** ("Próximamente") | P7 y alcance |
| Sin conexión | Se pueden hacer lecciones, labs y examen; guardar el progreso falla como "sin conexión" | `CLAUDE.md` sección 8 |
| Pausa de 7 días del plan gratuito | Se mantiene | `CLAUDE.md` sección 8 |

## 7. Barra inferior, avisos "Próximamente" y Main Menu

**Barra inferior.** 🏠 Inicio, 🗺️ Ruta, 🧪 Labs, 👥 Social, 👤 Perfil, con los textos e íconos de Figma (49:15, 2266:72, 2271:84, 2440:163).
- **Inicio, Ruta y Labs** viven en un contenedor con la barra visible. La ruta `Home(tab)` recibe la pestaña inicial (`Inicio` por defecto, `Ruta` desde el quiz, P4).
- **Perfil** abre la ruta `UserMenu` existente (P15), por encima de la barra.
- **Lección, laboratorio, informe y examen** se abren a pantalla completa y ocultan la barra (tienen "X": 2294:112, 2374:273, 2491:243).

**Aviso "Próximamente" (confirmado por el grupo, Q6; el texto no está en Figma).** Un solo mecanismo para tres elementos:
- Pestaña **Social**, tarjeta **"Concepto nuevo"** y tarjeta **"Prepárate con CiberMynd"**.
- Se dibujan **atenuados** (misma opacidad que las islas y labs bloqueados, como en 2271:84) y, al tocarlos, no navegan ni cambian de pestaña: aparece un **Snackbar "Próximamente"**. La pestaña activa no cambia. Un solo mensaje a la vez.
- Accesibilidad: descripción "…, próximamente" y estado "no disponible"; siguen siendo enfocables para que TalkBack anuncie el aviso.
- Implementación: el ViewModel emite el aviso por un `Channel` y la pantalla lo muestra con `SnackbarHost`; sin ruta nueva.
- Cambia respecto a la v1 de la spec: el texto era "Social llegará pronto"; ahora es "Próximamente" para los tres (P7: "igual que Social").

**Atenuado por bloqueo (islas, labs, examen):** al tocar un elemento bloqueado aparece también un Snackbar corto, **"Completa la isla anterior"** para islas y labs, y **"Completa las 6 islas"** para el examen (textos confirmados por el grupo, Q6; Figma no tiene el estado de toque sobre un bloqueado).

**Dónde encajan "Mi cuenta" y "Cerrar sesión" (decisiones del módulo 2):**

| Elemento | Dónde va | Por qué |
|---|---|---|
| **Cerrar sesión** | Ícono de salida arriba a la derecha de Inicio (49:15) | Único lugar del diseño para eso; misma lógica (`deviceSessionManager.endSession()` y limpiar la pila hacia Login) |
| **Mi cuenta** | Pestaña "Perfil" (abre `UserMenu`, que tiene su propio "Cerrar sesión") | Equivalente exacto, sin botones extra (P15) |

## 8. Modelo de datos

Borrador SQL: `supabase/migrations/0007_progreso_aprendizaje.sql` ("BORRADOR sin aplicar"). No toca 0001 a 0006 ni ninguna tabla anterior.

| Tabla | Clave | Columnas (FUENTE) | Quién escribe |
|---|---|---|---|
| `lesson_progress` | (`user_id`, `topic`) | `topic` ∈ los 6 temas de la sección 1; `completed_at` lo pone el servidor | dueño: insertar y leer |
| `lab_progress` | (`user_id`, `lab`) | `lab` ∈ los mismos 6 códigos; `completed_at` | dueño: insertar y leer. **Solo existe fila si el lab se completó** (3/3 objetivos, P6) |
| `exam_attempts` | `id` identity | `correct_count` 0-6; `passed` **generada** (`correct_count >= 5`: "puedes fallar como máximo 1", Figma 2486:249); `taken_at` | dueño: insertar y leer; sin update ni delete |

- **Cambios respecto al borrador anterior:**
  - `REDES` pasa a `REDES_SEGURAS` (nombre de la isla, la lección y la etiqueta del examen). Los 6 temas ya eran separados en el borrador.
  - `lab_progress` **pierde la columna `accuracy` y el permiso y la política de `update`**: como un lab incompleto no se guarda (P6), todo lo guardado sería 100%, un dato sin información. La precisión que ve la persona se calcula en la app (confirmado, sección 14, Q1 y Q9). Si el grupo quiere guardar el mejor resultado, es una migración nueva (0008).
- **Nada de `placement`:** aprobar el examen se guarda **solo** en `exam_attempts` (P5). Ninguna tabla de 0007 tiene clave foránea hacia tablas anteriores; todas apuntan solo a `auth.users`, como las de 0001.
- **Sin límites (P11):** no hay restricción de número ni de frecuencia de intentos en `exam_attempts`.
- **RLS:** `(select auth.uid()) = user_id` en select e insert. `anon` sin acceso.
- **GRANT:** solo `authenticated`, por columna; sin `TRUNCATE`, `REFERENCES` ni `TRIGGER` (migración 0002).
- **Índice:** `exam_attempts_user_taken_idx`. Las otras dos ya tienen la clave primaria.
- **Idempotencia:** volver a ver una lección o repetir un lab ya completado no debe fallar; la app trata la fila ya existente como éxito (no hay `update`).
- **Storage y Edge Functions:** no hacen falta.
- **No se guarda:** cuáles preguntas falló el examen, el contenido, ni los comandos escritos.
- **El estado de cada isla se deriva** (función pura `LearningProgress`): completa = lección vista **y** lab completado (P3); desbloqueada = completa la anterior (la 1 siempre); examen = las 6 completas; "Prueba Final completada" = al menos un intento con `passed` (SUPUESTO).

## 9. Decisiones propuestas y supuestos

| # | Propuesta | Tipo |
|---|---|---|
| D1 | Pantalla contenedor `Home(tab)` con `Scaffold` y barra inferior; las pantallas a pantalla completa se apilan encima. Rutas `@Serializable` nuevas: `Home`, `Lesson(topic)`, `Lab(lab)`, `LabReport`, `ExamIntro`, `ExamQuestion`, `ExamResult` | Arquitectura |
| D2 | Contenido en JSON dentro de `assets` + `kotlinx.serialization`, repositorio con interfaz y *fake* | Por analogía con el quiz (`placement-quiz-design.md:65-67`) |
| D3 | Motor de terminal simulado como función pura: objetivo + texto → salida de Figma o "no reconocido". Un objetivo se cumple al ejecutar su comando exacto (sin distinguir espacios sobrantes), en cualquier orden | SUPUESTO (Figma no dice si el orden importa); el texto "no reconocido" es propuesto |
| D4 | Repositorios con interfaz y *fake*: `LearningContentRepository`, `LearningProgressRepository` (`SupabaseLearningProgressRepository` en `data/supabase`) | Convención |
| D5 | Estado de islas/labs/examen/"lección diaria" derivado en `LearningProgress` (pura) | Arquitectura |
| D6 | La fila se identifica por la sesión, nunca por un dato de la pantalla | `CLAUDE.md` sección 3 |
| D7 | Errores del servidor mapeados a `DataAccessException`; nunca se muestra la excepción de la librería | `CLAUDE.md` sección 3 |
| D8 | El examen se corrige en el ViewModel y solo se envía `correct_count` | Riesgo R2 |
| D9 | **Respuestas del examen, verificadas por el grupo contra Figma (P10):** P1 = **C** `k7#mQ!9zRt$2` (Figma 2498:512); P2 = **B** (Un enlace que no coincide con el dominio oficial); P3 = **C** (Analizarlo con un antivirus antes de abrirlo); P4 = **B** (Cuelgas y verificas por un canal oficial); P5 = **B** (Usar una VPN para cifrar tu tráfico); P6 = **B** (Filtrar el tráfico de red no autorizado) | Decisión del grupo P10 |
| D10 | **Textos (P12, opción A):** los textos nuevos de la UI van en `app/src/main/res/values/strings_aprendizaje.xml` con prefijo `learning_` (`stringResource`). No se tocan los textos de los módulos 1 y 2 (F-09 sigue pendiente para ellos). Contenido (lecciones, labs, preguntas) va en JSON, no en `strings.xml`. Hoy `strings.xml` solo tiene `app_name`; no hay otra infraestructura | Decisión del grupo P12 |
| D11 | **Versión (P14):** `versionName = "2.1.0"` al cerrar el módulo. `versionCode`: **verificado en `app/build.gradle.kts` (líneas 33-34): hoy `versionCode = 3` y `versionName = "2.0.0"`**, así que el siguiente real es **`versionCode = 4`**. Propuesta para el cierre (Etapa 6); no se cambia antes | P14 + verificación en el repo |

## 10. Etapas de implementación (un commit por etapa; cada una valida con `./gradlew assembleDebug testDebugUnitTest lintDebug`, y `compileDebugAndroidTestKotlin` si se tocan pruebas instrumentadas)

Nada se ve en un teléfono desde aquí: la columna "Prueba en el teléfono" la hace el usuario.

| Etapa | Contenido | Criterios de aceptación | Prueba en el teléfono |
|---|---|---|---|
| **0** (usuario) | Revisar y ejecutar `0007`; correr sus consultas de verificación | Todas dan lo esperado; `anon` sin filas; sin claves foráneas hacia tablas anteriores | Ejecutar y pegar los resultados |
| **1. Datos y contenido** | Modelos (`LearningTopic` con los 6 temas, `ExamQuestion`), JSON de contenido con **solo** lo de Figma y las respuestas de D9, `strings_aprendizaje.xml` base (textos de bloqueo, "Próximamente" y "No tienes lecciones pendientes" incluidos), repositorios con interfaz y *fakes*, `SupabaseLearningProgressRepository`, motor de terminal, `LearningProgress`, `AppContainer` | Pruebas de unidad: motor de terminal (3 objetivos, comando desconocido, orden libre), precisión y tiempo estimado (sección 14), corrección del examen (0 a 6 fallos y el borde 1/2 errores), derivación de progreso (isla completa = lección y lab; desbloqueo en orden; examen con 6 islas; "lección diaria"), mapeo de errores | Ninguna (sin UI) |
| **2. Contenedor, barra e Inicio** | `Home(tab)`, barra, avisos "Próximamente", ícono de salida, Perfil → `UserMenu`; `PostAuthGate` → `Home(Inicio)` y quiz → `Home(Ruta)` | Pruebas de ViewModel con Turbine: cambiar de pestaña; Social, "Concepto nuevo" y CiberMynd emiten el aviso y no navegan; "Lección diaria" abre la siguiente pendiente; cerrar sesión; navegación existente sigue pasando | Cambiar de pestaña; tocar los 3 elementos atenuados; Perfil abre el menú; cerrar sesión; terminar el quiz y llegar a Ruta |
| **3. Ruta y lecciones** | `LearningPath`, `Lesson`; leer/escribir `lesson_progress` | Pruebas: estado de islas y desbloqueo, "Tu nivel: …", escribir una vez, fallo de red | Ver el mapa y tu nivel; abrir islas en orden; el bloqueo de las siguientes; cerrar la app y comprobar que persiste |
| **4. Laboratorios** | `Labs`, `Lab` (terminal) e informe; `lab_progress` | Pruebas: 3/3 = éxito y se guarda; menos = "Laboratorio no completado" y no se guarda; "Finalizar" siempre habilitado; precisión | Hacer los 6 labs; finalizar con 0, 1, 2 y 3 objetivos; ver que el lab no se marca si no se completa |
| **5. Examen final** | `ExamIntro`, preguntas, resultados; `exam_attempts` | Pruebas: 0, 1 y 2 fallos; reintentos sin límite; no revelar aciertos durante el examen; no revelar cuáles falló; **no se escribe nada en `placement`** | Bloqueado con <6 islas; aprobar con 0 y 1 error; no aprobar; reintentar; nivel de Mi Perfil intacto |
| **6. Cierre** | Comparación contra Figma pantalla por pantalla, informe `docs/superpowers/reports/modulo-3-diagnostico.md`, actualización de `CLAUDE.md` y `README`, `versionName 2.1.0` | Todo en verde; lint en 0 errores; diferencias documentadas | Recorrido completo desde el registro |

**Pruebas (convenciones):** `MainDispatcherRule` y Turbine; `assertTrue`/`assertFalse`/`assertEquals` (nunca `assert()` suelto); relojes inyectables; *fakes* con fallos configurables. Sin pruebas automáticas de las llamadas reales a Supabase (F-03).

## 11. Riesgos

- **R1 — Reglas de negocio.** Resuelto: las decisiones (sección 13) y las confirmaciones (sección 14) cubren todas las reglas que Figma no definía. Queda solo ejecutar `0007`.
- **R2 — Examen falseable.** Respuestas en el APK; el cliente envía `correct_count`. Si el módulo 4 añade ranking o XP, revisar (p. ej. corrección en una función SQL).
- **R3 — Terminal simulada.** Solo reconoce los comandos de Figma; debe quedar claro que es una simulación.
- **R4 — Datos de Figma inconsistentes** (C6, C12, C13, C14): se resuelven con las decisiones del grupo; la comparación final con Figma las anota.
- **R5 — Muchos estados de Figma** (≈90 frames). Los 30 estados `[estado …]` del examen son visuales (P8).
- **R6 — Desbloqueo y examen solo en la app.** Un cliente modificado puede abrir el examen sin las 6 islas. Sin impacto mientras no haya datos sociales (R2).
- **R7 — Sin conexión.** Progreso sin guardar si no hay red; se avisa y se puede reintentar.
- **R8 — Contenido en el APK.** Cambiar contenido requiere versión nueva.
- **R9 — Pausa del proyecto Supabase tras 7 días sin uso** (aceptada).
- **R10 — Nada se ve en un teléfono desde aquí:** cada etapa depende de la prueba manual.
- **R11 — "Lección diaria" sin lección pendiente:** resuelto (Q3): aviso "No tienes lecciones pendientes".

## 12. Diferencias deliberadas frente a Figma

| # | Diferencia | Motivo / fuente |
|---|---|---|
| F1 | "Tu nivel: Aprendiz" (2266:72) se muestra como "Tu nivel: BAJO/MEDIO/ALTO" | P2 |
| F2 | Social aparece atenuada, muestra "Próximamente" y no navega; no hay pantallas de Social | Alcance (módulo 4) |
| F3 | El ícono de salida de Inicio conserva "Cerrar sesión" y "Mi cuenta" vive en la pestaña Perfil | Decisiones del módulo 2, P15 |
| F4 | Racha (15) e insignias (3 de 9) siguen fijas | P9 |
| F5 | Los comandos de la terminal solo reconocen los de Figma | No se inventan comandos |
| F6 | **Nuevas:** "Concepto nuevo" y "Prepárate con CiberMynd" (49:15) aparecen atenuadas y muestran "Próximamente" en vez de abrir algo | P7 |
| F7 | **Nueva (P5):** la pantalla de aprobado **no dice "¡Subiste de nivel!" ni "Nuevo contenido desbloqueado en tu ruta y laboratorios"** (2491:357). Lo mismo en la introducción del examen (2486:249, tarjeta "¿Qué gano?") y en las 6 variantes de 1 error (2498:512 a 2498:572). **Textos propuestos (no de Figma):** en aprobado, el bloque "¡Subiste de nivel!" + "Nuevo contenido desbloqueado…" se reemplaza por **"¡Felicidades por aprobar el examen final!"** + **"Completaste la Ruta de Aprendizaje de NightWatch Protocol."**; en la introducción, el texto de "¿Qué gano?" pasa a **"Al aprobar completas la Ruta de Aprendizaje."** Se conservan "¡Examen aprobado!", "¡Examen aprobado con 1 error!", los textos de "Demostraste que dominas…" y las filas ✅/⚠️ | P5: aprobar no cambia el nivel y no promete contenido nuevo |
| F8 | **Nueva (P6):** el informe con menos de 3 objetivos tiene el título **"Laboratorio no completado"**, también para los labs cuyo informe de error de Figma dice "¡Misión completada!", "¡Simulación completada!" o "¡Firewall configurado!" (2404:243, 2404:261, 2404:279). Los títulos del informe de éxito se mantienen como en Figma | P6, C5 |
| F9 | **Nueva (P6):** "Finalizar laboratorio" está siempre habilitado; en Figma se ve atenuado aun con los objetivos cumplidos (2374:303) | P6, C14 |
| F10 | **Nueva (P4):** el botón de la pantalla de resultado del quiz conserva su texto actual ("Continuar") y lleva a Ruta; Figma lo llama "COMENZAR" en el plan de estudio. Ambos llevan a Inicio con la pestaña Ruta | P4 |
| F11 | **Nueva (P3):** los bloqueos se calculan por el progreso (lección y lab, en orden). Los datos de ejemplo de Figma (3 de 7 islas con 2 labs hechos) no se reproducen | P3, C3 |
| F12 | **Nueva (P8):** durante el examen no hay indicación de acierto; solo la opción elegida y la barra de progreso | P8 |
| F13 | **Nueva:** los textos nuevos ("Próximamente", "Completa la isla anterior", "Completa las 6 islas", "comando no reconocido", errores de red) no están en Figma | Necesarios para que la UI no quede muda |
| F14 | **Nueva (P2/P6):** la precisión del informe muestra también 33% y 67% (en Figma solo hay 100% y 0%), y el tiempo estimado del informe de contraseñas sigue la regla de la sección 14, Q2: se muestra el de las salidas reales de la terminal, o "—" (el "3 Horas" de 2400:144 no coincide con ninguna salida) | Confirmado por el grupo (Q1, Q2) |
| F15 | **Nueva (error de gramática de Figma):** la salida del lab de correo dice "este es una imitación" en Figma (2383:173); en la app es **"esta es una imitación"** (`learning_labs.json`). Cierra el pendiente E1-9 | Corrección pedida por el grupo (Etapa 2, tarea 0) |
| F16 | **Nueva (Etapa 2):** la chapa de Inicio (72:2 a 72:6) agrega una tercera línea "Nivel actual: …" y la tarjeta "Lección diaria" una línea de estado debajo (siguiente lección, "No tienes lecciones pendientes" o error). Figma solo tiene nombre y racha, y la tarjeta sin texto | Pedido de la tarea (nivel con el formato de Mi Perfil; lección diaria o "No tienes lecciones pendientes") |
| F17 | **Nueva (Etapa 3):** "Tu nivel: Aprendiz" (2266:72) se muestra con BAJO/MEDIO/ALTO o "Sin evaluar" (ya era F1) y el contador sale del progreso real, no de los "3 de 7" de Figma | P2, Q5 |
| F18 | **Nueva (Etapa 3):** la lección se marca como vista al abrirse (Figma no dice cuándo) | Q11 |
| F19 | **Nueva (Etapa 3):** en la lección, el botón "Ir al laboratorio →" lleva por ahora a la pestaña Labs ("en construcción") hasta la Etapa 4; las tarjetas tienen alto mínimo de 128 dp y crecen si el texto lo necesita (en Figma son fijas) | Etapa 4 pendiente |

## 13. Decisiones del grupo (2026-10-18)

| # | Pregunta | Decisión del grupo | Qué cambia en la spec |
|---|---|---|---|
| P1 | ¿Desde dónde se abre el Examen Final? | Desde la isla "Prueba Final" y desde la tarjeta de Labs (misma pantalla 2486:249) | Sección 5 (filas 3, 5 y 8); C7 resuelta |
| P2 | ¿Qué texto muestra el mapa en vez de "Aprendiz"? | "Tu nivel: <BAJO/MEDIO/ALTO>" con el mismo formato del resultado del quiz | Sección 5 (fila 3), F1; "Sin evaluar" como SUPUESTO si no hay resultado |
| P3 | ¿Qué completa una isla, cómo se desbloquea, cuándo se abre el examen, influye el nivel? | Isla completa = lección vista **y** lab completado. Desbloqueo en orden. Examen al completar las 6 islas. El nivel no cambia lo que se desbloquea: todos empiezan en la isla 1 | Secciones 5, 6, 8 (estado derivado), 10; F11; C3 resuelta |
| P4 | ¿Adónde llevan "COMENZAR" y "Continuar" del quiz? | A Inicio con la pestaña Ruta seleccionada | Sección 5 (conexión), 7 (`Home(tab)`), 9 (D1), 10 (etapa 2); F10 |
| P5 | ¿Qué hace aprobar el examen? (CERO RIESGOS) | **No cambia el nivel.** No se escribe en `placement` ni en ninguna tabla anterior; "aprobado" solo en `exam_attempts`. Texto de aprobado sin "Subiste de nivel" ni promesa de contenido | Secciones 1, 5 (fila 10), 8, 10 (etapa 5); F7 (texto propuesto) y C15 |
| P6 | ¿Cómo se finaliza un lab y qué pasa si faltan objetivos? | "Finalizar" siempre habilitado. 3/3: éxito y lab completado. Menos: informe "Laboratorio no completado", sin marcar. Precisión y tiempo: confirmados en la sección 14 (Q1, Q2) | Secciones 5 (filas 6 y 7), 8 (`lab_progress` sin `accuracy` ni `update`), 10 (etapa 4); F8, F9, F14 |
| P7 | ¿Qué hacen las tarjetas de Inicio? | "Lección diaria" abre la siguiente lección pendiente. "Concepto nuevo" y "Prepárate con CiberMynd" atenuadas con "Próximamente", igual que Social | Secciones 5 (fila 2), 7, 12 (F6); Q3 y Q10 (sección 14) |
| P8 | ¿Qué significan las variantes `[estado …]` del examen? | Estados visuales (opción elegida y barra de progreso). No se indica si una respuesta es correcta durante el examen | Sección 5 (fila 9), R5; F12 |
| P9 | ¿Racha e insignias? | Siguen fijas (15 días, 3 de 9). Sus reglas se definen en el módulo 4 | Secciones 3 y 6; F4. `2026-10-18-modulo-4-social-analisis.md` conserva la pregunta de la racha como del módulo 4 |
| P10 | ¿Respuestas correctas del examen? | Verificadas por el grupo contra Figma: P1 = C (2498:512), P2 = B, P3 = C, P4 = B, P5 = B, P6 = B | Sección 9 (D9) |
| P11 | ¿Límites del examen? | Sin límite de tiempo, de intentos ni de espera | Secciones 5 (fila 11) y 8 |
| P12 | ¿Textos? | Opción A: `strings_aprendizaje.xml` con prefijo `learning_` | Sección 9 (D10), etapas 1 y 2 |
| P13 | ¿Se aprueba `0007` con 3 tablas? | Se mantienen las 3 tablas, sujeto a revisión del SQL | Sección 8; `0007` ajustado (quita `accuracy` y `update` de `lab_progress`, renombra `REDES_SEGURAS`) |
| P14 | ¿Versión al cerrar el módulo? | 2.1.0 | Sección 9 (D11); `versionCode` actual 3 (verificado), siguiente 4 |
| P15 | ¿Qué abre la pestaña Perfil? | El Menú de usuario | Secciones 5 y 7 |

**Corrección a la sección 1 (del grupo):** los temas son 6 y separados, con las etiquetas de Figma (p. ej. "Redes Seguras"). Verificado contra Figma en la tabla de la sección 1.

## 14. Decisiones del grupo, segunda ronda: confirmaciones a las propuestas de la v2 (2026-10-18)

El grupo confirmó todas las propuestas de la v2 (Q1 a Q8) y añadió Q9 y Q10. Quedan registradas como decididas. No queda ninguna propuesta pendiente.

| # | Decisión confirmada | Por qué / fuente |
|---|---|---|
| Q1 | **Precisión del lab (P6):** `precisión = round(100 × objetivos cumplidos / 3)`. Todos los labs tienen exactamente 3 objetivos (Figma, 6 terminales). Resultados posibles: 0%, 33%, 67% y 100%. El informe de éxito muestra 100% y el de "no completado" el valor que corresponda. Se calcula en la app y no se guarda. Cada objetivo se marca ✅ si se cumplió y ❌ si no (Figma solo muestra todo ✅ o todo ❌; mezclarlos es extensión). Los textos descriptivos del informe de error ("No confirmaste que…") se conservan | **Confirmada.** Figma solo muestra 100% y 0% (2273:131, 2404:243) |
| Q2 | **Tiempo estimado del lab de contraseñas (P6):** el informe muestra el tiempo de **la contraseña más fuerte que la persona probó**, tomado de las salidas de Figma (2374:303): `crack 123456` → "menos de 1 segundo", `crack Nightwatch2024` → "4 horas", `crack N!ghtW4tch#7kQ2` → "300 años". "Más fuerte" sigue el orden de los objetivos (débil < patrón < robusta). Si no probó ninguna: "—". En éxito Figma muestra "300 Años" (2374:336): coincide. En no completado, Figma muestra "3 Horas" (2400:144), que **no coincide** con ninguna salida (el patrón dice "4 horas"): no se reproduce ese valor: **en el informe de error, un tiempo que no coincida con ninguna salida de la terminal se muestra como "—"**, y se muestra la salida real del comando más fuerte que sí se ejecutó. La precisión de este lab sigue Q1; el informe de Figma no muestra porcentaje, así que se muestra el tiempo y se omite el %. **Confirmada** (interpretación: el tiempo siempre sale de una salida real de la terminal) | C6 |
| Q3 | **"Lección diaria" (P7):** la "siguiente lección pendiente" es la de la primera isla desbloqueada cuya lección **no se ha visto**. Si no hay ninguna (todas vistas, o la lección ya vista pero falta el lab), la tarjeta muestra un Snackbar **"No tienes lecciones pendientes"** (texto no de Figma). **Confirmada** | Evita abrir una lección bloqueada |
| Q4 | **Acceso a un lab (P3):** un lab es accesible cuando su isla está desbloqueada, **sin exigir haber visto antes la lección** (la lección tiene el botón "Ir al laboratorio →" pero la lista de Labs también ofrece "Comenzar →"). La isla se completa solo con la lección **Y** el lab. **Confirmada** | P3 no lo aclaraba |
| Q5 | **Contador "N de 7 islas":** cuenta las 6 islas completas más la Prueba Final si hay un intento aprobado. **Confirmada** | Figma 2266:72 cuenta 7 |
| Q6 | **Textos de bloqueo** al tocar un elemento bloqueado: "Completa la isla anterior" (islas y labs) y "Completa las 6 islas" (examen). **Confirmados** | Figma no tiene el estado (sección 7) |
| Q7 | **Orden de los temas según Figma:** Contraseñas, Phishing, Malware, Ingeniería Social, Redes Seguras, Firewall (lecciones 1 a 6, islas y preguntas del examen). **Confirmado** | C12; define el desbloqueo en orden y el orden de las preguntas |
| Q8 | **Botón de inicio del examen:** **sí existe en Figma**: **"Comenzar Examen →"**, instancia `GoToLabButton` (2486:263) de la pantalla 2486:249; el texto no sale en la exportación de textos pero sí en la captura. No hace falta proponer otro texto, y no hay diferencia con Figma. (El nombre interno `GoToLabButton` sugiere que es un componente reutilizado del botón "Ir al laboratorio →" de las lecciones; solo informativo) | Verificado en Figma por captura de 2486:249 |
| Q9 | **`lab_progress` sin `accuracy`**: la tabla guarda solo `user_id`, `lab` y `completed_at`; la precisión se calcula en la app. **Confirmado** | Sección 8 y `0007` |
| Q10 | **"Próximamente" unificado** para Social, "Concepto nuevo" y "Prepárate con CiberMynd" (un solo Snackbar). **Confirmado** | Sección 7, P7 |
| Q11 | **Lección vista al abrirse (Etapa 3):** la lección se registra en `lesson_progress` en el momento en que se abre y se muestra (no al terminarla ni al tocar "Ir al laboratorio →"), con el registro idempotente (abrirla otra vez no da error ni duplica). Si guardar falla, se puede leer igual, sale un aviso breve y no se reintenta hasta la próxima vez que se abra | **Decisión del grupo, confirmada por el usuario** al pedir la Etapa 3. Cambia la fila 4 de la sección 5 ("Lección") |
