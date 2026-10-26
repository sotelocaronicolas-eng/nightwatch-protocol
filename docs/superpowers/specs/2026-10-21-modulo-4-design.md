# Módulo 4: Social (amigos, rachas de pareja y batallas): spec de diseño

Estado: **v2, decisiones cerradas el 2026-10-21 (solo especificación).** No hay código de la app ni cambios en recursos o Gradle. Las
decisiones D1 a D17 ya las tomó el grupo; las preguntas abiertas O1 a O15 de la v1 se respondieron el 2026-10-21 y están en la sección 12
("Decidido por el grupo"). **No queda ninguna pregunta abierta;** solo quedan dos cosas por confirmar al cierre (versión, sección 12).
Fecha: 2026-10-21. Rama: `modulo-4` (creada desde `main`, igual a `origin/main` en ese momento). Figma: fileKey `wd3fQTzF5NqsfGfdR4I1lb`.
Versión de partida: **2.1.0** (`versionCode` 4). Este módulo es desarrollo nuevo, no una migración: `supabase/migrations/` es solo la carpeta de los scripts
de la base de datos.

**Cómo leer las marcas.** Cada regla lleva su origen: **D#** (decisión del grupo), **F** (Figma, con el nodo), **P** (propuesta técnica de esta
spec: se puede revisar, no es decisión) y **O#** (decisión del grupo del 2026-10-21 sobre una de las preguntas abiertas de la v1, sección 12).
Lo que no está en Figma ni en las decisiones no se inventó: está marcado **P** con su alternativa.

## Estado

- **Estado al cierre (2026-10-23):** el módulo está implementado. Scripts `0008` a `0011` **aplicados y verificados** en Supabase (guion de `0008`: 219 de 219; guion
  de `0011`: 187 de 187; Security Advisor: 10 advertencias, 0 errores, 14 sugerencias), versión 2.2.0, y probado por el grupo en dos teléfonos. El informe de cierre es
  `docs/superpowers/reports/modulo-4-informe.md`. Lo que sigue de este documento es la especificación original, con las decisiones posteriores marcadas (O15, fotos, D20).
- La spec no cambia nada del módulo 3 ni de `CLAUDE.md`. `CLAUDE.md` se actualiza en la etapa de cierre (sección 10).
- Se verificó en esta etapa: la lectura de los documentos del repo, el texto de las capas y las capturas de los frames de Social en Figma, y los
  límites actuales del plan gratuito en la documentación oficial de Supabase (consultada el 2026-10-21; ver la sección 5).
- **No se verificó:** nada contra un Supabase real (Claude Code no se conecta), nada en un teléfono, la API de `realtime-kt` 3.2.4 en Kotlin
  para canales privados (la documentación consultada solo cubre el cliente de JavaScript; hace falta una prueba de concepto, sección 5) ni cómo cuenta
  Supabase cada mensaje de Realtime.

## Fuentes

| Fuente | Qué aporta |
|---|---|
| `CLAUDE.md` | Reglas del proyecto, de Supabase y de git |
| `docs/superpowers/specs/2026-10-18-modulo-4-social-analisis.md` | Análisis previo: qué datos de otras personas habría que leer y cómo protegerlos |
| `docs/superpowers/specs/2026-10-18-modulo-3-design.md` (secciones 8, 13 a 16) y `reports/modulo-3-diagnostico.md` | Modelo de `0007`, racha e insignias (sección 15), pendientes del módulo 4 |
| `docs/superpowers/supabase-configuracion.md` | Privilegios, RLS, `0001` a `0007`, advertencias aceptadas del Security Advisor |
| `supabase/migrations/0001` a `0007` | Convenciones de SQL (`(select auth.uid())`, `GRANT` por columna, `revoke` previo) |
| Código leído (sin cambiarlo) | `data/learning/Badges.kt` (`BadgeFacts`), `Streak.kt` (`computeStreak`), `UserPreferences.kt` (`HIDE_PUBLIC_PROGRESS`), `ui/components/*`, `gradle/libs.versions.toml` |
| Figma `wd3fQTzF5NqsfGfdR4I1lb` | 17 frames de Social (sección 1) |
| Documentación de Supabase (Realtime Limits, Pricing, Edge Functions Limits, Cron, Broadcast, Postgres Changes) | Límites y opciones de tiempo real (sección 5) |

## Decisiones del grupo (resumen: D1 a D17, y desde el 2026-10-21 O1 a O15 y D18 a D20)

| # | Resumen |
|---|---|
| D1 | La ulti se carga con 3 aciertos seguidos. Con la ulti cargada se puede responder una pregunta normal y la ulti sigue cargada |
| D2 | Cada pregunta dura 1 minuto; si se acaba, se pierde el turno. Mientras responde el rival hay una pantalla de espera (no existe en Figma) |
| D3 | Correcta: -20 HP al rival. Incorrecta: sin daño y pasa el turno. HP total 100 |
| D4 | Ulti: los dos reciben a la vez un reto de terminal de 2 minutos (ataque y defensa, distintos). Atacante no resuelve o resuelve mal: no pasa nada y pierde la carga. Resuelve bien: 45 HP. Si el defensor resolvió antes: escudo, mitad del daño redondeada |
| D5 | Desconexión: 1 minuto de gracia; si no vuelve, pierde |
| D6 | Emparejamiento ±3 niveles de combatiente, que se amplía; a los 60 s se sugiere retar a un amigo. Partida rápida y reto a un amigo usan la misma batalla |
| D7 | La app solo muestra la cuenta regresiva. El servidor controla tiempos, turnos, aciertos y daños. Las respuestas correctas no viajan al teléfono antes de responder |
| D8 | XP y nivel de combatiente aparte del nivel de nivelación; solo las batallas dan XP; niveles 1 a 20 = primera ruta; más allá, fuera de alcance |
| D9 | Banco de preguntas nuevo, por nivel de combatiente, temas de la primera ruta, mínimo 12 por tema (72 en total); borrador mío, revisión del grupo |
| D10 | Nombre de usuario único al habilitar Social, más un id único aparte que sirve de código de amigo; invitar por usuario o por id |
| D11 | "Ocultar mi progreso público" oculta nivel, logros e islas |
| D12 | Se QUITAN las "Sugerencias" del proyecto, también de AGREGAR AMIGOS y SOLICITUDES |
| D13 | Racha compartida solo de a parejas, inicia con invitación aceptada, suma un día cuando los DOS completan su ejercicio del día ese día, hora de Colombia; varias parejas por persona; solo se muestran las rachas acordadas |
| D14 | "Ejercicio del día" = una lección con su laboratorio, no repetible; con 6 lecciones y 6 labs la racha de pareja no pasa de 6 días (no coincide con los 12 de Figma; aceptado) |
| D15 | RACHA GRUPAL pasa a ser la lista de parejas con su racha; al tocar una, su detalle. El contador de afuera es la racha de la pareja con más días |
| D16 | Sin notificaciones fuera de la app. Invitaciones y retos se ven al abrir Social. La invitación a retar dura 2 minutos |
| D17 | Pantallas que faltan: se crean siguiendo el diseño existente, se ajustan al probarlas en el teléfono; sin estilo nuevo |
| O1 | XP: 100 XP por nivel (nivel = 1 + XP ÷ 100, tope 20 con 1.900 XP). Victoria +50, empate +25, derrota +10 |
| O2 | Empieza una persona elegida al azar por el servidor. La batalla termina con HP 0 o a los 10 turnos por jugador (20 en total): gana quien tenga más HP, y con el mismo HP es empate. Si los dos se desconectan más de 60 s: cancelada, sin ganador ni XP, no cuenta en el marcador. El empate no rompe ni suma la racha de batallas |
| O3 | El nombre de usuario **no** se puede cambiar |
| O4 | Una invitación a racha de pareja vale 3 días; máximo 5 pendientes enviadas a la vez (recibidas sin tope) |
| O5 | Sin ejercicios por hacer: nada. La racha se detiene y se explica en pantalla ("Ya completaste las 6 islas"); el botón "Hacer mi ejercicio de hoy →" se deshabilita con ese texto |
| O6 | Usuario: 3 a 20 caracteres (letras, números y `_`), único sin distinguir mayúsculas, sin lista de palabras prohibidas. Código de amigo: `NIGHT-` más 6 caracteres sin ambigüedades (sin 0/O ni 1/I), generado por el servidor |
| O7 | Máximo 50 amigos; 20 solicitudes pendientes enviadas; 24 horas de espera para volver a invitar a quien rechazó |
| O8 | La pareja de racha exige amistad aceptada. Eliminar al amigo termina la pareja y se pierde la racha |
| O9 | El día de aceptación cuenta, y cuenta cualquier ejercicio completado ese día de Colombia (regla por día, no por hora) |
| O10 (**cambia** respecto de la propuesta) | Ulti: cada jugador tiene **hasta 3 intentos** dentro de los 2 minutos; un comando incorrecto consume un intento y no termina el reto. **El comando NO se muestra en pantalla:** se sabe de memoria. Los retos usan **exactamente los comandos de los laboratorios** del módulo 3 (tal como están en `learning_labs.json`). El primero en acertar gana la carrera: si el atacante agota sus 3 intentos o se acaba el tiempo, no pasa nada y pierde la carga; si el defensor acierta antes de que acierte el atacante, activa el escudo (mitad del daño, redondeada); si el defensor falla o se queda sin tiempo, no hay escudo. Usar la ulti gasta el turno (de la propuesta, sin cambio) |
| O11 | El rango de ±3 niveles se amplía +1 nivel cada 10 s de espera, sin tope |
| O12 | Escalones de dificultad: niveles de combatiente 1 a 7, 8 a 14 y 15 a 20; no hace falta haber visto la lección de un tema; ampliar el banco después si se repite |
| O13 | "Revancha" envía un reto de 2 minutos al mismo rival (solo si es amigo; en partida rápida no aparece). La "X" de la arena pide confirmación y abandonar da la derrota |
| O14 | "Nivel" en D11 oculta el de nivelación y el de combatiente en el perfil (el de combatiente se sigue usando en el emparejamiento sin mostrarse). Solo emojis, sin fotos de amigos |
| O15 (**reemplazada el 2026-10-22**) | Se **puede reportar a cualquier persona** (búsqueda, solicitudes, perfil del amigo y rival de una batalla, también la partida rápida) y **bloquear**. Ningún número de reportes oculta una foto solo: el equipo lo hace a mano (`avatar_hidden`). Bloquear no termina una batalla en curso. Sin pantalla de moderación: el equipo revisa `social_reports` en el Table Editor. Script `0011` (**aplicado y verificado el 2026-10-23**, guion 187 de 187). Antes: "fuera del módulo" |
| D18 | Tiempo real: **se empieza con sondeo**, sin dependencia nueva. `realtime-kt` solo se agrega si el equipo lo aprueba después de una prueba de concepto; queda como **mejora opcional posterior** |
| D19 | El rótulo de la tarjeta "Racha grupal" de SOCIAL - INICIO pasa a **"Racha en pareja"** |
| D20 | Versión del cierre del módulo: **2.2.0**, `versionCode` 5 (**confirmada y aplicada el 2026-10-23**: el valor anterior era `versionCode` 4) |

## 1. Inventario de pantallas del Figma y pantallas que faltan

### 1.1 Las 17 pantallas de Figma

"Texto de Figma" sale de las capas y de las capturas del frame. Los botones de cada frame son los que se ven en la captura. Los números de los
ejemplos (12 días, 47 ejercicios, 8 amigos) son datos de muestra de Figma, no reglas.

| # | Frame (nodo) | Qué dibuja (F) | Qué cambian las decisiones | Qué se reutiliza del proyecto |
|---|---|---|---|---|
| 1 | SOCIAL - INICIO (2440:163) | Título "Social"; frase "Mantén tu racha con amigos y demuestra lo que sabes en batallas de preguntas."; 3 tarjetas: Racha grupal ("12 días", "Ver racha →"), Amigos ("8 amigos", "Ver amigos →"), Batallas ("5V · 2D", "Batallar →"); barra inferior con Social activa (`BottomNav` 2440:212) | La tarjeta 1 se llama **"Racha en pareja"** (D19, no "Racha grupal") y muestra la racha de la pareja con más días (D15). Social deja de estar atenuada | `HomeTopBar`, `NightWatchScreenBackground`, `NightWatchBottomBar` (hoy Social se dibuja atenuada con "Próximamente"; pasa a pestaña activa), tarjetas como las de Inicio |
| 2 | SOCIAL - AMIGOS (2446:181) | "8 amigos · 3 mantienen la racha hoy"; filas con avatar emoji, nombre, "🔥 N días" o "Sin racha activa", "✅ Ya entrenó hoy" / "⏳ Pendiente hoy" | El nombre es el nombre de usuario (D10). "Racha" es la racha personal del amigo (sección 8) | `TitleTopBar`, filas como `SettingsRow`, `UserAvatar`, `StreakText` |
| 3 | SOCIAL - AGREGAR AMIGOS (2446:212) | "Buscar por usuario" (`ej. valeNightwatch`), "Tu código de amigo" `NIGHT-7F3K` con "Copiar", enlace "📥 Solicitudes de amistad" con contador, bloque "Sugerencias" con "Agregar +" | **Se quita "Sugerencias"** (D12). Se busca por usuario **o por id** (D10) | `NightWatchTextField`, `ProfileButton`, `SettingsRow` |
| 4 | SOCIAL - SOLICITUDES (2446:242) | "Tienes 2 solicitudes de amistad pendientes."; recibidas con "Aceptar" y "Rechazar"; "Solicitudes enviadas" con "Cancelar solicitud"; bloque "Sugerencias" con "Agregar +" | **Se quita "Sugerencias"** (D12) | Mismas filas y botones |
| 5 | SOCIAL - PERFIL AMIGO (2446:265) | Nombre, "🔥 12 días de racha", "47 Ejercicios" y "5 Labs superados", "Insignias" (3 tarjetas), "Historial de Batallas" ("Ganaste/Perdiste vs X, hace N días"), "Eliminar amigo", "⚔️ Batallar" | D11: con "ocultar progreso" se oculta el nivel, las insignias y las islas (ejercicios y labs). Falta el botón "Invitar a una racha de pareja" (sección 1.2) | Tarjeta de insignia de `Achievements`, `UserAvatar`, `ProfileButton` |
| 6 | SOCIAL - RACHA GRUPAL (2447:186) | "12 días seguidos entrenando", semana L-D con ✓, aviso "⚠️ Aún no entrenas hoy. ¡No pierdas tu racha!", "Tus amigos hoy" (4 filas con ✅/⏳), "Hacer mi ejercicio de hoy →" | **Cambia (D13, D15):** pasa a lista de parejas; el detalle de una pareja reutiliza este frame con una sola persona en "hoy" | Frame mismo, `StreakText` |
| 7 | SOCIAL - BATALLAS (2448:187) | "5 Victorias" y "2 Derrotas"; tarjeta "🎲 Partida rápida" ("Emparejamiento contra un rival de tu nivel", "Buscar oponente →"); "Retar a un amigo" con filas y "⚔️ Retar" | La partida rápida y el reto usan la misma batalla (D6). El reto dura 2 minutos (D16) | Filas de amigos de la pantalla 2 |
| 8 | SOCIAL - BUSCANDO OPONENTE (2448:223) | "Buscando oponente…", "Esto puede tardar unos segundos. Te avisaremos apenas encontremos un rival de tu nivel.", Tú **VS** ❔, "Cancelar búsqueda" | A los 60 s se sugiere retar a un amigo (D6). "Te avisaremos" ya no aplica fuera de la app (D16): el texto cambia | `NightWatchScreenBackground`, `NightWatchButton` |
| 9 | SOCIAL - ARENA PREGUNTA (2449:189) | "Batalla vs Mateo", "X", barras de HP de los dos ("80/100 HP"), "Tu turno", "Racha para tu Ulti: 2 de 3 aciertos", pregunta y 4 opciones A a D | Falta el **temporizador de 1 minuto** (D2) | Opciones A a D de `ui/exam`, `TitleTopBar` |
| 10 | SOCIAL - ARENA CORRECTO (2449:221) | La opción elegida en oro, "✅ ¡Correcto! -20 HP a Mateo", "Siguiente turno →" | HP del rival baja 20 (D3) | Igual |
| 11 | SOCIAL - ARENA INCORRECTO (2449:255) | Opción elegida con ✕, "❌ Incorrecto. Sin daño. Mateo toma su turno." | Igual (D3). La correcta **no** se revela | Igual |
| 12 | SOCIAL - ULTI DISPONIBLE (2449:290) | 🔥 "¡Ulti disponible!": "Respondiste 3 preguntas seguidas. Puedes lanzar tu Ulti ahora — pero necesitas resolver un reto de terminal para que sea efectiva.", botones "Guardar para después" y "🔥 Usar Ulti ahora" | "Guardar para después" = responder una pregunta normal con la ulti aún cargada (D1) | `NightWatchButton`, `ProfileButton` |
| 13 | SOCIAL - DESAFÍO ULTI (2450:193) | "Desafío de tu Ulti", tarjeta "Bloquea la IP atacante" con la chapa de comando `ufw deny from 198.51.100.9`, salida de terminal, campo `> escribe el comando…`, "Enviar" | El plazo es de 2 minutos (D4), con cuenta regresiva visible y hasta 3 intentos (O10). **Se quita la chapa con el comando:** en pantalla solo se ve el objetivo; el comando se sabe de memoria (O10) | Terminal de `ui/lab` (campo y salida); sin la chapa de comando |
| 14 | SOCIAL - ULTI ÉXITO (2450:211) | 💥 "¡Ulti efectiva!", "Resolviste el comando a tiempo. Tu Ulti impacta con fuerza total.", "-45 HP a Mateo", "Continuar →" | Si hubo escudo, el daño es la mitad (D4): falta el texto de esa variante | Igual |
| 15 | SOCIAL - ULTI FALLO (2450:223) | 💨 "Tu Ulti falló", "No resolviste el comando a tiempo. La IP atacante sigue activa y tu Ulti no causó daño.", "0 HP de daño" | También cuando el comando fue incorrecto (D4); texto de Figma habla solo de tiempo | Igual |
| 16 | SOCIAL - FIN BATALLA VICTORIA (2451:195) | 🏆 "¡Victoria!", "Derrotaste a Mateo en la batalla de preguntas.", "80/100 HP restante", "🎁 +50 XP", "🔥 Racha de batallas +1", "⚔️ 6 Victorias · 2 Derrotas", "Revancha" y "Volver a Social" | El XP por victoria queda en O1 (Figma: +50) | `ConfirmationContent` (patrón de resultado) |
| 17 | SOCIAL - FIN BATALLA DERROTA (2451:209) | 💀 "Derrota", "Mateo ganó esta batalla. ¡Sigue practicando y vuelve más fuerte!", "0/100 HP restante", "📚 Repasa Contraseñas y Phishing", "🔥 Tu racha de entrenamiento sigue intacta", "⚔️ 5 Victorias · 3 Derrotas", "Revancha", "Volver a Social" | "Repasa X y Y" necesita saber qué temas falló (sección 9). "Revancha": O13 | Igual |

### 1.2 Pantallas que FALTAN (D17: se crean con el diseño existente y se ajustan en el teléfono)

No se dibuja nada nuevo aquí: solo se dice qué falta y con qué se armaría.

| # | Falta | Cuándo aparece | Con qué se arma |
|---|---|---|---|
| M1 | **Crear nombre de usuario** (y mostrar el código de amigo recién creado) | La primera vez que alguien abre Social (D10) | `NightWatchTextField` con el mismo manejo de errores que los formularios del módulo 1 (largo, ya en uso), `NightWatchButton`, `NightWatchScreenBackground` |
| M2 | **Espera de turno** (responde el rival) | Durante el turno del rival (D2) | Marco de la Arena (`TitleTopBar`, barras de HP del frame 2449:189), la píldora de turno `TurnPill` ("Tu turno" → "Turno de Mateo") y la cuenta regresiva del rival |
| M3 | **Temporizador visible** | En la pregunta (D2) y en el reto de ulti (D4) | Un texto de cuenta regresiva dentro de la píldora o de la barra del frame existente; no es una pantalla aparte |
| M4 | **Defensa de la ulti con escudo** | Cuando el rival lanza su ulti (D4) | Las pantallas 13 a 15 con otro texto: reto de defensa, estado "Escudo activo" y resultado "Recibiste N HP" |
| M5 | **Invitar a una racha de pareja** (enviar, recibir, aceptar, rechazar, terminar) | Desde Perfil amigo y desde la lista de parejas (D13, D15) | Filas "Aceptar / Rechazar / Cancelar solicitud" de SOLICITUDES (frame 4) |
| M6 | **Lista de parejas** (D15) | Reemplaza a RACHA GRUPAL como entrada | Filas del frame 2 con la racha de cada pareja |
| M7 | **Reto recibido y espera del reto** | En BATALLAS: "Retos recibidos" (2 minutos) y, para quien reta, "Esperando a Mateo…" con su cuenta regresiva (D16) | Filas del frame 7 y la cuenta de la pantalla 8 |
| M8 | **Sugerencia de retar a un amigo a los 60 s** | Dentro de BUSCANDO OPONENTE (D6) | Un bloque de texto y un botón en el frame 8 |
| M9 | **Fin de batalla por empate, por abandono, por desconexión y batalla cancelada** | Cierre de batalla (D5, O2) | Frames 16 y 17 con otro texto |
| M10 | **Rival desconectado / yo reconectando** | Durante la gracia de 1 minuto (D5) | Aviso sobre el marco de la Arena y cuenta regresiva |
| M11 | **Turno perdido por tiempo** | Se acabó el minuto (D2) | Banner del frame 11 con "Se acabó el tiempo. Sin daño." |
| M12 | **Estados vacíos y de error** | Sin amigos, sin solicitudes, sin parejas, sin batallas, búsqueda sin resultado, sin conexión, error de lectura, Social pausado | El patrón del módulo 3: aviso, "Reintentar" y estados de carga (`Notices.kt`, pantallas de Ruta y Labs) |
| M13 | **Elegir pareja para la racha** | Al aceptar una invitación o abrir una pareja | Es la misma pantalla M5 / M6 |

## 2. Diferencias entre el Figma y las decisiones

| # | Figma | Decisión | Qué se hace | Origen |
|---|---|---|---|---|
| S1 | RACHA GRUPAL: una racha de grupo de 4 amigos ("Tus amigos hoy"), 12 días | Solo parejas, varias por persona | La pantalla se vuelve una lista de parejas con su racha; el detalle usa el diseño del frame con **una** persona en "hoy" | D13, D15 |
| S2 | Tarjeta "Racha grupal / 12 días" en SOCIAL - INICIO | El contador de afuera es la racha de la pareja con más días | La tarjeta muestra esa cifra y se llama **"Racha en pareja"** (Figma dice "Racha grupal"). **Diferencia deliberada** | D15, D19 |
| S3 | Semana L-D con 5 ✓ y racha de 12 | Máximo 6 días | La racha de pareja nunca pasa de 6 (sección 7); el "12 días" de Figma no se alcanza. **Aceptado** | D14 |
| S4 | "Sugerencias" en AGREGAR AMIGOS y en SOLICITUDES | Se quitan | Se eliminan los dos bloques. **Diferencia deliberada.** No hay ninguna función que recomiende personas | D12 |
| S5 | Nombres propios (Valentina, Mateo) | Nombre de usuario único (D10) | Se muestra el nombre de usuario, no el nombre de la cuenta | D10 |
| S6 | "47 Ejercicios" y "5 Labs superados" | La ruta tiene 6 lecciones y 6 labs | Los contadores llegan como máximo a 6 cada uno. "Ejercicios" = lecciones vistas; "Labs superados" = labs completados (P) | P |
| S7 | "Racha de 12 días" como insignia del amigo | Insignias del módulo 3 | Se muestran las insignias reales del módulo 3 (sección 4.5), sin las que Figma inventa | P |
| S8 | `+50 XP` en la victoria | XP aparte, O1 | Se muestra el XP que se decida | O1 |
| S9 | "Te avisaremos apenas encontremos un rival" | Sin notificaciones fuera de la app | El texto se cambia: la búsqueda solo avanza con Social abierto | D16 |
| S10 | Social atenuada con "Próximamente" (módulo 3, F2) | Social habilitada | La pestaña pasa a activa; el aviso "Próximamente" de Social desaparece (las de "Concepto nuevo" y CiberMynd siguen) | Alcance |
| S11 | Arena sin temporizador ni pantalla de espera | D2 | Se agregan M2 y M3 | D2, D17 |
| S12 | ULTI: un solo reto, sin plazo numérico ("antes de que termine el turno") | Plazo de 2 minutos y defensa a la vez, con hasta 3 intentos cada uno | Reto de ataque + reto de defensa, cada uno de 2 minutos y 3 intentos | D4, O10 |
| S13 | Avatares de emoji (🧑, 🧙‍♂️) | Foto de perfil en un bucket privado (`0004`) | Se usan los emojis de Figma; mostrar la foto real de los amigos exigiría otra política de Storage (O14) | O14 |
| S14 | Marcador "5V · 2D" sin empates | Puede haber empate (O2) | Se agrega "E" al marcador cuando hay empates (por ejemplo "5V · 2D · 1E") | O2 |
| S15 | DESAFÍO ULTI muestra una chapa con el comando (`ufw deny from 198.51.100.9`) | El comando no se muestra; se sabe de memoria (O10) | Se quita la chapa; la tarjeta solo dice el objetivo. El comando de Figma **no** se usa: los retos usan comandos de los laboratorios | O10 |
| S16 | Tarjeta "Racha grupal" | Se llama "Racha en pareja" | Cambio de rótulo (ver S2) | D19 |

## 3. Modelo de datos nuevo para Social (propuesta; script `0008`)

**Entrega.** Un solo archivo `supabase/migrations/0008_social.sql`, escrito en una etapa posterior, con el encabezado "BORRADOR sin aplicar" como
`0007`. Lo ejecuta el usuario a mano en el SQL Editor, después de `0001` a `0007` y una sola vez. En esta etapa **no existe ese archivo**: lo de abajo
es solo la propuesta. Ninguna tabla nueva toca ni referencia `placement`, `profile`, `devices`, `recovery_email_codes`, `lesson_progress`, `lab_progress` ni
`exam_attempts` mediante claves foráneas (solo `auth.users`, como `0007`); las funciones de Social **leen** el progreso del módulo 3 y nunca escriben en él.

**Contenido en otro archivo (P).** El banco de preguntas y los retos de ulti van en `0009_banco_batallas.sql` y no dentro de `0008`: el grupo va a revisar
y corregir preguntas, y un script ya aplicado no se edita (`CLAUDE.md` sección 4); así una corrección es otro archivo, no un arreglo de `0008`.
La curva de XP (O1) también va en `0009`.

**Estado:** el script **ya está escrito** (`supabase/migrations/0008_social.sql`, borrador sin ejecutar, Etapa 1). Lo que sigue describe lo que ese archivo contiene; donde difiere de
la propuesta de la v1 de esta spec, se indica.

**Regla general de acceso.** Las tablas de varias personas (amistades, parejas, batallas, cola, retos) y los dos bancos son **cerradas**: RLS activo,
**sin políticas y sin `GRANT`** para `authenticated` y `anon`, el mismo patrón de `recovery_email_codes` (`0006`). Solo se leen y escriben con funciones
`SECURITY DEFINER` (sección 4). Motivo: el análisis previo prohíbe exponer el `user_id` de Auth de otra persona (punto 4 de su sección 3), y una política de
"participantes" daría la fila completa, que lleva los dos ids. Solo **tres** tablas tienen acceso directo, todas sin ids de otras personas: `social_profile` (política de dueño),
`battle_results` (política de dueño, solo lectura) y `fighter_level_curve` (dato de referencia, solo lectura).
**Menos superficie:** en vez de las 32 funciones públicas de la v1, el script deja **5** `SECURITY DEFINER` (una por dominio, con el verbo como parámetro) y una auxiliar `SECURITY INVOKER`; el
resto de la lógica vive en 20 funciones internas que la app no puede llamar.

### 3.1 Tablas

| Tabla | Clave | Columnas (propuesta) | Quién lee / escribe |
|---|---|---|---|
| `social_profile` | `user_id` | `username` (único sin distinguir mayúsculas), `friend_code` (único), `hide_progress` boolean, `created_at`. FK `user_id` → `auth.users` con `on delete cascade` | Dueño: `SELECT` de su fila, `INSERT (user_id, username)` y `UPDATE (hide_progress)`. **Sin función de alta:** el servidor genera `friend_code` con un valor por defecto (`social_new_code()`, formato `NIGHT-` + 6 caracteres, O6) y un nombre repetido lo rechaza el índice único (sin distinguir mayúsculas). El nombre no se puede cambiar (O3) |
| `friendships` | `id` | `user_low`, `user_high` (con `user_low < user_high`, un par = una fila), `requested_by`, `status` (`pending`, `accepted` o **`declined`**, que solo se conserva para la espera de 24 horas, O7), `created_at`, `responded_at`. Único (`user_low`, `user_high`) | Cerrada. Solo `social_friends` |
| `pair_streaks` | `id` | `user_low`, `user_high`, `invited_by`, `status` (`pending` o `active`; una pareja rechazada, cancelada, terminada o vencida **se borra**), `created_at`, `expires_at` (+3 días, O4), `accepted_at`. Único (`user_low`, `user_high`) | Cerrada. Solo `social_pairs`. **No guarda el contador**: la racha se deriva (sección 7) |
| `battle_questions` | `id` | `topic` (los 6 temas de `0007`), `tier` (1 a 3), `question`, `option_a` a `option_d`, `correct` (A a D), `active` | **Cerrada, sin ningún permiso**: las respuestas nunca salen (D7) |
| `ulti_challenges` | `id` | `kind` (`attack` o `defense`), `topic`, `objective_title` (lo único que se muestra), `expected_command` (nunca sale del servidor), `active` | Cerrada |
| `fighter_level_curve` | `level` | `level` 1 a 20, `min_xp` | **Solo lectura para `authenticated`** (dato de referencia, no sensible: la app calcula el nivel con ella); contenido en `0009` (O1). Vacía = todos son nivel 1 |
| `matchmaking_queue` | `user_id` | `fighter_level`, `joined_at`, `last_seen_at`, `battle_id` (nulo mientras espera) | Cerrada |
| `battle_active` (nueva respecto de la v1) | `user_id` | `battle_id` | Cerrada. Su clave primaria es lo que impide estar en dos batallas a la vez (reemplaza al "índice único parcial") |
| `battle_challenges` | `id` | `challenger_id`, `challenged_id`, `created_at`, `expires_at` (= `created_at` + 2 minutos, D16), `status` (`pending`, `accepted`, `declined`, `cancelled`, `expired`), `battle_id` | Cerrada |
| `battles` | `id` | `mode` (`quick` o `friend`), `status` (`active`, `finished`, `cancelled`), `player1_id`, `player2_id`, `hp1`, `hp2` (0 a 100), `correct_streak1`, `correct_streak2` (0 a 3), `ulti_charged1`, `ulti_charged2`, `turn_seat` (1 o 2), `turn_number`, `turn_phase` (`question`, `ulti`), `turn_deadline`, `current_question_id`, `last_seen1`, `last_seen2`, `version`, `winner_seat`, `end_reason` (`hp_zero`, `turn_limit`, `forfeit_disconnect`, `abandon`, `both_disconnected`), `created_at`, `finished_at` | Cerrada |
| `battle_turns` | (`battle_id`, `turn_number`) | `seat`, `question_id`, `outcome` (`correct`, `incorrect`, `timeout`, `ulti`), `chosen_option`, `damage`, `created_at`. Sirve de bitácora y evita repetir preguntas dentro de una batalla | Cerrada |
| `battle_ultis` | `id` | `battle_id`, `turn_number`, `attacker_seat`, `attack_challenge_id`, `defense_challenge_id`, `started_at`, `deadline` (= `started_at` + 2 minutos), `attacker_attempts` (0 a 3), `attacker_result` (`pending`, `solved`, `failed`), `attacker_at`, `defender_attempts` (0 a 3), `defender_result`, `defender_at`, `shield`, `damage`, `resolved_at` | Cerrada |
| `battle_results` | (`battle_id`, `user_id`) | `result` (`win`, `loss`, `draw`), `xp_awarded`, `hp_left`, `opponent_username` (foto del nombre del rival al cerrar, para el historial sin exponer su id), `finished_at`. Dos filas por batalla | **El dueño lee sus filas** (`SELECT` con política de dueño); nadie escribe desde la app. De aquí salen XP, marcador, historial y racha de batallas |
| `social_rate_limit` | (`user_id`, `action`) | `window_start`, `count` | Cerrada. Frena la búsqueda de usuarios y las invitaciones (sección 4.4) |

**Borrado de cuenta (P).** Todas las claves hacia `auth.users` con `on delete cascade`, como `0007`. Si una cuenta se elimina, sus batallas y sus filas de
resultados se borran, y a la otra persona le desaparece ese historial. Alternativa: dejar la batalla con el id en nulo. No se decide aquí.

### 3.2 Funciones (agrupadas: 5 `SECURITY DEFINER` públicas, 1 `INVOKER` auxiliar, 20 internas)

Ninguna recibe un `user_id`: la persona sale siempre de `auth.uid()`. Cada función pública recibe la **acción** como parámetro (lista cerrada; otra acción da `bad_action`) y devuelve `jsonb`.
Todas con `set search_path = ''`, `revoke all ... from public, anon` y `grant execute ... to authenticated`. **Ninguna es invocable por `anon`.**

| Función pública (`SECURITY DEFINER`) | Acciones | Qué reemplaza de la lista de la v1 |
|---|---|---|
| `social_friends(p_action, p_arg)` | `list`, `requests`, `search`, `invite`, `accept`, `decline`, `cancel`, `remove`, `profile` | 8 funciones de amigos |
| `social_pairs(p_action, p_arg)` | `overview`, `detail`, `invite`, `accept`, `decline`, `cancel`, `end` | 5 funciones de parejas |
| `battle_lobby(p_action, p_arg)` | `quick_join`, `quick_poll`, `quick_cancel`, `challenge_send`, `challenge_accept`, `challenge_decline`, `challenge_cancel`, `challenges` | 7 funciones de cola y retos |
| `battle_sync(p_battle)` | (sondeo) latido + resolución de plazos + vista personal de la batalla | `battle_state`, `battle_heartbeat` |
| `battle_play(p_battle, p_action, p_turn, p_arg)` | `answer`, `use_ulti`, `ulti_submit`, `abandon` | `battle_answer`, `battle_use_ulti`, `ulti_submit`, `battle_abandon` (y `battle_skip_ulti`, que ya no hace falta: "Guardar para después" es seguir respondiendo la pregunta del turno) |
| `social_new_code()` (`SECURITY INVOKER`) | — | Valor por defecto de `friend_code`; solo devuelve texto aleatorio |

**Se eliminaron de la v1** (no hacen falta): `social_enable` y `social_set_hide_progress` (el dueño inserta y actualiza `social_profile` directamente con permisos por columna), `social_me` (es un `SELECT` de su fila),
`fighter_summary` y `battle_history` (son `SELECT` sobre `battle_results`; el nivel sale de `fighter_level_curve`, ambas legibles). Antes de agrupar eran **32** funciones públicas; ahora son **5** (más 1 auxiliar).

**Internas** (20, prefijo `sx_`, `SECURITY INVOKER`, sin `EXECUTE` para `anon` ni `authenticated`; solo las llaman las 5 de arriba): `sx_username`, `sx_resolve_target`, `sx_are_friends`, `sx_friend_count`,
`sx_rate_limit`, `sx_personal_streak`, `sx_islands_done`, `sx_pair_metrics`, `sx_xp`, `sx_level`, `sx_user_level`, `sx_normalize_command`, `sx_pick_question`, `sx_battle_create`, `sx_battle_finish`,
`sx_advance_turn`, `sx_ulti_resolve`, `sx_battle_resolve`, `sx_battle_state`, `sx_try_match`.

### 3.3 Privilegios, con `GRANT` explícito (P)

El proyecto tiene "Automatically expose new tables" desactivado y "Automatic RLS" activado (`CLAUDE.md` sección 4), así que el script hace, como `0007`:

1. `enable row level security` en las 14 tablas.
2. `revoke all` de cada tabla para `anon` y `authenticated`, **antes** de cualquier `grant`.
3. `authenticated` recibe: en `social_profile`, `select`, `insert (user_id, username)` y `update (hide_progress)`; en `battle_results` y `fighter_level_curve`, solo `select`. Políticas: dueño `(select auth.uid()) = user_id` en las dos primeras y `using (true)` en la curva. Las otras 11 tablas quedan sin políticas y sin `GRANT`.
4. Cada función pública: `revoke all ... from public, anon` y `grant execute ... to authenticated` (el patrón de `0003` y `0006`; Supabase y PostgreSQL dan `EXECUTE` por defecto, así que el `revoke` es obligatorio).
5. Las funciones internas: `revoke all ... from public, anon, authenticated`.
6. Nada de `TRUNCATE`, `REFERENCES` ni `TRIGGER` (`0002`).

### 3.4 Tiempos, ulti y racha de pareja en el script

- **Tiempos con la hora del servidor.** Todo plazo (60 s por pregunta, 2 minutos de ulti, 60 s de gracia, 2 s de tolerancia de red) se guarda y se compara con `clock_timestamp()` de la base de datos. Ninguna función recibe una hora del teléfono.
- **Sin `pg_cron`.** `sx_battle_resolve` resuelve, bajo el candado de la fila de la batalla y **antes** de cada acción o sondeo: desconexión (60 s sin latido: pierde; los dos: cancelada), turno vencido y ulti vencida. `battle_lobby` resuelve primero la batalla activa de quien llama.
- **Ulti (O10).** `battle_ultis` guarda los intentos (máximo 3 por jugador) y la hora del servidor de cada acierto. `sx_ulti_resolve` aplica: atacante acierta = 45 HP, o 23 si el defensor acertó **antes** (estrictamente anterior); atacante sin intentos o sin tiempo = no pasa nada y pierde la carga. El comando esperado nunca sale del servidor; al teléfono solo llega el título del reto propio.
- **Racha de pareja (D13, D14).** `sx_pair_metrics` la calcula con la fecha de completado de cada isla (la mayor entre su lección y su lab), hora de Colombia, desde el día de aceptación (O9). No guarda el contador.
- **XP (O1).** `sx_battle_finish`: victoria +50, empate +25, derrota +10; quien pierde por abandono o desconexión recibe 0 (decisión técnica del script, ver el resumen del commit).

### 3.5 Verificaciones y advertencias esperadas

El script lleva al final, comentadas, consultas de verificación como las de `0007`: (a) RLS activo en las 14 tablas, (b) exactamente 5 políticas, (c) ningún privilegio de tabla para `anon` y `SELECT` solo en 3 tablas para `authenticated`,
(d) permisos por columna de `social_profile`, (e) `SECURITY DEFINER` y `search_path` de cada función, (f) quién puede ejecutar cada función, (g) claves foráneas solo a `auth.users` y a tablas nuevas, (h) formato del código y (i) tablas de contenido vacías hasta `0009`.
También lleva la tabla de **advertencias esperadas del Security Advisor** (sección 4.6). El **guion de escenarios** (`supabase/verificacion/0008_escenarios.sql`, Etapa 1) lo ejecuta el usuario a mano tras aplicar el script; su uso está en la "Parte F" de `supabase-configuracion.md`.

### 3.6 Verificación del script contra `0007` (2026-10-21)

Se comparó `0008_social.sql` con `0007_progreso_aprendizaje.sql`, con `LearningTopic` (Kotlin) y con `learning_labs.json`. **No se encontró ninguna diferencia de nombres ni de valores**; no hubo nada que corregir.

| Usado en `0008` (dónde) | Existe en `0007` | Coincide |
|---|---|---|
| `lesson_progress.user_id`, `.topic`, `.completed_at` (`sx_personal_streak`, `sx_islands_done`, `sx_pair_metrics`, `social_friends` `profile`) | `lesson_progress (user_id uuid, topic text, completed_at timestamptz, PK (user_id, topic))` | Sí |
| `lab_progress.user_id`, `.lab`, `.completed_at` (las mismas funciones) | `lab_progress (user_id uuid, lab text, completed_at timestamptz, PK (user_id, lab))` | Sí |
| Unión de isla: `lab_progress.lab = lesson_progress.topic` y mismo `user_id` | Los dos `check` aceptan **los mismos 6 valores** (`CONTRASENAS`, `PHISHING`, `MALWARE`, `INGENIERIA_SOCIAL`, `REDES_SEGURAS`, `FIREWALL`) | Sí: se unen por igualdad de texto |
| `exam_attempts.user_id`, `.taken_at` (`sx_personal_streak`) | `exam_attempts (id, user_id, correct_count smallint 0 a 6, passed generada, taken_at timestamptz)` | Sí |
| `exam_attempts.passed` y `.correct_count = 6` (`exam_passed`, `exam_perfect` del perfil del amigo) | `passed` = `correct_count >= 5` (generada); `correct_count` 0 a 6, así que 6 es alcanzable | Sí |
| `battle_questions.topic`, `ulti_challenges.topic` (`check` de los 6 temas) y la lista de `sx_pick_question` | Mismos 6 valores de `topic` y `lab` de `0007` | Sí, idénticos (sin tildes; `REDES_SEGURAS`, no `REDES`) |
| Temas del banco de la spec (9.4) y de los 12 retos de ulti (9.5) | `LearningTopic` (Kotlin) y `topic` de `learning_labs.json`: los mismos 6, guardados con `.name` | Sí |
| Claves foráneas a `auth.users (id)` con `on delete cascade` | Igual en las tres tablas de `0007` | Sí |
| Escritura en `lesson_progress`, `lab_progress`, `exam_attempts`, `placement`, `profile` | Tablas del módulo 3 y anteriores | **Ninguna**: `0008` solo las lee, desde funciones `SECURITY DEFINER` (el dueño no depende de los `GRANT` por columna de `0007`) |
| "Una isla se completa una sola vez" (racha de pareja, D14) | Lección y lab se registran con `INSERT ... ON CONFLICT DO NOTHING`: `completed_at` es la primera vez | Sí: ningún ejercicio se repite |

## 4. Seguridad

### 4.1 Quién decide qué (D7)

| Qué | Quién decide | Cómo |
|---|---|---|
| Si una respuesta es correcta | El servidor | `battle_play` (acción `answer`) compara con `battle_questions.correct`, que ningún rol de la app puede leer |
| El daño (20, 45, 23) y el HP | El servidor | El cliente nunca envía HP ni daño; solo envía la opción elegida o el comando escrito |
| De quién es el turno y cuándo vence | El servidor | `turn_seat`, `turn_deadline`, reloj de la base de datos (`clock_timestamp()`) |
| La carga de la ulti (3 aciertos seguidos) | El servidor | `correct_streak` y `ulti_charged` en `battles` |
| Escudo | El servidor | Compara `defender_at` con `attacker_at` (sección 6.4) |
| Quién gana, XP y nivel | El servidor | `sx_battle_finish`, una sola vez por batalla |
| La cuenta regresiva que se ve | **El teléfono, solo para mostrar** | `battle_sync` devuelve `server_now` y `seconds_left`; la app muestra esa cuenta y no confía en su propio reloj |

**Lo que sale al teléfono antes de responder (D7):** el texto de la pregunta y las 4 opciones, sin la correcta. Tras responder, la respuesta dice si
acertó, **pero no revela la correcta cuando falla** (Figma, frame 2449:255, solo marca la elegida con ✕). De los retos de ulti sale al teléfono **solo el
objetivo** (por ejemplo "Poner el archivo en cuarentena"); el comando esperado **nunca** sale del servidor (O10: el jugador lo sabe de memoria). El servidor decide si el
texto escrito coincide, con qué normalización, cuántos intentos quedan y en qué orden llegan los aciertos.

### 4.2 Funciones `SECURITY DEFINER` (P)

- `set search_path = ''` y nombres con esquema (`public.`, `auth.`, `pg_catalog.`), como `account_status` (`0005`) y `verify_recovery_email_code` (`0006`).
- La persona sale **solo** de `(select auth.uid())`. Si es nulo, error. Ninguna función recibe un `user_id` (un `user_id` recibido sería lo que `CLAUDE.md` prohíbe: identificar la fila por un dato de la pantalla).
- `revoke execute ... from public, anon` y `grant execute ... to authenticated`.
- Las funciones de batalla toman el candado de la fila de la batalla (`for update`) y comprueban `version` y `turn_number`: dos llamadas a la vez (los dos teléfonos al
  acabarse el tiempo, un doble toque) dan el mismo resultado y no dos.
- Los datos de otra persona salen por una **lista cerrada de columnas** (sección 4.5), nunca con `select *`.

### 4.3 Qué se expone y qué no

| Dato | ¿Lo ve otra persona? |
|---|---|
| `user_id` de Auth, correo, fecha de nacimiento, género, dispositivos, nombre de la cuenta | **Nunca.** Las funciones devuelven `username` y `friend_code` |
| Nombre de usuario y código de amigo | Quien busca por el nombre exacto o recibe el código; los amigos |
| Racha personal, "ya entrenó hoy" | Los amigos aceptados (no la oculta D11) |
| Nivel, insignias, islas (ejercicios y labs) | Los amigos aceptados, **salvo** con `hide_progress` (D11) |
| Historial de batallas | Solo entre los dos participantes |
| Respuestas del banco | **Nadie** desde la app |

### 4.4 Búsqueda de usuarios sin enumerar a todos (D10)

- **Coincidencia exacta** del nombre (sin distinguir mayúsculas) o del código de amigo. No hay búsqueda por prefijo, ni autocompletar, ni lista de usuarios.
- Longitud mínima de la consulta para evitar sondeos con 1 o 2 letras (P: 3 caracteres; formato en O6).
- La respuesta es mínima: `username`, `friend_code` y la relación (ninguna, pendiente enviada, pendiente recibida, amigos). Una persona sin Social habilitado, una cuenta inexistente y la propia persona responden **lo mismo**: `{"status":"error","code":"not_found"}`.
- **Los resultados de negocio no son excepciones.** `social_friends` (`search`, `invite`, ...), `social_pairs` y `battle_lobby` devuelven `{"status":"error","code":"..."}` (no encontrado, ya amigos, solicitud existente, espera de 24 h, demasiadas pendientes, límite de amigos, rival ocupado, reto pendiente) **sin lanzar excepción**. Una excepción deshace la transacción y con ella el **contador del límite de ritmo**, y así los intentos fallidos no contarían. Las excepciones quedan solo para `no_session`, `social_not_enabled`, `bad_action`, `bad_query` y `rate_limited`.
- **Ninguna respuesta distingue una cuenta que existe de una que no, salvo cuando la acción ya es legítima** (la persona ya está en mis amigos o ya hay una relación conmigo). En una invitación a racha de pareja y en un reto, "no existe", "soy yo" y "no es mi amigo" devuelven **el mismo** `not_found`. Las comprobaciones sobre mi propia situación (mis límites, mi reto pendiente, si ya estoy en una batalla) se hacen **antes** de resolver al destinatario. `accept`, `decline`, `cancel` y `remove` responden igual ante una persona inexistente o sin relación (`request_gone`, `pair_gone`, `challenge_gone` o `not_found`, según la acción).
- **Límite de ritmo** por persona (tabla `social_rate_limit`, función `sx_rate_limit`; valores técnicos: **20 búsquedas cada 10 minutos, 20 invitaciones de amistad por hora, 20 invitaciones de pareja por hora y 20 retos por hora**; ajustables al probarlo). Si se pasa: excepción `rate_limited`, y el contador se queda en el máximo hasta que pase la ventana (sigue bloqueando).
- El **código de amigo** tiene que resistir la adivinanza: el ejemplo de Figma (`NIGHT-7F3K`, 4 caracteres) da menos de un millón de combinaciones; con el límite de ritmo es difícil de recorrer, pero la propuesta es un código de al menos 6 caracteres (formato en O6).
- **Riesgo residual (aceptado en la propuesta):** quien conozca un nombre de usuario puede comprobar que existe. Es lo mismo que la comprobación de "nombre ya en uso" al crearlo, y es mucho menos que lo que ya acepta `account_status` (`CLAUDE.md` sección 8). Se documenta en el Security Advisor (4.6).
- No hay "Sugerencias" (D12): ninguna función recomienda ni enumera personas.

### 4.5 "Ocultar mi progreso público" (D11)

- Hoy es solo `UserPreferences.HIDE_PUBLIC_PROGRESS` en el teléfono. Pasa a guardarse en `social_profile.hide_progress` (el interruptor escribe en el servidor y mantiene la copia local) y el servidor lo aplica; **la app de quien mira no decide nada**.
- `friend_profile` devuelve, si `hide_progress` es falso: la lista de temas con lección vista, la de temas con lab completado, `exam_passed`, `exam_perfect` y la mejor racha (los campos de `BadgeFacts`, `data/learning/Badges.kt`), y la app ejecuta `evaluateBadges` con ellos. **Así no se duplican en SQL las condiciones de las 9 insignias.** Si es verdadero: esos campos llegan nulos y `hidden = true` (la app dibuja "Este jugador oculta su progreso").
- Sigue visible aunque se oculte: nombre de usuario, racha personal, "ya entrenó hoy", racha de pareja, historial de batallas y marcador. Qué significa "nivel" en D11: O14.
- Antes de habilitar Social, ninguna función devuelve datos de progreso de otra persona.

### 4.6 Security Advisor: lo que se espera y cómo se documenta

Cada advertencia nueva se corrige o se documenta en la sección "Advertencias aceptadas del Security Advisor" de `docs/superpowers/supabase-configuracion.md` **antes de fusionar a `main`** (`CLAUDE.md` sección 4).

| Esperado | Origen | Propuesta |
|---|---|---|
| "Signed-In Users Can Execute SECURITY DEFINER Function", **5** (una por función pública de la sección 3.2: `social_friends`, `social_pairs`, `battle_lobby`, `battle_sync` y `battle_play`; antes de agrupar eran 32) | La app llama cada función con la sesión, y Supabase solo expone funciones del esquema `public` | **Documentar**, una fila por función (tabla completa con el motivo de cada una al final de `0008_social.sql`): son la única vía de acceso a las tablas cerradas y cada una actúa solo sobre `auth.uid()`, no recibe ids de Auth, valida una lista cerrada de acciones y limita. Es el mismo caso que `verify_recovery_email_code` (advertencia 3 de la guía) |
| "Public Can Execute SECURITY DEFINER Function" | Solo si se olvida el `revoke ... from public, anon` | **Corregir**, no aceptar: ninguna función de Social debe poder llamarse sin sesión (a diferencia de `account_status`) |
| "RLS Enabled No Policy" (info), una por tabla cerrada (**11**) | Patrón de `recovery_email_codes` | **Documentar** (intencional: deniega todo a la app) |
| Función sin `search_path` fijo | Si una función no lo declara | **Corregir** |
| Las del Realtime Authorization, si se usa (sección 5) | Políticas sobre `realtime.messages` | Documentar al decidir |

Verificación manual antes de aplicar: el Advisor se mira **después** de ejecutar `0008`, y la lista real se compara con esta tabla; cualquier fila que no esté aquí se corrige o se documenta.

## 5. Tiempo real

### 5.1 Límites verificados del plan gratuito (documentación oficial, consultada el 2026-10-21)

| Límite | Gratuito | Fuente |
|---|---|---|
| Conexiones simultáneas de Realtime | **200** | `supabase.com/docs/guides/realtime/limits`, y 200 de pico en `supabase.com/pricing` |
| Mensajes por segundo | **100** | Realtime Limits |
| Entradas a canales por segundo | **100** | Realtime Limits |
| Canales por conexión | 100 | Realtime Limits |
| Mensajes de Realtime al mes (incluidos) | **2 millones** | `supabase.com/pricing`, `supabase.com/docs/guides/realtime/pricing` |
| Tamaño de un mensaje de Broadcast | 256 KB | Realtime Limits |
| Presencia: mensajes por segundo | 20 | Realtime Limits |
| Invocaciones de Edge Functions al mes | 500.000 | `supabase.com/pricing` |
| Edge Functions: duración de pared / CPU / memoria | 150 s / 2 s por petición / 256 MB | `supabase.com/docs/guides/functions/limits` |
| Pausa del proyecto | 7 días de inactividad | `supabase.com/pricing` (ya aceptado) |
| Base de datos / salida de datos | 500 MB / 5 GB | `supabase.com/pricing` |

**No verificado:** qué cuenta exactamente como "mensaje" (Broadcast, Presence, cambios de Postgres) y cómo se mide el pico de conexiones: las páginas consultadas remiten a otras guías ("Manage Realtime Messages usage"). Tampoco se verificó que `pg_cron` esté disponible en el plan gratuito: la página de Cron describe trabajos "desde cada segundo hasta una vez al año" y recomienda no más de 8 en paralelo ni más de 10 minutos por trabajo, pero no dice nada del plan. **Hay que comprobarlo en el panel (Database > Extensions) antes de depender de él.**

**Estimación (mía, no medida):** cada jugador en batalla ocupa una conexión. Con 200, hasta unas 100 batallas a la vez: más que de sobra para un curso. Una batalla de hasta 20 turnos genera, con la opción recomendada, unas 40 señales (20 cambios × 2 jugadores); con 2 millones al mes salen cientos de miles de batallas. Si cada mensaje de presencia o de latido contara, el cálculo cambia: se mide en la prueba de concepto.

### 5.2 Opciones

| Opción | Cómo funciona | Pros | Contras |
|---|---|---|---|
| **A. Sondeo (polling) con la API que ya existe** | La app llama `battle_sync` cada 2 a 3 s durante una batalla y cada 5 a 10 s en la espera, el emparejamiento y la lista de retos | **Sin dependencia nueva** (`postgrest-kt` ya está). Funciona con cualquier red. Un solo camino de código | Latencia de 2 a 3 s. Más peticiones. Más batería en pantalla. Sin límite documentado de peticiones en las páginas consultadas, pero cuenta en la salida de datos (5 GB) |
| **B. `realtime-kt` + Postgres Changes** (suscribirse a filas de `battles`) | El servidor avisa de cada cambio de fila | API conocida | Cada evento se autoriza contra cada suscriptor y se procesa en un solo hilo (Postgres Changes); obliga a abrir `SELECT` sobre `battles`, que lleva los ids de Auth de los dos jugadores (contradice 4.3) |
| **C. `realtime-kt` + Broadcast desde la base de datos, canal privado, como solo una "señal"** (recomendada) | Tras cada cambio, la función hace `realtime.send` por el tema `battle:<id>` con un contador (`version`), sin datos. La app, al recibirlo, llama `battle_state`. Si la señal falla, el sondeo (A) la cubre | **No viaja información sensible** por el canal; si Realtime cae, el juego sigue con A. Latencia de décimas de segundo. La documentación presenta Broadcast como la vía de escala | **Dependencia nueva (`realtime-kt`, BOM 3.2.4): requiere aprobación del equipo** (`CLAUDE.md` sección 2). Hay que crear políticas sobre `realtime.messages` (Realtime Authorization) y el cliente debe llamar `setAuth` antes de suscribirse (documentación de JavaScript). **No verificado** en Kotlin 3.2.4 |
| **D. Presencia (Presence) para saber si el rival está** | El canal informa quién entra y sale | Fácil de mostrar | Un cliente no puede afirmar que el otro se fue sin que el servidor lo compruebe (D7); el límite gratuito es de 20 mensajes por segundo. **No se usa para decidir**: la desconexión se decide con latidos al servidor (5.4) |
| **E. Edge Function con WebSocket propio** | | | No hace falta: límite de 150 s de duración en el plan gratuito y reimplementaría lo que Realtime ya da |

**Decisión del grupo (D18): se empieza con A (sondeo)**, sin dependencia nueva; la batalla funciona completa así. **C** (`realtime-kt` con Broadcast como señal) es una
**mejora opcional posterior**: solo se agrega si el equipo lo aprueba después de una prueba de concepto, con este criterio de salida: si `realtime-kt` 3.2.4 no
permite canales privados o no es estable con Ktor 3.3.0 y Kotlin 2.2.10, se queda en **A** y no se cambia nada más. El script `0008` está escrito **para sondeo** y no
toca la publicación de Realtime ni `realtime.messages`; si luego se adopta C, esas políticas irían en un script nuevo.

### 5.3 Turnos vencidos (sin un proceso que corra solo)

La base de datos no tiene un "temporizador" propio; el plazo se guarda como `turn_deadline` y se **resuelve de forma perezosa**:

1. Toda función de batalla empieza llamando `sx_battle_resolve` (bajo el candado de la fila): si `turn_phase = question` y `clock_timestamp() > turn_deadline` (más una tolerancia de red de 2 s, parámetro técnico), registra un turno perdido (`outcome = timeout`), no hay daño, y pasa el turno con 60 s completos para el siguiente. Lo mismo con el reto de ulti vencido (`battle_ultis.deadline`).
2. La app, cuando **su** cuenta regresiva llega a cero, llama `battle_sync`. Los dos teléfonos lo hacen; el primero resuelve y el segundo ve el resultado ya hecho (idempotente por el candado y `version`).
3. **Sin `pg_cron`:** el script `0008` **no depende** de `pg_cron`. Una batalla abandonada por los dos queda como `active` hasta que alguno abra Social: la primera llamada de Batallas (lobby) resuelve primero la batalla activa de esa persona, y eso libera la restricción de "una batalla activa por jugador".
4. La cola y los retos vencidos se limpian igual, de forma perezosa (un reto vencido deja de listarse cuando `expires_at < now()`).

### 5.4 Desconexiones (D5)

- **Sondeo obligatorio (D18).** Mientras haya una batalla abierta, la app **debe llamar a `battle_sync` cada 2 a 3 segundos, incluido durante su propio turno y durante la ulti** (no basta con llamar al actuar). Esa llamada, o cualquier acción de `battle_play`, es el **latido**: actualiza `last_seen1` o `last_seen2`.
- Un jugador está **desconectado** si pasan **más de 60 s sin ninguna llamada** (D5): el servidor lo da por desconectado y **pierde la batalla** (no solo el turno). El plazo se cuenta desde el último latido recibido, así que el error máximo es un intervalo de sondeo (2 a 3 s).
- **Orden en `sx_battle_resolve` (revisado):** la desconexión se evalúa **antes** que el turno vencido, y es el orden correcto. Quien sondea tiene un latido reciente: la desconexión da falso y solo pierde el **turno**. Quien no sondea más de 60 s tiene un turno vencido y está desconectado a la vez; si se evaluara primero el turno, la batalla seguiría con una persona ausente que perdería un turno tras otro. Quien dejó de sondear hace menos de 60 s en pleno turno solo pierde el turno. El latido de quien llama se registra **antes** de resolver, así que quien vuelve tarde nunca se castiga a sí mismo.
- Quien sigue conectado sigue llamando a `battle_sync`; esa llamada evalúa `now() - last_seen_otro > 60 s`. Si es cierto, **la batalla termina**, gana quien sigue conectado (`end_reason = forfeit_disconnect`).
- Si el jugador vuelve antes de los 60 s, el siguiente latido lo marca como conectado y la batalla sigue; el turno que le tocaba corre normal (si su minuto se acaba, pierde el turno).
- Ambos desconectados a la vez: O2.
- Android pone la app en segundo plano al cambiar de aplicación: los latidos se detienen y cuenta como desconexión. Es una consecuencia de no tener notificaciones fuera de la app (D16); se avisa en la pantalla de espera.
- Un reloj del teléfono mal puesto no afecta nada: toda decisión usa el reloj del servidor.

### 5.5 Qué se mide en la prueba de concepto

Tiempo de ida y vuelta de `battle_sync` en el teléfono, mensajes por batalla en el panel de Realtime, comportamiento con la pantalla apagada y con pérdida de red, y si la señal de Broadcast llega siempre antes del sondeo.

## 6. Emparejamiento y reto a amigos

### 6.1 Partida rápida (D6)

1. `battle_lobby('quick_join')` inserta a la persona en `matchmaking_queue` con su nivel de combatiente. Si ya está en una batalla activa, error.
2. Busca, con `for update skip locked`, a la persona en espera con la que la diferencia de niveles sea ≤ al rango actual. El rango arranca en **3** y **se amplía** con el tiempo de espera de la persona que lleva más tiempo; el ritmo de ampliación es la **O11**.
3. Si hay pareja, crea la batalla con la **misma función interna** que el reto a un amigo (D6) y marca a las dos filas con su `battle_id`.
4. La app consulta `battle_lobby('quick_poll')` cada 2 a 3 s. Un jugador que no consulta en 15 s se saca de la cola.
5. **A los 60 s sin rival** `quick_poll` devuelve `suggest_friend = true` y la app muestra M8. La búsqueda **sigue** (sugerir no cancela).
6. `quick_cancel` la saca de la cola. Cerrar la pantalla de búsqueda también.

### 6.2 Reto a un amigo (D6, D16)

1. Solo se puede retar a un amigo aceptado. `challenge_send(p_friend_code)` crea una fila `pending` con `expires_at = now() + 2 minutos`. No se puede retar a quien ya tiene un reto pendiente con la persona ni a quien está en una batalla activa.
2. **Sin notificaciones (D16):** la persona retada lo ve **al abrir Social**, en "Retos recibidos" de Batallas (M7). Mientras Social está abierto la app consulta `challenges_pending()` cada 5 a 10 s (P) para que el reto aparezca sin reabrir.
3. La persona que reta ve "Esperando a Mateo…" con la cuenta regresiva de 2 minutos. Si sale de esa pantalla, el reto se cancela (P; así nadie acepta contra alguien que ya se fue).
4. `challenge_respond(p_challenge, true)` dentro del plazo crea la batalla (la misma función interna) y marca el reto `accepted`. Pasado el plazo, `expired`: no se puede aceptar.
5. Quien reta y quien acepta **no** tienen cola ni rango: el nivel no importa.
6. "Revancha" (frames 16 y 17): O13.

### 6.3 Una misma batalla (D6)

`sx_battle_create(player1, player2, mode)` es la única función que crea una fila de `battles`; la partida rápida y el reto solo cambian `mode`. Reglas comunes: HP 100 para cada uno; empieza una persona al azar (O2); una batalla activa por persona (clave primaria de `battle_active`).

### 6.4 Reglas de batalla, juntas

| Regla | Detalle | Origen |
|---|---|---|
| Turno de pregunta | El servidor elige la pregunta (tema al azar entre los 6; dificultad según el nivel de combatiente de quien responde, sección 9.2) sin repetir dentro de la batalla. `turn_deadline` = inicio + 60 s | D2, D9, P |
| Respuesta correcta | -20 HP al rival; suma 1 a `correct_streak`. El turno pasa | D3 |
| Respuesta incorrecta o tiempo agotado | Sin daño; `correct_streak` vuelve a 0; el turno pasa | D2, D3, P (reinicio de la racha, inferido de "seguidos") |
| Carga de la ulti | Al llegar a 3 aciertos seguidos, `ulti_charged = true`. El contador no pasa de 3 | D1 |
| Turno con la ulti cargada | La persona elige: "Guardar para después" (responde una pregunta normal; la ulti **sigue cargada**, acierte o falle) o "Usar Ulti ahora" | D1, F 2449:290 |
| Ulti | Empieza `battle_ultis`: atacante y defensor reciben **a la vez** su reto (uno de ataque y otro de defensa, distintos), con 2 minutos | D4 |
| Resolver el reto | Cada jugador tiene **hasta 3 intentos** dentro de los 2 minutos. Un comando incorrecto consume un intento y **no** termina el reto. El comando **no se muestra** (se sabe de memoria). El servidor normaliza el texto, lo compara con el comando del laboratorio y guarda la hora del servidor de cada acierto | D4, O10 |
| Atacante agota los 3 intentos o se acaba el tiempo | No pasa nada (haya escudo o no), **pierde la carga** (`ulti_charged = false`, `correct_streak = 0`) y el turno pasa | D4, O10 |
| Atacante resuelve bien | Impacto de **45 HP**. Si el defensor resolvió bien **antes** (su hora es estrictamente anterior a la del atacante), escudo: daño = `round(45 / 2)` = **23** (redondeo de la mitad hacia arriba: 22,5 → 23) | D4, P (el redondeo exacto) |
| Después de la ulti | Gasta la carga y el turno pasa al otro (el reto ocurre dentro del turno del atacante, Figma 2450:205: "antes de que termine el turno") | F, P |
| Fin por HP | Si el HP de alguien llega a 0, la batalla termina y gana el otro | O2 (el resto de condiciones) |
| Desconexión | 60 s sin latido: pierde | D5 |

**Resolución de la ulti, paso a paso (P):**

- La ulti se resuelve en el instante en que se sabe: cuando el atacante **acierta** (impacto), cuando **agota sus 3 intentos** (falla) o cuando vence el plazo de 2 minutos sin acierto del atacante (falla).
- **El primero en acertar gana la carrera.** Si el atacante acierta en el instante `t_a`: hay escudo solo si el defensor acertó antes, en `t_d` con `t_d < t_a`. Si el defensor aún no había acertado, **no hay escudo** aunque acierte después.
- Un defensor que acierta antes que el atacante deja el escudo armado hasta que el atacante termine. Un defensor que agota sus 3 intentos o se queda sin tiempo **no** activa el escudo.
- Un atacante que agota sus intentos o el tiempo falla sin importar el defensor: no hay daño y pierde la carga.
- El defensor ve su pantalla de resultado ("Recibiste N HP", o "Tu escudo bloqueó la mitad").
- **Limitación aceptada:** el orden lo da la llegada al servidor, así que la latencia de cada jugador influye en quién "llegó antes". Los comandos son los de los laboratorios del módulo 3, que **sí viajan en el APK** (`learning_labs.json`): quien desarme la app puede conocerlos aunque la pantalla no los muestre. Los 3 intentos limitan la fuerza bruta. Es la misma clase de límite que el examen del módulo 3 (R2) y se documenta en la sección 11.

## 7. Racha compartida de pareja

### 7.1 Qué es el "ejercicio del día" y cuándo está hecho (D13, D14)

- Una **isla** (lección con su laboratorio, tema de `0007`) está completada cuando existe `lesson_progress` **y** `lab_progress` de ese tema (regla P3 del módulo 3). Su **fecha de completado** es la mayor de las dos `completed_at`.
- La persona **completó su ejercicio del día D** si alguna de sus islas tiene su fecha de completado en el día D, **hora de Colombia** (`America/Bogota`, UTC-5, sin horario de verano).
- No hace falta una tabla nueva: las fechas ya existen en `0007` y **ninguna fila se repite** (lección y lab se registran una vez; el registro ignora duplicados). Por eso es compatible con D14 ("no se puede repetir").
- El examen final y los intentos de examen **no cuentan** (D14: es una lección con su laboratorio).
- Como cada isla completa solo puede dar un día, **una persona tiene como máximo 6 días de ejercicio en toda su vida**, y la racha de pareja no puede pasar de 6 (D14). Completar dos islas en el mismo día cuenta un solo día y gasta una de las seis.

### 7.2 Reglas exactas

| # | Regla |
|---|---|
| R1 | **Inicio.** Una pareja existe cuando A invita a B (B es amigo de A, ver O8) y B acepta. La racha empieza a contar desde el día de aceptación (hora de Colombia): los ejercicios de días **anteriores** no cuentan. El día de la aceptación **sí** cuenta, y cuenta cualquier isla completada ese día de Colombia, aunque haya sido antes de aceptar (O9) |
| R2 | **Día compartido.** Un día D es compartido si, con D ≥ el día de aceptación, **los dos** completaron su ejercicio del día D (7.1) |
| R3 | **Racha actual.** Los días compartidos consecutivos que terminan en el último día compartido, mientras ese día sea **hoy o ayer** (hora de Colombia). Si el último día compartido es anterior a ayer: 0. La misma idea que la racha personal del módulo 3 |
| R4 | **Mejor racha.** La secuencia consecutiva más larga de la pareja. Nunca baja. Máximo 6 |
| R5 | **Estados que se muestran:** *activa* (hoy ya hicieron los dos), *en riesgo* (ayer fue compartido, hoy falta uno o los dos: banner de Figma "Aún no entrenas hoy"), *sin racha* (0), *ruta completa* (alguno de los dos ya completó las 6 islas, ver R9) |
| R6 | **Día sin completar.** Si pasa el día D sin que los dos lo completen, la racha actual vuelve a 0 al empezar el día D+2 (la regla de "hoy o ayer"). La pareja **sigue existiendo** y puede empezar una racha nueva, si a los dos les quedan islas por completar |
| R7 | **Parejas múltiples.** Una persona puede tener varias. Su ejercicio del día cuenta **para todas sus parejas a la vez** (una isla completada es un día completado, no se reparte). Cada pareja tiene su propio día de aceptación y su propia racha |
| R8 | **Contador de afuera (D15).** En la tarjeta de SOCIAL - INICIO: la **racha actual** más alta entre las parejas de la persona; 0 o ninguna pareja: "Sin racha" (texto propuesto) |
| R9 | **Límite de 6 días.** Con 6 lecciones y 6 labs la racha de pareja no pasa de 6. Cuando alguno de los dos completa su sexta isla, ya no puede aportar más días: la racha se detiene y la pantalla lo explica (qué se ofrece en el lugar del ejercicio: O5). Es una **limitación aceptada** (D14) |
| R10 | **Huso horario.** El día se corta a medianoche de Colombia para las dos personas, aunque una viva en otro huso. La racha **personal** también se calculará con hora de Colombia (tarea de la Etapa 6: hoy `computeStreak` del módulo 3 usa la zona del dispositivo), para que la propia coincida con la de los amigos y las parejas. Se muestra la nota "Hora de Colombia" en la pantalla de pareja (P) |
| R11 | **Hora del servidor.** Todo se calcula con los `completed_at` del servidor, no con el reloj del teléfono |
| R12 | **Terminar.** Cualquiera de los dos puede terminar la pareja. Terminarla (o eliminar la amistad, O8) la borra de las dos listas; no se conserva la racha |
| R13 | **Invitación sin responder.** Cuánto vale y cuántas pueden estar pendientes: **O4** |
| R14 | **Ocultar progreso (D11)** no oculta la racha de pareja: es un acuerdo entre las dos personas; se muestra solo "hizo su ejercicio hoy: sí o no", nunca qué isla |

### 7.3 Casos borde

| Caso | Resultado |
|---|---|
| A completa su ejercicio a las 23:50 y B a las 00:10 del día siguiente | No es día compartido: son días distintos. Ese día no suma. El día siguiente sí lo sería solo si **A** también completa otra isla ese día (a A le tiene que quedar alguna) |
| A completa 2 islas hoy y B 1 | Día compartido; A gastó 2 de sus 6 |
| A ya completó las 6 islas; B no | A no puede aportar días nuevos; la racha de la pareja se detiene (R9) |
| Pareja A-B y A-C, A completa una isla hoy; B también, C no | Día compartido para A-B, no para A-C (R7) |
| Se acepta a las 22:00 y A ya había completado una isla a las 09:00 del mismo día | Cuenta: la regla es por día de Colombia, no por hora (O9) |
| Reloj del teléfono adelantado | No afecta: se usan los instantes del servidor (R11) |
| Cuenta eliminada | La pareja y su racha desaparecen (borrado en cascada) |
| Alguien ve su racha de pareja en riesgo y completa su ejercicio a las 00:05 | Para ese día ya es tarde; cuenta para el día nuevo |

### 7.4 Dónde se calcula

La racha se **deriva** como las del módulo 3: nada se almacena, salvo la pareja y su día de aceptación. Se calcula **en el servidor** (`pair_streaks_overview`, `pair_streak_detail`), porque hay que leer el progreso de **dos** personas y las tablas de `0007` son solo del dueño. Esas funciones devuelven, de cada pareja, únicamente: `username` de la otra persona, racha actual, mejor racha, si cada una hizo su ejercicio hoy y el estado. **Nunca** devuelven qué isla se completó (R14). Como la regla se escribe en SQL y no en Kotlin, hay riesgo de que difiera de la lógica del cliente: se cubre con el guion de casos de la etapa de rachas (sección 10) y no con pruebas automáticas (F-03).

## 8. XP, niveles y relación con el progreso actual

### 8.1 Tres rachas que no son lo mismo

| | Racha personal | Racha compartida (de pareja) | Racha de batallas |
|---|---|---|---|
| Qué cuenta | Días seguidos con práctica: una lección vista, un lab completado o un intento de examen | Días seguidos en que **dos** personas completan su ejercicio del día | Victorias seguidas |
| Quién | Una persona | Una pareja (varias por persona) | Una persona |
| Zona horaria | **America/Bogota**: hoy la del dispositivo (módulo 3, `computeStreak`); se cambia en la Etapa 6 | **America/Bogota** (D13) | No depende del día |
| Dónde se calcula | La app (Kotlin) | El servidor (SQL) | El servidor (SQL, de `battle_results`) |
| Se rompe | Al pasar un día sin práctica (hoy o ayer) | Al pasar un día sin que los dos completen | Con una derrota (el empate: O1/O2) |
| Máximo | Sin límite en teoría; repetir no suma (limitación del módulo 3) | **6** (D14) | Sin límite |
| Dónde se ve | Inicio, menú de usuario, Mi Perfil, lista de amigos (la del amigo) | Lista de parejas, detalle, tarjeta de SOCIAL - INICIO (la mayor) | Fin de batalla ("🔥 Racha de batallas +1", F 2451:195) |
| Insignias | "Racha de 7 días" y "Racha de 30 días" usan la **mejor racha personal** | Ninguna (no hay insignia nueva en este módulo) | Ninguna |

Con 6 islas, la racha personal de 30 días del módulo 3 sigue siendo alcanzable solo con repasos, que no suman (limitación aceptada del módulo 3); la propuesta `0008` de una tabla de actividad **no se incluye** en este módulo (no se pidió) y se mantiene como pendiente.

### 8.2 XP y nivel de combatiente (D8)

- **XP** y **nivel de combatiente** son nuevos y **separados** del nivel de nivelación (`placement`: BAJO, MEDIO, ALTO). **Este módulo no lee ni escribe `placement`** (igual que el módulo 3): el nivel de nivelación sigue sin cambiar por nada de Social.
- **Solo las batallas dan XP** (D8). Aprobar el examen, completar labs o mantener rachas **no** dan XP.
- El XP se guarda como `xp_awarded` en `battle_results` y el total se **calcula** sumándolo: una sola fuente de verdad, sin un contador que pueda desfasarse. (Alternativa: un contador en una tabla de estadísticas, actualizado solo por `sx_battle_finish`; no cambia nada para la app.)
- El **nivel** sale del total y de la curva `fighter_level_curve` (1 a 20). El 20 es el tope: más allá, fuera de alcance (D8). Una persona nueva empieza en nivel 1 con 0 XP (P; no se parte del nivel de nivelación).
- La **curva** y cuánto XP da una victoria, una derrota y un empate: **O1** (Figma muestra +50 por victoria).
- El nivel de combatiente se usa en el **emparejamiento** (D6) y para escoger la **dificultad** de las preguntas (sección 9.2). No desbloquea nada de la ruta de aprendizaje.
- El XP solo lo escribe `sx_battle_finish`, una vez por batalla, en el servidor. Ningún rol de la app puede escribirlo.

### 8.3 Relación con el progreso del módulo 3

| Módulo 3 | Módulo 4 |
|---|---|
| `lesson_progress`, `lab_progress` | Los **lee** el servidor para la racha de pareja y el perfil del amigo; no los modifica |
| `exam_attempts` | Solo para el perfil del amigo (`exam_passed`, `exam_perfect`); no cuenta para la racha de pareja |
| Racha personal e insignias (cliente) | Siguen igual. Para el amigo, la app evalúa las insignias con `BadgeFacts` que le devuelve el servidor (4.5) |
| `placement` | **No se toca** |

## 9. Banco de preguntas y retos de ulti

### 9.1 Formato, tamaño y dónde vive (D7, D9)

- **No va en `assets`.** A diferencia del contenido del módulo 3 (JSON dentro del APK), aquí las respuestas correctas no pueden viajar al teléfono (D7). El banco vive en la tabla cerrada `battle_questions` y se carga con `0009`.
- Campos: `id`, `topic` (los 6 códigos de `0007`: `CONTRASENAS`, `PHISHING`, `MALWARE`, `INGENIERIA_SOCIAL`, `REDES_SEGURAS`, `FIREWALL`), `tier` (1 a 3), `question`, `option_a` a `option_d`, `correct` (`A` a `D`), `active`.
- **Tamaño:** 72 preguntas de unos 350 bytes cada una: unos 25 KB. Nada para el límite de 500 MB. Al teléfono llega, por turno, una pregunta y 4 opciones (unos 400 bytes).
- Corregir o agregar preguntas después: un script nuevo (`0010`, ...) que actualiza la tabla; no cambia la app. (Eso es una ventaja sobre el JSON del módulo 3, que exige una versión nueva de la app.)

### 9.2 Niveles de combatiente y dificultad (P, ajuste en O12)

D9 pide un banco "por nivel de combatiente". Con 12 preguntas por tema, la propuesta es **tres escalones de 4 preguntas por tema**:

| Escalón (`tier`) | Niveles de combatiente | Preguntas por tema |
|---|---|---|
| 1, básico | 1 a 7 | 4 |
| 2, intermedio | 8 a 14 | 4 |
| 3, avanzado | 15 a 20 | 4 |

El servidor escoge cada pregunta según el **nivel de quien responde** (no el del rival), con el tema al azar, sin repetir dentro de la batalla. Con solo 24 preguntas por escalón (6 temas × 4), una persona que juegue mucho en el mismo nivel verá repetidas; se anota como riesgo (sección 11) y la O12 propone ampliar el banco.

### 9.3 Cómo se sirven sin exponer las respuestas (D7)

1. `battle_sync` devuelve, en el turno de quien responde, el id, el texto y las 4 opciones de la pregunta y `seconds_left`. **No devuelve `correct`.**
2. `battle_play` con la acción `answer` (`p_turn`, `p_arg` = `A` a `D`) recibe la opción y el número de turno (para ignorar un envío viejo), compara en el servidor y devuelve `correct` (sí o no), el daño y el HP nuevo.
3. Si acertó, la app marca la opción elegida en oro (Figma 2449:221). Si falló, marca la elegida con ✕ y **no revela cuál era la correcta** (Figma 2449:255): así quien pierde no se lleva el banco.
4. `battle_turns` guarda la opción elegida: de ahí sale el "Repasa Contraseñas y Phishing" de la derrota (los dos temas con más fallos de esa batalla; P).
5. El orden de las opciones es el del banco (P). Mezclarlo por pregunta y batalla es posible desde el servidor si el grupo lo pide.

### 9.4 Borrador de las 72 preguntas (para revisión del grupo)

Convención de revisión: **una sola respuesta correcta**, sin trampas de lenguaje, nada que pueda leerse como ambiguo. Las letras correctas están repartidas (18 de cada una en total, 3 por letra en cada tema). Solo la pregunta CON-01 viene de Figma (2449:208); ninguna repite las del examen final. Estas respuestas están **en este documento del repo** para la revisión; en la app solo existirán en la base de datos.

**Tema 1: Contraseñas y Autenticación (`CONTRASENAS`)**

| Id | Esc. | Pregunta | A | B | C | D | Correcta |
|---|---|---|---|---|---|---|---|
| CON-01 | 1 | ¿Cuál de estas contraseñas es más segura? | 123456 | Tu_Nombre2024 | k7#mQ!9zRt$2 | contraseña | **C** |
| CON-02 | 1 | ¿Qué vuelve más difícil adivinar una contraseña? | Que sea corta y fácil de recordar | Que sea larga y mezcle letras, números y símbolos | Que use tu fecha de nacimiento | Que repita el mismo carácter | **B** |
| CON-03 | 1 | ¿Qué debes hacer con tus contraseñas? | Compartirlas con amigos de confianza | Dejarlas anotadas junto al computador | Enviarlas por mensaje cuando alguien las pida | No compartirlas con nadie | **D** |
| CON-04 | 1 | ¿Qué te ayuda a manejar muchas contraseñas distintas de forma segura? | Un gestor de contraseñas | Usar la misma en todas las cuentas | Anotarlas en un papel pegado al monitor | Guardarlas en un chat con un amigo | **A** |
| CON-05 | 2 | ¿Por qué es mala idea reutilizar la misma contraseña en varios sitios? | Porque los sitios la rechazan | Porque si se filtra en un sitio, se puede probar en los demás | Porque se vuelve más lenta de escribir | Porque el navegador la borra | **B** |
| CON-06 | 2 | ¿Qué es la autenticación en dos pasos (2FA)? | Escribir la contraseña dos veces | Tener dos contraseñas iguales | Pedir un segundo factor, como un código, además de la contraseña | Cambiar la contraseña cada dos días | **C** |
| CON-07 | 2 | Un programa prueba una lista de contraseñas comunes contra una cuenta, una tras otra. ¿Cómo se llama el ataque? | Ataque de diccionario | Suplantación de identidad | Escaneo de puertos | Cifrado asimétrico | **A** |
| CON-08 | 2 | ¿Cuál de estas es una mejor contraseña? | Password123 | qwerty | NombreApellido | mi-gato-come-pan-azul-en-la-luna | **D** |
| CON-09 | 3 | ¿Cómo debería guardar un sitio bien diseñado las contraseñas de sus usuarios? | En texto plano, para poder recuperarlas | Codificadas en Base64 | Cifradas con una clave que conoce todo el personal | Como un hash con sal, calculado con un algoritmo lento | **D** |
| CON-10 | 3 | ¿Qué ventaja tiene una llave de seguridad física (FIDO2) frente a un código por SMS? | Resiste el phishing, porque solo responde al sitio legítimo | Es más barata | No necesita ningún dispositivo | Permite usar contraseñas más cortas | **A** |
| CON-11 | 3 | Un sitio te avisa de que tu contraseña apareció en una filtración. ¿Qué haces primero? | Esperar a ver si pasa algo | Cambiar solo una letra | Cambiarla ahí y en cualquier otro sitio donde la usaras | Borrar tu correo | **C** |
| CON-12 | 3 | ¿Qué es el "password spraying"? | Probar millones de contraseñas contra una sola cuenta | Probar unas pocas contraseñas comunes contra muchas cuentas para evitar bloqueos | Robar contraseñas con un teclado falso | Adivinar contraseñas con ingeniería social | **B** |

**Tema 2: Phishing (`PHISHING`)**

| Id | Esc. | Pregunta | A | B | C | D | Correcta |
|---|---|---|---|---|---|---|---|
| PHI-01 | 1 | ¿Qué es el phishing? | Un virus que borra archivos | Un engaño para que entregues datos o dinero haciéndose pasar por alguien de confianza | Un tipo de contraseña | Un programa para limpiar el correo | **B** |
| PHI-02 | 1 | Un correo de "tu banco" dice que tu cuenta se bloqueará en una hora si no entras a un enlace. ¿Qué señal de alerta tiene? | Crea urgencia para que actúes sin pensar | Está escrito en español | Menciona un banco | Trae el logo del banco | **A** |
| PHI-03 | 1 | ¿Qué haces antes de tocar un enlace sospechoso? | Tocarlo para ver adónde lleva | Reenviarlo a tus contactos | Abrirlo con otra cuenta | Revisar la dirección real del enlace (por ejemplo, manteniéndolo presionado) | **D** |
| PHI-04 | 1 | ¿Qué haces con un correo que parece phishing? | Responder pidiendo explicaciones | Reenviarlo como aviso | No abrir enlaces ni adjuntos, y reportarlo o eliminarlo | Descargar el adjunto en otro equipo | **C** |
| PHI-05 | 2 | ¿Cuál de estas direcciones parece una copia falsa de banco-ejemplo.com? | app.banco-ejemplo.com | www.banco-ejemplo.com | banco-ejemp1o.com | banco-ejemplo.com | **C** |
| PHI-06 | 2 | ¿Qué es el "spear phishing"? | Un phishing masivo sin destinatario concreto | Un phishing dirigido a una persona concreta, con datos suyos para parecer real | Un phishing por llamada | Un filtro antispam | **B** |
| PHI-07 | 2 | ¿Qué es el "smishing"? | Phishing por mensajes de texto (SMS) | Phishing por llamada de voz | Phishing por correo | Phishing por códigos QR | **A** |
| PHI-08 | 2 | Un adjunto se llama "factura.pdf.exe". ¿Qué indica? | Es un PDF normal | Es un PDF protegido con contraseña | Es un archivo comprimido | Es un programa ejecutable disfrazado de PDF | **D** |
| PHI-09 | 3 | ¿Qué es el fraude BEC (Business Email Compromise)? | Alguien se hace pasar por un directivo o proveedor para pedir pagos o datos | Un virus que se propaga por correo | Un cifrado para correos | Un servidor de correo | **A** |
| PHI-10 | 3 | ¿Qué mecanismos ayudan a comprobar que un correo viene realmente del dominio que dice? | HTTP, FTP y SMTP | WPA2, WEP y WPS | RAID, NAS y SAN | SPF, DKIM y DMARC | **D** |
| PHI-11 | 3 | Una página falsa muestra el candado de HTTPS. ¿Qué significa eso? | Que el sitio es legítimo | Que la conexión va cifrada, pero no prueba que el sitio sea quien dice ser | Que el sitio no tiene virus | Que el gobierno lo aprobó | **B** |
| PHI-12 | 3 | Un gestor de contraseñas no rellena tu contraseña en un sitio que parece el de siempre. ¿Por qué eso ayuda contra el phishing? | Porque cifra el sitio | Porque bloquea los correos falsos | Porque solo autocompleta en el dominio guardado, así que la falta de relleno es una alerta | Porque cambia la contraseña sola | **C** |

**Tema 3: Malware (`MALWARE`)**

| Id | Esc. | Pregunta | A | B | C | D | Correcta |
|---|---|---|---|---|---|---|---|
| MAL-01 | 1 | ¿Qué es el malware? | Software hecho para dañar, espiar o tomar el control de un equipo | Un tipo de hardware | Una red wifi | Un navegador | **A** |
| MAL-02 | 1 | ¿Qué hace el ransomware? | Acelera el equipo | Cifra tus archivos y pide un pago para devolverlos | Mejora el antivirus | Borra el historial del navegador | **B** |
| MAL-03 | 1 | ¿Cuál es una buena forma de reducir el riesgo de malware? | Abrir todos los adjuntos | Descargar programas "crackeados" | Instalar aplicaciones solo de tiendas oficiales y mantener todo actualizado | Desactivar el antivirus para ir más rápido | **C** |
| MAL-04 | 1 | ¿Cuál de estos es un ejemplo de malware? | Un procesador de texto | Un controlador de impresora | Un salvapantallas oficial | Un programa espía (spyware) | **D** |
| MAL-05 | 2 | ¿Qué es un troyano? | Un virus que se copia solo por la red | Un tipo de antivirus | Un tipo de firewall | Un programa que se hace pasar por legítimo para colar algo dañino | **D** |
| MAL-06 | 2 | ¿Qué distingue a un gusano (worm)? | Se propaga por sí solo por la red, sin que alguien ejecute un archivo | Solo afecta a equipos Mac | Solo borra imágenes | Necesita siempre un USB | **A** |
| MAL-07 | 2 | ¿Qué hace un keylogger? | Acelera el teclado | Registra lo que escribes | Cambia el idioma del teclado | Cifra el disco | **B** |
| MAL-08 | 2 | Sospechas que tu equipo tiene malware. ¿Qué haces primero? | Seguir usándolo con normalidad | Pagar a quien te lo pida | Desconectarlo de la red y analizarlo con un antivirus actualizado | Reiniciar y esperar | **C** |
| MAL-09 | 3 | ¿Qué es una botnet? | Una red wifi pública | Un tipo de VPN | Una red de equipos infectados que un atacante controla a distancia | Una base de datos de contraseñas | **C** |
| MAL-10 | 3 | ¿Qué es un rootkit? | Un kit de herramientas legítimo para administradores | Un tipo de phishing | Un cifrador de archivos | Malware que oculta su presencia y conserva acceso privilegiado | **D** |
| MAL-11 | 3 | ¿Por qué ayuda contra el ransomware una copia de seguridad desconectada de la red (offline)? | Porque el ransomware no puede cifrarla | Porque es más rápida | Porque está en la misma carpeta | Porque evita infectarse | **A** |
| MAL-12 | 3 | ¿Para qué sirve analizar un archivo sospechoso en una "sandbox"? | Para borrarlo | Para ejecutarlo en un entorno aislado y observar su comportamiento sin riesgo para el equipo | Para comprimirlo | Para enviarlo por correo | **B** |

**Tema 4: Ingeniería Social (`INGENIERIA_SOCIAL`)**

| Id | Esc. | Pregunta | A | B | C | D | Correcta |
|---|---|---|---|---|---|---|---|
| ISO-01 | 1 | ¿Qué es la ingeniería social? | Programar redes sociales | Manipular a las personas para obtener información o acceso | Un curso de redes | Un tipo de cifrado | **B** |
| ISO-02 | 1 | ¿Qué dato no te pediría nunca un soporte técnico legítimo? | El número de ticket | El nombre del equipo | Una descripción del problema | Tu contraseña | **D** |
| ISO-03 | 1 | ¿Qué es el "pretexting"? | Inventar una historia creíble para ganar tu confianza y sacarte datos | Un tipo de malware | Un protocolo de red | Una copia de seguridad | **A** |
| ISO-04 | 1 | ¿Qué es el "tailgating"? | Un virus | Un cifrado | Entrar a una zona restringida siguiendo de cerca a alguien autorizado | Una VPN | **C** |
| ISO-05 | 2 | Alguien deja memorias USB "perdidas" en un estacionamiento para que las conecten. ¿Qué técnica es? | Baiting (cebo) | Smishing | Spoofing | Doxing | **A** |
| ISO-06 | 2 | Un mensaje dice: "Solo te quedan 5 minutos para reclamar tu premio". ¿Qué principio de manipulación usa? | Autoridad | Reciprocidad | Urgencia | Simpatía | **C** |
| ISO-07 | 2 | Un "jefe" te escribe desde un correo nuevo y te pide comprar tarjetas de regalo con urgencia. ¿Qué haces? | Comprarlas rápido | Verificar el pedido por otro canal que ya conozcas | Responder con tus datos | Reenviarlo a un compañero | **B** |
| ISO-08 | 2 | ¿Qué es el "shoulder surfing"? | Un deporte acuático | Un malware | Un tipo de red | Mirar por encima del hombro para ver contraseñas o pantallas | **D** |
| ISO-09 | 3 | ¿Qué es el "vishing"? | Phishing por SMS | Phishing por llamada de voz | Phishing por QR | Phishing por correo | **B** |
| ISO-10 | 3 | ¿Qué es el "quishing"? | Phishing con códigos QR maliciosos | Phishing por voz | Un ransomware | Un cifrado | **A** |
| ISO-11 | 3 | ¿Qué información pública de tus redes sociales podría ayudar a un atacante a adivinar tus preguntas de seguridad? | El idioma de tu teléfono | La hora a la que publicas | El modelo de tu teléfono | El nombre de tu mascota, tu ciudad natal o tu colegio | **D** |
| ISO-12 | 3 | ¿Cuál es la mejor defensa general contra la ingeniería social? | Un antivirus más potente | Una contraseña más larga | Desconfiar de pedidos urgentes o inusuales y confirmarlos por otro canal | Un firewall más caro | **C** |

**Tema 5: Redes Seguras (`REDES_SEGURAS`)**

| Id | Esc. | Pregunta | A | B | C | D | Correcta |
|---|---|---|---|---|---|---|---|
| RED-01 | 1 | ¿Qué es más seguro en tu wifi doméstica? | Dejarla sin contraseña | Usar WEP | Poner tus datos personales en el nombre de la red | WPA3 (o WPA2) con una contraseña robusta | **D** |
| RED-02 | 1 | ¿Qué riesgo tiene una wifi pública abierta? | Gasta más batería | Es más lenta | Alguien puede espiar el tráfico que no esté cifrado | Ninguno | **C** |
| RED-03 | 1 | ¿Qué hace una VPN? | Acelera internet | Crea un túnel cifrado para tu tráfico | Elimina los virus | Reemplaza al antivirus | **B** |
| RED-04 | 1 | ¿Qué deberías cambiar en un router nuevo? | La contraseña de administración que trae de fábrica | El color | La antena | Nada: ya viene seguro | **A** |
| RED-05 | 2 | ¿Qué indica "https://" al inicio de una dirección? | Que el sitio es del gobierno | Que la conexión con el sitio va cifrada | Que el sitio no tiene anuncios | Que el sitio es oficial | **B** |
| RED-06 | 2 | ¿Qué es un ataque "man-in-the-middle"? | Un ataque que borra discos | Un tipo de VPN | Una copia de seguridad | Un atacante que se interpone entre dos partes y puede leer o alterar lo que se envían | **D** |
| RED-07 | 2 | ¿Para qué sirve tener una red de invitados separada en tu router? | Para que un dispositivo comprometido de un invitado no alcance tus equipos | Para ir más rápido | Para usar menos cables | Para ocultar el nombre de la red | **A** |
| RED-08 | 2 | ¿Qué es WPS en un router y qué conviene hacer con él? | Un cifrado muy fuerte; conviene dejarlo activo | Un antivirus del router; conviene actualizarlo | Un emparejamiento por PIN con debilidades conocidas; conviene desactivarlo | Una VPN integrada; conviene activarla siempre | **C** |
| RED-09 | 3 | ¿Qué es el "DNS spoofing"? | Un escaneo de puertos | Un cifrado de disco | Falsear respuestas de DNS para llevarte a un sitio falso | Una copia de seguridad | **C** |
| RED-10 | 3 | ¿Qué hace un "evil twin"? | Duplica tus archivos | Imita una red wifi legítima para capturar el tráfico de quien se conecta | Es un tipo de switch | Es una VPN gratuita | **B** |
| RED-11 | 3 | ¿Qué protocolo reemplaza a Telnet con comunicación cifrada para administrar equipos a distancia? | FTP | HTTP | SMTP | SSH | **D** |
| RED-12 | 3 | ¿Para qué sirve un escaneo de puertos con una herramienta como nmap en tu propia red? | Para descubrir qué servicios están expuestos | Para aumentar la velocidad | Para cifrar el tráfico | Para borrar registros | **A** |

**Tema 6: Firewall (`FIREWALL`)**

| Id | Esc. | Pregunta | A | B | C | D | Correcta |
|---|---|---|---|---|---|---|---|
| FIR-01 | 1 | ¿Dónde puede estar un firewall? | Solo en un router | Solo en la nube | Como programa en un equipo o como dispositivo en la red | Solo en el navegador | **C** |
| FIR-02 | 1 | ¿Qué significa "denegar por defecto"? | Bloquear todo lo que no esté permitido de forma explícita | Permitir todo salvo lo prohibido | Borrar todas las reglas | Apagar el firewall | **A** |
| FIR-03 | 1 | ¿Qué es un puerto de red? | Un cable | Un número que identifica un servicio en un equipo (por ejemplo, 443 para HTTPS) | Un tipo de virus | Una contraseña | **B** |
| FIR-04 | 1 | Un programa desconocido te pide que desactives el firewall para instalarse. ¿Qué haces? | Desactivarlo | Desactivarlo solo un rato | Reiniciar el equipo | No hacerlo y revisar de dónde viene el programa | **D** |
| FIR-05 | 2 | ¿Qué comando de ufw bloquea el tráfico que viene de la IP 198.51.100.9? | ufw deny from 198.51.100.9 | ufw allow from 198.51.100.9 | ufw status | ufw reset | **A** |
| FIR-06 | 2 | ¿Qué puerto usa HTTPS por defecto? | 21 | 443 | 25 | 80 | **B** |
| FIR-07 | 2 | ¿Qué diferencia a un firewall "con estado" (stateful)? | Solo mira la dirección de destino | Solo funciona con wifi | Bloquea todo el tráfico | Sigue el estado de las conexiones, no solo cada paquete suelto | **D** |
| FIR-08 | 2 | ¿Qué hace la regla "ufw allow 22/tcp"? | Bloquea el puerto 22 | Borra la regla del puerto 22 | Permite conexiones entrantes al puerto 22 (SSH) | Cambia el puerto de SSH | **C** |
| FIR-09 | 3 | ¿Qué es una DMZ en una red? | Una copia de seguridad | Una red wifi de invitados sin cifrado | Un antivirus de red | Una zona separada donde viven los servicios expuestos a Internet, aislada de la red interna | **D** |
| FIR-10 | 3 | ¿Por qué importa el orden de las reglas de un firewall? | No importa | Se aplican todas a la vez | Se evalúan en orden y la primera que coincide suele ser la que se aplica | Solo cuenta la última | **C** |
| FIR-11 | 3 | ¿Qué diferencia hay entre DROP y REJECT en un firewall? | DROP descarta el paquete en silencio; REJECT avisa al origen de que fue rechazado | DROP avisa al origen; REJECT no | Son lo mismo | DROP abre el puerto; REJECT lo cierra | **A** |
| FIR-12 | 3 | ¿Qué hace un WAF (Web Application Firewall)? | Cifra los discos | Filtra el tráfico HTTP hacia una aplicación web para frenar ataques como la inyección SQL o el XSS | Reemplaza al antivirus | Reparte el wifi | **B** |

**Conteo para revisar:** 6 temas × 12 = 72. Por escalón: 24 cada uno (4 por tema). Letras correctas: A 18, B 18, C 18, D 18.

### 9.5 Borrador de los retos de ulti (ataque y defensa)

D4 pide un reto de ataque y otro de defensa, **distintos**; O10 exige que cada reto corresponda a un **objetivo real de un laboratorio** del módulo 3, con su comando **exacto** de
`app/src/main/assets/learning_labs.json`. Se proponen 6 de ataque y 6 de defensa, de **dos conjuntos separados** (uno de cada tipo por tema) para que nunca coincidan. **No hay comandos nuevos**; el
comando de Figma (`ufw deny from 198.51.100.9`) ya no se usa. En pantalla solo se muestra el **título del objetivo** (tal como lo escribe el laboratorio); el comando se sabe de memoria.

| Id | Tipo | Tema (laboratorio) | Objetivo que se muestra (título en el JSON) | Comando exacto (en el JSON) |
|---|---|---|---|---|
| ULT-A1 | Ataque | `CONTRASENAS` | Probar una contraseña débil | `crack 123456` |
| ULT-A2 | Ataque | `PHISHING` | Inspeccionar el remitente | `whois nightwatch-secure-verify.com` |
| ULT-A3 | Ataque | `MALWARE` | Escanear el archivo con el antivirus | `scan sospechoso.exe` |
| ULT-A4 | Ataque | `INGENIERIA_SOCIAL` | Verificar la identidad del llamante | `lookup +1-555-0134` |
| ULT-A5 | Ataque | `REDES_SEGURAS` | Investigar la IP sospechosa | `whois 203.0.113.55` |
| ULT-A6 | Ataque | `FIREWALL` | Bloquear el SSH expuesto | `ufw deny 203.0.113.77 port 22` |
| ULT-D1 | Defensa | `CONTRASENAS` | Probar una contraseña robusta | `crack N!ghtW4tch#7kQ2` |
| ULT-D2 | Defensa | `PHISHING` | Comparar con el dominio oficial | `diff nightwatch-verify.io nw.app` |
| ULT-D3 | Defensa | `MALWARE` | Poner el archivo en cuarentena | `quarantine sospechoso.exe` |
| ULT-D4 | Defensa | `INGENIERIA_SOCIAL` | Reportar el intento de ingeniería social | `report-incident social-engineering` |
| ULT-D5 | Defensa | `REDES_SEGURAS` | Ver el registro de accesos | `cat access.log` |
| ULT-D6 | Defensa | `FIREWALL` | Verificar las reglas activas | `iptables -L INPUT` |

- **Normalización (P):** igual que la terminal del módulo 3 (E1-1, `normalizeCommand` en `LabTerminal.kt`): se ignoran los espacios de los extremos y los repetidos; el **nombre del comando** (primera palabra) no distingue mayúsculas, los **argumentos** sí. La implementa `sx_normalize_command` en SQL.
- **Los comandos se copian a `0009`** (tabla `ulti_challenges`) y quedan **duplicados** respecto del JSON del módulo 3: si alguien cambia un comando del laboratorio, hay que cambiarlo también en la base. Propuesta para la Etapa 2: una prueba unitaria que lea el JSON y compruebe que cada reto de esta tabla existe tal cual.
- Hasta **3 intentos** por jugador dentro de los 2 minutos; un comando incorrecto consume un intento y no termina el reto (O10).
- Los textos de salida ("Tu Ulti solo será efectiva si…") salen de Figma; los de defensa y escudo son nuevos (M4).

### 9.6 Lista para el grupo

Revisar: (1) que cada pregunta tenga una sola respuesta correcta; (2) que el nivel de cada escalón sea el adecuado; (3) que los textos estén en español claro y sin tildes faltantes; (4) los 12 retos; (5) si 12 por tema alcanzan (O12).

## 10. Plan de etapas

Cada etapa es **un commit** (o uno por tema si es grande) en la rama `modulo-4`, con su criterio de aceptación y sus pruebas. Antes de cada commit de código: `./gradlew assembleDebug testDebugUnitTest lintDebug` (y `compileDebugAndroidTestKotlin` si se tocan pruebas instrumentadas); `lintDebug` en 0 errores; las **951 pruebas existentes siguen pasando**; ninguna pantalla ni ViewModel importa Supabase ni `BuildConfig`. Claude Code no ejecuta SQL, no se conecta a Supabase y no hace push; lo que necesita al usuario se marca **(usuario)**. Nada se ve en un teléfono desde el repositorio.

| Etapa | Contenido | Criterio de aceptación | Pruebas |
|---|---|---|---|
| **0 (esta)** | La spec | Revisada por el grupo; decisiones de la sección 12 cerradas el 2026-10-21 (O1 a O15, D18 a D20); `realtime-kt` queda como mejora opcional posterior | Ninguna |
| **1. Script `0008` y guion SQL** | `supabase/migrations/0008_social.sql` (**escrito, borrador sin ejecutar**: 14 tablas, 5 funciones públicas `SECURITY DEFINER`, 20 internas, `GRANT`, RLS, verificaciones comentadas y la tabla de advertencias esperadas del Security Advisor); **también escritos:** el guion de escenarios `supabase/verificacion/0008_escenarios.sql` (lo corre el usuario a mano; autoverificable) y la "Parte F: Social" en `supabase-configuracion.md` | **(usuario)** lo ejecuta a mano y corre las verificaciones: RLS en las 14, exactamente 5 políticas (las otras 11 tablas, cerradas), nada para `anon`, `EXECUTE` solo para `authenticated` y solo en las 5 funciones públicas y `social_new_code`, `search_path` vacío, claves foráneas solo a `auth.users` y tablas nuevas. Después revisa el Advisor y cada fila nueva se corrige o se documenta. No se tocó ninguna tabla anterior | Sin pruebas automáticas (F-03): guion manual con estos casos: crear usuario (único, sin distinguir mayúsculas), búsqueda exacta y límite de ritmo, invitación y aceptación, racha de pareja (con fechas de prueba escritas por el usuario como administrador), respuesta correcta (-20), incorrecta, 3 aciertos y ulti cargada, ulti 45, ulti con escudo (23), ulti fallida (pierde la carga), turno vencido, desconexión de 60 s, reto vencido a los 2 minutos, XP al terminar |
| **2. Contenido `0009`** | `0009_banco_batallas.sql`: 72 preguntas, 12 retos de ulti y la curva de XP | **(usuario)** aplicado. Conteos: 12 por tema y 4 por escalón por tema, 6 + 6 retos, una sola correcta por pregunta, 18 de cada letra, ninguna pregunta del examen repetida. El grupo aprobó el texto. Depende de O1 y O12. **HECHA: `0009` aplicado y verificado el 2026-10-21** (72 preguntas, 18 por letra, 12 retos, 20 niveles, máximo 1.900 XP) y el guion de escenarios repetido con el banco real: **219 de 219, 0 fallas** | Consultas de conteo; ninguna prueba automática |
| **3. Capa de datos del cliente** | Modelos y repositorios con interfaz (`SocialRepository`, `FriendsRepository`, `PairStreakRepository`, `BattleRepository`), clases `Supabase*` en `data/supabase`, *fakes*, nuevos tipos de error (por ejemplo, nombre en uso, demasiados intentos), `AppContainer`. Sin UI, sin dependencias nuevas | Mapeo de filas y de errores; ninguna excepción de la librería llega a la pantalla | Pruebas de mapeo y de errores como las del módulo 3; una prueba que comprueba que ninguna pantalla ni ViewModel importa Supabase |
| **4. Social habilitado** | Pestaña Social activa, SOCIAL - INICIO, crear nombre de usuario (M1), `hide_progress` en el servidor, estados vacíos y de error (M12) | Sin nombre: pide uno; con nombre: Inicio de Social. El interruptor "ocultar progreso" guarda en el servidor | ViewModels con Turbine y *fakes* (sin perfil, con perfil, nombre ocupado, sin conexión); el cambio de la barra inferior |
| **5. Amigos** | Lista, agregar (usuario o id, copiar código), solicitudes (aceptar, rechazar, cancelar), perfil del amigo con insignias y "ocultar progreso", eliminar | Sin "Sugerencias" en ninguna pantalla. La búsqueda solo coincide exacto. Un perfil oculto no muestra nivel, insignias ni islas | ViewModels; búsqueda sin coincidencia; límite de ritmo; perfil oculto y visible; evaluación de insignias con los `BadgeFacts` del amigo |
| **6. Rachas de pareja** | Invitar, aceptar, lista de parejas, detalle, tarjeta de Inicio con la mayor, "Hacer mi ejercicio de hoy →". **Tarea de la app, módulo 3 (mínimo):** la racha personal propia también se calcula con **hora de Colombia** (`America/Bogota`): hoy `computeStreak` del módulo 3 usa la zona del dispositivo; `ProgressSummaryProvider` pasa a usar esa zona, para que la racha propia coincida con la de los amigos (`sx_personal_streak`) y las parejas | Racha de 0 a 6 como máximo; varias parejas; estados *activa*, *en riesgo*, *sin racha* y *ruta completa*. La racha personal propia no cambia de día a las 00:00 del dispositivo sino a las 00:00 de Colombia | ViewModels con *fakes*; la derivación en SQL se verifica con el guion de la Etapa 1 y se repite con los datos de prueba. `computeStreak` ya recibe la zona como parámetro: se añaden pruebas con `America/Bogota` (un instante a las 23:30 de Colombia cuenta para ese día aunque en UTC sea el siguiente) **sin borrar ni cambiar las existentes**; los 951 casos siguen pasando |
| **7. Batallas I: lobby, cola y retos** | **Con sondeo** (D18, sin dependencia nueva): BATALLAS, BUSCANDO OPONENTE con la sugerencia a los 60 s, retos recibidos y enviados, cuenta de 2 minutos. La prueba de concepto de `realtime-kt` es opcional y posterior | La búsqueda se cancela; un reto vence a los 2 minutos y no se puede aceptar; la sugerencia aparece a los 60 s; cerrar Social cancela lo pendiente | Cuentas regresivas con tiempo virtual (`kotlinx-coroutines-test`); cola; reto vencido; sondeo |
| **8. Batallas II: arena** | Pregunta con temporizador de 1 minuto, espera de turno, correcto, incorrecto, turno perdido, latidos, desconexión y reconexión | El servidor decide todo; la app solo muestra la cuenta; la respuesta correcta no está en ningún estado ni registro; un doble toque no responde dos veces | ViewModels con *fake* del servidor: respuestas, vencimiento, reconexión, sondeo; una prueba que comprueba que ningún modelo del cliente tiene el campo de respuesta correcta |
| **9. Ulti** | Ulti disponible, "Guardar para después", desafío, defensa con escudo, éxito y fallo | Los dos retos a la vez, de 2 minutos y con hasta 3 intentos; el comando no se muestra; el resultado sigue la tabla de la sección 6.4 | Estados y tiempos; intentos (incorrecto consume uno y no termina); envío duplicado; resultados con y sin escudo |
| **10. Fin de batalla, XP y nivel** | Victoria, derrota, empate, "Revancha", "Volver a Social", historial, marcador, XP y nivel | XP y nivel salen del servidor; "Repasa X y Y"; el marcador coincide con el historial | ViewModels; marcador y racha de batallas |
| **11. Cierre** | `reports/modulo-4-diagnostico.md` (pantallas, diferencias con Figma, pruebas, lista visual y guion manual), actualización de `CLAUDE.md`, `README.md` y la guía de Supabase (Advisor), versión (2.2.0, `versionCode` 5, a confirmar al cierre) | Todo en verde, lint en 0 errores, diferencias documentadas, sin secretos en el diff | Recuento de pruebas del cierre |

**Orden de despliegue.** `0008` y `0009` se ejecutan **antes** de instalar la versión de la app de las Etapas 4 a 10 (sin las tablas, Social falla al cargar; se muestra el error con "Reintentar"). El plan gratuito pausa el proyecto tras 7 días sin actividad: antes de probar en el teléfono, comprobar que no esté pausado.

## 11. Riesgos y limitaciones

| # | Riesgo o limitación | Qué se hace |
|---|---|---|
| R1 | **Plan gratuito:** 200 conexiones de Realtime, 2 millones de mensajes al mes y 100 mensajes por segundo (verificado el 2026-10-21). Sobra para un curso, pero un evento con muchas personas a la vez se acercaría al tope | Estimaciones de la sección 5.1; con la opción A el consumo es de peticiones, no de conexiones |
| R2 | **Pausa a los 7 días sin actividad** (aceptada): Social, que depende de que haya otras personas, deja de funcionar hasta *Restore project* | Antes de una demostración, entrar al panel; documentar |
| R3 | **Sin notificaciones fuera de la app (D16):** una invitación o un reto solo se ven al abrir Social; el reto dura 2 minutos, así que **retar a alguien que no tiene Social abierto casi siempre vence**. Pasar a segundo plano corta los latidos y cuenta como desconexión (D5) | Aceptado por D16. El sondeo cada 5 a 10 s con Social abierto hace que el reto aparezca sin reabrir |
| R4 | **Trampas en el cliente:** el servidor decide todo, pero el comando de la ulti se ve en pantalla; un cliente modificado puede enviarlo al instante y ganar la carrera del escudo. Tampoco se puede impedir que alguien juegue con una segunda cuenta | Aceptado: es la clase del R2 del módulo 3. El servidor registra la hora de cada envío |
| R5 | **Limitación conocida: XP farmeable con dos cuentas.** Ganar por **abandono o por desconexión** del rival da la victoria completa (**+50 XP**); quien pierde así recibe 0. Una persona con **dos cuentas** puede entrar con una, abandonar con la otra y subir de nivel; también puede "regalarse" victorias | Sin ranking ni premios en este módulo, el impacto es bajo: el XP solo sube el nivel de combatiente y la dificultad de las preguntas. **Se revisa si se agrega un ranking** (opciones: XP reducido o nulo por abandono o contra la misma cuenta, tope diario). Documentada también en el encabezado de `0008` |
| R6 | **Justicia de la ulti:** quién "llegó antes" depende de la latencia de cada red | Documentado (6.4); se mide en la prueba de concepto |
| R7 | **Banco pequeño:** 24 preguntas por escalón se repiten pronto | O12; ampliar con scripts nuevos sin cambiar la app |
| R8 | **Reglas duplicadas en SQL y Kotlin:** la racha personal vive en el cliente y la de pareja en el servidor (con otra zona horaria) | Guion de casos; las insignias del amigo se evalúan con el mismo código del cliente (4.5) |
| R9 | **Complejidad del script:** `0008` es grande y se ejecuta a mano; un error ya aplicado se arregla en un script nuevo, no se edita | Revisión por el grupo antes de ejecutar; verificaciones comentadas; entrega en un solo archivo pero revisable por bloques |
| R10 | **Advertencias del Security Advisor:** 5 de "Signed-In Users Can Execute SECURITY DEFINER Function" (antes de agrupar iban a ser 32) y 11 de "RLS Enabled No Policy" | Documentarlas por grupo (4.6); cualquiera inesperada se corrige antes de fusionar |
| R11 | **Dependencia `realtime-kt` (mejora opcional posterior, D18):** no verificada con Kotlin 2.2.10 y Ktor 3.3.0 | No se agrega ahora. Solo con aprobación del equipo y tras una prueba de concepto; si falla, se queda el sondeo |
| R12 | **Moderación y menores (O15, reemplazada el 2026-10-22).** Los **nombres de usuario son texto libre** (solo se limita el largo y los caracteres, sin lista de palabras prohibidas) y la partida rápida empareja desconocidos (el análisis previo lo marcó). Desde `0011` (borrador) hay **reportar y bloquear** y el equipo puede **ocultar una foto**. **Limitación que queda:** no hay pantalla de moderación (se revisa `social_reports` en el Table Editor), nadie recibe un aviso cuando llega un reporte, el nombre de usuario ofensivo no se puede cambiar (O3) y no hay verificación de edad | El grupo acepta que la moderación sea manual; se documenta en `CLAUDE.md` al cierre. Si más adelante se abre a un público amplio, hay que automatizarla (lista de palabras, cola de revisión, pantalla) antes |
| R13 | **Enumeración parcial:** se puede comprobar si un nombre de usuario existe | Límite de ritmo, coincidencia exacta, sin listados; documentado (4.4) |
| R14 | **Pausa de la racha de pareja por límite de contenido (D14):** con 6 islas la racha no pasa de 6 días | Aceptado por el grupo (D14) |
| R15 | **Sin pruebas automáticas contra Supabase (F-03)** del juego, las rachas ni los permisos | Guion manual por etapa y lista de verificación en el cierre |
| R16 | **Borrado de cuenta:** borra el historial de batallas de la otra persona (P) | Alternativa en 3.1; decidir antes del script |

## 12. Decidido por el grupo (antes, preguntas abiertas)

El 2026-10-21 el grupo respondió las preguntas abiertas de la v1: **aceptó tal cual las propuestas de O1 a O9 y de O11 a O14**, **cambió O10** y dejó **O15 fuera del
módulo** (el 2026-10-22 el grupo **reemplazó** O15: se agregan reportar y bloquear, script `0011`). La columna "Decisión" es lo que se implementa; las filas O10 y O15 reemplazan a la propuesta original. Los mismos acuerdos están resumidos en la tabla de decisiones del
inicio.

### O1 a O5 (pedidas por el grupo)

| # | Pregunta | Decisión |
|---|---|---|
| **O1** | **Curva de XP del 1 al 20 y XP de una victoria, una derrota y un empate** (Figma muestra +50 por victoria) | 100 XP por nivel (nivel = 1 + XP ÷ 100, tope 20 con 1.900 XP). Victoria **+50**, empate **+25**, derrota **+10** (un mínimo por jugar). 38 victorias llevan al nivel 20 |
| **O2** | **Quién empieza el primer turno y cómo se cierra una batalla** (HP en 0; límite de turnos o de duración) | Empieza una persona elegida **al azar por el servidor**. Termina cuando el HP de alguien llega a 0. Límite: **10 turnos por jugador** (20 en total); al llegar, gana quien tenga más HP; mismo HP = **empate**. Si los dos se desconectan más de 60 s: batalla cancelada, sin ganador ni XP, no cuenta en el marcador. El empate no rompe ni suma la racha de batallas |
| **O3** | **¿El nombre de usuario se puede cambiar después?** | **No** en la primera versión (más simple y evita suplantaciones). Se podría agregar luego con un script nuevo |
| **O4** | **Cuánto vale una invitación a racha de pareja sin responder y cuántas pueden estar pendientes a la vez** | Vale **3 días** y se permiten **5 pendientes enviadas** a la vez; las recibidas no tienen tope. Al vencer desaparece sin aviso |
| **O5** | **Qué se ofrece como ejercicio del día cuando ya se completaron todos** | **Nada**: la racha se detiene y se explica en pantalla ("Ya completaste las 6 islas"); el botón "Hacer mi ejercicio de hoy →" se deshabilita con ese texto |

### O6 a O15 (surgieron al escribir la spec)

| # | Pregunta | Decisión |
|---|---|---|
| **O6** | Formato del **nombre de usuario** (largo, caracteres permitidos, mayúsculas, palabras prohibidas) y del **código de amigo** (Figma: `NIGHT-7F3K`) | Usuario: 3 a 20 caracteres, letras, números y `_`, único sin distinguir mayúsculas, sin lista de palabras prohibidas. Código: `NIGHT-` más 6 caracteres sin ambigüedades (sin 0/O ni 1/I), generado por el servidor |
| **O7** | **Límites de amistad:** máximo de amigos, de solicitudes pendientes enviadas y espera tras un rechazo | Máximo 50 amigos; 20 solicitudes pendientes enviadas; 24 horas de espera para volver a invitar a quien rechazó |
| **O8** | ¿La **pareja de racha exige amistad**? ¿Qué pasa con la pareja si se elimina la amistad? | Sí exige amistad aceptada. Eliminar al amigo termina la pareja y se pierde la racha |
| **O9** | **Día de aceptación:** ¿cuenta para la racha, y cuenta un ejercicio hecho ese mismo día antes de aceptar? | El día de aceptación cuenta, y cualquier ejercicio completado ese día de Colombia cuenta (regla por día, no por hora) |
| **O10** | **Ulti:** ¿cuántos envíos tiene cada jugador? ¿Un comando mal escrito es fallo inmediato? ¿Usar la ulti gasta el turno? | **Cambia respecto de la propuesta.** Hasta **3 intentos** por jugador dentro de los 2 minutos; un comando incorrecto consume un intento y no termina el reto. El comando **no se muestra** (se sabe de memoria): cambia el diseño de DESAFÍO ULTI (diferencia S15). Los retos usan **exactamente los comandos de los laboratorios** del módulo 3 (sección 9.5). El primero en acertar gana la carrera; atacante sin intentos o sin tiempo: no pasa nada y pierde la carga; defensor que acierta antes: escudo; defensor que falla o sin tiempo: sin escudo. Usar la ulti gasta el turno (de la propuesta, sin cambio) |
| **O11** | **Ritmo en que se amplía el rango** de ±3 niveles y si tiene tope | +1 nivel cada 10 s de espera, sin tope (a los 60 s ya cubre todo el rango 1 a 20) |
| **O12** | **Banco:** equivalencia de niveles de combatiente con los tres escalones, ¿hace falta haber visto la lección de un tema para recibir sus preguntas?, ¿12 por tema alcanzan? | Escalones 1 a 7, 8 a 14 y 15 a 20; no hace falta haber visto la lección; ampliar a 20 por tema después si se repiten |
| **O13** | **"Revancha"** de las pantallas de fin: ¿a quién reta y vale en partida rápida? Y la "X" de la arena: ¿abandona? | Revancha envía un reto de 2 minutos al mismo rival (solo si es amigo; en partida rápida el botón no aparece). "X" pide confirmación y abandonar da la derrota |
| **O14** | **Qué es "nivel" en D11** (¿nivel de nivelación, de combatiente o ambos?) y si se ven **fotos reales** de los amigos (bucket privado) o solo los emojis de Figma | "Nivel" oculta ambos en el perfil; el de combatiente se sigue usando en el emparejamiento sin mostrarse. Solo emojis, sin fotos de amigos |
| **O15** | **Moderación y menores:** reportar o bloquear usuarios, y si la partida rápida entre desconocidos se abre a toda la base de usuarios | **Reemplazada el 2026-10-22** (antes: "fuera del módulo", R12). **Reportar a cualquier persona** (desde la búsqueda, las solicitudes, el perfil del amigo y el rival de una batalla, partida rápida incluida) y **bloquear**. Ningún número de reportes oculta una foto automáticamente: solo el equipo, a mano, con `social_profile.avatar_hidden` desde el SQL Editor. **Bloquear no termina una batalla en curso**, solo impide las nuevas; elimina la amistad y la pareja y cancela los retos pendientes, y vale en los dos sentidos (búsqueda, invitaciones, retos y partida rápida). **Sin pantalla de moderación:** el equipo revisa `social_reports` en el Table Editor (limitación honesta). Límite: 10 reportes por día. Script `0011` (borrador sin aplicar) |

### Otras decisiones del 2026-10-21

| # | Decisión |
|---|---|
| D18 | Tiempo real: se empieza con **sondeo** (sin dependencia nueva). `realtime-kt` solo se agrega si el equipo lo aprueba después de una prueba de concepto; es una mejora opcional posterior |
| D19 | La tarjeta "Racha grupal" de SOCIAL - INICIO se llama **"Racha en pareja"** (diferencia deliberada frente a Figma, S2 y S16) |
| D20 | Versión del cierre: **2.2.0**, `versionCode` 5 (a confirmar al cierre) |

### Por confirmar (no son preguntas abiertas)

- La versión 2.2.0 / `versionCode` 5 se confirma en la Etapa 11.
- El título de la pantalla de lista de parejas (D15) no estaba decidido: la spec supone **"Racha en pareja"**, como la tarjeta de Inicio.

## 13. Perfil social editable (rama `social-perfil`, script `0012`)

Seguimiento del módulo 4 pedido el 2026-10-25. **Estado: implementado en la rama `social-perfil`; `0012` es un BORRADOR sin aplicar** y nada de esto se ha comprobado contra
un servidor real ni en un teléfono. Figma: SOCIAL UPDATE - INICIO (2638:258, reemplaza a 2440:163), PERFIL AMIGO (2638:374, reemplaza a 2446:265), TU PERFIL (2653:543),
EDITAR TU PERFIL (2653:611) y el componente "Editar fondo" (2653:701).

### 13.1 Decisiones (cerradas por el grupo)

- **Propósito:** cada persona ve y edita **su propio** perfil social (antes solo se veía el de los amigos).
- **Inicio:** tarjeta nueva "Tu Perfil" arriba (foto o emoji, chip "LVL n" con el nivel de combatiente, "Ver Perfil →"). La tarjeta de la pareja **sigue llamándose "Racha en pareja"** (D19).
- **Perfil del amigo y Tu perfil:** la pantalla no se desplaza; orden: cabecera (foto de 130 dp, nombre, nivel de combatiente, "Ya entrenó hoy", racha), Ejercicios / Labs superados,
  Insignias, Estadísticas e Historial de batallas. Los botones del fondo quedan fijos; si algo se oculta, lo de abajo sube; si no cabe (pantalla pequeña o fuente grande) solo el
  área central se desplaza. El historial es una caja con desplazamiento propio con **máximo 3** batallas ("Aún no hay batallas" si no hay).
- **Estadísticas:** "Batallas jugadas", "Victorias" y "Winrate" (victorias / jugadas redondeado a entero; con 0 jugadas muestra "-", nunca se divide). Son **los totales de la persona dueña
  del perfil contra cualquier rival** (todas sus batallas terminadas, abandonos y desconexiones incluidos; las canceladas no tienen fila). En Tu perfil salen de `battle_results` por
  `FighterRepository` (la misma lectura de Inicio, sin consulta nueva). En el perfil del amigo las calcula el servidor con las filas del amigo (`0012`).
- **Historial:** en Tu perfil, tus 3 últimas contra cualquier rival ("🏆 Ganaste vs X", "hace N días"); en el perfil del amigo, las 3 últimas **entre ustedes dos**.
- **Editar tu perfil:** un ojo (16 dp, área táctil de 48 dp, con descripción y estado) junto a Insignias, Estadísticas e Historial de batallas; un lápiz (32 dp) que abre el selector de
  fondo; GUARDAR CAMBIOS y CANCELAR fijos abajo. Es un **borrador local** hasta guardar: CANCELAR, la "X" y Atrás lo descartan. Un guardado fallido conserva el borrador.
- **Efecto de los ojos:** un apartado oculto **no sale** en el perfil del amigo ni en Tu perfil (solo en el editor, atenuado). **El servidor lo omite** de la respuesta. "Ocultar mi progreso
  público" (D11) sigue mandando sobre Insignias, Ejercicios/Labs y nivel **en lo que ven los demás**; Estadísticas e Historial solo dependen de su propio ojo.
- **Fondos:** 5 imágenes (`profile_bg_1` a `profile_bg_5`, 412 x 844, ya en `drawable-nodpi`) más "sin fondo". El servidor guarda **solo un número 0 a 5**; uno desconocido es "sin fondo".
  El fondo cubre toda la pantalla de Tu perfil, Editar y Perfil amigo **detrás** del contenido, salvo la barra superior (título y "X", por encima de la línea blanca), con capa oscura suave
  y recorte al centro.
- **Sin el `0012` aplicado:** el perfil del amigo se sigue mostrando con lo que hay y avisa "Función no disponible por ahora"; guardar en Editar falla con el mismo aviso.

### 13.2 Qué agrega el borrador `0012_perfil_social.sql`

- `social_profile`: `profile_bg smallint 0..5`, `show_badges`, `show_stats`, `show_history` (todas `not null`, con valor por defecto). El cliente las cambia **solo** con `UPDATE` por columna (como `hide_progress`);
  no hay `INSERT` de ellas; la RLS de dueño limita el `UPDATE` a la fila propia.
- `social_friends` (`create or replace`, misma firma, partiendo de la versión de `0011`): la acción `profile` devuelve además `profile_bg`, `show_badges`, `show_stats` y `show_history`;
  `stats` = `{played, wins, losses, draws}` con el conteo total de **todas** las batallas del amigo (sin el límite de 10); `history` = las 3 últimas **entre quien llama y el amigo**.
  **Omitir = clave ausente** en `stats` e `history`; `progress` conserva su contrato (la clave existe y vale `null` si se omite por `hide_progress` o por `show_badges = false`).
- Ninguna función pública ni tabla nuevas: ningún cambio en el Security Advisor (siguen 10 advertencias, 0 errores y 14 sugerencias, a confirmar al aplicarlo).
- Guion `supabase/verificacion/0012_escenarios.sql` (**no ejecutado**): permisos por columna, fondo y ojos, omisión, estadísticas con más de 10 batallas, historial de 3, `hide_progress`, bloqueo y `avatar_hidden`.

### 13.3 Código

`data/social` (`ProfileDisplaySettings`, `BattleStats`, parser tolerante, `SocialProfileRepository.updateDisplaySettings`), `ui/social/ProfileSections.kt` (cabecera, apartados, ojo, selector y
`SocialProfileScreen` con el fondo), `FriendProfile*`, `MyProfile*`, `EditMyProfile*`, tarjeta en `SocialTab`, textos en `strings_social_perfil.xml` (prefijo `social_`; `strings_social.xml` es solo de errores).

### 13.4 Decisiones de implementación (no estaban en el pedido)

1. **El ojo de Insignias oculta todo el bloque de progreso** (nivel de combatiente, Ejercicios y Labs, además de las insignias), porque el contrato `progress` los envía juntos (`BadgeFacts` + `fighter_level`).
2. En **Tu perfil**, `hide_progress` no oculta nada propio (solo afecta a lo que ven los demás): mandan solo los ojos.
3. Los textos nuevos van en `strings_social_perfil.xml` y no en `strings_social.xml`, que por su encabezado es solo de mensajes de error.
4. Las estadísticas e historial propios reutilizan `FighterRepository.getSummary()` (trae las filas de `battle_results` de la persona, hasta hoy sin límite): es la lectura que ya usa Inicio y evita una consulta nueva.
5. `ProgressSummary` (módulo 3) ganó tres campos con valor por defecto: `trainedToday`, `viewedLessonCount` y `completedLabCount`.
6. Los ojos hacen que la fila del título mida 48 dp **solo en el editor** (en los perfiles el título mide lo normal).
7. Tu perfil y Editar tu perfil entraron en un solo commit: Editar reutiliza el cargador y las rutas, y los dos se cablean juntos en la navegación.

### 13.5 Diferencias con Figma

| # | Figma | Aplicación |
|---|---|---|
| F1 | La tarjeta se llama "Racha grupal" | **"Racha en pareja"** (decisión del grupo, D19) |
| F2 | Tarjeta "Tu Perfil" con el emoji 🧑 | Muestra la foto de la persona si la tiene y el emoji si no |
| F3 | Lápiz e iconos como imágenes del diseño | El lápiz es el carácter "✎" y el ojo se dibuja con líneas (ojo tachado propio), sin imágenes con derechos |
| F4 | Selector 200 x 82 con casillas de unos 57 x 30 | Igual de medidas; las casillas son más bajas que 48 dp (solo los ojos cumplen los 48 dp) |
| F5 | El editor cabe en 917 dp | Con los ojos de 48 dp el editor mide unos 50 dp más; si no cabe, el área central se desplaza y los botones siguen fijos |
| F6 | Textos sin tildes ("Ya entreno hoy", "dias") | Con tildes; en Tu perfil "Ya entrenaste hoy" |
| F7 | "Nivel de combatiente: 1" | "Nivel de combatiente 1" (el texto que ya existía) |
| F8 | Tres insignias fijas | Las insignias desbloqueadas en una fila que se desplaza de lado si no caben |
| F9 | Posiciones absolutas en una pantalla de 412 x 917 | Distribución flexible; la cabecera, los apartados y el historial siguen el orden y las medidas del diseño, ajustadas al ancho |

### 13.6 Qué no se pudo comprobar

Nada en un teléfono (el aspecto exacto, el contraste del texto sobre cada fondo, el selector y su zona táctil, TalkBack) ni contra un Supabase real (`0012` sin aplicar, su guion sin ejecutar).

## Diagnóstico de la Etapa 0 (v1)

- **Qué se hizo:** esta spec (un solo archivo nuevo). Ningún código de la app, prueba, recurso, `gradle`, `CLAUDE.md` ni script SQL se tocó. No se editó `supabase/migrations/`.
- **Qué no se pudo verificar:** nada contra un Supabase real; nada en un teléfono; la API de `realtime-kt` en Kotlin; cómo cuenta Supabase cada mensaje de Realtime; si `pg_cron` está disponible en el plan gratuito; las pantallas de Figma se revisaron por el texto de las capas y por capturas de 11 de los 17 frames (los frames AMIGOS, ARENA PREGUNTA, ARENA INCORRECTO, BUSCANDO OPONENTE, DESAFÍO ULTI y ULTI FALLO solo por el texto de sus capas).
- **Pruebas y compilación:** no se ejecutaron (no hay cambios de código). Las 951 pruebas del módulo 3 no se vieron afectadas.
- **Git:** rama `modulo-4`; un solo commit de documentación; sin push; `main` sin tocar.

