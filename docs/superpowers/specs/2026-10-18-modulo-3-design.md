# Módulo 3: Ruta de aprendizaje, lecciones, laboratorios y examen final: spec de diseño

Estado: **BORRADOR para revisión del grupo. No se programa nada hasta que se resuelvan las preguntas abiertas de la sección 13.**
Fecha: 2026-10-18. Rama: `modulo-3`. Figma: fileKey `wd3fQTzF5NqsfGfdR4I1lb` (una sola página, `0:1`).

Convención de este documento: cada afirmación lleva su **FUENTE** (archivo del repo o nodo de Figma `nombre (ID)`). Lo que no tiene fuente va marcado **SUPUESTO** o **PREGUNTA ABIERTA** y no está decidido.

## 1. Alcance

**Decisión del equipo para esta spec** (respuesta de la persona responsable al diagnóstico previo):

- **Dentro:** Ruta de Aprendizaje (mapa), Lecciones, Laboratorios (lista, terminal e informes), Examen Final y la barra inferior de navegación (Inicio, Ruta, Labs, Social, Perfil), que **reemplaza al Main Menu provisional**.
- **Fuera:** todo el contenido de Social (17 pantallas `2440:163` a `2451:209`). Es el **módulo 4**: ver `2026-10-18-modulo-4-social-analisis.md` (solo análisis, sin SQL).
- **Excepción:** la pestaña "Social" **sí aparece** en la barra inferior, como en Figma, pero **no navega a ninguna pantalla** (sección 7).
- **Niveles:** se mantiene BAJO/MEDIO/ALTO (el código y la BD ya lo usan). "Aprendiz" (mapa) e "Intermedio" (Perfil, Figma) **no** se implementan como escala propia.
- **Temas:** los 6 de las lecciones y los laboratorios: Contraseñas, Phishing, Malware, Ingeniería Social, Redes y Firewall.
- **Sin cambios** en módulos 1 y 2, salvo lo mínimo para conectar (`CLAUDE.md` regla 7.6): el Main Menu y las rutas de salida del quiz.

## 2. Fuentes consultadas

| Fuente | Qué aporta |
|---|---|
| `CLAUDE.md` | Estado, convenciones, reglas de git y de Supabase |
| `docs/superpowers/specs/2026-09-23-auth-onboarding-design.md` (líneas 15-19, 56-57, 71) | Los 4 subsistemas del Figma; dejan fuera quiz y Core learning |
| `docs/superpowers/specs/2026-09-24-placement-quiz-design.md` (líneas 12-15, 57-69) | Deja fuera "the real Core Learning map, navigable lessons, labs, and final exam"; el plan de estudio es una lista estática |
| `docs/superpowers/specs/2026-10-09-profile-settings-design.md` (línea 16, 29, 214, 217) | Deja fuera Core Learning y Social; racha 15 e insignias 3 de 9 fijas; switches de notificaciones |
| `docs/superpowers/reports/modulo-2-diagnostico.md` (líneas 97-98, 197-200) | "Logros, laboratorios y novedades solo guardan el switch (necesitan backend)"; la racha es fija |
| `README.md` (líneas 55-56) | Logros e insignias solo visuales |
| `app/src/main/java/.../data/profile/FixedProfileData.kt` | Comentario: "Placeholder values until the learning module can provide real streaks, devices and badges" |
| `app/src/main/java/.../data/quiz/StudyPlan.kt`, `PlacementLevel.kt`, `QuizCategory.kt` | Niveles BAJO/MEDIO/ALTO y 4 categorías del quiz |
| `app/src/main/java/.../ui/mainmenu/MainMenuScreen.kt`, `ui/navigation/NightWatchNavHost.kt` | El Main Menu actual (placeholder) y a dónde llegan Login, quiz y resultado |
| `supabase/migrations/0001` a `0006` | Convenciones de SQL (RLS, GRANT, `(select auth.uid())`) |
| Figma `RUTA DE APRENDIZAJE (MAPA)` (2266:72), `LABORATORIOS` (2271:84), `LECCION - *` (2294:112 a 2294:202), todos los `LAB - *`, `EXAMEN *`, `MAIN MENU` (49:15), `SOCIAL - *` | Pantallas y textos |

No existe ningún documento del repo que defina reglas del módulo 3 (desbloqueo de islas, qué completa una isla, puntos o XP). Solo lo dice el Figma, y en parte se contradice (sección 4).

## 3. Lo ya definido, lo nuevo y lo que falta

**Ya definido (se respeta):**
- Core learning = "map, lessons, labs, final exam", y es un subsistema aparte de Social/Gamification. FUENTE: `2026-09-23-auth-onboarding-design.md:15-19`.
- El quiz de nivelación entrega BAJO/MEDIO/ALTO y un plan de estudio estático, no navegable. FUENTE: `2026-09-24-placement-quiz-design.md:57-61`.
- Contenido empaquetado en la app, no editable desde un servidor. FUENTE: `2026-09-24-placement-quiz-design.md:65-67` (decisión del quiz; aquí se propone repetirla, ver D2).
- Racha 15 días e insignias 3 de 9 son datos fijos hoy. FUENTE: `2026-10-09-profile-settings-design.md:27`, `FixedProfileData.kt`.
- Notificaciones de "Nuevos laboratorios" solo guardan el switch. FUENTE: `modulo-2-diagnostico.md:98`.
- Reglas de Supabase: RLS solo del dueño, GRANT explícito, mínimo privilegio, migraciones nuevas sin editar las viejas, el SQL lo ejecuta el usuario. FUENTE: `CLAUDE.md` sección 4.

**Nuevo en esta spec (lo único que sale de Figma y no estaba en el plan):** mapa de 6 islas + Prueba Final, 6 lecciones, 6 laboratorios con terminal simulada, examen de 6 preguntas, barra inferior.

**Sin definir (no se inventa; ver sección 13):** qué completa una isla, cómo se desbloquea, qué hacen las tarjetas de Inicio, qué hace un "nivel" distinto de BAJO/MEDIO/ALTO, qué significa "subiste de nivel", racha real e insignias.

## 4. Contradicciones e inconsistencias (reportadas, no resueltas)

| # | Hallazgo | Fuentes |
|---|---|---|
| C1 | La brief decía "ruta + labs = social"; los docs y Figma los separan. **Resuelta por el equipo:** Social es módulo 4. | `2026-09-23-auth-onboarding-design.md:15-19` |
| C2 | El mapa dice "Tu nivel: Aprendiz", Figma Perfil y Resultados del quiz dicen "Intermedio", el código usa BAJO/MEDIO/ALTO. **Resuelta por el equipo:** se mantiene BAJO/MEDIO/ALTO. Queda por definir qué texto muestra el mapa (P2). | Figma 2266:72, 2068:51, 2017:126; `PlacementLevel.kt` |
| C3 | "3 de 7 islas completadas" (3 islas en amarillo) frente a la lista de labs, donde solo 2 están "Completado" (Contraseñas y Correo falso). El Malware aparece completo en el mapa y pendiente en Labs. Son datos de ejemplo, pero implican que **completar una isla no es lo mismo que terminar su laboratorio** (PREGUNTA P3). | Figma 2266:72, 2271:84 |
| C4 | El plan de estudio del quiz de Figma lista 6 temas (con "Buenas Prácticas" y "Privacidad de Datos"); las lecciones/labs cubren otros 6 y el quiz del código tiene 4 categorías. **Resuelta por el equipo** para este módulo (6 temas de lecciones). El plan de estudio ya implementado no cambia. | Figma 2017:160, 2294:*; `QuizCategory.kt`, `StudyPlan.kt` |
| C5 | En los informes de laboratorio la variante "(ERROR)" conserva el título "¡Misión completada!" / "¡Laboratorio completado!" con 0% y ❌. No se sabe qué lleva a ese estado, porque "Finalizar laboratorio" parece deshabilitado hasta cumplir los objetivos (P6). | Figma 2404:243, 2370:139, 2400:144 |
| C6 | El informe de contraseñas muestra "300 Años" / "3 Horas" (tiempo estimado) en vez de un porcentaje; los demás muestran "% de precisión". | Figma 2374:336, 2400:144 vs 2273:131 |
| C7 | La tarjeta "Examen Final" aparece dentro de la lista de Labs (con la etiqueta "Prueba") y también como última isla del mapa ("Prueba Final"). Son dos entradas a la misma pantalla `2486:249`, aunque un laboratorio no es un examen. | Figma 2271:84, 2266:72 |
| C8 | Los textos "MÓDULO 1 de 6" … "MÓDULO 6 de 6" de las lecciones usan "módulo" para un tema; en el proyecto "módulo" es una etapa de desarrollo. Solo es un problema de lenguaje en la UI. | Figma 2294:112 a 2294:202 |
| C9 | El Main Menu de Figma tiene un ícono de salida arriba a la derecha (cerrar sesión), tres tarjetas ("Lección diaria", "Concepto nuevo", "Prepárate con CiberMynd") y no tiene "Mi cuenta". El Main Menu del código tiene dos botones de texto. | Figma 49:15; `MainMenuScreen.kt` |
| C10 | "Prepárate con CiberMynd" no se menciona en ningún documento ni tiene pantalla destino en Figma. | Figma 49:15 |
| C11 | Repo: la rama `modulo-3` ya existe también en remoto (`origin/modulo-3`) y está 2 commits por delante de `b339374` (los del `CLAUDE.md` y `.gitignore`). La brief decía que "parte de `b339374`": es cierto, pero no coincide con `main`. Existe además la rama/worktree `worktree-auth-onboarding` (`9a06941`). Solo informativo. | `git branch -vv`, `git worktree list` |

## 5. Pantallas del módulo 3

"Funcional" = lee o escribe datos reales (Supabase o DataStore). "Visual" = se ve como Figma pero con datos fijos o sin efecto.

| # | Pantalla | Nodo(s) de Figma | Ruta propuesta | Interacciones (FUENTE: Figma) | Datos | Funcional / Visual |
|---|---|---|---|---|---|---|
| 1 | Barra inferior (Inicio, Ruta, Labs, Social, Perfil) | instancia visible en 49:15, 2266:72, 2271:84, 2440:163 | componente `NightWatchBottomBar` | Cambiar de pestaña. Social: ver sección 7 | pestaña activa (memoria) | Funcional |
| 2 | Inicio (reemplaza Main Menu) | `MAIN MENU` 49:15 | `Home` (reemplaza `MainMenu`) | Ícono de salida (cerrar sesión). Tres tarjetas: ver P7 | nombre y avatar (ya existen), nivel, racha | Mixto: la racha sigue **fija** (15); tarjetas sin definir |
| 3 | Ruta de Aprendizaje (mapa) | `RUTA DE APRENDIZAJE (MAPA)` 2266:72 | `LearningPath` | Tocar una isla abre su lección; la última, "Prueba Final". Encabezado "Tu nivel" y "N de 7 islas completadas" con barra | progreso por tema, examen aprobado, nivel | Funcional (progreso en Supabase); regla de desbloqueo PREGUNTA P3 |
| 4 | Lección (6) | 2294:112, 2294:130, 2294:148, 2294:166, 2294:184, 2294:202 | `Lesson(topic)` | Leer tres tarjetas ("¿Qué es?", "¿Por qué importa?", "¿Cómo protegerte?"); botón "Ir al laboratorio →"; "X" cierra | marca "lección vista" | Funcional |
| 5 | Laboratorios (lista) | `LABORATORIOS` 2271:84 | `Labs` | 6 tarjetas con dificultad (Fácil, Medio, Difícil) y estado ("✅ Completado" o "Comenzar →"; algunas se ven atenuadas); última tarjeta "Examen Final" | progreso de labs | Funcional; regla de bloqueo PREGUNTA P3 |
| 6 | Laboratorio, terminal (6 × 2 estados) | Contraseñas 2374:273/2374:303, Archivo 2374:357/2374:387, Correo 2383:141/2383:173, Llamada 2383:206/2383:238, Redes 2299:118/2299:148, Firewall 2383:271/2383:303 | `Lab(lab)` | Lista de 3 objetivos con su comando ("TOCA PARA USAR EL COMANDO"); campo "escribe un comando…" + "Enviar"; la salida aparece en el cuadro; cada objetivo cumplido se marca ✓; "Finalizar laboratorio" | comandos y salidas: **contenido fijo** (Figma) | Funcional como simulación local; nada se ejecuta de verdad |
| 7 | Informe del laboratorio (6 × éxito/error) | Misión/correo 2273:131 / 2404:243, Red 2299:181 / 2370:139, Llamada 2348:156 / 2404:261, Firewall 2348:201 / 2404:279, Contraseñas 2374:336 / 2400:144, Malware 2374:420 / 2404:154 | `LabReport(lab, ...)` | Ver título, % de precisión (o tiempo estimado en contraseñas) y checklist; ver P6 para el estado de error | `lab_progress` | Funcional |
| 8 | Examen Final: introducción | `EXAMEN FINAL` 2486:249 | `ExamIntro` | "X"; botón para comenzar (no se ve en los textos exportados: **verificar en el diseño**) | último intento | Funcional |
| 9 | Examen Final: preguntas 1 a 6 | 2491:243, 2491:260, 2491:277, 2491:294, 2491:311, 2491:328 y variantes `[estado …]` 2498:246 a 2498:498 | `ExamQuestion(index)` | Una pregunta con 4 opciones (A a D); "Pregunta N de 6"; "X". Las variantes `[estado 1_1…F]` no se explican (P8) | preguntas fijas | Funcional |
| 10 | Examen: aprobado (con 0 o 1 error) | 2491:357 y 2498:512, 2498:524, 2498:536, 2498:548, 2498:560, 2498:572 | `ExamResult(...)` | Con 1 error muestra el tema y la respuesta correcta ("Tema: …", "Correcta: …"). "¡Subiste de nivel!" (P5) | `exam_attempts` | Funcional; el "subir de nivel" PREGUNTA P5 |
| 11 | Examen: no aprobado | `EXAMEN - NO APROBADO` 2491:345 | `ExamResult(...)` | Accesos a "Repasa la Ruta", "Practica en los Laboratorios", "Vuelve a intentar el examen". No dice cuáles fallaste | `exam_attempts` | Funcional |
| 12 | Aviso "Próximamente" de Social | *(no está en Figma)* | sin ruta | Ver sección 7 | ninguno | Visual |

**Se reutilizan, sin tocarlas:** Logros e insignias (2287:100, sigue estática 3 de 9), Notificaciones (los switches de "Nuevos laboratorios" y "Logros" siguen sin efecto real), Perfil y Menú de usuario (los abre la pestaña Perfil).

**Conexión con lo que ya existe:**
- **PostAuthGate → Home:** `PostAuthGateScreen.onNavigateToMainMenu` y `PlacementResultScreen` ("Continuar") navegan hoy a `MainMenu`; pasarán a `Home`. FUENTE: `NightWatchNavHost.kt:117-147`.
- **Plan de estudio del quiz:** Figma tiene un botón "COMENZAR" (2017:160, 2051:268, 2051:288). El resultado hoy muestra "Continuar". SUPUESTO: "Continuar" lleva a Inicio. A dónde llevaría "COMENZAR" no está definido (P4).
- **Nivel:** Inicio/Ruta leen `placement` (ya existe) y muestran BAJO/MEDIO/ALTO o "Sin evaluar", igual que Mi Perfil (`ProfileFormatters.kt`).
- **Racha:** Inicio y Perfil siguen con `FixedProfileData.STREAK_DAYS` (PREGUNTA P9).

## 6. Qué puede ser funcional y qué no (plan gratuito de Supabase)

| Función | Estado | Motivo |
|---|---|---|
| Progreso de lecciones, labs y examen guardado por usuario | **Funcional** (3 tablas pequeñas, RLS del dueño) | Muy poco volumen: unas pocas filas por persona |
| Lecciones, labs, preguntas del examen | **Funcional, contenido local** (JSON en `assets`) | Como el quiz: sin servidor editable; cambiarlo exige una versión nueva de la app |
| Terminal de los laboratorios | **Simulación local** | Solo se reconocen los comandos de Figma. No ejecuta nada real (ni debe) |
| Examen corregido en el teléfono | **Funcional con límite** | Las respuestas viajan en el APK: un cliente modificado podría falsear un intento. Aceptable mientras no haya ranking ni datos sociales que dependan de esto (riesgo R2) |
| Racha real | **Fija (visual)** hasta que el grupo defina la regla (P9) | No hay regla en ningún documento |
| Insignias reales | **Visual (3 de 9)** | Sin regla definida (P9) |
| Notificación "Nuevos laboratorios" | **Sin efecto** | Requeriría notificaciones push (servidor); el módulo 2 ya lo aceptó |
| Aviso de pausa de 7 días del plan gratuito | **Se mantiene** | Ya está aceptado en `CLAUDE.md` sección 8 |
| Sin conexión | Las lecciones, labs y el examen se pueden **hacer**; guardar el progreso falla como "sin conexión" (como en el resto de la app) | `CLAUDE.md` sección 8: sin modo sin conexión |

## 7. Barra inferior, "Social" y Main Menu

**Barra inferior.** Cinco pestañas con los textos e íconos del Figma (🏠 Inicio, 🗺️ Ruta, 🧪 Labs, 👥 Social, 👤 Perfil). FUENTE: 49:15, 2266:72, 2271:84, 2440:163.
- **Inicio, Ruta y Labs** viven dentro de una pantalla contenedor con la barra visible (`Scaffold`).
- **Perfil** abre la ruta `UserMenu` existente (Menú de usuario del módulo 2, con su "X"). No es una pestaña con contenido propio. (SUPUESTO: el Figma del Menú de usuario `2068:30` es un panel con "X" y no tiene la barra.)
- **Las pantallas de lección, laboratorio, informe y examen se abren por encima y ocultan la barra**, porque en Figma tienen "X" de cierre y no muestran la barra (2294:112, 2374:273, 2491:243).

**Pestaña "Social" (propuesta, a confirmar):**
- Se dibuja **atenuada** (misma opacidad que las islas bloqueadas del mapa), con el ícono y el rótulo del Figma.
- Al tocarla no cambia de pestaña ni navega: aparece un **Snackbar** corto: **"Social llegará pronto"** (texto propuesto; no está en Figma). Un solo mensaje a la vez, sin acumulación.
- La pestaña activa sigue siendo la anterior.
- Accesibilidad: descripción "Social, próximamente" y el estado "no disponible"; sigue siendo enfocable para que TalkBack pueda anunciar el aviso.
- Por qué Snackbar y no un diálogo: no bloquea, no exige "Aceptar" y es lo menos intrusivo para algo que se toca por curiosidad.
- Implementación (convención del proyecto): el contenedor emite el aviso por un `Channel` del ViewModel y se muestra con `SnackbarHost`; sin ruta nueva.

**Dónde encajan "Mi cuenta" y "Cerrar sesión" (decisiones del módulo 2, `profile-settings-design.md` P4 y decisión 10):**

| Elemento | Dónde va | Por qué |
|---|---|---|
| **Cerrar sesión** (Main Menu) | **El ícono de salida arriba a la derecha de Inicio**, que ya existe en Figma (49:15) | Es el único lugar del diseño pensado para eso; se conserva la misma lógica (`deviceSessionManager.endSession()` y limpiar la pila hacia Login) |
| **Mi cuenta** (Main Menu) | **La pestaña "Perfil"** de la barra, que abre `UserMenu` (que ya tiene su propio "Cerrar sesión") | Es el equivalente exacto de "Mi cuenta" y no añade botones al diseño. Además, tocar la chapa con avatar y nombre de Inicio (49:15) podría abrir lo mismo: **SUPUESTO**, no está en Figma |

Los dos caminos de "Cerrar sesión" (Inicio y Menú de usuario) siguen usando la misma lógica. No se pierde ninguna función del Main Menu actual.

## 8. Modelo de datos

Borrador SQL: `supabase/migrations/0007_progreso_aprendizaje.sql` (con el comentario "BORRADOR sin aplicar"). No toca 0001 a 0006.

| Tabla | Clave | Columnas (FUENTE) | Quién escribe |
|---|---|---|---|
| `lesson_progress` | (`user_id`, `topic`) | `topic` ∈ 6 temas (las 6 lecciones de Figma); `completed_at` lo pone el servidor | dueño: insertar y leer |
| `lab_progress` | (`user_id`, `lab`) | `lab` ∈ 6 labs (nombres de 2271:84); `accuracy` 0-100 (informes de Figma); `completed_at` | dueño: insertar, leer y actualizar solo `accuracy` |
| `exam_attempts` | `id` identity | `correct_count` 0-6; `passed` **generada** (`correct_count >= 5`, "puedes fallar como máximo 1", 2486:249); `taken_at` | dueño: insertar y leer; sin update ni delete |

- **RLS:** `(select auth.uid()) = user_id` en select, insert y update (donde aplica). `anon` sin acceso.
- **GRANT:** solo `authenticated`, por columna; sin `TRUNCATE`, `REFERENCES` ni `TRIGGER` (migración 0002).
- **Índice:** `exam_attempts_user_taken_idx` para el último intento. Las otras dos tablas ya tienen la clave primaria como índice.
- **Storage:** no hace falta ningún bucket. **Edge Functions:** no hacen falta.
- **No se guarda:** cuáles preguntas falló el examen (Figma dice que no se muestran), el contenido de las lecciones ni de los labs, el resultado de comandos.
- **No hay tabla de "isla completada":** el estado de cada isla se **deriva** de estas tres tablas con una función pura probada (`LearningProgress`), según la regla que decida el grupo (P3). Así cambiar la regla no exige migrar datos.
- **Qué falta modelar (no se inventa):** racha real, insignias, XP, el cambio de nivel tras el examen (P5, P9).

## 9. Decisiones propuestas y supuestos

Son propuestas técnicas; ninguna toca reglas de negocio sin fuente.

| # | Propuesta | Tipo |
|---|---|---|
| D1 | Una **pantalla contenedor** (`Home`) con `Scaffold` y barra inferior; las pantallas a pantalla completa se apilan encima. Rutas `@Serializable` nuevas: `Home`, `Lesson(topic)`, `Lab(lab)`, `LabReport`, `ExamIntro`, `ExamQuestion`, `ExamResult`. | Arquitectura (convención del proyecto) |
| D2 | Lecciones, labs y examen en JSON dentro de `assets`, con `kotlinx.serialization` y un repositorio de contenido con interfaz y *fake*, como `quiz_questions.json`. | SUPUESTO por analogía con el quiz (`placement-quiz-design.md:65-67`) |
| D3 | Un **motor de terminal simulado** como función pura: dado el objetivo y el texto, devuelve la salida de Figma o "no reconocido". Comandos y salidas se copian solo de Figma. | Arquitectura; el texto de "no reconocido" es SUPUESTO (no está en Figma) |
| D4 | Repositorios con interfaz y *fake*: `LearningContentRepository`, `LearningProgressRepository` (Supabase: `SupabaseLearningProgressRepository` en `data/supabase`). | Convención |
| D5 | Estado de cada isla/lab derivado en una función pura `LearningProgress` (sin Android ni Supabase). | Arquitectura |
| D6 | La fila del usuario se identifica **por la sesión**, nunca por un dato de la pantalla. | `CLAUDE.md` sección 3 |
| D7 | Errores del servidor: se mapean a `DataAccessException` y la UI los traduce; nunca se muestra la excepción de la librería. | `CLAUDE.md` sección 3 |
| D8 | El examen se **corrige en el ViewModel** y solo se envía `correct_count`. Mientras no haya ranking esto es suficiente. | Riesgo R2 |

**Textos y `strings.xml`.** Hoy `app/src/main/res/values/strings.xml` solo tiene `app_name`; **no existe infraestructura de textos**, y `CLAUDE.md` (sección 3 y 8, F-09) dice que ningún texto está en recursos y que es una tarea de todo el proyecto. Opciones para el módulo 3 (decide el grupo, P12):
- **A (recomendada):** los textos nuevos de la interfaz en un archivo propio `res/values/strings_aprendizaje.xml`, con prefijo `learning_`, leídos con `stringResource`. No toca los módulos 1-2, y deja la migración de F-09 sin deuda adicional para este módulo.
- **B:** textos en el código, como los módulos 1 y 2 (consistente hoy, pero suma deuda a F-09).
- El **contenido** (lecciones, labs, preguntas) va en JSON en cualquier caso (D2), no en `strings.xml`.

## 10. Etapas de implementación (cada una: un commit, validación y diagnóstico final)

Todas validan con `./gradlew assembleDebug testDebugUnitTest lintDebug` (+ `compileDebugAndroidTestKotlin` si se tocan pruebas instrumentadas). Nada se ve en un teléfono desde aquí: la columna "Prueba en el teléfono" la hace el usuario.

| Etapa | Contenido | Criterios de aceptación | Prueba en el teléfono |
|---|---|---|---|
| **0** (usuario) | Revisar y ejecutar `0007` en el SQL Editor; correr las consultas de verificación comentadas | Las 5 verificaciones dan lo esperado; `anon` sin filas | Ejecutar y pegar los resultados |
| **1. Datos y contenido** | Modelos (`LearningTopic`, `Lab`, `ExamQuestion`), JSON de contenido con **solo** lo que está en Figma, repositorios con interfaz, *fakes*, `SupabaseLearningProgressRepository`, motor de terminal, `LearningProgress`, `AppContainer` | Pruebas de unidad: motor de terminal (objetivos y comandos), corrección del examen (0 a 6 fallos y el borde de 1 error), derivación de progreso, mapeo de errores | Ninguna (sin UI) |
| **2. Contenedor, barra e Inicio** | `Home`, barra inferior, aviso "Próximamente" de Social, ícono de salida, Perfil → `UserMenu`; `PostAuthGate` y `PlacementResult` apuntan a `Home` | Pruebas de ViewModel con Turbine (cambio de pestaña, Social no cambia la pestaña y emite el aviso, cerrar sesión); pruebas de navegación existentes siguen pasando | Cambiar de pestaña; tocar Social y ver el aviso sin navegar; Perfil abre el menú; cerrar sesión; reabrir la app |
| **3. Ruta y lecciones** | `LearningPath`, `Lesson`; leer y escribir `lesson_progress` | Pruebas: estado de islas, escribir al abrir/terminar, fallo de red no bloquea la lectura | Ver el mapa con tu nivel; abrir cada lección; cerrar la app y comprobar que el progreso persiste |
| **4. Laboratorios** | `Labs`, `Lab` (terminal) e informe; leer/escribir `lab_progress` | Pruebas: objetivos, comando desconocido, "Finalizar" solo habilitado cuando corresponde (P6), guardado | Hacer los 6 labs; probar comando desconocido; ver el informe y el progreso |
| **5. Examen final** | `ExamIntro`, preguntas, resultados; `exam_attempts` | Pruebas: 0, 1 y 2 fallos, repetición, no revelar cuáles falló, guardado | Aprobar con 0 y 1 error y no aprobar; reintentar |
| **6. Cierre** | Comparación contra Figma pantalla por pantalla, informe `docs/superpowers/reports/modulo-3-diagnostico.md`, actualización de `CLAUDE.md`/`README`, `versionName` (propuesta: 2.1.0; **lo decide el grupo**) | Todo en verde; lint en 0 errores; diferencias deliberadas documentadas | Recorrido completo desde el registro |

**Pruebas (convenciones):** `MainDispatcherRule` y Turbine para ViewModels y eventos; `assertTrue`/`assertFalse`/`assertEquals` (nunca `assert()` suelto); relojes inyectables; *fakes* de repositorio con fallos configurables. No hay pruebas automáticas de las llamadas reales a Supabase (F-03 de la revisión, igual que hasta ahora).

## 11. Riesgos

- **R1 — Reglas sin definir.** Sin P3, P5, P6 y P8 no se puede implementar la Ruta, el informe de error ni el examen con fidelidad. Es el riesgo principal.
- **R2 — Examen falseable.** Las respuestas están en el APK y el cliente envía `correct_count`. Hoy no hay nada que dependa de eso; **si el módulo 4 añade ranking o XP, esto debe revisarse** (p. ej. corrección en una función SQL).
- **R3 — Simulación de terminal.** Solo se reconocen los comandos de Figma. Una persona que escriba otra cosa verá "no reconocido" (texto propuesto, fuera de Figma). Debe quedar claro que es una simulación.
- **R4 — Datos de Figma inconsistentes** (C3, C5, C6): se resuelven con la decisión del grupo, no en el código.
- **R5 — Muchos estados de Figma** (≈90 frames): el examen tiene 30 variantes `[estado …]` cuyo significado no está documentado (P8).
- **R6 — Sin conexión.** Progreso no guardado si no hay red; se muestra un aviso y se puede reintentar. Igual que el resto de la app.
- **R7 — Contenido en el APK.** Cambiar una lección o un lab requiere nueva versión (aceptado en el quiz).
- **R8 — Pausa del proyecto Supabase tras 7 días sin uso** (aceptada en `CLAUDE.md`).
- **R9 — Nada se ve en un teléfono desde aquí:** cada etapa depende de la prueba manual del usuario.

## 12. Diferencias deliberadas frente a Figma

| # | Diferencia | Motivo |
|---|---|---|
| F1 | "Tu nivel: Aprendiz" (mapa) se muestra con BAJO/MEDIO/ALTO | Decisión del equipo (C2). Redacción exacta: P2 |
| F2 | Social aparece atenuada y solo muestra un aviso; no hay pantallas de Social | Decisión del equipo (alcance) |
| F3 | El ícono de salida de Inicio conserva "Cerrar sesión" y "Mi cuenta" vive en la pestaña Perfil | Decisiones del módulo 2 |
| F4 | Racha (15) e insignias (3 de 9) siguen fijas | Sin regla definida |
| F5 | Los comandos de la terminal solo reconocen los de Figma | No se inventan comandos |
| F6 | Los textos nuevos ("Social llegará pronto", "comando no reconocido", mensajes de error de red) no están en Figma | Necesarios para que la UI no quede muda |
| F7 | El examen no revela qué preguntas se fallaron si no aprueba | Es lo que dice Figma (2486:249): **no es** diferencia, se anota para evitar dudas |

## 13. PREGUNTAS ABIERTAS para el grupo (no decididas)

| # | Pregunta | Por qué importa / fuente |
|---|---|---|
| P1 | ¿"Examen Final" se abre desde la tarjeta de Labs, desde la isla "Prueba Final", o desde las dos? | C7; Figma 2271:84 y 2266:72 |
| P2 | ¿Qué texto exacto muestra el mapa en vez de "Tu nivel: Aprendiz"? ¿"Tu nivel: MEDIO" (como el resultado del quiz) o "Bajo/Medio/Alto"? | Decisión de niveles; Figma 2266:72; `PlacementResultScreen.kt:84` |
| P3 | **¿Qué completa una isla** (ver la lección, terminar el laboratorio, o las dos)? **¿Cómo se desbloquea** la siguiente (¿en orden?), y **qué se ve bloqueado** en la lista de labs (en Figma 3 labs aparecen atenuados)? ¿Influye el nivel BAJO/MEDIO/ALTO en lo que se desbloquea o por dónde se empieza? | C3; Figma 2266:72, 2271:84; ningún documento lo define |
| P4 | El botón "COMENZAR" del plan de estudio (Figma 2017:160) ¿lleva a la Ruta? Hoy el resultado muestra "Continuar" hacia el menú. | `PlacementResultScreen`; Figma 2017:160 |
| P5 | "¡Subiste de nivel!" y "Nuevo contenido desbloqueado en tu ruta y laboratorios" al aprobar el examen: ¿qué cambia realmente? ¿Se actualiza BAJO→MEDIO→ALTO en `placement`? ¿Qué contenido nuevo existe? Figma no lo define. | Figma 2491:357, 2486:249 |
| P6 | ¿Un laboratorio con 0% cuenta como completado? ¿Qué lleva a la variante "(ERROR)" del informe, si "Finalizar laboratorio" parece requerir los 3 objetivos? ¿El título debe seguir diciendo "completado" en el error? | C5; Figma 2404:243, 2370:139 |
| P7 | **Inicio:** ¿qué hacen las tarjetas "Lección diaria", "Concepto nuevo" y "Prepárate con CiberMynd"? No hay pantallas ni definición. Propuesta (a decidir): "Lección diaria" abre la siguiente lección pendiente; las otras dos, "Próximamente". ¿Qué es CiberMynd? | C9, C10; Figma 49:15 |
| P8 | ¿Qué significan las variantes `[estado 1_1]`… `[estado F]` de las preguntas del examen (opción elegida, estado de la barra, selección final)? ¿Se muestra si la respuesta fue correcta durante el examen? Hoy solo se ve el resultado final. | Figma 2498:246 a 2498:498 |
| P9 | ¿Qué cuenta como "día de práctica" para la **racha real**? ¿Qué desbloquea cada **insignia**? Hasta que se defina siguen fijas (15 y 3 de 9). | `FixedProfileData.kt`; Figma 2287:100 |
| P10 | **Respuestas del examen:** solo la P1 tiene respuesta correcta indicada en Figma (C: `k7#mQ!9zRt$2`, frame 2498:512). Las de P2 a P6 las confirma el grupo (a primera vista, B, C, B, B, B, pero **no está en Figma**). | Figma 2491:243 a 2491:328, 2498:512 a 2498:572 |
| P11 | **Tiempo y reintentos del examen:** ¿hay límite de tiempo, de intentos o de espera entre intentos? Figma no lo dice. Hoy se propone sin límites. | Figma 2486:249, 2491:345 |
| P12 | **Textos:** ¿opción A (nuevo `strings_aprendizaje.xml`) u opción B (en el código)? | Sección 9; F-09 |
| P13 | ¿Se aprueba `0007` tal como está (3 tablas), o se prefiere guardar el progreso de otra forma (p. ej. una sola tabla `learning_progress`)? | `0007_progreso_aprendizaje.sql` |
| P14 | ¿La app se versiona como 2.1.0 al cerrar el módulo? | `CLAUDE.md` sección 1 |
| P15 | Pestaña **Perfil** de la barra: ¿abre el Menú de usuario (propuesta) o "Mi Perfil" directamente? | Sección 7; Figma 2068:30, 2068:51 |
