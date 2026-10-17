-- =====================================================================================================
-- NightWatch Protocol - Migración 0003: revoca EXECUTE de public.rls_auto_enable()
--
-- Se ejecuta a mano en el SQL Editor, DESPUÉS de 0001 y 0002 y una sola vez.
--
-- public.rls_auto_enable() NO es nuestra: la crea Supabase al activar la opción "Enable automatic RLS".
-- Es SECURITY DEFINER (se ejecuta con los privilegios de su dueño) y el Security Advisor la marcaba
-- como ejecutable por anon y authenticated, es decir, llamable desde la API.
--
-- Solo la usa el event trigger del sistema que activa RLS en las tablas nuevas, y el event trigger no
-- necesita que esos roles tengan EXECUTE. Por eso se revoca. Se verificó con una tabla de prueba que
-- el RLS automático sigue funcionando después del revoke.
-- =====================================================================================================

begin;

revoke execute on function public.rls_auto_enable() from public, anon, authenticated;

commit;
