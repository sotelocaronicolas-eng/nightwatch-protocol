# Descripción del Pull Request del módulo 4

Lista para pegar en GitHub. Rama `modulo-4` → `main`, con **1 aprobación** de otro integrante y **"Create a merge commit"** (CLAUDE.md, sección 6).

---

## Título

Módulo 4: Social (amigos, racha en pareja, batallas, fotos, reportar y bloquear) (v2.2.0)

## Resumen

Activa la pestaña **Social**. La persona crea un nombre de usuario, agrega amigos (por nombre exacto o por código), comparte una **racha en pareja**, **batalla** contra amigos o desconocidos (preguntas por turnos y un reto de terminal "Ulti"),
gana XP y nivel de combatiente, ve la **foto de perfil** de los demás y puede **reportar y bloquear** personas. **El servidor decide todo el juego** (aciertos, daño, turnos, plazos, rachas y XP); la app solo muestra y sondea cada 2 a 3 segundos mientras la arena está visible.
Versión **2.2.0** (`versionCode` 5).

Los scripts `0008` a `0011` **ya están aplicados y verificados** en el proyecto de Supabase del equipo; el grupo probó la app en dos teléfonos y todo funciona.

## Cambios por área

**Base de datos (`supabase/`)**
- `0008_social.sql`: 14 tablas de Social (11 cerradas, sin políticas ni `GRANT`), 5 funciones públicas `SECURITY DEFINER` (`social_friends`, `social_pairs`, `battle_lobby`, `battle_sync`, `battle_play`) y 20 internas `sx_*`.
- `0009_banco_batallas.sql`: 72 preguntas, 12 retos de ulti y la curva de XP (nivel 1 a 20).
- `0010_fotos_social.sql`: `avatar_key` y `avatar_version`, `social_set_avatar` y el bucket privado `social-avatars` con 4 políticas.
- `0011_reportes_bloqueos.sql`: `social_reports`, `social_blocks`, `avatar_hidden`, acciones `report`, `block`, `unblock` y `blocks` dentro de `social_friends`, y `sx_is_blocked`.
- `supabase/verificacion/`: guiones de escenarios de `0008` y `0011` (no son migraciones).

**App (`app/`)**
- `data/social`: modelos, parser de respuestas, repositorios (interfaz + implementación Supabase + *fakes*), sondeo (`BattlePoller`, `QuickMatchPoller`, `ChallengesPoller`), cuenta regresiva con reloj monótono y caché de fotos en memoria y disco.
- `ui/social`: 13 pantallas nuevas (inicio de Social, Amigos, Agregar amigos, Solicitudes, Perfil del amigo, Racha en pareja y su detalle, Batallas, búsqueda rápida, arena con ulti, fin de batalla, Personas bloqueadas) y los diálogos de Reportar y Bloquear.
- Navegación nueva en `NightWatchDestinations.kt` / `NightWatchNavHost.kt`; textos en `strings_social*.xml`; Social deja de estar atenuada en el Home.
- Cambios en código existente: avatar (`AvatarRepository` sincroniza la foto con Social y avisa con su propio texto si el borrado falla), Privacidad (ocultar progreso), Editar perfil.

**Revisión de código (`reports/revision-codigo-modulo-4.md`)**: 22 hallazgos; 11 corregidos (entre ellos R-01, rotar el teléfono ya no cancela el reto, y R-02/R-03/R-04 de accesibilidad), 8 aceptados, 3 pendientes de gravedad Baja (R-06, R-14, R-15).

**Documentación**: spec del módulo, informe de cierre (`modulo-4-informe.md`), guía de Supabase (Parte F), `CLAUDE.md` y `README.md`.

## Pruebas

- **1334 pruebas unitarias, 0 fallos** (`./gradlew assembleDebug testDebugUnitTest lintDebug` en verde); **lint: 0 errores**, 45 advertencias (las mismas de antes).
- **Guion SQL de `0008`: 219 de 219. Guion SQL de `0011`: 187 de 187.** Security Advisor: **10 advertencias, 0 errores, 14 sugerencias** (todas documentadas como aceptadas).
- Probado a mano en **dos teléfonos** con `0008` a `0011` aplicados: todo funcional.
- **No hay** pruebas de Compose de las pantallas de Social ni pruebas automáticas contra el Supabase real; no consta una prueba con TalkBack.

## Scripts SQL: orden si alguien clona de cero

El proyecto de Supabase del equipo **ya los tiene**. Un proyecto nuevo necesita **todos, en orden numérico, una sola vez**, pegados completos en el *SQL Editor* (con "Automatically expose new tables" desactivado y "Automatic RLS" activado):

1. `0001_esquema_inicial.sql`
2. `0002_minimo_privilegio.sql`
3. `0003_revoca_rls_auto_enable.sql`
4. `0004_storage_avatares.sql`
5. `0005_estado_de_cuenta.sql`
6. `0006_verificacion_correo_recuperacion.sql` (y desplegar la Edge Function `send-recovery-email-code`, ver su `README.md`)
7. `0007_progreso_aprendizaje.sql`
8. `0008_social.sql`
9. `0009_banco_batallas.sql` (**después** de `0008`; sin él una batalla no puede empezar: `bank_empty`)
10. `0010_fotos_social.sql`
11. `0011_reportes_bloqueos.sql`

Después: revisar el Security Advisor (debe dar 10 advertencias y 0 errores), y opcionalmente correr `supabase/verificacion/0008_escenarios.sql` (219 de 219) y luego `0011_escenarios.sql` (187 de 187) con dos cuentas de prueba. Los ajustes del panel (SMTP de Brevo, plantillas, proveedor Google, etc.) están en `docs/superpowers/supabase-configuracion.md`.
**Instalar la app 2.2.0 después de aplicar los scripts**: sin `0008` y `0009` Social falla al cargar; sin `0010` la app muestra el emoji; sin `0011` Reportar y Bloquear muestran «Función no disponible por ahora».
Las migraciones ya aplicadas **no se editan**; el siguiente número libre es `0012`.

## Riesgos

- **Moderación manual:** no hay pantalla de moderación; el equipo revisa `social_reports` en el panel y oculta una foto con `avatar_hidden`. Nadie recibe aviso de un reporte. Los nombres de usuario son texto libre y no se pueden cambiar.
- **Fotos:** cualquier persona con sesión puede leer y listar el bucket `social-avatars` (claves aleatorias, sin ids de Auth); la CDN puede tardar en mostrar una foto reemplazada.
- **XP:** ganar por abandono da +50 XP; con dos cuentas se podría farmear XP (sin ranking, impacto bajo).
- **Reportar confirma existencia** de una cuenta aunque haya bloqueado a quien reporta (10 intentos por día).
- **Plan gratuito:** Supabase pausa el proyecto a los 7 días sin actividad; los correos de Brevo tienen un límite de 300 al día (confirmado en el panel de Brevo el 2026-10-23).
- **Sondeo:** una batalla se pierde si pasan más de 60 s sin llamadas; por eso Reportar y Bloquear son diálogos y no pantallas nuevas.
- **Cambia lo que se ve:** las píldoras de Social ocupan 12 dp más de alto (área táctil de 48 dp) y el aspecto final de los diálogos y de las fotos no se ha comparado con Figma.

## Checklist para el revisor

- [ ] La rama contiene `origin/main` (el merge ya está al día; no hay conflictos).
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` en verde en tu computadora (1334 pruebas, lint sin errores).
- [ ] Ninguna pantalla ni ViewModel importa Supabase ni `BuildConfig` (lo vigila `SocialBoundaryTest`).
- [ ] Ninguna llamada envía un `user_id`; la identidad sale siempre de la sesión.
- [ ] `git diff main...HEAD` sin secretos: nada de `local.properties`, claves, URLs de proyectos ni correos personales.
- [ ] Los scripts `0008` a `0011` coinciden con lo que está aplicado (no se editaron después de ejecutarse) y cada función lleva `search_path = ''` y `revoke` de `public` y `anon`.
- [ ] El Security Advisor muestra 10 advertencias, 0 errores y 14 sugerencias, todas documentadas en "Advertencias aceptadas" de `supabase-configuracion.md`.
- [ ] `versionName` 2.2.0 y `versionCode` 5 en `app/build.gradle.kts`.
- [ ] Probar en un teléfono: crear usuario, agregar un amigo, una batalla corta, rotar el teléfono en la arena, reportar y bloquear (y desbloquear) a la otra persona.
- [ ] `CLAUDE.md`, `README.md` y la guía de Supabase describen el módulo 4 y los scripts `0008` a `0011` como aplicados.
- [ ] Merge con "Create a merge commit" y 1 aprobación; **sin** push directo a `main`.
