# Configuración local de Supabase

Guía corta para cada integrante del equipo. Contexto técnico completo en
[`reports/supabase-evaluacion.md`](reports/supabase-evaluacion.md). La app usa Firebase por defecto
(`BACKEND=firebase`); con `BACKEND=supabase` usa Supabase (bloques A y B de esta guía).

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
nueva con el siguiente número. Hoy hay cinco:

1. `0001_esquema_inicial.sql`: tablas, RLS, políticas y grants.
2. `0002_minimo_privilegio.sql`: quita a `authenticated` los privilegios `TRUNCATE`, `REFERENCES` y `TRIGGER`,
   que Supabase concede por defecto y la app no necesita.
3. `0003_revoca_rls_auto_enable.sql`: revoca `EXECUTE` de `public.rls_auto_enable()` (función que crea Supabase
   con "Enable automatic RLS" y que el Security Advisor marcaba como llamable por `anon` y `authenticated`).
4. `0004_storage_avatares.sql`: bucket privado `avatars` y sus 4 políticas en `storage.objects` (**pendiente de
   ejecutar**; ver el Bloque A).
5. `0005_estado_de_cuenta.sql`: función `public.account_status(p_email)`, que devuelve `not_found`, `password` u
   `other_provider` para que el login y el registro muestren errores individualizados (**pendiente de ejecutar**;
   ver "Errores individualizados").

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
| Inicio de sesión con Google | Funciona desde el [Bloque B](#bloque-b-google-códigos-por-correo-y-sesiones) |
| Cambio de correo | Funciona desde el Bloque B (con código) |
| Recuperación de contraseña ("Olvidé mi contraseña") | Funciona desde el Bloque B (con código) |

### Cómo probar

1. En `local.properties` pon `BACKEND=supabase` (y `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY` de tu proyecto).
2. **Sync** de Gradle y **Run**.
3. Para volver a Firebase, `BACKEND=firebase` y otro Sync.

Las cuentas y los datos de Firebase y de Supabase son independientes: entre un modo y otro hay que registrarse de
nuevo.

### Ajustes del panel de Supabase

- **Authentication > Providers > Email > Confirm email.** El Bloque A se probó con "Confirm email" **desactivado**.
  Desde el Bloque B el registro está pensado para tenerlo **ACTIVADO**: el registro crea la cuenta sin abrir sesión y
  la app lleva a la pantalla del código de 6 dígitos (ver el Bloque B). Con él desactivado el registro sigue entrando
  directo, como en el Bloque A.
- **Authentication > Password** (reglas del registro de la app, en `RegisterUiState.kt`):
  - Largo mínimo: **8**.
  - Debe incluir una mayúscula `A-Z` **y** un dígito `0-9`.
  - Debe incluir una minúscula `a-z`.
  - Debe incluir un carácter especial de este conjunto: `^ $ * . [ ] { } ( ) ? " ! @ # % & / \ , > < ' : ; | _ ~ ` = + -`.
  - En el panel: *Minimum length* **8** y *Password requirements* **Lowercase, uppercase letters, digits and
    symbols**. La app y el servidor piden lo mismo (la lista del registro y de "Cambiar contraseña" muestra los
    cuatro requisitos, con Firebase y con Supabase). Si el servidor tuviera un conjunto de símbolos más estrecho
    que el de la app, esa contraseña se rechazaría con el aviso de contraseña débil.
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
- **Mensajes de error al iniciar sesión y al registrarse.** Supabase responde igual (`invalid_credentials`) para un
  correo que no existe y para una contraseña incorrecta, a propósito. Para mantener los mismos mensajes que Firebase,
  la app consulta `public.account_status` (migración `0005`); ver la sección siguiente. Sin ejecutar `0005`, o si la
  consulta falla, el login vuelve al mensaje genérico "Correo o contraseña incorrectos" y el registro deja que
  Supabase responda `user_already_exists`.

### Errores individualizados y `account_status` (0005)

Requisito del curso: los errores de login y de registro deben ser individuales, como con Firebase.

| Situación | Mensaje |
|---|---|
| Login, el correo no existe | "El correo no está registrado" |
| Login, el correo existe con contraseña | "La contraseña es incorrecta" |
| Login, el correo solo existe con Google | "Esta cuenta se creó con Google. Usa «Continuar con Google»" |
| Registro, el correo ya tiene contraseña | "Ya tienes una cuenta con este correo" |
| Registro, el correo ya existe con Google | "Este correo ya está registrado con Google. Usa «Continuar con Google»" |

- **Cuándo se consulta.** En el login, solo después de que Supabase responda `invalid_credentials` (un login correcto
  o un fallo de red no consultan nada). En el registro, antes de `signUp`. Si la consulta falla por cualquier motivo,
  se usan los errores genéricos de arriba.
- **Compromiso de seguridad aceptado.** `account_status` es `SECURITY DEFINER` y la pueden ejecutar `anon` y
  `authenticated`: cualquiera puede preguntar si un correo tiene cuenta y de qué tipo (enumeración de cuentas). Solo
  devuelve una de tres palabras, nunca el id ni otro dato. **El Security Advisor de Supabase marcará esta función; la
  advertencia está aceptada por requisito del curso** y no hay que "corregirla".
- **Verificación de correos reales.** Comprobar que una dirección existe de verdad (que alguien la controla) se hará
  con un código de verificación de 6 dígitos enviado al correo (bloque B). No se puede resolver consultando al
  proveedor: Google no permite averiguar si una dirección de Gmail existe. `account_status` solo dice si ya hay una
  cuenta en NightWatch con ese correo.
- **Cierre remoto de sesión.** El cierre de un dispositivo concreto sigue siendo cooperativo (la marca `revoked` solo
  se cumple cuando el otro teléfono abre la app). "Cerrar sesión en todos" además revoca de verdad las otras sesiones
  desde el Bloque B (`signOut(SignOutScope.OTHERS)`).
- **Marca de tiempo de "visto por última vez".** La pone el reloj del teléfono, no el servidor.
- **Sesión sin red.** Si el token venció y no hay red, la app conserva al usuario guardado para no sacarlo al login;
  los datos fallan como "sin conexión" hasta que vuelva la red y se renueve el token. Si el servidor revoca la sesión
  después, todavía no hay un observador que lleve al login (pendiente).
- **Respaldo de Android.** La sesión de Supabase se guarda en preferencias compartidas (`sharedpref`), y ya queda
  fuera de cualquier respaldo: `AndroidManifest.xml` tiene `allowBackup="false"` y tanto `data_extraction_rules.xml`
  (respaldo en la nube y transferencia entre teléfonos) como `backup_rules.xml` excluyen `sharedpref`, `file`,
  `database` y `root`. No hay nada pendiente aquí.

## Bloque B: Google, códigos por correo y sesiones

Con `BACKEND=supabase` la app suma el inicio de sesión con Google, la verificación del correo con un código de 6 dígitos
(registro, cambio de correo y recuperación de contraseña), el reenvío con cuenta regresiva y la revocación real de las
otras sesiones. Con `BACKEND=firebase` nada de esto cambia: el cambio de correo y la recuperación siguen por enlace, con
las pantallas de siempre. Cada flujo se elige por una capacidad que expone el repositorio (`usesGoogleNonce`,
`recoversPasswordWithCode`, `changesEmailWithCode`), no por `BuildConfig` en la interfaz.

### Configuración exacta del panel (lo que el Bloque B espera)

Se hace a mano en el panel de Supabase; ninguna migración ni el código lo cambian. Los nombres de los menús cambian entre
versiones del panel: lo que importa son los ajustes de la columna derecha.

| Dónde | Valor |
|---|---|
| **Authentication > Emails > SMTP Settings** | SMTP propio (Brevo). Con el SMTP integrado de Supabase el límite de correos por hora es muy bajo |
| **Authentication > Emails > Templates**: "Confirm sign up", "Change email address" y "Reset password" | Solo el código, `{{ .Token }}`. **Sin enlaces** (`{{ .ConfirmationURL }}`): la app no maneja *deep links* |
| **Authentication > Sign In / Providers > Email**: *Email OTP length* | **6** |
| *Email OTP expiration* | **600** segundos (la app decide "El código expiró" con esos mismos 600 s) |
| *Minimum interval between emails to the same user* | **60** segundos (la cuenta regresiva de la app) |
| **Secure email change** | **Desactivado**: al cambiar el correo llega **un solo** código, al correo **nuevo** |
| **Confirm email** | **ACTIVADO**: el registro no abre sesión hasta verificar el código |
| **Authentication > Sign In / Providers > Google** | Activado, con el **Client ID web** (el mismo `default_web_client_id` que centraliza `AppContainer`). **Sin client secret**: se usa solo el flujo nativo con ID token. *Skip nonce checks* **desactivado** (el nonce es obligatorio) |
| **Password** (del Bloque A) | Sin cambios: mínimo 8, mayúscula, minúscula, dígito y símbolo |

### Los flujos

Tipos de OTP de supabase-kt 3.2.4 (`OtpType.Email`):

| Flujo | Verificar el código | Reenviar |
|---|---|---|
| Registro (y login con correo sin confirmar) | `SIGNUP` | `resendEmail(SIGNUP)` |
| Cambio de correo | `EMAIL_CHANGE` (con el correo **nuevo**) | `resendEmail(EMAIL_CHANGE)` |
| Recuperación de contraseña | `RECOVERY` | `resetPasswordForEmail` otra vez (Supabase no tiene `resend` para recuperación) |

**Google.** "Continuar con Google" (login y registro) genera un nonce aleatorio de 32 bytes por intento. Su SHA-256 en
hexadecimal va a Credential Manager (`setNonce`) y el nonce **sin hashear** a `signInWith(IDToken)` con el proveedor
Google. Cancelar el selector de cuentas no muestra ningún mensaje; sin conexión sale el diálogo de red; cualquier otro
fallo, el genérico. Si ya existe una cuenta con contraseña y el mismo correo, **Supabase vincula la identidad de Google
al mismo usuario** (mismo `uid`, mismos datos): después de entrar con Google, esa cuenta también entra con su contraseña.

**Registro.** Un registro que termina sin sesión no es un error: lleva a la pantalla "Verifica tu Correo" con el correo.
Con el código correcto (`verifyEmailOtp(SIGNUP)`) queda la sesión y sigue el flujo de siempre (quiz o menú). Si alguien
inicia sesión con un correo sin confirmar (`email_not_confirmed`), la app envía un código nuevo y lo lleva a la misma
pantalla. Si ese envío choca con el límite de 60 s, entra igual: el código anterior sigue valiendo.

**Cambio de correo.** Editar Perfil > "Confirma tu identidad" (la contraseña, que con el flujo por código se pide siempre
antes de enviar nada) > "Confirmar Correo" ("ENVIAR CÓDIGO", `updateUser` con el correo nuevo) > "Verifica tu Correo" >
"¡Correo actualizado!" > "VOLVER A EDITAR PERFIL". Mi Perfil toma el correo nuevo de la sesión que devuelve la
verificación.

**Recuperación de contraseña.** "¿Olvidaste tu contraseña?" abre el diálogo "Recuperación de contraseña" sobre el login,
con el correo ya escrito. Primero consulta `account_status`: `not_found` muestra "Correo no encontrado", `other_provider`
dice que la cuenta se creó con Google y `password` continúa con `resetPasswordForEmail`. El mismo diálogo muestra las 6
casillas; con el código correcto (`RECOVERY`) se abre una sesión de recuperación y la app va a la pantalla de contraseña
nueva (con la lista de requisitos de `PasswordRules`). `updateUser` con la contraseña, **cierra la sesión**, muestra
"¡Contraseña actualizada!" y vuelve al login. Salir de la pantalla de contraseña nueva sin guardar también cierra la
sesión.

**Reenviar y errores del código.** "Reenviar el código de 6 digitos" queda deshabilitado con la cuenta regresiva
("Reenviar en 45 s") durante 60 s tras cada envío. Si el servidor responde `over_email_send_rate_limit`, la cuenta
regresiva usa los segundos que indica. Errores: "El código ingresado no es correcto.", "El código expiró. Solicita uno
nuevo.", "Demasiados intentos..." (`over_request_rate_limit`) y el mensaje de "Sin conexión a internet" de siempre.

**Cerrar sesión en todos.** Además de marcar cada dispositivo (cierre cooperativo, como antes), llama a
`signOut(SignOutScope.OTHERS)`, que invalida los *refresh tokens* de las demás sesiones. Va primero: si falla, no se
marca nada y el botón puede repetirse. Cerrar un solo dispositivo sigue siendo solo la marca cooperativa.

### Diferencias deliberadas con Figma

- **Recuperación por correo, no por SMS.** Figma (`113:38` y `110:332`) pide un celular y manda un SMS; el SMS cuesta
  dinero, así que el diálogo pide el correo y manda el código por correo. Los textos de esos diálogos se adaptaron
  (correo en vez de celular, "Hemos enviado un código a <correo>").
- **Pantalla de contraseña nueva reutilizada.** No existe en Figma. Es "Cambiar Contraseña" (`2289:107`) sin el campo de
  contraseña actual, y termina en "¡Contraseña actualizada!" (`2289:154`) con el título "Nueva Contraseña".
- **El recuadro del código.** Las pantallas usan la barra gris con los dígitos de `2106:61` y el diálogo, las 6 casillas
  de `110:332`; las dos son el mismo campo (`CodeInput`).
- **Confirmar identidad antes de enviar el código de cambio de correo** (Figma no lo muestra): ver arriba.

### Limitaciones conocidas

- **Remitente sin dominio propio.** Con un remitente `@gmail.com` por Brevo, los correos pueden caer en **spam** o ser
  rechazados por DMARC. Para una demo, pedir al evaluador que revise la carpeta de spam; la solución real es un dominio
  propio con SPF/DKIM.
- **El access token de otras sesiones sigue válido hasta que expira.** `signOut(OTHERS)` mata el *refresh token*; el JWT
  ya emitido vale hasta su vencimiento (1 hora por defecto). El teléfono "cerrado" puede seguir leyendo datos ese rato.
- **Incorrecto y expirado son iguales para el servidor.** Supabase responde `otp_expired` en los dos casos. La app dice
  "expiró" si pasaron 600 s desde que ella envió el código (según el reloj del teléfono) y "incorrecto" en otro caso.
  Con el reloj del teléfono mal puesto el mensaje puede equivocarse (el código se acepta o rechaza igual, según el servidor).
- **Sesión de recuperación.** Entre verificar el código y guardar la contraseña nueva hay una sesión abierta sin que la
  persona haya escrito contraseña. Si la app se mata justo en esa pantalla, al abrirla de nuevo la persona entra (ya
  demostró controlar el correo). Salir con "X" o con Atrás la cierra.
- **Contraseña nueva igual a la anterior.** Supabase la rechaza (`same_password`) y la app lo muestra como "La contraseña
  no cumple con los requerimientos" (el mismo mapeo que ya usaban "Cambiar contraseña" y Editar Perfil).
- **Pedir otro código de recuperación invalida el anterior**, y el envío repetido lo limita el intervalo de 60 s.
- **"Cerrar sesión en todos" con todos los dispositivos ya marcados** muestra "No hay otros dispositivos con sesión
  abierta" y no vuelve a revocar en el servidor (se conserva el comportamiento de Firebase).
- **Firebase no cambia**, salvo el texto de Dispositivos, que ya no nombra a Firebase ("No revoca de inmediato el acceso
  de tu cuenta").
