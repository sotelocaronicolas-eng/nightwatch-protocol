# Módulo 5 · Referencia extraída del Figma (solo lectura)

Archivo: `wd3fQTzF5NqsfGfdR4I1lb` (NightWatch Protocol – copia), página 0:1, frames 412×917.
Extraído el 2026-11-02 con get_design_context / get_variable_defs / get_metadata.
**Regla: lo que NO está en este documento NO está definido en el Figma. No inventarlo; se marca como "propuesta" y la aprueba el grupo.**

## 1. Hechos clave
1. El Figma solo tiene modo claro para 23 frames (Perfil y Ajustes). El resto de la app NO tiene versión clara.
2. No existe un set de variables "light". El modo claro usa valores hex fijos, no variables.
3. No hay ningún texto en inglés en el Figma. Solo se dibujan dos idiomas: Español e English.
4. Los datos de ejemplo del Figma son de muestra (v1.4.0, "Intermedio", correos). No copiarlos como reales.

## 2. Inventario: 23 frames claros ↔ oscuros
| Pantalla | Claro | Oscuro |
|---|---|---|
| Menú de usuario | 2458:199 | 2068:30 |
| Ver perfil | 2458:264 | 2068:51 |
| Editar perfil | 2458:297 | 2106:30 |
| Logros e insignias | 2458:320 | 2287:100 |
| Notificaciones | 2458:349 | 2287:129 |
| Apariencia | 2458:383 | 2287:163 |
| Idioma | 2458:402 | 2287:181 |
| Privacidad y seguridad | 2458:414 | 2288:105 |
| Ayuda y soporte | 2458:453 | 2290:139 |
| Acerca de | 2458:476 | 2290:161 |
| Perfil actualizado | 2458:500 | 2087:104 |
| Confirmar cambio de correo | 2458:515 | 2106:50 |
| Cambiar contraseña | 2458:559 | 2289:107 |
| Correo de recuperación | 2458:574 | 2123:30 |
| Administrar dispositivos | 2458:586 | 2123:49 |
| Verificar código | 2458:604 | 2106:61 |
| Contraseña actualizada | 2458:624 | 2289:154 |
| Correo de recuperación guardado | 2458:642 | 2123:41 |
| Dispositivo desconectado | 2458:660 | 2123:65 |
| Correo actualizado | 2458:675 | 2106:82 |
| Código incorrecto | 2458:693 | 2106:71 |
| Cambiar contraseña (error actual) | 2472:243 | 2289:122 |
| Cambiar contraseña (error no coinciden) | 2472:259 | 2289:138 |

**Leídos el 2026-11-02 (ver §11):** 2458:515, 2458:624, 2458:642, 2458:675, 2472:259 y el componente ToggleSwitch 2286:106.

## 3. Pantallas SIN versión clara en el Figma (no inventar diseño)
Login y su familia (1:2, 97:293, 113:38, 9:27, 9:77, 3:5, 97:184, 106:16, 97:214, 10:68, 29:94, 72:94, 106:72, 110:332), Main Menu 49:15, quiz/nivelación (2017:*, 2051:*), Ruta 2266:72, Laboratorios 2271:84 y todos los LAB, Lección y Lección UPDATE (2294:*, 2621:*), Examen (2486:249, 2491:*, 2498:*), todo Social y Social UPDATE (2440:163 … 2653:701), Sistema de Diseño 2254:40.
→ Su versión clara es **propuesta derivada** de la paleta de los 23 frames y debe aprobarla el grupo.

## 4. Variables del Figma (sistema de diseño, oscuro)
| Variable | Valor |
|---|---|
| color/bg/base | #2c595d |
| color/bg/elevated | #3e6e71 |
| color/surface/button | #d9d9d9 |
| color/surface/button-muted | #a1a1a1 |
| color/border/accent | #597f82 |
| color/accent/gold | #e3b54f |
| color/text/on-dark | #ffffff |
| color/text/on-light | #162523 |
| radius/card 16 · radius/field 12 · radius/pill 100 · size/button-height 40 · size/listrow-height 60 |

Componentes: ListItemRow 2254:55, Button 2254:50 (variantes Primary, Secondary, Google), ToggleSwitch 2286:106 (imagen SVG On/Off, sin colores en código).

## 5. Valores del modo CLARO (hex fijos, leídos)
| Rol | Valor | Notas |
|---|---|---|
| Fondo de pantalla | #aef5d5 | todas las pantallas claras |
| Texto principal | #1c2b29 | (oscuro usa #fff / #162523) |
| Borde opción seleccionada | #b2842c 3px | oscuro: #e3b54f 3px |
| Check ✓ seleccionado | #1c2b29 | oscuro: dorado #e3b54f |
| Filas/tarjetas de opción | #d9d9d9 (surface/button), borde 2px #597f82, radio 16, sombra 0 4 4 rgba(0,0,0,.25) | |
| Tarjeta info y campos (Editar perfil, Correo recuperación, Verificar código) | fondo #f7f4ec, borde 2px #597f82, radio 12, sombra | |
| Campos Cambiar contraseña (frame 2458:559) | fondo #a1a1a1 (button-muted), borde 2px #597f82, radio 12 | |
| Campos Cambiar contraseña (frames de error 2472:243) | fondo #a9b8ae | **discrepancia** con 2458:559 |
| Borde de campo en error (contraseña) | #b85947 | texto error 12sp Medium #1c2b29 con "⚠" |
| Borde de código incorrecto | #ff2e16 | **otro rojo distinto** al de contraseña |
| Código: caja | fondo #f7f4ec, borde 2px #1c2b29 (normal) | dígitos Inclusive Sans SemiBold 25 |
| Divisores | #3f4a44 1px | |
| Botón primario | blanco #fff, radio 20, alto 47, ancho 299, texto #1c2b29 SemiBold 18, sombra | pantallas de confirmación |
| Botón "Guardar cambios" (Cambiar contraseña) | #d9d9d9 pill alto 48 ancho 372, texto 16 SemiBold | **inconsistente** con el blanco |
| Botón "Contactar a soporte" | #d9d9d9 pill 48 | |
| Banda cabecera Ver perfil | #f7f4ec con formas redondeadas | |
| Pill cabecera menú usuario | rgba(255,255,255,.6) radio 100 | |
| Insignia bloqueada | button-muted #a1a1a1 con opacidad 60% del frame; desbloqueada #d9d9d9 | tarjeta 116×130 radio 16 |
| Cabecera | título 17sp Inter SemiBold en (20,30); "X" 24sp Bold en (370.5,43); línea decorativa SVG en top 72 | SVG distinto por modo |
| Etiquetas de sección | 12sp SemiBold opacidad .7 | pie de versión 11sp opacidad .5 |

Confirmaciones (Perfil actualizado, Dispositivo desconectado, etc.): fondo del frame #aef5d5 pero un rectángulo **blanco 412×844 desde top 73** cubre casi toda la pantalla; ilustraciones decorativas al 25% de opacidad; icono central 195×195 en (108,202); título 22sp Bold en top 430; subtítulo 13–14sp Medium en top 465; botón en top 700. Verificar en pantalla si el área es blanca o verde.

## 6. Pantallas: estructura y textos (base en español para la traducción)
- **Apariencia**: subtítulo "Elige cómo se ve NightWatch Protocol en tu dispositivo."; tarjetas 372×68 (tops 253/335/417): 🌙 Oscuro "Fondo oscuro, ideal de noche"; ☀️ Claro "Fondo claro de alto contraste"; ⚙️ Automático "Sigue el ajuste del sistema". Seleccionada: ✓ + borde 3px. No está dibujado el estado "Automático seleccionado".
- **Idioma**: tarjetas 372×60 (tops 256/330): 🇪🇸 Español (✓), 🇺🇸 English. Sin descripciones.
- **Menú de usuario**: secciones CUENTA (Ver Perfil, Editar Perfil), PROGRESO (Logros e Insignias), PREFERENCIAS (Notificaciones, Apariencia, Idioma), PRIVACIDAD Y SEGURIDAD, SOPORTE (Ayuda y Soporte, Acerca de); botón "🚪 Cerrar Sesión" pill 48 borde #597f82; pie "Nightwatch Protocol · v1.4.0".
- **Ver perfil**: "Mi Perfil" 32sp Bold; Correo electrónico, Nivel actual, Racha activa, Miembro desde, Correo de recuperación; botón "EDITAR PERFIL".
- **Editar perfil**: Nombre completo, Correo electrónico, Nueva contraseña, Verificar contraseña, "Cambiar Avatar", botón "GUARDAR CAMBIOS".
- **Logros**: "3 de 9 insignias desbloqueadas"; 9 insignias (nombre 11sp Medium, ancho 104 centrado, emoji 24sp).
- **Notificaciones**: 5 filas con toggle: Recordatorio de lección diaria; Logros e insignias; Racha en riesgo; Nuevos laboratorios; Novedades de NightWatch.
- **Privacidad y seguridad**: Cambiar Contraseña, Correo de Recuperación, Administrar Dispositivos; Bloqueo Biométrico (toggle), Ocultar mi progreso público (toggle), Descargar mis datos.
- **Ayuda y soporte**: 4 FAQ + botón "Contactar a soporte".
- **Acerca de**: "Nightwatch Protocol" en Iceland 26sp, Versión, Términos y Condiciones, Política de Privacidad, Calificar la app, Reportar un problema, pie "Hecho con 🛡️ para futuros defensores de la ciberseguridad."
- **Cambiar contraseña**: etiquetas 13sp Regular; campos 330×47 (tops 326/410/494); "Guardar cambios" top 582.
- **Correo de recuperación**: título 22sp Bold top 158; campo en (41,341); botón "GUARDAR CORREO" top 700.
- **Verificar código / Código incorrecto**: "Verifica tu Correo", "Enviamos un código de 6 dígitos a …", 6 dígitos, "El código ingresado no es correcto.", botón "REINTENTAR", "Reenviar el código de 6 digitos".
- **Dispositivo desconectado**: "¡Sesión cerrada!", "Se cerró la sesión en ese dispositivo.", botón "VOLVER A DISPOSITIVOS".
- **Perfil actualizado**: "¡Perfil actualizado!", "Tus datos se guardaron correctamente.", botón "VOLVER A MI PERFIL".

## 7. Contrastes calculados (WCAG)
Claro: #1c2b29 sobre #aef5d5 = 11.79; sobre #d9d9d9 = 10.43; sobre #f7f4ec = 13.39; sobre #a9b8ae = 7.12; sobre blanco = 14.72. No texto: borde #b2842c sobre #d9d9d9 = 2.39 y sobre #aef5d5 = 2.70; borde #597f82 sobre #aef5d5 = 3.52; borde error #b85947 sobre #a9b8ae = 2.23.
Oscuro: blanco sobre #2c595d = 7.79; dorado #e3b54f sobre #2c595d = 4.08; #162523 sobre #d9d9d9 = 11.25; check dorado sobre tarjeta #d9d9d9 = 1.35 (casi invisible, la selección depende del borde); blanco sobre #3e6e71 = 5.72.
Insignia bloqueada: texto claro ≈ 7.78 (≈ 3.15 con la opacidad del frame); oscuro ≈ 3.76 (≈ 2.2 con opacidad).
Los bordes decorativos bajo 3:1 no son texto; los textos deben cumplir ≥ 4.5:1 (test de contraste).

## 8. Discrepancias a resolver con el grupo
1. Fondo de campos en Cambiar contraseña: #a1a1a1 (2458:559) vs #a9b8ae (frames de error).
2. Botón principal: blanco radio 20 (confirmaciones) vs #d9d9d9 pill (Guardar cambios, Contactar soporte).
3. Dos rojos de error: #b85947 (contraseña) vs #ff2e16 (código).
4. Color del ✓ distinto por modo (oscuro dorado, claro oscuro).
5. Rectángulo blanco 412×844 en las confirmaciones claras.
6. Datos de muestra obsoletos (v1.4.0, "Intermedio" vs niveles reales BAJO/MEDIO/ALTO).
7. Capitalización "Nightwatch Protocol" vs "NightWatch Protocol".
8. Contraste bajo en el Figma oscuro: blanco sobre #94A6A7 ≈ 2.5:1; dorado sobre #2C595D en 11sp ≈ 4.1:1.
9. Los assets decorativos (línea, ilustración) son distintos por modo; hay que exportar ambos.

## 9. NO definido en el Figma (no inventar)
Claro para login/registro/quiz/inicio/ruta/labs/lecciones/examen/social; nada en inglés; estado "Automático seleccionado"; estados disabled/pressed/focus; snackbars y diálogos; barra de estado y navegación (color e iconos); iconos de la barra inferior; splash claro; colores del toggle; tema para fuentes (tamaño de fuente fuera de alcance).

## 10. Instrucciones para Claude Code
- Llamar `create_session` con la fileKey antes de cualquier otra herramienta.
- Solo lectura: get_metadata, get_variable_defs, get_design_context (skillNames "resource:figma-design-to-code", excludeScreenshot true) y get_screenshot (enableBase64Response). Prohibido `use_figma`.
- Los URL de assets del MCP dan 403 por el proxy; no usar curl.
- Los 5 frames y el ToggleSwitch que estaban "no leídos" ya se leyeron (§11).

## 11. Frames leídos el 2026-11-02 (get_design_context y get_screenshot; solo lectura)
Todos: fondo de pantalla `#aef5d5`; texto `#1c2b29`; fuentes Inter (Semi Bold 16–18, Bold 22 y 24, Medium 13, Regular 13); cabecera con "X" 24sp Bold en (370.5, 43) y línea SVG en top 72/72.5/73.

| Frame | Hallazgos |
|---|---|
| **2458:515** Confirmar cambio de correo | Título "Confirmar Correo" 22sp Bold top 165; subtítulo 13sp Medium top 216 ("Escribe tu nuevo correo. Te enviaremos un código de verificación para confirmarlo."); etiqueta "Nuevo correo electrónico" 13sp Regular top 452; campo `#f7f4ec`, borde 2px `#597f82`, radio 12, 330×47 en (41,474), sombra 0 4 4 .25; **botón blanco** radio 20, 47×299 en top 700, texto "ENVIAR CÓDIGO" 18sp SemiBold `#1c2b29`; logo circular 50×50 en (36,9) y texto "Nightwatch Protocol" 16sp en (66,14); ilustración 207×207 en (102,245). El correo del campo es dato de muestra. **Sin rectángulo blanco de panel** (a diferencia de las confirmaciones) |
| **2458:624** Contraseña actualizada | **Rectángulo blanco 412×844 desde top 72** (verificado con captura: el área es blanca; solo la cabecera queda verde). Ruedas dentadas decorativas grises al 25 % (assets `fad44.png` y `2d7f8.png`); icono central 195×195 en (108,202) (círculo verde `#aef5d5` con ✓ blanco, anillo blanco con sombra); título 22sp Bold top 430, subtítulo 13sp Medium top 465; **botón "Listo" `#d9d9d9` pill** radio 100, 48×372 en top 700, texto 16sp SemiBold (**no blanco**); cabecera con título "Cambiar Contraseña" 17sp en (20,30) |
| **2458:642** Correo de recuperación guardado | Mismo patrón que 2458:624 (panel blanco 412×844 desde top 73) pero **botón blanco** radio 20, 47×299, "VOLVER A CUENTAS" 18sp; logo y "Nightwatch Protocol" arriba; título "¡Correo guardado!" y subtítulo "Tu correo de recuperación fue guardado con éxito." |
| **2458:675** Correo actualizado | Igual que 2458:642: panel blanco desde top 72, **botón blanco**, "VOLVER A EDITAR PERFIL"; título "¡Correo actualizado!"; subtítulo "Tu correo electrónico fue verificado y actualizado con éxito."; dos grupos de iconos 195×195 |
| **2472:259** Cambiar contraseña (error no coinciden) | Campos 330×47 en (20, 313/397/481) con fondo **`#a9b8ae`** (no `#a1a1a1`), borde 2px `#597f82` (variable border/accent) y radio 12 (variable radius/field); campo "Confirmar" con borde **`#b85947`**; mensaje "⚠ Las contraseñas no coinciden." 12sp Medium `#1c2b29` en top 531; **botón "Guardar cambios" blanco** pill 48×372 en top 579 (en 2458:559 era `#d9d9d9`); etiquetas 13sp Regular; marcadores "••••••••", "Mínimo 8 caracteres", "Repite la nueva contraseña" 14sp |
| **ToggleSwitch 2286:106** | Dos variantes `Style=On` (`2286:104`) y `Style=Off` (`2286:105`), 46×26, **cada una es una imagen SVG** (`e1833.svg` y `08320.svg`); el código de diseño no expone colores. Sin variante clara. Los colores del interruptor claro son **PROPUESTA** |

Aclaraciones a las discrepancias de §8: (2) el botón principal claro es **blanco en 4 de 5 frames leídos** (515, 642, 675, 2472:259) y gris solo en 2458:624 y 2458:559; (1) el fondo de campos de contraseña es `#a1a1a1` (2458:559), `#a9b8ae` (error actual y no coinciden); (5) el rectángulo blanco **es real**: ocupa todo el cuerpo de las confirmaciones con icono (2458:624, 642, 675, y presumiblemente 2458:500/660) y no aparece en las pantallas de formulario (2458:515).
Nuevos: dos SVG de línea de cabecera distintos en frames claros (`5f2d9` en 2458:624 y 2472:259; `1f95e` en 2458:515, 642 y 675): comprobar su color al exportar. Las ruedas dentadas y la ilustración central son las mismas imágenes en 2458:624, 642 y 675.
- Cualquier valor no listado aquí es propuesta y va marcado como tal en la spec.
