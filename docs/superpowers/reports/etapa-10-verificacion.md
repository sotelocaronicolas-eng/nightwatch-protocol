# Etapa 10 (módulo 5B): batallas, notificaciones, fechas, correo y cierre v2.4.0

Rama `modulo-5-ingles-final`. Nada se ejecutó en Supabase ni se desplegó; nada se ha visto en un teléfono.

## 1. Resumen por parte

**A. Batallas (D15).** Migración `0013_batallas_ingles.sql` (BORRADOR sin aplicar) con las 72 preguntas y los 12 títulos de ulti en inglés;
`battle_sync` y `battle_play` aceptan `p_lang` (por defecto `'es'`). La app v2.4.0 manda `p_lang` solo con inglés.
**B. Notificaciones y fechas.** Los recordatorios usan el idioma vigente al publicarse (también antes de Android 13 y desde el Worker) y
los canales se renombran al abrir la app. `BirthDate.display(language)`: español `dd/MM/yyyy` (idéntico), inglés `Mar 7, 1999`, sin depender de la zona.
**C. Correo.** Tres plantillas de Auth bilingües (`docs/supabase/plantillas-correo-bilingue.md`) y la Edge Function acepta `lang`.
**D. Cierre.** Este informe, CLAUDE.md, spec y versión 2.4.0 (versionCode 7) en un commit aparte.

## 2. Esquema real encontrado (Supabase)

- `battle_questions(id int identity, topic, tier, question, option_a..d, correct)`: 72 filas (6 temas × 12), `0009`.
- `ulti_challenges(id int identity, kind attack|defense, topic, objective_title, expected_command, active)`: 12 filas, `0009`.
- `battle_sync(p_battle)` y `battle_play(p_battle, p_action, p_turn, p_arg)` devuelven `sx_battle_state(p_battle, user)` (versión de `0011`),
  que es lo único que lee el texto de la pregunta (solo a quien tiene el turno) y el título del reto (solo el suyo).
- Tablas nuevas de `0013`: `battle_question_translations(question_id, lang, question, option_a..d)` y `ulti_challenge_translations(challenge_id, lang, objective_title)`.
  La letra correcta y el comando esperado **no se duplican**.

## 3. Regla del idioma de la batalla (desviación a aprobar)

Se propuso el idioma **de quien mira** (el `p_lang` de cada llamada), no el del que crea la sala. Motivo: cada pregunta la ve solo
quien tiene el turno y cada persona ve solo su reto de ulti; la respuesta correcta es una por `id`, así que resultado, daño y turnos son
idénticos en cualquier idioma. Un idioma por batalla pediría columna nueva en `battles`, tocar `sx_battle_create` y decidir quién manda
cuando los dos juegan en idiomas distintos. **Si el grupo prefiere "idioma del que crea la sala", es otro script (0014).**

## 4. Compatibilidad hacia atrás

- Servidor con `0013` + app v2.3.0 o anterior: no envían `p_lang`; el valor por defecto da español, igual que hoy. Las firmas viejas se
  eliminan (`drop function`) para que PostgREST no vea dos funciones y no haya ambigüedad.
- App v2.4.0 + servidor **sin** `0013`: con inglés la llamada falla con "función no encontrada" (PGRST202 → `SocialError.Unavailable`);
  el repositorio repite la llamada sin `p_lang` y no vuelve a enviarlo mientras la app siga abierta. La batalla sigue en español
  (el sondeo de 2 a 3 s no se interrumpe). Probado en `BattleLanguageTest`.
- Edge Function: sin `lang` (apps viejas) o sin redesplegar, sale en español.

## 5. Archivos de Supabase y orden de ejecución (a mano, por el equipo)

1. `supabase/migrations/0013_batallas_ingles.sql` (SQL Editor, de una vez; idempotente, una transacción).
2. `supabase/verificacion/0013_verificacion.sql` (solo SELECT; esperado: 72|72|0|12|12|0 y lo que dice cada consulta).
3. Redesplegar la Edge Function `send-recovery-email-code` (ver su README), cuando se apruebe.
4. Pegar las tres plantillas de `docs/supabase/plantillas-correo-bilingue.md` en Authentication → Emails → Templates.
5. Reversa si hiciera falta: `supabase/rollback/0013_rollback.sql` (devuelve las firmas y cuerpos de `0008`/`0011` y borra las tablas).

Tras aplicar `0013`, documentar en `supabase-configuracion.md` las advertencias esperadas (misma razón que `0008`; 2 tablas cerradas más).

## 6. Pendiente que NO se hizo a propósito

- **Quitar el aviso `social_arena_questions_spanish_only`**: se deja hasta que el equipo aplique `0013` y apruebe un commit final que lo elimine
  (con su gemelo en `values-en`; `TranslationParityTest` exige quitar las dos).
- `settings_language_notice_text` no cambia: sigue siendo cierto (los correos de Auth llevan ambos idiomas y los textos que llegan del servidor no se traducen).

## 7. B3: otros textos fuera de recursos

- Errores de red, Workers, notificaciones: ya son `UiText`; el Worker no tiene textos propios. No hay widgets ni atajos.
- Corregido: nombres de canal y notificaciones (idioma explícito), fecha de nacimiento.
- Se deja en español **a propósito**: las claves del JSON de "Exportar mis datos" (`nombre`, `correo`, …) son el formato del archivo; sus valores sí siguen el idioma.
- Viene del servidor y no se traduce: mensajes de error de Supabase Auth que no se mapean, nombres de usuario, y los correos hasta que se peguen las plantillas.

## 8. Riesgos y deuda

- Traducciones de las 72 preguntas y los 12 títulos: PROPUESTA hasta que el grupo las revise (el glosario se respetó).
- `BirthDate.display` en inglés usa el formato medio de la JVM/Android del idioma (`Mar 7, 1999`); Android real puede variar levemente por versión de ICU.
- Una persona que cambia de idioma en plena batalla ve la siguiente pregunta en el idioma nuevo (se lee en cada llamada).
- Las pruebas del SQL son estáticas (leen el archivo); el comportamiento real lo comprueba `0013_verificacion.sql` en el servidor.
- El idioma del correo de Auth no depende de la app: por eso son bilingües, no por idioma.

## 9. Lista para el emulador

1. **Notificación en ambos idiomas:** con Ajustes → Notificaciones, activar el recordatorio diario; forzar el trabajo (adb `cmd jobscheduler run` o cambiar la hora) y ver el texto. Cambiar a English en Ajustes → Idioma y repetir: el texto y el nombre del canal (Ajustes del sistema → App → Notificaciones) deben salir en inglés. Probar también en un emulador con Android 12 o menor.
2. **Batalla antes de aplicar `0013`:** con English, abrir una partida: debe funcionar y las preguntas salir en español (sin error ni parpadeo del sondeo).
3. **Batalla después de aplicar `0013`:** con English, preguntas y título de la ulti en inglés; con Español, igual que antes. Responder bien y mal: el daño y los turnos son los de siempre.
4. **Fechas:** Perfil → fecha de nacimiento: `07/03/1999` en español y `Mar 7, 1999` en inglés; Editar perfil igual. Cambiar la zona horaria del teléfono: no cambia la fecha mostrada.
5. **Cambio de idioma con la app abierta** (perfil, batalla, ajustes): la pantalla se recrea en el idioma nuevo sin perder la sesión ni la batalla en curso.
6. **Correo:** pedir un registro, un cambio de correo, una recuperación y un correo de recuperación alterno (tras pegar/redesplegar): llegan con código y los dos idiomas (el alterno, solo en el idioma de la app).
