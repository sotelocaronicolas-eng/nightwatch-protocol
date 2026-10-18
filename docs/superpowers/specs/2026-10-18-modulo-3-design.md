# Módulo 3: Ruta de aprendizaje, lecciones, laboratorios y examen final: spec de diseño

Estado: **v2, con las decisiones del grupo del 2026-10-18 (sección 13).** Quedan propuestas por confirmar en la sección 14. No se programa nada hasta que el grupo las confirme y se apruebe `0007`.
Fecha: 2026-10-18. Rama: `modulo-3`. Figma: fileKey `wd3fQTzF5NqsfGfdR4I1lb` (una sola página, `0:1`).

Convención de este documento: cada afirmación lleva su **FUENTE** (archivo del repo, nodo de Figma `nombre (ID)` o "decisión del grupo P#"). Lo que no tiene fuente va marcado **SUPUESTO** o **PROPUESTA** y no está decidido.

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

- El **orden de Figma** (lecciones "MÓDULO 1 a 6 de 6", islas y preguntas del examen) es el de la tabla: Contraseñas, **Phishing, Malware**, Ingeniería Social, Redes Seguras, Firewall. La lista del grupo enumeraba Malware antes que Phishing; se sigue Figma (ver sección 4, C12).
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

**Sigue sin definir:** ver sección 14 (propuestas por confirmar). Racha real e insignias pasan al módulo 4 (P9).

## 4. Contradicciones e inconsistencias (estado actual)

| # | Hallazgo | Fuentes | Estado |
|---|---|---|---|
| C1 | La brief decía "ruta + labs = social"; docs y Figma los separan | `2026-09-23-auth-onboarding-design.md:15-19` | Resuelta: Social es módulo 4 |
| C2 | Nivel: "Aprendiz" (mapa), "Intermedio" (Perfil Figma) y BAJO/MEDIO/ALTO (código) | Figma 2266:72, 2068:51, 2017:126 | Resuelta: BAJO/MEDIO/ALTO; formato "Tu nivel: BAJO" (P2) |
| C3 | "3 de 7 islas" frente a 2 labs completados en la lista | Figma 2266:72, 2271:84 | Resuelta: una isla = lección vista **y** lab completado (P3). Los datos de Figma son un ejemplo |
| C4 | El plan de estudio de Figma lista otros 6 temas (con "Buenas Prácticas" y "Privacidad de Datos") | Figma 2017:160 | Resuelta: los 6 temas de las lecciones; el plan de estudio ya implementado no cambia |
| C5 | Informes "(ERROR)" con título "completado" y 0% | Figma 2404:243, 2370:139, 2400:144 | Resuelta: título "Laboratorio no completado" (P6); es diferencia deliberada (F8) |
| C6 | El informe de contraseñas muestra un tiempo ("300 Años" / "3 Horas") y los demás un porcentaje; además "3 Horas" no coincide con ninguna salida de la terminal ("menos de 1 segundo", "4 horas", "300 años", Figma 2374:303) | Figma 2374:336, 2400:144, 2374:303 | **Abierta como propuesta** (sección 14, Q2) |
| C7 | "Examen Final" aparece en Labs y como isla "Prueba Final" | Figma 2271:84, 2266:72 | Resuelta: se abre desde las dos, misma pantalla (P1) |
| C8 | "MÓDULO N de 6" usa "módulo" para un tema | Figma 2294:112 a 2294:202 | Abierta, solo lenguaje: se conserva el texto de Figma |
| C9 | Main Menu de Figma (ícono de salida, 3 tarjetas, sin "Mi cuenta") frente al del código (2 botones) | Figma 49:15; `MainMenuScreen.kt` | Resuelta: sección 7 |
| C10 | "CiberMynd" no aparece en ningún documento | Figma 49:15 | Resuelta: tarjeta atenuada con "Próximamente" (P7) |
| C11 | `modulo-3` ya existe en remoto y va 2 commits por delante de `b339374`; existe el worktree `worktree-auth-onboarding` | `git branch -vv` | Solo informativo |
| C12 | La lista del grupo da el orden "Contraseñas, Malware, Phishing…"; Figma ordena Phishing (2.º) y Malware (3.º) en lecciones, islas y examen | Figma 2294:130, 2294:148, 2266:72, 2491:260, 2491:277 | **Se sigue Figma.** El grupo debe avisar si quiere el orden inverso: cambia el desbloqueo en orden y el orden de las preguntas |
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
| 8 | Examen Final: introducción | `EXAMEN FINAL` 2486:249 | `ExamIntro` | Se abre desde la isla "Prueba Final" y desde Labs (P1) solo con las 6 islas completadas (P3); "X"; botón de inicio (**verificar en el diseño**: no sale en los textos exportados) | último intento | Funcional |
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

**Aviso "Próximamente" (PROPUESTA de implementación; el texto es propuesto, no de Figma).** Un solo mecanismo para tres elementos:
- Pestaña **Social**, tarjeta **"Concepto nuevo"** y tarjeta **"Prepárate con CiberMynd"**.
- Se dibujan **atenuados** (misma opacidad que las islas y labs bloqueados, como en 2271:84) y, al tocarlos, no navegan ni cambian de pestaña: aparece un **Snackbar "Próximamente"**. La pestaña activa no cambia. Un solo mensaje a la vez.
- Accesibilidad: descripción "…, próximamente" y estado "no disponible"; siguen siendo enfocables para que TalkBack anuncie el aviso.
- Implementación: el ViewModel emite el aviso por un `Channel` y la pantalla lo muestra con `SnackbarHost`; sin ruta nueva.
- Cambia respecto a la v1 de la spec: el texto era "Social llegará pronto"; ahora es "Próximamente" para los tres (P7: "igual que Social").

**Atenuado por bloqueo (islas, labs, examen):** al tocar un elemento bloqueado **PROPUESTA:** también un Snackbar corto, **"Completa la isla anterior"** para islas y labs, y **"Completa las 6 islas"** para el examen (textos propuestos; Figma no tiene el estado de toque sobre un bloqueado).

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
  - `lab_progress` **pierde la columna `accuracy` y el permiso y la política de `update`**: como un lab incompleto no se guarda (P6), todo lo guardado sería 100%, un dato sin información. La precisión que ve la persona se calcula en la app (sección 14, Q1). Si el grupo quiere guardar el mejor resultado, es una migración nueva (0008).
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
| D11 | **Versión (P14):** `versionName = "2.1.0"` al cerrar el módulo. **SUPUESTO:** `versionCode = 4` (el actual es 3) | P14 + supuesto |

## 10. Etapas de implementación (un commit por etapa; cada una valida con `./gradlew assembleDebug testDebugUnitTest lintDebug`, y `compileDebugAndroidTestKotlin` si se tocan pruebas instrumentadas)

Nada se ve en un teléfono desde aquí: la columna "Prueba en el teléfono" la hace el usuario.

| Etapa | Contenido | Criterios de aceptación | Prueba en el teléfono |
|---|---|---|---|
| **0** (usuario) | Revisar y ejecutar `0007`; correr sus consultas de verificación | Todas dan lo esperado; `anon` sin filas; sin claves foráneas hacia tablas anteriores | Ejecutar y pegar los resultados |
| **1. Datos y contenido** | Modelos (`LearningTopic` con los 6 temas, `ExamQuestion`), JSON de contenido con **solo** lo de Figma y las respuestas de D9, `strings_aprendizaje.xml` base, repositorios con interfaz y *fakes*, `SupabaseLearningProgressRepository`, motor de terminal, `LearningProgress`, `AppContainer` | Pruebas de unidad: motor de terminal (3 objetivos, comando desconocido, orden libre), precisión y tiempo estimado (sección 14), corrección del examen (0 a 6 fallos y el borde 1/2 errores), derivación de progreso (isla completa = lección y lab; desbloqueo en orden; examen con 6 islas; "lección diaria"), mapeo de errores | Ninguna (sin UI) |
| **2. Contenedor, barra e Inicio** | `Home(tab)`, barra, avisos "Próximamente", ícono de salida, Perfil → `UserMenu`; `PostAuthGate` → `Home(Inicio)` y quiz → `Home(Ruta)` | Pruebas de ViewModel con Turbine: cambiar de pestaña; Social, "Concepto nuevo" y CiberMynd emiten el aviso y no navegan; "Lección diaria" abre la siguiente pendiente; cerrar sesión; navegación existente sigue pasando | Cambiar de pestaña; tocar los 3 elementos atenuados; Perfil abre el menú; cerrar sesión; terminar el quiz y llegar a Ruta |
| **3. Ruta y lecciones** | `LearningPath`, `Lesson`; leer/escribir `lesson_progress` | Pruebas: estado de islas y desbloqueo, "Tu nivel: …", escribir una vez, fallo de red | Ver el mapa y tu nivel; abrir islas en orden; el bloqueo de las siguientes; cerrar la app y comprobar que persiste |
| **4. Laboratorios** | `Labs`, `Lab` (terminal) e informe; `lab_progress` | Pruebas: 3/3 = éxito y se guarda; menos = "Laboratorio no completado" y no se guarda; "Finalizar" siempre habilitado; precisión | Hacer los 6 labs; finalizar con 0, 1, 2 y 3 objetivos; ver que el lab no se marca si no se completa |
| **5. Examen final** | `ExamIntro`, preguntas, resultados; `exam_attempts` | Pruebas: 0, 1 y 2 fallos; reintentos sin límite; no revelar aciertos durante el examen; no revelar cuáles falló; **no se escribe nada en `placement`** | Bloqueado con <6 islas; aprobar con 0 y 1 error; no aprobar; reintentar; nivel de Mi Perfil intacto |
| **6. Cierre** | Comparación contra Figma pantalla por pantalla, informe `docs/superpowers/reports/modulo-3-diagnostico.md`, actualización de `CLAUDE.md` y `README`, `versionName 2.1.0` | Todo en verde; lint en 0 errores; diferencias documentadas | Recorrido completo desde el registro |

**Pruebas (convenciones):** `MainDispatcherRule` y Turbine; `assertTrue`/`assertFalse`/`assertEquals` (nunca `assert()` suelto); relojes inyectables; *fakes* con fallos configurables. Sin pruebas automáticas de las llamadas reales a Supabase (F-03).

## 11. Riesgos

- **R1 — Reglas por confirmar.** Quedan las propuestas de la sección 14; sin ellas la precisión y el tiempo estimado no son definitivos. Riesgo ya menor que en la v1.
- **R2 — Examen falseable.** Respuestas en el APK; el cliente envía `correct_count`. Si el módulo 4 añade ranking o XP, revisar (p. ej. corrección en una función SQL).
- **R3 — Terminal simulada.** Solo reconoce los comandos de Figma; debe quedar claro que es una simulación.
- **R4 — Datos de Figma inconsistentes** (C6, C12, C13, C14): se resuelven con las decisiones y propuestas; la comparación final con Figma las anota.
- **R5 — Muchos estados de Figma** (≈90 frames). Los 30 estados `[estado …]` del examen son visuales (P8).
- **R6 — Desbloqueo y examen solo en la app.** Un cliente modificado puede abrir el examen sin las 6 islas. Sin impacto mientras no haya datos sociales (R2).
- **R7 — Sin conexión.** Progreso sin guardar si no hay red; se avisa y se puede reintentar.
- **R8 — Contenido en el APK.** Cambiar contenido requiere versión nueva.
- **R9 — Pausa del proyecto Supabase tras 7 días sin uso** (aceptada).
- **R10 — Nada se ve en un teléfono desde aquí:** cada etapa depende de la prueba manual.
- **R11 — "Lección diaria" sin lección pendiente:** ver Q3 (sección 14).

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
| F14 | **Nueva (P2/P6):** la precisión del informe muestra también 33% y 67% (en Figma solo hay 100% y 0%), y el tiempo estimado del informe de contraseñas sigue la regla de la sección 14, Q2 (el "3 Horas" de 2400:144 no coincide con ninguna salida de la terminal) | PROPUESTA, por confirmar |

## 13. Decisiones del grupo (2026-10-18)

| # | Pregunta | Decisión del grupo | Qué cambia en la spec |
|---|---|---|---|
| P1 | ¿Desde dónde se abre el Examen Final? | Desde la isla "Prueba Final" y desde la tarjeta de Labs (misma pantalla 2486:249) | Sección 5 (filas 3, 5 y 8); C7 resuelta |
| P2 | ¿Qué texto muestra el mapa en vez de "Aprendiz"? | "Tu nivel: <BAJO/MEDIO/ALTO>" con el mismo formato del resultado del quiz | Sección 5 (fila 3), F1; "Sin evaluar" como SUPUESTO si no hay resultado |
| P3 | ¿Qué completa una isla, cómo se desbloquea, cuándo se abre el examen, influye el nivel? | Isla completa = lección vista **y** lab completado. Desbloqueo en orden. Examen al completar las 6 islas. El nivel no cambia lo que se desbloquea: todos empiezan en la isla 1 | Secciones 5, 6, 8 (estado derivado), 10; F11; C3 resuelta |
| P4 | ¿Adónde llevan "COMENZAR" y "Continuar" del quiz? | A Inicio con la pestaña Ruta seleccionada | Sección 5 (conexión), 7 (`Home(tab)`), 9 (D1), 10 (etapa 2); F10 |
| P5 | ¿Qué hace aprobar el examen? (CERO RIESGOS) | **No cambia el nivel.** No se escribe en `placement` ni en ninguna tabla anterior; "aprobado" solo en `exam_attempts`. Texto de aprobado sin "Subiste de nivel" ni promesa de contenido | Secciones 1, 5 (fila 10), 8, 10 (etapa 5); F7 (texto propuesto) y C15 |
| P6 | ¿Cómo se finaliza un lab y qué pasa si faltan objetivos? | "Finalizar" siempre habilitado. 3/3: éxito y lab completado. Menos: informe "Laboratorio no completado", sin marcar. Precisión y tiempo: propuesta para confirmar | Secciones 5 (filas 6 y 7), 8 (`lab_progress` sin `accuracy` ni `update`), 10 (etapa 4); F8, F9, F14; propuestas Q1 y Q2 |
| P7 | ¿Qué hacen las tarjetas de Inicio? | "Lección diaria" abre la siguiente lección pendiente. "Concepto nuevo" y "Prepárate con CiberMynd" atenuadas con "Próximamente", igual que Social | Secciones 5 (fila 2), 7, 12 (F6); propuesta Q3 |
| P8 | ¿Qué significan las variantes `[estado …]` del examen? | Estados visuales (opción elegida y barra de progreso). No se indica si una respuesta es correcta durante el examen | Sección 5 (fila 9), R5; F12 |
| P9 | ¿Racha e insignias? | Siguen fijas (15 días, 3 de 9). Sus reglas se definen en el módulo 4 | Secciones 3 y 6; F4. `2026-10-18-modulo-4-social-analisis.md` conserva la pregunta de la racha como del módulo 4 |
| P10 | ¿Respuestas correctas del examen? | Verificadas por el grupo contra Figma: P1 = C (2498:512), P2 = B, P3 = C, P4 = B, P5 = B, P6 = B | Sección 9 (D9) |
| P11 | ¿Límites del examen? | Sin límite de tiempo, de intentos ni de espera | Secciones 5 (fila 11) y 8 |
| P12 | ¿Textos? | Opción A: `strings_aprendizaje.xml` con prefijo `learning_` | Sección 9 (D10), etapas 1 y 2 |
| P13 | ¿Se aprueba `0007` con 3 tablas? | Se mantienen las 3 tablas, sujeto a revisión del SQL | Sección 8; `0007` ajustado (quita `accuracy` y `update` de `lab_progress`, renombra `REDES_SEGURAS`) |
| P14 | ¿Versión al cerrar el módulo? | 2.1.0 | Sección 9 (D11); `versionCode 4` como SUPUESTO |
| P15 | ¿Qué abre la pestaña Perfil? | El Menú de usuario | Secciones 5 y 7 |

**Corrección a la sección 1 (del grupo):** los temas son 6 y separados, con las etiquetas de Figma (p. ej. "Redes Seguras"). Verificado contra Figma en la tabla de la sección 1.

## 14. Propuestas por confirmar (no decididas)

| # | Propuesta | Por qué |
|---|---|---|
| Q1 | **Precisión del lab (P6):** `precisión = round(100 × objetivos cumplidos / 3)`. Todos los labs tienen exactamente 3 objetivos (Figma, 6 terminales). Resultados posibles: 0%, 33%, 67% y 100%. El informe de éxito muestra 100% y el de "no completado" el valor que corresponda. Se calcula en la app y no se guarda. Cada objetivo se marca ✅ si se cumplió y ❌ si no (Figma solo muestra todo ✅ o todo ❌; mezclarlos es extensión). Los textos descriptivos del informe de error ("No confirmaste que…") se conservan | Figma solo muestra 100% y 0% (2273:131, 2404:243) |
| Q2 | **Tiempo estimado del lab de contraseñas (P6):** el informe muestra el tiempo de **la contraseña más fuerte que la persona probó**, tomado de las salidas de Figma (2374:303): `crack 123456` → "menos de 1 segundo", `crack Nightwatch2024` → "4 horas", `crack N!ghtW4tch#7kQ2` → "300 años". "Más fuerte" sigue el orden de los objetivos (débil < patrón < robusta). Si no probó ninguna: "—". En éxito Figma muestra "300 Años" (2374:336): coincide. En no completado, Figma muestra "3 Horas" (2400:144), que **no coincide** con ninguna salida (el patrón dice "4 horas"): se propone mostrar la salida real. La precisión de este lab sigue Q1; el informe de Figma no muestra porcentaje, así que se muestra el tiempo y se omite el %. Pendiente de confirmar | C6 |
| Q3 | **"Lección diaria" (P7):** la "siguiente lección pendiente" es la de la primera isla desbloqueada cuya lección **no se ha visto**. Si no hay ninguna (todas vistas, o la lección ya vista pero falta el lab), la tarjeta muestra un Snackbar **"No tienes lecciones pendientes"** (texto propuesto). Pendiente de confirmar | Evita abrir una lección bloqueada |
| Q4 | **Acceso a un lab (P3):** un lab es accesible cuando su isla está desbloqueada, **sin exigir haber visto antes la lección** (la lección tiene el botón "Ir al laboratorio →" pero la lista de Labs también ofrece "Comenzar →"). La isla se completa solo con ambos | P3 no lo aclara |
| Q5 | **Contador "N de 7 islas":** cuenta las 6 islas completas más la Prueba Final si hay un intento aprobado | Figma 2266:72 cuenta 7 |
| Q6 | **Textos propuestos de bloqueo** ("Completa la isla anterior", "Completa las 6 islas") y de la sección 7 | Figma no tiene el estado |
| Q7 | **Orden de los temas:** Figma (Phishing antes que Malware) frente a la lista del grupo (C12) | Cambiaría el orden de desbloqueo y de preguntas |
| Q8 | **Botón de inicio del examen y de la pantalla de resultado:** no aparecen en los textos exportados de 2486:249; verificar en el diseño | Detalle visual |
