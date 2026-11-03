# Etapas 5 y 6 · Tema en toda la app — lista de verificación visual (emulador o teléfono)

Claude Code no ve el teléfono: **nada de esto se ha mirado en pantalla**. Lo comprueba el grupo con esta lista.
Rama `modulo-5-tema-completo`. Spec: `2026-11-02-modulo-5-personalizacion-design.md` (secciones 3, 4 y 9). Complementa la lista de
la etapa 4 (`etapa-4-verificacion-visual.md`, que cubre Perfil y Ajustes y Apariencia).

## Qué cambió

- **Toda la app** sigue ahora el modo de Apariencia (Oscuro, Claro, Automático). Ya no existe la transición que forzaba oscuro
  (`ThemedDestinations.kt` se borró). El diálogo "Sesión cerrada", los snackbars, el selector de fecha y el bloqueo biométrico
  heredan el tema raíz.
- **El oscuro debe verse idéntico a v2.2.0.** Si ves una diferencia en oscuro, es un error: repórtala con la pantalla.
- Valores claros de pantallas **sin frame en el Figma** son PROPUESTA (ver `modulo-5a-cierre.md`): el grupo los aprueba o los cambia.

## Cómo probar (cada pantalla, 4 combinaciones)

1. **Oscuro** y **Claro** (Apariencia). Además **Automático** con el sistema en oscuro y en claro (una pasada corta).
2. **Fuente normal** y **fuente grande** (Ajustes del sistema > Tamaño de fuente, escala 1.3 o más).
3. **Rotación** (vertical y horizontal) en las pantallas largas y en las que tienen teclado.
4. En cada pantalla mirar: texto legible, nada cortado ni solapado, bordes visibles, iconos y emoji visibles, barra de estado y de
   navegación con iconos del color contrario al fondo.

Marca cada casilla: ☐ oscuro normal · ☐ oscuro grande · ☐ claro normal · ☐ claro grande · ☐ rotación.

## A. Arranque y cambio de modo

| Paso | Esperado |
|---|---|
| Abrir la app con el modo Claro guardado | El fondo ya es claro desde el primer frame tras la pantalla de inicio (la pantalla de inicio del sistema sigue **oscura**: ver "Recursos" en `modulo-5a-cierre.md`) |
| Cambiar de Oscuro a Claro en Apariencia y volver a Inicio, Ruta, Labs, Social | Todo claro, sin parpadeo ni reinicio |
| Cambiar el modo **con un reto pendiente** en Batallas y entrar a la arena | El reto sigue; el sondeo no se corta (R-01) |
| Con Apariencia en **Automático**, estando en la arena, cambiar el tema del teléfono (panel rápido) | Con Automático, el cambio del tema del **sistema** recrea la actividad (la app no declara `configChanges`): comprobar que la arena sigue, el turno no se pierde y el sondeo continúa. Si falla, es el riesgo R-01 que la etapa 7 debe resolver; anótalo |
| Cerrar la app del todo y abrirla | Conserva el modo |

## B. Autenticación (G1)

| Pantalla | Qué mirar en claro |
|---|---|
| Login | Fondo claro, campos `#F7F4EC`, botón principal blanco con texto oscuro, botón de Google blanco, enlaces azul oscuro, error con ⚠ |
| Diálogo de error de login | Fondo y texto del diálogo legibles; botón "Aceptar" visible |
| Recuperar contraseña (diálogo) | Botón "Enviar" con fondo azul oscuro y texto blanco (en oscuro: azul claro con texto negro, como antes) |
| Código de la recuperación (`VerifyCode`) y nueva contraseña | Cajas de código con borde visible; requisitos cumplidos en verde oscuro |
| Registro | Banda superior `#F7F4EC` con título y subtítulo oscuros y la ✕ visible; campos y requisitos de contraseña |
| Código del registro (`VerifyCode` de alta) | Igual que el código de la recuperación (antes estaba forzado oscuro) |
| Contraseña actualizada / restablecida | Panel blanco con el ✓ y el texto legibles |

## C. Quiz de nivelación, Inicio y Ruta (G2)

| Pantalla | Qué mirar en claro |
|---|---|
| Quiz de nivelación (pregunta) | Opciones `#D9D9D9` con texto oscuro; la opción elegida con borde azul oscuro; barra de progreso |
| Resultado del quiz | Nivel y plan de estudio con tarjetas legibles; botón principal |
| Inicio | Barra superior con logo y título oscuro y línea inferior oscura; chapa del usuario `#F7F4EC`; tarjetas de lección; hoja inferior con borde `#597F82`; avisos (snackbar) |
| Barra inferior (Inicio, Ruta, Labs, Perfil, Social) | Etiquetas legibles; la pestaña activa en dorado oscuro; "Social" atenuada |
| Ruta de aprendizaje | **Camino punteado visible** (dorado oscuro `#926A1B`, antes `#E3B54F`); islas completadas con aro claro, disponibles con halo, bloqueadas; nombres bajo cada isla legibles; tarjeta de progreso |

## D. Aprendizaje (G3)

| Pantalla | Qué mirar en claro |
|---|---|
| Labs (lista) | Tarjetas `#F7F4EC`, círculo del icono, chip de dificultad, texto oscuro |
| Lab (terminal) | Salida y entrada de la terminal sobre `#F7F4EC` con texto oscuro y cursor visible; tarjetas de objetivos; línea inferior de la barra |
| Informe del lab (éxito y fallo) | Cajas de estadísticas `#F7F4EC`; copa/emoji visible; "Reintentar guardado" si falla |
| Lección | Círculo dorado con aro `#F7F4EC`; tarjetas de contenido; scroll completo |
| Intro del examen | Círculo dorado con aro; reglas legibles |
| Examen (pregunta) | Tarjetas de opción `#F7F4EC`, selección con borde; temporizador/progreso |
| Resultado del examen (aprobado y reprobado) | Cajas de estadísticas; emoji; mensajes |

## E. Social (G4)

| Pantalla | Qué mirar en claro |
|---|---|
| Batallas (menú) | Tarjetas, botones de partida rápida y reto |
| Búsqueda rápida | Círculo "?" con signo oscuro sobre `#A1A1A1`; "VS" dorado oscuro |
| Arena | Barra de vida (lado propio dorado oscuro, rival rojo oscuro `#A13A2A`, centro gris), borde de respuesta correcta/incorrecta, avatares con sombrero y varita (el sombrero es oscuro `#1C2B29`) |
| Resultado de batalla | Ganado/perdido con color de acento legible |
| Amigos, solicitudes, bloqueados, agregar amigo | Filas con avatar y texto legible; insignias de estado |
| Rachas de pareja y detalle de pareja | Tarjeta `#D9D9D9` (antes gris verdoso) con texto oscuro; tira de la semana |
| Perfil del amigo | Fondo elegido (`profile_bg_1`…`5`) con capa oscura suave: texto claro legible sobre cada imagen; tarjetas de estadísticas; botones fijos |
| Mi perfil social y Editar mi perfil | Ojos (Insignias, Estadísticas, Historial); selector de fondos (panel gris de 200 × 82 dp) y botón de cambiar fondo (círculo oscuro con lápiz) |
| Reportar y bloquear (diálogos) | Fondo y botones legibles; el sondeo de la arena sigue por debajo |
| Avatar con foto y sin foto | Emoji con círculo `#F7F4EC`; la foto no cambia |

## F. Global (G5)

| Elemento | Qué mirar en claro |
|---|---|
| Bloqueo biométrico (`AppLock`) | Fondo claro, logo, "Nightwatch Protocol" oscuro, botón "Desbloquear". Para probarlo: activar el bloqueo en Privacidad, enviar la app a segundo plano y volver |
| Diálogo del sistema de huella | Es del sistema; no cambia con la app |
| Diálogo "Sesión cerrada" (cerrar sesión desde otro dispositivo) | Diálogo claro con texto oscuro |
| Snackbars de Inicio ("Próximamente", "Completa la isla anterior") | Fondo oscuro con texto claro (invertido, es el comportamiento de Material) |
| Selector de fecha (Editar perfil) | Fondo y días legibles; el día elegido resaltado |
| Hojas inferiores (Inicio) | Borde visible, texto legible |

## G. Perfil y Ajustes (repaso, ya migrados en la etapa 4)

Pasada corta de todas las pantallas del menú de usuario en claro y oscuro, solo para confirmar que quitar la transición no cambió
nada: Menú de usuario, Mi perfil, Editar perfil, Cambiar correo, Privacidad, Cambiar contraseña, Correo de recuperación,
Dispositivos, Notificaciones, Apariencia, Idioma, Logros, Ayuda, Acerca de, y las pantallas de confirmación.

## H. Barras del sistema

En **todas** las pantallas: en claro, iconos de la barra de estado y de navegación **oscuros**; en oscuro, **claros**, aunque el sistema
del teléfono esté al revés. Sin salto al navegar entre pantallas.

## I. Qué reportar

Para cada problema: pantalla, modo (oscuro/claro), fuente (normal/grande), rotación, y una captura. Los de contraste dudoso en claro
anótalos aunque se vean "bien": el cálculo está en `ContrastTest`, pero lo que importa es cómo se lee en el teléfono.
