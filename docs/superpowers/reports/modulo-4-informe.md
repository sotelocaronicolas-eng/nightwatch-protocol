# Informe de cierre del módulo 4 (Social)

Rama `modulo-4`, versión **2.2.0** (`versionCode` 5). Cierre del 23 de octubre de 2026. Spec:
[`specs/2026-10-21-modulo-4-design.md`](../specs/2026-10-21-modulo-4-design.md). Revisión de código:
[`reports/revision-codigo-modulo-4.md`](revision-codigo-modulo-4.md). Guía de Supabase:
[`supabase-configuracion.md`](../supabase-configuracion.md) (Parte F). Descripción del Pull Request: [`modulo-4-pr.md`](modulo-4-pr.md).

## 1. Alcance entregado

La pestaña **Social** deja de estar atenuada y pasa a ser activa. Todo lo que sigue funciona contra Supabase (`0008` a `0011` aplicados):

| Área | Qué hace la persona |
|---|---|
| **Perfil social** | Crea un nombre de usuario único (3 a 20 caracteres, no se puede cambiar) y recibe un código de amigo `NIGHT-XXXXXX`. Puede ocultar su progreso público |
| **Amigos** | Busca a una persona por nombre exacto o por código (sin listas ni sugerencias), envía y responde solicitudes (máximo 50 amigos, 20 solicitudes pendientes, 24 h de espera tras un rechazo), ve el perfil del amigo (racha, insignias, nivel, historial entre los dos) y lo elimina |
| **Racha en pareja** | Invita a un amigo a una racha compartida (vence a los 3 días, máximo 5 pendientes). Suma un día cuando los dos completan su ejercicio del día, hora de Colombia. Lista de parejas y detalle con la semana |
| **Batallas** | Partida rápida contra desconocidos de nivel parecido (el rango se amplía con la espera, a los 60 s se sugiere retar a un amigo) o reto a un amigo (2 minutos para aceptar). Arena por turnos con preguntas de 1 minuto, ulti con un reto de terminal (hasta 3 intentos, escudo del defensor), fin de batalla con XP, nivel de combatiente, temas a repasar, marcador y racha de batallas, y "Revancha" |
| **Fotos de perfil** | La foto del perfil se muestra en toda la parte Social (amigos, solicitudes, parejas, retos, arena y rival), en un bucket privado con claves aleatorias |
| **Reportar y bloquear** | Reportar a cualquier persona (foto, nombre, acoso, otro, con nota de hasta 200 caracteres); bloquear, desbloquear y la lista "Personas bloqueadas" (desde Amigos). El equipo puede ocultar una foto a mano |

Fuera del módulo: notificaciones push, ranking, una pantalla de moderación, la tabla de actividad por día para que repetir un ejercicio cuente en la racha (sería `0012`) y Realtime (se usa sondeo).

**Servidor:** `0008` (14 tablas, 5 funciones públicas y 20 internas), `0009` (72 preguntas, 12 retos de ulti y la curva de XP), `0010` (fotos) y `0011` (reportes y bloqueos).
**App:** `data/social` (modelos, parser, repositorios, sondeo, fotos), `data/supabase/Supabase*`, `ui/social` (13 pantallas nuevas y sus ViewModels), textos en `strings_social*.xml`.

## 2. Decisiones del grupo

Detalle completo en la sección "Decisiones del grupo" de la spec. Resumen:

- **Juego (D1 a D9, O1, O2, O10 a O13):** ulti con 3 aciertos seguidos; 1 minuto por pregunta; correcta = -20 HP, HP total 100; ulti de ataque y defensa de 2 minutos con hasta 3 intentos y el comando no se muestra
  (se sabe de memoria); 1 minuto de gracia por desconexión; rango de ±3 niveles que se amplía +1 cada 10 s; XP aparte del nivel de nivelación (100 XP por nivel, victoria +50, empate +25, derrota +10, tope nivel 20);
  empieza una persona al azar, 10 turnos por jugador; "X" de la arena pide confirmación y abandonar da la derrota. **El servidor decide todo** (D7); la app solo muestra la cuenta regresiva.
- **Cuentas y amistad (D10 a D12, O3, O6 a O9):** nombre de usuario único y fijo, código de amigo aparte; "Ocultar mi progreso público" oculta nivel, logros e islas; **se quitan las "Sugerencias"**; la pareja exige amistad aceptada.
- **Racha en pareja (D13 a D15, D19, O4, O5, O9):** solo de a parejas, varias por persona, hora de Colombia; con 6 lecciones y 6 labs **no pasa de 6 días** (aceptado); la tarjeta se llama "Racha en pareja".
- **Sin notificaciones (D16), tiempo real por sondeo (D18), pantallas nuevas con el estilo existente (D17), versión 2.2.0 (D20).**
- **Fotos (decisión del grupo, `0010`):** se muestra la foto de perfil en Social (reemplaza a O14, que decía "solo emojis"). Cualquier persona con sesión puede leer el bucket `social-avatars`; subir, reemplazar y borrar solo el archivo propio.
  Al borrar la foto la clave se cambia por una nueva. "Ocultar mi progreso" **no** oculta la foto.
- **Moderación (O15, reemplazada el 2026-10-22, `0011`):** se puede **reportar a cualquier persona** (búsqueda, solicitudes, perfil del amigo, rival de la arena y del fin de batalla, partida rápida incluida);
  **ningún número de reportes oculta una foto automáticamente** (solo el equipo, a mano, con `avatar_hidden`); **bloquear no termina una batalla en curso**, solo impide las nuevas; **no hay pantalla de moderación**
  (el equipo revisa `social_reports` en el Table Editor). Límite de 10 reportes por día.
- **R-05 aceptado:** todos los usuarios están en Colombia, así que la tira de la semana usa el reloj del teléfono para el "hoy" y no se escribió un script solo para eso.

**Decisiones técnicas de la implementación:** Reportar y Bloquear son **diálogos y no rutas** (una ruta que tape la arena detiene el sondeo y puede hacer perder la batalla a los 60 s);
las acciones nuevas viven dentro de `social_friends` para no sumar funciones públicas ni advertencias; en un servidor sin `0011` la app muestra «Función no disponible por ahora»; `block` y `unblock` comparten
30 acciones por hora.

## 3. Diferencias con el Figma

Lista completa (S1 a S16) en la sección 2 de la spec. Las que se ven:

- **"Racha grupal" pasa a "Racha en pareja"**; es una lista de parejas y el detalle muestra **una** persona en "hoy". El "12 días" de Figma no se alcanza (máximo 6).
- **Se quitan las "Sugerencias"** de Agregar amigos y de Solicitudes.
- Se muestra el **nombre de usuario**, no el nombre de la cuenta; los contadores del perfil llegan a 6 ("Ejercicios" = lecciones vistas, "Labs superados" = labs completados); las insignias son las 9 reales del módulo 3.
- Texto de la partida rápida sin "Te avisaremos...": la búsqueda solo avanza con Social abierto.
- **Pantallas que no existen en Figma:** espera de turno, temporizador de la arena, retos y respuesta al reto de ulti con escudo, Personas bloqueadas, y los diálogos de Reportar y Bloquear.
- **El comando de la ulti no se muestra** (Figma lo mostraba en una chapa). El marcador agrega "E" si hay empates.
- **Fotos reales** en lugar de los emojis de Figma (el emoji queda como respaldo).
- **Píldoras de 36 dp** con área táctil de 48 dp (R-04): se ven igual, pero cada fila con píldora ocupa 12 dp más de alto.

La comparación visual pantalla por pantalla contra Figma la hace el grupo en el teléfono (D17: "se ajustan al probarlas").

## 4. Seguridad

**Modelo.** La clave `publishable` viaja en el APK, así que la seguridad está en el servidor:
- **11 tablas de Social cerradas** (RLS activo, sin políticas y sin `GRANT`), más `social_reports` y `social_blocks` de `0011`. La app solo lee directamente su fila de `social_profile`, sus filas de `battle_results` y la curva de XP.
- Todo lo demás pasa por **funciones `SECURITY DEFINER`** con `search_path` vacío, `revoke` de `public` y `anon`, identidad **solo** desde `auth.uid()` (ninguna recibe un id de usuario) y acciones de una lista cerrada.
- **El servidor decide** aciertos, daño, turnos, plazos, rachas y XP con `clock_timestamp()`. Las preguntas se envían sin su respuesta y la app manda el número de turno (un turno viejo se rechaza).
- **No enumeración:** "no existe", "soy yo", "no es mi amigo" y "hay un bloqueo" devuelven la misma respuesta. Los límites de ritmo cuentan también los intentos fallidos (búsqueda 20 cada 10 min, invitaciones y retos 20 por hora, reportes 10 por día, fotos y bloqueos 30 por hora).
- **Fotos:** claves aleatorias (no ids de Auth), bucket privado, solo `image/jpeg` de máximo 256 KB, políticas por columna de `social_profile` (el cliente no puede escribir `avatar_key`, `avatar_version` ni `avatar_hidden`).
- La app nunca guarda ni muestra la excepción de la librería (su mensaje puede traer cabeceras con el token); el comando de la ulti no se muestra, no se guarda y no se registra.

**Security Advisor (estado final): 10 advertencias, 0 errores, 14 sugerencias.** Las 10 advertencias están aceptadas y documentadas en la guía de Supabase: las 4 anteriores al módulo
(`account_status` ×2, `verify_recovery_email_code` y *Leaked Password Protection*), S1 a S5 (`social_friends`, `social_pairs`, `battle_lobby`, `battle_sync`, `battle_play`) y S7 (`social_set_avatar`).
Las 14 sugerencias son "RLS Enabled No Policy" (`recovery_email_codes`, 11 tablas de `0008` y las 2 de `0011`), intencionales.

**Riesgos aceptados:**
- **Listado del bucket de fotos:** cualquier persona con sesión puede leer y *listar* `social-avatars` y ver las claves (uuid aleatorios que no se unen a ninguna cuenta fuera de las funciones de Social). Se obtiene una imagen sin nombre. Además, la CDN puede tardar en mostrar una foto reemplazada.
- **Farmeo de XP:** ganar por abandono o desconexión da +50 XP, así que una persona con dos cuentas podría subir de nivel. Sin ranking ni premios el impacto es bajo; se revisa si se agrega uno.
- **Reportar confirma existencia:** `report` distingue "no existe" de "existe" y no depende del bloqueo, así que quien conoce el nombre o el código de alguien que lo bloqueó puede comprobar que la cuenta existe (10 intentos por día). La búsqueda ya permitía comprobarlo antes del bloqueo.
- **Sin pantalla de moderación:** nadie recibe un aviso de un reporte, el equipo debe abrir el panel; los reportes repetidos del mismo par no se unifican; un nombre de usuario ofensivo no se puede cambiar (O3), solo se puede ocultar la foto o actuar sobre la cuenta desde el panel. Los nombres son texto libre.
- Ya aceptados antes del módulo: enumeración de cuentas por `account_status`, tokens de sesión sin cifrar (F-05), revocación de sesión con el access token vigente hasta ~1 h.
- Los comandos de la ulti son los de los laboratorios y viajan en el APK (los 3 intentos limitan la fuerza bruta); el orden de los aciertos del escudo es el de llegada al servidor.

## 5. Pruebas

| Qué | Resultado |
|---|---|
| **Pruebas unitarias** | **1334, 0 fallos** (951 al cerrar el módulo 3; 1294 en la revisión de código; +4 en la foto de Social y +36 en reportar y bloquear). `./gradlew assembleDebug testDebugUnitTest lintDebug` en verde |
| **Lint** | 0 errores, 45 advertencias (las mismas de antes del cierre; las del módulo 4 no añaden ninguna) |
| **Guion SQL de `0008`** (`supabase/verificacion/0008_escenarios.sql`) | **219 de 219**, 0 fallas (2026-10-21; con el banco real, tras `0009`) y sigue en 219 después de `0011` |
| **Guion SQL de `0011`** (`supabase/verificacion/0011_escenarios.sql`) | **187 de 187**, 0 fallas (2026-10-23). El guion necesita `set_config('storage.allow_delete_query', 'true', true)` para borrar sus archivos de prueba |
| **Security Advisor** | 10 advertencias, 0 errores, 14 sugerencias |
| **Prueba en teléfonos** | El grupo recorrió Social en **dos teléfonos** con `0008` a `0011` aplicados: todo funcional (2026-10-23) |

Qué cubren las unitarias: ViewModels con Turbine y `MainDispatcherRule` donde hay eventos, repositorios con *fakes*, parser y formato de las acciones (incluida `reportArg`), clasificación de errores
(`classifyNewActionFailure`), sondeos con tiempo virtual, cachés de fotos y una prueba de fronteras (`SocialBoundaryTest`: ninguna pantalla ni ViewModel importa Supabase o `BuildConfig`).

**No comprobado:** no hay pruebas de Compose de las pantallas de Social, ni pruebas automáticas contra el Supabase real (se verifican con los guiones SQL), ni consta una prueba específica con TalkBack
(R-02 y R-03 se corrigieron por razonamiento y compilan), ni la apariencia final de las áreas táctiles de 48 dp, de los diálogos y del gorro y la varita a 56 dp.

## 6. Limitaciones

- **Racha de pareja: máximo 6 días** (hay 6 islas; no coincide con los 12 de Figma). Repetir una lección o un lab ya registrado no suma un día.
- **Sin notificaciones push:** invitaciones, retos y batallas se ven al abrir Social; los interruptores de notificaciones de "Logros" y "Nuevos laboratorios" siguen sin efecto.
- **La batalla exige la app abierta:** el sondeo corre solo mientras la arena está visible; más de 60 s sin llamadas pierde la batalla. Un reto vale 2 minutos y una búsqueda de partida rápida avanza solo con Social abierto.
- **Plan gratuito de Supabase:** pausa el proyecto tras **7 días sin actividad** (la app y el login dejan de funcionar hasta *Restore project*; se restaura durante ~90 días).
- **Correos de Brevo:** plan gratuito con un límite de **300 correos al día** (confirmado en el panel de Brevo el 2026-10-23), compartido con la Edge Function; el remitente `@gmail.com` puede caer en spam.
- Sin modo sin conexión (una escritura sin internet falla como "sin conexión").
- La partida rápida empareja desconocidos y la moderación es manual (ver la sección 4).
- "Hoy" de la tira de la semana de la pareja viene del reloj del teléfono (R-05, aceptado).

## 7. Pendientes

**Hallazgos abiertos de la revisión de código** (los tres son de gravedad Baja y ninguno bloquea el Pull Request):
- **R-06:** los contadores `lastMineHandled` y `lastRivalHandled` se modifican dentro del lambda de `update` del `ArenaViewModel`; hoy no falla porque todo corre en el hilo principal.
- **R-14:** las 13 pantallas de Social no tienen `@Preview`.
- **R-15:** no hay pruebas de Compose de las pantallas de Social ni de la carga de fotos en la interfaz (F-03 y F-15 de la revisión anterior).

Estado de los 22 hallazgos: **11 corregidos, 8 aceptados (R-05, R-08, R-16, R-17, R-18, R-20, R-21, R-22) y 3 pendientes**.

**Otros pendientes:** pruebas de Compose; comparación visual con Figma y prueba con TalkBack; una pantalla de moderación o al menos un aviso al equipo cuando llegue un reporte; migrar los textos de los módulos 1 y 2 a `strings.xml` (F-09);
`0012` con la actividad por día si se quiere que repetir cuente en la racha; revisar la corrección del examen en el servidor si algún día hay ranking.

## 8. Guion de prueba manual

El grupo hizo una prueba en dos teléfonos y reporta todo funcional; este registro no detalla qué pasos exactos se recorrieron. El guion que sigue es el recorrido completo para repetirla (dos cuentas, A y B, cada una en su teléfono, con `0008` a `0011` aplicados):

1. **Alta:** A y B abren Social y crean su nombre de usuario (probar uno repetido, uno de 2 caracteres y uno con espacios). Copiar el código de amigo.
2. **Amistad:** A busca a B por nombre y por código, lo invita; B ve la solicitud en Social > Solicitudes y la acepta. Probar rechazar y volver a invitar (espera de 24 h), y cancelar una solicitud enviada.
3. **Perfil del amigo:** racha, "entrenó hoy", insignias y nivel; B activa "Ocultar mi progreso público" y A confirma que desaparecen nivel, logros e islas.
4. **Racha en pareja:** A invita a B; B acepta. Los dos completan el ejercicio del día; comprobar la tira de la semana, "en riesgo" y el estado "Ya completaste las 6 islas".
5. **Reto:** A reta a B; B ve el reto con su cuenta regresiva (2 min), lo acepta y entran a la arena. Probar rechazar, cancelar y dejar vencer.
6. **Batalla:** responder bien y mal, dejar vencer un turno, cargar la ulti con 3 aciertos, usar la ulti (atacante y defensor, con 3 intentos, con y sin escudo), rotar el teléfono en plena batalla (el reto o la cola no se cancelan) y salir con la "X" (pide confirmación y da la derrota).
7. **Desconexión:** cerrar la app de B más de 60 s en plena batalla: B pierde; si se cierran las dos, se cancela sin ganador.
8. **Partida rápida:** A y B buscan a la vez; comprobar la sugerencia de retar a un amigo a los 60 s y que "Revancha" solo aparece tras un reto a un amigo.
9. **Fin de batalla:** victoria, derrota, empate y cancelada; XP, nivel, temas a repasar, marcador y racha de batallas.
10. **Fotos:** A cambia su foto en Editar perfil; B la ve en amigos, solicitudes, pareja, retos y arena. A quita su foto (si falla el borrado debe verse el aviso propio) y vuelve a subir otra.
11. **Reportar:** desde el perfil del amigo, un resultado de búsqueda, una solicitud, el rival de la arena y el fin de batalla (también contra un desconocido de la partida rápida): elegir motivo, nota de 200 caracteres con su contador, enviar. Comprobar el aviso de límite al pasar de 10 en un día.
12. **Bloquear:** A bloquea a B desde el perfil: se acaban la amistad, la pareja y los retos pendientes; ninguno puede buscarse, invitarse ni retarse; la partida rápida no los empareja; **una batalla en curso sigue**. En Amigos > Personas bloqueadas, A lo desbloquea (la amistad no vuelve).
13. **Moderación (equipo):** en el panel, abrir `social_reports`; ocultar la foto de B (`avatar_hidden = true`) y comprobar que A y los demás ven el emoji y que B no puede subir otra; levantar la ocultación.
14. **Sin `0011` (opcional, en un proyecto de prueba):** Reportar, Bloquear y Personas bloqueadas muestran «Función no disponible por ahora» y el resto de Social funciona.
15. **Accesibilidad:** con TalkBack, comprobar que se anuncian el cambio de turno, los avisos de cada turno y que la cuenta regresiva no se lee cada segundo; que "Reportar", "Bloquear" y las píldoras se pueden activar.
