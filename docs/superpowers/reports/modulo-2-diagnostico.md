# Módulo 2: Perfil y Ajustes — diagnóstico final

> **Documento histórico.** Describe el proyecto cuando usaba Firebase (versión 1.x). La app hoy usa solo Supabase: ver
> [`migracion-supabase.md`](migracion-supabase.md).

Rama: `modulo-2-perfil-ajustes` (solo local, sin push; `main` sin cambios, sigue en `b17bc53`).
Spec: `docs/superpowers/specs/2026-10-09-profile-settings-design.md`.

**Qué se verificó y qué no.** Todo lo de este informe se verificó con compilación (`assembleDebug` y los fuentes de `androidTest`) y con pruebas unitarias en JVM con fakes. **Ninguna pantalla se vio en un dispositivo**: no se puede afirmar cómo se ve ni cómo responde el hardware (huella, selector de fotos, enlace de correo). Eso queda en la checklist de la sección 6.

> **Actualización (Etapas 8 y 9):** Notificaciones y Dispositivos pasaron de solo visuales a funcionales. Ver las secciones marcadas con 8 y 9 y el bloque de reglas nuevo en la sección 7.

> **Aviso: los hashes de los commits cambiaron.** Para poder subir el repositorio se reescribió el historial con
> `git filter-repo` y se quitaron de todos los commits y ramas `app/google-services.json` y `.idea/`, que estaban
> versionados desde el inicio. El contenido, los mensajes, el orden y la cantidad de commits no cambiaron, solo los
> hashes. **Los hashes que aparecen más abajo en este informe son los anteriores**; esta es la equivalencia
> (hash anterior → hash nuevo):
>
> | Commit | Anterior | Nuevo |
> |---|---|---|
> | `MainActivity` pasa a `FragmentActivity` y `androidx.biometric` | `f86946e` | `e21fd8b` |
> | Spec del Módulo 2 | `3ff9c24` | `4f1ca78` |
> | Etapa 1: cimientos, Menú de usuario y Mi Perfil | `a809f3e` | `4dc4752` |
> | Etapa 2: Editar Perfil, avatar y Confirma tu identidad | `3cb5fc9` | `1d78bea` |
> | Etapa 3A: Privacidad y Cambiar contraseña | `ff9ae51` | `47de98f` |
> | Etapa 3B: bloqueo biométrico | `b667e39` | `36c3279` |
> | Etapa 4: correo de recuperación y datos | `980de66` | `5e185e0` |
> | Etapa 5: cambio de correo con enlace | `ca3ccb5` | `a5be8d3` |
> | Etapa 6: pantallas restantes | `a586d1a` | `b50ef42` |
> | Etapa 7: informe de cierre | `ee278dc` | `4400e57` |
> | Etapa 8: notificaciones | `5f50a3f` | `8c757aa` |
> | Etapa 9: dispositivos | `66fec10` | `e5f85d5` |
> | Corrección de comillas del bloque de reglas en el informe | `2c8b116` | `06b3b8d` |
> | Etapa 10: Idioma y Apariencia | `95ca8a1` | `ba070a7` |
> | Etapa 11: correo de recuperación (causa real) | `f5fb23c` | `6b6b6d6` |
> | Etapa 12: Cambiar contraseña | `7f94f41` | `4f29950` |
> | Etapa 13: género y fecha de nacimiento | `8c8907b` | `d6b1933` |
> | Informe con las etapas 10 a 13 | `449e152` | `cccb1ea` |
> | Ajuste: Mi Perfil, filas de correo | `535544c` | `6a7c796` |
> | Ajuste: Idioma y Apariencia centradas | `6ce0816` | `e615e73` |
> | Informe con los ajustes de Mi Perfil e Idioma/Apariencia | `f240667` | `98497a3` |
>
> Commits nuevos posteriores a la limpieza: `329a2c3` (`.gitignore`), `1ad5cb2` (`firestore.rules`) y `996f50e` (`README.md`).
> En los commits anteriores a `98497a3` que no están en la tabla (los del Módulo 1) también cambió el hash.

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
| 7. Cierre (informe inicial, comparación con Figma, revisión) | Hecha | `ee278dc` |
| 8. Notificaciones funcionales (permiso, canales, recordatorios locales con WorkManager) | Hecha | `5f50a3f` |
| 9. Dispositivos funcionales (registro en Firestore, modo cooperativo) | Hecha | `66fec10` |
| 10. Idioma y Apariencia: distribución del espacio | Hecha (medidas de Figma; sin ver en celular) | `95ca8a1` |
| 11. Correo de recuperación no se guardaba (causa real corregida) | Hecha | `f5fb23c` |
| 12. Cambiar contraseña: alineación y estándares del registro | Hecha | `7f94f41` |
| 13. Género y fecha de nacimiento en el perfil | Hecha (reglas ya publicadas por ti) | `8c8907b` |
| Ajuste: Mi Perfil, filas de correo con la tipografía de las demás | Hecho (sin ver en celular) | `535544c` |
| Ajuste: Idioma y Apariencia centradas verticalmente | Hecho (sin ver en celular) | `6ce0816` |

Pendiente: nada del plan, salvo 3C (no aprobada), publicar las reglas de `devices` (sección 7) y las pruebas en celular.

## 2. Archivos y pruebas

- **Creados:** 124 archivos respecto a `main` (74 de código en `src/main`, 38 de pruebas unitarias, 1 prueba instrumentada, 9 recursos, el spec y este informe). Total de la rama: 137 archivos, +11 588 líneas.
- **Modificados (13):** `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml` (solo `POST_NOTIFICATIONS`, Etapa 8), `NightWatchApplication.kt` (canales y sincronización de recordatorios, Etapa 8), `MainActivity.kt`, `AuthError.kt`, `AuthErrorMapper.kt`, `AppContainer.kt`, `MainMenuScreen.kt`, `NightWatchDestinations.kt`, `NightWatchNavHost.kt`, `AuthErrorMapperTest.kt`, `FakeAuthRepository.kt`.
- **Sin tocar:** Login, Registro, Recuperar contraseña, Quiz, Resultado, `NightWatchTextField`, tema, `.idea`, `google-services.json`.
- **Pruebas unitarias: 373, 373 pasan, 0 fallan** (eran 80 antes del módulo: 293 nuevas; 22 de la Etapa 8, 40 de la 9, 14 de la 11, 10 de la 12 y 47 de la 13). `assembleDebug` y la compilación de `androidTest` pasan. Hay una prueba instrumentada nueva (Etapa 10) que **solo compila**: no se ejecutó porque no hay dispositivo.
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
| 11 | Administrar dispositivos (2123:49) | **Funcional (Etapa 9, modo cooperativo)**: lista los dispositivos reales de Firestore; no revoca tokens de Firebase |
| 12 | Dispositivo desconectado (2123:65) | **Funcional (Etapa 9)**: marca `revoked=true`; el texto aclara que no revoca Firebase |
| 13 | Logros e insignias (2287:100) | Solo visual (3 de 9 fijo) |
| 14 | Notificaciones (2287:129) | **Funcional (Etapa 8)**: permiso, canales y recordatorios locales reales para *lección diaria* y *racha en riesgo*. Logros, laboratorios y novedades **solo guardan el switch** (necesitan backend). |
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

**Decisiones de las Etapas 8 y 9 (valor más conservador):**
- Recordatorios: lección diaria a las 7:00 p.m. (como dice Figma) y racha en riesgo a las 9:00 p.m. (hora elegida por mí). Se piden permisos solo al activar uno de esos dos interruptores; los otros tres no piden permiso porque no pueden mostrar nada local.
- Si el permiso se retira en Ajustes del teléfono, al volver a la pantalla los recordatorios activos pasan a apagado y se cancelan; al abrir la app también se cancelan los trabajos sin permiso.
- El recordatorio no se muestra si no hay sesión iniciada.
- Dispositivos: el nombre es "Fabricante Modelo" (hasta 80 caracteres). "Cerrar sesión en todos" marca todos los demás; si no hay otros muestra un aviso. Un dispositivo ya marcado muestra "Cierre pendiente" y no se puede volver a tocar.
- Al cerrar sesión por propia mano, el dispositivo borra su documento (con un límite de 2 s si no hay red) antes de salir; al ser revocado también lo borra, para poder registrarse limpio en el siguiente inicio de sesión.
- La revisión de revocación corre al abrir la app y al volver a primer plano, como mucho una vez cada 30 s, y al abrir el Menú principal y la pantalla Dispositivos. Si Firestore no responde, la sesión se deja como está (no se cierra por error).
- "Datos locales" que se limpian al ser revocado: foto de perfil (archivo local y memoria). Los switches y el identificador del dispositivo se conservan; los recordatorios no se muestran sin sesión.
- La fila "Administrar Dispositivos" de Privacidad ahora cuenta dispositivos reales sin cierre pendiente ("Administra tus sesiones" si no se puede leer).

**Etapa 10 — Idioma y Apariencia (comparé las capturas de Figma 2287:163 y 2287:181 con mis medidas; no vi la app).**
- Figma deja ~140 dp entre el encabezado y la primera opción (y ~180 dp en Idioma); a propósito se acercó a 32 dp, como pediste. El resto sale del diseño: ilustraciones de 412×308 (Apariencia, 577×432 sin recorte) y 412×287 (Idioma, 677×369 dibujada con "cover", o sea recortada a los lados), centradas, con la opacidad del frame (0,8 y 0,7).
- La ilustración dejó de estar anclada al fondo: va **en flujo** justo debajo de las opciones, a ancho completo y con la proporción de su frame. Todo hace scroll junto (pantallas pequeñas y fuente grande).
- "Elige cómo se ve…" conserva el margen de Figma (8 dp dentro del borde de las tarjetas, igual a 28 dp desde el borde); el resto usa los 20 dp laterales y los 14 dp entre elementos del andamiaje común.
- No verificado: cómo queda en tu pantalla real (en teléfonos más altos que el frame de 917 dp queda espacio libre debajo de la ilustración).

**Etapa 11 — Causa real del error "No se pudo guardar el correo".**
- Las escrituras de Firestore (`set`, `update`, `delete`) devuelven `Task<Void>`; su `await()` devuelve **`null` cuando sale bien**. El repositorio escribía `withTimeoutOrNull(5 s) { …set().await() } ?: throw …`, y `withTimeoutOrNull` también devuelve `null` al agotarse el tiempo: no se podían distinguir, así que **siempre lanzaba** aunque Firestore ya hubiera guardado el dato.
- Descartado con el código: el mapa de campos (`recoveryEmail` + `updatedAt`) cumple las reglas publicadas (`hasOnly`, string ≤ 254); el documento `profile/main` existente no aporta campos fuera de la lista; la ViewModel no reautentica ni traga otra excepción distinta; el timeout no era el problema sino la forma de leer el resultado. No había `adb` (no se pudo mirar logcat), por eso se reprodujo con prueba: `aCallThatSucceedsWithANullResult_isNotATimeout`.
- Corrección: `firestoreCall` (usa `withTimeout`, clasifica el fallo en *sin conexión*, *permiso denegado* o *desconocido*). Se aplicó a perfil y dispositivos. Efectos colaterales corregidos: el aviso "Quedó guardada en este teléfono" del avatar salía siempre, y "Cerrar sesión en otro dispositivo" siempre mostraba error aunque la marca se escribía.
- **Las reglas de Firestore NO eran la causa y no cambiaron en esta etapa.**
- Mensajes: sin conexión → "Sin conexión a internet. Revisa tu conexión e inténtalo de nuevo."; permiso denegado → "No pudimos guardar el correo por un problema de permisos de tu cuenta. Inténtalo más tarde."; otro → "No se pudo guardar el correo. Inténtalo de nuevo." Nunca se muestra el detalle técnico.
- No verificado en celular: que el guardado ya funcione de punta a punta (la causa está demostrada por código y pruebas, no por una ejecución real).

**Etapa 12 — Cambiar contraseña.**
- Campos, requisitos y botón comparten el mismo ancho y los 20 dp laterales del botón (antes los campos medían 330 dp máx.). Esto difiere de Editar Perfil, que usa 41 dp con un botón de 299 dp: allí el botón y los campos siguen juntos.
- La brecha de 200 dp bajo el encabezado (Figma) se redujo a 32 dp para que entren los requisitos.
- Se reutiliza `PasswordRules.from` del registro sin tocarlo. Los **textos** de los tres requisitos ("Mínimo 8 caracteres", "Incluye una mayúscula y un número", "Incluye al menos un carácter especial") se repiten en `passwordRequirements` porque el registro los tiene dentro de una pantalla; una prueba comprueba que siguen el orden y el valor de cada regla.
- Validación en vivo: requisitos con marca verde, "igual a la actual" y "no coincide". El botón solo se habilita si hay contraseña actual, se cumplen las 3 reglas, la nueva es distinta de la actual y la confirmación coincide.

**Etapa 13 — Género y fecha de nacimiento.**
- Campos en Editar Perfil (después de "Verificar contraseña", para no mover los campos del Figma) y en Mi Perfil (después de "Miembro desde"). Para quitar el género hay una opción extra "Sin definir" en el menú y para la fecha un enlace "Quitar fecha"; quitarlos **borra el campo** en Firestore.
- Claves guardadas: `gender` = `male` | `female` | `other` | `prefer_not_to_say` (no la etiqueta) y `birthDate` = `yyyy-MM-dd`. Se muestran como "Masculino…" y `dd/MM/yyyy`.
- Validación propia sin `java.time` (`minSdk` 24 y no hay desugaring): fecha real, no futura (hoy sí vale), no anterior a hoy menos 120 años (29 feb cae en 28 feb si el año no es bisiesto). El selector solo ofrece fechas válidas y el ViewModel las vuelve a comprobar.
- Caché local en DataStore por usuario; Firestore se consulta con timeout de 5 s. Si la escritura remota falla, el dato queda en el teléfono y el flujo continúa a "Perfil actualizado" (Firestore deja la escritura en cola); no hay mensaje de aviso para este caso.
- "Descargar mis datos" incluye `genero` (etiqueta) y `fechaDeNacimiento` (ISO), o `null`.
- Son datos personales: no se escriben en logs ni en mensajes de error (no hay ningún log nuevo en el módulo).
- Correos largos en Mi Perfil: los valores de más de 22 caracteres pasan a una línea propia debajo de su etiqueta, en lugar de cortarse con "…". Desvío frente a Figma, que solo trae ejemplos cortos. No verificado en pantalla real.

**Ajustes visuales posteriores (sin cambios en datos ni reglas).**
- **Mi Perfil, filas de correo (`535544c`):** los valores de "Correo electrónico" y "Correo de recuperación" usan la misma tipografía que todos los demás (13 sp, semibold, negro, un único `InfoValueText`); no hay autoajuste ni reducción. Esas dos filas llevan la etiqueta arriba y el valor debajo, alineado a la izquierda y a todo el ancho de la tarjeta, con el mismo padding y los mismos separadores. Si no cabe en una línea pasa a una segunda (máximo 2) y solo entonces termina con "…". Sin valor, "—" se dibuja como en las demás filas vacías (fila en línea, alineada a la derecha). Las filas crecen con la fuente del sistema (`heightIn(min)` en lugar de alto fijo).
  - **Figma lo resuelve distinto:** en el frame original (2068:51) los dos correos van en línea con su etiqueta, alineados a la derecha, y en **10 sp** frente a 13 sp de las demás filas (es decir, el propio diseño reduce la letra; ahí solo caben correos cortos). Se cambió a propósito por lo que pediste.
  - No verificado: cómo se ve en tu celular, con fuente grande ni en pantallas angostas. No hay prueba de UI de esta tarjeta (la tarjeta es privada y no existe prueba de formateadores que dependa de ella); las 373 pruebas siguen pasando.
- **Idioma y Apariencia (`6ce0816`):** el andamiaje común (`SettingsScaffold`) tiene una opción `centerVertically`: el bloque completo (opciones + ilustración) se centra en el espacio bajo el encabezado, con 24 dp arriba y abajo para que el centrado sea parejo; si no cabe (pantalla pequeña o fuente grande) empieza arriba y hace scroll. **Apariencia tenía el mismo hueco** (~200 dp libres en el frame de 917 dp) y recibió el mismo criterio para que ambas queden consistentes. Las demás pantallas que usan el andamiaje no se centran (comportamiento anterior). Sigue sin haber lógica de idioma ni de tema. La prueba instrumentada de estas pantallas sigue compilando (no se ejecuta sin dispositivo).
  - Cambio deliberado frente a Figma: allí las opciones quedan ~140 dp (Apariencia) y ~180 dp (Idioma) bajo el encabezado y el espacio sobrante queda abajo.
  - No verificado: el resultado real en el celular (cuánto espacio sobra depende de la altura de tu pantalla).

## 5. Pasos manuales pendientes y riesgos

**Manuales:**
1. **Reglas de Firestore (nuevas, Etapa 13): publícalas ANTES de probar género y fecha.** Sin ellas, guardar esos campos falla en el servidor (el dato queda en el teléfono pero no sube). Archivo completo en la sección 7.
1. **Reglas de `devices` (Etapa 9):** ya están incluidas en el archivo completo de la sección 7; sin ellas la lista falla con "permiso denegado" y la app lo tolera (muestra el error de carga).
1. **Reglas de Firestore (anteriores):** ya las publicaste (bloque `profile` con `recoveryEmail`, `avatarBase64`, `updatedAt`). Si cambias el tamaño máximo del avatar, ajusta el límite de 150 000 caracteres.
2. **Firebase Auth:** el enlace de `verifyBeforeUpdateEmail` usa la plantilla de correo y el dominio autorizado del proyecto; revisa en Authentication > Plantillas que el envío de correos de Firebase esté activo (no lo pude comprobar desde aquí).
3. **Permisos:** `USE_BIOMETRIC` se fusiona solo desde la librería (verificado en el manifest fusionado). No hay permisos de almacenamiento ni cámara.
4. **Commit pendiente de tu parte:** nada; todo está commiteado en la rama. Falta decidir cuándo fusionar a `main`.

**Riesgos conocidos:**
- **R2 (enlace de correo):** al confirmarse el cambio, Firebase puede invalidar la sesión; probar en dispositivo real.
- **R16:** el aviso biométrico en Android 7–8 lo dibuja la librería con su propio diálogo; no probado.
- **R15:** si la biometría desaparece con el bloqueo activo, el bloqueo se apaga solo (decisión P15c). En Android 10 o menor no hay respaldo de PIN.
- **R6 (actualizado):** Apariencia e Idioma siguen siendo solo visuales. Notificaciones de logros, laboratorios y novedades solo guardan el switch (la pantalla lo dice en una nota).
- **Dispositivos (Etapa 9) es cooperativo:** "Cerrar sesión" en otro dispositivo solo marca `revoked=true`; la otra app lo honra la próxima vez que se abre. **No revoca tokens de Firebase** (limitación del cliente): una app modificada, o un teléfono que no abra NightWatch, mantiene la sesión. Las reglas dejan que el dueño escriba cualquier dispositivo suyo, así que un cliente puede quitarse su propia marca.
- **Dispositivos sin conexión:** si el dispositivo revocado está offline, no se entera hasta volver a abrir la app con red. Si el borrado de su documento falla, al volver a iniciar sesión se cierra una vez más y se limpia en el siguiente intento.
- **Recordatorios (Etapa 8):** Android puede retrasar unos minutos un trabajo periódico (ahorro de batería). La racha es un dato fijo (15), así que "racha en riesgo" es un aviso diario genérico a las 9:00 p.m., no un cálculo real. En Android 12 o menor no hay diálogo de permiso: si bloqueaste las notificaciones en Ajustes, el interruptor muestra el mensaje.
- **R7:** los switches son por dispositivo: otra persona que inicie sesión en el mismo teléfono los hereda.
- **Foto:** formatos que el sistema no decodifica dan "No pudimos leer esa imagen"; la foto vive como Base64 en el mismo documento que el correo de recuperación (~137 KB como máximo por lectura).
- **Lectura síncrona al arrancar:** `AppLockViewModel` lee la preferencia con `runBlocking` (igual que `NightWatchApplication` con la sesión) para no mostrar nada antes de decidir el bloqueo.
- **Pruebas de pantalla:** no hay pruebas instrumentadas nuevas de Compose; solo lógica en JVM.
- **P12 sin aprobar:** "Confirma tu identidad" pide siempre la contraseña; no hay `SecretVault`.
- **Datos personales (Etapa 13):** género y fecha de nacimiento viven en Firestore y en una copia local sin cifrar (DataStore, como el resto de preferencias). Quien tenga el teléfono desbloqueado y root podría leerlos. No se registran en logs.
- **Dos copias que pueden diferir:** si dos dispositivos cambian estos campos sin conexión, gana la última escritura que llegue a Firestore.
- **Reglas más estrictas que la app:** la regla pide `gender` dentro de la lista y `birthDate` con forma `yyyy-MM-dd`; **no** valida que la fecha sea real ni pasada (eso lo hace la app). Un cliente modificado podría guardar una fecha imposible.
- **Layout sin ver (Etapas 10, 12, 13):** el espaciado, el alineado y el menú/selector de fecha compilan y siguen las medidas de Figma, pero no se vieron en un dispositivo. Hay una prueba instrumentada para Idioma/Apariencia que solo compila.
- **No se corrigió** el mismo patrón `withTimeoutOrNull(...) ?: throw` en código de otros módulos porque no hay ninguno: `FirestoreQuizResultRepository` hace `await()` sin esa construcción.

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

**Etapa 8 — Notificaciones**
- [ ] Android 13+: con el recordatorio apagado, actívalo → aparece el diálogo del sistema. Permitir lo deja encendido; Denegar lo deja apagado con el mensaje rojo.
- [ ] Apagar un recordatorio cancela el aviso (mira en Ajustes > Aplicaciones > Notificaciones que sigan los canales "Recordatorio de lección diaria" y "Racha en riesgo").
- [ ] Quita el permiso en Ajustes del teléfono y vuelve a la pantalla: los dos recordatorios quedan apagados.
- [ ] Para probar el aviso sin esperar a las 7:00/9:00 p.m., cambia la hora del teléfono o usa `adb shell cmd jobscheduler` / las herramientas de WorkManager de Android Studio (App Inspection > Background Task Inspector).
- [ ] Sin sesión iniciada no debe llegar ninguna notificación.
- [ ] Los switches de logros, laboratorios y novedades se activan sin diálogo y la nota inferior lo explica.

**Etapa 9 — Dispositivos (necesitas dos teléfonos o un teléfono y un emulador con la misma cuenta)**
- [ ] Tras publicar las reglas, abre la app en el teléfono A y en el B; en A > Administrar Dispositivos ves A ("Actual") y B con "Hace N min".
- [ ] Toca B en A → "¡Sesión cerrada!" (con la aclaración de que no revoca Firebase) y B aparece como "Cierre pendiente".
- [ ] En B, ciérrala y vuelve a abrir (o llévala a segundo plano > 30 s y vuelve): sale sola a Login con el aviso "Se cerró tu sesión porque lo pediste desde otro dispositivo." y la foto local desaparece.
- [ ] Vuelve a iniciar sesión en B: aparece de nuevo en la lista como dispositivo activo.
- [ ] "CERRAR SESIÓN EN TODOS" marca los demás y nunca el actual; con un solo dispositivo muestra el aviso.
- [ ] Sin red: la lista muestra el error de carga y la sesión no se cierra.
- [ ] "Cerrar sesión" desde el Menú o desde el Main Menu quita ese dispositivo de la lista.

**Etapa 10 — Idioma y Apariencia**
- [ ] Apariencia: debajo del encabezado aparece "Elige cómo se ve…" y las 3 tarjetas más cerca que antes; la ilustración queda justo debajo de las tarjetas, centrada y a todo el ancho.
- [ ] Idioma: las 2 tarjetas cerca del encabezado y el globo debajo.
- [ ] Gira el teléfono o sube el tamaño de fuente: todo hace scroll y no se corta nada.
- [ ] Tocar "Claro", "Automático" o "English" no hace nada (la app sigue oscura y en español).

**Etapa 11 — Correo de recuperación**
- [ ] Guarda un correo válido: ahora debe salir "¡Correo guardado!" y verse en Mi Perfil y en Privacidad.
- [ ] Pon el teléfono en modo avión y guarda otro: debe decir "Sin conexión a internet…".
- [ ] Cambiar la foto: ya no debe salir "Quedó guardada en este teléfono" cuando hay conexión.
- [ ] Dispositivos: "Cerrar sesión" en otro dispositivo ya no debe mostrar error.

**Etapa 12 — Cambiar contraseña**
- [ ] Los tres campos y el botón miden lo mismo y están alineados con los mismos márgenes.
- [ ] Escribe una nueva débil: los requisitos se ponen verdes uno a uno y el botón sigue apagado.
- [ ] Repite la contraseña actual como nueva: aviso "no puede ser igual a la actual" y botón apagado.
- [ ] Confirmación distinta: aviso en vivo; coincidente + válida: botón activo y guarda.
- [ ] Contraseña actual incorrecta y modo avión mantienen sus mensajes.

**Etapa 13 — Género y fecha de nacimiento** (publica antes las reglas de la sección 7)
- [ ] Mi Perfil muestra "Género" y "Fecha de nacimiento" con "—" mientras no estén definidos; el correo largo ya no se corta con "…".
- [ ] "Correo de recuperación" tiene su tilde en Mi Perfil.
- [ ] Editar Perfil: el menú de género ofrece Sin definir, Masculino, Femenino, Otro y Prefiero no decir; el selector de fecha no deja elegir días futuros ni años anteriores a hace 120 años.
- [ ] Guarda ambos, vuelve a Mi Perfil y verifica `dd/MM/yyyy`; ciérrala y ábrela sin red: se ven igual.
- [ ] "Quitar fecha" y "Sin definir" los devuelven a "—" y el campo desaparece del documento en la consola de Firestore.
- [ ] "Descargar mis datos" incluye `genero` y `fechaDeNacimiento`.

## 7. Reglas de Firestore (completas, con `devices` y los campos nuevos del perfil)

Reemplaza todo el contenido de la pestaña "Reglas" y publica. `placement` y `devices` no cambian respecto a lo ya publicado; **el bloque `profile` es el único que cambia** (acepta `gender` y `birthDate`). **Publícalo ANTES de probar la Etapa 13.**

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Resultado del quiz de nivelación (sin cambios)
    match /users/{userId}/placement/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Perfil: correo de recuperación, avatar, género y fecha de nacimiento (CAMBIA: gender y birthDate)
    match /users/{userId}/profile/{document=**} {
      allow read, delete: if request.auth != null && request.auth.uid == userId;
      allow create, update: if request.auth != null && request.auth.uid == userId
        && request.resource.data.keys().hasOnly(['recoveryEmail', 'avatarBase64', 'updatedAt', 'gender', 'birthDate'])
        && (!('recoveryEmail' in request.resource.data)
            || (request.resource.data.recoveryEmail is string
                && request.resource.data.recoveryEmail.size() <= 254))
        && (!('avatarBase64' in request.resource.data)
            || (request.resource.data.avatarBase64 is string
                && request.resource.data.avatarBase64.size() <= 150000))
        && (!('gender' in request.resource.data)
            || request.resource.data.gender in ['male', 'female', 'other', 'prefer_not_to_say'])
        && (!('birthDate' in request.resource.data)
            || (request.resource.data.birthDate is string
                && request.resource.data.birthDate.size() == 10
                && request.resource.data.birthDate.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$')));
    }

    // NUEVO — Dispositivos (modo cooperativo): solo el dueño; id con forma de UUID; campos permitidos
    match /users/{userId}/devices/{deviceId} {
      allow read, delete: if request.auth != null && request.auth.uid == userId;
      allow create, update: if request.auth != null && request.auth.uid == userId
        && deviceId.matches('^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
        && request.resource.data.keys().hasOnly(['name', 'lastSeen', 'revoked'])
        && request.resource.data.keys().hasAll(['name'])
        && request.resource.data.name is string
        && request.resource.data.name.size() > 0
        && request.resource.data.name.size() <= 80
        && (!('lastSeen' in request.resource.data) || request.resource.data.lastSeen is timestamp)
        && (!('revoked' in request.resource.data) || request.resource.data.revoked is bool);
    }
  }
}
```

Perfil (Etapa 13): la app escribe `gender` (una de las 4 claves) y `birthDate` (`yyyy-MM-dd`) con `set(..., merge)` junto con `updatedAt`, y los borra con `FieldValue.delete()` al quitarlos. En el Rules Playground prueba una escritura de `users/<tu uid>/profile/main` con `gender = "male"` y `birthDate = "2000-05-07"` (permitida), otra con `gender = "x"` (denegada) y otra con `birthDate = "7/5/2000"` (denegada).

Cómo la usa la app: `set(..., merge)` con `name` y `lastSeen` (marca de tiempo del servidor) y `revoked` solo al crear; `update("revoked", true)` para pedir el cierre; `delete` al cerrar sesión. En el Rules Playground prueba: lectura y escritura de `users/<tu uid>/devices/<un UUID>` (permitidas), una con un campo extra como `foo` (denegada) y una con otro uid (denegada).
