# Social (candidato a módulo 4): análisis previo, sin diseño ni SQL

Estado: **solo análisis para que el grupo decida.** No hay tablas, SQL, rutas ni código. Fecha: 2026-10-18. Rama: `modulo-3`.
Origen: el equipo sacó Social del módulo 3 (`2026-10-18-modulo-3-design.md`, sección 1). En el módulo 3 solo existe la pestaña "Social" atenuada, que muestra un aviso y no navega.

Convención: cada afirmación cita su FUENTE (Figma `nombre (ID)` o archivo). Lo que no tiene fuente es **SUPUESTO** o **PREGUNTA ABIERTA**.

## 1. Qué muestra Figma (17 pantallas)

| Pantalla | Nodo | Qué se ve (FUENTE: Figma) |
|---|---|---|
| Social - Inicio | 2440:163 | Tres tarjetas: Racha grupal ("12 días"), Amigos ("8 amigos"), Batallas ("5V · 2D") |
| Amigos | 2446:181 | Lista con nombre, "🔥 N días de racha" y "✅ Ya entrenó hoy" / "⏳ Pendiente hoy" |
| Agregar amigos | 2446:212 | Buscar por usuario (`ej. valeNightwatch`), "Tu código de amigo" (`NIGHT-7F3K`), solicitudes (2), sugerencias con "Agregar +" |
| Solicitudes | 2446:242 | Recibidas (Aceptar/Rechazar), enviadas (Cancelar solicitud) y sugerencias |
| Perfil de amigo | 2446:265 | Nombre, racha, "47 Ejercicios", "5 Labs superados", insignias, historial de batallas, "Eliminar amigo" |
| Racha grupal | 2447:186 | Días seguidos, semana L-D y estado de cada amigo hoy |
| Batallas | 2448:187 | Victorias/derrotas, "Partida rápida" (emparejamiento por nivel) y "Retar a un amigo" |
| Buscando oponente | 2448:223 | Búsqueda con "Cancelar búsqueda" |
| Arena (pregunta / correcto / incorrecto) | 2449:189, 2449:221, 2449:255 | Duelo por turnos con HP (100), pregunta de 4 opciones y daño de 20 HP |
| Ulti (disponible / desafío / éxito / fallo) | 2449:290, 2450:193, 2450:211, 2450:223 | Con 3 aciertos seguidos se desbloquea una "Ulti" que exige un comando de terminal antes de que termine el turno (daño 45) |
| Fin de batalla (victoria / derrota) | 2451:195, 2451:209 | "+50 XP", racha de batallas, marcador de victorias y derrotas |

Ningún documento del repo define Social (solo lo nombra como subsistema: `2026-09-23-auth-onboarding-design.md:15-19`; `profile-settings-design.md:16`). Todo lo de abajo parte únicamente de Figma.

## 2. Datos de OTROS usuarios que habría que leer

| Función | Datos de otras personas | Sensibilidad |
|---|---|---|
| Amigos / Perfil de amigo | nombre visible, avatar, racha, si entrenó hoy, nº de ejercicios, labs superados, insignias, historial de batallas contra ti | Media: es actividad y progreso |
| Agregar amigos | búsqueda por **usuario** y **código de amigo**; sugerencias ("Camila", "Lucas") | Alta: la búsqueda y las sugerencias exponen identidades |
| Batallas | nivel del rival, su respuesta y su HP en tiempo real | Media |
| Racha grupal | quién de tus amigos entrenó hoy | Media |

**Qué no existe hoy y haría falta (PREGUNTAS ABIERTAS):**
- Un **nombre de usuario o alias público**. Hoy `profile` guarda correo de recuperación, avatar, género y fecha de nacimiento (`0001`); el nombre vive en Auth. Figma da ejemplos como `valeNightwatch`, pero no define reglas (largo, unicidad, cambio).
- Un **código de amigo** (`NIGHT-7F3K`): cómo se genera, si caduca.
- **De dónde sale "Sugerencias"**: Figma no lo explica y cualquier criterio ("usuarios del mismo nivel", "contactos") expone datos.

## 3. Cómo se protegería (hoy todo es solo del dueño)

Hoy todas las tablas tienen RLS `user_id = (select auth.uid())` y `anon` no accede a nada (`CLAUDE.md` sección 4). Lo social **rompe esa regla** porque una persona debe ver datos de otra. Principios, sin SQL:

1. **Nunca abrir las tablas privadas.** `profile`, `placement`, `devices` y los progresos del módulo 3 siguen siendo solo del dueño. No se agrega ninguna política "ver a mis amigos" sobre ellas, porque las políticas RLS dan acceso a **filas completas** y `profile` contiene el correo de recuperación, la fecha de nacimiento y el género.
2. **Una tabla pública aparte**, con **solo** los campos que la persona acepta mostrar (alias, avatar, racha, contadores). Se llena por una función `SECURITY DEFINER` (con `search_path` vacío) a partir de los datos reales, no por escritura libre del cliente.
3. **La amistad como relación explícita** (solicitud → aceptada) y políticas que solo muestren esa tabla pública a quien tenga una amistad aceptada con la persona. La búsqueda por alias o código se hace por **una función** que devuelve el mínimo (alias y avatar), nunca una lista abierta.
4. **Prohibido exponer correos, el `user_id` de Auth, fecha de nacimiento, género, dispositivos ni nada de `auth.users`.** Esto incluye `account_status` (ya expone si un correo existe, `CLAUDE.md` sección 8): social no puede añadir otra vía de enumeración.
5. **Mínimo privilegio por columna** y `GRANT` solo a `authenticated`, como en 0001 y 0002.
6. **"Ocultar mi progreso público"** ya existe como switch (`UserPreferences.HIDE_PUBLIC_PROGRESS`, `profile-settings-design.md:217`) pero hoy solo guarda el interruptor en el dispositivo. Para ser real tendría que guardarse en el servidor y respetarse en la tabla pública. Es **obligatorio** resolverlo antes de publicar cualquier dato de progreso.

## 4. Privacidad que se expondría

| Dato | ¿Se expone? | Nota |
|---|---|---|
| Correo | **No, nunca** | Ni por búsqueda ni por sugerencias |
| Nombre real | PREGUNTA ABIERTA: ¿se usa el nombre de Auth o un alias propio? | Un alias reduce el riesgo |
| Avatar | Solo si se decide | Hoy está en un bucket privado (`0004`); mostrarlo a amigos exige otra política de Storage o URLs firmadas |
| Racha, ejercicios, labs, insignias | Solo para amigos aceptados, y respetando "ocultar mi progreso" | Hoy son datos fijos |
| Historial de batallas | Solo entre los dos participantes | |
| Edad / género / dispositivos | **No** | |

Menores de edad: la app guarda fecha de nacimiento. Un servicio social entre desconocidos ("Partida rápida" con alguien de tu nivel, "Sugerencias") tiene implicaciones de seguridad y de normas de plataformas que el grupo debe revisar antes de abrirlo (PREGUNTA ABIERTA).

## 5. Tiempo real y plan gratuito

- **Partida rápida y Arena** (2448:223, 2449:189) son duelos por turnos en tiempo casi real. Eso exige **Realtime** (canales de Supabase) o consultas periódicas, más un **emparejamiento** (cola, rival "de tu nivel") y reglas de turno, HP, tiempo límite y abandono. **Ninguno está definido** en ningún documento.
- El emparejamiento y la validación de turnos con la **clave publishable** y solo RLS son difíciles de hacer a prueba de trampas: un cliente modificado podría declararse ganador. Hacerlo bien requiere funciones SQL o Edge Functions con reglas del servidor.
- **Plan gratuito:** Realtime y las Edge Functions tienen cuotas de conexiones y mensajes. **No se verificaron aquí (no hay conexión a Supabase ni consulta a la documentación actual); hay que revisarlas en la página de precios vigente antes de decidir.** Además, el proyecto se pausa tras 7 días sin actividad (`CLAUDE.md` sección 8), lo que encaja mal con una función que depende de que haya otras personas conectadas.
- **Racha grupal:** solo es útil con racha real. Hoy la racha está fija (15 días); el grupo decidió en el módulo 3 (P9) que **la racha real y sus reglas se definen en el módulo 4**, así que esa definición forma parte de este módulo.

## 6. Qué sería funcional y qué visual

| Parte | Posibilidad realista | Condición |
|---|---|---|
| Amigos con solicitudes (sin tiempo real) | **Funcional** | Alias público, tabla pública, función de búsqueda; ver sección 3 |
| Perfil de amigo y racha grupal | **Funcional** | Racha y contadores reales (sus reglas se definen en este módulo 4, según P9 del módulo 3) y "ocultar progreso" real |
| Batallas por turnos contra un **amigo** (turno cada cierto tiempo, no en vivo) | Posible | Reglas del servidor |
| Partida rápida con emparejamiento y arena en vivo | **Difícil en plan gratuito** | Realtime, cola y anti-trampas |
| Ulti con terminal | Posible tras las batallas | Reutiliza el motor de terminal del módulo 3 |
| XP | **Visual** hasta definir qué es y dónde se usa | No existe en ningún documento |

Recomendación (para discusión del grupo, no decidida): empezar por **Amigos + Perfil de amigo + Racha grupal** como funcional, y dejar **Batallas** como visual o por turnos asíncronos, porque son el único bloque que exige Realtime y emparejamiento.

## 7. Lo que debe quedar resuelto antes de escribir una spec del módulo 4

PREGUNTAS ABIERTAS para el grupo:

1. ¿Alias público? Reglas de unicidad, largo y cambio.
2. ¿Se muestra el nombre real, un alias o ambos?
3. ¿Habrá "Sugerencias"? ¿Con qué criterio, sin exponer datos?
4. ¿"Ocultar mi progreso público" pasa a guardarse en el servidor?
5. ¿Avatares visibles para amigos (Storage)?
6. ¿Batallas en vivo, asíncronas o solo visuales?
7. ¿Qué es la XP y qué hace?
8. ¿Qué cuenta como "día entrenado" para la racha real y qué desbloquea cada insignia? (Pasaron del módulo 3 a este módulo 4 por decisión P9 del módulo 3.)
9. ¿Hace falta revisar normas por menores de edad o de tiendas de apps?
10. ¿Cuotas reales de Realtime y Edge Functions del plan gratuito vigente?
11. ¿Qué pasa con las notificaciones de lo social (retos, solicitudes)? En el módulo 2 solo existen recordatorios locales.
