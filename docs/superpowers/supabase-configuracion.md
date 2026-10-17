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
Con `BACKEND=supabase` la app usa los repositorios Supabase del [Bloque A](#bloque-a-autenticación-datos-y-foto);
con `BACKEND=firebase` (el valor por defecto) todo sigue igual que antes.

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
nueva con el siguiente número. Hoy hay cuatro:

1. `0001_esquema_inicial.sql`: tablas, RLS, políticas y grants.
2. `0002_minimo_privilegio.sql`: quita a `authenticated` los privilegios `TRUNCATE`, `REFERENCES` y `TRIGGER`,
   que Supabase concede por defecto y la app no necesita.
3. `0003_revoca_rls_auto_enable.sql`: revoca `EXECUTE` de `public.rls_auto_enable()` (función que crea Supabase
   con "Enable automatic RLS" y que el Security Advisor marcaba como llamable por `anon` y `authenticated`).
4. `0004_storage_avatares.sql`: bucket privado `avatars` y sus 4 políticas en `storage.objects` (**pendiente de
   ejecutar**; ver el Bloque A).

El proyecto debe tener "Automatically expose new tables" desactivado y "Automatic RLS" activado; por eso el
script incluye `GRANT` explícitos al rol `authenticated`.

## Bloque A: autenticación, datos y foto

Con `BACKEND=supabase` la app usa Supabase para el inicio de sesión con correo y contraseña, los datos en Postgres
y la foto de perfil en Storage. Con `BACKEND=firebase` nada cambia.

### Qué funciona y qué no

| Función | Con `BACKEND=supabase` |
|---|---|
| Registro, inicio y cierre de sesión (correo y contraseña) | Funciona |
| Mantener la sesión al cerrar y reabrir la app | Funciona (la pantalla inicial espera a que cargue la sesión) |
| Quiz de nivelación (`public.placement`) | Funciona |
| Perfil: correo de recuperación, género, fecha de nacimiento (`public.profile`) | Funciona |
| Foto de perfil (Storage, bucket `avatars`) | Funciona **después de ejecutar `0004`** |
| Dispositivos (`public.devices`), incluida la revocación cooperativa | Funciona |
| Cambiar nombre, confirmar identidad, cambiar contraseña | Funciona |
| Descargar mis datos | Funciona (ya usaba los repositorios) |
| Inicio de sesión con Google | **No**: falla con el error genérico, sin cerrar la app |
| Cambio de correo | **No**: igual |
| Recuperación de contraseña ("Olvidé mi contraseña") | **No**: igual |

### Cómo probar

1. En `local.properties` pon `BACKEND=supabase` (y `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY` de tu proyecto).
2. **Sync** de Gradle y **Run**.
3. Para volver a Firebase, `BACKEND=firebase` y otro Sync.

Las cuentas y los datos de Firebase y de Supabase son independientes: entre un modo y otro hay que registrarse de
nuevo.

### Ajustes del panel de Supabase

- **Authentication > Providers > Email > Confirm email: desactivado.** Con "Confirm email" activado, el registro
  crea la cuenta pero no abre sesión; la app lo informa como un error (`EmailConfirmationRequired`) y no entra.
- **Authentication > Password** (reglas del registro de la app, en `RegisterUiState.kt`):
  - Largo mínimo: **8**.
  - Debe incluir una mayúscula `A-Z` **y** un dígito `0-9`.
  - Debe incluir un carácter especial de este conjunto: `^ $ * . [ ] { } ( ) ? " ! @ # % & / \ , > < ' : ; | _ ~ ` = + -`.
  - La app **no** exige una minúscula. La opción del panel más cercana es *Minimum length 8* + *Lowercase,
    uppercase letters, digits and symbols*; la única diferencia es que el servidor sí exigirá una minúscula. Una
    contraseña válida en la app pero sin minúsculas (por ejemplo `ABCDEFG1!`) la rechaza el servidor y la app
    muestra el aviso de contraseña débil.
- **Secure password change** (misma sección): si está activado, el servidor pide una sesión reciente para cambiar la
  contraseña. "Cambiar contraseña" ya vuelve a iniciar sesión antes, así que lo cumple.
- Con el SMTP integrado de Supabase hay un límite muy bajo de correos por hora; este bloque no envía ninguno.

### Ejecutar 0004 antes de probar la foto

`supabase/migrations/0004_storage_avatares.sql` crea el bucket privado `avatars` (256 KB, solo `image/jpeg`) y las
políticas de `storage.objects`: cada usuario solo ve, sube, reemplaza y borra lo que está en su carpeta
`<user_id>/`. Se ejecuta a mano en el SQL Editor, como las demás, y una sola vez. Sin él, subir la foto falla y la app
la deja solo en el teléfono (el mismo aviso de "sin sincronizar" que ya existía). La foto se guarda como
`avatars/<user_id>/avatar.jpg` y su ruta en `profile.avatar_path`; se descarga con la sesión del usuario, nunca por
una URL pública. La copia local de la foto se conserva igual que con Firebase.

### Diferencias frente a Firebase

- **Sin modo offline para escrituras.** Firestore guardaba en una cola lo que se escribía sin red y lo sincronizaba
  después. Supabase no: sin conexión la escritura falla como "sin conexión" y el dato queda solo en el teléfono
  (la foto y el género/fecha ya tienen copia local) hasta que se vuelva a guardar. Lo mismo pasa con el resultado del
  quiz: si falla, hay que repetirlo o guardarlo de nuevo.
- **Mensajes de error al iniciar sesión.** Supabase responde igual para un correo que no existe y para una
  contraseña incorrecta (a propósito). Todo cae en "Correo o contraseña incorrectos"; ya no aparecen "La contraseña
  es incorrecta", "El correo no está registrado" ni "Esta cuenta se creó con Google…".
- **Cierre remoto de sesión.** Sigue siendo cooperativo (la marca `revoked` solo se cumple cuando el otro teléfono
  abre la app). La revocación real de Supabase (`signOut(SignOutScope.OTHERS)`) **no** está conectada: el botón
  "Cerrar sesión en todos" solo marca los dispositivos, y el texto de la pantalla todavía habla de Firebase.
  Conectarla exige un método nuevo en el repositorio y tocar `DevicesViewModel`.
- **Marca de tiempo de "visto por última vez".** La pone el reloj del teléfono, no el servidor.
- **Sesión sin red.** Si el token venció y no hay red, la app conserva al usuario guardado para no sacarlo al login;
  los datos fallan como "sin conexión" hasta que vuelva la red y se renueve el token. Si el servidor revoca la sesión
  después, todavía no hay un observador que lleve al login (pendiente).
- **Respaldo de Android.** La sesión de Supabase se guarda en preferencias compartidas y todavía no está excluida de
  `backup_rules.xml` / `data_extraction_rules.xml` (pendiente del informe de evaluación, sección 6).
