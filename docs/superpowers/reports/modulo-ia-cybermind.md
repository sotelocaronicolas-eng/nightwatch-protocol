# Módulo IA CyberMind: informe

Rama `modulo-ia-cybermind` (desde `main` = v2.4.0). Un modelo de IA que corre **en el teléfono** (ONNX Runtime) clasifica a cada
estudiante, por tema, como **AVANCE** o **REFUERZO**. Dos formas de entrada: CSV y manual. No usa Supabase, no guarda ni envía datos de
estudiantes y no los escribe en ningún log.

## 1. Qué hay

| Parte | Dónde |
|---|---|
| Modelo y envoltorio (sin cambios de lógica) | `assets/cybermind_gbdt.onnx`, `cybermind/CyberMindModelo.kt` (solo cambió el `package`) |
| Dominio: temas, filas, resultados, `puntaje_tema` | `cybermind/CyberMindDominio.kt` |
| Validación del CSV (Kotlin puro) | `cybermind/ValidadorCsv.kt` |
| Validación del ingreso manual (Kotlin puro) | `cybermind/ValidadorManual.kt` |
| Evaluador (interfaz + ONNX) y `MODEL_VERSION` | `cybermind/EvaluadorEstudiante.kt` |
| Plantilla CSV | `cybermind/PlantillaCsv.kt` |
| Leer el CSV y guardar la plantilla (SAF) | `cybermind/ArchivosCsv.kt` |
| Resultados en memoria | `cybermind/ResultadosEnMemoria.kt` |
| Pantallas y ViewModels | `ui/cybermind/` (entrada, manual, CSV, resultados, detalle) |
| Textos | `res/values{,-en}/strings_cybermind.xml` (prefijo `cybermind_`) |
| Rutas | `ui/navigation/` (`CyberMind*Route`) |
| Conexión | tarjeta "Prepárate con CyberMind" de Inicio (`ui/home/HomeScreen.kt`) |

Flujo: Inicio → entrada → (CSV | manual) → resultados → detalle.

## 2. Decisiones

- **Instancia única.** `AppContainer.evaluadorEstudiante` envuelve `CyberMindModelo` con un `lazy`: la sesión de ONNX se crea **una sola vez, en la
  primera evaluación** (no al arrancar la app, para no sumar tiempo de inicio) y corre en `Dispatchers.Default`.
- **Regla de respaldo.** Si el modelo no carga, `CyberMindModelo` ya aplica la regla del 70 %; el evaluador lo marca como `REGLA_RESPALDO`
  y el detalle lo dice ("Evaluado con la regla de respaldo (70 %)"). Con la regla **no hay probabilidad real**, así que la confianza es `null` y la
  pantalla muestra solo el puntaje (decisión mía: mostrar "100 %" habría sido engañoso).
- **Nombre `Veredicto`.** `CyberMindModelo.kt` ya define una clase `Decision`; el enum AVANCE/REFUERZO de la interfaz se llama `Veredicto` para no tocar ese archivo.
- **Errores como datos.** `ErrorCsv` y `ErrorCampo` llevan datos, no texto: el dominio no conoce Android ni strings. `ui/cybermind/ErrorCsvTexto.kt` los traduce.
- **Se rechaza el archivo completo.** El validador junta **todos** los errores; la pantalla muestra los primeros 5 y "… y N errores más".
- **Tamaño antes de leer.** Se pide el tamaño al sistema (`OpenableColumns.SIZE`) y, si pasa de 200 KB, no se abre. Si el sistema no lo informa, se leen como máximo
  200 KB + 1 byte, lo que basta para rechazarlo. UTF-8 es **estricto**: un byte inválido rechaza el archivo en vez de cambiarlo por "?".
- **Resultados solo en memoria.** `ResultadosEnMemoria` los comparten los dos flujos, la lista y el detalle. Se vacía cuando se cierra la pantalla de resultados
  (`onCleared`). Si la app se reinicia con el detalle abierto, el detalle se cierra solo.
- **Manual.** El ID se asigna solo (`M00001`, `M00002`…, con ceros). Los cinco borradores se conservan al subir/bajar la cantidad de temas. El menú de tema oculta los ya usados.
  Aciertos, experiencia y tema se eligen de una lista (no se escriben). Acepta coma o punto al escribir decimales (el teclado en español suele dar la coma); el **CSV** sí exige punto.
- **"Evaluar otro".** En el flujo manual, el botón de abajo de resultados dice "Evaluar otro" y abre una pantalla manual **nueva** (formulario vacío); en CSV dice "Nueva evaluación".
- **Orden del puntaje.** `puntaje_tema = round(suma / 9 * 100, 1)` (`ReglasCyberMind.calcularPuntajeTema`); en el CSV se compara con tolerancia 0,05.
- **Colores.** Sin colores en duro (`NoHardcodedColorsTest` pasa). AVANCE usa `success`; REFUERZO usa el token propio `reinforce` (ver la sección 8).

## 3. Límites

- El modelo se evalúa **por fila** (un tema de un estudiante); no hay lote. Con 500 filas el costo es bajo, pero no se ha medido en un teléfono.
- `puntaje_global` solo se comprueba como promedio con **exactamente 5 filas** del estudiante (tolerancia 0,1); con menos no hay contra qué compararlo y solo se exige que sea igual entre sus filas.
- El id del estudiante solo se exige no vacío; no se valida su formato.
- Las filas de un CSV que traen errores no se evalúan: o entra el archivo entero o no entra ninguna.
- El ID del CSV aparece en pantalla y en la ruta de navegación del detalle (memoria de la app; no se guarda en disco ni se registra).
- La plantilla y el CSV demo son sintéticos. `cybermind_sintetico_oculto.csv` **no** se usó.

## 4. Cosas que NO coinciden con el Figma o con lo pedido

- **Texto de la entrada:** el Figma dice "Evalúa si un estudiante aprueba o necesita refuerzo". Se cambió a "…avanza o necesita refuerzo" porque pediste que nunca aparezca "Aprueba".
- **Iconos** de las dos opciones de la entrada: resuelto en la segunda ronda con los emojis del Figma como `Text` (sección 8).
- **Aciertos** son listas de 0 a 3 (el Figma muestra cajas de texto); así nunca hay un valor inválido.
- **Etiquetas en inglés:** `AVANCE`/`REFUERZO` se muestran como `ADVANCE`/`REINFORCE` con el inglés activo (decisión confirmada en la segunda ronda; el test de paridad no cambió).
- **Nombre en Inicio:** la tarjeta decía "CiberMynd"; ahora dice "CyberMind" (es y en).
- **Pintura de los chips:** el Figma usa verde y naranja; REFUERZO ya tiene su naranja (sección 8).

## 5. Pruebas

- Unitarias nuevas: `ValidadorCsvTest` (31), `ValidadorCsvBytesTest`, `ValidadorManualTest` (15), `EvaluarEstudiantesTest`, `PlantillaCsvTest`, `ErrorCsvTextoTest`, y los ViewModels
  (`CyberMindManualViewModelTest`, `CyberMindCsvViewModelTest`, `CyberMindResultadosViewModelTest`) con el evaluador falso, Turbine y `MainDispatcherRule`.
  Cubren la validez de `estudiantes_demo.csv` (500 filas, 100 estudiantes), cada regla del CSV, los límites 500 / 100 / 200 KB y el cálculo de `puntaje_tema`.
- Instrumentada (**solo compilada**): `androidTest/.../cybermind/CyberMindModeloInstrumentedTest`: carga el `.onnx` y evalúa
  `Diagnostico("criptografia", "cursos", 3, 1, 0, 44.4f, 61.0f, 38f)`, que debe dar `REFUERZO` con probabilidad 0,90 (±0,01) y origen `modelo_gbdt`.
  Los 9 valores salen del comentario de `CyberMindModelo.kt`, que dice que es el ejemplo de `src/probar_modelo_onnx.py`; **no vi ese script**, así que su igualdad con el del repo de IA está por confirmar.

## 6. Tamaño del APK

| | `app-debug.apk` |
|---|---|
| `main` (v2.4.0) | 23,6 MB |
| esta rama | 98,9 MB (+75,3 MB) |

Casi todo es `libonnxruntime.so` de las **cuatro** arquitecturas (arm64 17,6 MB, armeabi-v7a 12,3 MB, x86 20,3 MB, x86_64 20,3 MB, sin comprimir en el APK de depuración).
Un teléfono instala una sola. Reducirlo (por ejemplo `ndk { abiFilters }` a `arm64-v8a`, o publicar un AAB con divisiones por ABI) es una decisión del equipo y no se tomó aquí. El `.onnx` pesa 106 KB.
El APK de release se midió en la segunda ronda (sección 8.5).

## 7. Pendientes y preguntas

1. **Tamaño:** ¿se restringen las ABI, se sube un AAB, o se acepta? (cifras en 8.5)
2. **`MODEL_VERSION`:** resuelto: `"cybermind_gbdt-v1"`. El detalle muestra además la versión que el `.onnx` trae en sus metadatos, si la trae.
3. **Ejemplo de la prueba instrumentada:** confirmar que es el de `probar_modelo_onnx.py` (la prueba en el teléfono dio REFUERZO con 0,90, lo esperado).
4. **Color de REFUERZO / iconos / inglés:** resueltos en la segunda ronda (sección 8).
7. **Selector de archivos:** acepta `text/csv`, `text/plain`, `application/vnd.ms-excel` y `application/octet-stream` porque muchos teléfonos marcan un .csv así. Un archivo no CSV se rechaza por el encabezado.
8. **`puntaje_global`:** resuelto: promedio con 5 filas en el CSV y calculado en el manual (sección 8).
9. **Reglas de proceso:** una línea de `NightWatchDestinations.kt` (las rutas de CyberMind) se añadió con un `cat >>` de shell, que `CLAUDE.md` prohíbe; el contenido está bien y se revisó, pero queda dicho.
10. Sin pruebas de Compose de estas pantallas, ni `@Preview`, ni TalkBack (igual que la deuda del módulo 4).

## 8. Segunda ronda (etapa 6)

**Prueba en el teléfono (hecha por el equipo):** la prueba instrumentada da `REFUERZO` con 0,90 y el recorrido completo funciona.

1. **Manual: `puntaje_global` calculado** (6a). Ya no se escribe: es el promedio de los `puntaje_tema` de las filas con sus tres aciertos, redondeado a 1 decimal
   (`ValidadorManual.calcularPuntajeGlobal`), de solo lectura con la etiqueta "calculado" y recalculado en vivo; sin temas completos muestra un guion. Es una sola cifra
   para todas las filas, así que se quitó la validación de consistencia entre filas del manual (la de `experiencia_previa` se mantiene). Se quitaron `FilaManual.puntajeGlobal`,
   `ErroresFilaManual.puntajeGlobal`, `ValidacionManual.puntajeGlobalDistinto` y el texto `cybermind_error_global_differs` (es y en).
2. **CSV: regla del promedio** (6b). Con exactamente 5 filas, `puntaje_global` debe igualar el promedio de sus 5 `puntaje_tema` con tolerancia 0,1
   (`ErrorCsv.PuntajeGlobalNoPromedio`; "Estudiante E00012: puntaje_global no coincide con el promedio de sus temas", y su versión en inglés). Con menos filas solo se exige que
   sea igual entre ellas. Si además es distinto entre filas, solo se informa esa diferencia. `estudiantes_demo.csv` y la plantilla siguen siendo válidos.
3. **`MODEL_VERSION`** = `"cybermind_gbdt-v1"` (6c).
4. **Color de REFUERZO** (6c): token `reinforce` en `NightWatchColors` (naranja `#B45309` en claro y en oscuro; texto blanco encima 5,02:1, con su par en `ContrastTest`).
   Se usa en los chips y en la fila del detalle. AVANCE sigue en `success`; el CSV sigue con su color de error.
5. **Iconos de la entrada** (6c): `📜` y `🖊` como `Text`, con `contentDescription` ("Pergamino"/"Pluma", "Scroll"/"Pen"). Sin material-icons-extended ni assets.
6. **Inglés:** AVANCE/REFUERZO se quedan `ADVANCE`/`REINFORCE`; el test de paridad no cambió.
7. **Plan B del modelo** (6c): `OnnxEvaluadorEstudiante` captura `Throwable` al crear el modelo (no solo `Exception`); si falla (p. ej. `UnsatisfiedLinkError`), recuerda el fallo,
   no reintenta por cada fila, evalúa con la regla del 70 % y marca `REGLA_RESPALDO` (confianza `null`). Nota: el hueco real estaba en `OrtEnvironment.getEnvironment()`, que corre en el
   constructor de `CyberMindModelo` fuera de su `runCatching`; ahora lo cubre el envoltorio, sin tocar `CyberMindModelo.kt`. Prueba: `EvaluarEstudiantesTest` (fábrica que lanza `UnsatisfiedLinkError`).
   La prueba usa `OnnxEvaluadorEstudiante` con una fábrica que falla, no el `FakeEvaluadorEstudiante` (este no puede simular un fallo de creación).
8. **Tamaño del APK de release** (solo medido, sin cambios de configuración). Build: `./gradlew assembleRelease`, tal como está en `app/build.gradle.kts` (`release` con `optimization.enable = false`, sin `signingConfig`,
   así que sale `app-release-unsigned.apk`; la firma no cambia el tamaño de forma apreciable). No sé con seguridad si el APK de la v2.4.0 se firmó o se armó distinto: ver preguntas.

   | | Tamaño |
   |---|---|
   | `app-release-unsigned.apk` (esta rama) | **91,6 MB** (91 637 003 bytes) |
   | `lib/` dentro del APK (sin comprimir) | 73,8 MB |
   | dex (4 archivos) | 13,8 MB |
   | `res/` + `resources.arsc` | 3,7 MB |

   `libonnxruntime.so` por arquitectura (almacenada **sin comprimir** en el APK):

   | ABI | `libonnxruntime.so` | + `libonnxruntime4j_jni.so` |
   |---|---|---|
   | arm64-v8a | 17,6 MB | 0,9 MB |
   | armeabi-v7a | 12,3 MB | 0,8 MB |
   | x86 | 20,3 MB | 0,8 MB |
   | x86_64 | 20,3 MB | 0,9 MB |

   Las cuatro suman 70,4 MB (más 3,4 MB de los JNI): casi todo el APK. **Qué cambiaría (no aplicado):**
   - `abiFilters` solo `arm64-v8a`: el APK bajaría a unos **36 MB** (91,6 − 73,8 + 18,5 de las libs de arm64), pero dejaría de instalar/ejecutar en teléfonos de 32 bits (armeabi-v7a) y en emuladores x86/x86_64;
     lo esperable es que Android se niegue a instalarla ahí (sin ABI compatible); el plan B cubre un fallo de carga en un teléfono donde sí se instaló, no esa instalación.
   - AAB: Google Play entrega a cada teléfono solo su ABI, así que la descarga sería de unos 36 MB en un arm64 sin tocar `abiFilters`, y se conserva el soporte de las demás arquitecturas. Ojo: el APK de
     Releases de GitHub (el que usa el equipo) no se beneficia; el AAB solo sirve para Play. `bundle { language { enableSplit = false } }` ya está configurado.

9. **Validación:** `assembleDebug`, `testDebugUnitTest` (1625 pruebas, 0 fallos, 0 omitidas), `lintDebug` (0 errores) y `compileDebugAndroidTestKotlin` en verde antes de cada commit.

**Pendiente de comprobar en el teléfono:** el campo "Puntaje global (calculado)" del manual (guion, recálculo en vivo al cambiar aciertos de cualquier tema, claro y oscuro), los emojis 📜 y 🖊 (que
el teléfono los dibuje en color y no como cuadros), el naranja de REFUERZO en claro y oscuro, el mensaje del CSV con 5 filas fuera de tolerancia, y que "Versión del modelo" diga `cybermind_gbdt-v1`.
