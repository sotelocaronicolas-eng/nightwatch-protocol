-- =====================================================================================================
-- NightWatch Protocol - Script 0010: fotos de perfil en Social
--
-- BORRADOR sin aplicar. NO se ejecuta desde la app ni desde el repositorio: el equipo lo revisa y, si lo
-- aprueba, lo pega a mano en el SQL Editor, DESPUÉS de 0001 a 0009 y una sola vez. Mientras tenga esta
-- línea de "BORRADOR" puede editarse; una vez aplicado deja de editarse y cualquier cambio va en 0011 o
-- posterior.
--
-- Fuente: la decisión del grupo de mostrar la foto de perfil en toda la parte Social (amigos, solicitudes,
-- parejas, retos, batallas y arena). La app ya lo espera: mientras este script no esté aplicado el servidor no
-- manda las claves de las fotos y la app muestra el emoji de siempre (sigue funcionando).
--
-- QUÉ INCLUYE
--   * social_profile: dos columnas nuevas.
--       avatar_key      uuid, no nulo, único, con valor por defecto gen_random_uuid(). Es la CLAVE ALEATORIA que da
--                       nombre al archivo de la foto (<avatar_key>.jpg). NO es el id de Auth ni se deriva de él.
--       avatar_version  integer, nulo = la persona no tiene foto. Sube cada vez que cambia la foto; la app lo usa
--                       (junto con la clave) para guardar la foto en su caché y no pedirla otra vez.
--     El cliente NO puede escribirlas: el GRANT por columna de 0008 solo da INSERT (user_id, username) y
--     UPDATE (hide_progress), así que estas columnas solo las cambia la función nueva (abajo).
--   * Función pública nueva: public.social_set_avatar(p_has_photo boolean). La app la llama DESPUÉS de subir o
--     de borrar el archivo para avisar al servidor. SECURITY DEFINER, identidad solo desde auth.uid(),
--     search_path vacío, sin acceso para public ni anon, límite de ritmo con sx_rate_limit.
--   * Cuatro funciones de 0008 se REEMPLAZAN (create or replace, con la MISMA firma) para devolver también
--     avatar_key y avatar_version de cada persona que ya devolvían. No cambia nada más de su lógica:
--       social_friends   (amigos, solicitudes recibidas y enviadas, búsqueda y perfil del amigo)
--       social_pairs     (parejas de racha)
--       battle_lobby     (retos recibidos y enviado)
--       sx_battle_state  (la vista de una batalla: lo que devuelven battle_sync y battle_play; incluye a la
--                         persona que llama y a su rival)
--   * Bucket privado nuevo 'social-avatars' (solo image/jpeg, máximo 256 KB) y sus políticas en storage.objects.
--
-- QUÉ NO INCLUYE (a propósito)
--   * No toca el bucket 'avatars' de 0004 ni profile.avatar_path: la foto del perfil (Editar perfil) sigue donde
--     estaba. La app sube además una COPIA (la misma imagen de 256x256) al bucket nuevo cuando Social está
--     habilitado; son dos archivos distintos para que Social nunca lea la carpeta privada del perfil.
--   * Nada de tablas nuevas ni de GRANT nuevos sobre tablas.
--
-- DECISIONES TÉCNICAS TOMADAS AL ESCRIBIR EL SCRIPT
--   * Quién ve las fotos (decisión del grupo): cualquier persona con sesión puede LEER cualquier archivo del bucket
--     'social-avatars'; anon no tiene ningún acceso. Es necesario porque amigos, solicitudes, parejas, retos y
--     rivales de una batalla rápida no se conocen entre sí de antemano. Subir, reemplazar y borrar solo se puede
--     sobre el archivo cuyo nombre sea EXACTAMENTE '<avatar_key de la persona>.jpg'.
--   * Al BORRAR la foto, social_set_avatar(false) pone avatar_version en nulo y además CAMBIA avatar_key por una
--     clave nueva. Así (clave, versión) nunca se repite para una foto distinta (la caché de la app no puede
--     mostrar una foto vieja) y quien guardó la clave anterior ya no puede pedir nada con ella. Por eso la app
--     borra el archivo ANTES de llamar a la función: después la política ya no reconocería el nombre viejo.
--   * Los errores siguen la convención de 0008: las cuatro excepciones del encabezado de 0008 (no_session,
--     social_not_enabled, bad_action y rate_limited) y ningún resultado de negocio nuevo, porque no existe una
--     razón de negocio para rechazar "tengo / no tengo foto". Repetir la misma llamada es inofensivo.
--   * Ocultar mi progreso NO oculta la foto (decisión del grupo).
--
-- RIESGOS ACEPTADOS (documentados también en docs/superpowers/supabase-configuracion.md al aplicarlo)
--   * LISTADO DEL BUCKET: como cualquier persona con sesión puede leer el bucket, también puede LISTARLO con la
--     API de Storage y ver todas las claves (uuid aleatorios). Eso no revela ids de Auth, nombres ni cuentas:
--     una clave solo se une a una persona a través de las funciones de Social, que ya limitan quién ve a quién.
--     Lo único que obtendría es la imagen sin nombre. Se acepta porque la alternativa (una carpeta o una
--     política por amistad) obligaría a consultar friendships desde storage.objects en cada descarga, y porque
--     la foto ya es visible para quien encuentre a la persona por su nombre exacto (búsqueda de amigos).
--   * CDN: Supabase puede servir una copia en caché de un archivo reemplazado (mismo nombre) durante un rato.
--     La app lo tolera: la versión cambia, pero los bytes pueden tardar en actualizarse en otros teléfonos.
--
-- Supuestos del proyecto Supabase (igual que en 0001, 0004 y 0008):
--   * "Automatically expose new tables" DESACTIVADO y "Automatic RLS" ACTIVADO (aquí no se crean tablas).
--   * La clave publishable viaja en el APK: la seguridad real es RLS, el cierre de las tablas, las funciones y
--     las políticas de Storage.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. Columnas nuevas de social_profile
--    gen_random_uuid() es volátil: las filas que ya existen reciben cada una su propio valor. La restricción
--    única evita dos personas con la misma clave (un archivo, una persona).
--    Los permisos por columna de 0008 (INSERT user_id y username; UPDATE hide_progress) no incluyen estas
--    columnas, así que el cliente no puede escribirlas. El revoke de abajo no cambia nada hoy: deja escrito
--    que esa es la intención y protege si alguien concede un permiso más amplio por error.
-- -----------------------------------------------------------------------------------------------------
alter table public.social_profile
  add column avatar_key     uuid    not null default gen_random_uuid(),
  add column avatar_version integer;

alter table public.social_profile
  add constraint social_profile_avatar_key_key unique (avatar_key),
  add constraint social_profile_avatar_version_check check (avatar_version is null or avatar_version >= 1);

revoke insert (avatar_key, avatar_version), update (avatar_key, avatar_version)
  on public.social_profile from anon, authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 2. social_set_avatar: avisa al servidor que la persona subió o borró su foto
--    p_has_photo = true   la foto ya está subida: avatar_version sube en 1 (la primera vez, 1)
--    p_has_photo = false  la foto ya está borrada: avatar_version queda nulo y avatar_key cambia por una nueva
--                         (si ya no había foto no se cambia nada)
--    Devuelve {"status":"ok","avatar_key":"...","avatar_version":n|null}. Límite de ritmo: 30 llamadas por hora.
-- -----------------------------------------------------------------------------------------------------
create function public.social_set_avatar(p_has_photo boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me      uuid := (select auth.uid());
  v_key     uuid;
  v_version integer;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_has_photo is null then
    raise exception 'bad_action';
  end if;
  if not exists (select 1 from public.social_profile where user_id = v_me) then
    raise exception 'social_not_enabled';
  end if;
  perform public.sx_rate_limit(v_me, 'avatar', 30, interval '1 hour');

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

-- -----------------------------------------------------------------------------------------------------
-- 3. Funciones de 0008 que devuelven a otra persona: ahora con avatar_key y avatar_version
--    create or replace con la MISMA firma (nombre, parámetros y tipo de retorno): conserva el dueño y los
--    permisos. Lo único nuevo en cada una está marcado con "0010". Los revoke y grant de la sección 5 se
--    repiten por si acaso.
-- -----------------------------------------------------------------------------------------------------

-- 3.1 sx_battle_state (interna). 'me' y 'rival' llevan la clave y la versión de su foto.
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
      'avatar_key', v_msp.avatar_key,            -- 0010
      'avatar_version', v_msp.avatar_version     -- 0010
    ),
    'rival', jsonb_build_object(
      'username', public.sx_username(v_rival),
      'hp', case when v_seat = 1 then v_b.hp2 else v_b.hp1 end,
      'seconds_since_seen', round(extract(epoch from (v_now - case when v_seat = 1 then v_b.last_seen2 else v_b.last_seen1 end))::numeric, 1),
      'avatar_key', v_rsp.avatar_key,            -- 0010
      'avatar_version', v_rsp.avatar_version     -- 0010
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

-- 3.2 social_friends. Cada persona que devuelve lleva ahora avatar_key y avatar_version.
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
                 'trained_today', s.st -> 'today',
                 'avatar_key', sp.avatar_key,              -- 0010
                 'avatar_version', sp.avatar_version       -- 0010
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
                                            'avatar_key', sp.avatar_key, 'avatar_version', sp.avatar_version) order by f.created_at)  -- 0010
        from public.friendships f
        join public.social_profile sp on sp.user_id = f.requested_by
        where f.status = 'pending' and f.requested_by <> v_me and v_me in (f.user_low, f.user_high)
      ), '[]'::jsonb),
      'sent', coalesce((
        select jsonb_agg(jsonb_build_object('username', sp.username, 'friend_code', sp.friend_code,
                                            'avatar_key', sp.avatar_key, 'avatar_version', sp.avatar_version) order by f.created_at)  -- 0010
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
    -- Sin cuenta, sin Social habilitado o yo mismo: la MISMA respuesta (no se distingue una cuenta que no existe).
    if v_other is null or v_other = v_me then
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
                              'avatar_key', v_sp.avatar_key, 'avatar_version', v_sp.avatar_version);  -- 0010

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
    if v_other is null then
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
    -- Persona inexistente o sin solicitud mía: la MISMA respuesta.
    if v_other is null or not found then
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
      'avatar_key', v_sp.avatar_key,              -- 0010
      'avatar_version', v_sp.avatar_version       -- 0010
    );
  end if;
end;
$$;

-- 3.3 social_pairs. Cada pareja lleva la clave y la versión de la foto de la otra persona.
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
        'avatar_key', v_osp.avatar_key,             -- 0010
        'avatar_version', v_osp.avatar_version      -- 0010
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
    -- "No existe", "soy yo" y "no es mi amigo" devuelven EXACTAMENTE lo mismo (O8: la pareja exige amistad).
    if v_other is null or v_other = v_me or not public.sx_are_friends(v_me, v_other) then
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

-- 3.4 battle_lobby. Los retos recibidos y el enviado llevan la clave y la versión de la foto de quien reta o es retado.
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
                 'avatar_key', sp.avatar_key,              -- 0010
                 'avatar_version', sp.avatar_version       -- 0010
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
                 'avatar_key', sp.avatar_key,              -- 0010
                 'avatar_version', sp.avatar_version       -- 0010
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
    -- "No existe", "soy yo" y "no es mi amigo" devuelven EXACTAMENTE lo mismo.
    if v_other is null or v_other = v_me or not public.sx_are_friends(v_me, v_other) then
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
    -- Persona inexistente o sin reto mío: la MISMA respuesta.
    if v_other is null or not found then
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
-- 4. Bucket privado 'social-avatars' y sus políticas en storage.objects
--    Mismo patrón de 0004: privado, solo image/jpeg, máximo 256 KB (262144 bytes); on conflict corrige la
--    configuración si el bucket ya existe (p. ej. creado a mano desde el panel).
--    LEER (select): cualquier persona con sesión (rol authenticated), sobre ese bucket. anon no recibe política.
--    SUBIR, REEMPLAZAR y BORRAR (insert, update, delete): solo el archivo cuyo nombre sea EXACTAMENTE
--    '<avatar_key de la persona>.jpg', comprobado contra su propia fila de social_profile con (select auth.uid()).
--    La subida con upsert (reemplazar) necesita select, insert y update; update lleva using y with check iguales
--    para que nadie pueda mover un archivo a otro nombre. El nombre del objeto se escribe storage.objects.name
--    dentro de la subconsulta para que no se confunda con ninguna columna de social_profile.
--    social_profile tiene RLS con "el dueño lee su fila": la subconsulta solo ve la fila de quien sube.
-- -----------------------------------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('social-avatars', 'social-avatars', false, 262144, array['image/jpeg'])
on conflict (id) do update
  set public             = false,
      file_size_limit    = 262144,
      allowed_mime_types = array['image/jpeg'];

create policy social_avatars_select_authenticated on storage.objects
  for select to authenticated
  using (bucket_id = 'social-avatars');

create policy social_avatars_insert_own on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'social-avatars'
    and exists (
      select 1 from public.social_profile sp
      where sp.user_id = (select auth.uid())
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
        and storage.objects.name = sp.avatar_key::text || '.jpg'
    )
  )
  with check (
    bucket_id = 'social-avatars'
    and exists (
      select 1 from public.social_profile sp
      where sp.user_id = (select auth.uid())
        and storage.objects.name = sp.avatar_key::text || '.jpg'
    )
  );

create policy social_avatars_delete_own on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'social-avatars'
    and exists (
      select 1 from public.social_profile sp
      where sp.user_id = (select auth.uid())
        and storage.objects.name = sp.avatar_key::text || '.jpg'
    )
  );

-- -----------------------------------------------------------------------------------------------------
-- 5. Permisos de las funciones
--    La función nueva: revoke de public y anon ANTES del grant, grant solo a authenticated (como en 0008).
--    Las cuatro reemplazadas conservan sus permisos al hacer create or replace; se repiten aquí para que este
--    script, leído solo, deje claro cómo quedan.
-- -----------------------------------------------------------------------------------------------------
revoke all on function public.social_set_avatar(boolean) from public, anon;
grant execute on function public.social_set_avatar(boolean) to authenticated;

revoke all on function public.social_friends(text, text) from public, anon;
grant execute on function public.social_friends(text, text) to authenticated;

revoke all on function public.social_pairs(text, text) from public, anon;
grant execute on function public.social_pairs(text, text) to authenticated;

revoke all on function public.battle_lobby(text, text) from public, anon;
grant execute on function public.battle_lobby(text, text) to authenticated;

revoke all on function public.sx_battle_state(uuid, uuid) from public, anon, authenticated;

commit;

-- =====================================================================================================
-- Advertencias esperadas del Security Advisor (tabla para documentar las aceptadas, como en 0008)
--
-- El Advisor se revisa DESPUÉS de ejecutar este script. Cada fila que aparezca y no esté aquí se corrige o se
-- documenta en "Advertencias aceptadas del Security Advisor (Social)" de docs/superpowers/supabase-configuracion.md
-- ANTES de fusionar a main (CLAUDE.md, sección 4).
--
-- | # | Advertencia esperada | Objeto | Por qué | Acepta o corrige |
-- |---|---|---|---|---|
-- | 1 | "Signed-In Users Can Execute SECURITY DEFINER Function" | public.social_set_avatar(boolean) | Es SECURITY DEFINER porque la app no puede escribir avatar_key ni avatar_version (sin permiso de columna): solo esta función las cambia, con la identidad de auth.uid(), límite de ritmo y sin parámetros de usuario | Acepta (igual que las 5 funciones de 0008) |
-- | 2 | Las 4 filas de 0008 para social_friends, social_pairs, battle_lobby, battle_sync y battle_play | las mismas funciones | Siguen igual: solo devuelven dos campos más | Ya aceptadas en 0008 |
-- | 3 | (Info) "Storage bucket allows listing" o parecida, si el Advisor la ofrece | bucket social-avatars | Cualquier persona con sesión puede leer y listar el bucket (decisión del grupo). Las claves son uuid aleatorios, no ids de Auth | Acepta (riesgo documentado en el encabezado) |
-- | 4 | "RLS Policy Always True" | (ninguna esperada) | Las cuatro políticas de social-avatars llevan siempre bucket_id = 'social-avatars'; la de lectura es amplia a propósito pero acotada al bucket y a authenticated | Si aparece sobre social_avatars_select_authenticated, se acepta con esta razón |
-- | 5 | "Public Can Execute SECURITY DEFINER Function" | (ninguna) | social_set_avatar lleva revoke de public y anon | Si aparece, se CORRIGE |
-- | 6 | "Function Search Path Mutable" | (ninguna) | Todas las funciones de este script declaran set search_path = '' | Si aparece, se CORRIGE |
-- =====================================================================================================

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) Columnas nuevas: avatar_key uuid no nulo con default gen_random_uuid() y avatar_version integer nulo
-- select column_name, data_type, is_nullable, column_default
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'social_profile' and column_name in ('avatar_key', 'avatar_version');

-- b) Cada fila tiene su propia clave (las dos columnas deben dar el mismo número)
-- select count(*) as filas, count(distinct avatar_key) as claves_distintas from public.social_profile;

-- c) Privilegios por columna de social_profile: INSERT solo en (user_id, username) y UPDATE solo en hide_progress.
--    avatar_key y avatar_version NO deben aparecer.
-- select grantee, column_name, privilege_type
-- from information_schema.column_privileges
-- where table_schema = 'public' and table_name = 'social_profile' and grantee in ('anon', 'authenticated')
--   and privilege_type in ('INSERT', 'UPDATE')
-- order by privilege_type, column_name;

-- d) Quién puede ejecutar social_set_avatar: anon false, authenticated true; es SECURITY DEFINER con search_path vacío
-- select p.proname, p.prosecdef as es_security_definer, p.proconfig as configuracion,
--        has_function_privilege('anon', p.oid, 'execute') as anon,
--        has_function_privilege('authenticated', p.oid, 'execute') as authenticated
-- from pg_proc p
-- join pg_namespace n on n.oid = p.pronamespace
-- where n.nspname = 'public' and p.proname in ('social_set_avatar', 'social_friends', 'social_pairs', 'battle_lobby', 'sx_battle_state')
-- order by p.proname;

-- e) Bucket: una fila con public = false, file_size_limit = 262144 y allowed_mime_types = {image/jpeg}
-- select id, name, public, file_size_limit, allowed_mime_types from storage.buckets where id = 'social-avatars';

-- f) Políticas del bucket: 4 filas (SELECT, INSERT, UPDATE, DELETE), todas con roles = {authenticated}
-- select policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'storage' and tablename = 'objects' and policyname like 'social\_avatars\_%'
-- order by cmd;

-- g) Prueba con dos cuentas (A y B, las dos con Social habilitado):
--    1. A llama social_set_avatar(true) y sube <su avatar_key>.jpg a 'social-avatars': debe funcionar y avatar_version = 1.
--    2. A intenta subir con cualquier otro nombre, o <avatar_key de B>.jpg: debe fallar (política).
--    3. B lee <avatar_key de A>.jpg con la sesión de B: debe funcionar. Sin sesión (anon): no debe poder leer.
--    4. B intenta borrar o reemplazar <avatar_key de A>.jpg: debe fallar.
--    5. A llama social_set_avatar(false) después de borrar el archivo: avatar_version nulo y avatar_key distinta.
--    6. En social_friends('list') de B, A aparece con avatar_key y avatar_version (null si no tiene foto).

-- h) El cliente no puede escribir las columnas (debe dar "permission denied for table social_profile"):
-- update public.social_profile set avatar_version = 99 where user_id = auth.uid();   -- ejecutado como authenticated
