-- =====================================================================================================
-- NightWatch Protocol - Verificación de 0013_batallas_ingles.sql
--
-- Solo SELECT: no cambia nada. Se pega en el SQL Editor DESPUÉS de aplicar 0013; cada consulta dice qué se espera.
-- (Como se corre en el editor, que usa un rol con permisos, también puede leer las tablas cerradas.)
-- =====================================================================================================

-- 1. Conteos: 72 preguntas, 72 traducciones al inglés, 12 retos, 12 traducciones. Las dos diferencias deben ser 0.
select
  (select count(*) from public.battle_questions)                                        as preguntas_es,
  (select count(*) from public.battle_question_translations where lang = 'en')          as preguntas_en,
  (select count(*) from public.battle_questions q
    where not exists (select 1 from public.battle_question_translations t
                      where t.question_id = q.id and t.lang = 'en'))                    as preguntas_sin_en,   -- esperado: 0
  (select count(*) from public.ulti_challenges)                                         as retos_es,
  (select count(*) from public.ulti_challenge_translations where lang = 'en')           as retos_en,
  (select count(*) from public.ulti_challenges c
    where not exists (select 1 from public.ulti_challenge_translations t
                      where t.challenge_id = c.id and t.lang = 'en'))                   as retos_sin_en;       -- esperado: 0
-- esperado: 72 | 72 | 0 | 12 | 12 | 0

-- 2. Mismas opciones: cada traducción tiene las 4 opciones (el esquema las exige no vacías); aquí se comprueba que ninguna
--    quedó vacía o solo con espacios y que no hay opciones repetidas dentro de una pregunta. Esperado: 0 filas.
select t.question_id, t.question
from public.battle_question_translations t
where btrim(t.question) = '' or btrim(t.option_a) = '' or btrim(t.option_b) = '' or btrim(t.option_c) = '' or btrim(t.option_d) = ''
   or t.option_a in (t.option_b, t.option_c, t.option_d) or t.option_b in (t.option_c, t.option_d) or t.option_c = t.option_d;

-- 3. Ningún título de reto vacío. Esperado: 0 filas.
select challenge_id from public.ulti_challenge_translations where btrim(objective_title) = '';

-- 4. Ningún texto en inglés igual al español salvo opciones técnicas (puertos, comandos, siglas, nombres). Revisar a ojo:
--    la lista debe tener solo opciones como "443", "ufw ..." o siglas; nunca una pregunta.
select q.id, q.topic, t.question as pregunta_en
from public.battle_question_translations t
join public.battle_questions q on q.id = t.question_id
where t.lang = 'en' and t.question = q.question;      -- esperado: 0 filas

-- 5. La respuesta correcta no se duplicó: la tabla de traducciones NO tiene columna "correct" (esperado: 0 filas).
select column_name from information_schema.columns
where table_schema = 'public' and table_name = 'battle_question_translations' and column_name like '%correct%';

-- 6. Seguridad: RLS activo y SIN políticas ni permisos de tabla para anon/authenticated en las dos tablas nuevas.
select c.relname as tabla, c.relrowsecurity as rls_activo                                   -- esperado: 2 filas con true
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relname in ('battle_question_translations', 'ulti_challenge_translations');

select tablename, policyname from pg_policies
where schemaname = 'public' and tablename in ('battle_question_translations', 'ulti_challenge_translations');   -- esperado: 0 filas

select grantee, table_name, privilege_type from information_schema.role_table_grants
where table_schema = 'public' and table_name in ('battle_question_translations', 'ulti_challenge_translations')
  and grantee in ('anon', 'authenticated');                                                 -- esperado: 0 filas

-- 7. Funciones: solo existen las firmas nuevas (con p_lang); las viejas ya no. Esperado: 3 filas.
select p.proname, pg_get_function_identity_arguments(p.oid) as argumentos,
       p.prosecdef as security_definer, p.proconfig as configuracion
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname in ('battle_sync', 'battle_play', 'sx_battle_state')
order by p.proname;
-- esperado: battle_play(... p_lang text) | battle_sync(p_battle uuid, p_lang text) | sx_battle_state(... p_lang text)

-- 8. Permisos de ejecución: anon false en todas; authenticated true solo en battle_sync y battle_play.
select p.proname,
       has_function_privilege('anon', p.oid, 'execute') as anon,
       has_function_privilege('authenticated', p.oid, 'execute') as authenticated
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname in ('battle_sync', 'battle_play', 'sx_battle_state')
order by p.proname;
-- esperado: battle_play false|true, battle_sync false|true, sx_battle_state false|false

-- 9. Muestra para leer a ojo: 5 preguntas en español e inglés lado a lado, con la letra correcta (que es la misma).
select q.topic, q.tier, q.correct, q.question as es, t.question as en
from public.battle_questions q join public.battle_question_translations t on t.question_id = q.id and t.lang = 'en'
order by random() limit 5;
