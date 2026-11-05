# Plantillas de correo de Auth, bilingües (español + English)

Etapa 10 del módulo 5, decisión D17. **Un solo correo con dos bloques** (español arriba, English abajo), sin condicionales por idioma:
Supabase Auth no sabe en qué idioma está la app, y así quien se registra recibe el código sea cual sea su idioma.

**Qué plantillas se usan.** Se revisó el código (`data/supabase`, `ui/verifycode`): la app usa códigos de 6 dígitos (`{{ .Token }}`) en
tres flujos y **ningún enlace**:

| Plantilla del panel | Flujo de la app | Archivo de abajo |
|---|---|---|
| Confirm sign up | Registro (`OtpType SIGNUP`) | 1 |
| Change email address | Cambio de correo (`EMAIL_CHANGE`) | 2 |
| Reset password | Recuperación de contraseña (`RECOVERY`) | 3 |

"Magic link", "Invite user" y "Reauthentication" **no se usan** (la app no las dispara): no se tocan. El correo de recuperación
alterno no es de Auth: lo envía la Edge Function `send-recovery-email-code`, que ya responde según el idioma de la app (ver su README).

**Cómo se pega (a mano, no lo hace Claude Code ni la app):** Supabase → Authentication → Emails → Templates → elegir la plantilla →
poner el *Subject* y pegar el HTML en *Message body* → Save. Hay que dejar en el panel, sin cambios: Email OTP length **6**, expiración
**600 s**, sin `{{ .ConfirmationURL }}` (ver `docs/superpowers/supabase-configuracion.md`). Probar cada flujo con una cuenta de prueba
después de pegar. El estilo es el mismo de las plantillas actuales y de la Edge Function (fondo oscuro, código grande).

> El texto de "10 minutos" coincide con los 600 s del panel. Si se cambia ese ajuste, cambiar también los textos de aquí.

---

## 1. Confirm sign up

**Subject:** `Verifica tu correo · Verify your email – NightWatch Protocol`

```html
<!doctype html>
<html>
  <body style="margin:0;padding:24px;background:#0f1d1c;font-family:Arial,Helvetica,sans-serif;color:#e8f1f0;">
    <div style="max-width:420px;margin:0 auto;background:#162523;border-radius:16px;padding:32px 24px;text-align:center;">
      <h1 style="margin:0 0 8px;font-size:20px;">NightWatch Protocol</h1>

      <p style="margin:0 0 4px;font-size:14px;color:#a9c3c0;">Tu código para verificar tu correo:</p>
      <p style="margin:0 0 24px;font-size:12px;color:#a9c3c0;">Your code to verify your email:</p>
      <p style="margin:0 0 24px;font-size:36px;font-weight:bold;letter-spacing:8px;color:#ffffff;">{{ .Token }}</p>

      <p style="margin:0 0 4px;font-size:13px;color:#a9c3c0;">Este código vence en 10 minutos. Si no fuiste tú, ignora este correo.</p>
      <p style="margin:0;font-size:12px;color:#a9c3c0;">This code expires in 10 minutes. If this wasn't you, ignore this email.</p>
    </div>
  </body>
</html>
```

## 2. Change email address

**Subject:** `Confirma tu nuevo correo · Confirm your new email – NightWatch Protocol`

```html
<!doctype html>
<html>
  <body style="margin:0;padding:24px;background:#0f1d1c;font-family:Arial,Helvetica,sans-serif;color:#e8f1f0;">
    <div style="max-width:420px;margin:0 auto;background:#162523;border-radius:16px;padding:32px 24px;text-align:center;">
      <h1 style="margin:0 0 8px;font-size:20px;">NightWatch Protocol</h1>

      <p style="margin:0 0 4px;font-size:14px;color:#a9c3c0;">Tu código para confirmar el cambio de correo:</p>
      <p style="margin:0 0 24px;font-size:12px;color:#a9c3c0;">Your code to confirm the email change:</p>
      <p style="margin:0 0 24px;font-size:36px;font-weight:bold;letter-spacing:8px;color:#ffffff;">{{ .Token }}</p>

      <p style="margin:0 0 4px;font-size:13px;color:#a9c3c0;">Este código vence en 10 minutos. Si no fuiste tú, ignora este correo.</p>
      <p style="margin:0;font-size:12px;color:#a9c3c0;">This code expires in 10 minutes. If this wasn't you, ignore this email.</p>
    </div>
  </body>
</html>
```

## 3. Reset password

**Subject:** `Recupera tu contraseña · Reset your password – NightWatch Protocol`

```html
<!doctype html>
<html>
  <body style="margin:0;padding:24px;background:#0f1d1c;font-family:Arial,Helvetica,sans-serif;color:#e8f1f0;">
    <div style="max-width:420px;margin:0 auto;background:#162523;border-radius:16px;padding:32px 24px;text-align:center;">
      <h1 style="margin:0 0 8px;font-size:20px;">NightWatch Protocol</h1>

      <p style="margin:0 0 4px;font-size:14px;color:#a9c3c0;">Tu código para recuperar la contraseña:</p>
      <p style="margin:0 0 24px;font-size:12px;color:#a9c3c0;">Your code to reset your password:</p>
      <p style="margin:0 0 24px;font-size:36px;font-weight:bold;letter-spacing:8px;color:#ffffff;">{{ .Token }}</p>

      <p style="margin:0 0 4px;font-size:13px;color:#a9c3c0;">Este código vence en 10 minutos. Si no fuiste tú, ignora este correo: tu contraseña no cambia.</p>
      <p style="margin:0;font-size:12px;color:#a9c3c0;">This code expires in 10 minutes. If this wasn't you, ignore this email: your password stays the same.</p>
    </div>
  </body>
</html>
```

---

## Para un tercer idioma

Agregar un tercer párrafo por bloque en las tres plantillas (el correo crece una línea por idioma) y una entrada en `TEXTS` de
`supabase/functions/send-recovery-email-code/index.ts`. Cuando haya muchos idiomas, conviene pasar a un correo por idioma enviado
desde una Edge Function (el servidor sí conoce el idioma que manda la app).
