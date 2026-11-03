# Etapa 8 · Inglés de la interfaz — progreso (rama `modulo-5-ingles-ui`)

## Parte 1 de 2 (hecha)

`res/values-en/` ya tiene: `strings.xml` (comunes, insignias, errores de autenticación, notificaciones y plan de estudio), `strings_perfil.xml`,
`strings_auth.xml` y `strings_quiz.xml`. Español sin cambios (ni claves ni valores). Glosario `modulo-5-glosario.md` aplicado; inglés americano.

| Archivo | `<string>` | `<plurals>` | Notas |
|---|---|---|---|
| `strings.xml` | 81 | 0 | |
| `strings_perfil.xml` | 168 traducidas (+2 `translatable="false"` solo en `values/`) | 1 (one/other) | |
| `strings_auth.xml` | 29 | 0 | |
| `strings_quiz.xml` | 13 | 0 | |

## Pendiente: Parte 2 (otra sesión, misma rama)

`strings_aprendizaje.xml` (147 cadenas, 2 plurales), `strings_social.xml` (95) y `strings_social_pantallas.xml` (236 cadenas, 5 plurales, 1 `string-array`).
- `TranslationParityTest.PENDING_PARTE_2` debe quedar **vacía** al final (hoy nombra esos tres archivos).
- Tabla de revisión completa y actualización de la spec: son de la Parte 2.
- Mientras falten, `lintDebug` da **MissingTranslation como error** (485: 149 + 95 + 241). Es el único error de lint; no se silenció.

## Pruebas

`TranslationParityTest` revisa archivo por archivo: mismas claves, mismos placeholders (posición y tipo), plurales con `one`/`other`, nada de `translatable="false"` repetido
en inglés, cadenas no vacías y no idénticas al español salvo la lista de excepciones (documentada en la prueba):
`app_name`, `common_app_title`, `common_error_icon_description` ("Error"), `settings_menu_footer`, `profile_member_since_value` y los meses
cortos de febrero, marzo, junio, julio, septiembre, octubre y noviembre.

## Cadenas cuyo inglés mide más de 1.3 veces el español (espacios reducidos)

Solo dos en esta parte: `settings_menu_language` ("Idioma" 6 → "Language" 8, fila del menú, sin riesgo) y `settings_privacy_biometric_subtitle` (36 → 49, subtítulo de fila que ya admite dos líneas).
Los botones en mayúsculas (`SIGN OUT EVERYWHERE`, `BACK TO EDIT PROFILE`, `CONFIRM CODE`, `SAVE CHANGES`) miden igual o menos que el español. Pestañas, chips y tarjetas están en la Parte 2.

## Hallazgos (sin corregir)

- **Aviso de la pantalla Idioma:** las claves `settings_language_notice_*` **no existen todavía** (ni en `values/` ni en código); la spec las ubica en la etapa 10 (13.3). No las creé aquí; hay que decidir si se adelantan.
- **Español en código fuera de recursos** (mensajes de excepción internos, nunca se muestran): `data/common/DataAccess.kt:10`, `data/learning/ExamGrader.kt:40`, `data/social/SocialResponseParser.kt:91`. El resto son comentarios y descripciones de insignias en `Badges.kt` (campos de documentación).
- Recursos con texto de otro tipo: los símbolos `›`, emojis y banderas se quedan en código (PROPUESTA de la etapa 3).
- **Primer arranque con `values-en`:** cubierto por pruebas de lógica (`LanguageBootstrapTest`): si la actividad se recrea justo después de fijar español, la siguiente ejecución no lo pisa ni sobrescribe una elección posterior. Lo que no se puede probar aquí: que un teléfono en inglés pase por la recreación sin destello; está en `etapa-7-verificacion.md`.
- `contentDescription` y textos de `@Preview` no se revisaron en esta parte.
