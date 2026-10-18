-- =====================================================================================================
-- NightWatch Protocol - Migración 0004: bucket privado de fotos de perfil
--
-- NO se ejecuta desde la app ni desde el repositorio: se revisa y se pega a mano en el SQL Editor,
-- DESPUÉS de 0001, 0002 y 0003 y una sola vez. Debe ejecutarse ANTES de probar la foto de perfil con
-- BACKEND=supabase.
--
-- Qué crea:
--   * El bucket 'avatars' (privado): nada se sirve por una URL pública, la app descarga con la sesión.
--   * Políticas en storage.objects para el rol authenticated: cada usuario solo ve, sube, reemplaza y borra
--     lo que está en SU carpeta. El anon no recibe ninguna política, así que no tiene acceso.
--
-- Convención de rutas: <user_id>/avatar.jpg dentro del bucket 'avatars' (profile.avatar_path guarda esa misma
-- ruta). Subir o reemplazar exige ese nombre exacto; leer y borrar exigen que la primera carpeta sea el uid.
--
-- Formatos y tamaño: la app solo genera JPEG (BitmapAvatarImageProcessor: 256x256, calidad 80/60/40,
-- tope práctico de unos 100 KB). El bucket acepta solo image/jpeg y hasta 256 KB (262144 bytes).
--
-- Subir una foto con upsert (reemplazar avatar.jpg) necesita políticas de select, insert y update; por eso
-- las cuatro operaciones van separadas, igual que en 0001.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. Bucket privado
--    on conflict: si el bucket ya existe (p. ej. creado a mano desde el panel) se corrige su configuración.
-- -----------------------------------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', false, 262144, array['image/jpeg'])
on conflict (id) do update
  set public             = false,
      file_size_limit    = 262144,
      allowed_mime_types = array['image/jpeg'];

-- -----------------------------------------------------------------------------------------------------
-- 2. Políticas en storage.objects - solo authenticated y solo la carpeta del propio usuario
--    (select auth.uid()) en vez de auth.uid() a secas: Postgres lo evalúa una vez por consulta y no por fila.
--    select y delete usan using; insert usa with check; update usa ambos, así nadie puede mover un objeto
--    a otra ruta (ni a la carpeta de otra persona) cambiando su nombre.
-- -----------------------------------------------------------------------------------------------------

create policy avatars_select_own on storage.objects
  for select to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

-- insert y update exigen el nombre EXACTO '<uid>/avatar.jpg' y no solo la carpeta: así nadie puede llenar el
-- Storage del plan gratuito subiendo archivos con otros nombres dentro de su carpeta. select y delete se quedan
-- con la condición de carpeta (leer o borrar solo alcanza lo propio).
create policy avatars_insert_own on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'avatars'
    and name = (select auth.uid())::text || '/avatar.jpg'
  );

create policy avatars_update_own on storage.objects
  for update to authenticated
  using (
    bucket_id = 'avatars'
    and name = (select auth.uid())::text || '/avatar.jpg'
  )
  with check (
    bucket_id = 'avatars'
    and name = (select auth.uid())::text || '/avatar.jpg'
  );

create policy avatars_delete_own on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

commit;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) Bucket: una fila con public = false, file_size_limit = 262144 y allowed_mime_types = {image/jpeg}
-- select id, name, public, file_size_limit, allowed_mime_types
-- from storage.buckets
-- where id = 'avatars';

-- b) Políticas: deben salir 4 filas (SELECT, INSERT, UPDATE y DELETE), todas con roles = {authenticated}
--    y ninguna para anon ni public
-- select policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'storage' and tablename = 'objects' and policyname like 'avatars\_%'
-- order by cmd;

-- c) Prueba con dos usuarios: cada uno debe poder subir y leer <su_uid>/avatar.jpg, y NO poder leer, subir,
--    reemplazar ni borrar nada bajo <uid_del_otro>/. Sin sesión (anon) no debe poder leer ni subir nada.
