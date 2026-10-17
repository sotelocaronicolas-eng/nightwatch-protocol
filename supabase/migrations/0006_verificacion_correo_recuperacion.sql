-- =====================================================================================================
-- NightWatch Protocol - Migración 0006: verificación del correo de recuperación con un código
--
-- NO se ejecuta desde la app ni desde el repositorio: se revisa y se pega a mano en el SQL Editor,
-- DESPUÉS de 0001 a 0005 y una sola vez.
--
-- PROBLEMA: Supabase Auth solo envía códigos al correo PRINCIPAL del usuario. El correo de recuperación
-- (public.profile.recovery_email) es un correo alterno, así que para verificarlo hay que enviar el código por
-- fuera de Auth. El envío lo hace la Edge Function send-recovery-email-code (usa la API de Brevo) y esta
-- migración pone el resto:
--
--   1. public.recovery_email_codes   el código pendiente de cada usuario (solo su HASH, nunca el código).
--   2. public.profile                recovery_email deja de poder escribirse desde la app: solo se llena al
--                                    verificar un código (punto 3).
--   3. public.verify_recovery_email_code(p_code)   compara el código, cuenta los intentos y, si coincide,
--                                    guarda el correo en el perfil.
--
-- QUIÉN TOCA QUÉ:
--   * La tabla del punto 1 no tiene NINGÚN privilegio para anon ni authenticated. La escribe la Edge Function
--     con la llave service_role (que se salta RLS) y la lee la función del punto 3 (SECURITY DEFINER).
--   * La app solo puede llamar a la función del punto 3, y solo con sesión (authenticated).
--
-- FORMATO DEL HASH (debe ser IDÉNTICO en la Edge Function y aquí):
--   code_hash = sha256_hex( '<user_id>:<código de 6 dígitos>' )
--   user_id en minúsculas con guiones (uuid::text). El user_id va dentro del hash para que el mismo código
--   de dos usuarios no produzca el mismo hash. El código tiene solo 1.000.000 de valores posibles: la
--   protección real contra adivinarlo son los límites (5 intentos por código aquí, 1 envío por minuto y 5
--   por hora en la Edge Function), no el hash.
--
-- REQUISITO: la extensión pgcrypto (funciones digest). En Supabase ya viene habilitada en el esquema
-- "extensions"; la línea de abajo no hace nada si ya está.
-- =====================================================================================================

begin;

create extension if not exists pgcrypto with schema extensions;

-- -----------------------------------------------------------------------------------------------------
-- 1. Código pendiente. Una fila por usuario: pedir otro código reemplaza al anterior.
-- -----------------------------------------------------------------------------------------------------
create table public.recovery_email_codes (
  user_id                 uuid        not null primary key references auth.users (id) on delete cascade,
  email                   text        not null check (char_length(email) <= 254),
  code_hash               text        not null,
  expires_at              timestamptz not null,
  attempts                int         not null default 0,
  last_sent_at            timestamptz not null default now(),
  sent_count_window_start timestamptz not null default now(),
  sent_count              int         not null default 1
);

alter table public.recovery_email_codes enable row level security;

-- Sin políticas y sin privilegios: ni anon ni authenticated pueden leer, escribir ni siquiera ver la tabla.
-- service_role (Edge Function) se salta RLS; el grant solo asegura el privilegio de tabla.
revoke all on public.recovery_email_codes from anon, authenticated;
grant select, insert, update, delete on public.recovery_email_codes to service_role;

-- -----------------------------------------------------------------------------------------------------
-- 2. Cerrar la columna recovery_email al cliente.
--    Antes (0001) authenticated podía insertar y actualizar cualquier columna de su fila. Ahora solo las de
--    abajo; recovery_email se escribe únicamente dentro de verify_recovery_email_code. SELECT y DELETE no
--    cambian: el usuario sigue leyendo su perfil completo.
--
--    user_id aparece también en el UPDATE a propósito: la app guarda con upsert de PostgREST
--    (INSERT ... ON CONFLICT (user_id) DO UPDATE SET user_id = EXCLUDED.user_id, ...) y Postgres exige
--    privilegio de UPDATE sobre toda columna que aparezca en el SET. No abre nada: la política
--    profile_update_own (WITH CHECK user_id = auth.uid()) impide cambiar el user_id a otro usuario.
-- -----------------------------------------------------------------------------------------------------
revoke insert, update on public.profile from authenticated;

grant insert (user_id, gender, birth_date, avatar_path) on public.profile to authenticated;
grant update (user_id, gender, birth_date, avatar_path) on public.profile to authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 3. Verificación del código.
--    Resultados (texto, para que la app los mapee sin interpretar errores de Postgres):
--      'ok'                guardó el correo en el perfil y borró el código
--      'invalid'           el código no coincide (suma un intento)
--      'expired'           pasó la fecha de expires_at
--      'too_many_attempts' ya hubo 5 intentos fallidos con este código: hay que pedir otro
--      'not_found'         el usuario no tiene un código pendiente
--      'not_authenticated' no hay sesión
--    Una petición con código vencido o con 5 intentos ya gastados NO suma intentos.
-- -----------------------------------------------------------------------------------------------------
create function public.verify_recovery_email_code(p_code text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.recovery_email_codes%rowtype;
begin
  if v_uid is null then
    return 'not_authenticated';
  end if;

  -- FOR UPDATE: dos verificaciones simultáneas del mismo usuario se hacen en fila, no se pisan los intentos.
  select * into v_row
  from public.recovery_email_codes
  where user_id = v_uid
  for update;

  if not found then
    return 'not_found';
  end if;

  if v_row.attempts >= 5 then
    return 'too_many_attempts';
  end if;

  if now() > v_row.expires_at then
    return 'expired';
  end if;

  if encode(extensions.digest(v_uid::text || ':' || coalesce(p_code, ''), 'sha256'), 'hex') <> v_row.code_hash then
    update public.recovery_email_codes
    set attempts = attempts + 1
    where user_id = v_uid;
    return 'invalid';
  end if;

  -- Coincide: el correo pasa al perfil (se crea la fila si el usuario aún no tenía una) y el código se gasta.
  insert into public.profile (user_id, recovery_email)
  values (v_uid, v_row.email)
  on conflict (user_id) do update set recovery_email = excluded.recovery_email;

  delete from public.recovery_email_codes where user_id = v_uid;

  return 'ok';
end;
$$;

revoke all on function public.verify_recovery_email_code(text) from public, anon;
grant execute on function public.verify_recovery_email_code(text) to authenticated;

commit;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) La tabla tiene RLS activado y ninguna política (relrowsecurity = true; la segunda consulta no debe devolver filas)
-- select relname, relrowsecurity from pg_class where oid = 'public.recovery_email_codes'::regclass;
-- select * from pg_policies where schemaname = 'public' and tablename = 'recovery_email_codes';

-- b) Privilegios de la tabla: anon y authenticated NO deben aparecer; service_role sí
-- select grantee, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public' and table_name = 'recovery_email_codes'
-- order by grantee, privilege_type;

-- c) Privilegios por columna de profile para authenticated: recovery_email NO debe aparecer en INSERT ni UPDATE
-- select column_name, privilege_type
-- from information_schema.column_privileges
-- where table_schema = 'public' and table_name = 'profile' and grantee = 'authenticated'
-- order by privilege_type, column_name;

-- d) La función es SECURITY DEFINER, tiene search_path vacío y solo la ejecuta authenticated (ninguna fila PUBLIC ni anon)
-- select proname, prosecdef, proconfig
-- from pg_proc
-- where proname = 'verify_recovery_email_code' and pronamespace = 'public'::regnamespace;
-- select grantee, privilege_type
-- from information_schema.routine_privileges
-- where routine_schema = 'public' and routine_name = 'verify_recovery_email_code'
-- order by grantee;

-- e) pgcrypto está en el esquema extensions
-- select extname, extnamespace::regnamespace from pg_extension where extname = 'pgcrypto';

-- f) Comportamiento con un usuario de prueba (cámbialo por su id). Simula un código "123456" vigente y verifica
--    desde el SQL Editor como ese usuario; al terminar, borra lo que creaste.
-- insert into public.recovery_email_codes (user_id, email, code_hash, expires_at)
-- values ('00000000-0000-0000-0000-000000000000', 'respaldo@example.com',
--         encode(extensions.digest('00000000-0000-0000-0000-000000000000:123456', 'sha256'), 'hex'),
--         now() + interval '10 minutes');
-- select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000000","role":"authenticated"}', true);
-- select public.verify_recovery_email_code('000000');   -- invalid (attempts = 1)
-- select public.verify_recovery_email_code('123456');   -- ok
-- select recovery_email from public.profile where user_id = '00000000-0000-0000-0000-000000000000';
