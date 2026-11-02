# Etapa 4 · Tema — lista de verificación visual (emulador o teléfono)

Claude Code no ve el teléfono: **nada de esto se ha mirado en pantalla**. Lo comprueba el grupo con esta lista.
Rama `modulo-5-tema`. Spec: `2026-11-02-modulo-5-personalizacion-design.md` (secciones 3, 4 y 9).

## Qué cambia y qué no

- **Perfil y Ajustes** (y el código de verificación de correo cuando sirve al cambio de correo o al correo de recuperación) siguen el
  modo de Apariencia: Oscuro, Claro o Automático.
- **Todo lo demás** (Login, Registro, Quiz, Inicio, Ruta, Labs, Lección, Examen, Social, bloqueo biométrico, diálogo "Sesión cerrada")
  **queda forzado en oscuro** hasta la etapa 5, elija lo que se elija. Es a propósito: esas pantallas aún no tienen paleta clara.
- **El oscuro debe verse idéntico a v2.2.0.** Si ves una diferencia en oscuro, es un error de esta etapa: repórtala.

## Preparación

1. Instala `assembleDebug` de la rama. Inicia sesión.
2. Prueba con el sistema del teléfono en **oscuro** y otra vez en **claro** (Ajustes del sistema > Pantalla).
3. Fuente normal. Opcional: escala 1.3 en las pantallas largas (Editar perfil, Privacidad, Dispositivos).

## A. Apariencia (la pantalla nueva)

| Paso | Esperado |
|---|---|
| Menú de usuario > Apariencia, recién instalada | "Oscuro" marcada: ✓ y borde de 3 px dorado |
| Tocar "Claro" | La pantalla pasa a claro **al instante**, sin parpadeo ni reinicio; "Claro" lleva ✓ y borde; las otras dos sin ✓ |
| Tocar "Automático" con el sistema en oscuro / en claro | La pantalla sigue al sistema; "Automático" lleva ✓ y borde (**PROPUESTA**: el Figma no lo dibuja) |
| Volver al menú y a Inicio | Inicio, Ruta y Labs siguen **oscuros** (transición) |
| Cerrar la app del todo y abrirla | Conserva el modo; **sin destello oscuro > claro** al arrancar |
| Cerrar sesión y volver a entrar | Conserva el modo (es por dispositivo) |
| Cambiar el modo con un reto pendiente en Batallas, luego entrar a Batallas | El reto sigue (R-01: cambiar el modo no recrea la actividad) |

## B. Barras del sistema

En cada pantalla de Perfil y Ajustes en **claro**: iconos de la barra de estado y de navegación **oscuros** sobre fondo claro, aunque el
sistema esté en oscuro. En **oscuro**: iconos claros, aunque el sistema esté en claro. Al salir a Inicio (oscuro) vuelven a iconos claros.

## C. Pantalla por pantalla (Perfil y Ajustes)

Para cada fila: mirar en **oscuro** (debe ser igual que antes) y en **claro** (fondo `#AEF5D5`, texto `#1C2B29`, campos y tarjetas
de información `#F7F4EC`). Qué mirar: texto legible, nada cortado, bordes visibles, iconos y emoji legibles.

| Pantalla | Qué mirar en claro |
|---|---|
| `UserMenu` (Menú de usuario) | Píldora de cabecera blanca translúcida con nombre y racha legibles; secciones y filas `#D9D9D9`; "Cerrar sesión" con borde |
| `Profile` (Mi Perfil) | Banda superior `#F7F4EC` con "Mi perfil" y el nombre; cuerpo verde; tarjeta de datos `#F7F4EC` con divisores oscuros; botón "Editar perfil" blanco |
| `EditProfile` | Campos `#F7F4EC` con texto oscuro; selector de género (menú) y de fecha (diálogo) con colores del tema, no los de Material por defecto; errores con ⚠ y texto oscuro; "Guardar" blanco |
| `Achievements` | Tarjetas desbloqueadas `#D9D9D9`, bloqueadas atenuadas; círculo dorado `#926A1B` y círculo bloqueado; "Reintentar" legible |
| `NotificationSettings` | Interruptores: encendido `#926A1B`, apagado gris con borde `#597F82`, pulgar blanco con borde; nota inferior legible |
| `Appearance` | Ver sección A |
| `Language` | Sigue inerte (Español marcada; tocar English no hace nada). Mirar solo el aspecto |
| `PrivacySecurity` | Filas e interruptores; mensajes de error/éxito debajo de las filas legibles |
| `Help` y `About` | Filas; botón "Contactar a soporte" `#D9D9D9` (como dibuja el Figma, ver aprobación 3); texto de "Acerca de" legible |
| `ChangePassword` (y sus 2 errores) | Campos claros; lista de requisitos: cumplido en verde oscuro `#1B6B2A`, pendiente atenuado; "Guardar cambios" blanco |
| `RecoveryEmail` | Texto y campo claros; botón blanco |
| `Devices` | Tarjetas `#F7F4EC` con borde, nombre y estado legibles; botón "Cerrar todas las sesiones" |
| `ConfirmIdentity` | **Sin frame claro en el Figma**: paleta derivada. Revisar con ojo crítico |
| `ChangeEmail` | Campo y mensaje; si la cuenta es de Google, el aviso legible |
| `VerifyCode` desde "Cambiar correo" y "Correo de recuperación" | Barra del código `#F7F4EC` con borde `#597F82` (PROPUESTA); en error el borde es rojo `#B85947` y el mensaje lleva ⚠; "Reenviar" legible y atenuado durante la cuenta atrás |
| `VerifyCode` desde el **registro** | Debe verse **oscuro** siempre (pertenece al flujo de login) |
| `ProfileUpdated`, `EmailUpdated`, `PasswordUpdated`, `RecoveryEmailSaved`, `DeviceSignedOut` | Cabecera verde, **panel blanco** con ruedas dentadas al 25 %, título y mensaje oscuros legibles, botón `#D9D9D9` visible sobre el blanco |
| `PasswordReset` (tras recuperar contraseña desde el login) | Debe verse **oscuro** siempre |

## D. Diálogos, menús y hojas dentro de Perfil y Ajustes

- Selector de fecha de nacimiento (Editar perfil): contenedor, día seleccionado y botones con colores del tema claro.
- Menú de género: fondo claro y texto oscuro.
- Diálogo de código de 6 casillas (si lo ves desde el login): oscuro, como antes.
- No hay `Snackbar` ni hoja inferior dentro de Perfil y Ajustes.

## E. Lo que debe seguir igual (regresión)

Login, Registro, Quiz, Inicio, Ruta, Labs, Lección, Lab, Informe, Examen, Resultado, todo Social (incluida la arena) y el bloqueo
biométrico: **idénticos a v2.2.0**, también cuando Apariencia está en Claro. Botones y campos de texto compartidos (Login, Registro):
idénticos en oscuro.

## F. Qué anotar

Captura de cada pantalla en claro y oscuro, el modelo y la versión de Android, y cualquier texto con poco contraste, borde invisible o
componente que no cambie de color. Los contrastes ya conocidos del oscuro (texto blanco sobre campo gris 2.58:1, rojo de error 2.10:1,
verde de éxito 2.80:1 y otros de `ContrastTest`) **no** son errores nuevos: están documentados.
