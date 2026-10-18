-- =====================================================================================================
-- NightWatch Protocol - Migración 0002: mínimo privilegio para authenticated
--
-- Se ejecuta a mano en el SQL Editor, DESPUÉS de 0001_esquema_inicial.sql y una sola vez.
--
-- Por qué: Supabase aplica privilegios por defecto a las tablas nuevas del esquema public. Al verificar
-- los grants después de ejecutar 0001, el rol authenticated tenía, además de los cuatro que se
-- concedieron a propósito (SELECT, INSERT, UPDATE, DELETE), también REFERENCES, TRIGGER y TRUNCATE.
-- TRUNCATE no está sujeto a RLS: con ese privilegio, cualquier usuario autenticado podría vaciar la
-- tabla completa, con las filas de todos los demás. La app no necesita ninguno de los tres, así que se
-- quitan por mínimo privilegio.
--
-- 0001 no se edita porque ya se aplicó; el cambio va en esta migración nueva.
-- =====================================================================================================

begin;

revoke truncate, references, trigger
  on public.placement, public.profile, public.devices
  from authenticated;

commit;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; está comentada a propósito)
-- Debe devolver 12 filas de authenticated (DELETE, INSERT, SELECT y UPDATE en cada una de las tres
-- tablas) y ninguna de anon.
-- =====================================================================================================

-- select grantee, table_name, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public' and table_name in ('placement', 'profile', 'devices')
--   and grantee in ('anon', 'authenticated')
-- order by table_name, grantee, privilege_type;
