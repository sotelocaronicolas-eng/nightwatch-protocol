-- =====================================================================================================
-- NightWatch Protocol - Migración 0007: progreso de la ruta de aprendizaje (lecciones, laboratorios, examen)
--
-- BORRADOR sin aplicar. NO se ejecuta desde la app ni desde el repositorio: el equipo lo revisa y, si lo
-- aprueba, lo pega a mano en el SQL Editor, DESPUÉS de 0001 a 0006 y una sola vez. Mientras tenga esta
-- línea de "BORRADOR" puede editarse; una vez aplicado deja de editarse y cualquier cambio va en 0008.
--
-- Fuente: docs/superpowers/specs/2026-10-18-modulo-3-design.md (sección "Modelo de datos"). Todo lo que
-- aquí aparece sale de ahí; lo que la spec marca como PREGUNTA ABIERTA NO está modelado.
--
-- Qué NO incluye (a propósito):
--   * Nada de lo social (amigos, batallas, racha grupal): queda para el módulo 4 y no tiene tablas aquí.
--   * Racha real e insignias reales: la spec no tiene una regla definida para ellas (pregunta abierta).
--   * Contenido (lecciones, laboratorios, preguntas del examen): viaja dentro de la app, como el quiz.
--   * Cambio del nivel BAJO/MEDIO/ALTO de public.placement al aprobar el examen (pregunta abierta).
--
-- Supuestos del proyecto Supabase (igual que en 0001):
--   * "Automatically expose new tables" DESACTIVADO: cada tabla necesita GRANT explícito a authenticated.
--   * "Automatic RLS" ACTIVADO: aun así se hace enable row level security en cada tabla.
--   * La clave publishable viaja en el APK: la seguridad real es RLS y solo RLS.
--
-- Convenciones: user_id = (select auth.uid()); GRANT solo a authenticated, nunca TRUNCATE, REFERENCES
-- ni TRIGGER; privilegios por columna; los instantes (completed_at, taken_at) los pone el servidor.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. Tablas
-- -----------------------------------------------------------------------------------------------------

-- Lección vista. Una fila por usuario y tema. Los 6 temas salen de las 6 lecciones de Figma
-- (LECCION - Contraseñas y Autenticación 2294:112, Phishing 2294:130, Malware 2294:148, Ingeniería Social
-- 2294:166, Islas de Redes Seguras 2294:184, Fortaleza del Firewall 2294:202). En el código serán un enum
-- LearningTopic y se guardan con .name, como QuizCategory.
create table public.lesson_progress (
  user_id      uuid        not null references auth.users (id) on delete cascade,
  topic        text        not null
               check (topic in ('CONTRASENAS', 'PHISHING', 'MALWARE', 'INGENIERIA_SOCIAL', 'REDES', 'FIREWALL')),
  completed_at timestamptz not null default now(),
  primary key (user_id, topic)
);

-- Laboratorio hecho. Una fila por usuario y laboratorio (el mejor resultado; reintentar lo reemplaza si
-- mejora, lo decide la app). Un laboratorio por tema, según los nombres de Figma en LABORATORIOS 2271:84:
--   CONTRASENAS        -> Fuerza de Contraseñas           (LAB - FUERZA DE CONTRASEÑAS 2374:273)
--   PHISHING           -> Detecta el Correo Falso         (LAB - CORREO SOSPECHOSO 2383:141)
--   MALWARE            -> Analiza el Archivo Sospechoso   (LAB - ARCHIVO SOSPECHOSO 2374:357)
--   INGENIERIA_SOCIAL  -> Simulación de Llamada           (LAB - SIMULACIÓN DE LLAMADA 2383:206)
--   REDES              -> Seguridad en Redes              (LAB - SEGURIDAD EN REDES 2299:118)
--   FIREWALL           -> Configura tu Firewall           (LAB - CONFIGURA TU FIREWALL 2383:271)
-- accuracy (0 a 100) es el "% de precisión" de los informes de Figma (p. ej. 100% o 0%). El informe de
-- contraseñas muestra un tiempo estimado en vez de un porcentaje: no se guarda (se recalcula en la app).
-- Si un laboratorio con 0% cuenta como "completado" es una PREGUNTA ABIERTA: la tabla guarda el
-- resultado tal cual y la regla vive en la app, no aquí.
create table public.lab_progress (
  user_id      uuid        not null references auth.users (id) on delete cascade,
  lab          text        not null
               check (lab in ('CONTRASENAS', 'PHISHING', 'MALWARE', 'INGENIERIA_SOCIAL', 'REDES', 'FIREWALL')),
  accuracy     smallint    not null check (accuracy between 0 and 100),
  completed_at timestamptz not null default now(),
  primary key (user_id, lab)
);

-- Intento del examen final (EXAMEN FINAL 2486:249). Una fila por intento: "Vuelve a intentar el examen"
-- (EXAMEN - NO APROBADO 2491:345) implica varios intentos. Son 6 preguntas, una por tema, y se aprueba
-- con máximo 1 fallo (texto de 2486:249). Por eso passed es una columna GENERADA: el cliente solo envía
-- cuántas acertó y no puede escribir "aprobado" por su cuenta. NO se guarda cuáles falló: Figma dice que
-- si fallas más de 1 "no te diremos cuáles fueron".
-- Límite: la corrección del examen ocurre en la app (las preguntas viajan en el APK), así que un
-- cliente modificado podría enviar correct_count = 6. Es aceptable mientras no haya ranking ni nada
-- social que dependa de esto (ver riesgos de la spec).
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
--    * lesson_progress: se inserta y se lee; no se actualiza (ver de nuevo una lección no cambia nada)
--      y no se borra desde la app.
--    * lab_progress: se inserta, se lee y se actualiza solo accuracy (mejorar el resultado).
--    * exam_attempts: solo se inserta y se lee; un intento no se edita ni se borra (historial).
--      No se concede permiso sobre id ni passed (el servidor los calcula).
-- -----------------------------------------------------------------------------------------------------

revoke all on public.lesson_progress from anon;
revoke all on public.lab_progress    from anon;
revoke all on public.exam_attempts   from anon;

grant select on public.lesson_progress to authenticated;
grant insert (user_id, topic) on public.lesson_progress to authenticated;

grant select on public.lab_progress to authenticated;
grant insert (user_id, lab, accuracy) on public.lab_progress to authenticated;
grant update (accuracy) on public.lab_progress to authenticated;

grant select on public.exam_attempts to authenticated;
grant insert (user_id, correct_count) on public.exam_attempts to authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 4. Políticas RLS - solo el dueño, solo el rol authenticated
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

create policy lab_progress_update_own on public.lab_progress
  for update to authenticated
  using ((select auth.uid()) = user_id)
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

-- b) Políticas: deben salir 7 filas (2 en lesson_progress, 3 en lab_progress, 2 en exam_attempts), todas con roles = {authenticated}
-- select tablename, policyname, cmd, roles
-- from pg_policies
-- where schemaname = 'public' and tablename in ('lesson_progress', 'lab_progress', 'exam_attempts')
-- order by tablename, cmd;

-- c) Privilegios de tabla: NINGUNA fila para anon, y para authenticated no debe aparecer TRUNCATE, REFERENCES ni TRIGGER
-- select grantee, table_name, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public' and table_name in ('lesson_progress', 'lab_progress', 'exam_attempts')
--   and grantee in ('anon', 'authenticated')
-- order by table_name, grantee, privilege_type;

-- d) Privilegios por columna: authenticated solo en las columnas indicadas arriba
-- select grantee, table_name, column_name, privilege_type
-- from information_schema.column_privileges
-- where table_schema = 'public' and table_name in ('lesson_progress', 'lab_progress', 'exam_attempts')
--   and grantee = 'authenticated'
-- order by table_name, privilege_type, column_name;

-- e) passed es una columna generada: debe salir attgenerated = 's'
-- select attname, attgenerated from pg_attribute
-- where attrelid = 'public.exam_attempts'::regclass and attname = 'passed';
