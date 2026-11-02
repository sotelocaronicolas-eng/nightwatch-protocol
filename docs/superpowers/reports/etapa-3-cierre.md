# Módulo 5 · Etapa 3 (F-09): cierre

Ramas: `modulo-5-textos-perfil` (A), `modulo-5-textos-auth` (B1), `modulo-5-textos-aprendizaje` (B2), `modulo-5-textos-social` (B3).
Sin cambio visual y sin traducir: todos los textos resueltos en español son idénticos a los literales originales (`GoldenTextsTest`).

## 1. Qué se hizo en B2 y B3

- **B2 (aprendizaje):** el módulo 3 ya usaba `learning_*`. Solo quedaba un literal con texto para la persona: el aviso de la terminal del lab
  ante un comando desconocido (`learning_lab_unrecognized_command`). `TerminalEntry.output` y `TerminalResult.Unrecognized.message` pasan a `UiText`
  (el texto de un comando del lab sigue viniendo del JSON, como `UiText.Dynamic`). Sin otros literales en `ui/lesson`, `lab`, `labreport`, `labs`, `path`, `exam`, `examresult` ni `data/learning`.
- **B3 (Social):** `ui/social` y `data/social` ya no tenían texto fijo fuera de `@Preview` y de sus datos de muestra (los textos ya estaban en `strings_social*.xml` desde el módulo 4).
  El trabajo fue de **guarda**, no de migración. Las preguntas de batalla y los retos de ulti vienen de Postgres y no se tocaron (D1).

## 2. Claves por archivo de recursos (`app/src/main/res/values/`)

| Archivo | `<string>` | `<plurals>` |
|---|---|---|
| `strings.xml` (nombre de la app) | 1 | 0 |
| `strings_aprendizaje.xml` | 147 | 2 |
| `strings_auth.xml` | 29 | 0 |
| `strings_comunes.xml` | 74 | 0 |
| `strings_notificaciones.xml` | 6 | 0 |
| `strings_perfil.xml` | 170 | 1 |
| `strings_quiz.xml` | 13 | 0 |
| `strings_social.xml` | 33 | 0 |
| `strings_social_moderacion.xml` | 32 | 0 |
| `strings_social_pantallas.xml` | 236 | 5 |
| `strings_social_perfil.xml` | 30 | 0 |
| **Total** | **771** | **8** |

## 3. Guarda global (`NoHardcodedTextTest`)

Ahora recorre **todo `app/src/main/java`** (ui, data, di, MainActivity...). Ignora comentarios, `@Preview` y sus funciones de datos de muestra (`private fun preview...`),
literales sin letras, claves técnicas sin espacios ni tildes, identificadores con barras (`America/Bogota`), mensajes de `throw`/`require`/`check` (también en varias líneas),
`Exception(`, `Log.`, `TAG =` y `@Suppress...`. Una segunda prueba exige **cero** marcadores `PENDING_PART_B`.

**Excepciones por archivo (4), con motivo:**

| Archivo | Motivo |
|---|---|
| `data/device/DeviceIdentity.kt`, `data/device/SupabaseDeviceRepository.kt` | El nombre por defecto del teléfono ("Dispositivo Android", "Dispositivo") es un **dato guardado** en `devices`, no un texto de pantalla; no puede depender del idioma del momento. La lista de dispositivos ya muestra "Dispositivo sin nombre" desde un recurso cuando el nombre está en blanco. |
| `data/supabase/SupabaseErrors.kt` | Palabras en inglés de mensajes del servidor que se comparan ("row-level security") y la etiqueta de una causa que va a los registros. |
| `di/AppConfig.kt` | Mensaje para quien compila la app cuando falta una clave de `local.properties`; no es texto para quien aprende. |

`PersonalizationBoundaryTest`: la lista de archivos de `data/` que pueden nombrar recursos sigue en 10 (se añadió `LabTerminal.kt`). Todos construyen `UiText` con ids de recurso
(catálogo de insignias, plan de estudio, formateadores del perfil y de dispositivos, recordatorios, terminal del lab). Reducirla más obligaría a mover esos ids a `ui/`, lo que rompería las
pruebas de `data` que comparan `UiText`; **no se consideró viable** en esta etapa.

## 4. Escaneo final de literales en español que la guarda no ve

La guarda solo marca literales con espacio, tilde o mayúscula inicial. Revisado a mano lo demás (palabras sueltas en minúscula):

- `data/profile/ProfileDataExporter.kt:57-66`: **claves del JSON exportado** en español (`nombre`, `correo`, `genero`, `nivel`, `miembroDesde`...). No son texto de pantalla; el archivo exportado
  seguirá en español. Decisión pendiente del grupo (¿exportar con claves estables en inglés?).
- `data/learning`/`ui/labreport/LabReportViewModel.kt:156`: `formatEstimatedTime` pone mayúscula en cada palabra salvo `"de"` (lógica del español sobre el texto del JSON de labs). Pendiente para la etapa 8/10.
- Todo lo demás son claves técnicas (JSON del servidor, tablas, columnas, `"dark"`, `"light"`, `"male"`...).

## 5. Accesibilidad

Se revisaron los `contentDescription = null` de toda la app: son logos, ilustraciones y fondos decorativos, o están dentro de contenedores que ya llevan etiqueta. **No se agregó ninguna
descripción** y ningún ícono interactivo quedó sin ella. (La "X" de cerrar ya se había corregido en B1.)

## 6. Pendiente para la etapa 8 (inglés)

- Crear `values-en/` para los 11 archivos (771 cadenas y 8 plurales) con prueba de paridad.
- Traducir el contenido JSON (etapa 9) y decidir las claves del JSON exportado (sección 4).
- `formatEstimatedTime` y los nombres por defecto de dispositivo (sección 3) dependen del idioma.
- Aviso de la arena "Las preguntas están disponibles solo en español" (`social_arena_questions_spanish_only`, A5).
- Las 4 excepciones de la guarda no necesitan traducción.
