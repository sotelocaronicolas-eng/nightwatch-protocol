# Etapa 8 · Inglés de la interfaz — lista de verificación (emulador o teléfono)

Claude Code no ve el teléfono ni un emulador: **nada de esto se ha mirado en pantalla**. Las pruebas unitarias cubren la paridad de los textos y la lógica del aviso, no el aspecto.
Rama `modulo-5-ingles-ui`. Textos a revisar: `docs/superpowers/translations/ui-es-en.csv`. Contenido JSON (lecciones, labs, examen, quiz y sus preguntas) **sigue en español** (etapa 9);
las preguntas de la arena también (Postgres).

## Cómo probar
- **Idioma:** Español (debe verse **idéntico a v2.3.0**) e English (Perfil > Ajustes > Idioma).
- **Aspecto:** Oscuro y Claro (Apariencia). **Fuente:** normal y grande (escala 1.3 o más). **Rotación:** vertical y horizontal en las pantallas marcadas ↻.
- En cada pantalla, en inglés: nada cortado, solapado ni con "…" donde antes cabía; botones, chips, pestañas y tarjetas completos; emoji visibles; barras del sistema coherentes.
- Marca: ☐ es oscuro · ☐ es claro · ☐ en oscuro · ☐ en claro · ☐ en fuente grande · ☐ en rotación.
- **Sospechas de desborde (inglés más largo; ver la columna `revisar_longitud` de la tabla):** subtítulo del Bloqueo Biométrico; "What does it cover?"/"What do I get?" del examen; mensajes de Social
  ("You are already friends.", "The maximum number of friends was reached."); botones de la arena; pestañas de la barra inferior (Home, Path, Labs, Social, Profile); chips "LVL"; banners de la racha en pareja.

## A. Idioma y aviso
| Paso | Esperado |
|---|---|
| Abrir Idioma con Español | ✓ y borde en Español; **sin** aviso |
| Tocar English | La actividad se recrea sin cerrarse; los textos pasan a inglés; ✓ en English; aparece el aviso "Some server messages or emails may still arrive in Spanish." bajo las opciones |
| Tocar Español | Vuelve a español; el aviso desaparece |
| Cerrar y abrir la app | Conserva el idioma |
| Teléfono en inglés + instalación limpia | Abre en **español** (A4); el aviso no aparece |
| Android 13+: cambiar el idioma desde Ajustes del teléfono | La pantalla Idioma lo refleja al volver |
| Aviso en claro/oscuro y fuente grande | Legible y sin cortar |

## B. Pantalla por pantalla (es y en)
| Pantalla | Qué mirar en inglés |
|---|---|
| Idioma ↻ | Filas, ✓, aviso (ver A) |
| Menú de usuario, Perfil ↻, Editar Perfil (selector de fecha, géneros), Logros e Insignias | Secciones en mayúsculas (ACCOUNT, PRIVACY AND SECURITY…), botón SIGN OUT, nombres de las 9 insignias |
| Ajustes: Notificaciones, Apariencia, Privacidad y Seguridad, Dispositivos, Ayuda, Acerca de | Subtítulos de dos líneas, plural "1 active device / 2 active devices" |
| Login ↻ | "Username/Email", "Keep me signed in", "Don't have an account yet? Sign up here" (espacio antes del enlace), errores y diálogos |
| Registro ↻, Recuperación de contraseña, Nueva contraseña, Verificar código ↻ | "6-digit code" en título, pista y descripción; "Resend in 30 s" |
| Quiz ↻ | "Question 1 of 20", categorías con emoji, resultado ("Your level: HIGH") y plan de estudio; **las preguntas siguen en español** |
| Inicio | Racha ("Active Streak: 1 day 🔥" / "days"), tarjetas "Daily lesson", "New concept", "Get ready with CiberMynd", avisos "Coming soon" |
| Ruta de aprendizaje | "Learning Path", "3 of 6 islands completed", bloqueos "Complete the previous island", estados accesibles |
| Labs | Tarjetas, dificultades (Easy/Medium/Hard/Exam), "Start →", "Final Exam", bloqueos |
| Un Lab ↻ (terminal) | "OBJECTIVES · TAP TO USE THE COMMAND", placeholder "type a command…", los **comandos no cambian**; el contenido del lab sigue en español |
| Informe del lab (éxito y fallo) | Título, "Back to the path", "Retry saving" |
| Lección ↻ | "MODULE 1 of 6", "Go to the lab →" (el texto de la lección sigue en español) |
| Examen: introducción, preguntas, resultado (aprobado, 1 error, no aprobado) | Títulos con emoji, "Exam passed with 1 mistake!", botón OK |
| Social: Inicio | Tarjetas "Pair streak", "Friends", "Battles", marcador "5W · 2L" |
| Social: Amigos, Agregar amigos, Solicitudes, Personas bloqueadas, Perfil de un amigo | Plurales ("1 keeps / 2 keep their streak today"), diálogos Reportar y Bloquear |
| Social: Racha en pareja (lista y detalle) ↻ | Iniciales de la semana M T W T F S S, banners, "End pair streak" |
| Social: Batallas ↻ y Partida rápida ↻ | "Quick match", "Find opponent →", "Challenge", cuenta regresiva |
| **Arena ↻** | Encabezado "Battle vs …", "Your turn", "HP", racha de Ulti, avisos de espera y desconexión, pantalla de Ulti y escudo, diálogo "Leave the battle?"; **las preguntas y opciones siguen en español**; con fuente grande nada se corta ni tapa los botones |
| Resultado de batalla | "Victory!", "Defeat", "Draw", XP, nivel de combatiente, "Rematch" |
| Mi perfil social y Editar mi perfil | "Your Profile", ojos de visibilidad, "Change background" |

## C. Comportamiento
| Paso | Esperado |
|---|---|
| Cambiar de idioma con un reto pendiente en Batallas / dentro de la arena | El reto o la batalla continúan (R-01, etapa 7) |
| Cambiar de idioma con el teclado abierto o escribiendo | Se conserva lo escrito (si no, anotar la pantalla) |
| Rotar en inglés con fuente grande en las pantallas ↻ | Sin cortes |
| Exportar mis datos en inglés (Android 13+ y anterior) | El archivo sale en inglés en Android 13+; antes de 13 sigue el idioma de la app |
| Notificaciones de recordatorio | **Siguen en español** (etapa 10) |
| Fechas ("Member since Nov. 2026") y formato de fecha de nacimiento | Mes abreviado en inglés; el formato de la fecha **no** cambia todavía (etapa 10) |
| TalkBack en inglés | Lee las descripciones (fotos, ojos, insignias, objetivos del lab) en inglés |

## D. Si algo falla
- Anota: pantalla, idioma, aspecto, tamaño de fuente y orientación; adjunta captura. El texto se corrige en `app/src/main/res/values-en/<archivo>` (nunca en `values/`).
- Un texto que sigue en español puede ser contenido JSON o de Postgres (esperado) o una cadena que faltó: revisa la clave con la tabla.
