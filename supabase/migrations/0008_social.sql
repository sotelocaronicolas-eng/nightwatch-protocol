-- =====================================================================================================
-- NightWatch Protocol - Script 0008: Social (amigos, rachas de pareja y batallas)
--
-- BORRADOR sin aplicar. NO se ejecuta desde la app ni desde el repositorio: el equipo lo revisa y, si lo
-- aprueba, lo pega a mano en el SQL Editor, DESPUÉS de 0001 a 0007 y una sola vez. Mientras tenga esta
-- línea de "BORRADOR" puede editarse; una vez aplicado deja de editarse y cualquier cambio va en 0010 o
-- posterior (el 0009 está reservado para el banco de preguntas, los retos de ulti y la curva de XP).
--
-- Fuente: docs/superpowers/specs/2026-10-21-modulo-4-design.md (secciones 3 a 8 y 12). Todo lo que aquí
-- aparece sale de ahí y de las decisiones del grupo (D1 a D20, O1 a O15).
--
-- QUÉ INCLUYE
--   * 14 tablas, todas con RLS activo. Solo 3 tienen acceso desde la app (social_profile, battle_results
--     y fighter_level_curve); las otras 11 son CERRADAS: sin políticas y sin GRANT para anon ni authenticated
--     (el mismo patrón de recovery_email_codes en 0006).
--   * 5 funciones SECURITY DEFINER que la app puede llamar (social_friends, social_pairs, battle_lobby,
--     battle_sync y battle_play) y 1 función auxiliar SECURITY INVOKER (social_new_code). Antes de agrupar,
--     la spec listaba 32 funciones públicas: cada grupo recibe ahora una "acción" como parámetro.
--   * 20 funciones internas (prefijo sx_), sin EXECUTE para nadie de la app: solo las llaman las 5 de arriba.
--
-- QUÉ NO INCLUYE (a propósito)
--   * El banco de preguntas (72), los retos de ulti (12) y la curva de XP (nivel 1 a 20): van en 0009.
--     Mientras 0009 no esté aplicada, las tablas battle_questions, ulti_challenges y fighter_level_curve
--     están vacías: una batalla no puede empezar (error 'bank_empty') y todos son nivel 1.
--   * Nada de tiempo real (D18): ni publicación de Realtime ni políticas sobre realtime.messages. La app
--     hace sondeo (battle_sync, battle_lobby, social_friends). Si luego se adopta realtime-kt, va en un script nuevo.
--   * Nada de pg_cron: los turnos vencidos y las desconexiones se resuelven de forma perezosa, al llegar la
--     siguiente acción o sondeo (sección 6).
--   * Nada que toque tablas de módulos anteriores. Este script solo CREA objetos nuevos. Las funciones
--     LEEN lesson_progress, lab_progress y exam_attempts (0007) para la racha y el perfil del amigo; no
--     escriben en ellas ni en placement, profile, devices ni recovery_email_codes. Todas las claves foráneas
--     apuntan a auth.users o a tablas de este mismo script.
--   * Moderación, reportar o bloquear (O15: fuera del módulo).
--
-- DECISIONES TÉCNICAS TOMADAS AL ESCRIBIR EL SCRIPT (ver el resumen final del commit y la spec, sección 3)
--   * Tiempos (1 minuto por pregunta, 2 minutos de ulti, 60 s de gracia, 2 s de tolerancia de red): se
--     calculan y validan SIEMPRE con clock_timestamp() de la base de datos, nunca con la hora del teléfono.
--   * La ulti gasta el turno. Quien pierde por abandono o desconexión recibe 0 XP (no hay XP por irse).
--   * Una amistad rechazada se conserva con status 'declined' solo para aplicar la espera de 24 horas (O7);
--     una pareja de racha rechazada, cancelada, terminada o vencida se BORRA (R12 de la spec).
--   * Los valores de XP (+50 victoria, +25 empate, +10 derrota) están dentro de sx_battle_finish. La curva
--     de niveles NO: está en fighter_level_curve (0009). Cambiar los valores = create or replace en un script nuevo.
--
-- Supuestos del proyecto Supabase (igual que en 0001 y 0007):
--   * "Automatically expose new tables" DESACTIVADO: cada tabla necesita GRANT explícito a authenticated.
--   * "Automatic RLS" ACTIVADO: aun así se hace enable row level security en cada tabla.
--   * La clave publishable viaja en el APK: la seguridad real es RLS, el cierre de las tablas y las funciones.
--   * Supabase concede EXECUTE por defecto a anon y authenticated sobre las funciones nuevas de public: por
--     eso cada función lleva su revoke explícito (sección 8).
--
-- Convenciones: (select auth.uid()) = user_id; GRANT solo a authenticated, nunca TRUNCATE, REFERENCES ni
-- TRIGGER (0002); privilegios por columna; todas las funciones con set search_path = '' y nombres
-- calificados (public., auth.); la persona que actúa sale SIEMPRE de auth.uid(), nunca de un parámetro.
--
-- ERRORES QUE DEVUELVEN LAS FUNCIONES (mensaje de la excepción; la app los traduce a tipos del dominio):
--   no_session, social_not_enabled, bad_action, bad_query, rate_limited, not_found, invalid_target,
--   not_friends, already_friends, request_exists, request_incoming, request_gone, cooldown, too_many_pending,
--   friends_limit, pair_exists, pair_gone, challenge_pending, challenge_gone, already_in_battle, rival_busy,
--   battle_over, not_your_turn, stale_turn, bad_option, ulti_not_charged, no_ulti, bad_command,
--   already_resolved, bank_empty.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 0. Función auxiliar del código de amigo (SECURITY INVOKER)
--    La usa el valor por defecto de social_profile.friend_code, que se evalúa con los permisos de quien
--    inserta: por eso authenticated necesita EXECUTE (sección 8). Solo devuelve texto aleatorio, no lee nada.
--    Formato (O6): "NIGHT-" más 6 caracteres de un alfabeto de 32 sin ambigüedades (sin 0, O, 1 ni I).
--    Si dos personas recibieran el mismo código, el índice único falla y la persona repite el registro
--    (probabilidad despreciable: 32^6 = más de mil millones de códigos).
-- -----------------------------------------------------------------------------------------------------

create function public.social_new_code()
returns text
language sql
volatile
set search_path = ''
as $$
  select 'NIGHT-' || string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::integer, 1), '')
  from generate_series(1, 6)
$$;

-- -----------------------------------------------------------------------------------------------------
-- 1. Tablas
-- -----------------------------------------------------------------------------------------------------

-- 1.1 Perfil social. Una fila por usuario: se crea al habilitar Social (D10). Es la ÚNICA tabla de Social
-- que el dueño inserta y actualiza directamente.
--   * username: único sin distinguir mayúsculas (índice sobre lower), 3 a 20 caracteres de letras, números
--     y _ (O6). No se puede cambiar después (O3): no hay permiso de UPDATE sobre esa columna.
--   * friend_code: el id único aparte que sirve de código de amigo (D10). Lo genera el servidor.
--   * hide_progress: "Ocultar mi progreso público" (D11). Pasa de la preferencia local al servidor.
create table public.social_profile (
  user_id       uuid        not null primary key references auth.users (id) on delete cascade,
  username      text        not null
                check (username ~ '^[A-Za-z0-9_]{3,20}$'),
  friend_code   text        not null default public.social_new_code()
                check (friend_code ~ '^NIGHT-[A-HJ-NP-Z2-9]{6}$'),
  hide_progress boolean     not null default false,
  created_at    timestamptz not null default now()
);

create unique index social_profile_username_key on public.social_profile (lower(username));
create unique index social_profile_friend_code_key on public.social_profile (friend_code);

-- 1.2 Amistades. Un par de personas = una fila, con user_low < user_high para que no haya dos filas por par.
-- CERRADA: la fila lleva los ids de Auth de las dos personas y el análisis previo prohíbe exponerlos; la app
-- solo lee y escribe por social_friends. 'declined' se conserva para la espera de 24 horas tras un rechazo (O7).
create table public.friendships (
  id           bigint      generated always as identity primary key,
  user_low     uuid        not null references auth.users (id) on delete cascade,
  user_high    uuid        not null references auth.users (id) on delete cascade,
  requested_by uuid        not null references auth.users (id) on delete cascade,
  status       text        not null default 'pending'
               check (status in ('pending', 'accepted', 'declined')),
  created_at   timestamptz not null default now(),
  responded_at timestamptz,
  constraint friendships_pair_order check (user_low < user_high),
  constraint friendships_requester_in_pair check (requested_by = user_low or requested_by = user_high),
  constraint friendships_pair_key unique (user_low, user_high)
);

create index friendships_user_high_idx on public.friendships (user_high);

-- 1.3 Parejas de racha (D13). Una pareja existe mientras está pendiente o activa; rechazada, cancelada,
-- terminada o vencida se borra. NO guarda el contador: la racha se calcula (sección 5) con la fecha en que
-- se completó cada isla (lesson_progress y lab_progress de 0007) y accepted_at. CERRADA.
create table public.pair_streaks (
  id          bigint      generated always as identity primary key,
  user_low    uuid        not null references auth.users (id) on delete cascade,
  user_high   uuid        not null references auth.users (id) on delete cascade,
  invited_by  uuid        not null references auth.users (id) on delete cascade,
  status      text        not null default 'pending'
              check (status in ('pending', 'active')),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default now() + interval '3 days',
  accepted_at timestamptz,
  constraint pair_streaks_pair_order check (user_low < user_high),
  constraint pair_streaks_inviter_in_pair check (invited_by = user_low or invited_by = user_high),
  constraint pair_streaks_pair_key unique (user_low, user_high),
  constraint pair_streaks_accepted_when_active check ((status = 'active') = (accepted_at is not null))
);

create index pair_streaks_user_high_idx on public.pair_streaks (user_high);

-- 1.4 Banco de preguntas de batalla (D9). Sin filas hasta 0009. CERRADA SIN NINGÚN PERMISO: las respuestas
-- correctas no salen del servidor (D7). Solo las funciones SECURITY DEFINER leen esta tabla.
-- tier: 1 = niveles de combatiente 1 a 7, 2 = 8 a 14, 3 = 15 a 20 (O12).
create table public.battle_questions (
  id       integer     generated by default as identity primary key,
  topic    text        not null
           check (topic in ('CONTRASENAS', 'PHISHING', 'MALWARE', 'INGENIERIA_SOCIAL', 'REDES_SEGURAS', 'FIREWALL')),
  tier     integer     not null check (tier between 1 and 3),
  question text        not null check (char_length(question) between 1 and 400),
  option_a text        not null check (char_length(option_a) between 1 and 200),
  option_b text        not null check (char_length(option_b) between 1 and 200),
  option_c text        not null check (char_length(option_c) between 1 and 200),
  option_d text        not null check (char_length(option_d) between 1 and 200),
  correct  char(1)     not null check (correct in ('A', 'B', 'C', 'D')),
  active   boolean     not null default true
);

-- 1.5 Retos de terminal de la ulti (D4, O10). Sin filas hasta 0009. CERRADA: expected_command nunca sale del
-- servidor y de objective_title solo sale el título del reto del propio jugador. Los comandos deben ser
-- EXACTAMENTE los de los laboratorios del módulo 3 (learning_labs.json).
create table public.ulti_challenges (
  id               integer     generated by default as identity primary key,
  kind             text        not null check (kind in ('attack', 'defense')),
  topic            text        not null
                   check (topic in ('CONTRASENAS', 'PHISHING', 'MALWARE', 'INGENIERIA_SOCIAL', 'REDES_SEGURAS', 'FIREWALL')),
  objective_title  text        not null check (char_length(objective_title) between 1 and 120),
  expected_command text        not null check (char_length(expected_command) between 1 and 200),
  active           boolean     not null default true
);

-- 1.6 Curva de XP (O1). Sin filas hasta 0009 (con una tabla vacía todos son nivel 1). Es dato de referencia
-- sin nada sensible: la app la lee para calcular el nivel de combatiente a partir del XP.
create table public.fighter_level_curve (
  level  integer not null primary key check (level between 1 and 20),
  min_xp integer not null check (min_xp >= 0)
);

-- 1.7 Batallas (D1 a D7). Una fila por batalla; la partida rápida y el reto a un amigo usan la misma tabla (D6).
-- CERRADA: lleva los ids de Auth de los dos jugadores. El teléfono solo recibe la vista personalizada de
-- battle_sync. Asientos: player1 es el asiento 1 y player2 el asiento 2.
create table public.battles (
  id                  uuid        primary key default gen_random_uuid(),
  mode                text        not null check (mode in ('quick', 'friend')),
  status              text        not null default 'active' check (status in ('active', 'finished', 'cancelled')),
  player1_id          uuid        not null references auth.users (id) on delete cascade,
  player2_id          uuid        not null references auth.users (id) on delete cascade,
  hp1                 integer     not null default 100 check (hp1 between 0 and 100),
  hp2                 integer     not null default 100 check (hp2 between 0 and 100),
  correct_streak1     integer     not null default 0 check (correct_streak1 between 0 and 3),
  correct_streak2     integer     not null default 0 check (correct_streak2 between 0 and 3),
  ulti_charged1       boolean     not null default false,
  ulti_charged2       boolean     not null default false,
  turn_seat           integer     not null check (turn_seat in (1, 2)),
  turn_number         integer     not null default 1,
  turn_phase          text        not null default 'question' check (turn_phase in ('question', 'ulti')),
  turn_deadline       timestamptz not null,
  current_question_id integer     references public.battle_questions (id) on delete set null,
  last_seen1          timestamptz not null default now(),
  last_seen2          timestamptz not null default now(),
  version             integer     not null default 1,
  winner_seat         integer     check (winner_seat in (1, 2)),
  end_reason          text        check (end_reason in ('hp_zero', 'turn_limit', 'forfeit_disconnect', 'abandon', 'both_disconnected')),
  created_at          timestamptz not null default now(),
  finished_at         timestamptz,
  constraint battles_distinct_players check (player1_id <> player2_id)
);

create index battles_player1_idx on public.battles (player1_id);
create index battles_player2_idx on public.battles (player2_id);

-- 1.8 Batalla activa por jugador (O2 y D6): la clave primaria impide que una persona esté en dos batallas
-- a la vez. Se inserta al crear la batalla y se borra al cerrarla. CERRADA.
create table public.battle_active (
  user_id   uuid not null primary key references auth.users (id) on delete cascade,
  battle_id uuid not null references public.battles (id) on delete cascade
);

-- 1.9 Cola de la partida rápida (D6). Una fila por persona que busca oponente. CERRADA.
create table public.matchmaking_queue (
  user_id       uuid        not null primary key references auth.users (id) on delete cascade,
  fighter_level integer     not null check (fighter_level between 1 and 20),
  joined_at     timestamptz not null default now(),
  last_seen_at  timestamptz not null default now(),
  battle_id     uuid        references public.battles (id) on delete set null
);

-- 1.10 Retos a un amigo (D6, D16). Duran 2 minutos desde que se envían. CERRADA.
create table public.battle_challenges (
  id            bigint      generated always as identity primary key,
  challenger_id uuid        not null references auth.users (id) on delete cascade,
  challenged_id uuid        not null references auth.users (id) on delete cascade,
  created_at    timestamptz not null default now(),
  expires_at    timestamptz not null default now() + interval '2 minutes',
  status        text        not null default 'pending'
                check (status in ('pending', 'accepted', 'declined', 'cancelled', 'expired')),
  battle_id     uuid        references public.battles (id) on delete set null,
  constraint battle_challenges_distinct check (challenger_id <> challenged_id)
);

create unique index battle_challenges_pending_key
  on public.battle_challenges (challenger_id, challenged_id) where status = 'pending';
create index battle_challenges_challenged_idx on public.battle_challenges (challenged_id);
create index battle_challenges_challenger_idx on public.battle_challenges (challenger_id);

-- 1.11 Bitácora de turnos. Una fila por turno resuelto. Sirve para no repetir preguntas dentro de una batalla,
-- para el "Repasa X y Y" de la derrota y como registro. 'ulti' es un turno gastado en una ulti. CERRADA.
create table public.battle_turns (
  battle_id     uuid        not null references public.battles (id) on delete cascade,
  turn_number   integer     not null,
  seat          integer     not null check (seat in (1, 2)),
  question_id   integer     references public.battle_questions (id) on delete set null,
  outcome       text        not null check (outcome in ('correct', 'incorrect', 'timeout', 'ulti')),
  chosen_option char(1)     check (chosen_option in ('A', 'B', 'C', 'D')),
  damage        integer     not null default 0 check (damage >= 0),
  created_at    timestamptz not null default now(),
  primary key (battle_id, turn_number)
);

-- 1.12 Ultis (D4, O10). Una fila por ulti lanzada. Cada jugador tiene hasta 3 intentos dentro de los 2 minutos;
-- 'attacker_at' y 'defender_at' son las horas del servidor del ACIERTO (para decidir el escudo). CERRADA.
create table public.battle_ultis (
  id                   bigint      generated always as identity primary key,
  battle_id            uuid        not null references public.battles (id) on delete cascade,
  turn_number          integer     not null,
  attacker_seat        integer     not null check (attacker_seat in (1, 2)),
  attack_challenge_id  integer     not null references public.ulti_challenges (id),
  defense_challenge_id integer     not null references public.ulti_challenges (id),
  started_at           timestamptz not null,
  deadline             timestamptz not null,
  attacker_attempts    integer     not null default 0 check (attacker_attempts between 0 and 3),
  attacker_result      text        not null default 'pending' check (attacker_result in ('pending', 'solved', 'failed')),
  attacker_at          timestamptz,
  defender_attempts    integer     not null default 0 check (defender_attempts between 0 and 3),
  defender_result      text        not null default 'pending' check (defender_result in ('pending', 'solved', 'failed')),
  defender_at          timestamptz,
  shield               boolean     not null default false,
  damage               integer     not null default 0 check (damage >= 0),
  resolved_at          timestamptz,
  constraint battle_ultis_turn_key unique (battle_id, turn_number)
);

-- 1.13 Resultado de cada batalla, una fila por jugador (D8). De aquí salen el XP, el marcador, el historial y
-- la racha de batallas. opponent_username guarda el nombre del rival al cerrar la batalla (el nombre no se
-- puede cambiar, O3), así el historial se lee sin exponer el id del rival. El dueño LEE sus filas; nadie las escribe
-- desde la app (solo sx_battle_finish).
create table public.battle_results (
  battle_id         uuid        not null references public.battles (id) on delete cascade,
  user_id           uuid        not null references auth.users (id) on delete cascade,
  result            text        not null check (result in ('win', 'loss', 'draw')),
  xp_awarded        integer     not null default 0 check (xp_awarded >= 0),
  hp_left           integer     not null check (hp_left between 0 and 100),
  opponent_username text        not null,
  finished_at       timestamptz not null default now(),
  primary key (battle_id, user_id)
);

create index battle_results_user_finished_idx on public.battle_results (user_id, finished_at desc);

-- 1.14 Límite de ritmo (búsqueda de usuarios e invitaciones). CERRADA.
create table public.social_rate_limit (
  user_id      uuid        not null references auth.users (id) on delete cascade,
  action       text        not null,
  window_start timestamptz not null default now(),
  count        integer     not null default 0,
  primary key (user_id, action)
);

-- -----------------------------------------------------------------------------------------------------
-- 2. Row Level Security: activo en las 14 tablas
-- -----------------------------------------------------------------------------------------------------

alter table public.social_profile      enable row level security;
alter table public.friendships         enable row level security;
alter table public.pair_streaks        enable row level security;
alter table public.battle_questions    enable row level security;
alter table public.ulti_challenges     enable row level security;
alter table public.fighter_level_curve enable row level security;
alter table public.battles             enable row level security;
alter table public.battle_active       enable row level security;
alter table public.matchmaking_queue   enable row level security;
alter table public.battle_challenges   enable row level security;
alter table public.battle_turns        enable row level security;
alter table public.battle_ultis        enable row level security;
alter table public.battle_results      enable row level security;
alter table public.social_rate_limit   enable row level security;

-- -----------------------------------------------------------------------------------------------------
-- 3. Privilegios (GRANT) - solo authenticated, mínimo privilegio por columna
--    Se parte de cero (revoke all a anon y authenticated, ANTES de cualquier grant) y se concede solo lo
--    que la app necesita directamente. Sin TRUNCATE, REFERENCES ni TRIGGER (0002).
--      * social_profile: SELECT de su fila; INSERT (user_id, username) para habilitar Social; UPDATE solo
--        de hide_progress. username no se puede cambiar (O3) ni friend_code, created_at.
--      * battle_results: SELECT de sus filas (XP, marcador, historial). Sin INSERT, UPDATE ni DELETE.
--      * fighter_level_curve: SELECT (dato de referencia).
--      * Las otras 11 tablas quedan CERRADAS: RLS activo, sin políticas, sin GRANT.
-- -----------------------------------------------------------------------------------------------------

revoke all on public.social_profile      from anon;
revoke all on public.friendships         from anon;
revoke all on public.pair_streaks        from anon;
revoke all on public.battle_questions    from anon;
revoke all on public.ulti_challenges     from anon;
revoke all on public.fighter_level_curve from anon;
revoke all on public.battles             from anon;
revoke all on public.battle_active       from anon;
revoke all on public.matchmaking_queue   from anon;
revoke all on public.battle_challenges   from anon;
revoke all on public.battle_turns        from anon;
revoke all on public.battle_ultis        from anon;
revoke all on public.battle_results      from anon;
revoke all on public.social_rate_limit   from anon;

revoke all on public.social_profile      from authenticated;
revoke all on public.friendships         from authenticated;
revoke all on public.pair_streaks        from authenticated;
revoke all on public.battle_questions    from authenticated;
revoke all on public.ulti_challenges     from authenticated;
revoke all on public.fighter_level_curve from authenticated;
revoke all on public.battles             from authenticated;
revoke all on public.battle_active       from authenticated;
revoke all on public.matchmaking_queue   from authenticated;
revoke all on public.battle_challenges   from authenticated;
revoke all on public.battle_turns        from authenticated;
revoke all on public.battle_ultis        from authenticated;
revoke all on public.battle_results      from authenticated;
revoke all on public.social_rate_limit   from authenticated;

grant select on public.social_profile to authenticated;
grant insert (user_id, username) on public.social_profile to authenticated;
grant update (hide_progress) on public.social_profile to authenticated;

grant select on public.battle_results to authenticated;

grant select on public.fighter_level_curve to authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 4. Políticas RLS (solo las 3 tablas con acceso directo; solo el rol authenticated)
--    (select auth.uid()) en vez de auth.uid() a secas: Postgres lo evalúa una vez por consulta.
-- -----------------------------------------------------------------------------------------------------

-- social_profile: el dueño lee, crea y actualiza SOLO su fila. Nadie lee el perfil de otra persona por aquí:
-- eso lo hace social_friends, que devuelve únicamente nombre de usuario y código.
create policy social_profile_select_own on public.social_profile
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy social_profile_insert_own on public.social_profile
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy social_profile_update_own on public.social_profile
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

-- battle_results: cada jugador lee solo sus filas (no hay ningún id de otra persona en la fila).
create policy battle_results_select_own on public.battle_results
  for select to authenticated
  using ((select auth.uid()) = user_id);

-- fighter_level_curve: dato de referencia, no sensible. Lo lee cualquier persona con sesión.
create policy fighter_level_curve_select_all on public.fighter_level_curve
  for select to authenticated
  using (true);

-- -----------------------------------------------------------------------------------------------------
-- 5. Funciones internas (prefijo sx_). SECURITY INVOKER, sin EXECUTE para la app (sección 8): solo las llaman
--    las 5 funciones SECURITY DEFINER de la sección 7, que se ejecutan con los permisos del dueño del script.
--    Todas con search_path vacío y nombres calificados.
-- -----------------------------------------------------------------------------------------------------

-- 5.1 Nombre de usuario de una persona (nulo si no tiene Social).
create function public.sx_username(p_user uuid)
returns text
language sql
stable
set search_path = ''
as $$
  select username from public.social_profile where user_id = p_user
$$;

-- 5.2 Resuelve el destinatario de una invitación o de un reto: por código de amigo ("NIGHT-...") o por nombre
-- de usuario EXACTO (sin distinguir mayúsculas). Nunca busca por prefijo ni devuelve listas (sección 4.4 de la spec).
create function public.sx_resolve_target(p_arg text)
returns uuid
language sql
stable
set search_path = ''
as $$
  select sp.user_id
  from public.social_profile sp
  where case
          when upper(btrim(p_arg)) like 'NIGHT-%' then sp.friend_code = upper(btrim(p_arg))
          else lower(sp.username) = lower(btrim(p_arg))
        end
  limit 1
$$;

-- 5.3 ¿Son amigos aceptados?
create function public.sx_are_friends(p_a uuid, p_b uuid)
returns boolean
language sql
stable
set search_path = ''
as $$
  select exists (
    select 1 from public.friendships f
    where f.user_low = least(p_a, p_b) and f.user_high = greatest(p_a, p_b) and f.status = 'accepted'
  )
$$;

-- 5.4 Cuántos amigos aceptados tiene una persona (tope de 50, O7).
create function public.sx_friend_count(p_user uuid)
returns integer
language sql
stable
set search_path = ''
as $$
  select count(*)::integer from public.friendships f
  where f.status = 'accepted' and (f.user_low = p_user or f.user_high = p_user)
$$;

-- 5.5 Límite de ritmo. Cuenta las llamadas de una acción dentro de una ventana; si se pasa de p_max lanza
-- 'rate_limited'. Nota: al lanzar la excepción se deshace también el contador, que se queda en p_max hasta que
-- pase la ventana (sigue bloqueando).
create function public.sx_rate_limit(p_user uuid, p_action text, p_max integer, p_window interval)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_count integer;
begin
  insert into public.social_rate_limit (user_id, action, window_start, count)
  values (p_user, p_action, clock_timestamp(), 1)
  on conflict (user_id, action) do update
    set window_start = case when social_rate_limit.window_start <= clock_timestamp() - p_window
                            then clock_timestamp() else social_rate_limit.window_start end,
        count        = case when social_rate_limit.window_start <= clock_timestamp() - p_window
                            then 1 else social_rate_limit.count + 1 end
  returning count into v_count;

  if v_count > p_max then
    raise exception 'rate_limited';
  end if;
end;
$$;

-- 5.6 Racha PERSONAL de una persona vista desde el servidor (para la lista de amigos y el perfil del amigo).
-- Día de práctica = día de Colombia con una lección vista, un lab completado o un intento de examen (misma
-- definición que computeStreak del módulo 3, pero con hora de Colombia en vez de la zona del dispositivo).
-- Racha actual = días seguidos que terminan hoy o ayer. Devuelve {current, best, today}.
create function public.sx_personal_streak(p_user uuid)
returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  v_today   date := (now() at time zone 'America/Bogota')::date;
  v_current integer;
  v_best    integer;
  v_done    boolean;
begin
  with days as (
    select (l.completed_at at time zone 'America/Bogota')::date as d
    from public.lesson_progress l where l.user_id = p_user
    union
    select (b.completed_at at time zone 'America/Bogota')::date
    from public.lab_progress b where b.user_id = p_user
    union
    select (e.taken_at at time zone 'America/Bogota')::date
    from public.exam_attempts e where e.user_id = p_user
  ), numbered as (
    select d, d - (row_number() over (order by d))::integer as grp from days
  ), runs as (
    select count(*)::integer as len, max(d) as last_day from numbered group by grp
  )
  select coalesce((select max(len) from runs), 0),
         coalesce((select r.len from runs r where r.last_day >= v_today - 1 order by r.last_day desc limit 1), 0),
         exists (select 1 from days where d = v_today)
    into v_best, v_current, v_done;

  return jsonb_build_object('current', v_current, 'best', v_best, 'today', v_done);
end;
$$;

-- 5.7 Cuántas islas completó una persona (lección vista Y lab completado del mismo tema, regla P3 del módulo 3).
create function public.sx_islands_done(p_user uuid)
returns integer
language sql
stable
set search_path = ''
as $$
  select count(*)::integer
  from public.lesson_progress l
  join public.lab_progress b on b.user_id = l.user_id and b.lab = l.topic
  where l.user_id = p_user
$$;

-- 5.8 Racha de PAREJA (D13, D14; reglas R1 a R11 de la spec). Se calcula, no se guarda.
--   * "Ejercicio del día" = una isla completada ese día; la fecha de completado de una isla es la MAYOR entre
--     la de su lección y la de su lab. Como esas filas no se repiten (0007), ningún ejercicio se puede repetir.
--   * Día = día de Colombia (America/Bogota). Cuentan los días >= p_since (el día de aceptación, O9).
--   * Día compartido = los DOS completaron una isla ese día. Racha actual = días compartidos seguidos que
--     terminan hoy o ayer. Nunca pasa de 6 porque cada persona solo tiene 6 islas (D14).
--   * Solo devuelve si cada persona hizo su ejercicio hoy y los días compartidos: NUNCA qué isla (R14).
-- state: 'active' (hoy ya hicieron los dos), 'at_risk' (ayer fue compartido y hoy falta alguien), 'none' (racha 0)
-- o 'route_complete' (alguna de las dos personas ya completó las 6 islas y no puede aportar más días, R9).
create function public.sx_pair_metrics(p_me uuid, p_other uuid, p_since date)
returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  v_today       date := (now() at time zone 'America/Bogota')::date;
  v_current     integer;
  v_best        integer;
  v_last        date;
  v_me_today    boolean;
  v_other_today boolean;
  v_days        jsonb;
  v_state       text;
begin
  with mine as (
    select (greatest(l.completed_at, b.completed_at) at time zone 'America/Bogota')::date as d
    from public.lesson_progress l
    join public.lab_progress b on b.user_id = l.user_id and b.lab = l.topic
    where l.user_id = p_me
  ), theirs as (
    select (greatest(l.completed_at, b.completed_at) at time zone 'America/Bogota')::date as d
    from public.lesson_progress l
    join public.lab_progress b on b.user_id = l.user_id and b.lab = l.topic
    where l.user_id = p_other
  ), shared as (
    select d from mine where d >= p_since
    intersect
    select d from theirs where d >= p_since
  ), numbered as (
    select d, d - (row_number() over (order by d))::integer as grp from shared
  ), runs as (
    select count(*)::integer as len, max(d) as last_day from numbered group by grp
  )
  select coalesce((select max(len) from runs), 0),
         coalesce((select r.len from runs r where r.last_day >= v_today - 1 order by r.last_day desc limit 1), 0),
         (select max(d) from shared),
         exists (select 1 from mine where d = v_today),
         exists (select 1 from theirs where d = v_today),
         coalesce((select jsonb_agg(d order by d) from shared), '[]'::jsonb)
    into v_best, v_current, v_last, v_me_today, v_other_today, v_days;

  if public.sx_islands_done(p_me) >= 6 or public.sx_islands_done(p_other) >= 6 then
    v_state := 'route_complete';
  elsif v_current >= 1 and v_last = v_today then
    v_state := 'active';
  elsif v_current >= 1 then
    v_state := 'at_risk';
  else
    v_state := 'none';
  end if;

  return jsonb_build_object(
    'current', v_current,
    'best', v_best,
    'me_today', v_me_today,
    'partner_today', v_other_today,
    'state', v_state,
    'shared_days', v_days
  );
end;
$$;

-- 5.9 XP total de una persona (suma de sus batallas) y nivel de combatiente (curva de 0009; vacía = nivel 1).
create function public.sx_xp(p_user uuid)
returns integer
language sql
stable
set search_path = ''
as $$
  select coalesce(sum(r.xp_awarded), 0)::integer from public.battle_results r where r.user_id = p_user
$$;

create function public.sx_level(p_xp integer)
returns integer
language sql
stable
set search_path = ''
as $$
  select coalesce(max(c.level), 1)::integer from public.fighter_level_curve c where c.min_xp <= p_xp
$$;

create function public.sx_user_level(p_user uuid)
returns integer
language sql
stable
set search_path = ''
as $$
  select public.sx_level(public.sx_xp(p_user))
$$;

-- 5.10 Normalización del comando de la ulti: igual que normalizeCommand del módulo 3 (LabTerminal.kt): se
-- ignoran los espacios de los extremos y se juntan los repetidos; solo la PRIMERA palabra no distingue
-- mayúsculas; los argumentos sí. Vacío si el texto está en blanco.
create function public.sx_normalize_command(p_text text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
           when w = '' then ''
           else lower(split_part(w, ' ', 1)) || substr(w, char_length(split_part(w, ' ', 1)) + 1)
         end
  from (
    select regexp_replace(regexp_replace(coalesce(p_text, ''), '^\s+|\s+$', '', 'g'), '\s+', ' ', 'g') as w
  ) s
$$;

-- 5.11 Elige la pregunta del turno (D9). Dificultad según el nivel de combatiente de QUIEN RESPONDE
-- (1 a 7 -> escalón 1, 8 a 14 -> 2, 15 a 20 -> 3), tema al azar entre los 6, sin repetir dentro de la batalla.
-- Si ya no quedan sin usar, repite antes que bloquear la batalla. Sin preguntas (0009 sin aplicar): 'bank_empty'.
create function public.sx_pick_question(p_battle uuid, p_user uuid)
returns integer
language plpgsql
set search_path = ''
as $$
declare
  v_level integer := public.sx_user_level(p_user);
  v_tier  integer := case when v_level <= 7 then 1 when v_level <= 14 then 2 else 3 end;
  v_topic text := (array['CONTRASENAS', 'PHISHING', 'MALWARE', 'INGENIERIA_SOCIAL', 'REDES_SEGURAS', 'FIREWALL'])[1 + floor(random() * 6)::integer];
  v_id    integer;
begin
  select q.id into v_id
  from public.battle_questions q
  where q.active and q.tier = v_tier
    and not exists (select 1 from public.battle_turns t where t.battle_id = p_battle and t.question_id = q.id)
  order by (q.topic = v_topic) desc, random()
  limit 1;

  if v_id is null then
    select q.id into v_id
    from public.battle_questions q
    where q.active
      and not exists (select 1 from public.battle_turns t where t.battle_id = p_battle and t.question_id = q.id)
    order by random()
    limit 1;
  end if;

  if v_id is null then
    select q.id into v_id from public.battle_questions q where q.active order by random() limit 1;
  end if;

  if v_id is null then
    raise exception 'bank_empty';
  end if;
  return v_id;
end;
$$;

-- 5.12 Crea una batalla (la ÚNICA función que inserta en battles: partida rápida y reto usan la misma, D6).
-- Empieza una persona elegida al azar por el servidor (O2). HP 100 para cada una. Si alguna ya está en una
-- batalla activa, la clave primaria de battle_active lo impide ('already_in_battle').
create function public.sx_battle_create(p_player1 uuid, p_player2 uuid, p_mode text)
returns uuid
language plpgsql
set search_path = ''
as $$
declare
  v_id    uuid := gen_random_uuid();
  v_seat  integer := case when random() < 0.5 then 1 else 2 end;
  v_first uuid;
begin
  v_first := case when v_seat = 1 then p_player1 else p_player2 end;

  insert into public.battles (id, mode, player1_id, player2_id, turn_seat, turn_deadline)
  values (v_id, p_mode, p_player1, p_player2, v_seat, clock_timestamp() + interval '60 seconds');

  update public.battles
     set current_question_id = public.sx_pick_question(v_id, v_first)
   where id = v_id;

  insert into public.battle_active (user_id, battle_id) values (p_player1, v_id), (p_player2, v_id);

  return v_id;
exception
  when unique_violation then
    raise exception 'already_in_battle';
end;
$$;

-- 5.13 Cierra una batalla (O1, O2). Una sola vez por batalla (si ya no está activa, no hace nada).
--   * p_winner: 1 o 2; nulo = empate (solo por límite de turnos con el mismo HP) o cancelada.
--   * p_reason 'both_disconnected': la batalla se CANCELA, sin ganador, sin XP y sin filas en battle_results.
--   * XP: victoria +50, empate +25, derrota +10; quien PIERDE por abandono o desconexión recibe 0.
create function public.sx_battle_finish(p_battle uuid, p_winner integer, p_reason text)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_b  public.battles%rowtype;
  v_r1 text;
  v_r2 text;
  v_x1 integer;
  v_x2 integer;
begin
  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_b.status <> 'active' then
    return;
  end if;

  delete from public.battle_active where battle_id = p_battle;

  if p_reason = 'both_disconnected' then
    update public.battles
       set status = 'cancelled', end_reason = p_reason, finished_at = clock_timestamp(), version = version + 1
     where id = p_battle;
    return;
  end if;

  v_r1 := case when p_winner is null then 'draw' when p_winner = 1 then 'win' else 'loss' end;
  v_r2 := case when p_winner is null then 'draw' when p_winner = 2 then 'win' else 'loss' end;
  v_x1 := case v_r1 when 'win' then 50 when 'draw' then 25
                    else case when p_reason in ('forfeit_disconnect', 'abandon') then 0 else 10 end end;
  v_x2 := case v_r2 when 'win' then 50 when 'draw' then 25
                    else case when p_reason in ('forfeit_disconnect', 'abandon') then 0 else 10 end end;

  insert into public.battle_results (battle_id, user_id, result, xp_awarded, hp_left, opponent_username, finished_at)
  values
    (p_battle, v_b.player1_id, v_r1, v_x1, v_b.hp1, public.sx_username(v_b.player2_id), clock_timestamp()),
    (p_battle, v_b.player2_id, v_r2, v_x2, v_b.hp2, public.sx_username(v_b.player1_id), clock_timestamp());

  update public.battles
     set status = 'finished', winner_seat = p_winner, end_reason = p_reason,
         finished_at = clock_timestamp(), version = version + 1
   where id = p_battle;
end;
$$;

-- 5.14 Pasa el turno (D2, D3). Si ya se jugaron 20 turnos (10 por jugador, O2), la batalla termina: gana quien
-- tenga más HP y con el mismo HP es empate. Si no, empieza el turno del otro con pregunta nueva y 60 s.
create function public.sx_advance_turn(p_battle uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_b      public.battles%rowtype;
  v_next   integer;
  v_user   uuid;
  v_winner integer;
begin
  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_b.status <> 'active' then
    return;
  end if;

  if v_b.turn_number >= 20 then
    v_winner := case when v_b.hp1 > v_b.hp2 then 1 when v_b.hp2 > v_b.hp1 then 2 else null end;
    perform public.sx_battle_finish(p_battle, v_winner, 'turn_limit');
    return;
  end if;

  v_next := case when v_b.turn_seat = 1 then 2 else 1 end;
  v_user := case when v_next = 1 then v_b.player1_id else v_b.player2_id end;

  update public.battles
     set turn_number         = turn_number + 1,
         turn_seat           = v_next,
         turn_phase          = 'question',
         turn_deadline       = clock_timestamp() + interval '60 seconds',
         current_question_id = public.sx_pick_question(p_battle, v_user),
         version             = version + 1
   where id = p_battle;
end;
$$;

-- 5.15 Resuelve la ulti abierta (D4, O10). La llaman quien acierta como atacante, quien agota los 3 intentos
-- como atacante, o la resolución perezosa cuando se acaba el plazo. Reglas:
--   * Atacante acertó: impacto de 45 HP; si el defensor acertó ANTES (defender_at estrictamente anterior a
--     attacker_at), escudo: la mitad redondeada hacia arriba = 23 (round(45 / 2) = 22,5 -> 23).
--   * Atacante no acertó (sin intentos o sin tiempo): no pasa nada, haya escudo o no.
--   * En los dos casos el atacante gasta la carga (ulti_charged = false, correct_streak = 0) y el turno pasa.
--   * Si el defensor sigue pendiente al resolver, queda como 'failed' (sin escudo).
create function public.sx_ulti_resolve(p_battle uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_b      public.battles%rowtype;
  v_u      public.battle_ultis%rowtype;
  v_damage integer := 0;
  v_shield boolean := false;
begin
  select * into v_b from public.battles where id = p_battle for update;
  select * into v_u from public.battle_ultis
   where battle_id = p_battle and resolved_at is null
   order by id desc limit 1 for update;
  if not found then
    return;
  end if;

  if v_u.attacker_result = 'solved' then
    v_shield := coalesce(v_u.defender_result = 'solved' and v_u.defender_at < v_u.attacker_at, false);
    v_damage := case when v_shield then 23 else 45 end;
  end if;

  update public.battle_ultis
     set shield          = v_shield,
         damage          = v_damage,
         defender_result = case when defender_result = 'pending' then 'failed' else defender_result end,
         resolved_at     = clock_timestamp()
   where id = v_u.id;

  update public.battles
     set hp1             = case when v_u.attacker_seat = 2 then greatest(0, hp1 - v_damage) else hp1 end,
         hp2             = case when v_u.attacker_seat = 1 then greatest(0, hp2 - v_damage) else hp2 end,
         ulti_charged1   = case when v_u.attacker_seat = 1 then false else ulti_charged1 end,
         ulti_charged2   = case when v_u.attacker_seat = 2 then false else ulti_charged2 end,
         correct_streak1 = case when v_u.attacker_seat = 1 then 0 else correct_streak1 end,
         correct_streak2 = case when v_u.attacker_seat = 2 then 0 else correct_streak2 end,
         version         = version + 1
   where id = p_battle;

  insert into public.battle_turns (battle_id, turn_number, seat, question_id, outcome, damage)
  values (p_battle, v_b.turn_number, v_u.attacker_seat, null, 'ulti', v_damage);

  select * into v_b from public.battles where id = p_battle;
  if (v_u.attacker_seat = 1 and v_b.hp2 = 0) or (v_u.attacker_seat = 2 and v_b.hp1 = 0) then
    perform public.sx_battle_finish(p_battle, v_u.attacker_seat, 'hp_zero');
  else
    perform public.sx_advance_turn(p_battle);
  end if;
end;
$$;

-- 5.16 Resolución PEREZOSA de plazos (sin pg_cron). La llama cada función de batalla y el lobby, con la fila
-- de la batalla bloqueada, ANTES de hacer su trabajo. Usa solo clock_timestamp() del servidor. Orden:
--   1. Desconexión (D5): un jugador sin latido hace más de 60 s está desconectado. Si lo está uno, pierde y
--      gana el otro; si lo están los dos, la batalla se cancela (O2).
--   2. Turno de pregunta vencido (D2): pasados 60 s del plazo (más 2 s de tolerancia de red) se pierde el
--      turno, sin daño, y se reinicia la racha de la ulti del que no respondió.
--   3. Ulti vencida (D4): pasados 2 minutos (más 2 s de tolerancia) sin acierto del atacante, la ulti falla.
create function public.sx_battle_resolve(p_battle uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_b    public.battles%rowtype;
  v_now  timestamptz := clock_timestamp();
  v_off1 boolean;
  v_off2 boolean;
  v_u    public.battle_ultis%rowtype;
begin
  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_b.status <> 'active' then
    return;
  end if;

  v_off1 := v_now - v_b.last_seen1 > interval '60 seconds';
  v_off2 := v_now - v_b.last_seen2 > interval '60 seconds';
  if v_off1 and v_off2 then
    perform public.sx_battle_finish(p_battle, null, 'both_disconnected');
    return;
  elsif v_off1 then
    perform public.sx_battle_finish(p_battle, 2, 'forfeit_disconnect');
    return;
  elsif v_off2 then
    perform public.sx_battle_finish(p_battle, 1, 'forfeit_disconnect');
    return;
  end if;

  if v_b.turn_phase = 'question' then
    if v_now > v_b.turn_deadline + interval '2 seconds' then
      insert into public.battle_turns (battle_id, turn_number, seat, question_id, outcome)
      values (p_battle, v_b.turn_number, v_b.turn_seat, v_b.current_question_id, 'timeout');

      update public.battles
         set correct_streak1 = case when v_b.turn_seat = 1 then 0 else correct_streak1 end,
             correct_streak2 = case when v_b.turn_seat = 2 then 0 else correct_streak2 end
       where id = p_battle;

      perform public.sx_advance_turn(p_battle);
    end if;
  else
    select * into v_u from public.battle_ultis
     where battle_id = p_battle and resolved_at is null
     order by id desc limit 1 for update;
    if found and v_now > v_u.deadline + interval '2 seconds' then
      update public.battle_ultis
         set attacker_result = case when attacker_result = 'pending' then 'failed' else attacker_result end,
             defender_result = case when defender_result = 'pending' then 'failed' else defender_result end
       where id = v_u.id;
      perform public.sx_ulti_resolve(p_battle);
    end if;
  end if;
end;
$$;

-- 5.17 Vista de la batalla PARA UNA PERSONA (D7). Devuelve solo lo que esa persona puede ver:
--   * la pregunta con sus 4 opciones, SIN la respuesta correcta, y solo si es su turno;
--   * en una ulti, SOLO el título de su propio reto (nunca el comando esperado ni el reto del rival) y
--     cuántos intentos le quedan;
--   * el último turno resuelto (para los avisos de correcto, incorrecto, tiempo agotado o ulti);
--   * al cerrarse la batalla, el resultado, el XP y los dos temas más fallados ("Repasa X y Y").
-- Nunca devuelve ids de Auth, el id de la pregunta no revela nada y la hora del servidor va en server_now
-- (la app calcula la cuenta regresiva con seconds_left y no con su propio reloj).
create function public.sx_battle_state(p_battle uuid, p_user uuid)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  v_b     public.battles%rowtype;
  v_now   timestamptz := clock_timestamp();
  v_seat  integer;
  v_rival uuid;
  v_out   jsonb;
  v_q     public.battle_questions%rowtype;
  v_u     public.battle_ultis%rowtype;
  v_title text;
  v_is_att boolean;
  v_last  public.battle_turns%rowtype;
  v_res   public.battle_results%rowtype;
  v_ulti  public.battle_ultis%rowtype;
begin
  select * into v_b from public.battles where id = p_battle;
  v_seat  := case when v_b.player1_id = p_user then 1 else 2 end;
  v_rival := case when v_seat = 1 then v_b.player2_id else v_b.player1_id end;

  v_out := jsonb_build_object(
    'battle_id', v_b.id,
    'mode', v_b.mode,
    'status', v_b.status,
    'version', v_b.version,
    'server_now', v_now,
    'my_seat', v_seat,
    'turn_number', v_b.turn_number,
    'me', jsonb_build_object(
      'hp', case when v_seat = 1 then v_b.hp1 else v_b.hp2 end,
      'correct_streak', case when v_seat = 1 then v_b.correct_streak1 else v_b.correct_streak2 end,
      'ulti_charged', case when v_seat = 1 then v_b.ulti_charged1 else v_b.ulti_charged2 end
    ),
    'rival', jsonb_build_object(
      'username', public.sx_username(v_rival),
      'hp', case when v_seat = 1 then v_b.hp2 else v_b.hp1 end,
      'seconds_since_seen', round(extract(epoch from (v_now - case when v_seat = 1 then v_b.last_seen2 else v_b.last_seen1 end))::numeric, 1)
    ),
    'end_reason', v_b.end_reason
  );

  if v_b.status = 'active' then
    v_out := v_out || jsonb_build_object(
      'turn', jsonb_build_object(
        'is_mine', v_b.turn_seat = v_seat,
        'phase', v_b.turn_phase,
        'seconds_left', greatest(0, round(extract(epoch from (v_b.turn_deadline - v_now))::numeric, 1))
      )
    );

    if v_b.turn_phase = 'question' and v_b.turn_seat = v_seat then
      select * into v_q from public.battle_questions where id = v_b.current_question_id;
      v_out := v_out || jsonb_build_object(
        'question', jsonb_build_object(
          'id', v_q.id,
          'text', v_q.question,
          'options', jsonb_build_object('A', v_q.option_a, 'B', v_q.option_b, 'C', v_q.option_c, 'D', v_q.option_d)
        )
      );
    end if;

    if v_b.turn_phase = 'ulti' then
      select * into v_u from public.battle_ultis
       where battle_id = p_battle and resolved_at is null
       order by id desc limit 1;
      if found then
        v_is_att := v_u.attacker_seat = v_seat;
        select c.objective_title into v_title
          from public.ulti_challenges c
         where c.id = case when v_is_att then v_u.attack_challenge_id else v_u.defense_challenge_id end;
        v_out := v_out || jsonb_build_object(
          'ulti', jsonb_build_object(
            'role', case when v_is_att then 'attacker' else 'defender' end,
            'objective', v_title,
            'attempts_left', 3 - case when v_is_att then v_u.attacker_attempts else v_u.defender_attempts end,
            'my_result', case when v_is_att then v_u.attacker_result else v_u.defender_result end
          )
        );
      end if;
    end if;
  end if;

  select * into v_last from public.battle_turns where battle_id = p_battle order by turn_number desc limit 1;
  if found then
    v_out := v_out || jsonb_build_object(
      'last_turn', jsonb_build_object(
        'number', v_last.turn_number,
        'by_me', v_last.seat = v_seat,
        'outcome', v_last.outcome,
        'damage', v_last.damage
      )
    );
    if v_last.outcome = 'ulti' then
      select * into v_ulti from public.battle_ultis
       where battle_id = p_battle and turn_number = v_last.turn_number;
      if found then
        v_out := v_out || jsonb_build_object(
          'last_ulti', jsonb_build_object(
            'shield', v_ulti.shield,
            'attacker_result', v_ulti.attacker_result,
            'defender_result', v_ulti.defender_result
          )
        );
      end if;
    end if;
  end if;

  if v_b.status <> 'active' then
    select * into v_res from public.battle_results where battle_id = p_battle and user_id = p_user;
    if found then
      v_out := v_out || jsonb_build_object(
        'result', jsonb_build_object(
          'outcome', v_res.result,
          'xp', v_res.xp_awarded,
          'hp_left', v_res.hp_left,
          'xp_total', public.sx_xp(p_user),
          'level', public.sx_user_level(p_user),
          'weak_topics', coalesce((
            select jsonb_agg(w.topic order by w.misses desc, w.topic)
            from (
              select q.topic, count(*) as misses
              from public.battle_turns t
              join public.battle_questions q on q.id = t.question_id
              where t.battle_id = p_battle and t.seat = v_seat and t.outcome in ('incorrect', 'timeout')
              group by q.topic
              order by count(*) desc, q.topic
              limit 2
            ) w
          ), '[]'::jsonb)
        )
      );
    end if;
  end if;

  return v_out;
end;
$$;

-- 5.18 Intenta emparejar a una persona de la cola (D6, O11). Rango: ±3 niveles que se amplía +1 cada 10 s
-- de espera (sin tope) del que lleva más tiempo de los dos. Toma las filas con skip locked para que dos
-- búsquedas simultáneas no emparejen a la misma persona dos veces. Devuelve el id de la batalla o nulo.
create function public.sx_try_match(p_me uuid)
returns uuid
language plpgsql
set search_path = ''
as $$
declare
  v_mine  public.matchmaking_queue%rowtype;
  v_other public.matchmaking_queue%rowtype;
  v_id    uuid;
begin
  select * into v_mine from public.matchmaking_queue where user_id = p_me and battle_id is null for update;
  if not found then
    return null;
  end if;

  select q.* into v_other
  from public.matchmaking_queue q
  where q.user_id <> p_me
    and q.battle_id is null
    and q.last_seen_at >= clock_timestamp() - interval '15 seconds'
    and not exists (select 1 from public.battle_active a where a.user_id = q.user_id)
    and abs(q.fighter_level - v_mine.fighter_level)
        <= 3 + floor(extract(epoch from (clock_timestamp() - least(q.joined_at, v_mine.joined_at))) / 10)
  order by q.joined_at
  limit 1
  for update of q skip locked;
  if not found then
    return null;
  end if;

  v_id := public.sx_battle_create(p_me, v_other.user_id, 'quick');
  update public.matchmaking_queue set battle_id = v_id where user_id in (p_me, v_other.user_id);
  return v_id;
end;
$$;

-- -----------------------------------------------------------------------------------------------------
-- 6. Resolución de plazos y desconexiones: es sx_battle_resolve (5.16), perezosa. No hay
--    pg_cron, ni Edge Function, ni Realtime: cada sondeo de battle_sync, cada acción de battle_play y cada
--    llamada de battle_lobby (que resuelve primero la batalla activa de quien llama) hacen avanzar el reloj.
-- -----------------------------------------------------------------------------------------------------

-- -----------------------------------------------------------------------------------------------------
-- 7. Funciones públicas (las ÚNICAS que la app llama): 5 SECURITY DEFINER, con set search_path = ''.
--    Necesitan SECURITY DEFINER porque las tablas de Social están cerradas (leen y escriben datos de varias
--    personas y leen el progreso de otra persona, que 0007 deja solo del dueño). Cada una:
--      * saca la identidad SOLO de auth.uid() (sin sesión: 'no_session'); no recibe ningún user_id;
--      * exige tener Social habilitado ('social_not_enabled');
--      * valida la acción contra una lista cerrada ('bad_action');
--      * devuelve jsonb con campos escogidos a mano (nunca select *, nunca ids de Auth, nunca respuestas).
-- -----------------------------------------------------------------------------------------------------

-- 7.1 AMIGOS. Acciones (p_arg = nombre de usuario o código de amigo, según la acción):
--   list               amigos aceptados con su racha personal y si ya entrenaron hoy
--   requests           solicitudes recibidas y enviadas
--   search             busca UNA persona por nombre exacto o por código (límite: 20 cada 10 minutos)
--   invite             envía una solicitud (límite: 20 por hora; máximo 20 pendientes y 50 amigos; 24 h tras un rechazo)
--   accept / decline   responde a la solicitud de la persona p_arg
--   cancel             cancela la solicitud que yo envié a p_arg
--   remove             elimina al amigo p_arg (termina también la pareja de racha y cancela sus retos pendientes)
--   profile            perfil de un amigo: racha, si entrenó hoy, progreso (si no lo oculta, D11) e historial entre los dos
-- No hay "Sugerencias" (D12): ninguna acción recomienda ni lista personas.
create function public.social_friends(p_action text, p_arg text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me     uuid := (select auth.uid());
  v_other  uuid;
  v_f      public.friendships%rowtype;
  v_sp     public.social_profile%rowtype;
  v_st     jsonb;
  v_rel    text;
  v_prog   jsonb;
  v_hist   jsonb;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_action is null or p_action not in ('list', 'requests', 'search', 'invite', 'accept', 'decline', 'cancel', 'remove', 'profile') then
    raise exception 'bad_action';
  end if;
  if not exists (select 1 from public.social_profile where user_id = v_me) then
    raise exception 'social_not_enabled';
  end if;

  if p_action = 'list' then
    return jsonb_build_object('friends', coalesce((
      select jsonb_agg(x.item order by x.item ->> 'username')
      from (
        select jsonb_build_object(
                 'username', sp.username,
                 'friend_code', sp.friend_code,
                 'streak', s.st -> 'current',
                 'trained_today', s.st -> 'today'
               ) as item
        from public.friendships f
        join public.social_profile sp
          on sp.user_id = case when f.user_low = v_me then f.user_high else f.user_low end
        cross join lateral (select public.sx_personal_streak(sp.user_id) as st) s
        where f.status = 'accepted' and v_me in (f.user_low, f.user_high)
      ) x
    ), '[]'::jsonb));

  elsif p_action = 'requests' then
    return jsonb_build_object(
      'received', coalesce((
        select jsonb_agg(jsonb_build_object('username', sp.username, 'friend_code', sp.friend_code) order by f.created_at)
        from public.friendships f
        join public.social_profile sp on sp.user_id = f.requested_by
        where f.status = 'pending' and f.requested_by <> v_me and v_me in (f.user_low, f.user_high)
      ), '[]'::jsonb),
      'sent', coalesce((
        select jsonb_agg(jsonb_build_object('username', sp.username, 'friend_code', sp.friend_code) order by f.created_at)
        from public.friendships f
        join public.social_profile sp
          on sp.user_id = case when f.user_low = v_me then f.user_high else f.user_low end
        where f.status = 'pending' and f.requested_by = v_me
      ), '[]'::jsonb)
    );

  elsif p_action = 'search' then
    if p_arg is null or char_length(btrim(p_arg)) < 3 or char_length(btrim(p_arg)) > 40 then
      raise exception 'bad_query';
    end if;
    perform public.sx_rate_limit(v_me, 'search', 20, interval '10 minutes');
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null or v_other = v_me then
      return jsonb_build_object('found', false);
    end if;
    select * into v_sp from public.social_profile where user_id = v_other;
    select * into v_f from public.friendships
     where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other);
    v_rel := case
               when not found then 'none'
               when v_f.status = 'accepted' then 'friends'
               when v_f.status = 'pending' and v_f.requested_by = v_me then 'sent'
               when v_f.status = 'pending' then 'received'
               else 'none'
             end;
    return jsonb_build_object('found', true, 'username', v_sp.username, 'friend_code', v_sp.friend_code, 'relation', v_rel);

  elsif p_action = 'invite' then
    perform public.sx_rate_limit(v_me, 'invite', 20, interval '1 hour');
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null then
      raise exception 'not_found';
    end if;
    if v_other = v_me then
      raise exception 'invalid_target';
    end if;
    select * into v_f from public.friendships
     where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other) for update;
    if found then
      if v_f.status = 'accepted' then
        raise exception 'already_friends';
      elsif v_f.status = 'pending' then
        if v_f.requested_by = v_me then
          raise exception 'request_exists';
        else
          raise exception 'request_incoming';
        end if;
      elsif v_f.responded_at > clock_timestamp() - interval '24 hours' then
        raise exception 'cooldown';
      end if;
    end if;
    if (select count(*) from public.friendships where status = 'pending' and requested_by = v_me) >= 20 then
      raise exception 'too_many_pending';
    end if;
    if public.sx_friend_count(v_me) >= 50 then
      raise exception 'friends_limit';
    end if;
    if v_f.id is not null then
      update public.friendships
         set status = 'pending', requested_by = v_me, created_at = clock_timestamp(), responded_at = null
       where id = v_f.id;
    else
      insert into public.friendships (user_low, user_high, requested_by)
      values (least(v_me, v_other), greatest(v_me, v_other), v_me);
    end if;
    return jsonb_build_object('status', 'sent');

  elsif p_action = 'accept' then
    v_other := public.sx_resolve_target(p_arg);
    select * into v_f from public.friendships
     where user_low = least(v_me, coalesce(v_other, v_me)) and user_high = greatest(v_me, coalesce(v_other, v_me))
       and status = 'pending' and requested_by = v_other
     for update;
    if v_other is null or not found then
      raise exception 'request_gone';
    end if;
    if public.sx_friend_count(v_me) >= 50 or public.sx_friend_count(v_other) >= 50 then
      raise exception 'friends_limit';
    end if;
    update public.friendships set status = 'accepted', responded_at = clock_timestamp() where id = v_f.id;
    return jsonb_build_object('status', 'accepted');

  elsif p_action = 'decline' then
    v_other := public.sx_resolve_target(p_arg);
    update public.friendships
       set status = 'declined', responded_at = clock_timestamp()
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and requested_by = v_other;
    if not found then
      raise exception 'request_gone';
    end if;
    return jsonb_build_object('status', 'declined');

  elsif p_action = 'cancel' then
    v_other := public.sx_resolve_target(p_arg);
    delete from public.friendships
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and requested_by = v_me;
    if not found then
      raise exception 'request_gone';
    end if;
    return jsonb_build_object('status', 'cancelled');

  elsif p_action = 'remove' then
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null or not public.sx_are_friends(v_me, v_other) then
      raise exception 'not_found';
    end if;
    delete from public.friendships
     where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other);
    delete from public.pair_streaks
     where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other);
    update public.battle_challenges
       set status = 'cancelled'
     where status = 'pending'
       and ((challenger_id = v_me and challenged_id = v_other) or (challenger_id = v_other and challenged_id = v_me));
    return jsonb_build_object('status', 'removed');

  else
    -- profile
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null or not public.sx_are_friends(v_me, v_other) then
      raise exception 'not_found';
    end if;
    select * into v_sp from public.social_profile where user_id = v_other;
    v_st := public.sx_personal_streak(v_other);

    -- D11: con "ocultar mi progreso público" no sale nivel, insignias ni islas. Los campos del bloque son los
    -- de BadgeFacts (módulo 3): la app evalúa las 9 insignias con ellos y no se duplican las reglas en SQL.
    if v_sp.hide_progress then
      v_prog := null;
    else
      v_prog := jsonb_build_object(
        'viewed_lessons', coalesce((select jsonb_agg(l.topic order by l.topic) from public.lesson_progress l where l.user_id = v_other), '[]'::jsonb),
        'completed_labs', coalesce((select jsonb_agg(b.lab order by b.lab) from public.lab_progress b where b.user_id = v_other), '[]'::jsonb),
        'exam_passed', exists (select 1 from public.exam_attempts e where e.user_id = v_other and e.passed),
        'exam_perfect', exists (select 1 from public.exam_attempts e where e.user_id = v_other and e.correct_count = 6),
        'best_streak', v_st -> 'best',
        'fighter_level', public.sx_user_level(v_other)
      );
    end if;

    -- Historial de batallas ENTRE LOS DOS (solo las dos personas lo ven, desde su propia fila de resultado).
    select coalesce(jsonb_agg(h.item order by h.finished_at desc), '[]'::jsonb) into v_hist
    from (
      select r1.finished_at,
             jsonb_build_object('result', r1.result, 'finished_at', r1.finished_at) as item
      from public.battle_results r1
      join public.battle_results r2 on r2.battle_id = r1.battle_id and r2.user_id = v_other
      where r1.user_id = v_me
      order by r1.finished_at desc
      limit 10
    ) h;

    return jsonb_build_object(
      'username', v_sp.username,
      'friend_code', v_sp.friend_code,
      'streak', v_st -> 'current',
      'trained_today', v_st -> 'today',
      'hidden', v_sp.hide_progress,
      'progress', v_prog,
      'history', v_hist
    );
  end if;
end;
$$;

-- 7.2 PAREJAS DE RACHA (D13, D15). Acciones (p_arg = código o nombre de la OTRA persona):
--   overview           lista de parejas (activas y pendientes) con su racha, y top_current = la racha actual más alta (D15)
--   detail             lo mismo para una pareja, con los días compartidos
--   invite             invita a un AMIGO a una racha de pareja (O8; vale 3 días y máximo 5 pendientes enviadas, O4)
--   accept / decline   responde a la invitación de p_arg; aceptar fija el día de inicio (O9)
--   cancel             cancela la invitación que envié a p_arg
--   end                termina una pareja activa (la borra; no se conserva la racha, R12)
create function public.social_pairs(p_action text, p_arg text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me    uuid := (select auth.uid());
  v_other uuid;
  v_p     public.pair_streaks%rowtype;
  v_m     jsonb;
  v_items jsonb := '[]'::jsonb;
  v_top   integer := 0;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_action is null or p_action not in ('overview', 'detail', 'invite', 'accept', 'decline', 'cancel', 'end') then
    raise exception 'bad_action';
  end if;
  if not exists (select 1 from public.social_profile where user_id = v_me) then
    raise exception 'social_not_enabled';
  end if;

  -- Limpieza perezosa: una invitación sin responder vence a los 3 días y desaparece sin aviso (O4).
  delete from public.pair_streaks where status = 'pending' and expires_at <= clock_timestamp();

  if p_action in ('overview', 'detail') then
    if p_action = 'detail' then
      v_other := public.sx_resolve_target(p_arg);
      if v_other is null then
        raise exception 'pair_gone';
      end if;
    end if;

    for v_p in
      select * from public.pair_streaks ps
       where v_me in (ps.user_low, ps.user_high)
         and (p_action = 'overview' or v_other in (ps.user_low, ps.user_high))
       order by ps.created_at
    loop
      v_other := case when v_p.user_low = v_me then v_p.user_high else v_p.user_low end;
      if v_p.status = 'active' then
        v_m := public.sx_pair_metrics(v_me, v_other, (v_p.accepted_at at time zone 'America/Bogota')::date);
        v_top := greatest(v_top, (v_m ->> 'current')::integer);
      else
        v_m := null;
      end if;
      v_items := v_items || jsonb_build_array(jsonb_build_object(
        'username', public.sx_username(v_other),
        'friend_code', (select sp.friend_code from public.social_profile sp where sp.user_id = v_other),
        'status', case when v_p.status = 'active' then 'active'
                       when v_p.invited_by = v_me then 'pending_sent'
                       else 'pending_received' end,
        'expires_at', case when v_p.status = 'pending' then to_jsonb(v_p.expires_at) else null end,
        'metrics', v_m
      ));
    end loop;

    if p_action = 'detail' then
      if jsonb_array_length(v_items) = 0 then
        raise exception 'pair_gone';
      end if;
      return jsonb_build_object('pair', v_items -> 0);
    end if;
    return jsonb_build_object('pairs', v_items, 'top_current', v_top);

  elsif p_action = 'invite' then
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null or v_other = v_me then
      raise exception 'not_found';
    end if;
    if not public.sx_are_friends(v_me, v_other) then
      raise exception 'not_friends';
    end if;
    if exists (select 1 from public.pair_streaks
                where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)) then
      raise exception 'pair_exists';
    end if;
    if (select count(*) from public.pair_streaks where status = 'pending' and invited_by = v_me) >= 5 then
      raise exception 'too_many_pending';
    end if;
    insert into public.pair_streaks (user_low, user_high, invited_by, expires_at)
    values (least(v_me, v_other), greatest(v_me, v_other), v_me, clock_timestamp() + interval '3 days');
    return jsonb_build_object('status', 'sent');

  elsif p_action = 'accept' then
    v_other := public.sx_resolve_target(p_arg);
    update public.pair_streaks
       set status = 'active', accepted_at = clock_timestamp()
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and invited_by = v_other;
    if not found then
      raise exception 'pair_gone';
    end if;
    return jsonb_build_object('status', 'active');

  elsif p_action = 'decline' then
    v_other := public.sx_resolve_target(p_arg);
    delete from public.pair_streaks
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and invited_by = v_other;
    if not found then
      raise exception 'pair_gone';
    end if;
    return jsonb_build_object('status', 'declined');

  elsif p_action = 'cancel' then
    v_other := public.sx_resolve_target(p_arg);
    delete from public.pair_streaks
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and invited_by = v_me;
    if not found then
      raise exception 'pair_gone';
    end if;
    return jsonb_build_object('status', 'cancelled');

  else
    -- end
    v_other := public.sx_resolve_target(p_arg);
    delete from public.pair_streaks
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'active';
    if not found then
      raise exception 'pair_gone';
    end if;
    return jsonb_build_object('status', 'ended');
  end if;
end;
$$;

-- 7.3 LOBBY DE BATALLAS: partida rápida y retos a amigos (D6, D16). Acciones (p_arg = código del amigo):
--   quick_join / quick_poll / quick_cancel   entra a la cola, consulta (cada 2 a 3 s; a los 60 s devuelve
--                                            suggest_friend = true, D6) o sale de la cola
--   challenge_send                           reta a un AMIGO; el reto vale 2 minutos (D16)
--   challenge_accept / challenge_decline     responde al reto de p_arg (aceptar crea la batalla, la MISMA de la partida rápida)
--   challenge_cancel                         cancela mi reto pendiente
--   challenges                               retos recibidos, mi último reto enviado y la batalla activa si la hay
-- Antes de cada acción: limpieza perezosa de la cola y de los retos vencidos, y se resuelve primero la batalla
-- activa de quien llama (turnos vencidos y desconexiones), para que no se quede "ocupada" sin pg_cron.
create function public.battle_lobby(p_action text, p_arg text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me        uuid := (select auth.uid());
  v_other     uuid;
  v_my_battle uuid;
  v_q         public.matchmaking_queue%rowtype;
  v_ch        public.battle_challenges%rowtype;
  v_battle    uuid;
  v_wait      numeric;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_action is null or p_action not in ('quick_join', 'quick_poll', 'quick_cancel', 'challenge_send',
                                          'challenge_accept', 'challenge_decline', 'challenge_cancel', 'challenges') then
    raise exception 'bad_action';
  end if;
  if not exists (select 1 from public.social_profile where user_id = v_me) then
    raise exception 'social_not_enabled';
  end if;

  delete from public.matchmaking_queue
   where last_seen_at < clock_timestamp() - case when battle_id is null then interval '15 seconds' else interval '10 minutes' end;
  update public.battle_challenges
     set status = 'expired'
   where status = 'pending' and expires_at <= clock_timestamp();

  select battle_id into v_my_battle from public.battle_active where user_id = v_me;
  if v_my_battle is not null then
    perform public.sx_battle_resolve(v_my_battle);
    select battle_id into v_my_battle from public.battle_active where user_id = v_me;
  end if;

  if p_action = 'challenges' then
    return jsonb_build_object(
      'active_battle_id', v_my_battle,
      'received', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'username', sp.username,
                 'friend_code', sp.friend_code,
                 'seconds_left', floor(extract(epoch from (c.expires_at - clock_timestamp())))
               ) order by c.created_at)
        from public.battle_challenges c
        join public.social_profile sp on sp.user_id = c.challenger_id
        where c.challenged_id = v_me and c.status = 'pending'
      ), '[]'::jsonb),
      'sent', (
        select jsonb_build_object(
                 'username', sp.username,
                 'friend_code', sp.friend_code,
                 'status', c.status,
                 'seconds_left', greatest(0, floor(extract(epoch from (c.expires_at - clock_timestamp())))),
                 'battle_id', c.battle_id
               )
        from public.battle_challenges c
        join public.social_profile sp on sp.user_id = c.challenged_id
        where c.challenger_id = v_me and c.created_at > clock_timestamp() - interval '10 minutes'
        order by c.created_at desc
        limit 1
      )
    );

  elsif p_action = 'quick_join' then
    if v_my_battle is not null then
      return jsonb_build_object('status', 'in_battle', 'battle_id', v_my_battle);
    end if;
    delete from public.matchmaking_queue where user_id = v_me;
    insert into public.matchmaking_queue (user_id, fighter_level, joined_at, last_seen_at)
    values (v_me, public.sx_user_level(v_me), clock_timestamp(), clock_timestamp());
    v_battle := public.sx_try_match(v_me);
    if v_battle is not null then
      return jsonb_build_object('status', 'matched', 'battle_id', v_battle);
    end if;
    return jsonb_build_object('status', 'waiting', 'waited_seconds', 0, 'suggest_friend', false);

  elsif p_action = 'quick_poll' then
    if v_my_battle is not null then
      delete from public.matchmaking_queue where user_id = v_me;
      return jsonb_build_object('status', 'matched', 'battle_id', v_my_battle);
    end if;
    select * into v_q from public.matchmaking_queue where user_id = v_me;
    if not found then
      return jsonb_build_object('status', 'none');
    end if;
    if v_q.battle_id is not null then
      delete from public.matchmaking_queue where user_id = v_me;
      return jsonb_build_object('status', 'matched', 'battle_id', v_q.battle_id);
    end if;
    update public.matchmaking_queue set last_seen_at = clock_timestamp() where user_id = v_me;
    v_battle := public.sx_try_match(v_me);
    if v_battle is not null then
      return jsonb_build_object('status', 'matched', 'battle_id', v_battle);
    end if;
    v_wait := extract(epoch from (clock_timestamp() - v_q.joined_at));
    return jsonb_build_object('status', 'waiting', 'waited_seconds', floor(v_wait), 'suggest_friend', v_wait >= 60);

  elsif p_action = 'quick_cancel' then
    select * into v_q from public.matchmaking_queue where user_id = v_me;
    if found and v_q.battle_id is not null then
      delete from public.matchmaking_queue where user_id = v_me;
      return jsonb_build_object('status', 'matched', 'battle_id', v_q.battle_id);
    end if;
    delete from public.matchmaking_queue where user_id = v_me;
    return jsonb_build_object('status', 'cancelled');

  elsif p_action = 'challenge_send' then
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null or v_other = v_me then
      raise exception 'invalid_target';
    end if;
    if not public.sx_are_friends(v_me, v_other) then
      raise exception 'not_friends';
    end if;
    if v_my_battle is not null then
      raise exception 'already_in_battle';
    end if;
    if exists (select 1 from public.battle_active where user_id = v_other) then
      raise exception 'rival_busy';
    end if;
    if exists (select 1 from public.battle_challenges where challenger_id = v_me and status = 'pending') then
      raise exception 'challenge_pending';
    end if;
    insert into public.battle_challenges (challenger_id, challenged_id, created_at, expires_at)
    values (v_me, v_other, clock_timestamp(), clock_timestamp() + interval '2 minutes');
    return jsonb_build_object('status', 'sent', 'seconds_left', 120);

  elsif p_action = 'challenge_accept' then
    v_other := public.sx_resolve_target(p_arg);
    select * into v_ch from public.battle_challenges
     where challenger_id = v_other and challenged_id = v_me and status = 'pending'
     for update;
    if v_other is null or not found then
      raise exception 'challenge_gone';
    end if;
    if v_my_battle is not null then
      raise exception 'already_in_battle';
    end if;
    if exists (select 1 from public.battle_active where user_id = v_other) then
      raise exception 'rival_busy';
    end if;
    v_battle := public.sx_battle_create(v_other, v_me, 'friend');
    update public.battle_challenges set status = 'accepted', battle_id = v_battle where id = v_ch.id;
    delete from public.matchmaking_queue where user_id in (v_me, v_other);
    return jsonb_build_object('status', 'matched', 'battle_id', v_battle);

  elsif p_action = 'challenge_decline' then
    v_other := public.sx_resolve_target(p_arg);
    update public.battle_challenges
       set status = 'declined'
     where v_other is not null and challenger_id = v_other and challenged_id = v_me and status = 'pending';
    if not found then
      raise exception 'challenge_gone';
    end if;
    return jsonb_build_object('status', 'declined');

  else
    -- challenge_cancel
    update public.battle_challenges set status = 'cancelled' where challenger_id = v_me and status = 'pending';
    return jsonb_build_object('status', 'cancelled');
  end if;
end;
$$;

-- 7.4 SONDEO DE UNA BATALLA. Lo llama la app cada 2 a 3 s mientras hay una batalla abierta, y cuando su cuenta
-- regresiva llega a cero. Hace tres cosas, en este orden: (1) registra el latido de quien llama (D5);
-- (2) resuelve los plazos vencidos y las desconexiones (sx_battle_resolve); (3) devuelve la vista de esa
-- persona (sx_battle_state). Cualquier persona que no sea de la batalla recibe 'not_found'.
create function public.battle_sync(p_battle uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := (select auth.uid());
  v_b  public.battles%rowtype;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;

  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_me not in (v_b.player1_id, v_b.player2_id) then
    raise exception 'not_found';
  end if;

  if v_b.status = 'active' then
    update public.battles
       set last_seen1 = case when player1_id = v_me then clock_timestamp() else last_seen1 end,
           last_seen2 = case when player2_id = v_me then clock_timestamp() else last_seen2 end
     where id = p_battle;
    perform public.sx_battle_resolve(p_battle);
  end if;

  return public.sx_battle_state(p_battle, v_me);
end;
$$;

-- 7.5 ACCIONES DE UNA BATALLA (D1 a D5, O10). Acciones:
--   answer       p_turn = número de turno que la app dibujó, p_arg = 'A' a 'D'. Correcta: -20 HP al rival; incorrecta: sin daño;
--                en los dos casos pasa el turno. Un envío viejo ('stale_turn') o fuera de turno ('not_your_turn') se rechaza.
--   use_ulti     convierte el turno actual en una ulti (solo con la ulti cargada). "Guardar para después" no llama a nada:
--                es seguir respondiendo la pregunta del turno (la ulti sigue cargada).
--   ulti_submit  p_arg = comando escrito. Hasta 3 intentos por jugador dentro de los 2 minutos; uno incorrecto consume
--                un intento y no termina el reto. El primero en acertar gana la carrera (sx_ulti_resolve).
--   abandon      el que actúa pierde (O13; la app pide confirmación antes).
-- Antes de actuar se registra el latido y se resuelven los plazos vencidos; si por eso la batalla ya terminó, no se
-- aplica la acción y se devuelve el estado final. Siempre devuelve la vista actualizada de quien llama.
create function public.battle_play(p_battle uuid, p_action text, p_turn integer default null, p_arg text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me     uuid := (select auth.uid());
  v_b      public.battles%rowtype;
  v_seat   integer;
  v_q      public.battle_questions%rowtype;
  v_correct boolean;
  v_damage integer;
  v_u      public.battle_ultis%rowtype;
  v_ch     public.ulti_challenges%rowtype;
  v_att    integer;
  v_def    integer;
  v_t      timestamptz;
  v_is_att boolean;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_action is null or p_action not in ('answer', 'use_ulti', 'ulti_submit', 'abandon') then
    raise exception 'bad_action';
  end if;

  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_me not in (v_b.player1_id, v_b.player2_id) then
    raise exception 'not_found';
  end if;
  if v_b.status <> 'active' then
    raise exception 'battle_over';
  end if;
  v_seat := case when v_b.player1_id = v_me then 1 else 2 end;

  update public.battles
     set last_seen1 = case when v_seat = 1 then clock_timestamp() else last_seen1 end,
         last_seen2 = case when v_seat = 2 then clock_timestamp() else last_seen2 end
   where id = p_battle;
  perform public.sx_battle_resolve(p_battle);

  select * into v_b from public.battles where id = p_battle;
  if v_b.status <> 'active' then
    return public.sx_battle_state(p_battle, v_me);
  end if;

  if p_action = 'abandon' then
    perform public.sx_battle_finish(p_battle, case when v_seat = 1 then 2 else 1 end, 'abandon');

  elsif p_action = 'answer' then
    if v_b.turn_phase <> 'question' or v_b.turn_seat <> v_seat then
      raise exception 'not_your_turn';
    end if;
    if p_turn is distinct from v_b.turn_number then
      raise exception 'stale_turn';
    end if;
    if p_arg is null or upper(p_arg) not in ('A', 'B', 'C', 'D') then
      raise exception 'bad_option';
    end if;

    select * into v_q from public.battle_questions where id = v_b.current_question_id;
    v_correct := v_q.correct = upper(p_arg);
    v_damage  := case when v_correct then 20 else 0 end;

    insert into public.battle_turns (battle_id, turn_number, seat, question_id, outcome, chosen_option, damage)
    values (p_battle, v_b.turn_number, v_seat, v_q.id, case when v_correct then 'correct' else 'incorrect' end, upper(p_arg), v_damage);

    -- Racha de aciertos de la ulti (D1): 3 seguidos la cargan. Un fallo reinicia el contador, pero una ulti ya
    -- cargada SIGUE cargada (D1: responder una pregunta normal no la gasta).
    update public.battles
       set hp1             = case when v_seat = 2 then greatest(0, hp1 - v_damage) else hp1 end,
           hp2             = case when v_seat = 1 then greatest(0, hp2 - v_damage) else hp2 end,
           correct_streak1 = case when v_seat = 1 then (case when v_correct then least(3, correct_streak1 + 1) else 0 end) else correct_streak1 end,
           correct_streak2 = case when v_seat = 2 then (case when v_correct then least(3, correct_streak2 + 1) else 0 end) else correct_streak2 end,
           ulti_charged1   = case when v_seat = 1 and v_correct and correct_streak1 + 1 >= 3 then true else ulti_charged1 end,
           ulti_charged2   = case when v_seat = 2 and v_correct and correct_streak2 + 1 >= 3 then true else ulti_charged2 end,
           version         = version + 1
     where id = p_battle;

    select * into v_b from public.battles where id = p_battle;
    if (v_seat = 1 and v_b.hp2 = 0) or (v_seat = 2 and v_b.hp1 = 0) then
      perform public.sx_battle_finish(p_battle, v_seat, 'hp_zero');
    else
      perform public.sx_advance_turn(p_battle);
    end if;

  elsif p_action = 'use_ulti' then
    if v_b.turn_phase <> 'question' or v_b.turn_seat <> v_seat then
      raise exception 'not_your_turn';
    end if;
    if not (case when v_seat = 1 then v_b.ulti_charged1 else v_b.ulti_charged2 end) then
      raise exception 'ulti_not_charged';
    end if;

    select c.id into v_att from public.ulti_challenges c where c.kind = 'attack' and c.active order by random() limit 1;
    select c.id into v_def from public.ulti_challenges c where c.kind = 'defense' and c.active order by random() limit 1;
    if v_att is null or v_def is null then
      raise exception 'bank_empty';
    end if;

    v_t := clock_timestamp();
    insert into public.battle_ultis (battle_id, turn_number, attacker_seat, attack_challenge_id, defense_challenge_id, started_at, deadline)
    values (p_battle, v_b.turn_number, v_seat, v_att, v_def, v_t, v_t + interval '2 minutes');

    update public.battles
       set turn_phase = 'ulti', turn_deadline = v_t + interval '2 minutes', current_question_id = null, version = version + 1
     where id = p_battle;

  elsif p_action = 'ulti_submit' then
    if v_b.turn_phase <> 'ulti' then
      raise exception 'no_ulti';
    end if;
    select * into v_u from public.battle_ultis
     where battle_id = p_battle and resolved_at is null
     order by id desc limit 1 for update;
    if not found then
      raise exception 'no_ulti';
    end if;
    if p_arg is null or char_length(p_arg) > 200 then
      raise exception 'bad_command';
    end if;

    -- La hora del acierto se toma DESPUÉS de tener el candado de la batalla: así el orden de los aciertos
    -- (quién llegó primero, para el escudo) es el orden real de llegada al servidor.
    v_t := clock_timestamp();
    v_is_att := v_u.attacker_seat = v_seat;

    if v_is_att then
      if v_u.attacker_result <> 'pending' then
        raise exception 'already_resolved';
      end if;
      select * into v_ch from public.ulti_challenges where id = v_u.attack_challenge_id;
      if public.sx_normalize_command(p_arg) = public.sx_normalize_command(v_ch.expected_command) then
        update public.battle_ultis
           set attacker_attempts = attacker_attempts + 1, attacker_result = 'solved', attacker_at = v_t
         where id = v_u.id;
        perform public.sx_ulti_resolve(p_battle);
      elsif v_u.attacker_attempts + 1 >= 3 then
        update public.battle_ultis
           set attacker_attempts = attacker_attempts + 1, attacker_result = 'failed', attacker_at = v_t
         where id = v_u.id;
        perform public.sx_ulti_resolve(p_battle);
      else
        update public.battle_ultis set attacker_attempts = attacker_attempts + 1 where id = v_u.id;
      end if;
    else
      if v_u.defender_result <> 'pending' then
        raise exception 'already_resolved';
      end if;
      select * into v_ch from public.ulti_challenges where id = v_u.defense_challenge_id;
      if public.sx_normalize_command(p_arg) = public.sx_normalize_command(v_ch.expected_command) then
        update public.battle_ultis
           set defender_attempts = defender_attempts + 1, defender_result = 'solved', defender_at = v_t
         where id = v_u.id;
      elsif v_u.defender_attempts + 1 >= 3 then
        update public.battle_ultis
           set defender_attempts = defender_attempts + 1, defender_result = 'failed', defender_at = v_t
         where id = v_u.id;
      else
        update public.battle_ultis set defender_attempts = defender_attempts + 1 where id = v_u.id;
      end if;
    end if;
  end if;

  return public.sx_battle_state(p_battle, v_me);
end;
$$;

-- -----------------------------------------------------------------------------------------------------
-- 8. Permisos de EJECUCIÓN de las funciones
--    Supabase concede EXECUTE por defecto a anon y authenticated sobre las funciones nuevas de public, y
--    PostgreSQL lo concede a PUBLIC. Por eso cada función lleva su revoke explícito, ANTES de cualquier grant:
--      * Funciones públicas (la app las llama con sesión): revoke de PUBLIC y anon; grant solo a authenticated.
--        NINGUNA función de Social es invocable por anon: todas exigen sesión (auth.uid()), a diferencia de
--        account_status (0005), que debe poder llamarse justo cuando el login falla.
--      * Funciones internas (sx_): sin EXECUTE para PUBLIC, anon ni authenticated. Solo las ejecutan, como dueño
--        del script, las 5 funciones SECURITY DEFINER.
-- -----------------------------------------------------------------------------------------------------

revoke all on function public.social_new_code() from public, anon;
grant execute on function public.social_new_code() to authenticated;

revoke all on function public.social_friends(text, text) from public, anon;
grant execute on function public.social_friends(text, text) to authenticated;

revoke all on function public.social_pairs(text, text) from public, anon;
grant execute on function public.social_pairs(text, text) to authenticated;

revoke all on function public.battle_lobby(text, text) from public, anon;
grant execute on function public.battle_lobby(text, text) to authenticated;

revoke all on function public.battle_sync(uuid) from public, anon;
grant execute on function public.battle_sync(uuid) to authenticated;

revoke all on function public.battle_play(uuid, text, integer, text) from public, anon;
grant execute on function public.battle_play(uuid, text, integer, text) to authenticated;

revoke all on function public.sx_username(uuid) from public, anon, authenticated;
revoke all on function public.sx_resolve_target(text) from public, anon, authenticated;
revoke all on function public.sx_are_friends(uuid, uuid) from public, anon, authenticated;
revoke all on function public.sx_friend_count(uuid) from public, anon, authenticated;
revoke all on function public.sx_rate_limit(uuid, text, integer, interval) from public, anon, authenticated;
revoke all on function public.sx_personal_streak(uuid) from public, anon, authenticated;
revoke all on function public.sx_islands_done(uuid) from public, anon, authenticated;
revoke all on function public.sx_pair_metrics(uuid, uuid, date) from public, anon, authenticated;
revoke all on function public.sx_xp(uuid) from public, anon, authenticated;
revoke all on function public.sx_level(integer) from public, anon, authenticated;
revoke all on function public.sx_user_level(uuid) from public, anon, authenticated;
revoke all on function public.sx_normalize_command(text) from public, anon, authenticated;
revoke all on function public.sx_pick_question(uuid, uuid) from public, anon, authenticated;
revoke all on function public.sx_battle_create(uuid, uuid, text) from public, anon, authenticated;
revoke all on function public.sx_battle_finish(uuid, integer, text) from public, anon, authenticated;
revoke all on function public.sx_advance_turn(uuid) from public, anon, authenticated;
revoke all on function public.sx_ulti_resolve(uuid) from public, anon, authenticated;
revoke all on function public.sx_battle_resolve(uuid) from public, anon, authenticated;
revoke all on function public.sx_battle_state(uuid, uuid) from public, anon, authenticated;
revoke all on function public.sx_try_match(uuid) from public, anon, authenticated;

commit;

-- =====================================================================================================
-- Advertencias esperadas del Security Advisor (tabla para documentar las aceptadas, como en el módulo 3)
--
-- El Advisor se revisa DESPUÉS de ejecutar este script. Cada fila que aparezca y no esté aquí se corrige o se
-- documenta en "Advertencias aceptadas del Security Advisor" de docs/superpowers/supabase-configuracion.md
-- ANTES de fusionar a main (CLAUDE.md, sección 4).
--
-- | # | Advertencia esperada | Objeto | Por qué es SECURITY DEFINER | Acepta o corrige |
-- |---|---|---|---|---|
-- | 1 | "Signed-In Users Can Execute SECURITY DEFINER Function" | public.social_friends(text, text) | Lee y escribe friendships (cerrada, con los ids de Auth de dos personas) y lee el progreso de OTRA persona (lesson_progress, lab_progress y exam_attempts son solo del dueño). Devuelve solo nombre de usuario, código, racha y los campos de BadgeFacts; respeta "ocultar progreso". Sin sesión no hace nada (no_session); acción de lista cerrada; límites de ritmo, de pendientes y de amigos; no recibe ids | Se acepta |
-- | 2 | "Signed-In Users Can Execute SECURITY DEFINER Function" | public.social_pairs(text, text) | Lee el progreso de DOS personas para calcular la racha de pareja y escribe pair_streaks (cerrada). Devuelve solo racha, si cada una hizo su ejercicio hoy y los días compartidos, nunca qué isla. Exige amistad; máximo 5 invitaciones pendientes | Se acepta |
-- | 3 | "Signed-In Users Can Execute SECURITY DEFINER Function" | public.battle_lobby(text, text) | Escribe matchmaking_queue, battle_challenges y crea batallas (cerradas); empareja a dos personas con un solo candado (skip locked). Solo opera sobre auth.uid() y sus amigos | Se acepta |
-- | 4 | "Signed-In Users Can Execute SECURITY DEFINER Function" | public.battle_sync(uuid) | Lee battles (cerrada, con ids de Auth) y battle_questions (sin ningún permiso, con la respuesta correcta) para devolver la vista PERSONAL de la batalla: la pregunta sin su respuesta, solo el título del reto de ulti de quien llama. Solo los dos jugadores reciben algo; el resto, not_found | Se acepta |
-- | 5 | "Signed-In Users Can Execute SECURITY DEFINER Function" | public.battle_play(uuid, text, integer, text) | Es la única vía de escritura de la batalla: el servidor decide aciertos, daños, turnos, plazos, escudo y XP (D7). Compara la respuesta con battle_questions y el comando con ulti_challenges, tablas sin permiso para la app. Solo opera sobre auth.uid() y sobre batallas donde esa persona participa | Se acepta |
-- | 6 | (Info) "RLS Enabled No Policy" (11 tablas) | friendships, pair_streaks, battle_questions, ulti_challenges, matchmaking_queue, battle_challenges, battles, battle_active, battle_turns, battle_ultis, social_rate_limit | No aplica (son tablas): RLS activo sin políticas y sin GRANT niega todo acceso directo; solo las leen las funciones de arriba | Se acepta (igual que recovery_email_codes, 0006) |
-- | 7 | "Public Can Execute SECURITY DEFINER Function" | (ninguna) | Ninguna función de Social es invocable por anon: todas llevan revoke de public y anon y exigen sesión | Si aparece alguna, se CORRIGE (falta un revoke) |
-- | 8 | "Function Search Path Mutable" | (ninguna) | Todas las funciones, públicas e internas, declaran set search_path = '' | Si aparece alguna, se CORRIGE |
--
-- social_new_code() es SECURITY INVOKER (no genera advertencia). Las 20 funciones internas sx_ no tienen
-- EXECUTE para anon ni authenticated (no generan advertencia de ejecución).
-- =====================================================================================================

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) RLS activo en las 14 tablas: las 14 filas deben decir rls_activo = true
-- select c.relname as tabla, c.relrowsecurity as rls_activo
-- from pg_class c
-- join pg_namespace n on n.oid = c.relnamespace
-- where n.nspname = 'public'
--   and c.relname in ('social_profile', 'friendships', 'pair_streaks', 'battle_questions', 'ulti_challenges',
--                     'fighter_level_curve', 'battles', 'battle_active', 'matchmaking_queue', 'battle_challenges',
--                     'battle_turns', 'battle_ultis', 'battle_results', 'social_rate_limit')
-- order by c.relname;

-- b) Políticas: deben salir EXACTAMENTE 5 filas, todas con roles = {authenticated}: social_profile (select, insert,
--    update), battle_results (select) y fighter_level_curve (select). Ninguna en las otras 11 tablas.
-- select tablename, policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'public'
--   and tablename in ('social_profile', 'friendships', 'pair_streaks', 'battle_questions', 'ulti_challenges',
--                     'fighter_level_curve', 'battles', 'battle_active', 'matchmaking_queue', 'battle_challenges',
--                     'battle_turns', 'battle_ultis', 'battle_results', 'social_rate_limit')
-- order by tablename, cmd;

-- c) Privilegios de tabla: NINGUNA fila para anon; para authenticated solo SELECT en social_profile, battle_results y
--    fighter_level_curve (nada en las otras 11) y nunca TRUNCATE, REFERENCES ni TRIGGER
-- select grantee, table_name, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public'
--   and table_name in ('social_profile', 'friendships', 'pair_streaks', 'battle_questions', 'ulti_challenges',
--                      'fighter_level_curve', 'battles', 'battle_active', 'matchmaking_queue', 'battle_challenges',
--                      'battle_turns', 'battle_ultis', 'battle_results', 'social_rate_limit')
--   and grantee in ('anon', 'authenticated')
-- order by table_name, grantee, privilege_type;

-- d) Privilegios por columna de social_profile: INSERT solo en (user_id, username) y UPDATE solo en hide_progress
-- select grantee, column_name, privilege_type
-- from information_schema.column_privileges
-- where table_schema = 'public' and table_name = 'social_profile' and grantee in ('anon', 'authenticated')
--   and privilege_type in ('INSERT', 'UPDATE')
-- order by privilege_type, column_name;

-- e) Funciones de Social: las 5 públicas deben salir con es_security_definer = true y search_path = {search_path=""};
--    social_new_code y las 20 sx_ con es_security_definer = false. Ninguna sin search_path fijo.
-- select p.proname, p.prosecdef as es_security_definer, p.proconfig as configuracion
-- from pg_proc p
-- join pg_namespace n on n.oid = p.pronamespace
-- where n.nspname = 'public'
--   and (p.proname like 'sx\_%' or p.proname like 'social\_%' or p.proname like 'battle\_%')
-- order by p.proname;

-- f) Quién puede ejecutar cada función: anon debe ser false en todas; authenticated true SOLO en social_new_code,
--    social_friends, social_pairs, battle_lobby, battle_sync y battle_play (false en las 20 sx_)
-- select p.proname,
--        has_function_privilege('anon', p.oid, 'execute') as anon,
--        has_function_privilege('authenticated', p.oid, 'execute') as authenticated
-- from pg_proc p
-- join pg_namespace n on n.oid = p.pronamespace
-- where n.nspname = 'public'
--   and (p.proname like 'sx\_%' or p.proname like 'social\_%' or p.proname like 'battle\_%')
-- order by p.proname;

-- g) Claves foráneas: solo hacia auth.users o hacia tablas de este script (ninguna hacia placement, profile, devices,
--    recovery_email_codes, lesson_progress, lab_progress ni exam_attempts)
-- select conrelid::regclass as tabla, confrelid::regclass as apunta_a
-- from pg_constraint
-- where contype = 'f'
--   and conrelid::regclass::text in ('social_profile', 'friendships', 'pair_streaks', 'battles', 'battle_active',
--                                    'matchmaking_queue', 'battle_challenges', 'battle_turns', 'battle_ultis',
--                                    'battle_results', 'social_rate_limit')
-- order by 1, 2;

-- h) Reglas de nombre y de código (O6): el código nuevo debe verse como NIGHT-XXXXXX sin 0, O, 1 ni I
-- select public.social_new_code() from generate_series(1, 5);   -- solo para probar el formato

-- i) Las tablas de contenido están vacías hasta 0009 (esperado): las tres cuentas deben dar 0
-- select (select count(*) from public.battle_questions) as preguntas,
--        (select count(*) from public.ulti_challenges) as retos,
--        (select count(*) from public.fighter_level_curve) as niveles;
