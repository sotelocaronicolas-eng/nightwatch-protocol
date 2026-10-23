-- =====================================================================================================
-- NightWatch Protocol - Guion de escenarios del script 0011 (reportar y bloquear)
--
-- NO se ejecuta desde la app ni desde el repositorio: lo corres TÚ a mano en el SQL Editor de Supabase,
-- DESPUÉS de aplicar supabase/migrations/0011_reportes_bloqueos.sql (que sigue en BORRADOR: este guion NO se ha
-- ejecutado todavía). Vive en supabase/verificacion/ (no en migrations/) porque NO es un script del esquema.
--
-- CÓMO USARLO
--   1. Usa DOS cuentas de prueba de Authentication > Users (las mismas del guion de 0008 sirven). Copia sus ids.
--      Sin perfil de Social ni uso previo: el guion crea sus perfiles y los borra al terminar.
--   2. En la sección 2 reemplaza las dos líneas marcadas con "REEMPLAZA" (v_a y v_b).
--   3. Ejecuta TODO el archivo de una vez, con el rol postgres del SQL Editor.
--   4. El resultado es una tabla: una fila por comprobación con lo esperado, lo obtenido y OK o FALLA. La primera
--      fila resume (debe decir "0 fallas"). Si algo falla, la columna bloque dice dónde mirar.
--   5. Después, vuelve a correr supabase/verificacion/0008_escenarios.sql completo: debe seguir dando 219 de 219
--      (0011 reemplaza funciones de 0008 y de 0010, y ese guion es la prueba de que no cambió su lógica).
--
-- QUÉ HACE Y QUÉ GARANTIZA (igual que el guion de 0008)
--   * Todo el escenario es UNA sola sentencia (un bloque DO): es atómica. Si el guion lanza un error inesperado se
--     deshace TODO y no queda nada que limpiar.
--   * Cuando termina bien, BORRA lo que creó (perfiles, amistades, parejas, retos, batallas, reportes, bloqueos,
--     archivos de prueba en storage.objects) y RESTAURA el banco de preguntas, los retos de ulti y la curva de XP.
--   * TODAS las comprobaciones y TODOS los cambios se limitan a las dos cuentas de prueba (v_a y v_b): el resultado
--     no depende de que existan reportes, bloqueos, parejas o amistades de personas reales, y no toca sus filas.
--     Única excepción inevitable: la partida rápida usa la cola global; si una persona real entra a la cola
--     en esos segundos podría emparejarse con una cuenta de prueba (probabilidad muy baja; correr sin tráfico).
--   * La sesión se simula con set_config('request.jwt.claims', ...) y las llamadas se hacen con el rol authenticated
--     (o anon): así se prueban también los permisos reales.
--
-- QUÉ CUBRE
--   1 Permisos: tablas nuevas cerradas, sx_is_blocked sin EXECUTE, avatar_hidden no escribible por el cliente.
--   2 Regresión de 0008 en pequeño: búsqueda, invitación, enumeración, pareja, reto, partida rápida y eliminar amigo.
--   3 Reportar: formato, motivos, nota de 200, barras en la nota, uno mismo = inexistente, límite de 10, sin efecto
--     sobre quien reporta.
--   4 Bloquear y desbloquear: efectos (amistad, pareja y retos en los dos sentidos), búsqueda, invitaciones, pareja,
--     reto y aceptaciones en AMBOS sentidos con la misma respuesta que "no existe", partida rápida, límite.
--   5 Una batalla en curso NO se termina al bloquear.
--   6 avatar_hidden: las claves salen en null en cada función, subir queda rechazado (función y Storage), borrar sigue
--     permitido y al levantar la ocultación se puede subir otra vez.
--
-- QUÉ NO CUBRE
--   * Los 50 amigos, las 20 solicitudes y las 5 parejas pendientes (necesitan más cuentas).
--   * Que un tercero (una persona que no es A ni B) no vea los bloqueos de A y B: necesita una tercera cuenta.
--   * El flujo de Storage real (subir un archivo de verdad): se prueban las POLÍTICAS insertando y actualizando filas
--     de storage.objects, no los bytes. El orden "borrar archivo y luego social_set_avatar(false)" es de la app.
--
-- Cada bloque lleva en un comentario el resultado esperado de cada paso.
-- =====================================================================================================

-- -----------------------------------------------------------------------------------------------------
-- 1. Apoyo (tabla de resultados y funciones temporales; viven solo en esta sesión: pg_temp)
-- -----------------------------------------------------------------------------------------------------

drop table if exists pg_temp.t_res;
drop table if exists pg_temp.t_saved_q;
drop table if exists pg_temp.t_saved_u;
drop table if exists pg_temp.t_saved_curve;

create temp table t_res (
  n         serial primary key,
  bloque    text,
  paso      text,
  esperado  text,
  obtenido  text,
  ok        boolean
);
create temp table t_saved_q (id integer);
create temp table t_saved_u (id integer);
create temp table t_saved_curve (level integer, min_xp integer);

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

-- Atajos para las funciones públicas (siempre con el rol authenticated).
create or replace function pg_temp.f(p_user uuid, p_action text, p_arg text default null)
returns jsonb language sql as $$
  select pg_temp.call('authenticated', p_user, format('select public.social_friends(%L, %L)', p_action, p_arg))
$$;
create or replace function pg_temp.p(p_user uuid, p_action text, p_arg text default null)
returns jsonb language sql as $$
  select pg_temp.call('authenticated', p_user, format('select public.social_pairs(%L, %L)', p_action, p_arg))
$$;
create or replace function pg_temp.l(p_user uuid, p_action text, p_arg text default null)
returns jsonb language sql as $$
  select pg_temp.call('authenticated', p_user, format('select public.battle_lobby(%L, %L)', p_action, p_arg))
$$;
create or replace function pg_temp.sy(p_user uuid, p_battle uuid)
returns jsonb language sql as $$
  select pg_temp.call('authenticated', p_user, format('select public.battle_sync(%L::uuid)', p_battle))
$$;
create or replace function pg_temp.av(p_user uuid, p_has_photo boolean)
returns jsonb language sql as $$
  select pg_temp.call('authenticated', p_user, format('select public.social_set_avatar(%L::boolean)', p_has_photo))
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

-- Un campo de texto de un jsonb; 'NULL' si falta o es null (para comprobar que una clave de foto salió en null).
create or replace function pg_temp.nz(p_value text)
returns text language sql as $$
  select coalesce(p_value, 'NULL')
$$;

-- Borra TODO lo de Social de las dos cuentas de prueba (no toca su perfil): batallas, cola, retos, parejas, amistades,
-- reportes, bloqueos y contadores de ritmo.
create or replace function pg_temp.reset_social(p_a uuid, p_b uuid)
returns void language plpgsql as $$
begin
  delete from public.battles where player1_id in (p_a, p_b) or player2_id in (p_a, p_b);
  delete from public.matchmaking_queue where user_id in (p_a, p_b);
  delete from public.battle_challenges where challenger_id in (p_a, p_b) or challenged_id in (p_a, p_b);
  delete from public.pair_streaks where user_low in (p_a, p_b) or user_high in (p_a, p_b);
  delete from public.friendships where user_low in (p_a, p_b) or user_high in (p_a, p_b);
  delete from public.social_reports where reporter_id in (p_a, p_b) or reported_id in (p_a, p_b);
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

-- Empareja a A y B por la partida rápida y devuelve el id de la batalla (las dos cuentas con el mismo nivel).
create or replace function pg_temp.new_battle(p_a uuid, p_b uuid)
returns uuid language plpgsql as $$
declare
  v jsonb;
  b uuid;
begin
  delete from public.battles where player1_id in (p_a, p_b) or player2_id in (p_a, p_b);
  delete from public.matchmaking_queue where user_id in (p_a, p_b);
  delete from public.battle_challenges where challenger_id in (p_a, p_b) or challenged_id in (p_a, p_b);
  perform pg_temp.l(p_a, 'quick_join');
  v := pg_temp.l(p_b, 'quick_join');
  b := (v ->> 'battle_id')::uuid;
  -- now() es la hora de INICIO de la transacción (todo el guion es una sola): se fijan los latidos a la hora real
  update public.battles set last_seen1 = clock_timestamp(), last_seen2 = clock_timestamp() where id = b;
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
  v2         jsonb;
  t          text;
  i          integer;
  n          integer;
  bid        uuid;
  v_code_a   text;
  v_code_b   text;
  v_key_a    uuid;
  v_key_b    uuid;
  v_key_b2   uuid;
  v_long     text;
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
  if to_regclass('public.social_blocks') is null or to_regclass('public.social_reports') is null then
    raise exception 'Falta aplicar 0011_reportes_bloqueos.sql: no existen social_blocks ni social_reports.';
  end if;

  -- Restos de una corrida anterior (solo datos de Social de estas dos cuentas)
  perform pg_temp.reset_social(v_a, v_b);
  delete from storage.objects
   where bucket_id = 'social-avatars'
     and name in (select avatar_key::text || '.jpg' from public.social_profile where user_id in (v_a, v_b));
  delete from public.social_profile where user_id in (v_a, v_b);

  -- Los perfiles se crean como dueño (el guion de 0008 ya prueba que la app los crea por INSERT de columna)
  insert into public.social_profile (user_id, username) values (v_a, 'tst0011_a'), (v_b, 'tst0011_b');
  select friend_code into v_code_a from public.social_profile where user_id = v_a;
  select friend_code into v_code_b from public.social_profile where user_id = v_b;

  -- Banco mínimo temporal para las batallas: se apartan los datos reales (si los hay) y se insertan los de prueba.
  insert into pg_temp.t_saved_q select id from public.battle_questions where active;
  update public.battle_questions set active = false where id in (select id from pg_temp.t_saved_q);
  insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct)
  select 'CONTRASENAS', tr, 'TEST-0011 pregunta t' || tr || '-' || q, 'opcion-A', 'opcion-B', 'opcion-C', 'opcion-D', 'A'
  from generate_series(1, 3) tr, generate_series(1, 4) q;

  insert into pg_temp.t_saved_u select id from public.ulti_challenges where active;
  update public.ulti_challenges set active = false where id in (select id from pg_temp.t_saved_u);
  insert into public.ulti_challenges (kind, topic, objective_title, expected_command) values
    ('attack',  'FIREWALL', 'TEST-0011 ataque',  'test-atk one'),
    ('defense', 'MALWARE',  'TEST-0011 defensa', 'test-def two');

  insert into pg_temp.t_saved_curve select level, min_xp from public.fighter_level_curve;
  delete from public.fighter_level_curve;
  insert into public.fighter_level_curve (level, min_xp) values (1, 0), (2, 100), (3, 200);

  -- ===================================================================================================
  -- BLOQUE 1. Permisos y estructura
  -- ===================================================================================================
  begin
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede leer social_reports', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.social_reports'));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede leer social_blocks', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.social_blocks'));
    perform pg_temp.chk('1 Permisos', 'anon NO puede leer social_reports', 'ERR:42501',
      pg_temp.scalar('anon', null, 'select count(*)::text from public.social_reports'));
    perform pg_temp.chk('1 Permisos', 'anon NO puede leer social_blocks', 'ERR:42501',
      pg_temp.scalar('anon', null, 'select count(*)::text from public.social_blocks'));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede insertar un reporte a mano', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('insert into public.social_reports (reporter_id, reported_id, reason) values (%L, %L, %L)', v_a, v_b, 'other')));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede insertar un bloqueo a mano', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('insert into public.social_blocks (blocker_id, blocked_id) values (%L, %L)', v_a, v_b)));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede ejecutar la función interna sx_is_blocked', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, format('select public.sx_is_blocked(%L::uuid, %L::uuid)::text', v_a, v_b)));
    perform pg_temp.chk('1 Permisos', 'anon NO puede ejecutar social_friends (tampoco las acciones nuevas)', 'ERR:42501',
      pg_temp.scalar('anon', null, 'select public.social_friends(''report'', ''x|other|'')::text'));
    perform pg_temp.chk('1 Permisos', 'El cliente NO puede actualizar avatar_hidden', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('update public.social_profile set avatar_hidden = true where user_id = %L', v_a)));
    perform pg_temp.chk('1 Permisos', 'El cliente NO puede crear su perfil con avatar_hidden', 'ERR:42501',
      pg_temp.run('authenticated', v_a, format('insert into public.social_profile (user_id, username, avatar_hidden) values (%L, %L, true)', v_a, 'otro_nombre')));
    perform pg_temp.chk('1 Permisos', 'Pero SÍ puede leer su propio avatar_hidden (false por defecto)', 'false',
      pg_temp.scalar('authenticated', v_a, 'select avatar_hidden::text from public.social_profile'));
    update public.social_profile set avatar_hidden = true where user_id = v_a;
    t := (select avatar_hidden::text from public.social_profile where user_id = v_a);
    update public.social_profile set avatar_hidden = false where user_id = v_a;
    perform pg_temp.chk('1 Permisos', 'El dueño (aquí, postgres) sí puede cambiarlo y volver a false', 'true|false',
      t || '|' || (select avatar_hidden::text from public.social_profile where user_id = v_a));

    perform pg_temp.chk('1 Excepciones', 'Sin sesión: no_session (acción nueva)', 'exception:no_session', pg_temp.s(pg_temp.f(null, 'blocks')));
    perform pg_temp.chk('1 Excepciones', 'Sin Social habilitado: social_not_enabled (acción nueva)', 'exception:social_not_enabled',
      pg_temp.s(pg_temp.f(gen_random_uuid(), 'blocks')));
    perform pg_temp.chk('1 Excepciones', 'Una acción fuera de la lista sigue siendo bad_action', 'exception:bad_action', pg_temp.s(pg_temp.f(v_a, 'nope')));
    perform pg_temp.chk('1 Excepciones', 'blocks vacío al empezar', '0', jsonb_array_length(pg_temp.f(v_a, 'blocks') -> 'blocked')::text);
  exception when others then
    perform pg_temp.chk('1', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 2. Regresión de 0008 en pequeño (lo que 0011 reemplazó sigue haciendo lo mismo)
  -- ===================================================================================================
  begin
    perform pg_temp.reset_social(v_a, v_b);

    v := pg_temp.f(v_a, 'search', 'TST0011_B');
    perform pg_temp.chk('2 Regresión', 'Búsqueda por nombre exacto (otras mayúsculas): ok, relación none y con el código', 'ok|none|' || v_code_b,
      pg_temp.s(v) || '|' || (v ->> 'relation') || '|' || (v ->> 'friend_code'));
    perform pg_temp.chk('2 Regresión', 'Sin búsqueda por prefijo', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'search', 'tst0011')));
    perform pg_temp.chk('2 Regresión', 'Buscarse a uno mismo da lo MISMO que un inexistente', 'true',
      (pg_temp.f(v_a, 'search', 'tst0011_a') = pg_temp.f(v_a, 'search', 'zz0011_nobody'))::text);
    perform pg_temp.chk('2 Regresión', 'Consulta de 2 caracteres: bad_query', 'exception:bad_query', pg_temp.s(pg_temp.f(v_a, 'search', 'ab')));
    perform pg_temp.chk('2 Regresión', 'accept: inexistente = sin relación', 'true',
      (pg_temp.f(v_a, 'accept', 'zz0011_nobody') = pg_temp.f(v_a, 'accept', v_code_b))::text);
    perform pg_temp.chk('2 Regresión', 'invite a un inexistente: not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'invite', 'zz0011_nobody')));
    perform pg_temp.chk('2 Regresión', 'invite a uno mismo: invalid_target', 'error:invalid_target', pg_temp.s(pg_temp.f(v_a, 'invite', 'tst0011_a')));
    perform pg_temp.chk('2 Regresión', 'Pareja a un no-amigo = a un inexistente', 'true',
      (pg_temp.p(v_a, 'invite', 'zz0011_nobody') = pg_temp.p(v_a, 'invite', v_code_b))::text);
    perform pg_temp.chk('2 Regresión', 'Reto a un no-amigo = a un inexistente', 'true',
      (pg_temp.l(v_a, 'challenge_send', 'zz0011_nobody') = pg_temp.l(v_a, 'challenge_send', v_code_b))::text);

    perform pg_temp.chk('2 Regresión', 'A invita a B', 'sent', pg_temp.s(pg_temp.f(v_a, 'invite', 'tst0011_b')));
    perform pg_temp.chk('2 Regresión', 'Invitar otra vez: request_exists', 'error:request_exists', pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('2 Regresión', 'B invita mientras A ya invitó: request_incoming', 'error:request_incoming', pg_temp.s(pg_temp.f(v_b, 'invite', v_code_a)));
    perform pg_temp.chk('2 Regresión', 'B ve 1 solicitud recibida y A 1 enviada', '1|1',
      jsonb_array_length(pg_temp.f(v_b, 'requests') -> 'received')::text || '|' || jsonb_array_length(pg_temp.f(v_a, 'requests') -> 'sent')::text);
    perform pg_temp.chk('2 Regresión', 'B acepta', 'accepted', pg_temp.s(pg_temp.f(v_b, 'accept', v_code_a)));
    perform pg_temp.chk('2 Regresión', 'Invitar a quien ya es amigo: already_friends', 'error:already_friends', pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('2 Regresión', 'A tiene 1 amigo: tst0011_b', 'tst0011_b',
      pg_temp.f(v_a, 'list') -> 'friends' -> 0 ->> 'username');
    perform pg_temp.chk('2 Regresión', 'El perfil del amigo responde con su nombre', 'tst0011_b', pg_temp.f(v_a, 'profile', v_code_b) ->> 'username');

    perform pg_temp.chk('2 Regresión', 'A invita a B a una racha de pareja', 'sent', pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('2 Regresión', 'Invitar otra vez: pair_exists', 'error:pair_exists', pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('2 Regresión', 'B acepta la pareja', 'active', pg_temp.s(pg_temp.p(v_b, 'accept', v_code_a)));
    perform pg_temp.chk('2 Regresión', 'La pareja aparece activa en el resumen', 'active', pg_temp.p(v_a, 'overview') -> 'pairs' -> 0 ->> 'status');

    v := pg_temp.l(v_a, 'challenge_send', v_code_b);
    perform pg_temp.chk('2 Regresión', 'A reta a B: sent|120', 'sent|120', (v ->> 'status') || '|' || (v ->> 'seconds_left'));
    perform pg_temp.chk('2 Regresión', 'B ve 1 reto recibido', '1', jsonb_array_length(pg_temp.l(v_b, 'challenges') -> 'received')::text);
    v := pg_temp.l(v_b, 'challenge_accept', v_code_a);
    perform pg_temp.chk('2 Regresión', 'B acepta el reto: se crea la batalla', 'matched', pg_temp.s(v));
    perform pg_temp.chk('2 Regresión', 'La batalla es de modo friend', 'friend',
      (select mode from public.battles where id = (v ->> 'battle_id')::uuid));
    delete from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b);
    delete from public.battle_challenges where challenger_id in (v_a, v_b) or challenged_id in (v_a, v_b);

    bid := pg_temp.new_battle(v_a, v_b);
    perform pg_temp.chk('2 Regresión', 'Partida rápida con el mismo nivel: se emparejan de inmediato', 'quick|active',
      (select mode || '|' || status from public.battles where id = bid));
    delete from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b);
    delete from public.matchmaking_queue where user_id in (v_a, v_b);

    perform pg_temp.l(v_a, 'challenge_send', v_code_b);
    perform pg_temp.chk('2 Regresión', 'A elimina a B', 'removed', pg_temp.s(pg_temp.f(v_a, 'remove', v_code_b)));
    perform pg_temp.chk('2 Regresión', 'Termina la pareja (sin filas de A y B)', '0',
      (select count(*)::text from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b)));
    perform pg_temp.chk('2 Regresión', 'El reto pendiente queda cancelado', 'cancelled',
      (select status from public.battle_challenges where challenger_id in (v_a, v_b) or challenged_id in (v_a, v_b) order by id desc limit 1));
    perform pg_temp.chk('2 Regresión', 'La lista de amigos queda vacía', '0', jsonb_array_length(pg_temp.f(v_a, 'list') -> 'friends')::text);
    perform pg_temp.chk('2 Regresión', 'Eliminar NO crea un bloqueo', '0',
      (select count(*)::text from public.social_blocks where blocker_id in (v_a, v_b) or blocked_id in (v_a, v_b)));
  exception when others then
    perform pg_temp.chk('2', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 3. Reportar
  -- ===================================================================================================
  begin
    perform pg_temp.reset_social(v_a, v_b);
    perform pg_temp.chk('3 Reportes', 'Preparación: A y B son amigos', 'ok', pg_temp.befriend(v_a, v_b, v_code_a, v_code_b));

    perform pg_temp.chk('3 Reportes', 'A reporta a B por nombre con motivo y nota', 'reported',
      pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|harassment|Me insulta en el chat')));
    perform pg_temp.chk('3 Reportes', 'El reporte guarda motivo, nota y estado open', 'harassment|Me insulta en el chat|open',
      (select reason || '|' || coalesce(note, '(null)') || '|' || status from public.social_reports
        where reporter_id = v_a and reported_id = v_b order by id desc limit 1));
    perform pg_temp.chk('3 Reportes', 'Por código y SIN nota (dos barras, nota vacía): reported', 'reported',
      pg_temp.s(pg_temp.f(v_a, 'report', v_code_b || '|other|')));
    perform pg_temp.chk('3 Reportes', 'La nota vacía se guarda como null', 'other|(null)',
      (select reason || '|' || coalesce(note, '(null)') from public.social_reports
        where reporter_id = v_a and reported_id = v_b order by id desc limit 1));
    perform pg_temp.chk('3 Reportes', 'La nota puede llevar barras (todo lo que sigue a la segunda)', 'reported',
      pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|offensive_name|a|b|c')));
    perform pg_temp.chk('3 Reportes', 'y se guarda entera', 'offensive_name|a|b|c',
      (select reason || '|' || note from public.social_reports where reporter_id = v_a and reported_id = v_b order by id desc limit 1));
    t := pg_temp.s(pg_temp.f(v_a, 'report', ' tst0011_b | other |  hola  '));
    perform pg_temp.chk('3 Reportes', 'Los espacios de los extremos se quitan en las tres partes', 'reported|other|hola',
      t || '|' || (select reason || '|' || note from public.social_reports where reporter_id = v_a and reported_id = v_b order by id desc limit 1));
    perform pg_temp.chk('3 Reportes', 'Motivo inappropriate_photo', 'reported', pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|inappropriate_photo|')));
    v_long := repeat('x', 200);
    perform pg_temp.chk('3 Reportes', 'Una nota de EXACTAMENTE 200 caracteres se acepta', 'reported',
      pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|other|' || v_long)));
    perform pg_temp.chk('3 Reportes', 'y se guarda con sus 200 caracteres', '200',
      (select char_length(note)::text from public.social_reports where reporter_id = v_a and reported_id = v_b order by id desc limit 1));

    -- El límite es de 10 al día y los intentos inválidos también cuentan: se reinicia el contador entre grupos
    delete from public.social_rate_limit where user_id in (v_a, v_b);
    perform pg_temp.chk('3 Reportes', 'Una nota de 201 caracteres: bad_report', 'error:bad_report',
      pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|other|' || v_long || 'y')));
    perform pg_temp.chk('3 Reportes', 'Sin la segunda barra: bad_report', 'error:bad_report', pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|harassment')));
    perform pg_temp.chk('3 Reportes', 'Motivo desconocido: bad_report', 'error:bad_report', pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|spam|x')));
    perform pg_temp.chk('3 Reportes', 'Motivo vacío: bad_report', 'error:bad_report', pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b||x')));
    perform pg_temp.chk('3 Reportes', 'Destino de 2 caracteres: bad_report', 'error:bad_report', pg_temp.s(pg_temp.f(v_a, 'report', 'ab|other|')));
    perform pg_temp.chk('3 Reportes', 'p_arg nulo: bad_report', 'error:bad_report', pg_temp.s(pg_temp.f(v_a, 'report', null)));
    perform pg_temp.chk('3 Reportes', 'p_arg de más de 300 caracteres: bad_report', 'error:bad_report',
      pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|other|' || repeat('x', 290))));

    delete from public.social_rate_limit where user_id in (v_a, v_b);
    perform pg_temp.chk('3 Reportes', 'Reportarse a uno mismo da lo MISMO que reportar a un inexistente', 'true',
      (pg_temp.f(v_a, 'report', 'tst0011_a|other|') = pg_temp.f(v_a, 'report', 'zz0011_nobody|other|'))::text);
    perform pg_temp.chk('3 Reportes', 'y es un resultado, no una excepción: not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_a|other|')));
    perform pg_temp.chk('3 Reportes', 'Solo se guardaron los 6 reportes válidos de A (ningún formato inválido ni a sí mismo)', '6',
      (select count(*)::text from public.social_reports where reporter_id = v_a));

    -- Reportar no cambia nada para quien reporta
    perform pg_temp.chk('3 Reportes', 'Tras reportar, A y B siguen siendo amigos', '1',
      jsonb_array_length(pg_temp.f(v_a, 'list') -> 'friends')::text);
    perform pg_temp.chk('3 Reportes', 'A no bloqueó a nadie por reportar', '0', jsonb_array_length(pg_temp.f(v_a, 'blocks') -> 'blocked')::text);
    perform pg_temp.chk('3 Reportes', 'A puede seguir buscando a B (relación friends)', 'friends', pg_temp.f(v_a, 'search', v_code_b) ->> 'relation');
    perform pg_temp.chk('3 Reportes', 'B no recibe ningún aviso del reporte: su lista de amigos sigue igual', '1',
      jsonb_array_length(pg_temp.f(v_b, 'list') -> 'friends')::text);
    perform pg_temp.chk('3 Reportes', 'B también puede reportar a A', 'reported', pg_temp.s(pg_temp.f(v_b, 'report', v_code_a || '|harassment|x')));
    perform pg_temp.chk('3 Reportes', 'El reporte de B es de B contra A', '1',
      (select count(*)::text from public.social_reports where reporter_id = v_b and reported_id = v_a));

    -- Límite de ritmo: 10 por día; los intentos inválidos también cuentan
    delete from public.social_reports where reporter_id in (v_a, v_b);
    delete from public.social_rate_limit where user_id in (v_a, v_b);
    n := 0;
    for i in 1..2 loop
      if pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|nope|')) = 'error:bad_report' then n := n + 1; end if;
    end loop;
    for i in 1..8 loop
      if pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|other|x')) = 'reported' then n := n + 1; end if;
    end loop;
    perform pg_temp.chk('3 Ritmo', '2 reportes inválidos y 8 válidos responden lo suyo (los inválidos también cuentan)', '10', n::text);
    perform pg_temp.chk('3 Ritmo', 'El reporte 11 lanza rate_limited', 'exception:rate_limited', pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|other|x')));
    perform pg_temp.chk('3 Ritmo', 'El contador conserva los 10', '10',
      (select count::text from public.social_rate_limit where user_id = v_a and action = 'report'));
    perform pg_temp.chk('3 Ritmo', 'Solo se guardaron los 8 válidos', '8', (select count(*)::text from public.social_reports where reporter_id = v_a));
    perform pg_temp.chk('3 Ritmo', 'El límite de A no afecta a B', 'reported', pg_temp.s(pg_temp.f(v_b, 'report', v_code_a || '|other|x')));
    update public.social_rate_limit set window_start = now() - interval '2 days' where user_id = v_a;
    perform pg_temp.chk('3 Ritmo', 'Con la ventana vencida (simulada) A puede reportar otra vez', 'reported',
      pg_temp.s(pg_temp.f(v_a, 'report', 'tst0011_b|other|x')));
    perform pg_temp.reset_social(v_a, v_b);
  exception when others then
    perform pg_temp.chk('3', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 4. Bloquear y desbloquear (efectos en los dos sentidos)
  -- ===================================================================================================
  begin
    perform pg_temp.reset_social(v_a, v_b);
    perform pg_temp.chk('4 Bloqueo', 'Preparación: amigos', 'ok', pg_temp.befriend(v_a, v_b, v_code_a, v_code_b));
    perform pg_temp.chk('4 Bloqueo', 'Preparación: pareja activa', 'active',
      case when pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)) = 'sent' then pg_temp.s(pg_temp.p(v_b, 'accept', v_code_a)) else 'no se pudo invitar' end);
    perform pg_temp.chk('4 Bloqueo', 'Preparación: A reta a B y B reta a A (dos retos pendientes)', 'sent|sent',
      pg_temp.s(pg_temp.l(v_a, 'challenge_send', v_code_b)) || '|' || pg_temp.s(pg_temp.l(v_b, 'challenge_send', v_code_a)));
    perform pg_temp.chk('4 Bloqueo', 'Antes del bloqueo hay 1 amistad, 1 pareja y 2 retos pendientes', '1|1|2',
      (select count(*)::text from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b)) || '|' ||
      (select count(*)::text from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b)) || '|' ||
      (select count(*)::text from public.battle_challenges where status = 'pending' and (challenger_id in (v_a, v_b) or challenged_id in (v_a, v_b))));

    -- A bloquea a B
    perform pg_temp.chk('4 Bloquear', 'A bloquea a B (por nombre)', 'blocked', pg_temp.s(pg_temp.f(v_a, 'block', 'tst0011_b')));
    v := pg_temp.f(v_a, 'blocks');
    perform pg_temp.chk('4 Bloquear', 'A ve 1 persona bloqueada: tst0011_b con su código', '1|tst0011_b|' || v_code_b,
      jsonb_array_length(v -> 'blocked')::text || '|' || (v -> 'blocked' -> 0 ->> 'username') || '|' || (v -> 'blocked' -> 0 ->> 'friend_code'));
    perform pg_temp.chk('4 Bloquear', 'B NO ve que lo bloquearon: su lista de bloqueados sigue vacía', '0',
      jsonb_array_length(pg_temp.f(v_b, 'blocks') -> 'blocked')::text);
    perform pg_temp.chk('4 Bloquear', 'La amistad se borró de la tabla', '0',
      (select count(*)::text from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b)));
    perform pg_temp.chk('4 Bloquear', 'La pareja de racha se borró', '0',
      (select count(*)::text from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b)));
    perform pg_temp.chk('4 Bloquear', 'Los dos retos pendientes (en los dos sentidos) quedaron cancelados', '2|0',
      (select count(*)::text from public.battle_challenges where status = 'cancelled' and challenger_id in (v_a, v_b) and challenged_id in (v_a, v_b)) || '|' ||
      (select count(*)::text from public.battle_challenges where status = 'pending' and (challenger_id in (v_a, v_b) or challenged_id in (v_a, v_b))));
    perform pg_temp.chk('4 Bloquear', 'Las listas de amigos de los dos quedan vacías', '0|0',
      jsonb_array_length(pg_temp.f(v_a, 'list') -> 'friends')::text || '|' || jsonb_array_length(pg_temp.f(v_b, 'list') -> 'friends')::text);
    perform pg_temp.chk('4 Bloquear', 'Bloquear otra vez es inofensivo', 'blocked|1',
      pg_temp.s(pg_temp.f(v_a, 'block', v_code_b)) || '|' ||
      (select count(*)::text from public.social_blocks where blocker_id = v_a and blocked_id = v_b));
    perform pg_temp.chk('4 Bloquear', 'Bloquearse a uno mismo da lo MISMO que bloquear a un inexistente', 'true',
      (pg_temp.f(v_a, 'block', 'tst0011_a') = pg_temp.f(v_a, 'block', 'zz0011_nobody'))::text);
    perform pg_temp.chk('4 Bloquear', 'y es not_found, no una excepción', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'block', 'tst0011_a')));
    perform pg_temp.chk('4 Bloquear', 'No quedó ningún bloqueo a sí mismo ni a un inexistente', '1',
      (select count(*)::text from public.social_blocks where blocker_id in (v_a, v_b)));

    -- Sin amistad: todo responde not_found, lo MISMO que "no existe", en los dos sentidos
    perform pg_temp.chk('4 Sentidos', 'A busca a B: not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'search', v_code_b)));
    perform pg_temp.chk('4 Sentidos', 'B busca a A (el bloqueado también): not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_b, 'search', v_code_a)));
    perform pg_temp.chk('4 Sentidos', 'La búsqueda bloqueada es IDÉNTICA a la de un inexistente (A y B)', 'true|true',
      (pg_temp.f(v_a, 'search', 'tst0011_b') = pg_temp.f(v_a, 'search', 'zz0011_nobody'))::text || '|' ||
      (pg_temp.f(v_b, 'search', 'tst0011_a') = pg_temp.f(v_b, 'search', 'zz0011_nobody'))::text);
    perform pg_temp.chk('4 Sentidos', 'Invitar a amistad (A a B y B a A): not_found, igual que un inexistente', 'true|true|error:not_found|error:not_found',
      (pg_temp.f(v_a, 'invite', v_code_b) = pg_temp.f(v_a, 'invite', 'zz0011_nobody'))::text || '|' ||
      (pg_temp.f(v_b, 'invite', v_code_a) = pg_temp.f(v_b, 'invite', 'zz0011_nobody'))::text || '|' ||
      pg_temp.s(pg_temp.f(v_a, 'invite', 'tst0011_b')) || '|' || pg_temp.s(pg_temp.f(v_b, 'invite', 'tst0011_a')));
    perform pg_temp.chk('4 Sentidos', 'No se creó ninguna solicitud', '0',
      (select count(*)::text from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b)));
    perform pg_temp.chk('4 Sentidos', 'Invitar a pareja (A a B y B a A): not_found', 'error:not_found|error:not_found',
      pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)) || '|' || pg_temp.s(pg_temp.p(v_b, 'invite', v_code_a)));
    perform pg_temp.chk('4 Sentidos', 'Retar (A a B y B a A): not_found, igual que un inexistente', 'true|true|error:not_found|error:not_found',
      (pg_temp.l(v_a, 'challenge_send', v_code_b) = pg_temp.l(v_a, 'challenge_send', 'zz0011_nobody'))::text || '|' ||
      (pg_temp.l(v_b, 'challenge_send', v_code_a) = pg_temp.l(v_b, 'challenge_send', 'zz0011_nobody'))::text || '|' ||
      pg_temp.s(pg_temp.l(v_a, 'challenge_send', v_code_b)) || '|' || pg_temp.s(pg_temp.l(v_b, 'challenge_send', v_code_a)));
    perform pg_temp.chk('4 Sentidos', 'No se creó ningún reto', '0',
      (select count(*)::text from public.battle_challenges where status = 'pending' and (challenger_id in (v_a, v_b) or challenged_id in (v_a, v_b))));

    -- Defensa en profundidad: aunque una carrera dejara una amistad aceptada, el bloqueo manda
    insert into public.friendships (user_low, user_high, requested_by, status, responded_at)
    values (least(v_a, v_b), greatest(v_a, v_b), v_a, 'accepted', now());
    perform pg_temp.chk('4 Sentidos', 'Con una amistad aceptada "colada": la búsqueda sigue en not_found (A y B)', 'error:not_found|error:not_found',
      pg_temp.s(pg_temp.f(v_a, 'search', v_code_b)) || '|' || pg_temp.s(pg_temp.f(v_b, 'search', v_code_a)));
    perform pg_temp.chk('4 Sentidos', 'invitar a amistad: not_found y NO already_friends (A y B)', 'error:not_found|error:not_found',
      pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)) || '|' || pg_temp.s(pg_temp.f(v_b, 'invite', v_code_a)));
    perform pg_temp.chk('4 Sentidos', 'invitar a pareja: not_found aunque sean amigos (A y B)', 'error:not_found|error:not_found',
      pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)) || '|' || pg_temp.s(pg_temp.p(v_b, 'invite', v_code_a)));
    perform pg_temp.chk('4 Sentidos', 'retar: not_found aunque sean amigos (A y B)', 'error:not_found|error:not_found',
      pg_temp.s(pg_temp.l(v_a, 'challenge_send', v_code_b)) || '|' || pg_temp.s(pg_temp.l(v_b, 'challenge_send', v_code_a)));
    delete from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b);

    -- Aceptaciones que una carrera pudo dejar pendientes: responden como si ya no existieran
    insert into public.friendships (user_low, user_high, requested_by, status)
    values (least(v_a, v_b), greatest(v_a, v_b), v_b, 'pending');
    perform pg_temp.chk('4 Sentidos', 'Aceptar una solicitud pendiente de quien me bloqueó/bloqueé: request_gone', 'error:request_gone|pending',
      pg_temp.s(pg_temp.f(v_a, 'accept', v_code_b)) || '|' ||
      (select status from public.friendships where user_low in (v_a, v_b) and user_high in (v_a, v_b)));
    delete from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b);

    insert into public.pair_streaks (user_low, user_high, invited_by, expires_at)
    values (least(v_a, v_b), greatest(v_a, v_b), v_b, clock_timestamp() + interval '3 days');
    perform pg_temp.chk('4 Sentidos', 'Aceptar una pareja pendiente de la persona bloqueada: pair_gone y sigue pendiente', 'error:pair_gone|pending',
      pg_temp.s(pg_temp.p(v_a, 'accept', v_code_b)) || '|' ||
      (select status from public.pair_streaks where user_low in (v_a, v_b) and user_high in (v_a, v_b)));
    delete from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b);

    insert into public.battle_challenges (challenger_id, challenged_id, created_at, expires_at)
    values (v_b, v_a, clock_timestamp(), clock_timestamp() + interval '2 minutes');
    perform pg_temp.chk('4 Sentidos', 'Aceptar un reto pendiente de la persona bloqueada: challenge_gone y no se crea batalla', 'error:challenge_gone|0',
      pg_temp.s(pg_temp.l(v_a, 'challenge_accept', v_code_b)) || '|' ||
      (select count(*)::text from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b)));
    delete from public.battle_challenges where challenger_id in (v_a, v_b) or challenged_id in (v_a, v_b);

    -- Partida rápida: no los empareja mientras haya bloqueo, aunque pase el tiempo y el rango se amplíe
    perform pg_temp.chk('4 Partida rápida', 'A entra a la cola', 'waiting', pg_temp.s(pg_temp.l(v_a, 'quick_join')));
    perform pg_temp.chk('4 Partida rápida', 'B entra a la cola: NO se emparejan (hay bloqueo)', 'waiting', pg_temp.s(pg_temp.l(v_b, 'quick_join')));
    update public.matchmaking_queue set joined_at = now() - interval '130 seconds', last_seen_at = clock_timestamp()
     where user_id in (v_a, v_b);
    perform pg_temp.chk('4 Partida rápida', 'Aun con 130 s de espera y el rango ampliado siguen sin emparejarse (A y B)', 'waiting|waiting',
      pg_temp.s(pg_temp.l(v_a, 'quick_poll')) || '|' || pg_temp.s(pg_temp.l(v_b, 'quick_poll')));
    perform pg_temp.chk('4 Partida rápida', 'No se creó ninguna batalla', '0',
      (select count(*)::text from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b)));

    -- Desbloquear: vuelve la partida rápida (la batalla se reutiliza en el bloque 5)
    perform pg_temp.chk('4 Desbloquear', 'A desbloquea a B (por código)', 'unblocked', pg_temp.s(pg_temp.f(v_a, 'unblock', v_code_b)));
    perform pg_temp.chk('4 Desbloquear', 'La lista de bloqueados de A queda vacía', '0', jsonb_array_length(pg_temp.f(v_a, 'blocks') -> 'blocked')::text);
    v := pg_temp.l(v_b, 'quick_poll');
    perform pg_temp.chk('4 Desbloquear', 'Sin bloqueo, B se empareja con A en la cola', 'matched', pg_temp.s(v));
    bid := (v ->> 'battle_id')::uuid;
    perform pg_temp.chk('4 Desbloquear', 'A recibe la misma batalla', bid::text, pg_temp.l(v_a, 'quick_poll') ->> 'battle_id');
    update public.battles set last_seen1 = clock_timestamp(), last_seen2 = clock_timestamp() where id = bid;

    -- Desbloquear NO devuelve lo que el bloqueo borró
    perform pg_temp.chk('4 Desbloquear', 'Desbloquear no devuelve la amistad', '0',
      jsonb_array_length(pg_temp.f(v_a, 'list') -> 'friends')::text);
    perform pg_temp.chk('4 Desbloquear', 'Desbloquear otra vez (ya no hay bloqueo): not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'unblock', v_code_b)));
    perform pg_temp.chk('4 Desbloquear', 'Desbloquear a un inexistente da lo MISMO', 'true',
      (pg_temp.f(v_a, 'unblock', v_code_b) = pg_temp.f(v_a, 'unblock', 'zz0011_nobody'))::text);
    perform pg_temp.chk('4 Desbloquear', 'Desbloquear a uno mismo da lo MISMO', 'true',
      (pg_temp.f(v_a, 'unblock', 'tst0011_a') = pg_temp.f(v_a, 'unblock', 'zz0011_nobody'))::text);

    -- Un bloqueo del otro lado sigue valiendo aunque yo desbloquee el mío
    delete from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b);
    delete from public.matchmaking_queue where user_id in (v_a, v_b);
    perform pg_temp.chk('4 Desbloquear', 'Búsqueda restablecida: A encuentra a B (relación none)', 'none', pg_temp.f(v_a, 'search', v_code_b) ->> 'relation');
    perform pg_temp.chk('4 Desbloquear', 'B bloquea a A', 'blocked', pg_temp.s(pg_temp.f(v_b, 'block', v_code_a)));
    perform pg_temp.chk('4 Desbloquear', 'Con el bloqueo de B, A no encuentra a B aunque A no bloqueó a nadie', 'error:not_found|0',
      pg_temp.s(pg_temp.f(v_a, 'search', v_code_b)) || '|' || jsonb_array_length(pg_temp.f(v_a, 'blocks') -> 'blocked')::text);
    t := pg_temp.s(pg_temp.f(v_a, 'block', v_code_b));
    perform pg_temp.chk('4 Desbloquear', 'A bloquea a B además: sigue sin verse', 'blocked|error:not_found',
      t || '|' || pg_temp.s(pg_temp.f(v_a, 'search', v_code_b)));
    t := pg_temp.s(pg_temp.f(v_a, 'unblock', v_code_b));
    perform pg_temp.chk('4 Desbloquear', 'A desbloquea: el bloqueo de B sigue (A no encuentra a B)', 'unblocked|error:not_found',
      t || '|' || pg_temp.s(pg_temp.f(v_a, 'search', v_code_b)));
    t := pg_temp.s(pg_temp.f(v_b, 'unblock', v_code_a));
    perform pg_temp.chk('4 Desbloquear', 'B desbloquea: se ven otra vez (A encuentra a B)', 'unblocked|none',
      t || '|' || (pg_temp.f(v_a, 'search', v_code_b) ->> 'relation'));

    -- Reportar no depende del bloqueo
    perform pg_temp.chk('4 Reporte', 'B bloquea a A y A aun así puede reportar a B', 'blocked|reported',
      pg_temp.s(pg_temp.f(v_b, 'block', v_code_a)) || '|' || pg_temp.s(pg_temp.f(v_a, 'report', v_code_b || '|harassment|me bloqueó y me insulta')));
    perform pg_temp.chk('4 Reporte', 'y B puede reportar a A (a quien bloqueó)', 'reported', pg_temp.s(pg_temp.f(v_b, 'report', v_code_a || '|other|')));
    perform pg_temp.chk('4 Reporte', 'Bloquear tampoco reporta: solo hay los 2 reportes hechos', '2',
      (select count(*)::text from public.social_reports where reporter_id in (v_a, v_b)));
    perform pg_temp.f(v_b, 'unblock', v_code_a);

    -- Límite de ritmo: block y unblock comparten 30 por hora
    delete from public.social_rate_limit where user_id in (v_a, v_b);
    n := 0;
    for i in 1..30 loop
      if pg_temp.s(pg_temp.f(v_a, 'unblock', 'zz0011_nobody')) = 'error:not_found' then n := n + 1; end if;
    end loop;
    perform pg_temp.chk('4 Ritmo', '30 desbloqueos fallidos responden not_found', '30', n::text);
    perform pg_temp.chk('4 Ritmo', 'El 31 (un block) lanza rate_limited: comparten el mismo contador', 'exception:rate_limited',
      pg_temp.s(pg_temp.f(v_a, 'block', v_code_b)));
    perform pg_temp.chk('4 Ritmo', 'El contador conserva los 30', '30',
      (select count::text from public.social_rate_limit where user_id = v_a and action = 'block'));
    perform pg_temp.chk('4 Ritmo', 'El rate_limited no creó el bloqueo', '0',
      (select count(*)::text from public.social_blocks where blocker_id in (v_a, v_b)));
    delete from public.social_rate_limit where user_id in (v_a, v_b);
  exception when others then
    perform pg_temp.chk('4', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 5. Bloquear NO termina una batalla en curso
  -- ===================================================================================================
  begin
    perform pg_temp.reset_social(v_a, v_b);
    bid := pg_temp.new_battle(v_a, v_b);
    perform pg_temp.chk('5 Batalla', 'Preparación: batalla rápida activa entre A y B', 'quick|active|2',
      (select mode || '|' || status from public.battles where id = bid) || '|' ||
      (select count(*)::text from public.battle_active where battle_id = bid));
    v := pg_temp.sy(v_a, bid);
    perform pg_temp.chk('5 Batalla', 'Antes de bloquear A recibe la batalla activa', 'active', v ->> 'status');

    perform pg_temp.chk('5 Batalla', 'A bloquea al rival EN PLENA batalla', 'blocked', pg_temp.s(pg_temp.f(v_a, 'block', v_code_b)));
    perform pg_temp.chk('5 Batalla', 'La batalla sigue activa en la tabla', 'active', (select status from public.battles where id = bid));
    perform pg_temp.chk('5 Batalla', 'Los dos siguen marcados como en batalla', '2', (select count(*)::text from public.battle_active where battle_id = bid));
    perform pg_temp.chk('5 Batalla', 'A sigue recibiendo la batalla (battle_sync)', 'active|' || bid::text,
      (pg_temp.sy(v_a, bid) ->> 'status') || '|' || (pg_temp.sy(v_a, bid) ->> 'battle_id'));
    perform pg_temp.chk('5 Batalla', 'B también la sigue recibiendo', 'active|' || bid::text,
      (pg_temp.sy(v_b, bid) ->> 'status') || '|' || (pg_temp.sy(v_b, bid) ->> 'battle_id'));
    perform pg_temp.chk('5 Batalla', 'El rival sigue en la vista de A con su nombre', 'tst0011_b', pg_temp.sy(v_a, bid) -> 'rival' ->> 'username');
    perform pg_temp.chk('5 Batalla', 'No se canceló ni se terminó nada: sin resultados', '0',
      (select count(*)::text from public.battle_results where battle_id = bid));
    perform pg_temp.chk('5 Batalla', 'Bloquear en batalla tampoco saca a nadie de ella (A sigue en la misma batalla en el lobby)', bid::text,
      pg_temp.l(v_a, 'challenges') ->> 'active_battle_id');
    delete from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b);
    delete from public.matchmaking_queue where user_id in (v_a, v_b);
    perform pg_temp.f(v_a, 'unblock', v_code_b);
  exception when others then
    perform pg_temp.chk('5', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 6. avatar_hidden: las claves salen en null, subir queda rechazado, borrar sigue permitido
  -- ===================================================================================================
  begin
    perform pg_temp.reset_social(v_a, v_b);
    update public.social_profile set avatar_hidden = false where user_id in (v_a, v_b);

    t := pg_temp.s(pg_temp.av(v_a, true));
    perform pg_temp.chk('6 Foto', 'A sube su foto (social_set_avatar(true)): ok y versión 1', 'ok|1',
      t || '|' || (select avatar_version::text from public.social_profile where user_id = v_a));
    t := pg_temp.s(pg_temp.av(v_b, true));
    perform pg_temp.chk('6 Foto', 'B sube su foto: ok y versión 1', 'ok|1',
      t || '|' || (select avatar_version::text from public.social_profile where user_id = v_b));
    select avatar_key into v_key_a from public.social_profile where user_id = v_a;
    select avatar_key into v_key_b from public.social_profile where user_id = v_b;

    -- 6.1 Solicitudes y búsqueda. Control SIN ocultar: las claves salen
    perform pg_temp.chk('6 Foto', 'B invita a A', 'sent', pg_temp.s(pg_temp.f(v_b, 'invite', 'tst0011_a')));
    perform pg_temp.chk('6 Control', 'requests recibidas de A: la clave y la versión de B', v_key_b::text || '|1',
      (pg_temp.f(v_a, 'requests') -> 'received' -> 0 ->> 'avatar_key') || '|' || (pg_temp.f(v_a, 'requests') -> 'received' -> 0 ->> 'avatar_version'));
    perform pg_temp.chk('6 Control', 'requests enviadas de B: la clave y la versión de A', v_key_a::text || '|1',
      (pg_temp.f(v_b, 'requests') -> 'sent' -> 0 ->> 'avatar_key') || '|' || (pg_temp.f(v_b, 'requests') -> 'sent' -> 0 ->> 'avatar_version'));
    perform pg_temp.chk('6 Control', 'search de A a B: la clave de B', v_key_b::text, pg_temp.f(v_a, 'search', v_code_b) ->> 'avatar_key');

    update public.social_profile set avatar_hidden = true where user_id in (v_a, v_b);
    perform pg_temp.chk('6 Oculta', 'requests recibidas: clave y versión en null', 'NULL|NULL',
      pg_temp.nz(pg_temp.f(v_a, 'requests') -> 'received' -> 0 ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.f(v_a, 'requests') -> 'received' -> 0 ->> 'avatar_version'));
    perform pg_temp.chk('6 Oculta', 'requests enviadas: clave y versión en null', 'NULL|NULL',
      pg_temp.nz(pg_temp.f(v_b, 'requests') -> 'sent' -> 0 ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.f(v_b, 'requests') -> 'sent' -> 0 ->> 'avatar_version'));
    v := pg_temp.f(v_a, 'search', v_code_b);
    perform pg_temp.chk('6 Oculta', 'search: la persona se encuentra igual, pero sin clave ni versión', 'ok|tst0011_b|NULL|NULL',
      pg_temp.s(v) || '|' || (v ->> 'username') || '|' || pg_temp.nz(v ->> 'avatar_key') || '|' || pg_temp.nz(v ->> 'avatar_version'));

    -- 6.2 Amigos, perfil, pareja y retos. Control SIN ocultar
    update public.social_profile set avatar_hidden = false where user_id in (v_a, v_b);
    perform pg_temp.chk('6 Foto', 'A acepta la solicitud', 'accepted', pg_temp.s(pg_temp.f(v_a, 'accept', v_code_b)));
    perform pg_temp.chk('6 Foto', 'B invita a A a una racha de pareja y A acepta', 'active',
      case when pg_temp.s(pg_temp.p(v_b, 'invite', v_code_a)) = 'sent' then pg_temp.s(pg_temp.p(v_a, 'accept', v_code_b)) else 'no se pudo invitar' end);
    perform pg_temp.chk('6 Foto', 'B reta a A', 'sent', pg_temp.s(pg_temp.l(v_b, 'challenge_send', v_code_a)));
    perform pg_temp.chk('6 Control', 'list de A: la clave de B', v_key_b::text, pg_temp.f(v_a, 'list') -> 'friends' -> 0 ->> 'avatar_key');
    perform pg_temp.chk('6 Control', 'profile de B visto por A: la clave de B', v_key_b::text, pg_temp.f(v_a, 'profile', v_code_b) ->> 'avatar_key');
    perform pg_temp.chk('6 Control', 'pareja (overview y detail) de A: la clave de B', v_key_b::text || '|' || v_key_b::text,
      (pg_temp.p(v_a, 'overview') -> 'pairs' -> 0 ->> 'avatar_key') || '|' || (pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' ->> 'avatar_key'));
    perform pg_temp.chk('6 Control', 'retos: recibido por A (de B) y enviado por B (a A)', v_key_b::text || '|' || v_key_a::text,
      (pg_temp.l(v_a, 'challenges') -> 'received' -> 0 ->> 'avatar_key') || '|' || (pg_temp.l(v_b, 'challenges') -> 'sent' ->> 'avatar_key'));

    update public.social_profile set avatar_hidden = true where user_id in (v_a, v_b);
    perform pg_temp.chk('6 Oculta', 'list: clave y versión en null', 'NULL|NULL',
      pg_temp.nz(pg_temp.f(v_a, 'list') -> 'friends' -> 0 ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.f(v_a, 'list') -> 'friends' -> 0 ->> 'avatar_version'));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('6 Oculta', 'profile: el perfil sale igual pero sin clave ni versión', 'tst0011_b|NULL|NULL',
      (v ->> 'username') || '|' || pg_temp.nz(v ->> 'avatar_key') || '|' || pg_temp.nz(v ->> 'avatar_version'));
    perform pg_temp.chk('6 Oculta', 'search de una persona que ya es amiga: relación friends y sin clave', 'friends|NULL',
      (pg_temp.f(v_a, 'search', v_code_b) ->> 'relation') || '|' || pg_temp.nz(pg_temp.f(v_a, 'search', v_code_b) ->> 'avatar_key'));
    perform pg_temp.chk('6 Oculta', 'pareja: overview y detail sin clave ni versión', 'NULL|NULL|NULL|NULL',
      pg_temp.nz(pg_temp.p(v_a, 'overview') -> 'pairs' -> 0 ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.p(v_a, 'overview') -> 'pairs' -> 0 ->> 'avatar_version') || '|' ||
      pg_temp.nz(pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' ->> 'avatar_version'));
    perform pg_temp.chk('6 Oculta', 'retos: recibido por A y enviado por B sin clave ni versión', 'NULL|NULL|NULL|NULL',
      pg_temp.nz(pg_temp.l(v_a, 'challenges') -> 'received' -> 0 ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.l(v_a, 'challenges') -> 'received' -> 0 ->> 'avatar_version') || '|' ||
      pg_temp.nz(pg_temp.l(v_b, 'challenges') -> 'sent' ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.l(v_b, 'challenges') -> 'sent' ->> 'avatar_version'));

    -- 6.3 La batalla (sx_battle_state a través de battle_sync). Control SIN ocultar
    update public.social_profile set avatar_hidden = false where user_id in (v_a, v_b);
    v := pg_temp.l(v_a, 'challenge_accept', v_code_b);
    perform pg_temp.chk('6 Foto', 'A acepta el reto: se crea la batalla', 'matched', pg_temp.s(v));
    bid := (v ->> 'battle_id')::uuid;
    update public.battles set last_seen1 = clock_timestamp(), last_seen2 = clock_timestamp() where id = bid;
    perform pg_temp.chk('6 Control', 'battle_sync de A: su clave en "me" y la de B en "rival"', v_key_a::text || '|' || v_key_b::text,
      (pg_temp.sy(v_a, bid) -> 'me' ->> 'avatar_key') || '|' || (pg_temp.sy(v_a, bid) -> 'rival' ->> 'avatar_key'));
    perform pg_temp.chk('6 Control', 'battle_sync de B: su clave en "me" y la de A en "rival"', v_key_b::text || '|' || v_key_a::text,
      (pg_temp.sy(v_b, bid) -> 'me' ->> 'avatar_key') || '|' || (pg_temp.sy(v_b, bid) -> 'rival' ->> 'avatar_key'));
    update public.social_profile set avatar_hidden = true where user_id in (v_a, v_b);
    perform pg_temp.chk('6 Oculta', 'battle_sync de A: "me" y "rival" sin clave ni versión', 'NULL|NULL|NULL|NULL',
      pg_temp.nz(pg_temp.sy(v_a, bid) -> 'me' ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.sy(v_a, bid) -> 'me' ->> 'avatar_version') || '|' ||
      pg_temp.nz(pg_temp.sy(v_a, bid) -> 'rival' ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.sy(v_a, bid) -> 'rival' ->> 'avatar_version'));
    perform pg_temp.chk('6 Oculta', 'battle_sync de B: "me" y "rival" sin clave ni versión', 'NULL|NULL|NULL|NULL',
      pg_temp.nz(pg_temp.sy(v_b, bid) -> 'me' ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.sy(v_b, bid) -> 'me' ->> 'avatar_version') || '|' ||
      pg_temp.nz(pg_temp.sy(v_b, bid) -> 'rival' ->> 'avatar_key') || '|' || pg_temp.nz(pg_temp.sy(v_b, bid) -> 'rival' ->> 'avatar_version'));
    perform pg_temp.chk('6 Oculta', 'La batalla sigue igual aunque la foto esté oculta (nombre del rival y estado)', 'tst0011_b|active',
      (pg_temp.sy(v_a, bid) -> 'rival' ->> 'username') || '|' || (pg_temp.sy(v_a, bid) ->> 'status'));
    delete from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b);
    delete from public.battle_challenges where challenger_id in (v_a, v_b) or challenged_id in (v_a, v_b);

    -- 6.4 Subir queda rechazado (función y Storage), borrar sigue permitido, y al levantar se puede subir otra vez
    t := pg_temp.s(pg_temp.av(v_b, true));
    perform pg_temp.chk('6 Subir', 'Con la foto oculta, social_set_avatar(true) devuelve avatar_hidden y no sube la versión', 'error:avatar_hidden|1',
      t || '|' || (select avatar_version::text from public.social_profile where user_id = v_b));
    perform pg_temp.chk('6 Subir', 'Con la foto oculta el dueño lee su avatar_hidden', 'true',
      pg_temp.scalar('authenticated', v_b, 'select avatar_hidden::text from public.social_profile'));
    perform pg_temp.chk('6 Subir', 'Storage: B oculto NO puede subir <su clave>.jpg (política INSERT)', 'ERR:42501',
      pg_temp.run('authenticated', v_b, format('insert into storage.objects (bucket_id, name) values (%L, %L)', 'social-avatars', v_key_b::text || '.jpg')));

    update public.social_profile set avatar_hidden = false where user_id = v_b;
    perform pg_temp.chk('6 Subir', 'Storage: B sin ocultar SÍ puede subir <su clave>.jpg', 'ok',
      pg_temp.run('authenticated', v_b, format('insert into storage.objects (bucket_id, name) values (%L, %L)', 'social-avatars', v_key_b::text || '.jpg')));
    perform pg_temp.chk('6 Subir', 'Storage: B sin ocultar SÍ puede reemplazarlo (política UPDATE toca 1 fila)', '1',
      pg_temp.scalar('authenticated', v_b, format('with u as (update storage.objects set name = name where bucket_id = %L and name = %L returning 1) select count(*)::text from u', 'social-avatars', v_key_b::text || '.jpg')));

    update public.social_profile set avatar_hidden = true where user_id = v_b;
    perform pg_temp.chk('6 Subir', 'Storage: B oculto NO puede reemplazarlo (política UPDATE toca 0 filas)', '0',
      pg_temp.scalar('authenticated', v_b, format('with u as (update storage.objects set name = name where bucket_id = %L and name = %L returning 1) select count(*)::text from u', 'social-avatars', v_key_b::text || '.jpg')));
    perform pg_temp.chk('6 Subir', 'Storage: B oculto SÍ puede borrar su archivo (la política DELETE no cambió)', '1',
      pg_temp.scalar('authenticated', v_b, format('with d as (delete from storage.objects where bucket_id = %L and name = %L returning 1) select count(*)::text from d', 'social-avatars', v_key_b::text || '.jpg')));

    v := pg_temp.av(v_b, false);
    select avatar_key into v_key_b2 from public.social_profile where user_id = v_b;
    perform pg_temp.chk('6 Subir', 'Con la foto oculta, social_set_avatar(false) sigue funcionando: ok, versión null y clave nueva', 'ok|NULL|true',
      pg_temp.s(v) || '|' || pg_temp.nz(v ->> 'avatar_version') || '|' || (v_key_b2 <> v_key_b)::text);
    perform pg_temp.chk('6 Subir', 'y la ocultación NO se levanta sola al rotar la clave', 'true',
      (select avatar_hidden::text from public.social_profile where user_id = v_b));

    update public.social_profile set avatar_hidden = false where user_id = v_b;
    t := pg_temp.s(pg_temp.av(v_b, true));
    perform pg_temp.chk('6 Subir', 'Al levantar la ocultación B puede subir otra vez: ok y versión 1', 'ok|1',
      t || '|' || (select avatar_version::text from public.social_profile where user_id = v_b));
    perform pg_temp.chk('6 Subir', 'y vuelve a verse su clave nueva (search de A a B, ya amigos)', v_key_b2::text,
      pg_temp.f(v_a, 'search', v_code_b) ->> 'avatar_key');
  exception when others then
    perform pg_temp.chk('6', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- LIMPIEZA: borra todo lo que creó el guion y restaura el banco, los retos y la curva reales (si los había)
  -- ===================================================================================================
  perform pg_temp.reset_social(v_a, v_b);
  delete from storage.objects
   where bucket_id = 'social-avatars'
     and (name in (select avatar_key::text || '.jpg' from public.social_profile where user_id in (v_a, v_b))
          or name in (coalesce(v_key_a::text, '-') || '.jpg', coalesce(v_key_b::text, '-') || '.jpg', coalesce(v_key_b2::text, '-') || '.jpg'));
  delete from public.social_profile where user_id in (v_a, v_b);

  delete from public.battle_questions where question like 'TEST-0011%';
  update public.battle_questions set active = true where id in (select id from pg_temp.t_saved_q);
  delete from public.ulti_challenges where objective_title like 'TEST-0011%';
  update public.ulti_challenges set active = true where id in (select id from pg_temp.t_saved_u);
  delete from public.fighter_level_curve;
  insert into public.fighter_level_curve (level, min_xp) select level, min_xp from pg_temp.t_saved_curve;

  perform pg_temp.chk('Limpieza', 'No queda ningún perfil de prueba', '0', (select count(*)::text from public.social_profile where user_id in (v_a, v_b)));
  perform pg_temp.chk('Limpieza', 'No queda ningún reporte ni bloqueo de las cuentas de prueba', '0|0',
    (select count(*)::text from public.social_reports where reporter_id in (v_a, v_b) or reported_id in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.social_blocks where blocker_id in (v_a, v_b) or blocked_id in (v_a, v_b)));
  perform pg_temp.chk('Limpieza', 'No queda ninguna batalla, amistad ni pareja de prueba', '0|0|0',
    (select count(*)::text from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b)));
  perform pg_temp.chk('Limpieza', 'No queda ningún archivo de prueba en social-avatars ni pregunta o reto de prueba', '0|0|0',
    (select count(*)::text from storage.objects where bucket_id = 'social-avatars'
       and name in (coalesce(v_key_a::text, '-') || '.jpg', coalesce(v_key_b::text, '-') || '.jpg', coalesce(v_key_b2::text, '-') || '.jpg')) || '|' ||
    (select count(*)::text from public.battle_questions where question like 'TEST-0011%') || '|' ||
    (select count(*)::text from public.ulti_challenges where objective_title like 'TEST-0011%'));
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
-- delete from public.matchmaking_queue where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.battle_challenges where challenger_id in ('<ID_A>', '<ID_B>') or challenged_id in ('<ID_A>', '<ID_B>');
-- delete from public.pair_streaks where user_low in ('<ID_A>', '<ID_B>') or user_high in ('<ID_A>', '<ID_B>');
-- delete from public.friendships where user_low in ('<ID_A>', '<ID_B>') or user_high in ('<ID_A>', '<ID_B>');
-- delete from public.social_reports where reporter_id in ('<ID_A>', '<ID_B>') or reported_id in ('<ID_A>', '<ID_B>');
-- delete from public.social_blocks where blocker_id in ('<ID_A>', '<ID_B>') or blocked_id in ('<ID_A>', '<ID_B>');
-- delete from public.social_rate_limit where user_id in ('<ID_A>', '<ID_B>');
-- delete from storage.objects where bucket_id = 'social-avatars' and name in (select avatar_key::text || '.jpg' from public.social_profile where user_id in ('<ID_A>', '<ID_B>'));
-- delete from public.social_profile where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.battle_questions where question like 'TEST-0011%';
-- delete from public.ulti_challenges where objective_title like 'TEST-0011%';
-- -- Si había banco real, vuelve a activarlo a mano: update public.battle_questions set active = true; (y lo mismo en ulti_challenges)
