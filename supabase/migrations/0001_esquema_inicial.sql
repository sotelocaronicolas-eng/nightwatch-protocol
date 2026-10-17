-- =====================================================================================================
-- NightWatch Protocol - Esquema inicial de Supabase (equivalente a firestore.rules)
--
-- NO se ejecuta desde la app ni desde el repositorio: se revisa y se pega a mano en el SQL Editor.
--
-- Supuestos del proyecto Supabase:
--   * "Automatically expose new tables" DESACTIVADO: cada tabla necesita GRANT explícito al rol
--     authenticated, además de sus políticas RLS. Sin el GRANT, la API responde "permission denied"
--     aunque la política lo permita.
--   * "Automatic RLS" ACTIVADO: aun así se hace enable row level security en cada tabla (es idempotente
--     y deja el archivo correcto si algún día se ejecuta en un proyecto sin esa opción).
--   * La clave publishable viaja en el APK, así que la seguridad real es RLS y solo RLS.
--
-- Mapa de Firestore a Postgres:
--   users/{uid}/placement/result  ->  public.placement
--   users/{uid}/profile/main      ->  public.profile
--   users/{uid}/devices/{id}      ->  public.devices
--   uid (cadena de Firebase)      ->  user_id uuid de auth.users
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. Tablas
-- -----------------------------------------------------------------------------------------------------

-- Resultado del quiz de nivelación. Una fila por usuario (user_id es la clave primaria).
-- Los valores permitidos salen de PlacementLevel.kt y QuizCategory.kt (se guardan con .name).
-- Nota: firestore.rules solo comprobaba el dueño aquí; estos CHECK son una validación nueva y más estricta.
create table public.placement (
  user_id         uuid        not null primary key references auth.users (id) on delete cascade,
  level           text        not null
                  check (level in ('BAJO', 'MEDIO', 'ALTO')),
  weak_categories text[]      not null default '{}',
  completed_at    timestamptz not null default now(),
  constraint placement_weak_categories_valid
    check (weak_categories <@ array['CONCEPTOS_BASICOS', 'CONTRASENAS', 'INGENIERIA_SOCIAL', 'REDES_MALWARE']::text[])
);

-- Perfil. Una fila por usuario. Mismas validaciones que la regla de /profile en firestore.rules:
--   recoveryEmail: texto de hasta 254 caracteres
--   gender:        uno de los valores de Gender.key
--   birthDate:     'yyyy-MM-dd'. El tipo date ya fuerza ese formato y además rechaza fechas imposibles
--                  como 2026-02-31 (la regla de Firestore solo comprobaba la forma dddd-dd-dd)
-- avatarBase64 (hasta 150000 caracteres) NO se migra: avatar_path guardará la ruta del archivo en la
-- futura etapa de Supabase Storage y queda en null mientras tanto.
create table public.profile (
  user_id        uuid        not null primary key references auth.users (id) on delete cascade,
  recovery_email text        check (char_length(recovery_email) <= 254),
  avatar_path    text        check (char_length(avatar_path) <= 255),
  gender         text        check (gender in ('male', 'female', 'other', 'prefer_not_to_say')),
  birth_date     date,
  updated_at     timestamptz not null default now()
);

-- Dispositivos (modo cooperativo). Equivalente a /devices/{deviceId} en firestore.rules:
--   deviceId con forma de UUID              -> device_id uuid
--   keys().hasOnly(name, lastSeen, revoked) -> columnas fijas
--   hasAll(['name']), name is string        -> name text not null
--   name.size() > 0 && <= 80                -> check de largo entre 1 y 80
--   lastSeen is timestamp                   -> last_seen timestamptz
--   revoked is bool                         -> revoked boolean
create table public.devices (
  user_id   uuid        not null references auth.users (id) on delete cascade,
  device_id uuid        not null,
  name      text        not null check (char_length(name) between 1 and 80),
  last_seen timestamptz not null default now(),
  revoked   boolean     not null default false,
  primary key (user_id, device_id)
);

-- -----------------------------------------------------------------------------------------------------
-- 2. updated_at del lado del servidor
--    Equivale a FieldValue.serverTimestamp() en un set(..., merge) del perfil. Se dispara en insert y en
--    update, así el cliente no puede fijar la fecha a su gusto. search_path vacío: la función no
--    depende del esquema del llamador (todo va calificado).
-- -----------------------------------------------------------------------------------------------------

create function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger profile_set_updated_at
  before insert or update on public.profile
  for each row execute function public.set_updated_at();

-- -----------------------------------------------------------------------------------------------------
-- 3. Row Level Security
-- -----------------------------------------------------------------------------------------------------

alter table public.placement enable row level security;
alter table public.profile   enable row level security;
alter table public.devices   enable row level security;

-- -----------------------------------------------------------------------------------------------------
-- 4. Privilegios (GRANT) - necesarios porque las tablas nuevas no se exponen automáticamente
--    Sin sesión (rol anon) no hay acceso a nada: equivale a "request.auth != null" de firestore.rules.
--    El revoke es explícito aunque anon no tenga grants, para que el archivo lo deje documentado.
-- -----------------------------------------------------------------------------------------------------

revoke all on public.placement from anon;
revoke all on public.profile   from anon;
revoke all on public.devices   from anon;

grant select, insert, update, delete on public.placement to authenticated;
grant select, insert, update, delete on public.profile   to authenticated;
grant select, insert, update, delete on public.devices   to authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 5. Políticas RLS - solo el dueño, solo el rol authenticated
--    Equivale a "request.auth != null && request.auth.uid == userId" de firestore.rules.
--    (select auth.uid()) en vez de auth.uid() a secas: Postgres lo evalúa una vez por consulta y no por fila.
--    select y delete usan using; insert usa with check; update usa ambos (la fila actual y la nueva
--    deben ser del usuario, así nadie puede "regalar" una fila cambiando user_id).
-- -----------------------------------------------------------------------------------------------------

-- placement (firestore.rules: allow read, write)
create policy placement_select_own on public.placement
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy placement_insert_own on public.placement
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy placement_update_own on public.placement
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy placement_delete_own on public.placement
  for delete to authenticated
  using ((select auth.uid()) = user_id);

-- profile (firestore.rules: allow read, delete / create, update con validación; la validación está en los CHECK)
create policy profile_select_own on public.profile
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy profile_insert_own on public.profile
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy profile_update_own on public.profile
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy profile_delete_own on public.profile
  for delete to authenticated
  using ((select auth.uid()) = user_id);

-- devices (firestore.rules: allow read, delete / create, update con validación; la validación está en las columnas)
create policy devices_select_own on public.devices
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy devices_insert_own on public.devices
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy devices_update_own on public.devices
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy devices_delete_own on public.devices
  for delete to authenticated
  using ((select auth.uid()) = user_id);

commit;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) RLS activo en las tres tablas: las tres filas deben decir rls_activo = true
-- select c.relname as tabla, c.relrowsecurity as rls_activo
-- from pg_class c
-- join pg_namespace n on n.oid = c.relnamespace
-- where n.nspname = 'public' and c.relname in ('placement', 'profile', 'devices')
-- order by c.relname;

-- b) Políticas: deben salir 12 filas (select, insert, update y delete por cada tabla), todas con roles = {authenticated}
-- select tablename, policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'public' and tablename in ('placement', 'profile', 'devices')
-- order by tablename, cmd;

-- c) Privilegios: authenticated con select, insert, update y delete en cada tabla, y NINGUNA fila para anon
-- select grantee, table_name, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public' and table_name in ('placement', 'profile', 'devices')
--   and grantee in ('anon', 'authenticated')
-- order by table_name, grantee, privilege_type;

-- d) Trigger de updated_at en profile
-- select tgname, tgrelid::regclass as tabla
-- from pg_trigger
-- where tgrelid = 'public.profile'::regclass and not tgisinternal;

-- e) La función del trigger tiene search_path vacío: proconfig debe mostrar {search_path=""}
-- select proname, proconfig from pg_proc
-- where proname = 'set_updated_at' and pronamespace = 'public'::regnamespace;
