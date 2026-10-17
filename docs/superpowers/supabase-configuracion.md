# Configuración local de Supabase

Guía corta para cada integrante del equipo. Contexto técnico completo en
[`reports/supabase-evaluacion.md`](reports/supabase-evaluacion.md). Por ahora la app sigue usando Firebase:
esta configuración solo prepara el terreno para las próximas etapas.

## 1. Crear tu `local.properties`

1. En la raíz del proyecto, copia `local.properties.example` como `local.properties` (si Android Studio ya te
   creó uno con `sdk.dir`, agrega las tres líneas al final en lugar de reemplazarlo).
2. Completa los valores:

   ```properties
   SUPABASE_URL=https://TU-PROYECTO.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
   BACKEND=firebase
   ```

3. `local.properties` **nunca se sube a git** (está en `.gitignore`). Antes de cada commit, confirma con
   `git status` que no aparece.

Si faltan `SUPABASE_URL` o `SUPABASE_PUBLISHABLE_KEY`, la app compila igual con `BACKEND=firebase`. Con
`BACKEND=supabase` y alguno de los dos vacío, la app falla al iniciar con un mensaje que dice qué falta.
Cambia `BACKEND` a `supabase` solo cuando las etapas con repositorios Supabase estén integradas: hoy todos
los repositorios siguen siendo los de Firebase.

## 2. Dónde están la URL y la clave

En el panel de Supabase de tu proyecto:

- **URL del proyecto:** botón **Connect** (arriba), o **Project Settings > API**.
- **Clave publishable** (`sb_publishable_...`): **Project Settings > API Keys**, o el botón **Connect**.

## 3. La clave secret nunca va en la app

Usa **solo** la clave `publishable`. La clave `secret` (o `service_role` en proyectos antiguos) se salta RLS y
da acceso total a los datos: no va en `local.properties`, ni en el código, ni en un chat del equipo.

La clave publishable es pública por diseño (cualquiera puede extraerla del APK). Lo que protege los datos son
las políticas RLS. Se mantiene fuera de git para poder cambiar de proyecto sin tocar código, no porque sea secreta.

## 4. Ejecutar el SQL

El esquema está en `supabase/migrations/0001_esquema_inicial.sql`. **Nada lo ejecuta automáticamente.**

1. Revísalo completo (tablas, políticas y grants) antes de correrlo.
2. Pégalo en **SQL Editor** del panel de Supabase y ejecútalo a mano, una sola vez por proyecto.
3. Corre las consultas de verificación comentadas al final del archivo: RLS activo en las tres tablas, 12
   políticas y ningún privilegio para `anon`.
4. Prueba con dos usuarios que cada uno no puede leer, escribir ni borrar las filas del otro.

### Cómo se manejan las migraciones

Los archivos de `supabase/migrations/` se ejecutan **en orden numérico** (`0001`, `0002`, ...) y **una sola vez
por proyecto**. Una migración que ya se aplicó **no se edita**: cualquier cambio posterior va en una migración
nueva con el siguiente número. Hoy hay dos: `0001_esquema_inicial.sql` (tablas, RLS, políticas y grants) y
`0002_minimo_privilegio.sql` (quita a `authenticated` los privilegios `TRUNCATE`, `REFERENCES` y `TRIGGER`,
que Supabase concede por defecto y la app no necesita).

El proyecto debe tener "Automatically expose new tables" desactivado y "Automatic RLS" activado; por eso el
script incluye `GRANT` explícitos al rol `authenticated`.
