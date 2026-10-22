-- =====================================================================================================
-- NightWatch Protocol - Guion de escenarios del script 0008 (Social)
--
-- NO se ejecuta desde la app ni desde el repositorio: lo corres TÚ a mano en el SQL Editor de Supabase,
-- DESPUÉS de aplicar supabase/migrations/0008_social.sql. Este archivo vive en supabase/verificacion/ (no en
-- migrations/) porque NO es un script del esquema: no se aplica en un proyecto nuevo.
--
-- CÓMO USARLO
--   1. Crea DOS cuentas de prueba en Authentication > Users (cualquier correo; que no tengan progreso real
--      ni uso de Social). Copia sus ids (uuid).
--   2. En la sección 2 (más abajo) reemplaza las dos líneas marcadas con "REEMPLAZA" (v_a y v_b).
--   3. Ejecuta TODO el archivo de una vez, con el rol postgres del SQL Editor.
--   4. El resultado es una tabla: una fila por comprobación con lo esperado, lo obtenido y OK o FALLA. La primera
--      fila resume (debe decir "0 fallas"). Si algo falla, el bloque (columna bloque) dice dónde mirar.
--
-- QUÉ HACE Y QUÉ GARANTIZA
--   * Todo el escenario está en UNA sola sentencia (un bloque DO): es atómica. Si el guion lanza un error
--     inesperado, se deshace TODO (también el cambio temporal del banco) y no queda nada que limpiar.
--   * Cuando termina bien, el propio guion BORRA lo que creó (perfiles, amistades, batallas, progreso de prueba)
--     y RESTAURA el banco de preguntas, los retos de ulti y la curva de XP que hubiera (si ya aplicaste 0009).
--   * Para que las batallas arranquen aunque 0009 no esté aplicada, inserta un banco MÍNIMO temporal (preguntas
--     cuya respuesta correcta es siempre A, un reto de ataque `test-atk one` y uno de defensa `test-def two`) y una
--     curva de prueba (nivel 2 con 100 XP y nivel 3 con 200 XP). Si ya hay datos reales, los desactiva mientras
--     dura el guion y los vuelve a activar al final.
--   * La sesión se simula con set_config('request.jwt.claims', ...) (como en la verificación de 0006) y las
--     llamadas se hacen con el rol authenticated (o anon), así se prueban también los permisos reales.
--   * El paso del tiempo se simula modificando turn_deadline, last_seen1/2, deadline, expires_at, responded_at,
--     joined_at y accepted_at desde aquí (el dueño sí puede; la app no).
--
-- QUÉ NO CUBRE (necesita más cuentas o es demasiado costoso de simular)
--   * Parejas de racha múltiples por persona, el tope de 5 invitaciones de pareja pendientes, el tope de 20
--     solicitudes de amistad pendientes y el de 50 amigos (necesitan 3 o más cuentas).
--   * "rival_busy" (un amigo que ya está en una batalla con otra persona): necesita una tercera cuenta.
--   * El emparejamiento entre jugadores de niveles lejanos con XP real (se prueba la ampliación del rango forzando
--     el nivel en la cola).
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

-- Llama una función como una persona (simula la sesión con los claims y cambia al rol indicado).
-- Devuelve el jsonb de la función, o {"status":"exception","code":"<mensaje>"} si lanzó una excepción.
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

-- Atajos para las cinco funciones públicas (siempre con el rol authenticated).
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
create or replace function pg_temp.pl(p_user uuid, p_battle uuid, p_action text, p_turn integer default null, p_arg text default null)
returns jsonb language sql as $$
  select pg_temp.call('authenticated', p_user,
    format('select public.battle_play(%L::uuid, %L, %L::integer, %L)', p_battle, p_action, p_turn, p_arg))
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

-- Instante de la hora de Colombia (America/Bogota) en una fecha dada.
create or replace function pg_temp.bog(p_day date, p_time time)
returns timestamptz language sql as $$
  select (p_day + p_time) at time zone 'America/Bogota'
$$;

-- Isla completada (lección vista + lab completado) de un tema, con la hora indicada; y su inverso.
create or replace function pg_temp.island(p_user uuid, p_topic text, p_at timestamptz)
returns void language plpgsql as $$
begin
  insert into public.lesson_progress (user_id, topic, completed_at) values (p_user, p_topic, p_at) on conflict do nothing;
  insert into public.lab_progress (user_id, lab, completed_at) values (p_user, p_topic, p_at) on conflict do nothing;
end;
$$;
create or replace function pg_temp.unisland(p_user uuid, p_topic text)
returns void language plpgsql as $$
begin
  delete from public.lesson_progress where user_id = p_user and topic = p_topic;
  delete from public.lab_progress where user_id = p_user and lab = p_topic;
end;
$$;

-- Batallas: de quién es el turno, el otro jugador, HP, asiento.
create or replace function pg_temp.tu(p_battle uuid)
returns uuid language sql as $$
  select case when turn_seat = 1 then player1_id else player2_id end from public.battles where id = p_battle
$$;
create or replace function pg_temp.other(p_battle uuid, p_user uuid)
returns uuid language sql as $$
  select case when player1_id = p_user then player2_id else player1_id end from public.battles where id = p_battle
$$;
create or replace function pg_temp.hp(p_battle uuid, p_user uuid)
returns integer language sql as $$
  select case when player1_id = p_user then hp1 else hp2 end from public.battles where id = p_battle
$$;
create or replace function pg_temp.seat(p_battle uuid, p_user uuid)
returns integer language sql as $$
  select case when player1_id = p_user then 1 else 2 end from public.battles where id = p_battle
$$;

-- Borra todas las batallas, cola y retos de dos personas (cascada a turnos, ultis, resultados y batalla activa).
create or replace function pg_temp.wipe_battles(p_a uuid, p_b uuid)
returns void language plpgsql as $$
begin
  delete from public.battles where player1_id in (p_a, p_b) or player2_id in (p_a, p_b);
  delete from public.matchmaking_queue where user_id in (p_a, p_b);
  delete from public.battle_challenges where challenger_id in (p_a, p_b) or challenged_id in (p_a, p_b);
end;
$$;

-- Empareja a A y B por la partida rápida y devuelve el id de la batalla.
create or replace function pg_temp.new_battle(p_a uuid, p_b uuid)
returns uuid language plpgsql as $$
declare
  v jsonb;
  b uuid;
begin
  perform pg_temp.wipe_battles(p_a, p_b);
  perform pg_temp.l(p_a, 'quick_join');
  v := pg_temp.l(p_b, 'quick_join');
  b := (v ->> 'battle_id')::uuid;
  -- now() es la hora de INICIO de la transacción (todo el guion es una sola): se fijan los latidos iniciales a la hora real
  update public.battles set last_seen1 = clock_timestamp(), last_seen2 = clock_timestamp() where id = b;
  return b;
end;
$$;

-- Batalla nueva con la ulti YA cargada para quien tiene el primer turno (atacante). La carga por 3 aciertos
-- se prueba aparte (bloque 6); aquí solo se prepara el escenario de cada variante de la ulti.
create or replace function pg_temp.charged_battle(p_a uuid, p_b uuid)
returns uuid language plpgsql as $$
declare
  b uuid := pg_temp.new_battle(p_a, p_b);
begin
  update public.battles
     set ulti_charged1 = ulti_charged1 or turn_seat = 1,
         ulti_charged2 = ulti_charged2 or turn_seat = 2
   where id = b;
  return b;
end;
$$;

-- Responde la pregunta del turno actual como quien tiene el turno (o como p_user, si se indica).
create or replace function pg_temp.ans(p_battle uuid, p_user uuid, p_opt text)
returns jsonb language sql as $$
  select pg_temp.pl(p_user, p_battle, 'answer', (select turn_number from public.battles where id = p_battle), p_opt)
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
  bid2       uuid;
  f          uuid;
  s          uuid;
  v_code_a   text;
  v_code_b   text;
  v_today    date := (now() at time zone 'America/Bogota')::date;
  v_xp       integer;
  v_exp_lvl  integer;
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

  -- Restos de una corrida anterior (solo datos de Social de estas dos cuentas)
  perform pg_temp.wipe_battles(v_a, v_b);
  delete from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b);
  delete from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b);
  delete from public.social_rate_limit where user_id in (v_a, v_b);
  delete from public.social_profile where user_id in (v_a, v_b);

  if exists (select 1 from public.lesson_progress where user_id in (v_a, v_b))
     or exists (select 1 from public.lab_progress where user_id in (v_a, v_b))
     or exists (select 1 from public.exam_attempts where user_id in (v_a, v_b)) then
    raise exception 'Las cuentas de prueba deben estar SIN progreso de aprendizaje: este guion lo borra al terminar.';
  end if;

  -- Banco mínimo temporal: se apartan los datos reales (si los hay) y se insertan los de prueba.
  insert into pg_temp.t_saved_q select id from public.battle_questions where active;
  update public.battle_questions set active = false where id in (select id from pg_temp.t_saved_q);
  insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct)
  select 'CONTRASENAS', tr, 'TEST-0008 pregunta t' || tr || '-' || q, 'opcion-A', 'opcion-B', 'opcion-C', 'opcion-D', 'A'
  from generate_series(1, 3) tr, generate_series(1, 4) q;

  insert into pg_temp.t_saved_u select id from public.ulti_challenges where active;
  update public.ulti_challenges set active = false where id in (select id from pg_temp.t_saved_u);
  insert into public.ulti_challenges (kind, topic, objective_title, expected_command) values
    ('attack',  'FIREWALL', 'TEST-0008 ataque',  'test-atk one'),
    ('defense', 'MALWARE',  'TEST-0008 defensa', 'test-def two');

  insert into pg_temp.t_saved_curve select level, min_xp from public.fighter_level_curve;
  delete from public.fighter_level_curve;
  insert into public.fighter_level_curve (level, min_xp) values (1, 0), (2, 100), (3, 200);

  -- ===================================================================================================
  -- BLOQUE 1. Permisos y creación de perfiles (RLS, GRANT por columna, tablas cerradas, funciones)
  -- ===================================================================================================
  begin
    t := pg_temp.run('authenticated', v_a, format('insert into public.social_profile (user_id, username) values (%L, %L)', v_a, 'tst0008_a'));
    perform pg_temp.chk('1 Perfiles', 'A crea su perfil (INSERT por columna user_id, username)', 'ok', t);

    t := pg_temp.run('authenticated', v_b, format('insert into public.social_profile (user_id, username) values (%L, %L)', v_b, 'TST0008_A'));
    perform pg_temp.chk('1 Perfiles', 'Un nombre repetido (otras mayúsculas) lo rechaza el índice único', 'ERR:23505', t);

    t := pg_temp.run('authenticated', v_b, format('insert into public.social_profile (user_id, username) values (%L, %L)', v_b, 'ab'));
    perform pg_temp.chk('1 Perfiles', 'Un nombre de 2 caracteres incumple el check (3 a 20)', 'ERR:23514', t);

    t := pg_temp.run('authenticated', v_b, format('insert into public.social_profile (user_id, username) values (%L, %L)', v_b, 'tst0008_b'));
    perform pg_temp.chk('1 Perfiles', 'B crea su perfil', 'ok', t);

    t := pg_temp.run('authenticated', v_a, format('insert into public.social_profile (user_id, username) values (%L, %L)', v_b, 'otro_nombre'));
    perform pg_temp.chk('1 Perfiles', 'A no puede insertar el perfil de B (RLS)', 'ERR:42501', t);

    select friend_code into v_code_a from public.social_profile where user_id = v_a;
    select friend_code into v_code_b from public.social_profile where user_id = v_b;
    perform pg_temp.chk('1 Perfiles', 'El código de A tiene formato NIGHT-XXXXXX (sin 0, O, 1, I)', 'true', (v_code_a ~ '^NIGHT-[A-HJ-NP-Z2-9]{6}$')::text);
    perform pg_temp.chk('1 Perfiles', 'Los códigos de A y B son distintos', 'true', (v_code_a <> v_code_b)::text);

    perform pg_temp.chk('1 Perfiles', 'A solo ve SU fila de social_profile', '1',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.social_profile'));

    t := pg_temp.run('authenticated', v_a, format('update public.social_profile set username = %L where user_id = %L', 'cambiado', v_a));
    perform pg_temp.chk('1 Perfiles', 'El nombre de usuario NO se puede cambiar (sin UPDATE de username, O3)', 'ERR:42501', t);

    t := pg_temp.run('authenticated', v_a, format('update public.social_profile set hide_progress = true where user_id = %L', v_a));
    perform pg_temp.chk('1 Perfiles', 'A sí puede actualizar hide_progress', 'ok', t);
    t := pg_temp.run('authenticated', v_a, format('update public.social_profile set hide_progress = false where user_id = %L', v_a));
    perform pg_temp.chk('1 Perfiles', 'A vuelve a mostrar su progreso', 'ok', t);

    -- Tablas cerradas: ningún permiso (ni siquiera leer)
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede leer friendships', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.friendships'));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede leer battles', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.battles'));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede leer battle_questions (respuestas correctas)', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.battle_questions'));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede leer ulti_challenges (comandos esperados)', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.ulti_challenges'));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede leer pair_streaks', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.pair_streaks'));
    perform pg_temp.chk('1 Permisos', 'authenticated NO puede ejecutar una función interna (sx_xp)', 'ERR:42501',
      pg_temp.scalar('authenticated', v_a, format('select public.sx_xp(%L::uuid)::text', v_a)));
    perform pg_temp.chk('1 Permisos', 'anon NO puede ejecutar social_friends', 'ERR:42501',
      pg_temp.scalar('anon', null, 'select public.social_friends(''list'', null)::text'));
    perform pg_temp.chk('1 Permisos', 'anon NO puede leer social_profile', 'ERR:42501',
      pg_temp.scalar('anon', null, 'select count(*)::text from public.social_profile'));
    perform pg_temp.chk('1 Permisos', 'authenticated lee la curva de XP (dato de referencia, 3 filas de prueba)', '3',
      pg_temp.scalar('authenticated', v_a, 'select count(*)::text from public.fighter_level_curve'));

    -- Las únicas excepciones de las funciones de negocio
    perform pg_temp.chk('1 Excepciones', 'Sin sesión: no_session', 'exception:no_session', pg_temp.s(pg_temp.f(null, 'list')));
    perform pg_temp.chk('1 Excepciones', 'Sesión sin Social habilitado: social_not_enabled', 'exception:social_not_enabled',
      pg_temp.s(pg_temp.f(gen_random_uuid(), 'list')));
    perform pg_temp.chk('1 Excepciones', 'Acción fuera de la lista: bad_action', 'exception:bad_action', pg_temp.s(pg_temp.f(v_a, 'nope')));
    perform pg_temp.chk('1 Excepciones', 'bad_action también en parejas', 'exception:bad_action', pg_temp.s(pg_temp.p(v_a, 'nope')));
    perform pg_temp.chk('1 Excepciones', 'bad_action también en el lobby', 'exception:bad_action', pg_temp.s(pg_temp.l(v_a, 'nope')));
  exception when others then
    perform pg_temp.chk('1', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 2. Búsqueda, invitación, enumeración y límite de ritmo
  -- ===================================================================================================
  begin
    -- Búsqueda: coincidencia EXACTA (nombre sin distinguir mayúsculas, o código)
    v := pg_temp.f(v_a, 'search', 'TST0008_B');
    perform pg_temp.chk('2 Búsqueda', 'Por nombre exacto (otras mayúsculas): encontrado', 'ok', pg_temp.s(v));
    perform pg_temp.chk('2 Búsqueda', 'La relación inicial es none', 'none', v ->> 'relation');
    perform pg_temp.chk('2 Búsqueda', 'Devuelve el código de B', v_code_b, v ->> 'friend_code');
    perform pg_temp.chk('2 Búsqueda', 'Por código: encontrado', 'ok', pg_temp.s(pg_temp.f(v_a, 'search', v_code_b)));
    perform pg_temp.chk('2 Búsqueda', 'Sin búsqueda por prefijo (tst0008 no encuentra a nadie)', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'search', 'tst0008')));
    perform pg_temp.chk('2 Búsqueda', 'Nombre inexistente', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'search', 'zz0008_nobody')));
    perform pg_temp.chk('2 Búsqueda', 'Buscarse a uno mismo da lo MISMO que un inexistente', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'search', 'tst0008_a')));
    perform pg_temp.chk('2 Búsqueda', 'Consulta de 2 caracteres: bad_query', 'exception:bad_query', pg_temp.s(pg_temp.f(v_a, 'search', 'ab')));

    -- Enumeración: una persona inexistente y una que existe pero NO tiene relación conmigo responden IGUAL
    perform pg_temp.chk('2 Enumeración', 'accept: inexistente = sin relación', 'true',
      (pg_temp.f(v_a, 'accept', 'zz0008_nobody') = pg_temp.f(v_a, 'accept', v_code_b))::text);
    perform pg_temp.chk('2 Enumeración', 'accept responde request_gone', 'error:request_gone', pg_temp.s(pg_temp.f(v_a, 'accept', v_code_b)));
    perform pg_temp.chk('2 Enumeración', 'decline: inexistente = sin relación', 'true',
      (pg_temp.f(v_a, 'decline', 'zz0008_nobody') = pg_temp.f(v_a, 'decline', v_code_b))::text);
    perform pg_temp.chk('2 Enumeración', 'cancel: inexistente = sin relación', 'true',
      (pg_temp.f(v_a, 'cancel', 'zz0008_nobody') = pg_temp.f(v_a, 'cancel', v_code_b))::text);
    perform pg_temp.chk('2 Enumeración', 'remove: inexistente = no es mi amigo', 'true',
      (pg_temp.f(v_a, 'remove', 'zz0008_nobody') = pg_temp.f(v_a, 'remove', v_code_b))::text);
    perform pg_temp.chk('2 Enumeración', 'profile: inexistente = no es mi amigo', 'true',
      (pg_temp.f(v_a, 'profile', 'zz0008_nobody') = pg_temp.f(v_a, 'profile', v_code_b))::text);
    perform pg_temp.chk('2 Enumeración', 'profile de un no-amigo: not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'profile', v_code_b)));
    perform pg_temp.chk('2 Enumeración', 'Pareja (invite): inexistente = no es amigo (mismo not_found)', 'true',
      (pg_temp.p(v_a, 'invite', 'zz0008_nobody') = pg_temp.p(v_a, 'invite', v_code_b))::text);
    perform pg_temp.chk('2 Enumeración', 'Pareja (invite) a un no-amigo: not_found', 'error:not_found', pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('2 Enumeración', 'Pareja (invite) a uno mismo: el mismo not_found', 'error:not_found', pg_temp.s(pg_temp.p(v_a, 'invite', 'tst0008_a')));
    perform pg_temp.chk('2 Enumeración', 'Pareja: accept/decline/cancel/end iguales para inexistente y sin relación', 'true',
      ((pg_temp.p(v_a, 'accept', 'zz0008_nobody') = pg_temp.p(v_a, 'accept', v_code_b))
       and (pg_temp.p(v_a, 'decline', 'zz0008_nobody') = pg_temp.p(v_a, 'decline', v_code_b))
       and (pg_temp.p(v_a, 'cancel', 'zz0008_nobody') = pg_temp.p(v_a, 'cancel', v_code_b))
       and (pg_temp.p(v_a, 'end', 'zz0008_nobody') = pg_temp.p(v_a, 'end', v_code_b)))::text);
    perform pg_temp.chk('2 Enumeración', 'Reto (challenge_send): inexistente = no es amigo (mismo not_found)', 'true',
      (pg_temp.l(v_a, 'challenge_send', 'zz0008_nobody') = pg_temp.l(v_a, 'challenge_send', v_code_b))::text);
    perform pg_temp.chk('2 Enumeración', 'Reto a un no-amigo: not_found', 'error:not_found', pg_temp.s(pg_temp.l(v_a, 'challenge_send', v_code_b)));
    perform pg_temp.chk('2 Enumeración', 'Reto: accept/decline iguales para inexistente y sin relación', 'true',
      ((pg_temp.l(v_a, 'challenge_accept', 'zz0008_nobody') = pg_temp.l(v_a, 'challenge_accept', v_code_b))
       and (pg_temp.l(v_a, 'challenge_decline', 'zz0008_nobody') = pg_temp.l(v_a, 'challenge_decline', v_code_b)))::text);

    -- Invitación de amistad
    perform pg_temp.chk('2 Invitación', 'A invita a B (por nombre)', 'sent', pg_temp.s(pg_temp.f(v_a, 'invite', 'tst0008_b')));
    perform pg_temp.chk('2 Invitación', 'Invitar otra vez: request_exists', 'error:request_exists', pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('2 Invitación', 'B invita a A mientras A ya lo invitó: request_incoming', 'error:request_incoming', pg_temp.s(pg_temp.f(v_b, 'invite', v_code_a)));
    perform pg_temp.chk('2 Invitación', 'Invitarse a uno mismo: invalid_target', 'error:invalid_target', pg_temp.s(pg_temp.f(v_a, 'invite', 'tst0008_a')));
    perform pg_temp.chk('2 Invitación', 'Invitar a un inexistente: not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'invite', 'zz0008_nobody')));
    perform pg_temp.chk('2 Invitación', 'B ve 1 solicitud recibida', '1', jsonb_array_length(pg_temp.f(v_b, 'requests') -> 'received')::text);
    perform pg_temp.chk('2 Invitación', 'A ve 1 solicitud enviada', '1', jsonb_array_length(pg_temp.f(v_a, 'requests') -> 'sent')::text);
    perform pg_temp.chk('2 Invitación', 'La relación vista por A es sent', 'sent', pg_temp.f(v_a, 'search', v_code_b) ->> 'relation');
    perform pg_temp.chk('2 Invitación', 'La relación vista por B es received', 'received', pg_temp.f(v_b, 'search', v_code_a) ->> 'relation');

    -- Cancelar y volver a invitar
    perform pg_temp.chk('2 Invitación', 'A cancela su solicitud', 'cancelled', pg_temp.s(pg_temp.f(v_a, 'cancel', v_code_b)));
    perform pg_temp.chk('2 Invitación', 'A vuelve a invitar', 'sent', pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)));

    -- Rechazo y espera de 24 horas (O7)
    perform pg_temp.chk('2 Rechazo', 'B rechaza', 'declined', pg_temp.s(pg_temp.f(v_b, 'decline', v_code_a)));
    perform pg_temp.chk('2 Rechazo', 'A invita de nuevo dentro de las 24 h: cooldown', 'error:cooldown', pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)));
    update public.friendships set responded_at = now() - interval '25 hours' where status = 'declined';
    perform pg_temp.chk('2 Rechazo', 'Pasadas 25 h (simuladas) A puede invitar otra vez', 'sent', pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)));

    -- Aceptar
    perform pg_temp.chk('2 Amistad', 'B acepta', 'accepted', pg_temp.s(pg_temp.f(v_b, 'accept', v_code_a)));
    perform pg_temp.chk('2 Amistad', 'Invitar a quien ya es amigo: already_friends', 'error:already_friends', pg_temp.s(pg_temp.f(v_a, 'invite', v_code_b)));
    v := pg_temp.f(v_a, 'list');
    perform pg_temp.chk('2 Amistad', 'A tiene 1 amigo', '1', jsonb_array_length(v -> 'friends')::text);
    perform pg_temp.chk('2 Amistad', 'El amigo es tst0008_b, racha 0 y sin entrenar hoy', 'tst0008_b|0|false',
      (v -> 'friends' -> 0 ->> 'username') || '|' || (v -> 'friends' -> 0 ->> 'streak') || '|' || (v -> 'friends' -> 0 ->> 'trained_today'));
    perform pg_temp.chk('2 Amistad', 'B tiene 1 amigo', '1', jsonb_array_length(pg_temp.f(v_b, 'list') -> 'friends')::text);

    -- Límite de ritmo: los FALLOS también cuentan (los resultados de negocio no son excepciones: no deshacen el contador)
    delete from public.social_rate_limit where user_id in (v_a, v_b);
    n := 0;
    for i in 1..20 loop
      if pg_temp.s(pg_temp.f(v_a, 'search', 'zz0008_nobody')) = 'error:not_found' then n := n + 1; end if;
    end loop;
    perform pg_temp.chk('2 Ritmo', 'search: 20 búsquedas fallidas seguidas responden not_found', '20', n::text);
    perform pg_temp.chk('2 Ritmo', 'search: la 21 lanza rate_limited', 'exception:rate_limited', pg_temp.s(pg_temp.f(v_a, 'search', 'zz0008_nobody')));
    perform pg_temp.chk('2 Ritmo', 'search: el contador conserva las 20 (los fallos cuentan)', '20',
      (select count::text from public.social_rate_limit where user_id = v_a and action = 'search'));
    perform pg_temp.chk('2 Ritmo', 'search: la 22 sigue bloqueada', 'exception:rate_limited', pg_temp.s(pg_temp.f(v_a, 'search', 'tst0008_b')));

    n := 0;
    for i in 1..20 loop
      if pg_temp.s(pg_temp.f(v_a, 'invite', 'zz0008_nobody')) = 'error:not_found' then n := n + 1; end if;
    end loop;
    perform pg_temp.chk('2 Ritmo', 'invite (amistad): 20 invitaciones fallidas responden not_found', '20', n::text);
    perform pg_temp.chk('2 Ritmo', 'invite (amistad): la 21 lanza rate_limited', 'exception:rate_limited', pg_temp.s(pg_temp.f(v_a, 'invite', 'zz0008_nobody')));

    n := 0;
    for i in 1..20 loop
      if pg_temp.s(pg_temp.p(v_a, 'invite', 'zz0008_nobody')) = 'error:not_found' then n := n + 1; end if;
    end loop;
    perform pg_temp.chk('2 Ritmo', 'pair_invite: 20 invitaciones de pareja fallidas responden not_found', '20', n::text);
    perform pg_temp.chk('2 Ritmo', 'pair_invite: la 21 lanza rate_limited', 'exception:rate_limited', pg_temp.s(pg_temp.p(v_a, 'invite', 'zz0008_nobody')));
    perform pg_temp.chk('2 Ritmo', 'pair_invite: el contador conserva las 20', '20',
      (select count::text from public.social_rate_limit where user_id = v_a and action = 'pair_invite'));

    n := 0;
    for i in 1..20 loop
      if pg_temp.s(pg_temp.l(v_a, 'challenge_send', 'zz0008_nobody')) = 'error:not_found' then n := n + 1; end if;
    end loop;
    perform pg_temp.chk('2 Ritmo', 'challenge_send: 20 retos fallidos responden not_found', '20', n::text);
    perform pg_temp.chk('2 Ritmo', 'challenge_send: la 21 lanza rate_limited', 'exception:rate_limited', pg_temp.s(pg_temp.l(v_a, 'challenge_send', 'zz0008_nobody')));
    perform pg_temp.chk('2 Ritmo', 'challenge_send: el contador conserva las 20', '20',
      (select count::text from public.social_rate_limit where user_id = v_a and action = 'challenge_send'));

    -- Una ventana vencida reinicia el contador
    update public.social_rate_limit set window_start = now() - interval '2 hours' where user_id = v_a;
    perform pg_temp.chk('2 Ritmo', 'Con la ventana vencida (simulada) vuelve a responder', 'ok', pg_temp.s(pg_temp.f(v_a, 'search', 'tst0008_b')));
    delete from public.social_rate_limit where user_id in (v_a, v_b);
  exception when others then
    perform pg_temp.chk('2', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 3. Pareja de racha (hora de Colombia, fecha de completado de la isla, día de aceptación)
  -- ===================================================================================================
  begin
    perform pg_temp.chk('3 Pareja', 'A invita a B a una racha de pareja', 'sent', pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('3 Pareja', 'Invitar otra vez: pair_exists', 'error:pair_exists', pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)));
    perform pg_temp.chk('3 Pareja', 'B la ve como pending_received', 'pending_received', pg_temp.p(v_b, 'overview') -> 'pairs' -> 0 ->> 'status');
    perform pg_temp.chk('3 Pareja', 'A la ve como pending_sent', 'pending_sent', pg_temp.p(v_a, 'overview') -> 'pairs' -> 0 ->> 'status');
    perform pg_temp.chk('3 Pareja', 'B acepta', 'active', pg_temp.s(pg_temp.p(v_b, 'accept', v_code_a)));
    v := pg_temp.p(v_a, 'overview');
    perform pg_temp.chk('3 Pareja', 'Recién aceptada: racha 0, sin racha (none), top_current 0', '0|none|0',
      (v -> 'pairs' -> 0 -> 'metrics' ->> 'current') || '|' || (v -> 'pairs' -> 0 -> 'metrics' ->> 'state') || '|' || (v ->> 'top_current'));

    -- El día de aceptación se retrasa 10 días para poder probar días anteriores
    update public.pair_streaks set accepted_at = now() - interval '10 days';

    -- Caso 1: hoy, a las 23:30 de Colombia (que en UTC ya es el día siguiente): cuenta como HOY
    perform pg_temp.island(v_a, 'CONTRASENAS', pg_temp.bog(v_today, '23:30'));
    perform pg_temp.island(v_b, 'CONTRASENAS', pg_temp.bog(v_today, '23:30'));
    v := pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' -> 'metrics';
    perform pg_temp.chk('3 Racha', 'Los dos completaron hoy (23:30 Bogotá): racha 1, activa', '1|active',
      (v ->> 'current') || '|' || (v ->> 'state'));
    perform pg_temp.chk('3 Racha', 'me_today y partner_today verdaderos; 1 día compartido', 'true|true|1',
      (v ->> 'me_today') || '|' || (v ->> 'partner_today') || '|' || jsonb_array_length(v -> 'shared_days')::text);

    -- Caso 2: ayer
    perform pg_temp.island(v_a, 'PHISHING', pg_temp.bog(v_today - 1, '12:00'));
    perform pg_temp.island(v_b, 'PHISHING', pg_temp.bog(v_today - 1, '12:00'));
    v := pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' -> 'metrics';
    perform pg_temp.chk('3 Racha', 'Ayer y hoy compartidos: racha 2, mejor 2, activa', '2|2|active',
      (v ->> 'current') || '|' || (v ->> 'best') || '|' || (v ->> 'state'));

    -- Caso 3: hace 3 días (con un hueco antes)
    perform pg_temp.island(v_a, 'MALWARE', pg_temp.bog(v_today - 3, '12:00'));
    perform pg_temp.island(v_b, 'MALWARE', pg_temp.bog(v_today - 3, '12:00'));
    v := pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' -> 'metrics';
    perform pg_temp.chk('3 Racha', 'Con un hueco: 3 días compartidos, racha 2, mejor 2', '3|2|2',
      jsonb_array_length(v -> 'shared_days')::text || '|' || (v ->> 'current') || '|' || (v ->> 'best'));

    -- Caso 4: hoy falta B (día sin completar por uno)
    perform pg_temp.unisland(v_b, 'CONTRASENAS');
    v := pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' -> 'metrics';
    perform pg_temp.chk('3 Racha', 'Hoy solo A: racha 1 (viva por ayer), en riesgo (at_risk)', '1|at_risk|true|false',
      (v ->> 'current') || '|' || (v ->> 'state') || '|' || (v ->> 'me_today') || '|' || (v ->> 'partner_today'));

    -- Caso 5: ayer tampoco: la racha se rompe
    perform pg_temp.unisland(v_a, 'PHISHING');
    perform pg_temp.unisland(v_b, 'PHISHING');
    v := pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' -> 'metrics';
    perform pg_temp.chk('3 Racha', 'Solo queda un día compartido hace 3 días: racha 0, mejor 1, none', '0|1|none',
      (v ->> 'current') || '|' || (v ->> 'best') || '|' || (v ->> 'state'));

    -- Caso 6: se restablecen; el contador de afuera y la racha personal del amigo
    perform pg_temp.island(v_b, 'CONTRASENAS', pg_temp.bog(v_today, '23:30'));
    perform pg_temp.island(v_a, 'PHISHING', pg_temp.bog(v_today - 1, '12:00'));
    perform pg_temp.island(v_b, 'PHISHING', pg_temp.bog(v_today - 1, '12:00'));
    v := pg_temp.p(v_a, 'overview');
    perform pg_temp.chk('3 Racha', 'top_current (D15) = 2 con una sola pareja', '2', v ->> 'top_current');
    v2 := pg_temp.f(v_a, 'list');
    perform pg_temp.chk('3 Racha', 'La racha PERSONAL de B visible para A (hora de Colombia): 2 y entrenó hoy', '2|true',
      (v2 -> 'friends' -> 0 ->> 'streak') || '|' || (v2 -> 'friends' -> 0 ->> 'trained_today'));

    -- Caso 7: A completa las 6 islas: ya no puede aportar más días
    perform pg_temp.island(v_a, 'INGENIERIA_SOCIAL', pg_temp.bog(v_today - 8, '12:00'));
    perform pg_temp.island(v_a, 'REDES_SEGURAS', pg_temp.bog(v_today - 9, '12:00'));
    perform pg_temp.island(v_a, 'FIREWALL', pg_temp.bog(v_today - 10, '12:00'));
    v := pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' -> 'metrics';
    perform pg_temp.chk('3 Racha', 'A con las 6 islas: estado route_complete (límite de 6 días, D14)', 'route_complete|2',
      (v ->> 'state') || '|' || (v ->> 'current'));

    -- Caso 8: el día de aceptación cuenta, los anteriores no (O9)
    update public.pair_streaks set accepted_at = now();
    v := pg_temp.p(v_a, 'detail', v_code_b) -> 'pair' -> 'metrics';
    perform pg_temp.chk('3 Racha', 'Aceptada hoy: solo cuenta hoy (1 día compartido, racha 1)', '1|1',
      jsonb_array_length(v -> 'shared_days')::text || '|' || (v ->> 'current'));

    -- Detalle, terminar, vencimiento
    perform pg_temp.chk('3 Pareja', 'detail de una persona inexistente: pair_gone', 'error:pair_gone', pg_temp.s(pg_temp.p(v_a, 'detail', 'zz0008_nobody')));
    perform pg_temp.chk('3 Pareja', 'A termina la pareja', 'ended', pg_temp.s(pg_temp.p(v_a, 'end', v_code_b)));
    perform pg_temp.chk('3 Pareja', 'Ya no hay parejas', '0', jsonb_array_length(pg_temp.p(v_a, 'overview') -> 'pairs')::text);
    perform pg_temp.chk('3 Pareja', 'Invitar de nuevo', 'sent', pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)));
    update public.pair_streaks set expires_at = now() - interval '1 minute';
    perform pg_temp.chk('3 Pareja', 'Una invitación vencida (3 días, simulado) desaparece al consultar', '0',
      jsonb_array_length(pg_temp.p(v_b, 'overview') -> 'pairs')::text);
    perform pg_temp.chk('3 Pareja', 'y se borra de la tabla', '0', (select count(*)::text from public.pair_streaks));
    perform pg_temp.chk('3 Pareja', 'B rechaza una invitación nueva: declined y se borra', 'declined',
      (case when pg_temp.s(pg_temp.p(v_a, 'invite', v_code_b)) = 'sent' then pg_temp.s(pg_temp.p(v_b, 'decline', v_code_a)) else 'no se pudo invitar' end));
    perform pg_temp.chk('3 Pareja', 'Tras rechazar no queda fila', '0', (select count(*)::text from public.pair_streaks));

    -- Limpieza del progreso de prueba
    delete from public.lesson_progress where user_id in (v_a, v_b);
    delete from public.lab_progress where user_id in (v_a, v_b);
  exception when others then
    perform pg_temp.chk('3', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 4. Partida rápida (cola, sugerencia a los 60 s, ampliación del rango)
  -- ===================================================================================================
  begin
    perform pg_temp.wipe_battles(v_a, v_b);

    perform pg_temp.chk('4 Cola', 'A entra a la cola: waiting', 'waiting', pg_temp.s(pg_temp.l(v_a, 'quick_join')));
    update public.matchmaking_queue set joined_at = now() - interval '61 seconds' where user_id = v_a;
    v := pg_temp.l(v_a, 'quick_poll');
    perform pg_temp.chk('4 Cola', 'A los 60 s sin rival se sugiere retar a un amigo (suggest_friend)', 'waiting|true',
      (v ->> 'status') || '|' || (v ->> 'suggest_friend'));
    perform pg_temp.chk('4 Cola', 'La búsqueda sigue en la cola', '1', (select count(*)::text from public.matchmaking_queue where user_id = v_a));
    perform pg_temp.chk('4 Cola', 'A cancela la búsqueda', 'cancelled', pg_temp.s(pg_temp.l(v_a, 'quick_cancel')));
    perform pg_temp.chk('4 Cola', 'La cola queda vacía', '0', (select count(*)::text from public.matchmaking_queue));
    perform pg_temp.chk('4 Cola', 'Sin fila en la cola, quick_poll da none', 'none', pg_temp.s(pg_temp.l(v_a, 'quick_poll')));

    -- Rango ±3 que se amplía: A (nivel forzado a 15) y B (nivel 1)
    perform pg_temp.l(v_a, 'quick_join');
    update public.matchmaking_queue set fighter_level = 15 where user_id = v_a;
    perform pg_temp.chk('4 Rango', 'B (nivel 1) y A (nivel 15): diferencia 14 > 3, no se emparejan', 'waiting', pg_temp.s(pg_temp.l(v_b, 'quick_join')));
    update public.matchmaking_queue set joined_at = now() - interval '130 seconds', last_seen_at = now();
    v := pg_temp.l(v_b, 'quick_poll');
    perform pg_temp.chk('4 Rango', 'Tras 130 s el rango es 3 + 13 = 16: se emparejan', 'matched', pg_temp.s(v));
    bid := (v ->> 'battle_id')::uuid;
    perform pg_temp.chk('4 Rango', 'A recibe la misma batalla', bid::text, pg_temp.l(v_a, 'quick_poll') ->> 'battle_id');
    perform pg_temp.chk('4 Rango', 'La batalla es de modo quick, activa, 100 HP cada uno', 'quick|active|100|100',
      (select mode || '|' || status || '|' || hp1 || '|' || hp2 from public.battles where id = bid));
    perform pg_temp.chk('4 Rango', 'Las dos personas quedan en una batalla activa', '2', (select count(*)::text from public.battle_active where battle_id = bid));
    perform pg_temp.chk('4 Rango', 'Entrar a la cola estando en batalla: in_battle', 'in_battle', pg_temp.s(pg_temp.l(v_a, 'quick_join')));
    perform pg_temp.chk('4 Rango', 'Sin carga, usar la ulti: ulti_not_charged', 'exception:ulti_not_charged',
      pg_temp.s(pg_temp.pl(pg_temp.tu(bid), bid, 'use_ulti')));
    perform pg_temp.wipe_battles(v_a, v_b);

    -- Emparejamiento directo con el mismo nivel
    bid := pg_temp.new_battle(v_a, v_b);
    perform pg_temp.chk('4 Cola', 'Mismo nivel: B entra y se empareja de inmediato', 'true', (bid is not null)::text);
    perform pg_temp.wipe_battles(v_a, v_b);
  exception when others then
    perform pg_temp.chk('4', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 5. Reto a un amigo (2 minutos)
  -- ===================================================================================================
  begin
    perform pg_temp.wipe_battles(v_a, v_b);

    v := pg_temp.l(v_a, 'challenge_send', v_code_b);
    perform pg_temp.chk('5 Reto', 'A reta a B', 'sent|120', (v ->> 'status') || '|' || (v ->> 'seconds_left'));
    perform pg_temp.chk('5 Reto', 'Un segundo reto pendiente: challenge_pending', 'error:challenge_pending', pg_temp.s(pg_temp.l(v_a, 'challenge_send', v_code_b)));
    v := pg_temp.l(v_b, 'challenges');
    perform pg_temp.chk('5 Reto', 'B ve 1 reto recibido de tst0008_a', '1|tst0008_a',
      jsonb_array_length(v -> 'received')::text || '|' || (v -> 'received' -> 0 ->> 'username'));

    -- Vencido a los 2 minutos
    update public.battle_challenges set expires_at = now() - interval '1 second';
    perform pg_temp.chk('5 Reto', 'Un reto vencido no se puede aceptar: challenge_gone', 'error:challenge_gone', pg_temp.s(pg_temp.l(v_b, 'challenge_accept', v_code_a)));
    perform pg_temp.chk('5 Reto', 'A ve su reto como expired', 'expired', pg_temp.l(v_a, 'challenges') -> 'sent' ->> 'status');

    -- Rechazar y cancelar
    perform pg_temp.l(v_a, 'challenge_send', v_code_b);
    perform pg_temp.chk('5 Reto', 'B rechaza', 'declined', pg_temp.s(pg_temp.l(v_b, 'challenge_decline', v_code_a)));
    perform pg_temp.chk('5 Reto', 'A ve su reto como declined', 'declined', pg_temp.l(v_a, 'challenges') -> 'sent' ->> 'status');
    perform pg_temp.l(v_a, 'challenge_send', v_code_b);
    perform pg_temp.chk('5 Reto', 'A cancela su reto', 'cancelled', pg_temp.s(pg_temp.l(v_a, 'challenge_cancel')));

    -- Aceptar: la MISMA batalla que la partida rápida (D6), modo friend
    perform pg_temp.l(v_a, 'challenge_send', v_code_b);
    v := pg_temp.l(v_b, 'challenge_accept', v_code_a);
    perform pg_temp.chk('5 Reto', 'B acepta: se crea la batalla', 'matched', pg_temp.s(v));
    bid := (v ->> 'battle_id')::uuid;
    perform pg_temp.chk('5 Reto', 'Batalla de modo friend, con A (quien reta) como jugador 1', 'friend|true',
      (select mode || '|' || (player1_id = v_a)::text from public.battles where id = bid));
    perform pg_temp.chk('5 Reto', 'A ve la batalla activa en su lobby', bid::text, pg_temp.l(v_a, 'challenges') ->> 'active_battle_id');
    perform pg_temp.chk('5 Reto', 'En batalla, retar de nuevo: already_in_battle', 'error:already_in_battle', pg_temp.s(pg_temp.l(v_a, 'challenge_send', v_code_b)));
    perform pg_temp.chk('5 Reto', 'En batalla, la partida rápida da in_battle', 'in_battle', pg_temp.s(pg_temp.l(v_b, 'quick_join')));
    perform pg_temp.wipe_battles(v_a, v_b);
  exception when others then
    perform pg_temp.chk('5', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 6. Batalla: respuestas, daño, carga de la ulti (3 aciertos), ulti con ESCUDO y fin por HP
  --   f = quien tiene el primer turno (defensor de la ulti); s = el otro (atacante, acierta 3 veces seguidas)
  -- ===================================================================================================
  begin
    bid := pg_temp.new_battle(v_a, v_b);
    f := pg_temp.tu(bid);
    s := pg_temp.other(bid, f);

    -- La pregunta SIN la respuesta correcta (D7)
    v := pg_temp.sy(f, bid);
    perform pg_temp.chk('6 Pregunta', 'Quien tiene el turno recibe la pregunta con 4 opciones', 'true|4',
      (v ? 'question')::text || '|' || (select count(*)::text from jsonb_object_keys(v -> 'question' -> 'options')));
    perform pg_temp.chk('6 Pregunta', 'La respuesta correcta NO viaja (ninguna clave "correct")', 'false', (v::text like '%"correct"%')::text);
    perform pg_temp.chk('6 Pregunta', 'Es su turno y le quedan entre 55 y 60 s', 'true|true',
      (v -> 'turn' ->> 'is_mine') || '|' || (((v -> 'turn' ->> 'seconds_left')::numeric between 55 and 60))::text);
    v2 := pg_temp.sy(s, bid);
    perform pg_temp.chk('6 Pregunta', 'El otro jugador NO recibe pregunta (pantalla de espera)', 'false|false',
      (v2 ? 'question')::text || '|' || (v2 -> 'turn' ->> 'is_mine'));
    perform pg_temp.chk('6 Pregunta', 'La vista incluye la hora del servidor (server_now)', 'true', (v ? 'server_now')::text);

    -- Validaciones
    perform pg_temp.chk('6 Respuesta', 'Respuesta de un turno viejo: stale_turn', 'exception:stale_turn', pg_temp.s(pg_temp.pl(f, bid, 'answer', 5, 'A')));
    perform pg_temp.chk('6 Respuesta', 'Responder sin ser tu turno: not_your_turn', 'exception:not_your_turn', pg_temp.s(pg_temp.pl(s, bid, 'answer', 1, 'A')));
    perform pg_temp.chk('6 Respuesta', 'Opción inválida: bad_option', 'exception:bad_option', pg_temp.s(pg_temp.pl(f, bid, 'answer', 1, 'Z')));

    -- Turno 1 (f): incorrecta -> sin daño, pasa el turno
    v := pg_temp.ans(bid, f, 'B');
    perform pg_temp.chk('6 Respuesta', 'Incorrecta: outcome incorrect, daño 0', 'incorrect|0', (v -> 'last_turn' ->> 'outcome') || '|' || (v -> 'last_turn' ->> 'damage'));
    perform pg_temp.chk('6 Respuesta', 'HP intacto (100 y 100) y el turno 2 es del otro', '100|100|2|true',
      pg_temp.hp(bid, f) || '|' || pg_temp.hp(bid, s) || '|' || (select turn_number from public.battles where id = bid) || '|' || (pg_temp.tu(bid) = s)::text);
    -- Turno 2 (s): correcta -> -20 HP al rival
    v := pg_temp.ans(bid, s, 'A');
    perform pg_temp.chk('6 Respuesta', 'Correcta: outcome correct, daño 20', 'correct|20', (v -> 'last_turn' ->> 'outcome') || '|' || (v -> 'last_turn' ->> 'damage'));
    perform pg_temp.chk('6 Respuesta', 'El rival queda en 80 HP (visto por quien acertó) y la racha de aciertos es 1', '80|1',
      (v -> 'rival' ->> 'hp') || '|' || (v -> 'me' ->> 'correct_streak'));

    -- Turnos 3 a 6: f falla, s acierta dos veces más -> 3 seguidos: ulti cargada
    perform pg_temp.ans(bid, f, 'B');
    perform pg_temp.ans(bid, s, 'A');
    perform pg_temp.ans(bid, f, 'B');
    v := pg_temp.ans(bid, s, 'A');
    perform pg_temp.chk('6 Ulti', '3 aciertos seguidos: ulti cargada, racha 3', 'true|3', (v -> 'me' ->> 'ulti_charged') || '|' || (v -> 'me' ->> 'correct_streak'));
    perform pg_temp.chk('6 Ulti', 'El rival está en 40 HP (3 aciertos x 20)', '40', pg_temp.hp(bid, f)::text);

    -- Turno 7 (f) falla; turno 8 (s) "guarda para después": responde una pregunta normal y la ulti SIGUE cargada
    perform pg_temp.ans(bid, f, 'B');
    v := pg_temp.ans(bid, s, 'B');
    perform pg_temp.chk('6 Ulti', 'Responder una pregunta normal con la ulti cargada no la gasta', 'true', v -> 'me' ->> 'ulti_charged');
    perform pg_temp.ans(bid, f, 'B');

    -- Turno 10 (s): usa la ulti
    v := pg_temp.pl(s, bid, 'use_ulti');
    perform pg_temp.chk('6 Ulti', 'La ulti empieza: fase ulti, rol atacante, 3 intentos', 'ulti|attacker|3',
      (v -> 'turn' ->> 'phase') || '|' || (v -> 'ulti' ->> 'role') || '|' || (v -> 'ulti' ->> 'attempts_left'));
    perform pg_temp.chk('6 Ulti', 'El atacante ve SOLO el título de su reto', 'TEST-0008 ataque', v -> 'ulti' ->> 'objective');
    v2 := pg_temp.sy(f, bid);
    perform pg_temp.chk('6 Ulti', 'El defensor recibe a la vez SU reto (distinto)', 'defender|TEST-0008 defensa',
      (v2 -> 'ulti' ->> 'role') || '|' || (v2 -> 'ulti' ->> 'objective'));
    perform pg_temp.chk('6 Ulti', 'Ninguna vista contiene el comando esperado (no viaja al teléfono)', 'false',
      ((v::text || v2::text) ilike '%test-atk%' or (v::text || v2::text) ilike '%test-def%')::text);

    -- El defensor falla una vez y acierta a la segunda (con otras mayúsculas y espacios): el escudo queda armado
    v2 := pg_temp.pl(f, bid, 'ulti_submit', null, 'x');
    perform pg_temp.chk('6 Ulti', 'Defensor: un comando incorrecto consume un intento y no termina el reto', '2|pending',
      (v2 -> 'ulti' ->> 'attempts_left') || '|' || (v2 -> 'ulti' ->> 'my_result'));
    v2 := pg_temp.pl(f, bid, 'ulti_submit', null, '  TEST-DEF   two ');
    perform pg_temp.chk('6 Ulti', 'Defensor acierta (primera palabra sin distinguir mayúsculas, espacios normalizados)', 'solved', v2 -> 'ulti' ->> 'my_result');

    -- El atacante falla una vez y acierta después: ESCUDO -> mitad redondeada = 23
    v := pg_temp.pl(s, bid, 'ulti_submit', null, 'foo');
    perform pg_temp.chk('6 Ulti', 'Atacante: un fallo consume un intento (quedan 2)', '2', v -> 'ulti' ->> 'attempts_left');
    v := pg_temp.pl(s, bid, 'ulti_submit', null, 'Test-Atk one');
    perform pg_temp.chk('6 Ulti', 'Atacante acierta DESPUÉS del defensor: daño 23 con escudo', 'ulti|23|true',
      (v -> 'last_turn' ->> 'outcome') || '|' || (v -> 'last_turn' ->> 'damage') || '|' || (v -> 'last_ulti' ->> 'shield'));
    perform pg_temp.chk('6 Ulti', 'El defensor queda en 17 HP (40 - 23)', '17', pg_temp.hp(bid, f)::text);
    perform pg_temp.chk('6 Ulti', 'El atacante pierde la carga y la racha; el turno pasa al defensor', 'false|0|true',
      (select (case when seat = 1 then ulti_charged1 else ulti_charged2 end)::text || '|' || (case when seat = 1 then correct_streak1 else correct_streak2 end)::text || '|' || (turn_seat <> seat)::text
         from public.battles, (select pg_temp.seat(bid, s) as seat) x where id = bid));

    -- Turno 11 (f) falla; turno 12 (s) acierta: f llega a 0 HP, termina la batalla por HP
    perform pg_temp.ans(bid, f, 'B');
    perform pg_temp.ans(bid, s, 'A');
    perform pg_temp.chk('6 Fin', 'HP en 0: batalla terminada por hp_zero, gana el atacante', 'finished|hp_zero|true',
      (select status || '|' || end_reason || '|' || (winner_seat = pg_temp.seat(bid, s))::text from public.battles where id = bid));
    perform pg_temp.chk('6 Fin', 'XP: ganador +50 (100 HP restantes), perdedor +10 (0 HP)', '50|100|10|0',
      (select xp_awarded || '|' || hp_left from public.battle_results where battle_id = bid and user_id = s) || '|' ||
      (select xp_awarded || '|' || hp_left from public.battle_results where battle_id = bid and user_id = f));
    perform pg_temp.chk('6 Fin', 'Resultados: win y loss', 'win|loss',
      (select result from public.battle_results where battle_id = bid and user_id = s) || '|' ||
      (select result from public.battle_results where battle_id = bid and user_id = f));
    perform pg_temp.chk('6 Fin', 'Nadie queda en una batalla activa', '0', (select count(*)::text from public.battle_active where battle_id = bid));
    v := pg_temp.sy(f, bid);
    perform pg_temp.chk('6 Fin', 'La vista del perdedor trae el resultado: loss, 10 XP, nivel 1', 'loss|10|1',
      (v -> 'result' ->> 'outcome') || '|' || (v -> 'result' ->> 'xp') || '|' || (v -> 'result' ->> 'level'));
    perform pg_temp.chk('6 Fin', '"Repasa X y Y": el tema de las preguntas falladas', '["CONTRASENAS"]', (v -> 'result' -> 'weak_topics')::text);
    perform pg_temp.chk('6 Fin', 'Responder en una batalla terminada: battle_over', 'exception:battle_over', pg_temp.s(pg_temp.pl(s, bid, 'answer', 13, 'A')));
    perform pg_temp.wipe_battles(v_a, v_b);
  exception when others then
    perform pg_temp.chk('6', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 7. Variantes de la ulti (cada una con una batalla nueva y la ulti ya cargada)
  --   x = atacante (primer turno); y = defensor
  -- ===================================================================================================
  begin
    -- 7a. Sin escudo: el defensor no resolvió antes -> 45 HP; su acierto tardío se rechaza
    bid := pg_temp.charged_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    perform pg_temp.pl(f, bid, 'use_ulti');
    v := pg_temp.pl(f, bid, 'ulti_submit', null, 'test-atk one');
    perform pg_temp.chk('7a Ulti sin escudo', 'El atacante acierta primero: daño 45, sin escudo', 'ulti|45|false',
      (v -> 'last_turn' ->> 'outcome') || '|' || (v -> 'last_turn' ->> 'damage') || '|' || (v -> 'last_ulti' ->> 'shield'));
    perform pg_temp.chk('7a Ulti sin escudo', 'El defensor queda en 55 HP', '55', pg_temp.hp(bid, s)::text);
    perform pg_temp.chk('7a Ulti sin escudo', 'El defensor que acierta tarde (turno ya pasado) se rechaza: no_ulti', 'exception:no_ulti',
      pg_temp.s(pg_temp.pl(s, bid, 'ulti_submit', null, 'test-def two')));

    -- 7b. El atacante agota los 3 intentos: no pasa nada y pierde la carga
    bid := pg_temp.charged_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    perform pg_temp.pl(f, bid, 'use_ulti');
    perform pg_temp.pl(f, bid, 'ulti_submit', null, 'mal1');
    perform pg_temp.pl(f, bid, 'ulti_submit', null, 'mal2');
    v := pg_temp.pl(f, bid, 'ulti_submit', null, 'mal3');
    perform pg_temp.chk('7b 3 intentos fallidos', 'Al tercer fallo la ulti falla: daño 0', 'ulti|0|failed',
      (v -> 'last_turn' ->> 'outcome') || '|' || (v -> 'last_turn' ->> 'damage') || '|' || (v -> 'last_ulti' ->> 'attacker_result'));
    perform pg_temp.chk('7b 3 intentos fallidos', 'El defensor sigue en 100 HP', '100', pg_temp.hp(bid, s)::text);
    perform pg_temp.chk('7b 3 intentos fallidos', 'El atacante pierde la carga y el turno pasa', 'false|2',
      (select (case when pg_temp.seat(bid, f) = 1 then ulti_charged1 else ulti_charged2 end)::text || '|' || turn_number from public.battles where id = bid));

    -- 7c. El atacante falla aunque el defensor ya hubiera resuelto: no hay daño ni escudo
    bid := pg_temp.charged_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    perform pg_temp.pl(f, bid, 'use_ulti');
    perform pg_temp.pl(s, bid, 'ulti_submit', null, 'test-def two');
    perform pg_temp.pl(f, bid, 'ulti_submit', null, 'mal1');
    perform pg_temp.pl(f, bid, 'ulti_submit', null, 'mal2');
    v := pg_temp.pl(f, bid, 'ulti_submit', null, 'mal3');
    perform pg_temp.chk('7c Fallo con escudo armado', 'Atacante sin intentos: daño 0 aunque el defensor acertó', '0|solved|100',
      (v -> 'last_turn' ->> 'damage') || '|' || (v -> 'last_ulti' ->> 'defender_result') || '|' || pg_temp.hp(bid, s));

    -- 7d. El defensor agota sus 3 intentos: sin escudo; el atacante acierta -> 45
    bid := pg_temp.charged_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    perform pg_temp.pl(f, bid, 'use_ulti');
    perform pg_temp.pl(s, bid, 'ulti_submit', null, 'mal1');
    perform pg_temp.pl(s, bid, 'ulti_submit', null, 'mal2');
    v := pg_temp.pl(s, bid, 'ulti_submit', null, 'mal3');
    perform pg_temp.chk('7d Defensor sin intentos', 'El defensor agota sus 3 intentos: su resultado es failed', 'failed', v -> 'ulti' ->> 'my_result');
    perform pg_temp.chk('7d Defensor sin intentos', 'Un cuarto envío: already_resolved', 'exception:already_resolved',
      pg_temp.s(pg_temp.pl(s, bid, 'ulti_submit', null, 'test-def two')));
    v := pg_temp.pl(f, bid, 'ulti_submit', null, 'test-atk one');
    perform pg_temp.chk('7d Defensor sin intentos', 'El atacante acierta: 45 HP, sin escudo', '45|false',
      (v -> 'last_turn' ->> 'damage') || '|' || (v -> 'last_ulti' ->> 'shield'));

    -- 7e. Por tiempo: pasan los 2 minutos sin acierto del atacante
    bid := pg_temp.charged_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    perform pg_temp.pl(f, bid, 'use_ulti');
    update public.battle_ultis set deadline = now() - interval '10 seconds' where battle_id = bid;
    v := pg_temp.sy(s, bid);
    perform pg_temp.chk('7e Ulti por tiempo', 'Vencido el plazo (simulado), la ulti falla al siguiente sondeo', 'ulti|0|failed',
      (v -> 'last_turn' ->> 'outcome') || '|' || (v -> 'last_turn' ->> 'damage') || '|' || (v -> 'last_ulti' ->> 'attacker_result'));
    perform pg_temp.chk('7e Ulti por tiempo', 'Sin daño, carga perdida y el turno pasa a la fase de pregunta', '100|false|question',
      pg_temp.hp(bid, s) || '|' || (select (case when pg_temp.seat(bid, f) = 1 then ulti_charged1 else ulti_charged2 end)::text from public.battles where id = bid)
      || '|' || (select turn_phase from public.battles where id = bid));

    -- 7f. Fin por HP con la ulti (45 >= 45)
    bid := pg_temp.charged_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    update public.battles set hp1 = case when player1_id = s then 45 else hp1 end, hp2 = case when player2_id = s then 45 else hp2 end where id = bid;
    perform pg_temp.pl(f, bid, 'use_ulti');
    perform pg_temp.pl(f, bid, 'ulti_submit', null, 'test-atk one');
    perform pg_temp.chk('7f Fin con ulti', 'Con 45 HP el impacto de 45 termina la batalla por hp_zero', 'finished|hp_zero|true',
      (select status || '|' || end_reason || '|' || (winner_seat = pg_temp.seat(bid, f))::text from public.battles where id = bid));
    perform pg_temp.wipe_battles(v_a, v_b);
  exception when others then
    perform pg_temp.chk('7', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 8. Plazos y desconexión (sondeo de 2 a 3 s; más de 60 s sin llamadas = desconectado)
  -- ===================================================================================================
  begin
    -- 8a. Turno vencido de quien SÍ sondea: pierde el turno, no la batalla
    bid := pg_temp.new_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    update public.battles
       set correct_streak1 = case when turn_seat = 1 then 2 else correct_streak1 end,
           correct_streak2 = case when turn_seat = 2 then 2 else correct_streak2 end,
           turn_deadline = now() - interval '10 seconds'
     where id = bid;
    v := pg_temp.sy(s, bid);
    perform pg_temp.chk('8a Turno vencido', 'Se registra el turno perdido (timeout), la batalla sigue activa', '1|2|active',
      (select count(*)::text from public.battle_turns where battle_id = bid and outcome = 'timeout' and seat = pg_temp.seat(bid, f))
      || '|' || (select turn_number from public.battles where id = bid) || '|' || (select status from public.battles where id = bid));
    perform pg_temp.chk('8a Turno vencido', 'Sin daño y la racha de aciertos de quien perdió el turno vuelve a 0', '100|100|0',
      pg_temp.hp(bid, f) || '|' || pg_temp.hp(bid, s) || '|'
      || (select (case when pg_temp.seat(bid, f) = 1 then correct_streak1 else correct_streak2 end)::text from public.battles where id = bid));
    perform pg_temp.chk('8a Turno vencido', 'El siguiente jugador recibe 55 a 60 s completos', 'true|true',
      (v -> 'turn' ->> 'is_mine') || '|' || (((v -> 'turn' ->> 'seconds_left')::numeric between 55 and 60))::text);
    perform pg_temp.wipe_battles(v_a, v_b);

    -- 8b. Orden: turno vencido Y desconectado (más de 60 s sin llamadas): pierde la BATALLA, no solo el turno
    bid := pg_temp.new_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    update public.battles
       set last_seen1 = case when player1_id = f then now() - interval '70 seconds' else last_seen1 end,
           last_seen2 = case when player2_id = f then now() - interval '70 seconds' else last_seen2 end,
           turn_deadline = now() - interval '10 seconds'
     where id = bid;
    perform pg_temp.sy(s, bid);
    perform pg_temp.chk('8b Desconexión antes que turno', 'f no sondeó en 70 s y su turno venció: la batalla termina por desconexión', 'finished|forfeit_disconnect|true',
      (select status || '|' || end_reason || '|' || (winner_seat = pg_temp.seat(bid, s))::text from public.battles where id = bid));
    perform pg_temp.chk('8b Desconexión antes que turno', 'No se registró un turno perdido (se evaluó primero la desconexión)', '0',
      (select count(*)::text from public.battle_turns where battle_id = bid));
    perform pg_temp.chk('8b Desconexión antes que turno', 'XP: gana +50 (ver limitación de farmeo); quien se desconecta 0', '50|0',
      (select xp_awarded::text from public.battle_results where battle_id = bid and user_id = s) || '|' ||
      (select xp_awarded::text from public.battle_results where battle_id = bid and user_id = f));
    perform pg_temp.wipe_battles(v_a, v_b);

    -- 8c. Un jugador desconectado, sin turno vencido
    bid := pg_temp.new_battle(v_a, v_b);
    update public.battles set last_seen2 = now() - interval '61 seconds' where id = bid;
    perform pg_temp.sy((select player1_id from public.battles where id = bid), bid);
    perform pg_temp.chk('8c Desconexión', 'Con 61 s sin llamadas el jugador 2 pierde: gana el 1 (forfeit_disconnect)', 'finished|forfeit_disconnect|1',
      (select status || '|' || end_reason || '|' || winner_seat from public.battles where id = bid));
    perform pg_temp.wipe_battles(v_a, v_b);

    -- 8d. Con menos de 60 s todavía no (la gracia es de un minuto; se usan 40 s para dejar margen al propio guion)
    bid := pg_temp.new_battle(v_a, v_b);
    update public.battles set last_seen2 = now() - interval '40 seconds' where id = bid;
    perform pg_temp.sy((select player1_id from public.battles where id = bid), bid);
    perform pg_temp.chk('8d Gracia', 'Con 40 s sin llamadas la batalla sigue activa', 'active', (select status from public.battles where id = bid));
    perform pg_temp.wipe_battles(v_a, v_b);

    -- 8e. Los dos desconectados: se cancela, sin ganador ni XP (O2); la resuelve el lobby de quien vuelve
    bid := pg_temp.new_battle(v_a, v_b);
    update public.battles set last_seen1 = now() - interval '61 seconds', last_seen2 = now() - interval '61 seconds' where id = bid;
    v := pg_temp.l(v_a, 'challenges');
    perform pg_temp.chk('8e Ambos desconectados', 'Batalla cancelada, both_disconnected, sin ganador', 'cancelled|both_disconnected|true',
      (select status || '|' || end_reason || '|' || (winner_seat is null)::text from public.battles where id = bid));
    perform pg_temp.chk('8e Ambos desconectados', 'Sin filas de resultado (no cuenta en el marcador ni da XP)', '0',
      (select count(*)::text from public.battle_results where battle_id = bid));
    perform pg_temp.chk('8e Ambos desconectados', 'Y nadie queda "en batalla"', 'null', jsonb_typeof(v -> 'active_battle_id'));
    perform pg_temp.wipe_battles(v_a, v_b);

    -- 8f. Abandono: pierde quien abandona
    bid := pg_temp.new_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    perform pg_temp.pl(f, bid, 'abandon');
    perform pg_temp.chk('8f Abandono', 'Gana el otro por abandon', 'finished|abandon|true',
      (select status || '|' || end_reason || '|' || (winner_seat = pg_temp.seat(bid, s))::text from public.battles where id = bid));
    perform pg_temp.chk('8f Abandono', 'XP: ganador +50, quien abandona 0', '50|0',
      (select xp_awarded::text from public.battle_results where battle_id = bid and user_id = s) || '|' ||
      (select xp_awarded::text from public.battle_results where battle_id = bid and user_id = f));
    perform pg_temp.wipe_battles(v_a, v_b);

    -- 8g. Fin por 20 turnos: gana quien tenga más HP
    bid := pg_temp.new_battle(v_a, v_b);
    f := pg_temp.tu(bid);  s := pg_temp.other(bid, f);
    update public.battles set turn_number = 20, hp1 = 70, hp2 = 60 where id = bid;
    perform pg_temp.ans(bid, pg_temp.tu(bid), 'B');
    perform pg_temp.chk('8g 20 turnos', 'Al terminar el turno 20 gana el de más HP (jugador 1 con 70 contra 60)', 'finished|turn_limit|1',
      (select status || '|' || end_reason || '|' || winner_seat from public.battles where id = bid));
    perform pg_temp.chk('8g 20 turnos', 'XP: +50 y +10', '50|10',
      (select xp_awarded::text from public.battle_results r join public.battles b on b.id = r.battle_id where r.battle_id = bid and r.user_id = b.player1_id) || '|' ||
      (select xp_awarded::text from public.battle_results r join public.battles b on b.id = r.battle_id where r.battle_id = bid and r.user_id = b.player2_id));
    perform pg_temp.wipe_battles(v_a, v_b);

    -- 8h. Empate por 20 turnos con el mismo HP
    bid := pg_temp.new_battle(v_a, v_b);
    update public.battles set turn_number = 20, hp1 = 60, hp2 = 60 where id = bid;
    perform pg_temp.ans(bid, pg_temp.tu(bid), 'B');
    perform pg_temp.chk('8h Empate', 'Mismo HP: finished, turn_limit, sin ganador', 'finished|turn_limit|true',
      (select status || '|' || end_reason || '|' || (winner_seat is null)::text from public.battles where id = bid));
    perform pg_temp.chk('8h Empate', 'Dos resultados draw con +25 XP cada uno', '2|50',
      (select count(*)::text from public.battle_results where battle_id = bid and result = 'draw') || '|' ||
      (select sum(xp_awarded)::text from public.battle_results where battle_id = bid));
    perform pg_temp.wipe_battles(v_a, v_b);
  exception when others then
    perform pg_temp.chk('8', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 9. XP y nivel de combatiente (curva de prueba: 1 con 0 XP, 2 con 100, 3 con 200)
  -- ===================================================================================================
  begin
    -- A abandona una batalla (B gana +50, A 0) y B abandona otra (A gana +50, B 0)
    bid := pg_temp.new_battle(v_a, v_b);
    perform pg_temp.pl(v_a, bid, 'abandon');
    perform pg_temp.l(v_a, 'quick_join');
    bid2 := (pg_temp.l(v_b, 'quick_join') ->> 'battle_id')::uuid;
    update public.battles set last_seen1 = clock_timestamp(), last_seen2 = clock_timestamp() where id = bid2;
    perform pg_temp.pl(v_b, bid2, 'abandon');

    perform pg_temp.chk('9 XP', 'B ve sus 2 resultados y NINGUNO de A (RLS de dueño)', '2|0',
      pg_temp.scalar('authenticated', v_b, 'select count(*)::text from public.battle_results') || '|' ||
      pg_temp.scalar('authenticated', v_b, format('select count(*)::text from public.battle_results where user_id = %L', v_a)));
    perform pg_temp.chk('9 XP', 'authenticated no puede insertar resultados (XP solo lo escribe el servidor)', 'ERR:42501',
      pg_temp.run('authenticated', v_b, format('insert into public.battle_results (battle_id, user_id, result, xp_awarded, hp_left, opponent_username) values (%L, %L, ''win'', 999, 100, ''x'')', bid, v_b)));
    perform pg_temp.chk('9 XP', 'El resultado guarda el nombre del rival (sin exponer su id)', 'tst0008_a',
      (select opponent_username from public.battle_results where battle_id = bid and user_id = v_b));

    -- XP total de B = 50 (ganó el primero) + 0 (abandonó el segundo) = 50 -> nivel 1
    v_xp := (select coalesce(sum(xp_awarded), 0) from public.battle_results where user_id = v_b);
    perform pg_temp.chk('9 XP', 'XP total de B = 50', '50', v_xp::text);
    perform pg_temp.chk('9 Nivel', 'Nivel de B con 50 XP = 1', '1', public.sx_user_level(v_b)::text);

    -- Se sube el XP de B a 150 y 250 para probar la curva
    update public.battle_results set xp_awarded = 150 where battle_id = bid and user_id = v_b;
    v_xp := (select sum(xp_awarded) from public.battle_results where user_id = v_b);
    v_exp_lvl := least(3, 1 + v_xp / 100);
    perform pg_temp.chk('9 Nivel', 'Con 150 XP el nivel es 2', '2', public.sx_user_level(v_b)::text);
    perform pg_temp.chk('9 Nivel', 'El nivel coincide con la cuenta del guion', v_exp_lvl::text, public.sx_user_level(v_b)::text);
    perform pg_temp.l(v_b, 'quick_join');
    perform pg_temp.chk('9 Nivel', 'El emparejamiento usa el nivel de combatiente (cola con nivel 2)', '2',
      (select fighter_level::text from public.matchmaking_queue where user_id = v_b));
    perform pg_temp.l(v_b, 'quick_cancel');
    update public.battle_results set xp_awarded = 250 where battle_id = bid and user_id = v_b;
    perform pg_temp.chk('9 Nivel', 'Con 250 XP el nivel es 3 (tope de la curva de prueba)', '3', public.sx_user_level(v_b)::text);
    perform pg_temp.chk('9 Nivel', 'El nivel de B visto en una batalla terminada (battle_sync) es 3', '3',
      pg_temp.sy(v_b, bid) -> 'result' ->> 'level');
    perform pg_temp.wipe_battles(v_a, v_b);
  exception when others then
    perform pg_temp.chk('9', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 10. Perfil del amigo, historial y "ocultar mi progreso público" (D11)
  -- ===================================================================================================
  begin
    -- Un historial entre los dos: A gana por abandono de B
    bid := pg_temp.new_battle(v_a, v_b);
    perform pg_temp.pl(v_b, bid, 'abandon');

    -- Progreso de B: 2 islas, 1 intento de examen perfecto
    perform pg_temp.island(v_b, 'CONTRASENAS', pg_temp.bog(v_today, '10:00'));
    perform pg_temp.island(v_b, 'PHISHING', pg_temp.bog(v_today - 1, '10:00'));
    insert into public.exam_attempts (user_id, correct_count) values (v_b, 6);

    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('10 Perfil', 'Perfil visible: hidden false', 'false', v ->> 'hidden');
    perform pg_temp.chk('10 Perfil', 'Lecciones vistas y labs completados (los campos de BadgeFacts)', '["CONTRASENAS", "PHISHING"]|["CONTRASENAS", "PHISHING"]',
      (v -> 'progress' -> 'viewed_lessons')::text || '|' || (v -> 'progress' -> 'completed_labs')::text);
    perform pg_temp.chk('10 Perfil', 'Examen aprobado y perfecto, mejor racha 2', 'true|true|2',
      (v -> 'progress' ->> 'exam_passed') || '|' || (v -> 'progress' ->> 'exam_perfect') || '|' || (v -> 'progress' ->> 'best_streak'));
    perform pg_temp.chk('10 Perfil', 'Racha personal 2 y entrenó hoy', '2|true', (v ->> 'streak') || '|' || (v ->> 'trained_today'));
    perform pg_temp.chk('10 Perfil', 'Historial entre los dos: 1 batalla, ganada por A', '1|win',
      jsonb_array_length(v -> 'history')::text || '|' || (v -> 'history' -> 0 ->> 'result'));

    -- B oculta su progreso
    perform pg_temp.chk('10 Ocultar', 'B activa "ocultar mi progreso público" (UPDATE por columna)', 'ok',
      pg_temp.run('authenticated', v_b, format('update public.social_profile set hide_progress = true where user_id = %L', v_b)));
    v := pg_temp.f(v_a, 'profile', v_code_b);
    perform pg_temp.chk('10 Ocultar', 'Oculto: hidden true y el bloque de progreso (nivel, insignias, islas) es null', 'true|null',
      (v ->> 'hidden') || '|' || jsonb_typeof(v -> 'progress'));
    perform pg_temp.chk('10 Ocultar', 'Sigue visible: nombre, racha, si entrenó hoy e historial (D11)', 'tst0008_b|2|true|1',
      (v ->> 'username') || '|' || (v ->> 'streak') || '|' || (v ->> 'trained_today') || '|' || jsonb_array_length(v -> 'history')::text);
    perform pg_temp.run('authenticated', v_b, format('update public.social_profile set hide_progress = false where user_id = %L', v_b));
    perform pg_temp.chk('10 Ocultar', 'Al mostrarlo de nuevo vuelve el progreso', 'object', jsonb_typeof(pg_temp.f(v_a, 'profile', v_code_b) -> 'progress'));
    perform pg_temp.wipe_battles(v_a, v_b);
  exception when others then
    perform pg_temp.chk('10', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- BLOQUE 11. Eliminar al amigo: termina la pareja y cancela los retos pendientes
  -- ===================================================================================================
  begin
    perform pg_temp.p(v_a, 'invite', v_code_b);
    perform pg_temp.p(v_b, 'accept', v_code_a);
    perform pg_temp.l(v_a, 'challenge_send', v_code_b);
    perform pg_temp.chk('11 Eliminar', 'A elimina a B', 'removed', pg_temp.s(pg_temp.f(v_a, 'remove', v_code_b)));
    perform pg_temp.chk('11 Eliminar', 'Termina la pareja de racha (sin filas)', '0', (select count(*)::text from public.pair_streaks));
    perform pg_temp.chk('11 Eliminar', 'El reto pendiente queda cancelado', 'cancelled', (select status from public.battle_challenges order by id desc limit 1));
    perform pg_temp.chk('11 Eliminar', 'Ya no son amigos: la lista está vacía', '0', jsonb_array_length(pg_temp.f(v_a, 'list') -> 'friends')::text);
    perform pg_temp.chk('11 Eliminar', 'Y el perfil del ex amigo da not_found', 'error:not_found', pg_temp.s(pg_temp.f(v_a, 'profile', v_code_b)));
  exception when others then
    perform pg_temp.chk('11', 'EXCEPCIÓN INESPERADA EN EL GUION', 'sin excepción', sqlerrm || ' (' || sqlstate || ')');
  end;

  -- ===================================================================================================
  -- LIMPIEZA: borra todo lo que creó el guion y restaura el banco, los retos y la curva reales (si los había)
  -- ===================================================================================================
  perform pg_temp.wipe_battles(v_a, v_b);
  delete from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b);
  delete from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b);
  delete from public.social_rate_limit where user_id in (v_a, v_b);
  delete from public.exam_attempts where user_id in (v_a, v_b);
  delete from public.lab_progress where user_id in (v_a, v_b);
  delete from public.lesson_progress where user_id in (v_a, v_b);
  delete from public.social_profile where user_id in (v_a, v_b);

  delete from public.battle_questions where question like 'TEST-0008%';
  update public.battle_questions set active = true where id in (select id from pg_temp.t_saved_q);
  delete from public.ulti_challenges where objective_title like 'TEST-0008%';
  update public.ulti_challenges set active = true where id in (select id from pg_temp.t_saved_u);
  delete from public.fighter_level_curve;
  insert into public.fighter_level_curve (level, min_xp) select level, min_xp from pg_temp.t_saved_curve;

  perform pg_temp.chk('Limpieza', 'No queda ningún perfil de prueba', '0', (select count(*)::text from public.social_profile where user_id in (v_a, v_b)));
  perform pg_temp.chk('Limpieza', 'No queda ninguna pregunta ni reto de prueba', '0|0',
    (select count(*)::text from public.battle_questions where question like 'TEST-0008%') || '|' ||
    (select count(*)::text from public.ulti_challenges where objective_title like 'TEST-0008%'));
  perform pg_temp.chk('Limpieza', 'No queda ninguna batalla, amistad ni pareja de prueba', '0|0|0',
    (select count(*)::text from public.battles where player1_id in (v_a, v_b) or player2_id in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.friendships where user_low in (v_a, v_b) or user_high in (v_a, v_b)) || '|' ||
    (select count(*)::text from public.pair_streaks where user_low in (v_a, v_b) or user_high in (v_a, v_b)));
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
-- delete from public.social_rate_limit where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.exam_attempts where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.lab_progress where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.lesson_progress where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.social_profile where user_id in ('<ID_A>', '<ID_B>');
-- delete from public.battle_questions where question like 'TEST-0008%';
-- delete from public.ulti_challenges where objective_title like 'TEST-0008%';
-- -- Si había banco real, vuelve a activarlo a mano: update public.battle_questions set active = true; (y lo mismo en ulti_challenges)
