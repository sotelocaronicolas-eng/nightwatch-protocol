-- =====================================================================================================
-- NightWatch Protocol - Script 0012: perfil social editable (fondo, apartados visibles y estadísticas)
--
-- BORRADOR sin aplicar. NO se ejecuta desde la app ni desde el repositorio: el equipo lo revisa y, si lo
-- aprueba, lo pega a mano en el SQL Editor, DESPUÉS de 0001 a 0011 y una sola vez. Mientras tenga esta
-- línea de "BORRADOR" puede editarse; una vez aplicado deja de editarse y cualquier cambio va en 0013 o
-- posterior.
--
-- Fuente: la decisión del grupo del 2026-10-25 (ramas social-perfil): cada persona ve y edita SU PROPIO perfil
-- social ("Tu perfil" y "Editar tu perfil") y el perfil del amigo muestra el fondo que ÉL eligió, sus
-- estadísticas y solo los apartados que ÉL deja visibles.
--
-- La app ya lo espera: mientras este script no esté aplicado, social_friends('profile') no devuelve
-- 'profile_bg' y la app muestra "Función no disponible por ahora" en lo nuevo, sin romper el perfil del amigo.
--
-- QUÉ INCLUYE
--   * social_profile: cuatro columnas nuevas, todas "no nulo" y con valor por defecto:
--       profile_bg    smallint  default 0     check (profile_bg between 0 and 5)   0 = sin fondo; 1 a 5 = fondo predeterminado
--       show_badges   boolean   default true  Insignias (y el bloque de progreso: nivel, ejercicios y labs, ver abajo)
--       show_stats    boolean   default true  Estadísticas
--       show_history  boolean   default true  Historial de batallas
--     El cliente las cambia SOLO con UPDATE por columna (GRANT UPDATE de esas cuatro a authenticated, el mismo
--     patrón que hide_progress). No hay INSERT de ellas: nacen con su valor por defecto. Los check garantizan
--     el rango; un valor fuera de rango se rechaza con el código 23514. La política social_profile_update_own
--     (0008, user_id = auth.uid()) ya limita el UPDATE a la fila propia: nadie puede cambiar el perfil de otra
--     persona. avatar_hidden, username, friend_code y las demás columnas siguen SIN permiso de UPDATE.
--   * social_friends se REEMPLAZA (create or replace, MISMA firma) a partir de su versión vigente en 0011, sin
--     cambiar nada más de su lógica. Solo cambia la acción 'profile'; lo nuevo está marcado con "0012".
--
-- FORMATO DE LA ACCIÓN profile (0012). Se mantienen los campos de 0011 (username, friend_code, streak,
-- trained_today, hidden, progress, history, avatar_key, avatar_version) y se suman:
--       "profile_bg":   0..5      siempre presente
--       "show_badges":  boolean   siempre presente
--       "show_stats":   boolean   siempre presente
--       "show_history": boolean   siempre presente
--       "stats":        {"played":n,"wins":n,"losses":n,"draws":n}   SOLO si show_stats; AUSENTE (sin clave) si no
--       "history":      [{"result","finished_at"}, ...]              SOLO si show_history; AUSENTE (sin clave) si no
--   * OMITIR = AUSENTE en 'stats' y 'history' (la clave no existe), para que ningún cliente tome un "null" o una
--     lista vacía por datos. 'progress' conserva su contrato de 0011: la clave siempre existe y vale null cuando
--     se omite (por hide_progress O por not show_badges); el contrato del bloque (los campos de BadgeFacts más
--     fighter_level) no cambia, solo cambia CUÁNDO se envía.
--   * 'hidden' sigue siendo hide_progress ("Ocultar mi progreso público", D11). Cuando es false pero show_badges
--     es false, progress también es null: la app lo distingue por show_badges.
--   * stats: el CONTEO TOTAL de TODAS las batallas terminadas DEL AMIGO contra cualquier rival (su fila de
--     battle_results, sin el límite de 10 y sin depender de quién mira). Las canceladas no cuentan porque no
--     tienen fila; las perdidas por abandono o desconexión sí. played = wins + losses + draws (result tiene un
--     check de tres valores). Nunca divide: el porcentaje lo calcula la app, que con played = 0 muestra "-".
--   * history: las 3 últimas batallas ENTRE QUIEN LLAMA Y EL AMIGO (antes 10), de la más nueva a la más vieja,
--     vistas desde quien llama. Solo esas dos personas las ven.
--   * Estadísticas e Historial solo dependen de su propio ojo; hide_progress NO las oculta (decisión del grupo).
--     Insignias y el bloque de progreso dependen de hide_progress Y de show_badges.
--   * Errores: sin cambios. "No existe" y "no es mi amigo" devuelven el mismo not_found. Los efectos de bloqueo
--     (un bloqueo termina la amistad, así que el perfil responde not_found) y de avatar_hidden (avatar_key y
--     avatar_version en null) siguen igual.
--
-- QUÉ NO INCLUYE (a propósito)
--   * Ninguna función pública nueva ni ninguna tabla nueva: sin advertencias nuevas del Security Advisor.
--   * El perfil PROPIO no pasa por una función: la app lee y escribe su fila de social_profile (SELECT del
--     dueño y UPDATE por columna) y lee sus batallas de battle_results, como hasta ahora.
--   * El fondo es solo un número. Las imágenes viven en la app (res/drawable-nodpi, profile_bg_1 a profile_bg_5).
--     Un número que la app no conozca se muestra como "sin fondo".
--
-- Supuestos del proyecto Supabase (igual que en 0001, 0008, 0010 y 0011):
--   * "Automatically expose new tables" DESACTIVADO y "Automatic RLS" ACTIVADO. Este script no crea tablas.
--   * La clave publishable viaja en el APK: la seguridad real es RLS, los GRANT por columna y las funciones.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. social_profile: columnas nuevas
--    Las filas existentes reciben el valor por defecto (sin fondo, todo visible): nada cambia para nadie hasta
--    que la persona edite su perfil.
-- -----------------------------------------------------------------------------------------------------
alter table public.social_profile
  add column profile_bg   smallint not null default 0
                          constraint social_profile_profile_bg_check check (profile_bg between 0 and 5),
  add column show_badges  boolean  not null default true,
  add column show_stats   boolean  not null default true,
  add column show_history boolean  not null default true;

-- -----------------------------------------------------------------------------------------------------
-- 2. Permisos por columna
--    UPDATE de las cuatro, como hide_progress (0008). Sin INSERT: el INSERT de 0008 sigue siendo solo
--    (user_id, username). El revoke de INSERT no cambia nada hoy: deja escrita la intención y protege si
--    alguien concede un permiso más amplio por error (igual que avatar_hidden en 0011).
--    La RLS de dueño (social_profile_update_own, user_id = auth.uid()) limita el UPDATE a la fila propia.
-- -----------------------------------------------------------------------------------------------------
revoke insert (profile_bg, show_badges, show_stats, show_history) on public.social_profile from anon, authenticated;
revoke update (profile_bg, show_badges, show_stats, show_history) on public.social_profile from anon;

grant update (profile_bg, show_badges, show_stats, show_history) on public.social_profile to authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 3. social_friends (de 0011). Solo cambia la acción 'profile'.
-- -----------------------------------------------------------------------------------------------------
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
  v_stats  jsonb;  -- 0012: totales del amigo en 'profile'
  v_out    jsonb;  -- 0012: respuesta de 'profile'
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
    -- 0011. p_arg = 'destino|motivo|nota' (ver el encabezado de 0011). Primero el ritmo, luego el formato: los intentos inválidos también cuentan.
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
    -- 0012: el ojo de Insignias (show_badges = false) lo omite igual que hide_progress.
    if v_sp.hide_progress or not v_sp.show_badges then
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

    v_out := jsonb_build_object(
      'username', v_sp.username,
      'friend_code', v_sp.friend_code,
      'streak', v_st -> 'current',
      'trained_today', v_st -> 'today',
      'hidden', v_sp.hide_progress,
      'progress', v_prog,
      'avatar_key', case when v_sp.avatar_hidden then null else v_sp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_sp.avatar_hidden then null else v_sp.avatar_version end,      -- 0010, 0011
      'profile_bg', v_sp.profile_bg,                                                              -- 0012
      'show_badges', v_sp.show_badges,
      'show_stats', v_sp.show_stats,
      'show_history', v_sp.show_history
    );

    -- 0012: totales del AMIGO contra cualquier rival (todas sus batallas terminadas, sin límite). Un apartado
    -- oculto no se calcula ni se envía: la clave queda AUSENTE.
    if v_sp.show_stats then
      select jsonb_build_object(
               'played', count(*),
               'wins',   count(*) filter (where r.result = 'win'),
               'losses', count(*) filter (where r.result = 'loss'),
               'draws',  count(*) filter (where r.result = 'draw'))
        into v_stats
        from public.battle_results r
       where r.user_id = v_other;
      v_out := v_out || jsonb_build_object('stats', v_stats);
    end if;

    -- Historial de batallas ENTRE LOS DOS (solo las dos personas lo ven, desde su propia fila de resultado).
    -- 0012: las 3 últimas (antes 10) y solo si el amigo deja visible el historial.
    if v_sp.show_history then
      select coalesce(jsonb_agg(h.item order by h.finished_at desc), '[]'::jsonb) into v_hist
      from (
        select r1.finished_at,
               jsonb_build_object('result', r1.result, 'finished_at', r1.finished_at) as item
        from public.battle_results r1
        join public.battle_results r2 on r2.battle_id = r1.battle_id and r2.user_id = v_other
        where r1.user_id = v_me
        order by r1.finished_at desc
        limit 3
      ) h;
      v_out := v_out || jsonb_build_object('history', v_hist);
    end if;

    return v_out;
  end if;
end;
$$;

-- -----------------------------------------------------------------------------------------------------
-- 4. Permisos de la función
--    create or replace conserva los permisos; se repiten para que este script, leído solo, deje claro cómo queda.
-- -----------------------------------------------------------------------------------------------------
revoke all on function public.social_friends(text, text) from public, anon;
grant execute on function public.social_friends(text, text) to authenticated;

commit;

-- =====================================================================================================
-- Advertencias esperadas del Security Advisor (tabla para documentar las aceptadas, como en 0008, 0010 y 0011)
--
-- El Advisor se revisa DESPUÉS de ejecutar este script. Cada fila que aparezca y no esté aquí se corrige o se
-- documenta en "Advertencias aceptadas del Security Advisor (Social)" de docs/superpowers/supabase-configuracion.md
-- ANTES de fusionar a main (CLAUDE.md, sección 4).
--
-- Antes de este script (después de 0011): 10 advertencias, 0 errores y 14 sugerencias. Después: IGUAL, sin
-- ninguna advertencia nueva, porque este script no crea tablas ni funciones nuevas: solo agrega columnas a una
-- tabla que ya tiene RLS y políticas, y reemplaza una función ya aceptada.
--
-- | # | Advertencia esperada | Objeto | Por qué | Acepta o corrige |
-- |---|---|---|---|---|
-- | 1 | "Signed-In Users Can Execute SECURITY DEFINER Function" | (ninguna nueva) | No hay funciones públicas nuevas: lo nuevo está dentro de social_friends, que ya está aceptada (S1) | Ya aceptada |
-- | 2 | (Info) "RLS Enabled No Policy" | (ninguna nueva) | No hay tablas nuevas | - |
-- | 3 | "Public Can Execute SECURITY DEFINER Function" | (ninguna) | social_friends lleva revoke de public y anon | Si aparece, se CORRIGE |
-- | 4 | "Function Search Path Mutable" | (ninguna) | social_friends declara set search_path = '' | Si aparece, se CORRIGE |
-- =====================================================================================================

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- El guion supabase/verificacion/0012_escenarios.sql prueba el comportamiento completo con dos cuentas.
-- =====================================================================================================

-- a) Las cuatro columnas: no nulas, con su valor por defecto (0, true, true, true)
-- select column_name, data_type, is_nullable, column_default
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'social_profile'
--   and column_name in ('profile_bg', 'show_badges', 'show_stats', 'show_history')
-- order by column_name;

-- b) El check del fondo (debe dar social_profile_profile_bg_check con profile_bg >= 0 y <= 5)
-- select conname, pg_get_constraintdef(oid) from pg_constraint
-- where conrelid = 'public.social_profile'::regclass and conname = 'social_profile_profile_bg_check';

-- c) Privilegios por columna: UPDATE de hide_progress y de las cuatro nuevas para authenticated; INSERT solo de user_id y username
-- select grantee, column_name, privilege_type
-- from information_schema.column_privileges
-- where table_schema = 'public' and table_name = 'social_profile' and grantee in ('anon', 'authenticated')
--   and privilege_type in ('INSERT', 'UPDATE')
-- order by privilege_type, column_name;

-- d) social_friends sigue SECURITY DEFINER con search_path vacío, sin EXECUTE para anon y con EXECUTE para authenticated
-- select p.proname, p.prosecdef as es_security_definer, p.proconfig as configuracion,
--        has_function_privilege('anon', p.oid, 'execute') as anon,
--        has_function_privilege('authenticated', p.oid, 'execute') as authenticated
-- from pg_proc p
-- join pg_namespace n on n.oid = p.pronamespace
-- where n.nspname = 'public' and p.proname = 'social_friends';

-- e) El cliente no puede escribir las columnas de otra persona ni avatar_hidden (debe dar "permission denied for table social_profile"):
-- update public.social_profile set avatar_hidden = true where user_id = auth.uid();   -- ejecutado como authenticated
