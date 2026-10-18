# send-recovery-email-code

Edge Function que envía el código de 6 dígitos al **correo de recuperación** (el alterno) del usuario. Supabase Auth
solo envía códigos al correo principal, así que este envío es propio y sale por la **API transaccional de Brevo**.

Va junto con la migración `supabase/migrations/0006_verificacion_correo_recuperacion.sql` (la tabla donde se guarda el
hash del código y la función SQL que lo verifica). Hay que ejecutar la migración **antes** de usar la función.

Esta carpeta no se despliega sola: se pega a mano en el panel (no se usa la CLI de Supabase).

## Qué hace

- Exige el JWT del usuario (`Authorization: Bearer <access token>`); sin usuario válido responde `401`.
- Cuerpo: `{ "email": "respaldo@example.com" }`. Valida el formato y que tenga 254 caracteres o menos.
  Si es igual al correo principal de la cuenta responde `400 { "error": "same_as_primary" }`.
- Límites por usuario: 60 segundos entre dos envíos y 5 envíos por hora. Si se pasa responde
  `429 { "error": "rate_limited", "retry_after_seconds": N }`.
- Genera un código con `crypto.getRandomValues`, guarda **solo su hash** (`sha256("<user_id>:<código>")`, el mismo
  formato que la función SQL) con vencimiento a 10 minutos y 0 intentos, y envía el correo.
- Si Brevo falla responde `502 { "error": "email_failed" }` y borra el código guardado.
- Éxito: `200 { "ok": true }`. **Nunca** devuelve ni registra el código ni el hash.

## Despliegue (desde el panel de Supabase)

1. Panel de Supabase → **Edge Functions** → **Deploy a new function** → **Via Editor**.
2. Nombre de la función, exactamente: `send-recovery-email-code` (la app la invoca por ese nombre).
3. Pega todo el contenido de `index.ts` y pulsa **Deploy**.
4. En los ajustes de la función deja **Verify JWT** activado (es la opción por defecto; la función además comprueba el
   usuario por dentro).
5. **Edge Functions → Secrets** (o *Manage secrets*): agrega los tres secretos de abajo.

## Secretos

`SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` los provee Supabase solos; **no** hay que crearlos. Los que sí:

| Secreto              | Valor                                                                                      |
|----------------------|--------------------------------------------------------------------------------------------|
| `BREVO_API_KEY`      | Una **API key** de Brevo (SMTP & API → API Keys). No es la clave SMTP que usa Auth.        |
| `BREVO_SENDER_EMAIL` | El remitente verificado en Brevo (el mismo que usas en el SMTP de Supabase).               |
| `BREVO_SENDER_NAME`  | El nombre que verá quien recibe el correo, por ejemplo `NightWatch Protocol`.              |

Estos correos salen por la **API de Brevo**, no por el SMTP de Supabase: cuentan en el límite diario de Brevo, no en
el de Supabase.

## Prueba rápida sin la app (opcional)

Con una sesión real, `POST https://<proyecto>.supabase.co/functions/v1/send-recovery-email-code` con la cabecera
`Authorization: Bearer <access_token del usuario>` y el cuerpo `{"email":"otro@example.com"}`. Con la llave publicable
como token (sin usuario) debe responder `401`.
