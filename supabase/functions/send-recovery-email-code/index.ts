// NightWatch Protocol - Edge Function send-recovery-email-code
//
// Envía un código de 6 dígitos al CORREO DE RECUPERACIÓN (el alterno) del usuario con sesión. Supabase Auth solo
// envía códigos al correo principal, por eso este envío es propio: el código se genera aquí, se guarda solo su
// hash en public.recovery_email_codes (migración 0006) y el correo sale por la API transaccional de Brevo.
// La app comprueba el código con la función SQL public.verify_recovery_email_code.
//
// Petición:  POST  { "email": "respaldo@example.com" }  con el JWT del usuario en Authorization.
// Respuestas (JSON):
//   200 { ok: true }
//   400 { error: "invalid_email" | "same_as_primary" | "invalid_request" }
//   401 { error: "unauthorized" }
//   405 { error: "method_not_allowed" }
//   429 { error: "rate_limited", retry_after_seconds }   (menos de 60 s desde el último envío, o 5 envíos en 1 hora)
//   500 { error: "not_configured" | "storage_failed" }
//   502 { error: "email_failed" }                         (Brevo rechazó el envío; el código guardado se borra)
//
// El código y su hash NUNCA se devuelven ni se escriben en los registros.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const CODE_LENGTH = 6;
const CODE_TTL_MS = 10 * 60 * 1000; // vence a los 10 minutos
const MIN_INTERVAL_MS = 60 * 1000; // al menos 60 s entre dos envíos al mismo usuario
const WINDOW_MS = 60 * 60 * 1000; // ventana de 1 hora...
const MAX_SENDS_PER_WINDOW = 5; // ...con un máximo de 5 envíos
const MAX_EMAIL_LENGTH = 254;
const EMAIL_FORMAT = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const SUBJECT = "Verifica tu correo de recuperación de NightWatch Protocol";

function json(status: number, body: Record<string, unknown>, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function rateLimited(retryAfterSeconds: number): Response {
  const seconds = Math.max(1, Math.ceil(retryAfterSeconds));
  return json(429, { error: "rate_limited", retry_after_seconds: seconds }, { "Retry-After": String(seconds) });
}

/** Código de CODE_LENGTH dígitos con ceros a la izquierda, sin sesgo (se descartan los valores que lo causarían). */
function generateCode(): string {
  const modulus = 10 ** CODE_LENGTH;
  const limit = Math.floor(0x100000000 / modulus) * modulus;
  const buffer = new Uint32Array(1);
  do {
    crypto.getRandomValues(buffer);
  } while (buffer[0] >= limit);
  return String(buffer[0] % modulus).padStart(CODE_LENGTH, "0");
}

/** sha256 en hexadecimal minúscula. Debe coincidir con encode(digest(texto, 'sha256'), 'hex') de la migración 0006. */
async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** Mismo estilo que las plantillas de Supabase: el código grande y poco más. `code` son solo dígitos. */
function emailHtml(code: string): string {
  return `<!doctype html>
<html lang="es">
  <body style="margin:0;padding:24px;background:#0f1d1c;font-family:Arial,Helvetica,sans-serif;color:#e8f1f0;">
    <div style="max-width:420px;margin:0 auto;background:#162523;border-radius:16px;padding:32px 24px;text-align:center;">
      <h1 style="margin:0 0 8px;font-size:20px;">NightWatch Protocol</h1>
      <p style="margin:0 0 24px;font-size:14px;color:#a9c3c0;">Tu código para verificar el correo de recuperación:</p>
      <p style="margin:0 0 24px;font-size:36px;font-weight:bold;letter-spacing:8px;color:#ffffff;">${code}</p>
      <p style="margin:0 0 8px;font-size:13px;color:#a9c3c0;">Este código vence en 10 minutos.</p>
      <p style="margin:0;font-size:13px;color:#a9c3c0;">Si no fuiste tú, ignora este correo.</p>
    </div>
  </body>
</html>`;
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const brevoApiKey = Deno.env.get("BREVO_API_KEY");
  const senderEmail = Deno.env.get("BREVO_SENDER_EMAIL");
  const senderName = Deno.env.get("BREVO_SENDER_NAME");
  if (!supabaseUrl || !serviceRoleKey || !brevoApiKey || !senderEmail || !senderName) {
    console.error("send-recovery-email-code: faltan secretos de configuración");
    return json(500, { error: "not_configured" });
  }

  // 1. Usuario: sale del JWT de la petición. Sin usuario válido (incluida la llave anónima, que también es un JWT) -> 401.
  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!jwt) return json(401, { error: "unauthorized" });

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userData, error: userError } = await admin.auth.getUser(jwt);
  const user = userData?.user;
  if (userError || !user) return json(401, { error: "unauthorized" });

  // 2. Cuerpo.
  let rawEmail: unknown;
  try {
    rawEmail = (await req.json())?.email;
  } catch {
    return json(400, { error: "invalid_request" });
  }
  if (typeof rawEmail !== "string") return json(400, { error: "invalid_request" });
  const email = rawEmail.trim();
  if (email.length === 0 || email.length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.test(email)) {
    return json(400, { error: "invalid_email" });
  }
  if (email.toLowerCase() === (user.email ?? "").toLowerCase()) {
    return json(400, { error: "same_as_primary" });
  }

  // 3. Límites: 60 s entre envíos y 5 por hora.
  const now = Date.now();
  let windowStart = now;
  let sentCount = 1;
  const { data: existing, error: readError } = await admin
    .from("recovery_email_codes")
    .select("last_sent_at, sent_count_window_start, sent_count")
    .eq("user_id", user.id)
    .maybeSingle();
  if (readError) {
    console.error("send-recovery-email-code: no se pudo leer el estado", readError.code);
    return json(500, { error: "storage_failed" });
  }
  if (existing) {
    const sinceLast = now - new Date(existing.last_sent_at).getTime();
    if (sinceLast < MIN_INTERVAL_MS) return rateLimited((MIN_INTERVAL_MS - sinceLast) / 1000);

    const existingWindowStart = new Date(existing.sent_count_window_start).getTime();
    if (now - existingWindowStart < WINDOW_MS) {
      if (existing.sent_count >= MAX_SENDS_PER_WINDOW) {
        return rateLimited((existingWindowStart + WINDOW_MS - now) / 1000);
      }
      windowStart = existingWindowStart;
      sentCount = existing.sent_count + 1;
    }
  }

  // 4. Código nuevo: solo se guarda su hash. Reemplaza al anterior y reinicia los intentos.
  const code = generateCode();
  const codeHash = await sha256Hex(`${user.id}:${code}`);
  const { error: saveError } = await admin.from("recovery_email_codes").upsert(
    {
      user_id: user.id,
      email,
      code_hash: codeHash,
      expires_at: new Date(now + CODE_TTL_MS).toISOString(),
      attempts: 0,
      last_sent_at: new Date(now).toISOString(),
      sent_count_window_start: new Date(windowStart).toISOString(),
      sent_count: sentCount,
    },
    { onConflict: "user_id" },
  );
  if (saveError) {
    // Solo el código de error: el detalle de Postgres puede incluir la fila completa (con el hash).
    console.error("send-recovery-email-code: no se pudo guardar el código", saveError.code);
    return json(500, { error: "storage_failed" });
  }

  // 5. Envío por la API transaccional de Brevo. Si falla, el código guardado no sirve: se borra.
  let sent = false;
  try {
    const response = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: { "api-key": brevoApiKey, "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        sender: { name: senderName, email: senderEmail },
        to: [{ email }],
        subject: SUBJECT,
        htmlContent: emailHtml(code),
      }),
    });
    sent = response.ok;
    if (!sent) console.error("send-recovery-email-code: Brevo respondió", response.status);
  } catch {
    console.error("send-recovery-email-code: no se pudo contactar a Brevo");
  }
  if (!sent) {
    await admin.from("recovery_email_codes").delete().eq("user_id", user.id);
    return json(502, { error: "email_failed" });
  }

  return json(200, { ok: true });
});
