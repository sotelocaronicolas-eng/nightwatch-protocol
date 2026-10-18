-- =====================================================================================================
-- NightWatch Protocol - Migración 0007: progreso de la ruta de aprendizaje (lecciones, laboratorios, examen)
--
-- BORRADOR sin aplicar. NO se ejecuta desde la app ni desde el repositorio: el equipo lo revisa y, si lo
-- aprueba, lo pega a mano en el SQL Editor, DESPUÉS de 0001 a 0006 y una sola vez. Mientras tenga esta
-- línea de "BORRADOR" puede editarse; una vez aplicado deja de editarse y cualquier cambio va en 0008.
--
-- Fuente: docs/superpowers/specs/2026-10-18-modulo-3-design.md, secciones 8 (modelo de datos) y 13
-- (decisiones del grupo del 2026-10-18). Todo lo que aquí aparece sale de ahí.
--
-- Qué NO incluye (a propósito):
--   * NADA que toque tablas de módulos anteriores. Esta migración solo CREA tres tablas nuevas. No hace
--     alter, insert, update ni delete sobre public.placement, public.profile, public.devices ni
--     public.recovery_email_codes, y ninguna tabla nueva tiene clave foránea hacia ellas: todas apuntan
--     solo a auth.users, como las de 0001. Aprobar el examen NO cambia el nivel BAJO/MEDIO/ALTO (decisión
--     P5): el resultado "aprobado" vive solo en public.exam_attempts.
--   * Nada de lo social (amigos, batallas, racha grupal): es el módulo 4.
--   * Racha real e insignias reales: siguen fijas en la app; sus reglas se definen en el módulo 4 (P9).
--   * Precisión ("% de precisión") de los laboratorios: se calcula en la app y no se guarda, porque un
--     laboratorio incompleto no se guarda (P6) y todo lo guardado valdría 100%.
--   * Contenido (lecciones, laboratorios, preguntas del examen): viaja dentro de la app, como el quiz.
--   * El desbloqueo en orden, "el examen se abre con las 6 islas" y "una isla se completa con la lección y
--     el laboratorio" (P3) se calculan en la app a partir de estas tablas; la base de datos no los obliga.
--   * Límites de intentos o de tiempo del examen: no hay (P11).
--
-- Supuestos del proyecto Supabase (igual que en 0001):
--   * "Automatically expose new tables" DESACTIVADO: cada tabla necesita GRANT explícito a authenticated.
--   * "Automatic RLS" ACTIVADO: aun así se hace enable row level security en cada tabla.
--   * La clave publishable viaja en el APK: la seguridad real es RLS y solo RLS.
--
-- Convenciones: (select auth.uid()) = user_id; GRANT solo a authenticated, nunca TRUNCATE, REFERENCES
-- ni TRIGGER (0002); privilegios por columna; los instantes (completed_at, taken_at) los pone el servidor.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. Tablas
-- -----------------------------------------------------------------------------------------------------

-- Lección vista. Una fila por usuario y tema. Son 6 temas, separados, con los nombres de Figma:
--   CONTRASENAS        -> Isla de las Contraseñas   / lección "Contraseñas y Autenticación" (2294:112)
--   PHISHING           -> Bahía del Phishing        / lección "Phishing" (2294:130)
--   MALWARE            -> Arrecife del Malware      / lección "Malware" (2294:148)
--   INGENIERIA_SOCIAL  -> Cueva del Engaño Social   / lección "Ingeniería Social" (2294:166)
--   REDES_SEGURAS      -> Islas de Redes Seguras    / lección "Islas de Redes Seguras" (2294:184)
--   FIREWALL           -> Fortaleza del Firewall    / lección "Fortaleza del Firewall" (2294:202)
-- En el código serán un enum LearningTopic y se guardan con .name, como QuizCategory.
-- Ver una lección otra vez no cambia nada: la app trata la fila ya existente como éxito (no hay update).
create table public.lesson_progress (
  user_id      uuid        not null references auth.users (id) on delete cascade,
  topic        text        not null
               check (topic in ('CONTRASENAS', 'PHISHING', 'MALWARE', 'INGENIERIA_SOCIAL', 'REDES_SEGURAS', 'FIREWALL')),
  completed_at timestamptz not null default now(),
  primary key (user_id, topic)
);

-- Laboratorio completado. Una fila por usuario y laboratorio, y SOLO si se cumplieron los 3 objetivos
-- (decisión P6): un laboratorio con menos objetivos muestra el informe "Laboratorio no completado" y no
-- se guarda. Un laboratorio por tema; los nombres de las tarjetas de Figma (LABORATORIOS 2271:84):
--   CONTRASENAS        -> Fuerza de Contraseñas           (terminal 2374:273)
--   PHISHING           -> Detecta el Correo Falso         (terminal "Correo Sospechoso" 2383:141)
--   MALWARE            -> Analiza el Archivo Sospechoso   (terminal "Archivo Sospechoso" 2374:357)
--   INGENIERIA_SOCIAL  -> Simulación de Llamada           (terminal 2383:206)
--   REDES_SEGURAS      -> Seguridad en Redes              (terminal 2299:118)
--   FIREWALL           -> Configura tu Firewall           (terminal 2383:271)
-- Repetir un laboratorio ya completado no cambia nada (no hay update; la fila existente es éxito).
create table public.lab_progress (
  user_id      uuid        not null references auth.users (id) on delete cascade,
  lab          text        not null
               check (lab in ('CONTRASENAS', 'PHISHING', 'MALWARE', 'INGENIERIA_SOCIAL', 'REDES_SEGURAS', 'FIREWALL')),
  completed_at timestamptz not null default now(),
  primary key (user_id, lab)
);

-- Intento del examen final (EXAMEN FINAL 2486:249). Una fila por intento: sin límite de intentos ni de
-- espera entre intentos (decisión P11). Son 6 preguntas, una por tema, y se aprueba con máximo 1 fallo
-- (texto de 2486:249). Por eso passed es una columna GENERADA: el cliente solo envía cuántas acertó y no
-- puede escribir "aprobado" por su cuenta. NO se guarda cuáles falló (Figma: si fallas más de 1 "no te
-- diremos cuáles fueron"). Aprobar NO modifica ninguna otra tabla (decisión P5).
-- Límite conocido: la corrección ocurre en la app (las preguntas viajan en el APK), así que un cliente
-- modificado podría enviar correct_count = 6. Es aceptable mientras no haya ranking ni nada social que
-- dependa de esto (riesgo R2 de la spec).
-- id es identity: para insertar no hace falta ningún permiso aparte sobre su secuencia interna.
create table public.exam_attempts (
  id            bigint      generated always as identity primary key,
  user_id       uuid        not null references auth.users (id) on delete cascade,
  correct_count smallint    not null check (correct_count between 0 and 6),
  passed        boolean     generated always as (correct_count >= 5) stored,
  taken_at      timestamptz not null default now()
);

-- Para "¿aprobó alguna vez?" y "último intento" por usuario.
create index exam_attempts_user_taken_idx on public.exam_attempts (user_id, taken_at desc);

-- -----------------------------------------------------------------------------------------------------
-- 2. Row Level Security
-- -----------------------------------------------------------------------------------------------------

alter table public.lesson_progress enable row level security;
alter table public.lab_progress    enable row level security;
alter table public.exam_attempts   enable row level security;

-- -----------------------------------------------------------------------------------------------------
-- 3. Privilegios (GRANT) - solo authenticated, mínimo privilegio por columna
--    Sin TRUNCATE, REFERENCES ni TRIGGER (0002). anon no recibe nada.
--    * lesson_progress y lab_progress: solo se leen e insertan. No hay update ni delete desde la app.
--    * exam_attempts: solo se leen e insertan; un intento no se edita ni se borra (historial).
--      No se concede permiso sobre id, passed ni taken_at (el servidor los calcula).
--    Los INSERT por columna omiten completed_at: lo fija el default now() del servidor.
-- -----------------------------------------------------------------------------------------------------

revoke all on public.lesson_progress from anon;
revoke all on public.lab_progress    from anon;
revoke all on public.exam_attempts   from anon;

grant select on public.lesson_progress to authenticated;
grant insert (user_id, topic) on public.lesson_progress to authenticated;

grant select on public.lab_progress to authenticated;
grant insert (user_id, lab) on public.lab_progress to authenticated;

grant select on public.exam_attempts to authenticated;
grant insert (user_id, correct_count) on public.exam_attempts to authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 4. Políticas RLS - solo el dueño, solo el rol authenticated
--    (select auth.uid()) en vez de auth.uid() a secas: Postgres lo evalúa una vez por consulta y no por fila.
-- -----------------------------------------------------------------------------------------------------

-- lesson_progress
create policy lesson_progress_select_own on public.lesson_progress
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy lesson_progress_insert_own on public.lesson_progress
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

-- lab_progress
create policy lab_progress_select_own on public.lab_progress
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy lab_progress_insert_own on public.lab_progress
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

-- exam_attempts
create policy exam_attempts_select_own on public.exam_attempts
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy exam_attempts_insert_own on public.exam_attempts
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

commit;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) RLS activo en las tres tablas: las tres filas deben decir rls_activo = true
-- select c.relname as tabla, c.relrowsecurity as rls_activo
-- from pg_class c
-- join pg_namespace n on n.oid = c.relnamespace
-- where n.nspname = 'public' and c.relname in ('lesson_progress', 'lab_progress', 'exam_attempts')
-- order by c.relname;

-- b) Políticas: deben salir 6 filas (select e insert en cada tabla), todas con roles = {authenticated}
--    y ninguna de update ni delete
-- select tablename, policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'public' and tablename in ('lesson_progress', 'lab_progress', 'exam_attempts')
-- order by tablename, cmd;

-- c) Privilegios de tabla: NINGUNA fila para anon; para authenticated solo SELECT (los INSERT son por
--    columna, ver d), y nunca TRUNCATE, REFERENCES ni TRIGGER
-- select grantee, table_name, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public' and table_name in ('lesson_progress', 'lab_progress', 'exam_attempts')
--   and grantee in ('anon', 'authenticated')
-- order by table_name, grantee, privilege_type;

-- d) Privilegios por columna: authenticated debe tener INSERT solo en (user_id, topic), (user_id, lab) y
--    (user_id, correct_count), y SELECT en las columnas de cada tabla; nada sobre id, passed ni completed_at/taken_at
-- select grantee, table_name, column_name, privilege_type
-- from information_schema.column_privileges
-- where table_schema = 'public' and table_name in ('lesson_progress', 'lab_progress', 'exam_attempts')
--   and grantee in ('anon', 'authenticated')
-- order by table_name, privilege_type, column_name;

-- e) passed es una columna generada: debe salir attgenerated = 's'
-- select attname, attgenerated from pg_attribute
-- where attrelid = 'public.exam_attempts'::regclass and attname = 'passed';

-- f) Aislamiento de módulos anteriores: las claves foráneas de las tres tablas deben apuntar SOLO a auth.users
--    (ninguna fila con public.placement, public.profile, public.devices ni public.recovery_email_codes)
-- select conrelid::regclass as tabla, confrelid::regclass as apunta_a
-- from pg_constraint
-- where contype = 'f'
--   and conrelid in ('public.lesson_progress'::regclass, 'public.lab_progress'::regclass, 'public.exam_attempts'::regclass);

-- g) Los valores permitidos de topic y lab son exactamente los 6 temas (REDES_SEGURAS, no REDES)
-- select conrelid::regclass as tabla, pg_get_constraintdef(oid) as regla
-- from pg_constraint
-- where contype = 'c'
--   and conrelid in ('public.lesson_progress'::regclass, 'public.lab_progress'::regclass)
-- order by 1;
