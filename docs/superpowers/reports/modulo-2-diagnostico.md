# Módulo 2: Perfil y Ajustes — diagnóstico final

Rama: `modulo-2-perfil-ajustes` (solo local, sin push; `main` sin cambios, sigue en `b17bc53`).
Spec: `docs/superpowers/specs/2026-10-09-profile-settings-design.md`.

**Qué se verificó y qué no.** Todo lo de este informe se verificó con compilación (`assembleDebug` y los fuentes de `androidTest`) y con pruebas unitarias en JVM con fakes. **Ninguna pantalla se vio en un dispositivo**: no se puede afirmar cómo se ve ni cómo responde el hardware (huella, selector de fotos, enlace de correo). Eso queda en la checklist de la sección 6.

## 1. Etapas

| Etapa | Estado | Commit |
|---|---|---|
| Previa: `MainActivity` a `FragmentActivity` + `androidx.biometric` 1.1.0 | Hecha (probada por ti en el celular) | `f86946e` |
| Previa: spec del módulo | Hecha | `3ff9c24` |
| 1. Cimientos, Menú de usuario y Mi Perfil | Hecha | `a809f3e` |
| 2. Editar Perfil, Perfil actualizado, avatar y Confirma tu identidad | Hecha | `3cb5fc9` |
| 3A. Privacidad y Seguridad + Cambiar contraseña | Hecha | `ff9ae51` |
| 3B. Bloqueo biométrico | Hecha | `b667e39` |
| 3C. Huella en "Confirma tu identidad" (P12) | **Saltada a propósito**: P12 no está aprobada | — |
| 4. Correo de recuperación + Descargar mis datos | Hecha | `980de66` |
| 5. Cambio de correo con enlace | Hecha | `ca3ccb5` |
| 6. Pantallas restantes | Hecha | `a586d1a` |
| 7. Cierre (este informe, comparación con Figma, revisión) | Hecha | (el commit de este informe) |

Pendiente: nada del plan, salvo 3C (no aprobada) y las pruebas en celular.

## 2. Archivos y pruebas

- **Creados:** 89 archivos respecto a `main` (54 de código en `src/main`, 26 de pruebas, 8 recursos —4 PNG de ilustraciones, los 2 engranajes, el sobre y el vector del check—, 1 spec). Total de la rama: 100 archivos, +7 813 líneas.
- **Modificados (11):** `app/build.gradle.kts`, `gradle/libs.versions.toml`, `MainActivity.kt`, `AuthError.kt`, `AuthErrorMapper.kt`, `AppContainer.kt`, `MainMenuScreen.kt`, `NightWatchDestinations.kt`, `NightWatchNavHost.kt`, `AuthErrorMapperTest.kt`, `FakeAuthRepository.kt`.
- **Sin tocar:** Login, Registro, Recuperar contraseña, Quiz, Resultado, `NightWatchTextField`, tema, manifest, `.idea`, `google-services.json`.
- **Pruebas unitarias: 240, 240 pasan, 0 fallan** (eran 80 antes del módulo: 160 nuevas). `assembleDebug` y la compilación de `androidTest` pasan.
- **Lint:** 2 errores y 34 avisos. Los 2 errores (`LocalContextGetResourceValueCall` en `LoginScreen.kt:93` y `RegisterScreen.kt:100`) son de módulos anteriores y no los toqué. De este módulo salen solo avisos menores (usar `androidx.exifinterface`, extensiones KTX, tamaño del vector del check).
- **Nota:** quedan 3 `assert()` sueltos que no son de este módulo (`RegisterViewModelTest` ×2 y `NightWatchButtonTest`); en el módulo nuevo se usó `assertTrue`/`assertFalse`.

## 3. Estado de las 23 pantallas de Figma

| # | Pantalla (nodo) | Estado |
|---|---|---|
| 1 | Menú de usuario (2068:30) | Funcional (pantalla completa, no panel lateral) |
| 2 | Mi Perfil (2068:51) | Funcional |
| 3 | Perfil actualizado (2087:104) | Funcional |
| 4 | Editar Perfil (2106:30) | Funcional |
| 5 | Confirmar cambio de correo (2106:50) | Funcional (adaptada a enlace) |
| 6 | Verificar código (2106:61) | Funcional (adaptada a enlace) |
| 7 | Código incorrecto (2106:71) | Funcional (es el estado de error de la anterior) |
| 8 | Correo actualizado (2106:82) | Funcional |
| 9 | Correo de recuperación (2123:30) | Funcional |
| 10 | Correo guardado (2123:41) | Funcional |
| 11 | Administrar dispositivos (2123:49) | Solo visual (datos fijos) |
| 12 | Dispositivo desconectado (2123:65) | Solo visual (no cierra ninguna sesión real) |
| 13 | Logros e insignias (2287:100) | Solo visual (3 de 9 fijo) |
| 14 | Notificaciones (2287:129) | Funcional: guarda los switches en el teléfono. **No envía notificaciones.** |
| 15 | Apariencia (2287:163) | Solo visual |
| 16 | Idioma (2287:181) | Solo visual |
| 17 | Privacidad y seguridad (2288:105) | Funcional: bloqueo biométrico, descargar datos y accesos. "Ocultar mi progreso público" solo guarda el switch. |
| 18 | Cambiar contraseña (2289:107) | Funcional |
| 19 | Error de contraseña actual (2289:122) | Funcional (estado de la anterior) |
| 20 | Error de no coinciden (2289:138) | Funcional (estado de la anterior) |
| 21 | Contraseña actualizada (2289:154) | Funcional |
| 22 | Ayuda y soporte (2290:139) | Solo visual (sin respuestas, ni destino de contacto) |
| 23 | Acerca de (2290:161) | Parcial: muestra la versión real; sus 4 filas no hacen nada |
| + | Confirma tu identidad (no está en Figma) | Funcional (solo contraseña) |

## 4. Decisiones por defecto y diferencias con Figma

**Decisiones del spec con su valor por defecto:** P5 menú de pantalla completa con "X"; P6 "Miembro desde" con la fecha real de creación; P7 el dispositivo no actual y "Cerrar sesión en todos" abren la pantalla estática; P8 switches por dispositivo; P9 exportar con el selector de archivos del sistema y un aviso breve; P10 la foto se guarda al elegirla; P11 enlace "Quitar foto" y texto rojo para errores del avatar; P13 re-bloqueo a los 30 s; P15 pantalla de bloqueo con botón "Desbloquear", textos del aviso del sistema y desactivado automático si desaparece la biometría.

**Decisiones mías no cubiertas por el spec (valor más conservador):**
- La foto no se muestra en "Perfil actualizado": el spec la listaba ahí, pero el frame de Figma tiene el círculo con check y ningún espacio para el avatar. Gana Figma.
- Editar Perfil sin ningún cambio igualmente muestra "Perfil actualizado".
- Si cambias nombre y contraseña y Firebase pide confirmar identidad, el nombre ya guardado no se reenvía al reintentar.
- Cambio de correo: se rechaza el mismo correo de la cuenta ("Ese ya es el correo de tu cuenta") y uno con formato inválido; "ya en uso" tiene su mensaje.
- Tras abrir el enlace, si la consulta falla por token vencido (riesgo R2) se muestra un mensaje propio: "No pudimos confirmar el cambio. Si ya abriste el enlace, cierra sesión e inicia con tu nuevo correo."
- Correo de recuperación: vacío o inválido da "Correo electrónico inválido"; no se compara con el correo principal; si no hay uno guardado, la fila de Privacidad dice "Agregar correo de recuperación" (Figma muestra un correo de ejemplo).
- Exportar datos: lo que no se pueda leer (sin red, sin quiz) se exporta como `null`/"Sin evaluar" en lugar de bloquear la exportación.
- El bloqueo biométrico también alterna al tocar toda la fila, no solo el switch.
- El re-bloqueo dibuja la pantalla de bloqueo **encima** del grafo de navegación ya compuesto (conserva dónde estabas); solo el arranque en frío no compone el grafo hasta desbloquear.
- Al cerrar sesión se vacía el avatar en memoria (el archivo local queda por uid).
- "Ayuda y soporte" y "Acerca de": filas y botón sin acción, porque Figma no define respuestas, enlaces ni dirección de soporte (no inventé ninguna).

**Diferencias visuales con Figma (aproximaciones):**
- Los formularios usan `NightWatchTextField` (regla del spec): altura de 56 dp en lugar de 47, texto en blanco, sin placeholder ("Mínimo 8 caracteres" va como texto de apoyo) y error con el rojo `#FF2E16` del proyecto en lugar de `#B85947`/`#D98C73`.
- Sin sombra en el círculo del check (el SVG tenía un filtro de sombra; el vector de Android no lo lleva).
- Las ilustraciones de Apariencia, Idioma, Dispositivos, sobre y engranajes son los PNG del Figma, colocados con dimensiones del frame pero anclados abajo/arriba según la pantalla (no a coordenadas absolutas).
- Las 5 pantallas de confirmación comparten un componente: el texto del mensaje usa 13 sp (14 sp en "Perfil actualizado", como en Figma) y los botones se anclan al fondo.
- Los separadores de la tarjeta de Mi Perfil y el panel superior se armaron con capas superpuestas; las curvas siguen las medidas del frame pero no se compararon en pantalla.
- Datos de ejemplo fijos tal como están en Figma: nombres de dispositivos ("iPhone de Nicolas", "Samsung S26 Ultra"), "Hace 2h", racha de 15 días.
- Las filas de Privacidad usan el componente común (margen de 16 dp en lugar de 14).

## 5. Pasos manuales pendientes y riesgos

**Manuales:**
1. **Reglas de Firestore:** ya las publicaste (bloque `profile` con `recoveryEmail`, `avatarBase64`, `updatedAt`). Si cambias el tamaño máximo del avatar, ajusta el límite de 150 000 caracteres.
2. **Firebase Auth:** el enlace de `verifyBeforeUpdateEmail` usa la plantilla de correo y el dominio autorizado del proyecto; revisa en Authentication > Plantillas que el envío de correos de Firebase esté activo (no lo pude comprobar desde aquí).
3. **Permisos:** `USE_BIOMETRIC` se fusiona solo desde la librería (verificado en el manifest fusionado). No hay permisos de almacenamiento ni cámara.
4. **Commit pendiente de tu parte:** nada; todo está commiteado en la rama. Falta decidir cuándo fusionar a `main`.

**Riesgos conocidos:**
- **R2 (enlace de correo):** al confirmarse el cambio, Firebase puede invalidar la sesión; probar en dispositivo real.
- **R16:** el aviso biométrico en Android 7–8 lo dibuja la librería con su propio diálogo; no probado.
- **R15:** si la biometría desaparece con el bloqueo activo, el bloqueo se apaga solo (decisión P15c). En Android 10 o menor no hay respaldo de PIN.
- **R6:** dispositivos, "cerrar sesión en todos", Apariencia e Idioma parecen reales y no lo son; Notificaciones guarda el switch pero no envía nada.
- **R7:** los switches son por dispositivo: otra persona que inicie sesión en el mismo teléfono los hereda.
- **Foto:** formatos que el sistema no decodifica dan "No pudimos leer esa imagen"; la foto vive como Base64 en el mismo documento que el correo de recuperación (~137 KB como máximo por lectura).
- **Lectura síncrona al arrancar:** `AppLockViewModel` lee la preferencia con `runBlocking` (igual que `NightWatchApplication` con la sesión) para no mostrar nada antes de decidir el bloqueo.
- **Pruebas de pantalla:** no hay pruebas instrumentadas nuevas de Compose; solo lógica en JVM.
- **P12 sin aprobar:** "Confirma tu identidad" pide siempre la contraseña; no hay `SecretVault`.

## 6. Checklist en el celular (por etapa)

**Etapa 1 — Menú y Mi Perfil**
- [ ] Main Menu muestra "Mi cuenta" y conserva "Cerrar sesión".
- [ ] "Mi cuenta" abre el Menú de usuario: encabezado y "X" fijos, lista con scroll, versión 1.4.0 al final.
- [ ] "Ver Perfil" muestra tu inicial, nombre, correo, nivel (o "Sin evaluar"), racha 15 y "Miembro desde".
- [ ] Cerrar sesión desde el Main Menu y desde el Menú de usuario lleva a Login; vuelve a entrar con otra cuenta y no ves la foto/nombre de la anterior.

**Etapa 2 — Editar Perfil, avatar, identidad**
- [ ] Cambiar el nombre y guardar → "Perfil actualizado" → "Volver a mi perfil" muestra el nombre nuevo (y el Menú también).
- [ ] Nueva contraseña débil, distinta de la verificación, y válida; con sesión vieja pide "Confirma tu identidad" y al confirmar guarda.
- [ ] Contraseña actual equivocada en "Confirma tu identidad" → "La contraseña actual no es correcta".
- [ ] "Cambiar Avatar" abre el selector de fotos; la foto aparece en Editar Perfil, Mi Perfil y el Menú.
- [ ] Foto de cámara (con rotación) sale derecha; cerrar y reabrir la app sin red conserva la foto.
- [ ] "Quitar foto" vuelve a la inicial. Cuenta de Google: ves el aviso en lugar de contraseña y correo.

**Etapa 3A — Privacidad y Cambiar contraseña**
- [ ] Contraseña actual mala, no coinciden, reglas incumplidas, éxito y volver a entrar con la nueva.
- [ ] "Ocultar mi progreso público" conserva su valor al reabrir.

**Etapa 3B — Bloqueo biométrico**
- [ ] Activar sin huella registrada: texto rojo y el switch queda apagado.
- [ ] Activar con huella: aviso de prueba, queda encendido; cancelar el aviso lo deja apagado con el texto rojo.
- [ ] Cerrar la app y abrirla: pide huella antes de mostrar nada; Atrás sale de la app.
- [ ] Segundo plano < 30 s no pide; ≥ 30 s sí. Abrir el selector de fotos no bloquea.
- [ ] Quitar todas las huellas del teléfono con el bloqueo activo: la app abre y el switch aparece apagado.

**Etapa 4 — Correo de recuperación y datos**
- [ ] Guardar un correo válido → "¡Correo guardado!" → aparece en Mi Perfil y en Privacidad.
- [ ] Correo inválido muestra el error; sin red muestra "No se pudo guardar el correo".
- [ ] "Descargar mis datos" abre el selector de archivos y el `.json` contiene tus datos.

**Etapa 5 — Cambio de correo**
- [ ] En Editar Perfil cambia el correo → "Confirmar Correo" → "ENVIAR ENLACE" → llega el correo.
- [ ] "YA VERIFIQUÉ MI CORREO" antes de abrir el enlace → "El enlace venció o no se ha verificado" + "REINTENTAR".
- [ ] Abrir el enlace y confirmar → "¡Correo actualizado!". Anota si la sesión se cierra (R2).
- [ ] "Reenviar el enlace" muestra "Te enviamos el enlace de nuevo."

**Etapa 6 — Pantallas restantes**
- [ ] Notificaciones: los 5 switches con sus valores iniciales y persistencia.
- [ ] Apariencia, Idioma, Logros, Ayuda, Acerca de (versión 1.4.0) se abren y cierran con la "X".
- [ ] Dispositivos: el segundo dispositivo y "CERRAR SESIÓN EN TODOS" abren "¡Sesión cerrada!" y vuelven.
