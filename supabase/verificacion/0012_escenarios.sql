-- =====================================================================================================
-- NightWatch Protocol - Guion de escenarios del script 0012 (perfil social editable)
--
-- NO se ejecuta desde la app ni desde el repositorio: lo corres TÚ a mano en el SQL Editor de Supabase,
-- DESPUÉS de aplicar supabase/migrations/0012_perfil_social.sql (que sigue en BORRADOR: este guion NO se ha
-- ejecutado todavía). Vive en supabase/verificacion/ (no en migrations/) porque NO es un script del esquema.
--
-- CÓMO USARLO
--   1. Usa DOS cuentas de prueba de Authentication > Users (las mismas de los guiones de 0008 y 0011 sirven).
--      Sin perfil de Social ni uso previo: el guion crea sus perfiles y los borra al terminar.
--   2. En la sección 2 reemplaza las dos líneas marcadas con "REEMPLAZA" (v_a y v_b).
--   3. Ejecuta TODO el archivo de una vez, con el rol postgres del SQL Editor.
--   4. El resultado es una tabla: una fila por comprobación con lo esperado, lo obtenido y OK o FALLA. La primera
--      fila resume (debe decir "0 fallas"). Si algo falla, la columna bloque dice dónde mirar.
--   5. Después, vuelve a correr 0008_escenarios.sql y 0011_escenarios.sql completos: deben seguir dando 219 de 219
--      y 187 de 187 (0012 reemplaza social_friends y esos guiones son la prueba de que no cambió el resto de su lógica).
--
-- QUÉ HACE Y QUÉ GARANTIZA (igual que los guiones de 0008 y 0011)
--   * Todo el escenario es UNA sola sentencia (un bloque DO): es atómica. Si el guion lanza un error inesperado se
--     deshace TODO y no queda nada que limpiar.
--   * Cuando termina bien, BORRA lo que creó (perfiles, amistades, bloqueos, batallas y sus resultados).
--   * TODAS las comprobaciones y TODOS los cambios se limitan a las dos cuentas de prueba (v_a y v_b). No toca el
--     banco de preguntas, los retos de ulti ni la curva de XP: las batallas de prueba se insertan ya terminadas.
--   * La sesión se simula con set_config('request.jwt.claims', ...) y las llamadas se hacen con el rol authenticated
--     (o anon): así se prueban también los permisos reales.
--
-- QUÉ CUBRE
--   1 Columnas y permisos por columna: valores por defecto; el cliente actualiza las 4 columnas de SU fila, no las de
--     otra persona; valores fuera de rango o nulos rechazados; username, friend_code y avatar_hidden siguen sin
--     permiso; sin INSERT de las nuevas; anon sin acceso; nadie lee la fila de otra persona.
--   2 El perfil del amigo devuelve el fondo y los ojos (y los valores por defecto de un perfil sin editar).
--   3 Los apartados ocultos se omiten en el servidor: stats e history AUSENTES; con el ojo de Insignias cerrado,
--     progress sigue (solo fighter_level, viewed_count y labs_count; los hechos de insignias AUSENTES).
--   4 Estadísticas del AMIGO: 0 jugadas; victorias, derrotas y empates; más de 10 batallas (no se corta en 10);
--     no dependen del rival; las filas de quien mira no cuentan.
--   5 Historial entre los dos: máximo 3, de la más nueva a la más vieja, solo batallas compartidas.
--   6 hide_progress sigue mandando sobre TODO el progreso (null aunque show_badges sea true), y NO sobre
--     estadísticas ni historial. 6b: los contadores coinciden con las filas reales de la cuenta B (0 y más de 0;
--     el guion inserta filas de lesson_progress y lab_progress de B y borra solo las que puso).
--   7 Regresión: bloqueo, avatar_hidden, no_found indistinguible, bad_action y list.
--
-- QUÉ NO CUBRE
--   * Que un tercero (una persona que no es A ni B) no vea los datos de A y B: necesita una tercera cuenta.
--   * La app (pantallas, fondos, ojos): se comprueba a mano en el teléfono.
-- =====================================================================================================

-- -----------------------------------------------------------------------------------------------------
-- 1. Apoyo (tabla de resultados y funciones temporales; viven solo en esta sesión: pg_temp)
-- -----------------------------------------------------------------------------------------------------

drop table if exists pg_temp.t_res;

create temp table t_res (
  n         serial primary key,
  bloque    text,
  paso      text,
  esperado  text,
  obtenido  text,
  ok        boolean
);

-- Registra una comprobación.
create or replace function pg_temp.chk(p_bloque text, p_paso text, p_esperado text, p_obtenido text)
returns void language plpgsql as $$
begin
  insert into pg_temp.t_res (bloque, paso, esperado, obtenido, ok)
  values (p_bloque, p_paso, p_esperado, p_obtenido, p_esperado is not distinct from p_obtenido);
end;
$$;

-- Llama una función como una persona. Devuelve el jsonb, o {"status":"exception","code":"<mensaje>"} si lanzó excepción.
create or replace function pg_temp.call(p_role text, p_user uuid, p_sql text)
returns jsonb language plpgsql as $$
declare
  v jsonb;
begin
  perform set_config('request.jwt.claims',
    case when p_user is null then '{}' else json_build_object('sub', p_user, 'role', p_role)::text end, true);
  execute 'set local role ' || quote_ident(p_role);
  execute p_sql into v;
  reset role;
  return v;
exception when others then
  reset role;
  return jsonb_build_object('status', 'exception', 'code', sqlerrm, 'sqlstate', sqlstate);
end;
$$;

-- Ejecuta una consulta de UN valor de texto como una persona. Devuelve el valor, o 'ERR:<sqlstate>'.
create or replace function pg_temp.scalar(p_role text, p_user uuid, p_sql text)
returns text language plpgsql as $$
declare
  v text;
begin
  perform set_config('request.jwt.claims',
    case when p_user is null then '{}' else json_build_object('sub', p_user, 'role', p_role)::text end, true);
  execute 'set local role ' || quote_ident(p_role);
  execute p_sql into v;
  reset role;
  return v;
exception when others then
  reset role;
  return 'ERR:' || sqlstate;
end;
$$;

-- Ejecuta una sentencia (insert, update...) como una persona. Devuelve 'ok' o 'ERR:<sqlstate>'.
create or replace function pg_temp.run(p_role text, p_user uuid, p_sql text)
returns text language plpgsql as $$
begin
  perform set_config('request.jwt.claims',
    case when p_user is null then '{}' else json_build_object('sub', p_user, 'role', p_role)::text end, true);
  execute 'set local role ' || quote_ident(p_role);
  execute p_sql;
  reset role;
  return 'ok';
exception when others then
  reset role;
  return 'ERR:' || sqlstate;
end;
$$;

-- Atajo para social_friends (siempre con el rol authenticated).
create or replace function pg_temp.f(p_user uuid, p_action text, p_arg text default null)
returns jsonb language sql as $$
  select pg_temp.call('authenticated', p_user, format('select public.social_friends(%L, %L)', p_action, p_arg))
$$;

-- Resumen de una respuesta: 'ok'/'sent'/... si salió bien, 'error:<code>' si fue un resultado de negocio,
-- 'exception:<mensaje>' si lanzó excepción.
create or replace function pg_temp.s(v jsonb)
returns text language sql as $$
  select case
           when v is null then 'null'
           when v ->> 'status' in ('error', 'exception') then (v ->> 'status') || ':' || coalesce(v ->> 'code', '')
           else coalesce(v ->> 'status', '(sin status)')
         end
$$;

-- Un campo de texto; 'NULL' si falta o es null.
create or replace function pg_temp.nz(p_value text)
returns text language sql as $$
  select coalesce(p_value, 'NULL')
$$;

-- ¿Existe la clave en el objeto? ('true' o 'false'; distingue AUSENTE de null).
create or replace function pg_temp.has(v jsonb, p_key text)
returns text language sql as $$
  select (coalesce(v, '{}'::jsonb) ? p_key)::text
$$;

-- Los resultados del historial como texto: 'loss,win,draw'.
create or replace function pg_temp.hist(v jsonb)
returns text language sql as $$
  select coalesce((select string_agg(e ->> 'result', ',' order by o) from jsonb_array_elements(v -> 'history') with ordinality as t(e, o)), '')
$$;

-- Borra TODO lo de Social de las dos cuentas de prueba (no toca su perfil): batallas (y sus resultados, en cascada),
-- amistades, bloqueos y contadores de ritmo.
create or replace function pg_temp.reset_social(p_a uuid, p_b uuid)
returns void language plpgsql as $$
begin
  delete from public.battles where player1_id in (p_a, p_b) or player2_id in (p_a, p_b);
  delete from public.friendships where user_low in (p_a, p_b) or user_high in (p_a, p_b);
  delete from public.social_blocks where blocker_id in (p_a, p_b) or blocked_id in (p_a, p_b);
  delete from public.social_rate_limit where user_id in (p_a, p_b);
end;
$$;

-- Hace amigas a las dos cuentas por las funciones reales (A invita, B acepta).
create or replace function pg_temp.befriend(p_a uuid, p_b uuid, p_code_a text, p_code_b text)
returns text language plpgsql as $$
declare
  t text;
begin
  t := pg_temp.s(pg_temp.f(p_a, 'invite', p_code_b));
  if t <> 'sent' then return 'invite:' || t; end if;
  t := pg_temp.s(pg_temp.f(p_b, 'accept', p_code_a));
  if t <> 'accepted' then return 'accept:' || t; end if;
  return 'ok';
end;
$$;

-- Inserta una batalla TERMINADA entre A y B (como dueño) con la fila de resultado de A y/o la de B (null = sin fila),
-- terminada hace p_age. Devuelve el id de la batalla.
create or replace function pg_temp.mk_battle(p_a uuid, p_b uuid, p_res_a text, p_res_b text, p_age interval)
returns uuid language plpgsql as $$
declare
  b uuid;
begin
  insert into public.battles (mode, status, player1_id, player2_id, turn_seat, turn_deadline, finished_at)
  values ('quick', 'finished', p_a, p_b, 1, now(), now() - p_age)
  returning id into b;
  if p_res_a is not null then
    insert into public.battle_results (battle_id, user_id, result, xp_awarded, hp_left, opponent_username, finished_at)
    values (b, p_a, p_res_a, 0, 50, 'rival_x', now() - p_age);
  end if;
  if p_res_b is not null then
    insert into public.battle_results (battle_id, user_id, result, xp_awarded, hp_left, opponent_username, finished_at)
    values (b, p_b, p_res_b, 0, 50, 'rival_y', now() - p_age);
  end if;
  return b;
end;
$$;

-- -----------------------------------------------------------------------------------------------------
-- 2. Escenarios (una sola sentencia atómica)
-- -----------------------------------------------------------------------------------------------------

do $escenarios$
declare
  v_a        uuid := '00000000-0000-0000-0000-00000000000a';   -- << REEMPLAZA: id de la cuenta de prueba A
  v_b        uuid := '00000000-0000-0000-0000-00000000000b';   -- << REEMPLAZA: id de la cuenta de prueba B
  v          jsonb;
  t          text;
  i          integer;
  v_code_a   text;
  v_code_b   text;
  v_key_b    uuid;
begin
  -- ===================================================================================================
  -- 0. Comprobaciones previas y preparación
  -- ===================================================================================================
  if v_a = '00000000-0000-0000-0000-00000000000a'::uuid or v_b = '00000000-0000-0000-0000-00000000000b'::uuid then
    raise exception 'REEMPLAZA v_a y v_b por los ids de dos cuentas de prueba (Authentication > Users).';
  end if;
  if v_a = v_b then
    raise exception 'v_a y v_b deben ser cuentas distintas.';
  end if;
  if (select count(*) from auth.users where id in (v_a, v_b)) <> 2 then
    raise exception 'v_a o v_b no existen en auth.users.';
  end if;
  if not exists (select 1 from information_schema.columns
                 where table_schema = 'public' and table_name = 'social_profile' and column_name = 'profile_bg') then
    raise exception 'Falta aplicar 0012_perfil_social.sql: social_profile no tiene profile_bg.';
  end if;

  -- Restos de una corrida anterior (solo datos de Social de estas dos cuentas)
  perform pg_temp.reset_social(v_a, v_b);
  delete from public.social_profile where user_id in (v_a, v_b);

  -- Los perfiles se crean como dueño (el guion de 0008 ya prueba que la app los crea por INSERT de columna)
  insert into public.social_profile (user_id, username) values (v_a, 'tst0012_a'), (v_b, 'tst0012_b');
  select friend_code into v_code_a from public.social_profile where user_id = v_a;
  select friend_code into v_code_b from public.social_profile where user_id = v_b;
  select avatar_key into v_key_b from public.social_profile where user_id = v_b;

  -- ===================================================================================================
  -- BLOQUE 1. Columnas y permisos por columna
  -- ===================================================================================================
  begin
    perform pg_temp.chk('1 Columnas', 'Valores por defecto de un perfil nuevo (fondo 0, todo visible)', '0|true|true|true',
      pg_temp.scalar('authenticated', v_a, 'select profile_bg::text || ''|'' || show_badges::text || ''|'' || show_stats::text || ''|'' || show_history::text from public.social_profile'));

    perform pg_temp.chk('1 Permisos', 'A SÍ puede actualizar profile_bg de su fila', 'ok',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set profile_bg = 4 where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'y queda guardado', '4',
      pg_temp.scalar('authenticated', v_a, 'select profile_bg::text from public.social_profile'));
    perform pg_temp.chk('1 Permisos', 'A SÍ puede actualizar show_badges, show_stats y show_history de su fila', 'ok|ok|ok',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set show_badges = false where user_id = %L', v_a)) || '|' ||
      pg_temp.run('authenticated', v_a, format('update public.social_profile set show_stats = false where user_id = %L', v_a)) || '|' ||
      pg_temp.run('authenticated', v_a, format('update public.social_profile set show_history = false where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'A puede cambiar las cuatro a la vez (como GUARDAR CAMBIOS)', 'ok',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set profile_bg = 0, show_badges = true, show_stats = true, show_history = true where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'Vuelven a los valores por defecto', '0|true|true|true',
      pg_temp.scalar('authenticated', v_a, 'select profile_bg::text || ''|'' || show_badges::text || ''|'' || show_stats::text || ''|'' || show_history::text from public.social_profile'));
    perform pg_temp.chk('1 Permisos', 'Los extremos 0 y 5 se aceptan', 'ok|ok',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set profile_bg = 5 where user_id = %L', v_a)) || '|' ||
      pg_temp.run('authenticated', v_a, format('update public.social_profile set profile_bg = 0 where user_id = %L', v_a)));

    perform pg_temp.chk('1 Permisos', 'profile_bg = 6 rechazado por el check', 'ERR:23514',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set profile_bg = 6 where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'profile_bg = -1 rechazado por el check', 'ERR:23514',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set profile_bg = -1 where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'profile_bg = null rechazado (no nulo)', 'ERR:23502',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set profile_bg = null where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'show_badges = null rechazado (no nulo)', 'ERR:23502',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set show_badges = null where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'show_stats = null y show_history = null rechazados', 'ERR:23502|ERR:23502',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set show_stats = null where user_id = %L', v_a)) || '|' ||
      pg_temp.run('authenticated', v_a, format('update public.social_profile set show_history = null where user_id = %L', v_a)));

    perform pg_temp.chk('1 Permisos', 'A NO cambia el perfil de B: el UPDATE toca 0 filas (RLS de dueño)', '0',
      pg_temp.scalar('authenticated', v_a, format('with u as (update public.social_profile set profile_bg = 2 where user_id = %L returning 1) select count(*)::text from u', v_b)));
    perform pg_temp.chk('1 Permisos', 'y B sigue con el fondo 0', '0',
      (select profile_bg::text from public.social_profile where user_id = v_b));
    perform pg_temp.chk('1 Permisos', 'A no puede LEER la fila de B (ni sus columnas nuevas)', '0',
      pg_temp.scalar('authenticated', v_a, format('select count(*)::text from public.social_profile where user_id = %L', v_b)));

    perform pg_temp.chk('1 Permisos', 'El cliente NO puede actualizar avatar_hidden', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set avatar_hidden = true where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'El cliente NO puede actualizar username', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set username = %L where user_id = %L', 'otro_nombre', v_a)));
    perform pg_temp.chk('1 Permisos', 'El cliente NO puede actualizar friend_code', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set friend_code = %L where user_id = %L', 'NIGHT-AAAAAA', v_a)));
    perform pg_temp.chk('1 Permisos', 'hide_progress sigue siendo actualizable (regresión de 0008)', 'ok',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set hide_progress = false where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'anon NO puede actualizar profile_bg', 'ERR:42501',
      pg_temp.run('anon', null, format('update public.social_profile set profile_bg = 1 where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'anon NO puede leer social_profile', 'ERR:42501',
      pg_temp.scalar('anon', null, 'select count(*)::text from public.social_profile'));
    perform pg_temp.chk('1 Permisos', 'Sin INSERT de las columnas nuevas (permiso denegado antes de cualquier otra comprobación)', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('insert into public.social_profile (user_id, username, profile_bg) values (%L, %L, 3)', v_a, 'otro_nombre')));
    perform pg_temp.chk('1 Permisos', 'Ni de show_badges', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('insert into public.social_profile (user_id, username, show_badges) values (%L, %L, false)', v_a, 'otro_nombre')));
  exception when others then
    perform pg_temp.chk('1', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 2. El perfil del amigo devuelve el fondo y los ojos
  -- ===================================================================================================
  begin
    perform pg_temp.chk('2 Perfil', 'Hacer amigas a A y B', 'ok', pg_temp.befriend(v_a, v_b, v_code_a, v_code_b));

    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('2 Perfil', 'Perfil sin editar: fondo 0 y los tres ojos abiertos', '0|true|true|true',
      pg_temp.nz(v ->> 'profile_bg') || '|' || pg_temp.nz(v ->> 'show_badges') || '|' || pg_temp.nz(v ->> 'show_stats') || '|' || pg_temp.nz(v ->> 'show_history'));
    perform pg_temp.chk('2 Perfil', 'y trae progress (objeto), stats e history', 'object|true|true',
      jsonb_typeof(v -> 'progress') || '|' || pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history'));
    perform pg_temp.chk('2 Perfil', 'Conserva el contrato de 0011: nombre, código, hidden y la clave de foto', 'tst0012_b|false|true',
      (v ->> 'username') || '|' || (v ->> 'hidden') || '|' || (pg_temp.has(v, 'avatar_key')));

    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set profile_bg = 3 where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('2 Perfil', 'B elige el fondo 3: A lo ve en el perfil de B', '3', pg_temp.nz(v ->> 'profile_bg'));
    perform pg_temp.chk('2 Perfil', 'y es un número (smallint), no un texto', 'number', jsonb_typeof(v -> 'profile_bg'));

    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_stats = false where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('2 Perfil', 'B cierra el ojo de Estadísticas: show_stats = false y el fondo no cambia', 'false|true|true|3',
      pg_temp.nz(v ->> 'show_stats') || '|' || pg_temp.nz(v ->> 'show_badges') || '|' || pg_temp.nz(v ->> 'show_history') || '|' || pg_temp.nz(v ->> 'profile_bg'));
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_stats = true where user_id = %L', v_b));
  exception when others then
    perform pg_temp.chk('2', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 3. Los apartados ocultos se omiten en el servidor
  -- ===================================================================================================
  begin
    -- Estadísticas
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_stats = false where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('3 Omitir', 'Sin ojo de Estadísticas: la clave stats está AUSENTE (no null)', 'false|true|true',
      pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history') || '|' || pg_temp.has(v, 'progress'));
    perform pg_temp.chk('3 Omitir', 'y el progreso sigue siendo un objeto', 'object', jsonb_typeof(v -> 'progress'));
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_stats = true where user_id = %L', v_b));

    -- Historial
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_history = false where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('3 Omitir', 'Sin ojo de Historial: la clave history está AUSENTE (no una lista vacía)', 'true|false',
      pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history'));
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_history = true where user_id = %L', v_b));

    -- Insignias (SOLO las insignias: el nivel y los contadores siguen)
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_badges = false where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('3 Omitir', 'Sin ojo de Insignias: progress SIGUE siendo un objeto (la clave existe) y hidden sigue false', 'object|true|false',
      jsonb_typeof(v -> 'progress') || '|' || pg_temp.has(v, 'progress') || '|' || (v ->> 'hidden'));
    perform pg_temp.chk('3 Omitir', 'y trae solo fighter_level, labs_count y viewed_count', 'fighter_level,labs_count,viewed_count',
      (select string_agg(k, ',' order by k) from jsonb_object_keys(v -> 'progress') k));
    perform pg_temp.chk('3 Omitir', 'sin los hechos de insignias (viewed_lessons, completed_labs, exam_passed, exam_perfect, best_streak AUSENTES)', 'false|false|false|false|false',
      pg_temp.has(v -> 'progress', 'viewed_lessons') || '|' || pg_temp.has(v -> 'progress', 'completed_labs') || '|' ||
      pg_temp.has(v -> 'progress', 'exam_passed') || '|' || pg_temp.has(v -> 'progress', 'exam_perfect') || '|' || pg_temp.has(v -> 'progress', 'best_streak'));
    perform pg_temp.chk('3 Omitir', 'y nada de eso quita stats ni history', 'true|true',
      pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history'));

    -- Los tres cerrados a la vez
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_stats = false, show_history = false where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('3 Omitir', 'Los tres ojos cerrados: progress solo con contadores (objeto), sin stats ni history; sigue el nombre, la racha y el fondo', 'object|false|false|tst0012_b|3',
      jsonb_typeof(v -> 'progress') || '|' || pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history') || '|' || (v ->> 'username') || '|' || pg_temp.nz(v ->> 'profile_bg'));
    perform pg_temp.chk('3 Omitir', 'y los tres interruptores se reportan en false', 'false|false|false',
      pg_temp.nz(v ->> 'show_badges') || '|' || pg_temp.nz(v ->> 'show_stats') || '|' || pg_temp.nz(v ->> 'show_history'));

    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_badges = true, show_stats = true, show_history = true where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('3 Omitir', 'Al abrir los ojos de nuevo todo vuelve', 'object|true|true',
      jsonb_typeof(v -> 'progress') || '|' || pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history'));
  exception when others then
    perform pg_temp.chk('3', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 4. Estadísticas del AMIGO (totales contra cualquier rival, sin el límite de 10)
  -- ===================================================================================================
  begin
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('4 Stats', 'B sin batallas: played, wins, losses y draws en 0 (nunca null, nunca se divide)', '0|0|0|0',
      pg_temp.nz(v -> 'stats' ->> 'played') || '|' || pg_temp.nz(v -> 'stats' ->> 'wins') || '|' || pg_temp.nz(v -> 'stats' ->> 'losses') || '|' || pg_temp.nz(v -> 'stats' ->> 'draws'));

    -- 5 batallas ENTRE A y B. Resultado de A (de la más nueva a la más vieja): loss, win, draw, win, win.
    -- Resultado de B (la inversa): win, loss, draw, loss, loss.
    perform pg_temp.mk_battle(v_a, v_b, 'loss', 'win',  interval '1 day');
    perform pg_temp.mk_battle(v_a, v_b, 'win',  'loss', interval '2 days');
    perform pg_temp.mk_battle(v_a, v_b, 'draw', 'draw', interval '3 days');
    perform pg_temp.mk_battle(v_a, v_b, 'win',  'loss', interval '4 days');
    perform pg_temp.mk_battle(v_a, v_b, 'win',  'loss', interval '5 days');

    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('4 Stats', 'Con 5 batallas entre ellos: B jugó 5, ganó 1, perdió 3 y empató 1 (se ve DESDE B, no desde A)', '5|1|3|1',
      pg_temp.nz(v -> 'stats' ->> 'played') || '|' || pg_temp.nz(v -> 'stats' ->> 'wins') || '|' || pg_temp.nz(v -> 'stats' ->> 'losses') || '|' || pg_temp.nz(v -> 'stats' ->> 'draws'));

    -- 13 batallas más de B contra OTRAS personas (solo existe su fila: 8 victorias, 3 derrotas, 2 empates). Hace más de 10 días.
    for i in 1..8 loop
      perform pg_temp.mk_battle(v_a, v_b, null, 'win', make_interval(days => 10 + i));
    end loop;
    for i in 1..3 loop
      perform pg_temp.mk_battle(v_a, v_b, null, 'loss', make_interval(days => 20 + i));
    end loop;
    for i in 1..2 loop
      perform pg_temp.mk_battle(v_a, v_b, null, 'draw', make_interval(days => 30 + i));
    end loop;
    -- Dos batallas donde solo existe la fila de A (no cuentan para B)
    perform pg_temp.mk_battle(v_a, v_b, 'win', null, interval '40 days');
    perform pg_temp.mk_battle(v_a, v_b, 'win', null, interval '41 days');

    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('4 Stats', 'Con 18 batallas de B (más de 10) el conteo NO se corta en 10 y suma todo: 18|9|6|3', '18|9|6|3',
      pg_temp.nz(v -> 'stats' ->> 'played') || '|' || pg_temp.nz(v -> 'stats' ->> 'wins') || '|' || pg_temp.nz(v -> 'stats' ->> 'losses') || '|' || pg_temp.nz(v -> 'stats' ->> 'draws'));
    perform pg_temp.chk('4 Stats', 'Las estadísticas no dependen del rival: incluyen las 13 batallas sin fila de A, y las 2 filas solo de A no se suman', 'true',
      ((v -> 'stats' ->> 'played')::int = (select count(*) from public.battle_results where user_id = v_b))::text);
    perform pg_temp.chk('4 Stats', 'played = wins + losses + draws', 'true',
      ((v -> 'stats' ->> 'played')::int = (v -> 'stats' ->> 'wins')::int + (v -> 'stats' ->> 'losses')::int + (v -> 'stats' ->> 'draws')::int)::text);
    perform pg_temp.chk('4 Stats', 'Los números son enteros JSON (no texto)', 'number|number|number|number',
      jsonb_typeof(v -> 'stats' -> 'played') || '|' || jsonb_typeof(v -> 'stats' -> 'wins') || '|' || jsonb_typeof(v -> 'stats' -> 'losses') || '|' || jsonb_typeof(v -> 'stats' -> 'draws'));
  exception when others then
    perform pg_temp.chk('4', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 5. Historial entre los dos: máximo 3, de la más nueva a la más vieja, solo batallas compartidas
  -- ===================================================================================================
  begin
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('5 Historial', 'Hay 5 batallas compartidas pero salen solo 3', '3', jsonb_array_length(v -> 'history')::text);
    perform pg_temp.chk('5 Historial', 'Vistas desde A y de la más nueva a la más vieja: loss, win, draw', 'loss,win,draw', pg_temp.hist(v));
    perform pg_temp.chk('5 Historial', 'Las fechas bajan (la primera es la más nueva)', 'true',
      ((v -> 'history' -> 0 ->> 'finished_at')::timestamptz > (v -> 'history' -> 1 ->> 'finished_at')::timestamptz
       and (v -> 'history' -> 1 ->> 'finished_at')::timestamptz > (v -> 'history' -> 2 ->> 'finished_at')::timestamptz)::text);
    perform pg_temp.chk('5 Historial', 'Cada fila lleva solo result y finished_at', 'finished_at,result',
      (select string_agg(k, ',' order by k) from jsonb_object_keys(v -> 'history' -> 0) k));

    -- Visto desde B (la inversa): B ve a A como amigo
    v := pg_temp.f(v_b, 'profile', v_code_a);
    perform pg_temp.chk('5 Historial', 'Desde B, el historial con A es la inversa: win, loss, draw', 'win,loss,draw', pg_temp.hist(v));
    perform pg_temp.chk('5 Historial', 'y las estadísticas de A (el amigo visto por B) cuentan sus 5 + 2 batallas: 7|5|1|1', '7|5|1|1',
      pg_temp.nz(v -> 'stats' ->> 'played') || '|' || pg_temp.nz(v -> 'stats' ->> 'wins') || '|' || pg_temp.nz(v -> 'stats' ->> 'losses') || '|' || pg_temp.nz(v -> 'stats' ->> 'draws'));

    -- Con solo 2 batallas compartidas, salen 2
    delete from public.battle_results
     where battle_id in (select battle_id from public.battle_results where user_id = v_a and result in ('draw') );
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('5 Historial', 'Una batalla compartida menos (la del empate): el historial sigue en 3 con la 4.ª más nueva', '3',
      jsonb_array_length(v -> 'history')::text);
    perform pg_temp.chk('5 Historial', 'y ahora es loss, win, win', 'loss,win,win', pg_temp.hist(v));
  exception when others then
    perform pg_temp.chk('5', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 6. hide_progress sigue mandando sobre insignias y progreso, y NO sobre estadísticas ni historial
  -- ===================================================================================================
  begin
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set hide_progress = true where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6 Ocultar progreso', 'Con hide_progress: hidden true y progress null (la clave existe)', 'true|null|true',
      (v ->> 'hidden') || '|' || jsonb_typeof(v -> 'progress') || '|' || pg_temp.has(v, 'progress'));
    perform pg_temp.chk('6 Ocultar progreso', 'pero stats e history siguen (solo dependen de su propio ojo)', 'true|true',
      pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history'));
    perform pg_temp.chk('6 Ocultar progreso', 'y el show_badges sigue en true', 'true', pg_temp.nz(v ->> 'show_badges'));

    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_badges = false, show_stats = false, show_history = false where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6 Ocultar progreso', 'hide_progress + los tres ojos cerrados: nada con datos', 'true|null|false|false',
      (v ->> 'hidden') || '|' || jsonb_typeof(v -> 'progress') || '|' || pg_temp.has(v, 'stats') || '|' || pg_temp.has(v, 'history'));

    -- hide_progress = true con show_badges = true: progress sigue null (hide_progress manda sobre todo el progreso)
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_badges = true where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6 Ocultar progreso', 'hide_progress = true aunque show_badges sea true: progress null y la clave existe', 'true|null|true|true',
      (v ->> 'hidden') || '|' || jsonb_typeof(v -> 'progress') || '|' || pg_temp.has(v, 'progress') || '|' || pg_temp.nz(v ->> 'show_badges'));

    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set hide_progress = false, show_badges = true, show_stats = true, show_history = true where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6 Ocultar progreso', 'Sin hide_progress y con el ojo de Insignias abierto, progress trae sus 8 campos', 'object|8|true',
      jsonb_typeof(v -> 'progress') || '|' || (select count(*)::text from jsonb_object_keys(v -> 'progress')) || '|' || pg_temp.has(v -> 'progress', 'fighter_level'));
    perform pg_temp.chk('6 Ocultar progreso', 'Los campos de BadgeFacts más los tres del bloque base', 'best_streak,completed_labs,exam_passed,exam_perfect,fighter_level,labs_count,viewed_count,viewed_lessons',
      (select string_agg(k, ',' order by k) from jsonb_object_keys(v -> 'progress') k));
  exception when others then
    perform pg_temp.chk('6', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 6b. Los contadores (viewed_count, labs_count) coinciden con las filas reales de la cuenta de prueba
  --            B, con el ojo de Insignias cerrado y abierto. Solo se borran las filas que el guion insertó.
  -- ===================================================================================================
  declare
    v_les_prev text[];
    v_lab_prev text[];
  begin
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_badges = false where user_id = %L', v_b));

    -- Con lo que B tenga hoy (normalmente 0 y 0 en una cuenta de prueba limpia)
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6b Contadores', 'Sin tocar nada, los contadores son el número real de filas de B',
      (select count(*) from public.lesson_progress where user_id = v_b)::text || '|' || (select count(*) from public.lab_progress where user_id = v_b)::text,
      pg_temp.nz(v -> 'progress' ->> 'viewed_count') || '|' || pg_temp.nz(v -> 'progress' ->> 'labs_count'));
    perform pg_temp.chk('6b Contadores', 'y son números (no texto ni null)', 'number|number',
      jsonb_typeof(v -> 'progress' -> 'viewed_count') || '|' || jsonb_typeof(v -> 'progress' -> 'labs_count'));

    -- Más de 0: se insertan 3 lecciones y 2 labs (sin duplicar lo que B ya tenga)
    v_les_prev := coalesce((select array_agg(topic) from public.lesson_progress where user_id = v_b), '{}');
    v_lab_prev := coalesce((select array_agg(lab) from public.lab_progress where user_id = v_b), '{}');
    insert into public.lesson_progress (user_id, topic)
      select v_b, x from unnest(array['CONTRASENAS', 'PHISHING', 'MALWARE']) x on conflict do nothing;
    insert into public.lab_progress (user_id, lab)
      select v_b, x from unnest(array['CONTRASENAS', 'PHISHING']) x on conflict do nothing;

    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6b Contadores', 'Con el ojo de Insignias cerrado, los contadores reflejan las filas reales (y son más de 0)',
      (select count(*) from public.lesson_progress where user_id = v_b)::text || '|' || (select count(*) from public.lab_progress where user_id = v_b)::text || '|true|true',
      pg_temp.nz(v -> 'progress' ->> 'viewed_count') || '|' || pg_temp.nz(v -> 'progress' ->> 'labs_count') || '|' ||
      ((v -> 'progress' ->> 'viewed_count')::int >= 3)::text || '|' || ((v -> 'progress' ->> 'labs_count')::int >= 2)::text);
    perform pg_temp.chk('6b Contadores', 'y siguen sin viewed_lessons ni completed_labs', 'false|false',
      pg_temp.has(v -> 'progress', 'viewed_lessons') || '|' || pg_temp.has(v -> 'progress', 'completed_labs'));

    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set show_badges = true where user_id = %L', v_b));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6b Contadores', 'Con el ojo abierto, los contadores valen lo mismo que el tamaño de las listas', 'true|true',
      ((v -> 'progress' ->> 'viewed_count')::int = jsonb_array_length(v -> 'progress' -> 'viewed_lessons'))::text || '|' ||
      ((v -> 'progress' ->> 'labs_count')::int = jsonb_array_length(v -> 'progress' -> 'completed_labs'))::text);

    -- Se quitan solo las filas que puso el guion
    delete from public.lesson_progress where user_id = v_b and not (topic = any (v_les_prev));
    delete from public.lab_progress where user_id = v_b and not (lab = any (v_lab_prev));
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set hide_progress = false, show_badges = true where user_id = %L', v_b));
  exception when others then
    perform pg_temp.chk('6b','EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 7. Regresión: avatar_hidden, bloqueo, no_found indistinguible, bad_action y list
  -- ===================================================================================================
  begin
    -- avatar_hidden
    update public.social_profile set avatar_hidden = true where user_id = v_b;
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('7 Regresión', 'avatar_hidden: avatar_key y avatar_version salen en null, y el fondo sigue', 'NULL|NULL|3',
      pg_temp.nz(v ->> 'avatar_key') || '|' || pg_temp.nz(v ->> 'avatar_version') || '|' || pg_temp.nz(v ->> 'profile_bg'));
    update public.social_profile set avatar_hidden = false where user_id = v_b;
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('7 Regresión', 'Sin avatar_hidden vuelve la clave de la foto', v_key_b::text, pg_temp.nz(v ->> 'avatar_key'));

    -- list sigue funcionando
    perform pg_temp.chk('7 Regresión', 'list: A ve a B entre sus amigos', 'tst0012_b',
      pg_temp.f(v_a, 'list') -> 'friends' -> 0 ->> 'username');

    -- bad_action sigue siendo excepción
    perform pg_temp.chk('7 Regresión', 'Una acción desconocida sigue lanzando bad_action', 'exception:bad_action', pg_temp.s(pg_temp.f(v_a, 'nope')));

    -- Inexistente y no-amigo: la misma respuesta
    perform pg_temp.chk('7 Regresión', 'Perfil de un código que no existe: not_found', 'error:not_found',
      pg_temp.s(pg_temp.f(v_a, 'profile', 'NIGHT-ZZZZZZ')));

    -- Bloqueo
    perform pg_temp.chk('7 Regresión', 'A bloquea a B', 'blocked', pg_temp.s(pg_temp.f(v_a, 'block', v_code_b)));
    perform pg_temp.chk('7 Regresión', 'Con el bloqueo A ya no tiene perfil de B: la MISMA respuesta que "no existe"', 'error:not_found',
      pg_temp.s(pg_temp.f(v_a, 'profile', v_code_b)));
    perform pg_temp.chk('7 Regresión', 'y B tampoco tiene el de A', 'error:not_found',
      pg_temp.s(pg_temp.f(v_b, 'profile', v_code_a)));
    perform pg_temp.chk('7 Regresión', 'La respuesta de un perfil bloqueado y la de uno inexistente son idénticas', 'true',
      (pg_temp.f(v_a, 'profile', v_code_b) = pg_temp.f(v_a, 'profile', 'NIGHT-ZZZZZZ'))::text);
    perform pg_temp.chk('7 Regresión', 'A desbloquea a B; la amistad NO vuelve (not_found hasta que se hagan amigos otra vez)', 'unblocked|error:not_found',
      pg_temp.s(pg_temp.f(v_a, 'unblock', v_code_b)) || '|' || pg_temp.s(pg_temp.f(v_a, 'profile', v_code_b)));
  exception when others then
    perform pg_temp.chk('7', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- LIMPIEZA: borra todo lo que creó el guion
  -- ===================================================================================================
  perform pg_temp.reset_social(v_a, v_b);
  delete from public.social_profile where user_id in (v_a, v_b);

  perform pg_temp.chk('Limpieza', 'No queda ningún perfil de prueba', '0', (select count(*)::text from public.social_profile where user_id in (v_a, v_b)));
  perform pg_temp.chk('Limpieza', 'No queda ninguna batalla, resultado, amistad ni bloqueo de prueba', '0|0|0|0',
    (select count(*)::text from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.battle_results where user_id in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.social_blocks where blocker_id in (v_a, v_b) or blocked_id in (v_a, v_b)));
end
$escenarios$;

-- -----------------------------------------------------------------------------------------------------
-- 3. Resultado: la primera fila es el RESUMEN (debe decir "0 fallas"). Si hay FALLA, mira el bloque y el paso.
-- -----------------------------------------------------------------------------------------------------

select n, bloque, paso, esperado, obtenido, resultado
from (
  select 0 as n,
         'RESUMEN'::text as bloque,
         (count(*) filter (where not ok))::text || ' fallas de ' || count(*)::text || ' comprobaciones' as paso,
         '0'::text as esperado,
         (count(*) filter (where not ok))::text as obtenido,
         case when count(*) filter (where not ok) = 0 then 'OK' else 'FALLA' end as resultado
  from pg_temp.t_res
  union all
  select n, bloque, paso, esperado, obtenido, case when ok then 'OK' else 'FALLA' end
  from pg_temp.t_res
) x
order by n;

-- -----------------------------------------------------------------------------------------------------
-- 4. Limpieza de emergencia (SOLO si el bloque DO se interrumpió a medias; normalmente no hace falta porque es
--    una sola sentencia atómica y se deshace sola). Reemplaza los dos ids y quita los comentarios.
-- -----------------------------------------------------------------------------------------------------
-- delete from public.battles where player1_id in ('<ID_A>', '<ID_B>') or player2_id in ('<ID_A>', '<ID_B>');
-- delete from public.friendships where user_low in ('<ID_A>', '<ID_B>') or user_high in ('<ID_A>', '<ID_B>');
-- delete from public.social_blocks where blocker_id in ('<ID_A>', '<ID_B>') or blocked_id in ('<ID_A>', '<ID_B>');
-- delete from public.social_rate_limit where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.social_profile where user_id in ('<ID_A>', '<ID_B>');
