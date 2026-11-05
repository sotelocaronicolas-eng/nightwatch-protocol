# Etapa 9 (5B) · Contenido JSON en inglés · verificación

Rama `modulo-5-ingles-contenido`. Sin push, sin tocar `main`, sin Supabase. Nada de esto se ha visto en un teléfono o emulador.

## 1. Estructura final (decisión D18)

```
app/src/main/assets/content/
├── es/   learning_lessons.json  learning_labs.json  learning_exam.json  quiz_questions.json   (base; movidos con git mv, sin cambiar un byte)
└── en/   learning_lessons.json  learning_labs.json  learning_exam.json  quiz_questions.json
```

- Ruta de un archivo: `content/<tag>/<archivo>` (`contentAssetPath` en `data/content/LocalizedContent.kt`; `<tag>` es `AppLanguage.tag`).
- `loadLocalizedContent(language, file, readAsset, parse)`: lee el idioma activo; si el archivo **no existe o no se puede interpretar**, usa el español, **archivo por archivo** (un examen en inglés roto no tira las lecciones en inglés). El español no se protege: si el base está roto, el error sale como antes.
- `LocalLearningContentRepository` y `LocalQuizContentRepository` reciben `language: () -> AppLanguage?` (en `AppContainer`: `languageRepository.currentLanguage()`, el mismo mecanismo que `ContextTextResolver`). Guardan **una copia en memoria por idioma**, así que cambiar de idioma con la app abierta no sirve contenido viejo. `null` (instalación nueva) = español.
- Lógica tocada: solo la carga. Los modelos, ViewModels, `LabTerminal`, `gradeExam` y la UI no cambiaron.

### Cómo se añade un tercer idioma (`xx`)
1. Agregar `XX("xx")` a `AppLanguage` (y lo de `values-xx`, `locales_config.xml` de la sección 13.4 de la spec).
2. Copiar `assets/content/en/` a `assets/content/xx/` y traducir los 4 archivos.
3. Nada más en código: `ContentParityTest` y `LocalizedContentLoadingTest` recorren `AppLanguage.entries`, así que exigen sus 4 archivos y la paridad con el español desde que exista la entrada. Sin la carpeta, la app cae al español.

## 2. Conteos (idénticos en es y en)

| Archivo | Elementos |
|---|---|
| `learning_lessons.json` | 6 lecciones × 4 tarjetas = 24 tarjetas |
| `learning_labs.json` | 6 labs × 3 objetivos = 18 objetivos (18 comandos) |
| `learning_exam.json` | 6 preguntas × 4 opciones |
| `quiz_questions.json` | 20 preguntas × 4 opciones (5 por categoría × 4 categorías) |

Tabla de revisión: `docs/superpowers/translations/contenido-es-en.csv` (295 filas: lecciones 60, labs 99, examen 36, quiz 100; columnas archivo, id, campo, español, inglés, y marca). Los saltos de línea van como `\n` literal. Marcas: `LARGO` (tarjetas de lección), `PROPUESTA`, `SALIDA DE TERMINAL EN PROSA`, `IGUAL (…)`. En el examen y el quiz la opción correcta lleva "(CORRECTA)" en el campo.

## 3. Pruebas (todas en `app/src/test/.../data/content/` salvo indicación)

| Prueba | Qué garantiza |
|---|---|
| `ContentParityTest.structure_isTheSame_andFixedValuesDoNotChange` | Mismas claves, mismo orden y tamaño de listas; `topic`, `difficulty`, `category`, `id`, `emoji`, `command`, `source` y `correctIndex` iguales; ningún texto vacío; ningún texto igual al español salvo la lista documentada (`123456`, `k7#mQ!9zRt$2`, Phishing, Malware, Firewall, Smishing/Vishing/Whaling/Tailgating y las dos líneas de log de `cat access.log`) |
| `…correctAnswers_areTheSameInExamAndQuiz` | Mismo `correctIndex` en las 26 preguntas, 4 opciones en ambos |
| `…labCommands_areIdentical` | Los 18 comandos de labs idénticos |
| `…labsAndLessons_keepTheirTechnicalTokens` | Cifras, IPs, dominios, nombres de archivo y siglas (WPA3, SSH, DENY, UFW…) de cada texto de labs y lecciones son los mismos en ambos idiomas |
| `…lessonParagraphsAndBullets_keepTheirFormat` | Mismos párrafos (`\n\n`) y viñetas `•` por tarjeta; sin espacios dobles |
| `LocalizedContentLoadingTest` (8 pruebas) | Idioma activo elige carpeta; sigue el cambio de idioma y guarda una copia por idioma; falta un archivo inglés → ese archivo en español; falta/está roto el JSON inglés → todo español; el español no toca `/en/`; si el base falla el error sale; quiz con respaldo; mismo `id`/`correctIndex`/categoría en el banco completo |
| `SpanishContentGoldenTest` | SHA-256 de los 4 archivos en español (fin de línea normalizado) = SHA-256 de los originales en `HEAD` antes del movimiento |
| `LearningContentTest` (existente) | Sigue pasando sobre `content/es/`; solo cambió la ruta esperada en `repository_readsEachAssetOnce…` |

Resultado final de `./gradlew assembleDebug testDebugUnitTest lintDebug`: **BUILD SUCCESSFUL**, **1508 pruebas, 0 fallos, 0 errores, 0 omitidas**; lint **0 errores**. Pruebas nuevas de esta etapa: 14 (5 de paridad, 8 de carga y respaldo, 1 dorada).
Guards existentes (`NoHardcodedTextTest`, `NoHardcodedColorsTest`, `TranslationParityTest`, `SocialBoundaryTest`…): sin cambios y en verde; ninguno exigió ajuste.
El primer intento falló una vez en `labsAndLessons_keepTheirTechnicalTokens` por mi propia expresión de siglas ("las VPN" → "VPNs"); se corrigió la prueba, no el contenido.

Commits (sin push):
- `75d43c4` Contenido por idioma: los JSON pasan a `assets/content/es/` y los repositorios leen la carpeta del idioma activo, con respaldo en español (validado solo, sin los archivos en inglés).
- `72e4e87` Contenido en inglés: lecciones, laboratorios, examen final y quiz, con pruebas de paridad y de respaldo.
- El commit de documentos (esta tabla, este reporte, spec y `CLAUDE.md`) va después; no toca código.

## 4. Decisiones que debes revisar (PROPUESTA)

1. **Salidas de terminal de los labs: traduje la prosa, no los datos.** La regla dice "salidas de terminal NO se traducen", pero las 16 salidas de los labs son frases en español escritas por el grupo ("Dominio registrado hace 6 días…"), no salida real de un programa, y el objetivo es que el lab se vea en inglés. Traduje la prosa y dejé intactos IPs, puertos, dominios, nombres de archivo (`sospechoso.exe`, `usuario47`), firmas (`Trojan.Generic.NW`), siglas y las dos líneas de log de `cat`. `ContentParityTest` lo vigila. Si prefieres la lectura literal, se revierte editando esas 16 líneas de `en/learning_labs.json` (filas "SALIDA DE TERMINAL EN PROSA" del CSV).
2. **`sospechoso.exe` y `usuario47` quedan en español** porque son parte del comando que se escribe (no se traduce); el enunciado en inglés los menciona tal cual.
3. **`estimatedTime`** del lab de contraseñas se tradujo ("less than 1 second", "4 hours", "300 years"): se muestra en el informe y debe coincidir con "Estimated time: …" de la salida (la prueba `labs_estimatedTime…` del español sigue ahí; para inglés lo cubre la paridad de tokens).
4. **Nombres de islas** (glosario: "Isla" = Island): Password Island, Phishing Bay, Malware Reef, Social Deception Cave, Secure Networks Islands, Firewall Fortress. Ninguno estaba en el glosario; añádelos allí si los apruebas. "Cueva del Engaño Social" → "Social Deception Cave" (no "Social Engineering Cave") para conservar "Engaño".
5. Otros términos usados, ya en el glosario: passphrase, salt/hash, Trojan, worm, backup, deny by default, Final exam. Nuevos sin glosario: "password spraying", "evil twin", "DMZ"… se dejan como en el original (nombres técnicos); "Ataque de diccionario" → "Dictionary attack", "Red de invitados" → "Guest network", "Escaneo de puertos" → "Port scanning", "Denegar por defecto" → "Deny by default".
6. Títulos de tarjetas: "¿Qué es?" → "What is it?", "¿Por qué importa?" → "Why does it matter?", "Tipos y técnicas" → "Types and techniques", "¿Cómo protegerte?" → "How to protect yourself".
7. **Tuteo/ejemplos adaptados:** "Tu_Nombre2024" → "Your_Name2024" y "contraseña" → "password" (opciones del examen); la respuesta correcta (`k7#mQ!9zRt$2`) y su índice no cambian.

## 5. Condiciones de parada: ninguna se activó

- Ningún contenido en español cambió (hash idéntico).
- Ninguna pregunta depende del idioma (revisadas las 26; ningún juego de palabras; la respuesta correcta es la misma en ambos).
- Ningún test existente falló por causas que no entendiera.
- La carga actual admitía el cambio sin rediseño.

## 6. Riesgos y deuda

- **Traducción sin revisión humana:** hace falta que el grupo repase `contenido-es-en.csv`, igual que `ui-es-en.csv`.
- **Contenido ya en pantalla al cambiar de idioma:** AppCompat recrea la Activity y los `ViewModel` se conservan. Los repositorios ya sirven el idioma correcto, pero un `ViewModel` que cargó el contenido antes del cambio lo muestra hasta volver a cargar. Riesgo bajo: Ruta, Labs e Inicio releen en `ON_RESUME` y Lección, Lab y Examen se crean nuevos al entrar (y el idioma se cambia desde Ajustes, no a mitad de un examen). **Comprobar a mano** (sección 7, punto 5); si falla, la corrección es recargar al cambiar el idioma en esos `ViewModel` (no lo toqué: sale de "solo la carga").
- **Progreso guardado:** `lesson_progress`, `lab_progress` y `exam_attempts` usan `topic`/ids (no texto), así que no dependen del idioma. Un intento de examen guardado no conserva el texto de las preguntas.
- **`LocalQuizContentRepositoryTest` (androidTest)** usa el constructor con `Context`, que sigue existiendo; no se ejecutó (no hay emulador aquí).
- **Fin de línea:** git avisa "LF will be replaced by CRLF" en Windows (ya ocurría); la prueba dorada lo normaliza.
- **Sigue en español (fuera de esta etapa):** preguntas y retos de batalla (D15, Supabase), correos y mensajes del servidor, notificaciones, fechas (etapa 10).
- Los campos `source` (referencias a Figma) quedan en español en ambos idiomas; no se muestran.

## 7. Qué probar a mano en el emulador

Con la app en **English** (Perfil → Ajustes → Idioma):
1. **Ruta y Lecciones:** nombres de las 6 islas; abrir cada lección: 4 tarjetas con títulos "What is it? / Why does it matter? / Types and techniques / How to protect yourself", párrafos separados, viñetas, y en Firewall los comandos `ufw allow 22/tcp` y `ufw deny from 198.51.100.9` sin traducir.
2. **Labs:** abrir los 6; el enunciado y los objetivos en inglés; **escribir el comando exacto** (`crack 123456`, `whois nightwatch-secure-verify.com`, `scan sospechoso.exe`, `lookup +1-555-0134`, `cat access.log`, `ufw deny 203.0.113.77 port 22`…) y ver la salida en inglés; en el lab de contraseñas, el informe muestra "Estimated time for your strongest password" y "300 years". Informe de éxito y de fallo.
3. **Examen Final:** las 6 preguntas en inglés; contestar bien (C, B, C, B, B, B) y aprobar; probar un fallo y ver la revisión de la respuesta ("C) k7#mQ!9zRt$2").
4. **Quiz de nivelación** (cuenta nueva): las preguntas y opciones en inglés; el resultado y las áreas débiles siguen saliendo.
5. **Cambiar de idioma con la app abierta:** estando en Inicio/Ruta/Labs, ir a Ajustes → Idioma, cambiar y volver: los títulos de lecciones y labs ya deben estar en el nuevo idioma sin reiniciar (riesgo de la sección 6).
6. Lo mismo en **Español**: todo igual que en v2.3.0 (ni una coma de diferencia).
El respaldo a español (falta un archivo inglés) lo cubren las pruebas; no hace falta provocarlo a mano.
