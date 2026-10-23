-- =====================================================================================================
-- NightWatch Protocol - Script 0011: reportar y bloquear personas (moderación mínima de Social)
--
-- BORRADOR sin aplicar. NO se ejecuta desde la app ni desde el repositorio: el equipo lo revisa y, si lo
-- aprueba, lo pega a mano en el SQL Editor, DESPUÉS de 0001 a 0010 y una sola vez. Mientras tenga esta
-- línea de "BORRADOR" puede editarse; una vez aplicado deja de editarse y cualquier cambio va en 0012 o
-- posterior.
--
-- Fuente: la decisión del grupo del 2026-10-22, que REEMPLAZA a O15 ("reportar y bloquear quedan fuera del
-- módulo"; spec del módulo 4, sección 12). Decisiones:
--   * Se puede reportar a CUALQUIER persona: desde la búsqueda, las solicitudes, el perfil del amigo y el rival
--     de una batalla (también la partida rápida).
--   * NINGÚN número de reportes oculta una foto automáticamente. Solo el equipo la oculta, a mano, desde el
--     SQL Editor, con la columna nueva social_profile.avatar_hidden.
--   * Bloquear NO termina una batalla en curso; solo impide las nuevas.
--   * NO hay pantalla de moderación: el equipo revisa los reportes en el Table Editor de Supabase (o con la
--     consulta de abajo). Es una limitación honesta, documentada en docs/superpowers/supabase-configuracion.md.
--
-- La app ya lo espera: mientras este script no esté aplicado el servidor responde 'bad_action' a las acciones
-- nuevas y la app muestra "Función no disponible por ahora" sin romper nada.
--
-- QUÉ INCLUYE
--   * Dos tablas CERRADAS (RLS activo, sin políticas y sin GRANT para anon ni authenticated; el patrón de
--     recovery_email_codes en 0006 y de las 11 tablas cerradas de 0008):
--       social_reports  reporter_id, reported_id, reason, note (hasta 200 caracteres), created_at, status
--       social_blocks   blocker_id, blocked_id, created_at (clave primaria compuesta)
--   * social_profile.avatar_hidden (boolean, no nulo, false). El cliente NO puede escribirla (sin INSERT ni
--     UPDATE de columna): solo el equipo, como dueño de la tabla.
--   * Una función interna nueva, public.sx_is_blocked(a, b), sin EXECUTE para anon ni authenticated.
--   * Acciones NUEVAS dentro de social_friends (NO hay funciones públicas nuevas, para no sumar advertencias
--     del Security Advisor):  report, block, unblock, blocks.
--   * Seis funciones se REEMPLAZAN (create or replace, con la MISMA firma) a partir de sus versiones vigentes:
--       social_friends   (0010)  acciones nuevas; invite y accept respetan el bloqueo; avatar_hidden
--       social_pairs     (0010)  invite y accept respetan el bloqueo; avatar_hidden
--       battle_lobby     (0010)  challenge_send y challenge_accept respetan el bloqueo; avatar_hidden
--       sx_battle_state  (0010)  avatar_hidden en 'me' y en 'rival'
--       sx_try_match     (0008)  la partida rápida no empareja a dos personas con un bloqueo entre ellas
--       social_set_avatar(0010)  con avatar_hidden = true no se puede subir ({"status":"error","code":"avatar_hidden"})
--     No cambia nada más de su lógica: lo nuevo está marcado con "0011".
--   * Las políticas de INSERT y UPDATE del bucket 'social-avatars' (0010) se vuelven a crear con una condición
--     más: la persona no debe tener avatar_hidden.
--
-- FORMATO DE LA ACCIÓN report (p_arg, tres partes separadas por una barra vertical):
--       destino|motivo|nota
--   destino  nombre de usuario exacto o código de amigo (NIGHT-XXXXXX); ni uno ni otro llevan '|'.
--   motivo   uno de: inappropriate_photo, offensive_name, harassment, other.
--   nota     opcional, hasta 200 caracteres después de quitar espacios de los extremos; PUEDE llevar '|' (todo lo
--            que sigue a la segunda barra es la nota). Siempre van las DOS barras, aunque no haya nota:
--            'Mateo_99|harassment|' es válido; 'Mateo_99|harassment' no.
--   Todo el texto de p_arg mide como máximo 300 caracteres. Un formato inválido devuelve
--   {"status":"error","code":"bad_report"} (resultado de negocio, sin excepción).
--
-- RESPUESTAS (resultado, nunca excepción; las excepciones siguen siendo solo las de 0008: no_session,
-- social_not_enabled, bad_action, bad_query y rate_limited):
--   report    {"status":"reported"}                  o  error: bad_report, not_found
--   block     {"status":"blocked"}                   o  error: not_found
--   unblock   {"status":"unblocked"}                 o  error: not_found
--   blocks    {"blocked":[{"username","friend_code","avatar_key","avatar_version"}, ...]}  (los más nuevos primero)
--   "No existe", "soy yo" y cualquier otro rechazo de report, block y unblock devuelven EXACTAMENTE el mismo
--   not_found (igual que 0008).
--   Límites de ritmo (sx_rate_limit): report 10 por día; block y unblock comparten 30 por hora (el grupo fijó
--   solo el de report; el de bloquear es de este script y evita que se use para martillar la base).
--
-- EFECTOS DE BLOQUEAR (A bloquea a B). Al bloquear:
--   * se borra la amistad entre los dos (en cualquier estado: aceptada, pendiente o rechazada), la pareja de
--     racha (pendiente o activa) y se cancelan los retos pendientes entre los dos (como remove);
--   * NO se termina una batalla en curso, ni se toca la cola de la partida rápida.
--   Mientras exista el bloqueo, EN AMBOS SENTIDOS (si A bloquea a B, ni A ni B pueden):
--   * search      responde not_found;
--   * invite (amistad), invite (pareja) y challenge_send responden not_found (la MISMA respuesta que "no existe");
--   * accept (amistad, pareja o reto) responde como si la solicitud ya no existiera (por si una carrera dejó una
--     pendiente);
--   * la partida rápida (sx_try_match) no los empareja.
--   Reportar NO depende del bloqueo: A puede reportar a B aunque B haya bloqueado a A. Consecuencia aceptada: report
--   distingue "no existe" de "existe", así que quien ya conoce el nombre o el código de alguien que lo bloqueó
--   puede comprobar que la cuenta existe (con 10 intentos al día). Esa comprobación ya era posible por la
--   búsqueda antes de que existiera el bloqueo.
--
-- avatar_hidden. Con avatar_hidden = true:
--   * social_friends, social_pairs, battle_lobby y sx_battle_state (battle_sync y battle_play) devuelven
--     avatar_key y avatar_version en null para esa persona: nadie ve su foto y la app muestra el emoji;
--   * las políticas INSERT y UPDATE de 'social-avatars' rechazan subir o reemplazar;
--   * social_set_avatar(true) devuelve {"status":"error","code":"avatar_hidden"}. social_set_avatar(false) y
--     borrar el archivo siguen permitidos.
--   OJO: el archivo sigue en el bucket y cualquier persona con sesión podría pedirlo si conociera su clave
--   (nadie la recibe ya) o listando el bucket (riesgo ya aceptado en 0010). Para RETIRARLO de verdad, bórralo
--   también en Storage > social-avatars (el archivo se llama <avatar_key>.jpg).
--   CÓMO LO ACTIVA Y LO LEVANTA EL EQUIPO: ver la sección "Operación del equipo" al final.
--
-- QUÉ NO INCLUYE (a propósito)
--   * Ninguna tabla, función ni permiso de módulos anteriores cambia, salvo lo listado arriba.
--   * Ninguna ocultación automática de fotos por número de reportes, ni baneo de cuentas, ni aviso a la persona
--     reportada, ni pantalla de moderación. Un reporte no cambia nada para quien reporta (no lo bloquea, no
--     cancela nada).
--   * Los reportes repetidos del mismo par no se deduplican: el equipo ve todos (máximo 10 al día por persona).
--
-- Supuestos del proyecto Supabase (igual que en 0001, 0008 y 0010):
--   * "Automatically expose new tables" DESACTIVADO y "Automatic RLS" ACTIVADO: aun así cada tabla nueva lleva
--     su enable row level security y su revoke explícitos.
--   * La clave publishable viaja en el APK: la seguridad real es RLS, el cierre de las tablas, las funciones y
--     las políticas de Storage.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. Tablas nuevas (CERRADAS)
--    social_reports.note: hasta 200 caracteres; la app y la función lo recortan, el check lo garantiza.
--    Las dos tablas borran sus filas si se borra la cuenta (on delete cascade), como el resto de Social.
-- -----------------------------------------------------------------------------------------------------
create table public.social_reports (
  id          bigint      generated always as identity primary key,
  reporter_id uuid        not null references auth.users (id) on delete cascade,
  reported_id uuid        not null references auth.users (id) on delete cascade,
  reason      text        not null
              check (reason in ('inappropriate_photo', 'offensive_name', 'harassment', 'other')),
  note        text
              check (note is null or char_length(note) <= 200),
  created_at  timestamptz not null default now(),
  status      text        not null default 'open'
              check (status in ('open', 'reviewed')),
  constraint social_reports_not_self check (reporter_id <> reported_id)
);

create index social_reports_reported_idx on public.social_reports (reported_id);
create index social_reports_status_idx   on public.social_reports (status, created_at desc);

create table public.social_blocks (
  blocker_id uuid        not null references auth.users (id) on delete cascade,
  blocked_id uuid        not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint social_blocks_not_self check (blocker_id <> blocked_id)
);

-- Para preguntar "¿me bloqueó alguien?" sin recorrer la tabla (la clave primaria ya cubre blocker_id).
create index social_blocks_blocked_idx on public.social_blocks (blocked_id);

alter table public.social_reports enable row level security;
alter table public.social_blocks  enable row level security;

revoke all on public.social_reports from anon, authenticated;
revoke all on public.social_blocks  from anon, authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 2. social_profile.avatar_hidden
--    El GRANT por columna de 0008 solo da INSERT (user_id, username) y UPDATE (hide_progress), así que el
--    cliente no puede escribir esta columna. El revoke de abajo no cambia nada hoy: deja escrito que esa es la
--    intención y protege si alguien concede un permiso más amplio por error (igual que en 0010).
--    El dueño de la fila SÍ puede leerla (SELECT de la tabla, RLS del dueño): es su propio dato.
-- -----------------------------------------------------------------------------------------------------
alter table public.social_profile
  add column avatar_hidden boolean not null default false;

revoke insert (avatar_hidden), update (avatar_hidden) on public.social_profile from anon, authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 3. sx_is_blocked(a, b): ¿hay un bloqueo entre las dos personas, en CUALQUIER sentido? (interna)
--    Con un id nulo devuelve false. Sin EXECUTE para la app (sección 6).
-- -----------------------------------------------------------------------------------------------------
create function public.sx_is_blocked(p_a uuid, p_b uuid)
returns boolean
language sql
stable
set search_path = ''
as $$
  select exists (
    select 1 from public.social_blocks b
    where (b.blocker_id = p_a and b.blocked_id = p_b)
       or (b.blocker_id = p_b and b.blocked_id = p_a)
  )
$$;

-- -----------------------------------------------------------------------------------------------------
-- 4. Funciones reemplazadas (create or replace, MISMA firma: conservan dueño y permisos; la sección 6 los repite)
-- -----------------------------------------------------------------------------------------------------

-- 4.1 sx_try_match (de 0008): la partida rápida no empareja a dos personas con un bloqueo entre ellas.
create or replace function public.sx_try_match(p_me uuid)
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
    and not public.sx_is_blocked(p_me, q.user_id)   -- 0011
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

-- 4.2 sx_battle_state (de 0010, interna). 'me' y 'rival' devuelven avatar_key y avatar_version en null si la
-- foto de esa persona está oculta.
create or replace function public.sx_battle_state(p_battle uuid, p_user uuid)
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
  v_msp   public.social_profile%rowtype;  -- 0010: perfil social de quien mira
  v_rsp   public.social_profile%rowtype;  -- 0010: perfil social del rival
begin
  select * into v_b from public.battles where id = p_battle;
  v_seat  := case when v_b.player1_id = p_user then 1 else 2 end;
  v_rival := case when v_seat = 1 then v_b.player2_id else v_b.player1_id end;
  select * into v_msp from public.social_profile where user_id = p_user;   -- 0010
  select * into v_rsp from public.social_profile where user_id = v_rival;  -- 0010

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
      'ulti_charged', case when v_seat = 1 then v_b.ulti_charged1 else v_b.ulti_charged2 end,
      'avatar_key', case when v_msp.avatar_hidden then null else v_msp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_msp.avatar_hidden then null else v_msp.avatar_version end       -- 0010, 0011
    ),
    'rival', jsonb_build_object(
      'username', public.sx_username(v_rival),
      'hp', case when v_seat = 1 then v_b.hp2 else v_b.hp1 end,
      'seconds_since_seen', round(extract(epoch from (v_now - case when v_seat = 1 then v_b.last_seen2 else v_b.last_seen1 end))::numeric, 1),
      'avatar_key', case when v_rsp.avatar_hidden then null else v_rsp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_rsp.avatar_hidden then null else v_rsp.avatar_version end       -- 0010, 0011
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

-- 4.3 social_set_avatar (de 0010): con avatar_hidden = true no se puede subir una foto.
create or replace function public.social_set_avatar(p_has_photo boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me      uuid := (select auth.uid());
  v_key     uuid;
  v_version integer;
  v_hidden  boolean;   -- 0011
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_has_photo is null then
    raise exception 'bad_action';
  end if;
  select avatar_hidden into v_hidden from public.social_profile where user_id = v_me;   -- 0011 (antes: exists)
  if v_hidden is null then
    raise exception 'social_not_enabled';
  end if;
  perform public.sx_rate_limit(v_me, 'avatar', 30, interval '1 hour');

  -- 0011: la foto de esta persona está oculta por el equipo. Es un resultado, no una excepción (el contador del ritmo se conserva).
  if p_has_photo and v_hidden then
    return jsonb_build_object('status', 'error', 'code', 'avatar_hidden');
  end if;

  if p_has_photo then
    update public.social_profile
       set avatar_version = coalesce(avatar_version, 0) + 1
     where user_id = v_me
    returning avatar_key, avatar_version into v_key, v_version;
  else
    select avatar_key, avatar_version into v_key, v_version
      from public.social_profile where user_id = v_me;
    if v_version is not null then
      update public.social_profile
         set avatar_key = gen_random_uuid(), avatar_version = null
       where user_id = v_me
      returning avatar_key, avatar_version into v_key, v_version;
    end if;
  end if;

  return jsonb_build_object('status', 'ok', 'avatar_key', v_key, 'avatar_version', v_version);
end;
$$;

-- 4.4 social_friends (de 0010). Acciones nuevas: report, block, unblock, blocks. invite, search y accept respetan
-- el bloqueo. Toda persona que devuelve lleva avatar_key y avatar_version en null si su foto está oculta.
create or replace function public.social_friends(p_action text, p_arg text default null)
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
  v_target text;   -- 0011: partes de p_arg en 'report'
  v_reason text;
  v_note   text;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_action is null or p_action not in ('list', 'requests', 'search', 'invite', 'accept', 'decline', 'cancel', 'remove', 'profile',
                                          'report', 'block', 'unblock', 'blocks') then   -- 0011: las cuatro últimas
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
                 'trained_today', s.st -> 'today',
                 'avatar_key', case when sp.avatar_hidden then null else sp.avatar_key end,              -- 0010, 0011
                 'avatar_version', case when sp.avatar_hidden then null else sp.avatar_version end       -- 0010, 0011
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
        select jsonb_agg(jsonb_build_object('username', sp.username, 'friend_code', sp.friend_code,
                                            'avatar_key', case when sp.avatar_hidden then null else sp.avatar_key end,
                                            'avatar_version', case when sp.avatar_hidden then null else sp.avatar_version end) order by f.created_at)  -- 0010, 0011
        from public.friendships f
        join public.social_profile sp on sp.user_id = f.requested_by
        where f.status = 'pending' and f.requested_by <> v_me and v_me in (f.user_low, f.user_high)
      ), '[]'::jsonb),
      'sent', coalesce((
        select jsonb_agg(jsonb_build_object('username', sp.username, 'friend_code', sp.friend_code,
                                            'avatar_key', case when sp.avatar_hidden then null else sp.avatar_key end,
                                            'avatar_version', case when sp.avatar_hidden then null else sp.avatar_version end) order by f.created_at)  -- 0010, 0011
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
    -- Sin cuenta, sin Social habilitado, yo mismo o un bloqueo entre los dos: la MISMA respuesta (no se distingue una cuenta que no existe).
    if v_other is null or v_other = v_me or public.sx_is_blocked(v_me, v_other) then   -- 0011: sx_is_blocked
      return jsonb_build_object('status', 'error', 'code', 'not_found');
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
    return jsonb_build_object('status', 'ok', 'found', true, 'username', v_sp.username, 'friend_code', v_sp.friend_code, 'relation', v_rel,
                              'avatar_key', case when v_sp.avatar_hidden then null else v_sp.avatar_key end,
                              'avatar_version', case when v_sp.avatar_hidden then null else v_sp.avatar_version end);  -- 0010, 0011

  elsif p_action = 'invite' then
    if p_arg is null or char_length(btrim(p_arg)) < 3 or char_length(btrim(p_arg)) > 40 then
      raise exception 'bad_query';
    end if;
    perform public.sx_rate_limit(v_me, 'invite', 20, interval '1 hour');
    -- Primero lo que depende SOLO de mí (mis límites): así no sirve de pista sobre la otra cuenta.
    if (select count(*) from public.friendships where status = 'pending' and requested_by = v_me) >= 20 then
      return jsonb_build_object('status', 'error', 'code', 'too_many_pending');
    end if;
    if public.sx_friend_count(v_me) >= 50 then
      return jsonb_build_object('status', 'error', 'code', 'friends_limit');
    end if;
    v_other := public.sx_resolve_target(p_arg);
    -- "No existe" y un bloqueo entre los dos devuelven EXACTAMENTE lo mismo (0011).
    if v_other is null or public.sx_is_blocked(v_me, v_other) then
      return jsonb_build_object('status', 'error', 'code', 'not_found');
    end if;
    if v_other = v_me then
      return jsonb_build_object('status', 'error', 'code', 'invalid_target');
    end if;
    -- A partir de aquí ya hay una relación conmigo (amigo, solicitud o rechazo): responder con detalle es legítimo.
    select * into v_f from public.friendships
     where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other) for update;
    if found then
      if v_f.status = 'accepted' then
        return jsonb_build_object('status', 'error', 'code', 'already_friends');
      elsif v_f.status = 'pending' then
        if v_f.requested_by = v_me then
          return jsonb_build_object('status', 'error', 'code', 'request_exists');
        else
          return jsonb_build_object('status', 'error', 'code', 'request_incoming');
        end if;
      elsif v_f.responded_at > clock_timestamp() - interval '24 hours' then
        return jsonb_build_object('status', 'error', 'code', 'cooldown');
      end if;
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
    -- Persona inexistente, sin solicitud mía o con un bloqueo de por medio (0011): la MISMA respuesta.
    if v_other is null or not found or public.sx_is_blocked(v_me, v_other) then
      return jsonb_build_object('status', 'error', 'code', 'request_gone');
    end if;
    if public.sx_friend_count(v_me) >= 50 or public.sx_friend_count(v_other) >= 50 then
      return jsonb_build_object('status', 'error', 'code', 'friends_limit');
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
      return jsonb_build_object('status', 'error', 'code', 'request_gone');
    end if;
    return jsonb_build_object('status', 'declined');

  elsif p_action = 'cancel' then
    v_other := public.sx_resolve_target(p_arg);
    delete from public.friendships
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and requested_by = v_me;
    if not found then
      return jsonb_build_object('status', 'error', 'code', 'request_gone');
    end if;
    return jsonb_build_object('status', 'cancelled');

  elsif p_action = 'remove' then
    v_other := public.sx_resolve_target(p_arg);
    -- Inexistente y "no es mi amigo" devuelven lo mismo.
    if v_other is null or not public.sx_are_friends(v_me, v_other) then
      return jsonb_build_object('status', 'error', 'code', 'not_found');
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

  elsif p_action = 'report' then
    -- 0011. p_arg = 'destino|motivo|nota' (ver el encabezado). Primero el ritmo, luego el formato: los intentos inválidos también cuentan.
    perform public.sx_rate_limit(v_me, 'report', 10, interval '1 day');
    if p_arg is null
       or char_length(p_arg) > 300
       or coalesce(array_length(string_to_array(p_arg, '|'), 1), 0) < 3 then
      return jsonb_build_object('status', 'error', 'code', 'bad_report');
    end if;
    v_target := btrim(split_part(p_arg, '|', 1));
    v_reason := btrim(split_part(p_arg, '|', 2));
    -- La nota es TODO lo que sigue a la segunda barra (puede llevar más barras).
    v_note   := btrim(substr(p_arg, char_length(split_part(p_arg, '|', 1)) + char_length(split_part(p_arg, '|', 2)) + 3));
    if char_length(v_target) < 3 or char_length(v_target) > 40
       or v_reason not in ('inappropriate_photo', 'offensive_name', 'harassment', 'other')
       or char_length(v_note) > 200 then
      return jsonb_build_object('status', 'error', 'code', 'bad_report');
    end if;
    v_other := public.sx_resolve_target(v_target);
    -- "No existe" y "soy yo" devuelven EXACTAMENTE lo mismo (no se puede reportar a uno mismo). El bloqueo NO impide reportar.
    if v_other is null or v_other = v_me then
      return jsonb_build_object('status', 'error', 'code', 'not_found');
    end if;
    insert into public.social_reports (reporter_id, reported_id, reason, note)
    values (v_me, v_other, v_reason, nullif(v_note, ''));
    return jsonb_build_object('status', 'reported');

  elsif p_action = 'block' then
    -- 0011. Bloquea a una persona (por nombre exacto o código). Idempotente. No termina una batalla en curso.
    perform public.sx_rate_limit(v_me, 'block', 30, interval '1 hour');
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null or v_other = v_me then
      return jsonb_build_object('status', 'error', 'code', 'not_found');
    end if;
    insert into public.social_blocks (blocker_id, blocked_id)
    values (v_me, v_other)
    on conflict (blocker_id, blocked_id) do nothing;
    delete from public.friendships
     where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other);
    delete from public.pair_streaks
     where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other);
    update public.battle_challenges
       set status = 'cancelled'
     where status = 'pending'
       and ((challenger_id = v_me and challenged_id = v_other) or (challenger_id = v_other and challenged_id = v_me));
    return jsonb_build_object('status', 'blocked');

  elsif p_action = 'unblock' then
    -- 0011. Quita MI bloqueo. No devuelve la amistad ni la pareja. Si la otra persona también me bloqueó, ese bloqueo sigue.
    perform public.sx_rate_limit(v_me, 'block', 30, interval '1 hour');
    v_other := public.sx_resolve_target(p_arg);
    delete from public.social_blocks
     where v_other is not null and blocker_id = v_me and blocked_id = v_other;
    if not found then
      return jsonb_build_object('status', 'error', 'code', 'not_found');
    end if;
    return jsonb_build_object('status', 'unblocked');

  elsif p_action = 'blocks' then
    -- 0011. Las personas que YO bloqueé (nunca las que me bloquearon a mí), las más nuevas primero.
    return jsonb_build_object('blocked', coalesce((
      select jsonb_agg(jsonb_build_object(
               'username', sp.username,
               'friend_code', sp.friend_code,
               'avatar_key', case when sp.avatar_hidden then null else sp.avatar_key end,
               'avatar_version', case when sp.avatar_hidden then null else sp.avatar_version end
             ) order by b.created_at desc)
      from public.social_blocks b
      join public.social_profile sp on sp.user_id = b.blocked_id
      where b.blocker_id = v_me
    ), '[]'::jsonb));

  else
    -- profile
    v_other := public.sx_resolve_target(p_arg);
    if v_other is null or not public.sx_are_friends(v_me, v_other) then
      return jsonb_build_object('status', 'error', 'code', 'not_found');
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
      'history', v_hist,
      'avatar_key', case when v_sp.avatar_hidden then null else v_sp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_sp.avatar_hidden then null else v_sp.avatar_version end       -- 0010, 0011
    );
  end if;
end;
$$;

-- 4.5 social_pairs (de 0010). invite y accept respetan el bloqueo; la foto de la otra persona es null si está oculta.
create or replace function public.social_pairs(p_action text, p_arg text default null)
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
  v_osp   public.social_profile%rowtype;  -- 0010: perfil social de la otra persona
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
        return jsonb_build_object('status', 'error', 'code', 'pair_gone');
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
      select * into v_osp from public.social_profile where user_id = v_other;  -- 0010
      v_items := v_items || jsonb_build_array(jsonb_build_object(
        'username', public.sx_username(v_other),
        'friend_code', (select sp.friend_code from public.social_profile sp where sp.user_id = v_other),
        'status', case when v_p.status = 'active' then 'active'
                       when v_p.invited_by = v_me then 'pending_sent'
                       else 'pending_received' end,
        'expires_at', case when v_p.status = 'pending' then to_jsonb(v_p.expires_at) else null end,
        'metrics', v_m,
        'avatar_key', case when v_osp.avatar_hidden then null else v_osp.avatar_key end,              -- 0010, 0011
        'avatar_version', case when v_osp.avatar_hidden then null else v_osp.avatar_version end       -- 0010, 0011
      ));
    end loop;

    if p_action = 'detail' then
      if jsonb_array_length(v_items) = 0 then
        return jsonb_build_object('status', 'error', 'code', 'pair_gone');
      end if;
      return jsonb_build_object('pair', v_items -> 0);
    end if;
    return jsonb_build_object('pairs', v_items, 'top_current', v_top);

  elsif p_action = 'invite' then
    if p_arg is null or char_length(btrim(p_arg)) < 3 or char_length(btrim(p_arg)) > 40 then
      raise exception 'bad_query';
    end if;
    perform public.sx_rate_limit(v_me, 'pair_invite', 20, interval '1 hour');
    -- Primero lo que depende SOLO de mí (mi tope de invitaciones pendientes).
    if (select count(*) from public.pair_streaks where status = 'pending' and invited_by = v_me) >= 5 then
      return jsonb_build_object('status', 'error', 'code', 'too_many_pending');
    end if;
    v_other := public.sx_resolve_target(p_arg);
    -- "No existe", "soy yo", "no es mi amigo" y un bloqueo entre los dos devuelven EXACTAMENTE lo mismo (O8: la pareja exige amistad).
    if v_other is null or v_other = v_me or not public.sx_are_friends(v_me, v_other)
       or public.sx_is_blocked(v_me, v_other) then   -- 0011
      return jsonb_build_object('status', 'error', 'code', 'not_found');
    end if;
    if exists (select 1 from public.pair_streaks
                where user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)) then
      return jsonb_build_object('status', 'error', 'code', 'pair_exists');
    end if;
    insert into public.pair_streaks (user_low, user_high, invited_by, expires_at)
    values (least(v_me, v_other), greatest(v_me, v_other), v_me, clock_timestamp() + interval '3 days');
    return jsonb_build_object('status', 'sent');

  elsif p_action = 'accept' then
    v_other := public.sx_resolve_target(p_arg);
    update public.pair_streaks
       set status = 'active', accepted_at = clock_timestamp()
     where v_other is not null
       and not public.sx_is_blocked(v_me, v_other)   -- 0011
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and invited_by = v_other;
    if not found then
      return jsonb_build_object('status', 'error', 'code', 'pair_gone');
    end if;
    return jsonb_build_object('status', 'active');

  elsif p_action = 'decline' then
    v_other := public.sx_resolve_target(p_arg);
    delete from public.pair_streaks
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and invited_by = v_other;
    if not found then
      return jsonb_build_object('status', 'error', 'code', 'pair_gone');
    end if;
    return jsonb_build_object('status', 'declined');

  elsif p_action = 'cancel' then
    v_other := public.sx_resolve_target(p_arg);
    delete from public.pair_streaks
     where v_other is not null
       and user_low = least(v_me, v_other) and user_high = greatest(v_me, v_other)
       and status = 'pending' and invited_by = v_me;
    if not found then
      return jsonb_build_object('status', 'error', 'code', 'pair_gone');
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
      return jsonb_build_object('status', 'error', 'code', 'pair_gone');
    end if;
    return jsonb_build_object('status', 'ended');
  end if;
end;
$$;

-- 4.6 battle_lobby (de 0010). challenge_send y challenge_accept respetan el bloqueo (la partida rápida lo hace
-- sx_try_match). Los retos llevan la foto de quien reta o es retado, en null si está oculta.
create or replace function public.battle_lobby(p_action text, p_arg text default null)
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
                 'seconds_left', floor(extract(epoch from (c.expires_at - clock_timestamp()))),
                 'avatar_key', case when sp.avatar_hidden then null else sp.avatar_key end,              -- 0010, 0011
                 'avatar_version', case when sp.avatar_hidden then null else sp.avatar_version end       -- 0010, 0011
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
                 'battle_id', c.battle_id,
                 'avatar_key', case when sp.avatar_hidden then null else sp.avatar_key end,              -- 0010, 0011
                 'avatar_version', case when sp.avatar_hidden then null else sp.avatar_version end       -- 0010, 0011
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
    if p_arg is null or char_length(btrim(p_arg)) < 3 or char_length(btrim(p_arg)) > 40 then
      raise exception 'bad_query';
    end if;
    perform public.sx_rate_limit(v_me, 'challenge_send', 20, interval '1 hour');
    -- Primero lo que depende SOLO de mí (ya estoy en una batalla, ya tengo un reto pendiente).
    if v_my_battle is not null then
      return jsonb_build_object('status', 'error', 'code', 'already_in_battle');
    end if;
    if exists (select 1 from public.battle_challenges where challenger_id = v_me and status = 'pending') then
      return jsonb_build_object('status', 'error', 'code', 'challenge_pending');
    end if;
    v_other := public.sx_resolve_target(p_arg);
    -- "No existe", "soy yo", "no es mi amigo" y un bloqueo entre los dos devuelven EXACTAMENTE lo mismo.
    if v_other is null or v_other = v_me or not public.sx_are_friends(v_me, v_other)
       or public.sx_is_blocked(v_me, v_other) then   -- 0011
      return jsonb_build_object('status', 'error', 'code', 'not_found');
    end if;
    -- Ya es mi amigo: decir que está ocupado es legítimo.
    if exists (select 1 from public.battle_active where user_id = v_other) then
      return jsonb_build_object('status', 'error', 'code', 'rival_busy');
    end if;
    insert into public.battle_challenges (challenger_id, challenged_id, created_at, expires_at)
    values (v_me, v_other, clock_timestamp(), clock_timestamp() + interval '2 minutes');
    return jsonb_build_object('status', 'sent', 'seconds_left', 120);

  elsif p_action = 'challenge_accept' then
    v_other := public.sx_resolve_target(p_arg);
    select * into v_ch from public.battle_challenges
     where challenger_id = v_other and challenged_id = v_me and status = 'pending'
     for update;
    -- Persona inexistente, sin reto mío o con un bloqueo de por medio (0011): la MISMA respuesta.
    if v_other is null or not found or public.sx_is_blocked(v_me, v_other) then
      return jsonb_build_object('status', 'error', 'code', 'challenge_gone');
    end if;
    if v_my_battle is not null then
      return jsonb_build_object('status', 'error', 'code', 'already_in_battle');
    end if;
    if exists (select 1 from public.battle_active where user_id = v_other) then
      return jsonb_build_object('status', 'error', 'code', 'rival_busy');
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
      return jsonb_build_object('status', 'error', 'code', 'challenge_gone');
    end if;
    return jsonb_build_object('status', 'declined');

  else
    -- challenge_cancel
    update public.battle_challenges set status = 'cancelled' where challenger_id = v_me and status = 'pending';
    return jsonb_build_object('status', 'cancelled');
  end if;
end;
$$;

-- -----------------------------------------------------------------------------------------------------
-- 5. Políticas de 'social-avatars' (de 0010): INSERT y UPDATE con una condición más (no avatar_hidden)
--    Se borran y se vuelven a crear con el mismo nombre. La de lectura (select) y la de borrado (delete) no
--    cambian: una persona con la foto oculta puede borrar su archivo. Quien tenga avatar_hidden = true no puede
--    subir ni reemplazar su foto; nadie recibe su clave por las funciones.
-- -----------------------------------------------------------------------------------------------------
drop policy social_avatars_insert_own on storage.objects;
drop policy social_avatars_update_own on storage.objects;

create policy social_avatars_insert_own on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'social-avatars'
    and exists (
      select 1 from public.social_profile sp
      where sp.user_id = (select auth.uid())
        and not sp.avatar_hidden
        and storage.objects.name = sp.avatar_key::text || '.jpg'
    )
  );

create policy social_avatars_update_own on storage.objects
  for update to authenticated
  using (
    bucket_id = 'social-avatars'
    and exists (
      select 1 from public.social_profile sp
      where sp.user_id = (select auth.uid())
        and not sp.avatar_hidden
        and storage.objects.name = sp.avatar_key::text || '.jpg'
    )
  )
  with check (
    bucket_id = 'social-avatars'
    and exists (
      select 1 from public.social_profile sp
      where sp.user_id = (select auth.uid())
        and not sp.avatar_hidden
        and storage.objects.name = sp.avatar_key::text || '.jpg'
    )
  );

-- -----------------------------------------------------------------------------------------------------
-- 6. Permisos de las funciones
--    Las reemplazadas conservan sus permisos al hacer create or replace; se repiten aquí para que este script,
--    leído solo, deje claro cómo quedan. La interna nueva (sx_is_blocked) no tiene EXECUTE para nadie de la app.
-- -----------------------------------------------------------------------------------------------------
revoke all on function public.sx_is_blocked(uuid, uuid) from public, anon, authenticated;

revoke all on function public.social_friends(text, text) from public, anon;
grant execute on function public.social_friends(text, text) to authenticated;

revoke all on function public.social_pairs(text, text) from public, anon;
grant execute on function public.social_pairs(text, text) to authenticated;

revoke all on function public.battle_lobby(text, text) from public, anon;
grant execute on function public.battle_lobby(text, text) to authenticated;

revoke all on function public.social_set_avatar(boolean) from public, anon;
grant execute on function public.social_set_avatar(boolean) to authenticated;

revoke all on function public.sx_battle_state(uuid, uuid) from public, anon, authenticated;
revoke all on function public.sx_try_match(uuid) from public, anon, authenticated;

commit;

-- =====================================================================================================
-- Advertencias esperadas del Security Advisor (tabla para documentar las aceptadas, como en 0008 y 0010)
--
-- El Advisor se revisa DESPUÉS de ejecutar este script. Cada fila que aparezca y no esté aquí se corrige o se
-- documenta en "Advertencias aceptadas del Security Advisor (Social)" de docs/superpowers/supabase-configuracion.md
-- ANTES de fusionar a main (CLAUDE.md, sección 4).
--
-- Antes de este script (después de 0010): 10 advertencias y 0 errores. Después: 10 advertencias y 0 errores, y
-- 2 sugerencias informativas nuevas.
--
-- | # | Advertencia esperada | Objeto | Por qué | Acepta o corrige |
-- |---|---|---|---|---|
-- | 1 | (Info) "RLS Enabled No Policy" | public.social_reports | Intencional: RLS activo sin políticas y sin GRANT niega todo acceso directo; solo la escribe social_friends (action report) y la lee el equipo desde el panel | Acepta (igual que recovery_email_codes y las 11 tablas cerradas de 0008) |
-- | 2 | (Info) "RLS Enabled No Policy" | public.social_blocks | Intencional: igual que la anterior; solo la usan social_friends, social_pairs, battle_lobby y sx_try_match | Acepta |
-- | 3 | "Signed-In Users Can Execute SECURITY DEFINER Function" | (ninguna nueva) | No hay funciones públicas nuevas: report, block, unblock y blocks son acciones de social_friends, que ya está aceptada (S1). social_pairs, battle_lobby y social_set_avatar siguen igual (S2, S3 y S7) | Ya aceptadas |
-- | 4 | "Public Can Execute SECURITY DEFINER Function" | (ninguna) | Ninguna función de este script es invocable por anon: todas llevan revoke de public y anon | Si aparece, se CORRIGE |
-- | 5 | "Function Search Path Mutable" | (ninguna) | Todas las funciones de este script declaran set search_path = '' | Si aparece, se CORRIGE |
-- | 6 | "RLS Policy Always True" | (ninguna) | Las políticas de 'social-avatars' siguen acotadas al bucket (la de lectura es amplia a propósito, ver 0010) | Si aparece, se acepta con la razón de 0010 |
-- =====================================================================================================

-- =====================================================================================================
-- Operación del equipo (a mano, en el SQL Editor, con el rol postgres; están comentadas a propósito)
-- =====================================================================================================

-- Revisar los reportes (también se pueden ver en el Table Editor, tabla social_reports; esta consulta añade los nombres):
-- select r.id, r.created_at, r.status, r.reason, r.note,
--        rp.username as quien_reporta, dp.username as reportado, dp.friend_code as codigo_reportado, dp.avatar_hidden
-- from public.social_reports r
-- join public.social_profile rp on rp.user_id = r.reporter_id
-- join public.social_profile dp on dp.user_id = r.reported_id
-- order by r.status, r.created_at desc;

-- Cuántos reportes abiertos tiene cada persona (para decidir, a juicio del equipo; nada se oculta solo):
-- select dp.username, count(*) as abiertos, array_agg(distinct r.reason) as motivos
-- from public.social_reports r
-- join public.social_profile dp on dp.user_id = r.reported_id
-- where r.status = 'open'
-- group by dp.username
-- order by abiertos desc;

-- Marcar un reporte como revisado:
-- update public.social_reports set status = 'reviewed' where id = <ID_DEL_REPORTE>;

-- OCULTAR la foto de una persona (nadie la ve; ella no puede subir otra):
-- update public.social_profile set avatar_hidden = true where username = '<NOMBRE_DE_USUARIO>';
--   Para retirar también el archivo: Storage > social-avatars > borra <avatar_key>.jpg
--   (avatar_key: select avatar_key from public.social_profile where username = '<NOMBRE_DE_USUARIO>';)

-- LEVANTAR la ocultación (la foto vuelve si el archivo sigue en el bucket y con la misma clave y versión):
-- update public.social_profile set avatar_hidden = false where username = '<NOMBRE_DE_USUARIO>';

-- Quién tiene la foto oculta hoy:
-- select username, friend_code from public.social_profile where avatar_hidden;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- El guion supabase/verificacion/0011_escenarios.sql prueba el comportamiento completo con dos cuentas.
-- =====================================================================================================

-- a) RLS activo en las dos tablas nuevas (las dos filas con rowsecurity = true)
-- select tablename, rowsecurity from pg_tables
-- where schemaname = 'public' and tablename in ('social_reports', 'social_blocks');

-- b) Ninguna política y ningún privilegio de tabla para anon ni authenticated (debe dar 0 filas en las dos consultas)
-- select * from pg_policies where schemaname = 'public' and tablename in ('social_reports', 'social_blocks');
-- select grantee, table_name, privilege_type from information_schema.role_table_grants
-- where table_schema = 'public' and table_name in ('social_reports', 'social_blocks') and grantee in ('anon', 'authenticated');

-- c) avatar_hidden: columna booleana no nula con default false, y NO aparece en los privilegios por columna de INSERT ni UPDATE
-- select column_name, data_type, is_nullable, column_default
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'social_profile' and column_name = 'avatar_hidden';
-- select grantee, column_name, privilege_type
-- from information_schema.column_privileges
-- where table_schema = 'public' and table_name = 'social_profile' and grantee in ('anon', 'authenticated')
--   and privilege_type in ('INSERT', 'UPDATE')
-- order by privilege_type, column_name;

-- d) Funciones: sx_is_blocked sin EXECUTE para anon ni authenticated; las demás SECURITY DEFINER con search_path vacío
-- select p.proname, p.prosecdef as es_security_definer, p.proconfig as configuracion,
--        has_function_privilege('anon', p.oid, 'execute') as anon,
--        has_function_privilege('authenticated', p.oid, 'execute') as authenticated
-- from pg_proc p
-- join pg_namespace n on n.oid = p.pronamespace
-- where n.nspname = 'public'
--   and p.proname in ('sx_is_blocked', 'sx_try_match', 'sx_battle_state', 'social_set_avatar', 'social_friends', 'social_pairs', 'battle_lobby')
-- order by p.proname;

-- e) Políticas del bucket: siguen 4 filas (SELECT, INSERT, UPDATE, DELETE, todas con roles = {authenticated}); la de INSERT
--    y las dos condiciones de UPDATE ahora mencionan avatar_hidden
-- select policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'storage' and tablename = 'objects' and policyname like 'social\_avatars\_%'
-- order by cmd;

-- f) El cliente no puede escribir avatar_hidden (debe dar "permission denied for table social_profile"):
-- update public.social_profile set avatar_hidden = true where user_id = auth.uid();   -- ejecutado como authenticated
