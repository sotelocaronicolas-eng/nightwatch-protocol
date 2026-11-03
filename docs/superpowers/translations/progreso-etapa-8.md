# Etapa 8 · Inglés de la interfaz — progreso (rama `modulo-5-ingles-ui`)

**Estado: COMPLETO (partes 1 y 2).** Falta la revisión del grupo (tabla `ui-es-en.csv`) y verlo en el emulador (`reports/etapa-8-verificacion.md`).

`res/values-en/` tiene los 7 archivos de `res/values/` (español sin cambios, salvo la clave nueva del aviso). Glosario `modulo-5-glosario.md` aplicado más la sección
"Términos nuevos de la Parte 2" (PROPUESTA). Inglés americano.

| Archivo | `<string>` traducidas | `<plurals>` | Otros |
|---|---|---|---|
| `strings.xml` | 81 | 0 | |
| `strings_auth.xml` | 29 | 0 | |
| `strings_quiz.xml` | 13 | 0 | |
| `strings_perfil.xml` | 169 (+2 `translatable="false"` solo en `values/`) | 1 | incluye `settings_language_notice_text` |
| `strings_aprendizaje.xml` | 147 | 2 | |
| `strings_social.xml` | 95 | 0 | |
| `strings_social_pantallas.xml` | 236 | 5 | 1 `string-array` |
| **Total** | **770** | **8** | 1 arreglo |

## Qué hay
- `TranslationParityTest`: paridad archivo por archivo de TODOS los archivos (sin lista de pendientes), placeholders, plurales, no vacío y no idéntico al español salvo excepciones documentadas en la propia prueba.
- `LanguageNoticeTest`: el aviso de Idioma solo con English, texto en los dos idiomas.
- Tabla de revisión: `ui-es-en.csv` y guía `ui-es-en.md`.

## Pendiente (otras etapas)
Contenido JSON (etapa 9); notificaciones, fechas, correos y el texto final del aviso (etapa 10); preguntas de batalla en Postgres (D15, etapa posterior).
