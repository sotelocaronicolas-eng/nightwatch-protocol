# Módulo 3: Ruta de aprendizaje, lecciones, laboratorios y examen final — diagnóstico de cierre

Rama: `modulo-3` (35 commits sobre `main` antes de los cuatro del cierre documental —este informe, `CLAUDE.md` y `README.md`, las guías de Supabase y la versión—; 96 archivos y +13.170 / −157 líneas medidos en ese punto; sin push, `main` sin tocar).
Versión al cerrar: **2.1.0** (`versionCode` 4). Spec: `docs/superpowers/specs/2026-10-18-modulo-3-design.md` (con su sección 16, "Cierre").
Figma: fileKey `wd3fQTzF5NqsfGfdR4I1lb`.

**Qué se verificó y qué no.** Todo lo de este informe se verificó con compilación, pruebas unitarias en JVM con *fakes*, `lintDebug`,
`compileDebugAndroidTestKotlin` y comparación del código con el Figma (herramientas de lectura de Figma, sin abrir la app).
**Nada de lo visual se comprobó en un teléfono desde el repositorio** y **no se probó contra un Supabase real** desde aquí: eso lo
hace el grupo con la lista de la sección 10 y el guion de la sección 11. Las cifras salen de comandos ejecutados el 2026-10-18; lo que
no se pudo comprobar está marcado "no verificado".

## 1. Alcance entregado frente a la spec

**Dentro y entregado:**

| Parte | Qué se entregó |
|---|---|
| Barra inferior | Inicio, Ruta, Labs, Social (atenuada, "Próximamente") y Perfil (abre el Menú de usuario). Reemplaza al Main Menu provisional |
| Inicio | Chapa con nombre, avatar, nivel y **racha real**; "Lección diaria" (abre la siguiente lección o dice "No tienes lecciones pendientes"); "Concepto nuevo" y "Prepárate con CiberMynd" atenuadas |
| Ruta | Mapa de 6 islas y la Prueba Final con estados derivados del progreso, contador "N de 7 islas", nivel BAJO/MEDIO/ALTO |
| Lecciones | 6 lecciones (contenido de Figma en JSON); la lección se registra al abrirse (Q11) |
| Laboratorios | Lista de 6 labs y el Examen Final; 6 terminales simuladas; informe de éxito o de "Laboratorio no completado" con precisión (o tiempo estimado en Contraseñas); el lab se registra solo con 3 de 3 |
| Examen Final | Introducción, 6 preguntas (tocar avanza), resultado aprobado con 0 o 1 error y no aprobado; un intento registrado por resultado; sin cambiar el nivel |
| Progreso | Derivado de `lesson_progress`, `lab_progress` y `exam_attempts` (islas, desbloqueo, "lección diaria", contador) y releído al volver a cada pantalla |
| Racha e insignias | Racha actual real (Inicio, Menú de usuario y Mi Perfil) y las 9 insignias reales en Logros e Insignias (Q13) |
| Datos | Migración `0007` (3 tablas, aplicada a mano), repositorio de progreso con fechas, contenido en JSON |

**Fuera del alcance (módulo 4):** todo **Social** (17 pantallas de Figma `2440:163` a `2451:209`). Solo existe la pestaña atenuada.
"Concepto nuevo" y "Prepárate con CiberMynd" también quedan como visuales ("Próximamente").

**Cambios que no estaban en la spec original** (pedidos por el grupo durante el módulo): racha e insignias reales (Etapa 5.5, reemplaza a P9),
condición de "Leyenda NightWatch" (Etapa 6), nombres completos de las insignias (Etapa 5.6) y el arreglo de la palomita del Lab.

## 2. Pantallas: nodo de Figma, ruta, tipo y resultado de la revisión estática

"Funcional" = lee o escribe datos reales o aplica reglas reales. "Visual" = se ve como Figma sin efecto.
Resultado de la revisión estática (Etapa 6, comparando el código con `get_design_context` de cada nodo):
**coincide** = sin diferencias objetivas; **corregido** = se ajustó una medida; **F-nn** = diferencia documentada;
**no verificado** = no se pudo comparar en esta pasada.

| Pantalla | Nodo(s) de Figma | Ruta en la app | Tipo | Resultado |
|---|---|---|---|---|
| Barra inferior | `BottomNav` 2264:116 (en 49:15, 2266:72, 2271:84) | componente `NightWatchBottomBar` | Funcional | Coincide (altura, radio, emojis, etiquetas, activo en oro, inactivo al 55 %). Social atenuada: SUPUESTO E2-1 |
| Inicio | 49:15 | `Home(Inicio)` | Funcional (2 tarjetas visuales) | Coincide; F16 (nivel y línea de lección), F20 (nombre en Bold, no hay ExtraBold) |
| Ruta (mapa) | 2266:72 | `Home(Ruta)` | Funcional | **Corregido** (barra de progreso a 62 dp, 2266:79). Coincide el resto (posiciones, 92/108 dp, rótulos). F1, F17; E3-1 (rótulos a 98 dp) |
| Lección (6) | 2294:112 (revisada), 2294:130, 2294:148, 2294:166, 2294:184, 2294:202 | `Lesson(topic)` | Funcional | 2294:112 coincide (títulos, 96 dp, tarjetas, botón). Las otras 5 usan el mismo componente y su texto sale del JSON de la Etapa 1; **no se re-comparó cada una en esta pasada**. F18, F19 |
| Labs (lista) | 2271:84 | `Home(Labs)` | Funcional | Coincide (118 dp, borde 2 dp, chip, botón); E4-1 a E4-4 |
| Terminal (6 × 2) | 2374:273/303, 2374:357/387, 2383:141/173, 2383:206/238, 2299:118/148, 2383:271/303 | `Lab(topic)` | Simulación local | 2374:273/303 coincide; las otras 5 tienen la misma estructura (metadatos, Etapa 4A). F5, F22 (campo a 48 dp), E4-6, E4-7 |
| Informe de lab (6 × éxito/no completado) | 2273:131/2404:243, 2374:336/2400:144 (revisados); 2299:181/2370:139, 2348:156/2404:261, 2348:201/2404:279, 2374:420/2404:154 | `LabReport(topic, completedObjectives)` | Funcional | Coincide (trofeo 🏆 o roto, StatBox, lista, 2 botones). F8, F14, F21, F23 |
| Examen: introducción | 2486:249 | `ExamIntro` | Funcional | **Corregido** (círculo a 104 dp). F7 (texto de "¿Qué gano?") |
| Examen: preguntas (6) | 2491:243, 2491:260, 2491:277, 2491:294, 2491:311, 2491:328; variantes `[estado …]` 2498:246… | `Exam` | Funcional | **Corregido** (texto a 272 dp y caja de 60: opciones en 332). F12; E5-2 |
| Examen aprobado (0 errores) | 2491:357 | `ExamResult` | Funcional | Coincide salvo el texto de F7 y su tamaño (22 sp) |
| Examen aprobado (1 error, 6 variantes) | 2498:512 a 2498:572 (revisada 2498:512) | `ExamResult` | Funcional | Coincide (tema y correcta de la pregunta fallada); F7 |
| Examen no aprobado | 2491:345 | `ExamResult` | Funcional | Coincide; F21, F22, F26 (3 a 4 dp, estimado) |
| Logros e Insignias | 2287:100 | `Achievements` | Funcional | Coincide (tarjetas 116 × 130, 3 columnas, estados); F24, F25 |
| Resultado del quiz → Ruta | 2017:160, 2051:268 | `PlacementResultRoute` | Funcional (módulo 2) | F10. **No verificado** en esta pasada (es del módulo 2) |
| Social (17 pantallas) | 2440:163 a 2451:209 | — | Fuera del módulo | n/a (módulo 4) |

## 3. Decisiones del grupo (resumen)

**Primera ronda (P1 a P15, 2026-10-18):** P1 el Examen Final se abre desde la isla "Prueba Final" y desde Labs. P2 el mapa dice "Tu nivel: BAJO/MEDIO/ALTO"
("Sin evaluar" sin resultado). P3 una isla se completa con lección vista **y** lab completado; desbloqueo en orden; el examen con las 6 islas; el nivel no
cambia lo que se desbloquea. P4 "Continuar" del quiz lleva a Home con la Ruta. P5 aprobar **no cambia el nivel** y no se escribe en `placement` ni en
tablas anteriores. P6 "Finalizar" siempre habilitado; 3/3 = éxito y lab completado, menos = "Laboratorio no completado" sin registrar. P7 "Lección diaria" abre la
siguiente lección; "Concepto nuevo" y CiberMynd atenuadas. P8 (corregida en la Etapa 5) las variantes `[estado …]` del examen son rutas del prototipo, no estados
visuales. P9 **(reemplazada por Q13)** racha e insignias fijas. P10 respuestas del examen verificadas (C, B, C, B, B, B). P11 examen sin límite de tiempo ni de
intentos. P12 textos en `strings_aprendizaje.xml` (prefijo `learning_`). P13 las 3 tablas de `0007`. P14 versión 2.1.0. P15 Perfil abre el Menú de usuario.

**Segunda ronda (Q1 a Q13):** Q1 precisión = `round(100 × objetivos / 3)`, ✅/❌ por objetivo. Q2 tiempo estimado de Contraseñas = el de la contraseña más fuerte probada
(de las salidas de la terminal) o "—". Q3 "Lección diaria" = primera isla desbloqueada con lección sin ver; si no hay, "No tienes lecciones pendientes". Q4 un lab
es accesible con su isla desbloqueada, sin exigir la lección. Q5 el contador "N de 7" cuenta las 6 islas y la Prueba Final aprobada. Q6 textos de bloqueo
("Completa la isla anterior", "Completa las 6 islas"). Q7 orden de Figma (Contraseñas, Phishing, Malware, Ingeniería Social, Redes, Firewall). Q8 "Comenzar Examen →".
Q9 `lab_progress` sin `accuracy`. Q10 un solo "Próximamente". **Q11** la lección se registra al abrirse. **Q12** tocar una opción la registra y avanza (sin "Siguiente").
**Q13** racha e insignias reales, derivadas del progreso (sección 15 de la spec); condición de "Leyenda NightWatch" elegida por el grupo.

## 4. Diferencias deliberadas frente a Figma (consolidadas)

| # | Diferencia | Motivo |
|---|---|---|
| F1 | "Tu nivel: Aprendiz" → BAJO/MEDIO/ALTO (o "Sin evaluar") | P2 |
| F2 | Social atenuada con "Próximamente"; sin pantallas de Social | Alcance (módulo 4) |
| F3 | El ícono de salida de Inicio cierra sesión; "Mi cuenta" vive en Perfil | Módulo 2, P15 |
| F4 | ~~Racha e insignias fijas~~ **ya no aplica**: son reales | Q13 |
| F5 | La terminal solo reconoce los comandos de Figma | No se inventan comandos |
| F6 | "Concepto nuevo" y CiberMynd atenuadas con "Próximamente" | P7 |
| F7 | Aprobado sin "¡Subiste de nivel!" ni promesa de contenido; textos propuestos | P5 |
| F8 | Informe con menos de 3 objetivos: "Laboratorio no completado" | P6 |
| F9 | "Finalizar laboratorio" siempre habilitado | P6 |
| F10 | El botón del resultado del quiz conserva "Continuar" y lleva a Ruta | P4 |
| F11 | Bloqueos por progreso real, no los datos de ejemplo de Figma | P3 |
| F12 | Sin indicación de acierto en el examen; sin opción elegida ni barra de progreso en Figma | P8, Q12 |
| F13 | Textos nuevos que Figma no tiene (avisos, errores de red) | Necesarios |
| F14 | Precisión con 33 % y 67 %; tiempo estimado solo de salidas reales | Q1, Q2 |
| F15 | "esta es una imitación" (Figma: "este es una imitación") | Corrección pedida por el grupo |
| F16 | Chapa de Inicio con línea de nivel; "Lección diaria" con línea de estado | Etapa 2 |
| F17 | El contador sale del progreso real, no de "3 de 7" | P2, Q5 |
| F18 | La lección se registra al abrirse | Q11 |
| F19 | Tarjetas de la lección con alto mínimo de 128 dp que crecen | Texto largo |
| **F20** | **Nueva.** Nombre de la chapa en Inter Bold (Figma ExtraBold) | No hay fuente ExtraBold en el proyecto |
| **F21** | **Nueva.** Botones claros con texto oscuro (Figma, en varios frames, blanco sobre gris claro) | Legibilidad |
| **F22** | **Nueva.** Campo de comando y "Enviar" a 48 dp (Figma 44); líneas tocables de "no aprobado" a 48 dp | Tamaño táctil mínimo |
| **F23** | **Nueva.** Informes de lab con 20 / 13 / 14 sp en los seis (Figma mezcla 22/14/13 y 20/13/14) | E4B-1 |
| **F24** | **Nueva.** Logros: nombre a 12 sp (Figma 11) y tarjeta de 130 dp como mínimo, con hasta 3 líneas | Nombres cortados en el teléfono |
| **F25** | **Nueva.** "Leyenda NightWatch" y los emojis de las insignias desbloqueadas no están en Figma | Sección 15 de la spec |
| **F26** | **Nueva.** "Examen no aprobado": unos 3 a 4 dp de desfase (estimado, no medido) | Alturas de texto; ver propuesta en la spec |

**Diferencias nuevas halladas en la revisión de la Etapa 6** (F20 a F26) y su propuesta: F20 conseguir el archivo Inter ExtraBold si el grupo quiere el peso exacto;
F21 mantener (legibilidad); F22 mantener (accesibilidad); F23 unificar (ya unificado); F24 mantener; F25 el grupo confirma emojis y condición; F26 medir en el teléfono.

**Contradicciones de Figma con decisiones del grupo:** ninguna nueva. **Accesibilidad de color (no se cambió, es diseño de Figma):** el texto blanco sobre `#94A6A7`
(tarjetas de lección y de la introducción, chapa, tarjeta de progreso) tiene un contraste aproximado de **2,5:1** y el oro `#E3B54F` sobre `#2C595D` en texto pequeño
(11 sp) unos **4,1:1**, por debajo de 4,5:1. Propuesta: oscurecer el gris de las tarjetas o usar texto `#162523` sobre ellas (calculado, no medido con herramienta).

**Pantallas "no verificado" en esta pasada:** las lecciones 2 a 6 (comparación fina), las pantallas de "aprobado con 1 error" distintas de 2498:512, y la pantalla de
resultado del quiz (módulo 2). Ninguna llamada a Figma falló.

## 5. Supuestos pendientes de confirmación del grupo

Lista corta (la completa, con cada identificador, está en la spec):

- **Q11** la lección se marca vista al abrirse (no al terminarla). Decisión del grupo, pero conviene confirmarla con el uso real.
- **Emojis de las insignias** desbloqueadas distintas de 🥇, 🔥 y 🔑 (🎣 🌐 🌟 📞 🎓): Figma solo dibuja 🔒 (E55-2, F25).
- **Regla de Leyenda NightWatch:** 6 islas completadas y un examen con 6 aciertos (E55-1, elegida por el grupo).
- Racha: día de práctica = lección vista, lab completado o intento de examen; repetir no suma día (límite, sección 8).
- Racha 0: "Racha Activa de 0 días 🔥" (E55-3).
- Textos propuestos del aprobado y de la introducción (F7, E5-4) y todos los avisos y errores de red (E2-5, E3-7, E4-5, E4B-6, E5-6, E55-5).
- Destinos de los botones del informe y del resultado ("Reintentar" = repetir desde cero; "Volver a la ruta" = Ruta) (E4B-3, E5-5).
- El campo de comando conserva su texto tras "Enviar" (E4-8); los objetivos se cumplen en cualquier orden (E1-2); normalización de la terminal (E1-1).
- Fuente monoespaciada del sistema en la terminal (E4-6); `X` cierra sin diálogo y sin guardar en lab y examen.
- "Lección diaria" no menciona un lab pendiente (Q3; ver la limitación de la sección 8 y su propuesta).
- Íconos de las islas bloqueadas de Redes y Firewall (E3-3, E6-5).

## 6. Modelo de datos y seguridad

**Migración `0007_progreso_aprendizaje.sql`** (aplicada a mano por el usuario; el archivo conserva el encabezado "BORRADOR" tal como se aplicó y no se edita):

| Tabla | Clave | Columnas | Escribe el cliente |
|---|---|---|---|
| `lesson_progress` | (`user_id`, `topic`) | `topic` ∈ los 6 temas; `completed_at` (servidor) | `INSERT (user_id, topic)` y `SELECT` |
| `lab_progress` | (`user_id`, `lab`) | `lab` ∈ los 6 temas; `completed_at` (servidor) | `INSERT (user_id, lab)` y `SELECT` |
| `exam_attempts` | `id` identity | `correct_count` 0–6; `passed` **generada** (`>= 5`); `taken_at` (servidor) | `INSERT (user_id, correct_count)` y `SELECT` |

- **RLS** activo en las tres, solo el dueño: `(select auth.uid()) = user_id` en `select` e `insert` (6 políticas, ninguna de `update` ni `delete`).
- **GRANT** solo a `authenticated`, por columna; `revoke all` previo a `anon` y `authenticated`; sin `TRUNCATE`, `REFERENCES` ni `TRIGGER`; `anon` sin acceso.
- Ninguna tabla tiene clave foránea hacia tablas de módulos anteriores (solo `auth.users`): **aprobar el examen no puede tocar `placement`**.
- Registro **idempotente** de lección y lab: `upsert` que ignora duplicados (`INSERT … ON CONFLICT DO NOTHING`), que solo necesita `INSERT`. El intento de examen es un `INSERT` simple.
- La fila del usuario sale **siempre de la sesión**; los errores se mapean a `DataAccessException`; ninguna pantalla ni ViewModel importa Supabase (comprobado con `grep`; hay una prueba que lo verifica para el examen).

**Verificaciones a a g de `0007`** (al final del archivo SQL): a) RLS activo; b) 6 políticas, solo `authenticated`; c) privilegios de tabla; d) privilegios por columna;
e) `passed` generada (`attgenerated = 's'`); f) claves foráneas solo hacia `auth.users`; g) valores permitidos de `topic` y `lab`. **Las ejecutó el usuario a mano y dieron el resultado
esperado** (spec, Etapa 0); los resultados detallados no se guardaron en el repositorio.

**Pruebas contra Supabase hechas en el teléfono:** **no hay registro en el repositorio** de que se hayan hecho las comprobaciones de `lesson_progress`, `lab_progress`,
`exam_attempts` (sin duplicados, con `passed` verdadero y falso) ni de que `placement` no cambia tras aprobar. Quedan como casillas de la sección 11 para que el grupo las marque.
Desde el repositorio solo se probó con *fakes* y sin red (mapeo de filas y fechas, mapeo de errores).

## 7. Pruebas y validación

Validación final (2026-10-18): `./gradlew assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL**.

| Concepto | Resultado |
|---|---|
| `testDebugUnitTest` | **951 pruebas, 0 fallos, 0 omitidas** (89 archivos `.kt` en `src/test`, con *fakes* y apoyos) |
| `lintDebug` | **0 errores, 36 advertencias** (ver abajo) |
| `compileDebugAndroidTestKotlin` | Compila (12 archivos instrumentados; **no se ejecutaron**, necesitan dispositivo) |
| `assembleDebug` | Compila |

**Pruebas por etapa** (línea base antes del módulo: 601):

| Etapa | Total | Nuevas |
|---|---|---|
| 1 Datos y contenido | 677 | 76 |
| 2 Home, barra e Inicio | 705 | 28 |
| 3 Ruta y lecciones | 751 | 46 |
| 4A Labs y terminal | 791 | 40 |
| 4B Informes, guardado y progreso | 829 | 38 |
| 5 Examen final | 878 | 49 |
| 5.5 Insignias y racha reales | 946 | 68 (netas: se quitó `AchievementsDataTest`) |
| 6 Leyenda NightWatch | 951 | 5 |

**Advertencias de lint (36):** 9 `UnusedResources` (colores de plantilla y los íconos del lanzador), 7 `GradleDependency` y 6 `NewerVersionAvailable` (versiones más nuevas; las
dependencias no se actualizan sin acuerdo, `CLAUDE.md`), 3 `UseKtx` y 2 `ExifInterface` (módulo 2, avatar), 3 `IconXmlAndPng` (lanzador), 2 `VectorRaster` y 1 `VectorPath`, 1 `RedundantLabel`,
1 `InlinedApi` (notificaciones, módulo 2) y 1 `AndroidGradlePluginVersion`. **Del módulo 3 quedan 2:** `VectorRaster` y `VectorPath` de `img_route_path.xml` (el trazo punteado de la
Ruta, vector de 6.387 caracteres); no es trivial (habría que simplificar el trazo) y se deja. Antes de la Etapa 6 eran 43; se corrigieron 7 del módulo 3 (4 `MissingKeepAnnotation` y 3 `Typos`).

**Higiene y revisión de código (Etapa 6):** sin `TODO` ni `FIXME`; "Próximamente" solo en Social y en las dos tarjetas de Inicio; **sin textos de UI sin mover** a `strings_aprendizaje.xml`
en el módulo 3 (los íconos de emoji y el "…" también salieron); sin pruebas sin aserciones; sin `assert()` suelto. Código sin uso dudoso (se deja y se reporta): la condición
`AvailableFromModule4` (la usará el módulo 4) y `LabsCompletedAtLeast` (sirve para cambiar una insignia editando una línea); ambas tienen pruebas.
**Accesibilidad básica:** íconos accionables con `contentDescription` ("Cerrar lección", "Cerrar laboratorio", "Cerrar examen", "Cerrar sesión"); objetivos del Lab con ícono ✓ y descripción "cumplido/pendiente";
islas y tarjetas con estado hablado; el estado nunca depende solo del color (candado, ✅, ✓, texto). Se corrigió el tamaño táctil de tres elementos a 48 dp (F22). Se reporta, sin cambiar: el contraste de color de Figma (sección 4) y
que una isla completada y una disponible se distinguen visualmente por el color y el aro (además de la descripción hablada).

**Revisión de secretos (Etapa 6): sin hallazgos.** Se buscaron `sb_publishable`, `sb_secret`, `service_role`, `eyJ…`, `AIza…`, `client_secret`, claves privadas y claves de Brevo en los archivos versionados y en
`git diff main...HEAD`: solo hay menciones de los **nombres** de las claves en documentos, el marcador `sb_publishable_TU_CLAVE` y un valor falso de prueba (`sb_publishable_x`). `local.properties`, `google-services.json` y `CLAUDE.local.md`
están en `.gitignore` y no están versionados; no hay `.jks`, `.keystore` ni `.env` versionados.

## 8. Limitaciones conocidas

- **Examen corregido en el cliente (R2).** Las respuestas viajan en el APK y se envía `correct_count`; un cliente modificado podría falsear un intento. Aceptable sin ranking (R6 igual para el desbloqueo en orden).
- **Racha:** repetir una lección o un lab ya registrado no genera fila nueva, así que **no suma un día**; quien repasa lo mismo cada día verá la racha en 0 pasados dos días. Resolverlo exige una tabla de actividad (propuesta `0008`, no creada).
- **Sin modo sin conexión:** guardar sin internet falla como "sin conexión" (hay "Reintentar guardado"); un lab o examen cuyo guardado falla y se abandona hay que repetirlo (E4B-9).
- **Intento de examen no idempotente:** si el guardado expira pero llegó, el reintento deja una fila repetida (E5-8); no afecta a la corrección.
- **E3-10:** abrir una lección bloqueada no se impide en la base de datos ni en el ViewModel de la lección (solo la Ruta y la tarjeta de Inicio); sin enlaces profundos no hay otra puerta.
- **Pausa del proyecto Supabase a los 7 días** sin actividad (plan gratuito, aceptada): la app deja de funcionar hasta pulsar *Restore project*.
- **"Lección diaria" con un lab pendiente** dice "No tienes lecciones pendientes" (Q3); propuesta en la spec (E6-4).
- **Notificaciones "Logros" y "Nuevos laboratorios" sin efecto:** los interruptores guardan la preferencia en el teléfono; no hay push (módulo 4).
- Datos de módulos anteriores que siguen fijos (dispositivos, etc.): ver `CLAUDE.md`, sección 8.
- Revisar `F-24` de la revisión de código histórica: menciona una migración "0007" que ya se usó para este módulo; una migración futura será `0008`.

## 9. Pendientes para el módulo 4

- **Social** (17 pantallas): amigos, solicitudes, racha grupal, batallas; alias público, tabla pública, funciones de búsqueda (ver `2026-10-18-modulo-4-social-analisis.md`).
- **Posible `0008`:** tabla de actividad por día para que repetir cuente en la racha, y/o guardar el mejor resultado de un lab; **no se crea sin decisión del grupo**.
- **Corrección del examen en el servidor** (función SQL) si hay ranking o XP.
- **Notificaciones push** (logros, nuevos labs, retos) y su efecto sobre los interruptores.
- Decidir si "Lección diaria" debe incluir el lab pendiente (E6-4) y la fecha de desbloqueo de cada insignia.
- Las pruebas contra el servidor real de las llamadas de progreso (F-03) y las pruebas de Compose de las pantallas nuevas.

## 10. Lista de verificación visual para el grupo

Marcar cada casilla al comprobarlo en el teléfono. "Dónde ver" es la ruta en la app con una cuenta que tenga el progreso necesario.

| ☐ | Pantalla | Nodo de Figma | Cómo llegar | Qué mirar |
|---|---|---|---|---|
| ☐ | Barra inferior | 2264:116 | Cualquier pestaña de Home | Cinco pestañas; la activa en oro; Social más tenue; el Snackbar "Próximamente" aparece sobre la barra; Perfil abre el menú y no queda seleccionada; la barra no queda tapada por la navegación del sistema |
| ☐ | Inicio | 49:15 | `Inicio` | Nombre, avatar, "Nivel actual: …" y "Racha Activa de N días 🔥" en la chapa; hoja redondeada con el asa; 3 tarjetas con sus ilustraciones; las dos últimas atenuadas; la línea bajo "Lección diaria"; el ícono de salida |
| ☐ | Ruta (mapa) | 2266:72 | `Ruta` | Etiquetas de las islas **en dos líneas** ("Isla de las / Contraseñas", "Islas de Redes / Seguras"); la línea punteada; la isla disponible con el aro dorado y el halo; las bloqueadas con 🔒; "N de 7 islas" y la barra; desplazamiento del mapa |
| ☐ | Lección | 2294:112 y las otras 5 | Tocar una isla desbloqueada | Título, "MÓDULO N de 6", círculo del emoji, 3 tarjetas (el texto largo hace crecer la tarjeta), "🧪 Ir al laboratorio →", "X" |
| ☐ | Labs | 2271:84 | `Labs` | 6 tarjetas + "Examen Final"; estados "✅ Completado", "Comenzar →" y atenuada; chips Fácil/Medio/Difícil/Prueba; el toque en una bloqueada |
| ☐ | Terminal | 2374:273 y las otras 5 | Tocar un lab disponible | **Centrado de la ✓ dorada** en el círculo de cada objetivo cumplido; las tres tarjetas con su comando; salida con el texto de Figma; **el teclado no tapa el campo ni los botones**; **la rotación conserva el intento**; fuente monoespaciada |
| ☐ | Terminal resuelta | 2374:303 | Cumplir los 3 objetivos | Salida con `$ comando` y respuesta de cada uno; el campo conserva el último comando |
| ☐ | Informe de lab (éxito) | 2273:131, 2374:336 | "Finalizar laboratorio" con 3/3 | 🏆, título del lab, StatBox (100 % o tiempo en Contraseñas), ✅ por objetivo, "Avance guardado", los 2 botones |
| ☐ | Informe de lab (no completado) | 2404:243, 2400:144 | "Finalizar" con 0, 1 o 2 | Trofeo roto, "Laboratorio no completado", porcentaje (0/33/67) o tiempo, ❌ en lo pendiente |
| ☐ | Examen: introducción | 2486:249 | `Ruta` → Prueba Final (con 6 islas) | Círculo 📝, 4 tarjetas, "Al aprobar completas la Ruta de Aprendizaje.", "Comenzar Examen →" |
| ☐ | Examen: pregunta | 2491:243 | "Comenzar Examen →" | "Pregunta N de 6", tema en oro, texto de la pregunta, 4 opciones A–D, el adorno gris de abajo; un toque avanza sin decir si acertaste |
| ☐ | Examen aprobado | 2491:357 | 6 aciertos | 🏆, "¡Examen aprobado!", "¡Felicidades por aprobar el examen final!" (sin "Subiste de nivel"), 3 líneas ✅, ACEPTAR |
| ☐ | Examen aprobado con 1 error | 2498:512 | 5 aciertos | "con 1 error", "⚠️ 1 respuesta incorrecta", "Tema: …", "Correcta: …" de la pregunta fallada |
| ☐ | Examen no aprobado | 2491:345 | 4 o menos | 📖, "Sigue practicando", 3 líneas tocables, "Reintentar", "Volver al inicio"; no dice cuáles fallaste |
| ☐ | Logros e Insignias | 2287:100 | Perfil → "Logros e Insignias" | "N de 9" real; los **9 nombres completos** (hasta 3 líneas, sin recorte) y la misma altura por fila; desbloqueadas en oro con su emoji; bloqueadas atenuadas con 🔒 |
| ☐ | Racha | 49:15 y Mi Perfil | Inicio, menú de usuario, Mi Perfil | "Racha Activa de N días 🔥" (con 1: "día"); "N días 🔥" en Mi Perfil |
| ☐ | Modo oscuro / tamaño de texto | todas | Ajustes del sistema | La app usa un único esquema de color oscuro (`darkColorScheme`) y no sigue el modo del sistema: comprobar que se ve igual con el modo claro y el oscuro del teléfono. Con el texto del sistema en grande nada debe cortarse ni solaparse (especialmente tarjetas de Labs, lecciones, Logros y resultados) |
| ☐ | Rotación | todas las pantallas completas | Girar el teléfono | Lab y examen conservan el intento; lección, informe y resultado se redibujan sin perder datos |

## 11. Guion de prueba manual de punta a punta (cuenta nueva)

Necesita internet, un teléfono o emulador con Google Play Services y acceso al Table Editor de Supabase. Marcar cada casilla.

1. ☐ **Registro** con un correo nuevo y verificar el código de 6 dígitos. Resultado: llega al quiz.
2. ☐ **Nivelación:** terminar el quiz y pulsar "Continuar". Resultado: Inicio con la pestaña **Ruta**; "Tu nivel: BAJO/MEDIO/ALTO"; "0 de 7 islas completadas". En Supabase, `placement` tiene 1 fila del usuario (anota su `level`).
3. ☐ **Inicio:** "Racha Activa de 0 días 🔥"; "Siguiente: <primera lección>". Tocar "Concepto nuevo" y Social: aparece "Próximamente".
4. ☐ **Ruta y lección:** tocar la isla 2 (bloqueada): "Completa la isla anterior". Abrir la lección 1 y volver: la racha pasa a **1 día** el mismo día; en `lesson_progress` hay 1 fila (`CONTRASENAS`). Abrirla otra vez: sin error y sigue 1 fila.
5. ☐ **Lab, fracaso:** abrir el lab 1 desde la lección ("Ir al laboratorio →"); cumplir solo 1 o 2 objetivos, "Finalizar": "Laboratorio no completado", ❌ y el porcentaje; `lab_progress` sin filas.
6. ☐ **Lab, éxito:** "Reintentar", cumplir los 3, "Finalizar": "¡Laboratorio completado!", "Avance guardado", "300 Años"; `lab_progress` con 1 fila. Repetirlo: sin error y sigue 1 fila.
7. ☐ **Progreso:** "Volver a la ruta": isla 1 dorada, isla 2 disponible, "1 de 7"; Labs: lab 1 "✅ Completado", lab 2 "Comenzar →"; Inicio: "Siguiente: Phishing". En Logros: 🥇 Primeros pasos y 🔑 Maestro de Contraseñas desbloqueadas ("2 de 9").
8. ☐ **Completar las 6 islas** (lección y lab de cada una). Con Phishing, Ingeniería Social y Redes se desbloquean 🎣, 📞 y 🌐. "6 de 7".
9. ☐ **Examen con 2 o más errores:** Prueba Final → "Comenzar Examen →"; fallar 2 preguntas: "Examen no aprobado", sin decir cuáles. En `exam_attempts` hay 1 fila con `correct_count` ≤ 4 y `passed = false`. La Prueba Final sigue disponible.
10. ☐ **Examen con 1 error:** "Reintentar" (empieza en la pregunta 1, mismo orden); fallar solo la pregunta 1: "Examen aprobado con 1 error", tema y correcta; fila nueva con `correct_count = 5` y `passed = true`. "7 de 7" en Ruta, Labs e Inicio; 🎓 Graduado NightWatch desbloqueada.
11. ☐ **Examen con 0 errores:** responder todo bien (C, B, C, B, B, B): "¡Examen aprobado!"; fila con `correct_count = 6` y `passed = true`; 🌟 Leyenda NightWatch desbloqueada.
12. ☐ **`placement` sin cambios:** en el Table Editor, la fila de `placement` del usuario tiene el mismo `level` y `updated_at` que en el paso 2; Mi Perfil e Inicio muestran el mismo nivel.
13. ☐ **Abandonar un examen:** abrir uno, contestar 2 preguntas y pulsar "X": no hay fila nueva en `exam_attempts`.
14. ☐ **Rotación:** girar el teléfono en medio de un lab y de un examen: se conserva el intento.
15. ☐ **Modo avión:** activarlo, terminar un lab o un examen: el resultado se ve con "Sin conexión… aún no se guardó" y "Reintentar guardado". Desactivarlo y pulsar el botón: una sola fila nueva; un doble toque no duplica. En Logros sin conexión: se conserva lo último y aparece "Puede estar desactualizado".
16. ☐ **Racha al día siguiente:** sin actividad al día siguiente la racha sigue en 1; pasado un día más sin actividad baja a 0 y las insignias de racha ganadas no se bloquean.
17. ☐ **Cerrar sesión y volver a entrar:** cerrar sesión desde Inicio (ícono de salida) o el menú; iniciar sesión otra vez: el progreso, la racha y las insignias siguen igual.

## 12. Borrador de la descripción del pull request

**Título:** Módulo 3: ruta de aprendizaje, lecciones, laboratorios, examen final, racha e insignias reales (v2.1.0)

**Resumen.** Añade el módulo 3: un contenedor `Home` con barra inferior (Inicio, Ruta, Labs, Social atenuada, Perfil), el mapa de 6 islas, 6 lecciones, 6 laboratorios con terminal simulada e informe, el
Examen Final y la racha y las 9 insignias reales. El progreso se guarda en tres tablas nuevas (`0007`) y todo lo demás (islas, desbloqueo, racha, insignias) se deriva de él. Versión 2.1.0 (`versionCode` 4).

**Cambios principales**
- Nueva navegación `Home(tab)`, `Lesson`, `Lab`, `LabReport`, `ExamIntro`, `Exam`, `ExamResult` (rutas `@Serializable`); `MainMenu` se elimina.
- `data/learning`: contenido en JSON (`assets`), `LabTerminal`, `gradeExam`, `deriveLearningProgress`, `Streak`, `Badges` (catálogo en un solo archivo) y `ProgressSummaryProvider`.
- `SupabaseLearningProgressRepository` (`data/supabase`): lectura de progreso con fechas y escritura idempotente de lección y lab; un intento de examen por resultado. La fila sale siempre de la sesión.
- Pantallas con estados de carga y error de red con "Reintentar", guardado con "Reintentar guardado" y relectura del progreso al volver.
- Módulo 2 tocado solo en lo mínimo: Mi Perfil, Menú de usuario y Logros (racha e insignias reales); se eliminaron los datos fijos de racha e insignias.
- Textos nuevos en `strings_aprendizaje.xml` (prefijo `learning_`); 951 pruebas unitarias (analizadas en este informe).

**Cómo probar:** ver la sección 11 de `docs/superpowers/reports/modulo-3-diagnostico.md` (guion de punta a punta con una cuenta nueva) y la lista visual de la sección 10.

**Riesgos**
- Nada de lo visual se probó en un teléfono ni contra el servidor real desde el repositorio (ver las casillas de las secciones 10 y 11).
- El examen se corrige en el cliente (R2); el desbloqueo en orden también es solo de la app (R6).
- La racha no cuenta repetir una lección o un lab ya registrado (límite documentado, propuesta `0008`).
- Plan gratuito de Supabase: pausa a los 7 días y sin modo sin conexión.
- Decisiones del grupo que conviene confirmar: Q11, emojis de las insignias y la regla de "Leyenda NightWatch".

**Migración `0007`:** `supabase/migrations/0007_progreso_aprendizaje.sql` ya está **aplicada manualmente** en el proyecto Supabase y verificada con sus consultas a a g (no se ejecuta desde la app ni desde el repositorio).
No hay migraciones nuevas en este PR. Los cambios futuros irán en `0008`.

**Checklist de revisión**
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin` en verde (951 pruebas, 0 errores de lint).
- [ ] Sin secretos en el diff (`local.properties` no está versionado).
- [ ] Ninguna pantalla ni ViewModel importa Supabase ni `BuildConfig`.
- [ ] Ningún código del examen escribe en `placement` ni en otra tabla distinta de `exam_attempts`.
- [ ] Recorrida la lista visual (sección 10) y el guion (sección 11) en un teléfono.
- [ ] 1 aprobación de otro integrante; merge con "Create a merge commit"; nadie sube a `main` directo.
