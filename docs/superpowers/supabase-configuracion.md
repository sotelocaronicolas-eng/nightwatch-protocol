# Configuración local de Supabase

Guía técnica para el equipo: qué hay que configurar en Supabase (y por qué) y cómo funcionan los flujos. La puesta en
marcha paso a paso de un integrante nuevo está en el [`README.md`](../../README.md); la historia de la migración, en
[`reports/migracion-supabase.md`](reports/migracion-supabase.md). La app usa **solo Supabase** (bloques A, B, C y D de
esta guía son las etapas en que se construyó).

## 1. Crear tu `local.properties`

1. En la raíz del proyecto, copia `local.properties.example` como `local.properties` (si Android Studio ya te
   creó uno con `sdk.dir`, agrega las tres líneas al final en lugar de reemplazarlo).
2. Completa los valores:

   ```properties
   SUPABASE_URL=https://TU-PROYECTO.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
   GOOGLE_WEB_CLIENT_ID=TU_ID.apps.googleusercontent.com
   ```

3. `local.properties` **nunca se sube a git** (está en `.gitignore`). Antes de cada commit, confirma con
   `git status` que no aparece.

La app compila aunque falte alguna de las tres claves, pero **al iniciar falla** con un mensaje que dice cuál falta
(`appConfigError`, en `di/AppConfig.kt`).

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
nueva con el siguiente número. Hoy hay siete **ya aplicadas** en el proyecto del equipo (un proyecto nuevo las necesita
todas, en orden) y tres más **ya aplicadas** (`0008`, `0009` y `0010`, Social; ver la Parte F):

1. `0001_esquema_inicial.sql`: tablas, RLS, políticas y grants.
2. `0002_minimo_privilegio.sql`: quita a `authenticated` los privilegios `TRUNCATE`, `REFERENCES` y `TRIGGER`,
   que Supabase concede por defecto y la app no necesita.
3. `0003_revoca_rls_auto_enable.sql`: revoca `EXECUTE` de `public.rls_auto_enable()` (función que crea Supabase
   con "Enable automatic RLS" y que el Security Advisor marcaba como llamable por `anon` y `authenticated`).
4. `0004_storage_avatares.sql`: bucket privado `avatars` y sus 4 políticas en `storage.objects` (aplicada; ver el
   Bloque A).
5. `0005_estado_de_cuenta.sql`: función `public.account_status(p_email)`, que devuelve `not_found`, `password` u
   `other_provider` para que el login y el registro muestren errores individualizados (aplicada; ver "Errores
   individualizados").
6. `0006_verificacion_correo_recuperacion.sql`: tabla `recovery_email_codes`, cierra la columna
   `profile.recovery_email` al cliente y crea `verify_recovery_email_code(p_code)` (aplicada; ver "Parte D").
7. `0007_progreso_aprendizaje.sql`: tablas `lesson_progress`, `lab_progress` y `exam_attempts` del módulo 3, con RLS del
   dueño y `GRANT` por columna (aplicada; ver "Parte E"). No toca ninguna tabla anterior.
8. `0008_social.sql` (**aplicada y verificada**): 14 tablas y 5 funciones públicas del módulo 4 (amigos, rachas de pareja y batallas).
   No toca ninguna tabla anterior; ver la Parte F.
9. `0009_banco_batallas.sql` (**aplicada y verificada el 2026-10-21**): curva de XP (20 niveles, máximo 1.900 XP), 72 preguntas de batalla
   (18 respuestas correctas de cada letra) y 12 retos de ulti. Se aplicó a mano **después** de `0008`.
10. `0010_fotos_social.sql` (**aplicada y verificada el 2026-10-22**): fotos de perfil en Social (dos columnas en `social_profile`, la función
    `social_set_avatar`, el bucket privado `social-avatars` y sus 4 políticas). Se aplicó a mano **después** de `0009`; ver la Parte F.
11. `0011_reportes_bloqueos.sql` (**borrador, sin aplicar**): reportar y bloquear personas, y ocultar una foto desde el SQL Editor (`avatar_hidden`). Ver la Parte F.

El proyecto debe tener "Automatically expose new tables" desactivado y "Automatic RLS" activado; por eso el
script incluye `GRANT` explícitos al rol `authenticated`.

## Bloque A: autenticación, datos y foto

La app usa Supabase para el inicio de sesión con correo y contraseña, los datos en Postgres y la foto de perfil en
Storage.

### Qué funciona y qué no

| Función | Estado |
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

1. Completa tu `local.properties` (sección 1) y ejecuta las migraciones pendientes (sección 4).
2. **Sync** de Gradle y **Run**.

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
    cuatro requisitos). Si el servidor tuviera un conjunto de símbolos más estrecho
    que el de la app, esa contraseña se rechazaría con el aviso de contraseña débil.
- **Secure password change** (misma sección): si está activado, el servidor pide una sesión reciente para cambiar la
  contraseña. "Cambiar contraseña" ya vuelve a iniciar sesión antes, así que lo cumple.
- Con el SMTP integrado de Supabase hay un límite muy bajo de correos por hora: por eso desde el Bloque B se usa un SMTP
  propio (Brevo, ver más abajo).

### La migración 0004 y la foto (ya aplicada)

`supabase/migrations/0004_storage_avatares.sql` (hay que ejecutarla antes de probar la foto en un proyecto nuevo) crea el bucket privado `avatars` (256 KB, solo `image/jpeg`) y las
políticas de `storage.objects`: cada usuario solo ve, sube, reemplaza y borra lo que está en su carpeta
`<user_id>/`. Se ejecuta a mano en el SQL Editor, como las demás, y una sola vez. Sin él, subir la foto falla y la app
la deja solo en el teléfono (el mismo aviso de "sin sincronizar" que ya existía). La foto se guarda como
`avatars/<user_id>/avatar.jpg` y su ruta en `profile.avatar_path`; se descarga con la sesión del usuario, nunca por
una URL pública. La foto también se guarda como copia local en el teléfono.

### Limitaciones propias de esta arquitectura

- **Sin modo offline para escrituras.** No hay una cola de escrituras: sin conexión la escritura falla como "sin
  conexión" y el dato queda solo en el teléfono (la foto y el género/fecha tienen copia local) hasta que se vuelva a
  guardar. Lo mismo pasa con el resultado del quiz: si falla, hay que repetirlo o guardarlo de nuevo.
- **Mensajes de error al iniciar sesión y al registrarse.** Supabase responde igual (`invalid_credentials`) para un
  correo que no existe y para una contraseña incorrecta, a propósito. Para dar mensajes individuales, la app consulta
  `public.account_status` (migración `0005`); ver la sección siguiente. Sin ejecutar `0005`, o si la consulta falla, el
  login vuelve al mensaje genérico "Correo o contraseña incorrectos" y el registro deja que Supabase responda
  `user_already_exists`.

### Errores individualizados y `account_status` (0005)

Requisito del curso: los errores de login y de registro deben ser individuales ("El correo no está registrado", "La
contraseña es incorrecta").

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

La app suma el inicio de sesión con Google, la verificación del correo con un código de 6 dígitos (registro, cambio de
correo y recuperación de contraseña), el reenvío con cuenta regresiva y la revocación real de las otras sesiones. No
hay enlaces en los correos: todo es con códigos.

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
  persona haya escrito contraseña. Salir con "X" o con Atrás la cierra. Si la app se mata justo en esa pantalla, queda
  guardada una marca de "recuperación en curso" (se escribe **antes** de pedir la verificación) y al abrir la app de
  nuevo se cierra esa sesión y se va al login (Parte C, C3).
- **Contraseña nueva igual a la anterior.** Supabase la rechaza (`same_password`) y la app lo muestra como "La contraseña
  no cumple con los requerimientos" (el mismo mapeo que ya usaban "Cambiar contraseña" y Editar Perfil).
- **Pedir otro código de recuperación invalida el anterior**, y el envío repetido lo limita el intervalo de 60 s.
- **"Cerrar sesión en todos" revoca en el servidor en cada toque** (Parte C, C4), aunque todos los dispositivos ya estén
  marcados. Si no hay otros dispositivos que marcar, igual se revoca y se muestra "No hay otros dispositivos con sesión
  abierta".
- **Texto de Dispositivos:** el aviso dice que marcar un dispositivo "no revoca de inmediato el acceso de tu cuenta"
  (la revocación real es la de "Cerrar sesión en todos").

## Parte C: correcciones de la prueba en dispositivo

- **C1, espaciado del quiz y de los resultados.** Las preguntas siguen Figma `2017:30`: barra de
  progreso, "Pregunta X de 6" y la categoría arriba, bajo la barra de estado; la pregunta y sus 4 opciones centradas en el
  espacio que queda; el botón (SIGUIENTE / FINALIZAR en la última) abajo. Si no cabe, solo la parte del medio se desplaza.
  "¡Resultados listos!" (`2017:126`) respeta la barra de estado y el margen superior del diseño. Es un commit
  independiente para poder llevarlo a `main`.
- **C2, desvincular la cuenta de Google vieja al cambiar el correo (Supabase).** Tras verificar el código del cambio de
  correo, la app lee las identidades del usuario (`retrieveUserForCurrentSession`) y llama a `unlinkIdentity` por cada
  identidad **Google** cuyo `identity_data.email` no sea el correo nuevo (sin distinguir mayúsculas). La identidad de
  correo nunca se toca. **Requiere "Allow manual linking" activado** en el panel (*Authentication > Sign In / Providers*,
  sección de *User Signups*); sin él el servidor rechaza el desvínculo.
  Si la desvinculación falla, **el cambio de correo no falla**: "¡Correo actualizado!" muestra debajo el aviso "Tu cuenta
  de Google anterior sigue vinculada a este perfil." Antes no había ningún aviso posterior a una verificación (los avisos
  existentes, como `notice` en Dispositivos o "Te enviamos un código nuevo.", viven en la misma pantalla); este es el
  patrón nuevo: `CodeFlow.completionNotice` > `VerifyCodeEvent.VerifiedWithNotice` > `EmailUpdated(notice)`.
- **C3, no dejar sesión abierta si se interrumpe la recuperación (Supabase).** Al pedir la verificación del código de
  recuperación se guarda en DataStore (`SessionPreferences`) la marca "recuperación en curso"; se borra si el código se
  rechaza, al guardar la contraseña nueva (ya con la sesión cerrada) y al salir con X/Atrás. En el arranque, después de
  `awaitSessionReady` y antes de decidir la ruta, si la marca existe se cierra la sesión, se borra la marca y se va al
  login.
- **C4, "Cerrar sesión en todos" siempre revoca en el servidor (Supabase).** Cada toque llama a
  `signOut(SignOutScope.OTHERS)`, aunque todos los dispositivos ya estén marcados. La marca cooperativa y el aviso se
  conservan.

## Parte D: verificar con código el correo de recuperación

**Qué hace.** "GUARDAR CORREO" en *Correo de Recuperación* no guarda directo: envía un código de 6 dígitos al correo
alterno, abre la pantalla "Verifica tu Correo" (el mismo componente de los otros flujos, con reenvío y cuenta
regresiva) y solo con el código correcto se guarda y aparece "¡Correo guardado!" (botón "VOLVER A CUENTAS").

**Por qué hay un envío propio.** Supabase Auth solo envía códigos al correo **principal** del usuario. El del correo
alterno lo envía la Edge Function `send-recovery-email-code` por la **API transaccional de Brevo**.

**Piezas (todas se despliegan a mano; ver la lista de pasos abajo).**

| Pieza | Qué es |
|---|---|
| `supabase/migrations/0006_verificacion_correo_recuperacion.sql` | Tabla `public.recovery_email_codes` (solo el *hash* del código; ningún privilegio para `anon` ni `authenticated`), cierra `profile.recovery_email` al cliente (el `INSERT`/`UPDATE` de `authenticated` queda limitado por columna) y crea `public.verify_recovery_email_code(p_code)` (`SECURITY DEFINER`, solo `authenticated`) |
| `supabase/functions/send-recovery-email-code/index.ts` | Edge Function: exige el JWT, valida el correo, limita (60 s entre envíos, 5 por hora), guarda el hash y envía por Brevo. Su `README.md` tiene el despliegue |
| App | `functions-kt` (mismo BOM 3.2.4) invoca la función; `rpc` llama a la función SQL. Mapeo: `429` > espera con los segundos; `same_as_primary` > "Usa un correo distinto al principal"; `email_failed` > error de envío; `invalid` > "El código ingresado no es correcto."; `expired` > expirado; `too_many_attempts` > demasiados intentos |

**Formato del hash** (idéntico en la función y en el SQL): `sha256_hex("<user_id>:<código>")`, con el `user_id` en
minúsculas con guiones. **El código y el hash nunca se registran ni se devuelven.**

**Seguridad.** Con 6 dígitos hay 1.000.000 de códigos; lo que protege de adivinarlos son los límites: 5 intentos por
código (la función SQL), 60 s entre envíos y 5 envíos por hora (la Edge Function) y el vencimiento de 10 minutos.
`profile.recovery_email` ya no puede escribirse desde la app: solo `verify_recovery_email_code` lo escribe. En el
`UPDATE` de `profile` se concede también `user_id` porque el *upsert* de PostgREST lo incluye en el `SET`; no abre nada
porque la política `profile_update_own` (`WITH CHECK user_id = auth.uid()`) impide cambiarlo.

**Límite diario de Brevo.** Estos correos salen por la API de Brevo, no por el SMTP de Supabase: **cuentan en el límite
diario de Brevo** (300 al día en el plan gratuito, junto con los correos de Auth que ya salen por el SMTP de Brevo), no
en el límite de correos de Supabase.

**Orden de despliegue.** Ejecutar `0006` **antes** de instalar la versión de la app de la Parte D: después de `0006`
una versión anterior de la app ya no puede guardar el correo de recuperación (la columna se cerró) y la nueva sin la
función desplegada no puede enviar el código.

## Parte E: progreso de aprendizaje (módulo 3, migración 0007)

**Qué hace.** El módulo 3 (ruta, lecciones, laboratorios y examen final) guarda el avance en tres tablas nuevas. Todo lo demás se
**deriva** en la app a partir de ellas: qué islas están completas o desbloqueadas, el contador "N de 7", la "Lección diaria", la
racha y las 9 insignias. Nada de eso se almacena.

**Las tres tablas** (`supabase/migrations/0007_progreso_aprendizaje.sql`, aplicada a mano):

| Tabla | Clave | Columnas | Qué puede hacer la app (`authenticated`) |
|---|---|---|---|
| `lesson_progress` | (`user_id`, `topic`) | `topic` ∈ `CONTRASENAS`, `PHISHING`, `MALWARE`, `INGENIERIA_SOCIAL`, `REDES_SEGURAS`, `FIREWALL`; `completed_at` lo pone el servidor | `SELECT` e `INSERT (user_id, topic)` |
| `lab_progress` | (`user_id`, `lab`) | `lab` ∈ los mismos 6 códigos; `completed_at` (servidor). **Solo existe fila si el lab se completó (3 de 3 objetivos)** | `SELECT` e `INSERT (user_id, lab)` |
| `exam_attempts` | `id` (identity) | `correct_count` 0 a 6; `passed` **columna generada** (`correct_count >= 5`); `taken_at` (servidor). Una fila por intento, sin límite | `SELECT` e `INSERT (user_id, correct_count)` |

- **RLS** activo y solo del dueño: `(select auth.uid()) = user_id` en `select` e `insert` (6 políticas; ninguna de `update` ni
  `delete`). `anon` no tiene acceso. Ningún `TRUNCATE`, `REFERENCES` ni `TRIGGER`.
- **Aislamiento (P5):** las claves foráneas apuntan solo a `auth.users`. Aprobar el examen **no puede** cambiar `placement` ni
  el nivel BAJO/MEDIO/ALTO: el "aprobado" vive solo en `exam_attempts`.
- **Escritura idempotente** de lección y lab: la app hace un `upsert` que ignora duplicados (`on_conflict=user_id,topic` o
  `user_id,lab` con `Prefer: resolution=ignore-duplicates`), que PostgREST ejecuta como `INSERT … ON CONFLICT DO NOTHING`; solo
  necesita `INSERT`, y repetir no es un error ni duplica. Un intento de examen es un `INSERT` simple (cada llamada añade una fila).
  El cliente solo envía `correct_count`: `passed` y las fechas las calcula el servidor.
- **Lectura:** la app lee las tres tablas completas del usuario (con sus fechas `completed_at` y `taken_at`, que `authenticated` puede
  leer) para calcular el progreso y la racha. Los errores se mapean a `DataAccessException`.
- **Verificación.** Al final del archivo hay siete consultas, a a g (RLS, políticas, privilegios de tabla y de columna, `passed`
  generada, claves foráneas solo a `auth.users`, valores permitidos de `topic` y `lab`). Las ejecutó el usuario a mano y dieron el
  resultado esperado. Las llamadas reales desde la app no tienen pruebas automáticas (F-03): se comprueban a mano con el guion de
  `reports/modulo-3-diagnostico.md`.
- **Límites conocidos.** El examen se corrige en el teléfono (un cliente modificado podría enviar `correct_count = 6`; aceptable sin
  ranking). El desbloqueo en orden y el acceso al examen son reglas de la app, no de la base de datos. Repetir una lección o un lab ya
  registrado no genera fila, así que no suma un día a la racha (propuesta: una tabla de actividad en `0008`, no creada).

**Orden de despliegue.** Ejecutar `0007` **antes** de instalar la versión 2.1.0 de la app: sin las tablas, abrir una lección,
completar un lab o terminar un examen falla al guardar (la app lo muestra como error de guardado, con "Reintentar guardado").

## Parte F: Social (módulo 4, script 0008)

**Estado: `0008` aplicado en Supabase y verificado** (rama `modulo-4`; el guion dio 219 comprobaciones con 0 fallas el 2026-10-21 y el
Security Advisor no mostró errores; ver "Resultado de la verificación"). El script es `supabase/migrations/0008_social.sql` y su guion de
verificación `supabase/verificacion/0008_escenarios.sql`. La especificación está en `docs/superpowers/specs/2026-10-21-modulo-4-design.md`.
**`0008`, `0009` y `0010` ya no se editan:** cualquier cambio va en un script nuevo (`0010`, las fotos, está descrito más abajo en "Qué crea el 0010"). El banco de preguntas, los retos de ulti y la curva de XP
están en `0009` (**aplicado y verificado el 2026-10-21**: 72 preguntas, 18 respuestas correctas de cada letra, 12 retos y 20 niveles con un
máximo de 1.900 XP). El guion de escenarios se repitió **con el banco real** y dio **219 de 219 comprobaciones, 0 fallas**.

### Qué crea el 0008

| Qué | Detalle |
|---|---|
| **14 tablas**, todas con RLS activo | `social_profile`, `friendships`, `pair_streaks`, `battle_questions`, `ulti_challenges`, `fighter_level_curve`, `battles`, `battle_active`, `matchmaking_queue`, `battle_challenges`, `battle_turns`, `battle_ultis`, `battle_results`, `social_rate_limit` |
| **Acceso directo desde la app (solo 3 tablas)** | `social_profile`: la persona lee, inserta (`user_id`, `username`) y actualiza (`hide_progress`) solo su fila. `battle_results`: lee solo sus filas. `fighter_level_curve`: lectura (dato de referencia). Ninguna lleva el id de Auth de otra persona |
| **11 tablas cerradas** | RLS activo, **sin políticas y sin `GRANT`** para `anon` y `authenticated` (el patrón de `recovery_email_codes`, `0006`). Incluye el banco de preguntas y los retos de ulti: las respuestas y los comandos esperados **nunca** salen del servidor |
| **5 funciones públicas `SECURITY DEFINER`** | `social_friends`, `social_pairs`, `battle_lobby`, `battle_sync` y `battle_play`. La acción va como parámetro y devuelven `jsonb`. Todas con `search_path` vacío, `revoke all ... from public, anon` y `grant execute ... to authenticated`; **ninguna es invocable por `anon`** |
| **1 función auxiliar `SECURITY INVOKER`** | `social_new_code()`: valor por defecto del código de amigo (`NIGHT-` + 6 caracteres) |
| **20 funciones internas (`sx_*`)** | Sin `EXECUTE` para `anon` ni `authenticated`; solo las llaman las 5 públicas |
| Lo que **no** hace | No toca `placement`, `profile`, `devices`, `recovery_email_codes` ni las tablas del módulo 3 (solo **lee** `lesson_progress`, `lab_progress` y `exam_attempts` desde las funciones). Sin Realtime, sin `pg_cron`, sin Edge Function |

- **Tiempos con la hora de la base de datos** (`clock_timestamp()`): 1 minuto por pregunta, 2 minutos de ulti, 60 s de gracia por desconexión y 2 s de tolerancia de red. La app solo **muestra** la cuenta regresiva.
- **Sondeo (D18).** La app llama a `battle_sync` **cada 2 a 3 segundos** mientras haya una batalla abierta, **también durante su turno y durante la ulti**. Si pasan **más de 60 s sin llamadas**, el servidor la da por desconectada y **pierde la batalla**. Los turnos vencidos y las desconexiones se resuelven al llegar la siguiente acción o sondeo.
- **Racha de pareja y racha personal de los amigos** con **hora de Colombia** (`America/Bogota`), calculadas en el servidor con la fecha de completado de cada isla. Se calculan, no se guardan.
- **Respuestas de error.** Las funciones de negocio devuelven `{"status":"error","code":"..."}` **sin lanzar excepción**, para que el contador del límite de ritmo no se deshaga; las excepciones quedan para `no_session`, `social_not_enabled`, `bad_action`, `bad_query` y `rate_limited` (lista completa en el encabezado del script). Ninguna respuesta distingue una cuenta que existe de una que no, salvo que la acción ya sea legítima (la persona está en mis amigos).
- **Límites de ritmo:** 20 búsquedas cada 10 minutos; 20 invitaciones de amistad, 20 de pareja y 20 retos por hora.
- **Limitaciones conocidas:** ganar por abandono o desconexión da **+50 XP**, así que una persona con **dos cuentas** podría farmear XP (sin ranking el impacto es bajo; se revisa si se agrega uno). El orden de los aciertos de la ulti es el de llegada al servidor. Los comandos de la ulti son los de los laboratorios y viajan en el APK. Buscar por usuario permite comprobar que un nombre existe (con límite de ritmo y sin listados). Reportar y bloquear no estaban en `0008` (O15, fuera del módulo); el grupo decidió el 2026-10-22 resolverlo en `0011` (más abajo).

### Qué crea el 0010 (fotos de perfil en Social)

**Estado: aplicado y verificado el 2026-10-22** (permisos por columna y las 4 políticas de `social-avatars`). El script es
`supabase/migrations/0010_fotos_social.sql`. Ya no se edita.

| Qué | Detalle |
|---|---|
| **2 columnas en `social_profile`** | `avatar_key` (uuid, no nulo, único, `gen_random_uuid()`: la **clave aleatoria** que da nombre al archivo `<avatar_key>.jpg`; no es el id de Auth) y `avatar_version` (entero, nulo = sin foto; sube con cada cambio y la app lo usa para su caché). El cliente **no puede escribirlas**: el `GRANT` por columna de `0008` solo da `INSERT` (`user_id`, `username`) y `UPDATE` (`hide_progress`) |
| **1 función pública nueva** | `social_set_avatar(p_has_photo boolean)`, `SECURITY DEFINER`, `search_path` vacío, `revoke` de `public` y `anon`, `grant` a `authenticated`, 30 llamadas por hora. `true` sube `avatar_version`; `false` lo deja nulo **y cambia `avatar_key`** por una clave nueva |
| **4 funciones reemplazadas** | `social_friends`, `social_pairs`, `battle_lobby` y la interna `sx_battle_state` (mismas firmas): devuelven además `avatar_key` y `avatar_version` de cada persona que ya devolvían |
| **Bucket `social-avatars`** | Privado, solo `image/jpeg`, máximo 256 KB, con **4 políticas** en `storage.objects`: leer (cualquier persona con sesión; `anon` nada) y subir, reemplazar y borrar solo el archivo `<avatar_key de la persona>.jpg` |
| Lo que **no** hace | No toca el bucket `avatars` de `0004` ni `profile.avatar_path` (la foto de Editar perfil sigue donde estaba; la app sube **además** una copia al bucket nuevo). Sin tablas nuevas ni `GRANT` nuevos sobre tablas |

- **Orden al borrar la foto.** La app borra primero el archivo del bucket y **solo si salió bien** llama a `social_set_avatar(false)`: después
  de rotar la clave la política ya no reconocería el nombre viejo. Si el borrado falla, no se rota nada y la app lo avisa.
- **Advertencias del Security Advisor.** Queda **1 advertencia nueva**: «Signed-In Users Can Execute SECURITY DEFINER Function» sobre
  `public.social_set_avatar(boolean)` (fila S7 de «Advertencias aceptadas del Security Advisor (Social)»). Con las 9 anteriores quedan
  **10 advertencias y 0 errores**. Las cuatro funciones reemplazadas ya estaban aceptadas (S1 a S4) y no suman otra.
- **Riesgos aceptados** (decisión del grupo):
  - **Listado del bucket.** Como cualquier persona con sesión puede leer el bucket, también puede **listarlo** y ver todas las claves. Son
    uuid aleatorios: no revelan ids de Auth, nombres ni cuentas, porque una clave solo se une a una persona a través de las funciones de
    Social, que ya limitan quién ve a quién. Lo único que se obtiene es la imagen sin nombre. La alternativa (una política por amistad)
    obligaría a consultar `friendships` desde `storage.objects` en cada descarga.
  - **CDN.** Supabase puede servir una copia en caché de un archivo reemplazado (mismo nombre) durante un rato: la versión cambia pero los
    bytes pueden tardar en actualizarse en otros teléfonos.
- **Limitación: moderación.** Las fotos no se revisan antes de publicarse y cualquier persona con sesión puede ver cualquier archivo del
  bucket. Hoy no hay forma de ocultar una foto ofensiva desde la app. La respuesta del grupo es el script `0011` (reportes, bloqueos y
  `avatar_hidden`, en preparación): el equipo revisará los reportes en el Table Editor y ocultará la foto desde el SQL Editor.

### Qué crea el 0011 (reportar y bloquear) — borrador, sin aplicar

**Estado: borrador sin aplicar** (`supabase/migrations/0011_reportes_bloqueos.sql`; su guion de comprobación es `supabase/verificacion/0011_escenarios.sql`
y tampoco se ha ejecutado). Reemplaza la decisión O15 ("fuera del módulo") por la del grupo del 2026-10-22. Se aplica a mano **después** de `0010`.
Mientras no esté aplicado, la app sigue funcionando: el servidor responde `bad_action` a las acciones nuevas y las pantallas de Reportar y Bloquear
muestran «Función no disponible por ahora».

| Qué | Detalle |
|---|---|
| **2 tablas cerradas** | `social_reports` (quién reporta, a quién, motivo, nota de hasta 200 caracteres, fecha y estado `open`/`reviewed`) y `social_blocks` (quién bloquea a quién, clave primaria compuesta). RLS activo, **sin políticas y sin `GRANT`** para `anon` y `authenticated` |
| **1 columna nueva** | `social_profile.avatar_hidden` (booleano, `false` por defecto). El cliente **no puede escribirla**: solo el equipo, como dueño de la tabla |
| **0 funciones públicas nuevas** | Acciones nuevas dentro de `social_friends`: `report`, `block`, `unblock` y `blocks`. Formato de `report`: `destino|motivo|nota` (motivos `inappropriate_photo`, `offensive_name`, `harassment`, `other`; la nota puede llevar `|`, siempre van las dos barras) |
| **1 función interna nueva** | `sx_is_blocked(a, b)`, sin `EXECUTE` para `anon` ni `authenticated` |
| **6 funciones reemplazadas** | `social_friends`, `social_pairs`, `battle_lobby`, `sx_battle_state`, `sx_try_match` y `social_set_avatar` (mismas firmas) |
| **2 políticas de Storage recreadas** | `INSERT` y `UPDATE` de `social-avatars` con la condición `not avatar_hidden` |

- **Bloquear (A bloquea a B).** Se borran la amistad y la pareja de racha y se cancelan los retos pendientes entre los dos. **No termina una batalla en curso.**
  Mientras dure el bloqueo, **en los dos sentidos**: la búsqueda, invitar a amistad, invitar a pareja y retar responden `not_found` (lo mismo que "no existe"), aceptar
  responde como si ya no existiera y la partida rápida no los empareja.
- **Reportar** es posible a **cualquier persona** y no depende del bloqueo. Límite: **10 reportes por día**. No cambia nada para quien reporta. **Ningún número de reportes
  oculta una foto solo.**
- **Ocultar una foto** (`avatar_hidden = true`, lo hace el equipo desde el SQL Editor): las funciones devuelven `avatar_key` y `avatar_version` en `null` para esa persona,
  no puede subir otra foto y `social_set_avatar(true)` responde `{"status":"error","code":"avatar_hidden"}`. El archivo sigue en el bucket: para retirarlo de verdad se borra
  también en Storage. Las consultas para activar y levantar la ocultación, y para revisar los reportes, están en la sección "Operación del equipo" al final del script.
- **Limitación honesta: no hay pantalla de moderación.** El equipo revisa los reportes en el Table Editor de Supabase (tabla `social_reports`) o con la consulta del script,
  y decide a mano. Nadie recibe un aviso cuando llega un reporte. Los reportes repetidos del mismo par no se unifican. Un nombre de usuario ofensivo no se puede cambiar
  desde la app (O3): hoy solo se puede ocultar la foto; para una cuenta abusiva el equipo debe actuar desde el panel de Supabase.
- **Advertencias esperadas del Security Advisor:** ninguna advertencia de función nueva (no hay funciones públicas nuevas); **2 sugerencias informativas** nuevas "RLS Enabled
  No Policy" (`social_reports` y `social_blocks`). Siguen **10 advertencias y 0 errores**. Tabla completa al final del script.
- **No comprobado:** el script no se ha ejecutado contra Supabase. Se comprueba con el guion `0011_escenarios.sql` (dos cuentas de prueba) y las consultas del final del script.
  Hay un punto por confirmar al aplicarlo: que el rol del SQL Editor pueda borrar y recrear las dos políticas de `storage.objects` (si no, el script entero se deshace sin dejar nada).

### Orden de aplicación

1. **Antes:** `0001` a `0007` ya aplicadas. Comprobar que el proyecto no esté pausado (plan gratuito: pausa a los 7 días sin actividad).
2. **Aplicar `0008_social.sql`** pegándolo **completo** en el SQL Editor, **una sola vez** (**hecho**). Una vez aplicado no se edita y cualquier cambio va en un script nuevo.
3. **Correr las consultas de verificación** del final del archivo (a a i) y el **guion de escenarios** (más abajo) (**hecho**: ver "Resultado de la verificación").
4. **Revisar el Security Advisor** y documentar las advertencias aceptadas (**hecho**: ver "Advertencias aceptadas del Security Advisor (Social)").
5. **Aplicar `0009`** (banco de 72 preguntas, retos de ulti y curva de XP) **después** del `0008` (**hecho el 2026-10-21**, con sus consultas de conteo: 72 preguntas, 18 por letra, 12 retos, 20 niveles, máximo 1.900 XP). Antes de aplicarlo las tablas de contenido están vacías: una batalla no puede empezar (`bank_empty`) y todos son nivel 1.
6. **Aplicar `0010`** (fotos) **después** de `0009` (**hecho el 2026-10-22**, con sus consultas de verificación: permisos por columna y las 4 políticas de `social-avatars`). Sin `0010` la app sigue funcionando y muestra el emoji de siempre.
7. **Instalar la versión de la app** de las etapas de Social **después** de `0008` y `0009`: sin las tablas, Social falla al cargar y muestra el error con "Reintentar".

### Cómo verificar

- **Consultas a a i** al final de `0008_social.sql` (comentadas): RLS activo en las 14 tablas; exactamente 5 políticas; ningún privilegio de tabla para `anon` y `SELECT` solo en 3 tablas para `authenticated`; permisos por columna de `social_profile`; `SECURITY DEFINER` y `search_path` de cada función; quién puede ejecutar cada función; claves foráneas solo a `auth.users` y a tablas nuevas; formato del código de amigo; tablas de contenido vacías hasta `0009`.
- **Guion de escenarios** `supabase/verificacion/0008_escenarios.sql` (no se aplica a un proyecto nuevo; es solo una prueba):
  1. Crea **dos cuentas de prueba** en Authentication > Users (sin progreso real) y copia sus ids.
  2. Reemplaza `v_a` y `v_b` (dos líneas marcadas "REEMPLAZA") en el bloque de escenarios.
  3. Ejecuta **todo el archivo** de una vez, con el rol `postgres` del SQL Editor.
  4. El resultado es una tabla con una fila por comprobación (esperado, obtenido, OK o FALLA); la primera fila resume y debe decir **0 fallas**.
  - El bloque de escenarios es **una sola sentencia atómica**: si falla con un error inesperado, se deshace todo. Si termina, **borra** lo que creó y **restaura** el banco, los retos y la curva reales (si ya existían). Cubre permisos, perfiles, búsqueda, invitaciones, ritmo, parejas, partida rápida, retos, respuestas, daño, ulti (éxito, escudo, tres intentos fallidos, por tiempo), turno vencido, desconexión (de uno y de los dos), abandono, fin por HP y por 20 turnos, XP, nivel y "ocultar progreso". **No cubre** parejas múltiples ni los topes de 5, 20 y 50 (necesitan más cuentas).
- **Pruebas desde un teléfono:** no hay pruebas automáticas contra el servidor real (F-03); lo que se verifica a mano está en la lista del informe de cierre del módulo 4 (aún por escribir).

### Resultado de la verificación

| Qué | Resultado |
|---|---|
| Guion `supabase/verificacion/0008_escenarios.sql` | **219 comprobaciones, 0 fallas.** Ejecutado el **2026-10-21** en el SQL Editor, con **dos usuarios de prueba** (ya **eliminados**). El guion borró lo que creó y restauró el banco, los retos y la curva |
| Mismo guion **con el banco real** (después de aplicar `0009`) | **219 de 219 comprobaciones, 0 fallas** (2026-10-21). El guion aparta el banco real mientras corre y lo vuelve a activar al terminar |
| Conteos de `0009` | 72 preguntas (12 por tema, 4 por escalón en cada tema), 18 respuestas correctas de cada letra, 12 retos de ulti (6 de ataque y 6 de defensa) y 20 niveles con un máximo de 1.900 XP |
| Security Advisor tras aplicar `0008` | **0 errores** y las advertencias esperadas (tabla "Advertencias aceptadas del Security Advisor (Social)") |
| Corrección del guion tras la primera corrida | El bloque 9 crea la segunda batalla con la partida rápida y no con `new_battle`, porque `new_battle` borra las batallas anteriores de los dos jugadores. Es un cambio del guion, no del script `0008` |

### Advertencias esperadas del Security Advisor (0008)

Estas eran las advertencias **previstas** antes de aplicar `0008` (tabla copiada del final del script). Ya se aplicó y se revisó el Advisor: las
aceptadas están en "Advertencias aceptadas del Security Advisor (Social)", más abajo.

| # | Advertencia esperada | Objeto | Por qué es `SECURITY DEFINER` | Se acepta o se corrige |
|---|---|---|---|---|
| 1 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.social_friends(text, text)` | Lee y escribe `friendships` (cerrada, con los ids de Auth de dos personas) y lee el progreso de **otra** persona (`lesson_progress`, `lab_progress` y `exam_attempts` son solo del dueño). Devuelve solo nombre de usuario, código, racha y los campos de las insignias; respeta "ocultar progreso". Sin sesión no hace nada; acción de lista cerrada; límites de ritmo, de pendientes y de amigos; no recibe ids. Los resultados de negocio no distinguen una cuenta inexistente de una que no es amiga | Se acepta |
| 2 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.social_pairs(text, text)` | Lee el progreso de **dos** personas para calcular la racha de pareja y escribe `pair_streaks` (cerrada). Devuelve solo racha, si cada una hizo su ejercicio hoy y los días compartidos, nunca qué isla. Exige amistad; máximo 5 invitaciones pendientes y 20 por hora; "no existe" y "no es amigo" dan el mismo código | Se acepta |
| 3 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.battle_lobby(text, text)` | Escribe `matchmaking_queue` y `battle_challenges` y crea batallas (cerradas); empareja a dos personas con un solo candado. Solo opera sobre `auth.uid()` y sus amigos; 20 retos por hora; "no existe" y "no es amigo" dan el mismo código | Se acepta |
| 4 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.battle_sync(uuid)` | Lee `battles` (cerrada, con ids de Auth) y `battle_questions` (sin ningún permiso, con la respuesta correcta) para devolver la vista **personal** de la batalla: la pregunta sin su respuesta y solo el título del reto de ulti de quien llama. Solo los dos jugadores reciben algo; el resto, `not_found` | Se acepta |
| 5 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.battle_play(uuid, text, integer, text)` | Es la única vía de escritura de la batalla: el servidor decide aciertos, daños, turnos, plazos, escudo y XP. Compara la respuesta con `battle_questions` y el comando con `ulti_challenges`, tablas sin permiso para la app. Solo opera sobre `auth.uid()` y sobre batallas donde esa persona participa | Se acepta |
| 6 | (Info) "RLS Enabled No Policy" (11 tablas) | `friendships`, `pair_streaks`, `battle_questions`, `ulti_challenges`, `matchmaking_queue`, `battle_challenges`, `battles`, `battle_active`, `battle_turns`, `battle_ultis`, `social_rate_limit` | No aplica (son tablas): RLS activo sin políticas y sin `GRANT` niega todo acceso directo; solo las leen las funciones de arriba | Se acepta (igual que `recovery_email_codes`, `0006`) |
| 7 | "Public Can Execute SECURITY DEFINER Function" | (ninguna) | Ninguna función de Social es invocable por `anon`: todas llevan `revoke` de `public` y `anon` y exigen sesión | Si aparece alguna, se **corrige** (falta un `revoke`) |
| 8 | "Function Search Path Mutable" | (ninguna) | Todas las funciones, públicas e internas, declaran `set search_path = ''` | Si aparece alguna, se **corrige** |

`social_new_code()` es `SECURITY INVOKER` y las 20 funciones internas `sx_*` no tienen `EXECUTE` para `anon` ni `authenticated`: no generan advertencia de ejecución.

## Advertencias aceptadas del Security Advisor

Revisadas el 19 de octubre de 2026 en el Security Advisor del panel de Supabase. Cada fila dice qué advierte, sobre qué objeto,
de dónde viene, por qué se acepta y cuándo hay que volver a mirarla.

| # | Advertencia | Objeto | Origen | Por qué se acepta | Cuándo revisarla de nuevo |
|---|---|---|---|---|---|
| 1 | "Public Can Execute SECURITY DEFINER Function" | `public.account_status(p_email text)` | Migración `0005` | Requisito del profesor: errores de inicio de sesión individualizados. La función debe poder llamarse **sin sesión** porque se usa justo cuando el login falla. **Riesgo conocido:** permite saber si un correo está registrado (enumeración de cuentas). **Mitigaciones:** solo devuelve `'not_found'`, `'password'` u `'other_provider'`, nunca ids ni otros datos; `search_path` vacío; rechaza correos nulos o de más de 254 caracteres. | Si el requisito del curso cambia. |
| 2 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.account_status(p_email text)` | Migración `0005` | Mismo motivo que la anterior: el registro también la consulta, con sesión o sin ella. | Igual que la 1. |
| 3 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.verify_recovery_email_code(p_code text)` | Migración `0006` | Correcta por diseño: la app debe llamarla para verificar el código del correo de recuperación. Necesita `SECURITY DEFINER` porque lee `recovery_email_codes` y escribe `profile.recovery_email`, a los que la app no tiene acceso directo. Solo actúa sobre `auth.uid()`, no recibe ids, limita a 5 intentos y 10 minutos, y solo la ejecuta `authenticated`. | Si cambia el flujo del correo de recuperación o los permisos de la función. |
| 4 | "Leaked Password Protection Disabled" | Auth | Ajuste del proyecto | No está disponible en el plan gratuito (solo Pro; usa HaveIBeenPwned). **Protección vigente:** mínimo 8 caracteres con minúscula, mayúscula, número y símbolo, igual en la app y en el servidor. | Si el proyecto pasa a un plan de pago: activarla. |
| 5 | (Info) "RLS Enabled No Policy" | `public.recovery_email_codes` | Migración `0006` | Intencional: RLS activo sin políticas niega todo acceso desde la app. Solo la usan la Edge Function `send-recovery-email-code` (`service_role`) y `verify_recovery_email_code` (`SECURITY DEFINER`). Es la configuración más cerrada posible. | Solo si la tabla cambia de uso. |

**Nota.** Las tablas del módulo 3 (`lesson_progress`, `lab_progress`, `exam_attempts`, migración `0007`) no generan advertencias.
Si una migración futura agrega una advertencia nueva, se corrige o se documenta aquí con su motivo **antes de fusionar a `main`**.
Las del `0008` (módulo 4, Social) están en la sección siguiente.

## Advertencias aceptadas del Security Advisor (Social)

Revisadas tras aplicar `0008` (módulo 4). El Advisor mostró **0 errores**. Con estas, quedan **9 advertencias en total**: las 4 de arriba
(filas 1 a 4) y las **5 nuevas** de Social; la fila informativa "RLS Enabled No Policy" (nivel info) se suma a la que ya existía de
`recovery_email_codes`. Cada fila dice qué advierte, sobre qué objeto, por qué la función es `SECURITY DEFINER` y por qué se acepta.
El detalle del diseño está en la Parte F y en el encabezado de `0008_social.sql`.

| # | Advertencia | Objeto | Por qué es `SECURITY DEFINER` | Por qué se acepta | Cuándo revisarla de nuevo |
|---|---|---|---|---|---|
| S1 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.social_friends(text, text)` | Lee y escribe `friendships` (cerrada, con los ids de Auth de dos personas) y lee el progreso de **otra** persona (`lesson_progress`, `lab_progress` y `exam_attempts` son solo del dueño) | Es la única vía a esos datos y la app debe llamarla con sesión. Solo actúa sobre `auth.uid()`; no recibe ids de Auth (usa nombre de usuario o código); acción de lista cerrada; devuelve solo nombre de usuario, código, racha y los campos de las insignias y respeta "ocultar progreso"; límites de ritmo (20 búsquedas cada 10 minutos, 20 invitaciones por hora), de pendientes y de amigos; los resultados de negocio no distinguen una cuenta inexistente de una que no es amiga; `anon` no puede ejecutarla | Si cambia lo que devuelve o la lista de acciones |
| S2 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.social_pairs(text, text)` | Lee el progreso de **dos** personas para calcular la racha de pareja y escribe `pair_streaks` (cerrada) | Devuelve solo racha, si cada persona hizo su ejercicio hoy y los días compartidos, nunca qué isla. Exige amistad; máximo 5 invitaciones pendientes y 20 por hora; "no existe" y "no es amigo" dan el mismo código; solo `auth.uid()` | Igual que S1 |
| S3 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.battle_lobby(text, text)` | Escribe `matchmaking_queue` y `battle_challenges` y crea batallas (tablas cerradas); empareja a dos personas con un solo candado | Solo opera sobre `auth.uid()` y sus amigos; 20 retos por hora; "no existe" y "no es amigo" dan el mismo código; el reto vale 2 minutos | Igual que S1 |
| S4 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.battle_sync(uuid)` | Lee `battles` (cerrada, con ids de Auth) y `battle_questions` (sin ningún permiso para la app, con la respuesta correcta) | Devuelve la vista **personal** de la batalla: la pregunta sin su respuesta y solo el título del reto de ulti de quien llama. Solo los dos jugadores reciben algo; cualquier otra persona recibe `not_found` | Si cambia la vista que devuelve |
| S5 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.battle_play(uuid, text, integer, text)` | Es la única vía de escritura de la batalla: compara la respuesta con `battle_questions` y el comando con `ulti_challenges`, tablas sin permiso para la app | El servidor decide aciertos, daños, turnos, plazos, escudo y XP con la hora de la base de datos. Solo opera sobre `auth.uid()` y sobre batallas donde esa persona participa | Si se agrega una acción nueva |
| S6 | (Info) "RLS Enabled No Policy" | 11 tablas: `friendships`, `pair_streaks`, `battle_questions`, `ulti_challenges`, `matchmaking_queue`, `battle_challenges`, `battles`, `battle_active`, `battle_turns`, `battle_ultis`, `social_rate_limit` | No aplica (son tablas) | Intencional: RLS activo sin políticas y sin `GRANT` niega todo acceso directo desde la app; solo las leen las funciones de arriba. Es la misma configuración de `recovery_email_codes` (`0006`, fila 5 de la tabla anterior) | Solo si una tabla cambia de uso |
| S7 | "Signed-In Users Can Execute SECURITY DEFINER Function" | `public.social_set_avatar(boolean)` | Script `0010`. La app no puede escribir `avatar_key` ni `avatar_version` (sin permiso de columna): solo esta función las cambia | Identidad solo desde `auth.uid()`, sin parámetros de usuario más que "tengo / no tengo foto", 30 llamadas por hora, sin acceso para `public` ni `anon`. Repetir la llamada es inofensivo | Si cambia lo que escribe |

Tras aplicar `0010` (2026-10-22): **10 advertencias y 0 errores** (las 4 de la primera tabla, S1 a S5 y S7; S6 es solo informativa). Las cuatro
funciones que `0010` reemplaza (`social_friends`, `social_pairs`, `battle_lobby` y la interna `sx_battle_state`, que se muestra a través de
`battle_sync` y `battle_play`) conservan su aceptación (S1 a S5). Nota: la tabla del final de `0010` dice «las 4 filas de 0008» y nombra cinco
funciones; las filas aceptadas son cinco (S1 a S5).

No hay advertencias de "Public Can Execute SECURITY DEFINER Function" ni de "Function Search Path Mutable" para Social: ninguna función se puede
llamar sin sesión y todas declaran `set search_path = ''`. `social_new_code()` es `SECURITY INVOKER` y las 20 funciones internas `sx_*` no
tienen `EXECUTE` para `anon` ni `authenticated`.
