# Auditoría de secretos antes de la entrega (2026-11-08)

El repositorio pasó a ser **público** el 2026-11-08. Esta auditoría revisó el árbol actual y **todo el historial** (todas las ramas locales y
remotas, `git rev-list --all`). Es de solo lectura: no se cambió código ni historial. No se copia ningún valor sensible en este informe.

## Resultado

**No se encontró ningún secreto real.** No hace falta rotar ninguna clave ni reescribir el historial. Es seguro que el repositorio siga público,
con las observaciones menores de la última sección.

## Qué se buscó y qué se encontró

| Búsqueda | Resultado |
|---|---|
| `local.properties`, `google-services.json`, `.env`, `CLAUDE.local.md` en el historial (`git log --all --full-history -- <archivo>`) | **Nunca estuvieron versionados.** Siguen en el `.gitignore` (también `*.jks` y `*.keystore`) |
| Llaves de firma (`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`) alguna vez añadidas | Ninguna |
| Archivos versionados hoy con nombres sensibles | Solo `local.properties.example` (plantilla con marcadores) |
| Tokens JWT (`eyJ...`), claves `sb_secret_*` y `sb_publishable_*` reales | Ninguno. Solo aparece el marcador `sb_publishable_TU_CLAVE` en la plantilla y en dos documentos |
| Claves de Brevo (`xkeysib-`), Google API (`AIza...`), secretos OAuth (`GOCSPX-`), AWS (`AKIA...`), tokens de GitHub, bloques `PRIVATE KEY` | Ninguno |
| ID de cliente OAuth de Google (`...apps.googleusercontent.com`) | Ninguno escrito en el repo (tampoco sería secreto) |
| URL real del proyecto de Supabase (`<ref>.supabase.co`) | Ninguna. Solo marcadores (`TU-PROYECTO`, `abc`) y la ruta de la Edge Function |
| `service_role`, `BREVO_API_KEY`, `api-key`, `secret`, `password`, `token` con un valor largo asignado | Solo nombres de clases, de campos y de pruebas (`WeakPassword`, `ChangePasswordViewModel`...) y menciones en documentación; ningún valor |
| Cadenas largas tipo clave en `.kt`, `.md`, `.ts`, `.sql`, `.properties`, `.toml`, `.xml` | Solo nombres de pruebas y el trazado de un ícono vectorial |
| Teléfonos | Ninguno |

El código lee `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` y `GOOGLE_WEB_CLIENT_ID` de `local.properties` (no versionado). La clave
*publishable* y la URL serían públicas por diseño (la seguridad está en RLS), pero ni siquiera están en el repositorio.

## Datos personales

Direcciones de correo reales encontradas en el historial (solo archivo, commit y tipo):

| Dónde | Commit | Tipo |
|---|---|---|
| `app/src/test/.../data/profile/ProfileFormattersTest.kt` | `4dc4752`, `07417ed`, `94f5c36` | Dos correos `@gmail.com` de personas del equipo como dato de prueba |
| `app/src/test/.../data/avatar/AvatarDisplayTest.kt` | `4dc4752` | Un correo `@gmail.com` como dato de prueba |
| `app/src/main/.../ui/profile/myprofile/MyProfileScreen.kt` | `d6b1933`, `6a7c796` | Un correo `@gmail.com` de un integrante dentro de un comentario |

Todo lo demás son direcciones de ejemplo (`@example.com`, `user@gmail.com`) y la línea de coautoría de Claude.

Son correos personales, no credenciales: no dan acceso a nada. Quitarlos del historial exigiría reescribirlo (`git filter-repo` y push forzado),
lo que está prohibido por las reglas del equipo y rompería el tag `v2.4.0`. **Decisión para el grupo:** aceptarlo, o cambiar los datos de prueba y el
comentario por `@example.com` en una rama aparte (solo limpia el árbol actual, no el historial).

## Capturas de pantalla (`docs/screenshots/`)

Se revisaron las seis imágenes (login, inicio, ruta, labs, arena, idioma). No muestran correos, claves ni datos de cuentas reales. Aparecen el
nombre de usuario de prueba «ciber», fotos de perfil de prueba (un perro y un auto) y la hora y la batería del teléfono. La pantalla de login
tiene los campos vacíos.

## Recomendaciones que no son urgentes

- Confirmar en el panel de Supabase que la clave `secret`/`service_role` y las de Brevo nunca se pegaron en un chat o un documento fuera del repo
  (esta auditoría solo ve lo que está en git).
- Comprobar que la Edge Function `send-recovery-email-code` sigue leyendo sus tres secretos del entorno (así está en el repo).
- Con el repositorio público, el APK de Releases lleva la URL y la clave *publishable* dentro; es lo esperado y la protección es RLS.
