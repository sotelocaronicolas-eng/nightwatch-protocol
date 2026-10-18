-- =====================================================================================================
-- NightWatch Protocol - Migración 0005: public.account_status(p_email)
--
-- NO se ejecuta desde la app ni desde el repositorio: se revisa y se pega a mano en el SQL Editor,
-- DESPUÉS de 0001 a 0004 y una sola vez.
--
-- REQUISITO DEL CURSO: los errores de inicio de sesión y de registro deben ser individualizados, como con
-- Firebase ("El correo no está registrado", "La contraseña es incorrecta", "Esta cuenta se creó con Google",
-- "Ya tienes una cuenta con este correo"). Supabase responde invalid_credentials para todo, a propósito, para
-- que la API no sirva para averiguar qué correos tienen cuenta. Esta función es la excepción consciente.
--
-- COMPROMISO DE SEGURIDAD (aceptado): cualquiera, incluso sin sesión (rol anon), puede preguntar si un correo
-- está registrado y con qué tipo de acceso. Eso es una enumeración de cuentas. El Security Advisor de Supabase
-- marcará esta función (SECURITY DEFINER ejecutable por anon y authenticated) y la advertencia se acepta de
-- forma consciente por el requisito del curso. Mitigaciones que sí tiene:
--   * Devuelve solo una de tres palabras. Nunca el id del usuario ni ningún otro dato.
--   * Rechaza correos nulos o de más de 254 caracteres sin consultar la tabla.
--   * search_path vacío y nombres calificados: no depende del esquema del llamador.
--   * No hay privilegios para PUBLIC; solo anon y authenticated pueden ejecutarla.
--
-- Resultados:
--   'not_found'      no existe usuario con ese correo (o el valor es nulo / demasiado largo)
--   'password'       el usuario tiene una identidad con provider = 'email' (inicia sesión con contraseña)
--   'other_provider' existe, pero sin identidad de correo (por ejemplo, solo Google)
-- =====================================================================================================

begin;

create function public.account_status(p_email text)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when p_email is null or char_length(p_email) > 254
      then 'not_found'
    when not exists (
      select 1
      from auth.users u
      where lower(u.email) = lower(trim(p_email))
    )
      then 'not_found'
    when exists (
      select 1
      from auth.users u
      join auth.identities i on i.user_id = u.id
      where lower(u.email) = lower(trim(p_email))
        and i.provider = 'email'
    )
      then 'password'
    else 'other_provider'
  end;
$$;

revoke all on function public.account_status(text) from public;
grant execute on function public.account_status(text) to anon, authenticated;

commit;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) La función existe, es SECURITY DEFINER (prosecdef = true), estable (provolatile = 's') y tiene
--    search_path vacío (proconfig = {search_path=""})
-- select proname, prosecdef, provolatile, proconfig
-- from pg_proc
-- where proname = 'account_status' and pronamespace = 'public'::regnamespace;

-- b) Quién puede ejecutarla: deben aparecer anon y authenticated, y NINGUNA fila con grantee = PUBLIC.
--    (postgres y service_role pueden aparecer: son roles de la plataforma, no de la app)
-- select grantee, privilege_type
-- from information_schema.routine_privileges
-- where routine_schema = 'public' and routine_name = 'account_status'
-- order by grantee;

-- c) Comportamiento (cambia los correos por unos reales de tus usuarios de prueba):
-- select public.account_status('correo-que-no-existe@example.com');   -- not_found
-- select public.account_status('  ' || 'USUARIO.CON.CONTRASENA@example.com' || ' '); -- password
-- select public.account_status('usuario.solo.google@example.com');    -- other_provider
-- select public.account_status(null);                                 -- not_found
