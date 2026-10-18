# Migración de Firebase a Supabase: informe final

Versión de la app: **2.0.0**. Rama de trabajo: `experimento/supabase` (que pasa a ser `main`). Este es el informe
histórico de la migración: aquí sí se nombra Firebase, porque es de lo que se migró. El resto de la documentación y todo
el código describen solo Supabase.

## 1. Decisión del equipo y por qué

El proyecto nació con Firebase (Authentication y Cloud Firestore). Se probó migrar a Supabase en la rama
`experimento/supabase` mientras `main` seguía con Firebase, con la regla de que ambos modos convivieran (`BACKEND=firebase`
o `BACKEND=supabase`). Con la migración probada en dispositivo, **el equipo decidió quedarse solo con Supabase y quitar
Firebase del código**. Razones:

- **Seguridad visible y revisable.** Las reglas de acceso son SQL (RLS, privilegios por columna, funciones) en
  `supabase/migrations`, versionadas en git y comprobables con consultas; en Firebase eran reglas en un archivo que se
  publicaba a mano en la consola.
- **Revocación real de sesiones.** Con Firebase "cerrar sesión en otro dispositivo" solo podía ser una marca cooperativa;
  con Supabase `signOut(OTHERS)` invalida los *refresh tokens* de las demás sesiones.
- **Una sola ruta de código.** Mantener dos backends obligaba a banderas de capacidad en las interfaces, ramas en la
  interfaz y el doble de pruebas. Con un backend, las interfaces y los flujos son más simples.
- **Códigos por correo en lugar de enlaces.** La app no maneja *deep links*; con Supabase todo (registro, cambio de correo,
  recuperación) funciona con códigos de 6 dígitos.

## 2. Qué cambió

| Etapa | Qué se hizo |
|---|---|
| Etapa 0 | Evaluación de viabilidad (`supabase-evaluacion.md`): versiones, `minSdk` 26, flujos que cambian. Se trajeron las mejoras de mantenimiento |
| Bloque A | `SupabaseAuthRepository`, repositorios de perfil, dispositivos, quiz y foto (Storage); esquema SQL con RLS; errores individualizados con `account_status`; espera de la sesión al arrancar |
| Bloque B | Login con Google (ID token + nonce), registro con código, cambio de correo con código, recuperación de contraseña con código, reenvío con cuenta regresiva, revocación real de sesiones |
| Parte C | Quiz y resultados con el espaciado de Figma; desvincular la cuenta de Google vieja al cambiar el correo; cerrar la sesión de recuperación interrumpida; "Cerrar sesión en todos" siempre revoca en el servidor |
| Parte D | Verificación del correo de recuperación con un código (migración `0006`, Edge Function `send-recovery-email-code`) |
| Partes E y F | Teclado que no tapa los formularios, botones que se desplazan con el contenido, "Confirma tu identidad" con la distribución de "Confirmar Correo" |
| Parte G | Correcciones de la revisión de código: arranque robusto, diálogo de recuperación sin sesión abierta, mensaje sin cuenta de Google, accesibilidad y estilo |
| Parte H | **Firebase fuera:** se quitaron el enum `Backend`, las implementaciones Firebase y sus pruebas, los flujos por enlace, las banderas de capacidad, las dependencias y el plugin, y `firestore.rules`; el ID de cliente de Google sale de `local.properties` |
| Parte I | README para el equipo y este informe |

Números: las pruebas unitarias pasaron de **373** (línea base) a **597**. En la parte H bajaron de 657 a 597 al eliminar
las de Firebase: 49 de los archivos borrados (`AuthErrorMapperTest`, `FirestoreAccessTest`, `BackendTest`,
`ForgotPasswordViewModelTest`, `VerifyEmailLinkViewModelTest`), 16 de las banderas de capacidad y del guardado directo del
correo de recuperación, y 5 nuevas (`AppConfigTest`). `lintDebug`: 0 errores y 34 advertencias.

## 3. Firebase frente a Supabase

| | Firebase (antes) | Supabase (ahora) |
|---|---|---|
| Reglas de acceso | Archivo `firestore.rules` publicado a mano | SQL en el repositorio: RLS, privilegios mínimos, funciones `SECURITY DEFINER` |
| Datos | Documentos (`users/{uid}/...`) | Tablas Postgres con restricciones `CHECK` |
| Sesiones de otros dispositivos | Solo marca cooperativa | Revocación real con `signOut(OTHERS)` (más la marca cooperativa) |
| Recuperación y cambio de correo | Enlaces (necesitaban *deep links*) | Códigos de 6 dígitos |
| Errores "correo no registrado" | Los daba el servidor (con la protección de enumeración desactivada) | Función `account_status` (compromiso aceptado, ver sección 4) |
| Modo sin conexión | **Sí**: Firestore guardaba en cola y sincronizaba después | **No**: sin conexión la escritura falla y queda una copia local solo del género, la fecha y la foto |
| Plan gratuito | Sin pausa por inactividad | **El proyecto se pausa tras 7 días sin actividad** |
| Correos | Los enviaba Firebase | Requieren SMTP propio (Brevo); límite diario del plan gratuito |
| Despliegue | Consola de Firebase | Migraciones, ajustes del panel y Edge Function a mano |

Lo que **se perdió**: el modo sin conexión de Firestore y la comodidad de los correos ya resueltos por Firebase. Lo que **se
ganó**: control y transparencia del acceso a los datos, revocación real de sesiones y un solo backend.

## 4. Medidas de seguridad

- **RLS en todas las tablas** (`placement`, `profile`, `devices`, `recovery_email_codes`) con políticas por dueño y
  `anon` sin acceso a nada.
- **Mínimo privilegio** (`0002`): se quitan a `authenticated` los privilegios `TRUNCATE`, `REFERENCES` y `TRIGGER` que
  Supabase concede por defecto; `0003` revoca `EXECUTE` de `rls_auto_enable()`.
- **Security Advisor.** Se revisó y se corrigió lo corregible. Queda una advertencia **aceptada a propósito**:
  `account_status` es `SECURITY DEFINER` y la pueden ejecutar `anon` y `authenticated`, lo que permite preguntar si un
  correo tiene cuenta (enumeración de cuentas). Existe por un **requisito del curso** (errores individualizados de login
  y registro); devuelve solo una de tres palabras y rechaza correos nulos o de más de 254 caracteres.
- **Códigos con hash.** El código del correo de recuperación se guarda como `sha256("<user_id>:<código>")`, nunca en
  claro; 5 intentos por código, vence a los 10 minutos, 60 s entre envíos y 5 envíos por hora. `profile.recovery_email` ya
  no se puede escribir desde la app (privilegios por columna): solo la función `verify_recovery_email_code`.
- **Revocación real de sesiones** con `signOut(OTHERS)` (el token de acceso ya emitido vale hasta que expira, 1 hora).
- **Google con nonce:** 32 bytes aleatorios; su SHA-256 va a Credential Manager y el nonce crudo a Supabase.
- **Sesión de recuperación:** se guarda una marca *antes* de verificar el código; se borra al guardar la contraseña, al
  salir o al cerrar el diálogo; si queda puesta, el siguiente arranque cierra esa sesión.
- **Sin secretos en la app:** solo la clave *publishable* en `BuildConfig`; la `secret`/`service_role` y las claves de
  Brevo viven solo en el panel; ningún registro (log) imprime tokens, códigos ni nonces.

## 5. Diferencias deliberadas frente a Figma

- **Recuperación por correo, no por SMS** (Figma `113:38` y `110:332` piden un celular): el SMS cuesta dinero, así que el
  diálogo pide el correo y manda el código por correo; los textos se adaptaron.
- **Pantalla de contraseña nueva** (no existe en Figma): es "Cambiar Contraseña" sin la contraseña actual.
- **"Confirma tu identidad"** (no existe en Figma): se arma con la distribución de "Confirmar Correo" (`2106:50`) y la
  ilustración `id_card` de Material Symbols (Apache 2.0).
- **Confirmar la identidad antes de enviar el código** de cambio de correo, porque el servidor nunca pide un inicio de
  sesión reciente en ese caso.
- **El recuadro del código:** barra gris en las pantallas (`2106:61`) y seis casillas en el diálogo (`110:332`); son el
  mismo campo.

## 6. Problemas reales encontrados y cómo se resolvieron

- **SMTP de Gmail bloqueado.** Supabase no pudo enviar correos por el SMTP de Gmail (el acceso quedó bloqueado). Se
  cambió a **Brevo** como SMTP propio y se dejó un remitente verificado.
- **IP autorizadas de Brevo.** La opción de IP autorizadas de Brevo rechaza las llamadas desde servidores con IP
  variable (SMTP de Supabase y Edge Functions); hay que tenerla **desactivada** para que los correos salgan.
- **Sesión asíncrona.** supabase-kt carga la sesión guardada de forma asíncrona: leer el usuario antes de tiempo mandaba
  a un usuario con sesión al login. Se añadió `awaitSessionReady()` con un tiempo máximo, y el arranque decide la ruta solo
  después (y ahora, aunque falle la lectura local, nunca se queda en el *splash*).
- **Privilegios por defecto.** Supabase concede a `authenticated` más privilegios de los necesarios y expone funciones
  creadas por la plataforma; se recortaron con `0002` y `0003`, y la tabla de códigos se creó sin ningún privilegio.
- **`Auth.signOut()` sin red no limpia la sesión local:** el cierre de sesión siempre limpia localmente aunque el servidor
  no responda.
- **`otp_expired` para códigos incorrectos y vencidos:** el servidor no distingue, así que la app decide "expiró" con su
  propio reloj (600 s).

## 7. Limitaciones conocidas y trabajo pendiente

Pendiente (con el identificador de la revisión de código, ver
[`revision-codigo-experimento-supabase.md`](revision-codigo-experimento-supabase.md)):

- **Prueba de punta a punta del servidor (F-03):** no hay pruebas automáticas que ejecuten la Edge Function, la
  migración `0006` ni las llamadas reales; se verifican a mano (lista de pruebas del diagnóstico final).
- **F-08:** el flujo de Google sigue duplicado entre Login y Registro.
- **F-09:** ningún texto está en `strings.xml` (heredado del proyecto original).
- **F-12:** el aviso de "Correo actualizado" viaja como texto en la ruta de navegación.
- **F-15:** las pruebas de repositorio usan un `Proxy` sobre `Auth` y no hay pruebas de Compose de las pantallas nuevas.
- **F-05 y F-06:** la sesión no está cifrada con Keystore y la Edge Function no tiene un límite global de envíos.
- **Actualizar Kotlin para poder usar supabase-kt 3.8.0.** La versión actual del proyecto (Kotlin 2.2.10) obliga a
  quedarse en supabase-kt 3.2.4 y Ktor 3.3.0; subir Kotlin es una decisión del equipo y se hace aparte.
- **Residuo del avatar en Base64:** `RemoteProfile.avatarBase64` y la ruta de Base64 de `AvatarRepository` existían para
  Firestore y ya nunca se llenan; se pueden retirar con un cambio de pruebas.
- **La migración `0001` y la `0005` conservan en sus comentarios SQL la mención de Firebase** porque ya se ejecutaron y una
  migración aplicada no se edita.

Limitaciones del servicio (ver README): pausa del proyecto tras 7 días sin actividad, límite de correos de Brevo y
remitente `@gmail.com` que puede caer en spam.
