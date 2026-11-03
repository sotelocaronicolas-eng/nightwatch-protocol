# Etapa 7 · Base del idioma — lista de verificación (emulador o teléfono)

Claude Code no ve el teléfono ni un emulador: **nada de esto se ha mirado en pantalla**; las pruebas unitarias cubren la lógica
(estado, primer arranque, repositorio con fakes, la decisión de R-01 y guardas sobre el código y el XML), no la actividad real.
Rama `modulo-5-idioma-base`. Spec: `2026-11-02-modulo-5-personalizacion-design.md` (secciones 5, 7 y 13).

**Qué cambió y debe verse igual que en v2.3.0:** `MainActivity` ahora es `AppCompatActivity`, el tema base es
`Theme.AppCompat.Light.NoActionBar` (no DayNight) y hay `locales_config.xml` (es, en). **No hay `values-en`**: elegir English guarda el
idioma pero los textos siguen en español (esperado hasta la etapa 8). Todo lo demás (oscuro, claro, automático) debe ser idéntico.

Probar en **Android 13+** y en **Android 8 a 12** (minSdk 26) si hay emulador de ambos: la ruta de guardado del idioma es distinta.

## A. Arranque y primer frame

| Paso | Esperado |
|---|---|
| Instalar encima de v2.3.0 y abrir con tema **Oscuro** | Pantalla de inicio oscura → app oscura, sin destello claro |
| Abrir con tema **Claro** guardado | Mismo comportamiento que en 5A (inicio del sistema oscuro, luego claro desde el primer frame, sin parpadeo oscuro→claro) |
| Abrir con **Automático** y el sistema en oscuro / en claro | Sigue al sistema |
| Barras de estado y de navegación en oscuro y en claro | Iconos claros sobre oscuro, oscuros sobre claro (igual que 5A) |
| Rotar en Inicio, Ruta, Labs | No se pierde la pantalla ni el estado |
| Teléfono configurado en **inglés**, instalación limpia (borrar datos) | La app abre en **español** (A4) y, en Android 13+, Ajustes del teléfono > Idioma de la app muestra Español |
| Teléfono en español, instalación limpia | Abre en español, sin reinicio visible |
| Segundo arranque tras el primero | No repite el reinicio; mantiene el idioma elegido |

## B. Pantalla Idioma (Perfil > Ajustes > Idioma)

| Paso | Esperado |
|---|---|
| Abrir Idioma | ✓ y borde dorado en **Español** |
| Tocar **English** | La actividad se recrea **sin cerrarse la app**; vuelve a la pantalla Idioma con ✓ y borde en **English**; los textos siguen en español (no hay `values-en`) |
| Tocar **Español** | ✓ vuelve a Español |
| Cerrar la app del todo y abrirla | Conserva el idioma elegido (✓ correcto) |
| Android 13+: cambiar el idioma desde Ajustes del teléfono > Apps > NightWatch > Idioma y volver a la app | El ✓ de la pantalla Idioma refleja el cambio (se relee al volver) |
| Android 13+: ver las opciones del idioma de la app en Ajustes | Solo aparecen Español e Inglés |
| Cerrar sesión y volver a entrar | Se conservan idioma y tema |
| Tema claro y tema oscuro en la pantalla Idioma | ✓ y borde con los colores de 5A; textos legibles |

## C. Cambio de idioma y de tema sin perder estado (R-01)

| Paso | Esperado |
|---|---|
| Batallas: enviar un reto a un amigo, quedar en "esperando", rotar | El reto sigue pendiente (no se cancela) |
| Lo mismo cambiando el **tema del teléfono** con Apariencia en Automático | El reto sigue pendiente |
| Lo mismo cambiando el **idioma de la app desde Ajustes del teléfono** (Android 13+) | El reto sigue pendiente |
| Salir de Batallas con la flecha atrás | El reto **sí** se cancela (salida real) |
| Arena de batalla en curso con la pantalla visible: rotar; cambiar tema del sistema (Automático); cambiar el idioma desde Ajustes del teléfono | La batalla continúa, el turno y la pregunta no se pierden; el sondeo sigue (la batalla no se pierde por abandono) |
| **Hallazgo (a revisar):** "Buscando oponente" (partida rápida) y rotar | La búsqueda **reinicia el contador** de espera (sale de la cola y vuelve a entrar). No pierde la búsqueda, solo el tiempo acumulado. Anotar si se nota |
| Cambiar el tema en Apariencia (Oscuro↔Claro) en cualquier pantalla | **No** recrea la actividad (sin parpadeo, sin perder el scroll ni lo escrito) |
| Cambiar fuente del sistema a grande con un reto pendiente | El reto sigue |

## D. Biométrico y bloqueo

| Paso | Esperado |
|---|---|
| Activar bloqueo biométrico, ir al fondo y volver pasado el plazo | Pide huella; el diálogo del sistema funciona (la actividad ahora es AppCompat) |
| Con el aviso biométrico en pantalla, rotar | No aparece un segundo aviso ni se rompe (`isUnlockPromptShowing` es de la actividad: **hallazgo posible**, anotar qué pasa) |
| Desbloquear y cambiar tema/idioma | Sin volver a pedir huella (la recreación no cuenta como estar en segundo plano) |

## E. Notificaciones, Perfil y Ajustes

| Paso | Esperado |
|---|---|
| Recordatorios activados: llega la notificación de práctica diaria | Se ve igual que antes (en español; el idioma de las notificaciones es la etapa 10) |
| Perfil > Exportar mis datos, con idioma Español y con English | Funciona y el archivo sale en español (no hay `values-en`); en Android < 13 el exportador usa el idioma elegido, no el del teléfono |
| Recorrer Perfil, Ajustes (Apariencia, Idioma, Notificaciones, Privacidad, Ayuda, Acerca de) en oscuro y en claro | Idéntico a v2.3.0 |
| Selector de fecha de nacimiento, diálogos, snackbars | Con el tema elegido, sin colores raros del tema AppCompat |
| Permisos del sistema (notificaciones) | El diálogo del sistema aparece normal |

## F. Si algo falla

- Pantalla blanca o cierre al abrir: revisar `adb logcat` por `IllegalStateException` de tema ("You need to use a Theme.AppCompat theme").
- Destello claro u oscuro en el primer frame: es el color de ventana (`themes.xml` / `SystemChrome.kt`); anotar la combinación.
- La app se reinicia sola dos veces al primer arranque: anotar versión de Android (puede ser la fijación de español, A4).
