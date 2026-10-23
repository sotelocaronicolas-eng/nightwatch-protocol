# Revisión de código del módulo 4 (Social y batallas)

Rama `modulo-4`. Revisión hecha el 22 de octubre de 2026 sobre `git diff main...HEAD` (80 archivos, unos 17.000 líneas añadidas, 23 commits
antes de esta revisión) y los archivos existentes que esos cambios tocan (Home, Privacidad, Editar perfil, el contenedor de dependencias y la
navegación). No se ejecutó nada contra Supabase ni en un teléfono: lo que depende de eso está marcado como "no comprobado".

## 1. Resumen ejecutivo

1. **No hay hallazgos críticos.** La arquitectura se cumple: ninguna pantalla ni ViewModel importa Supabase ni `BuildConfig` (lo vigila una prueba),
   el servidor decide aciertos, daños, turnos, plazos y rachas, y la app solo los muestra. Tampoco queda nada de Firebase.
2. **Un hallazgo alto (R-01):** al rotar el teléfono (o cambiar el tema, el idioma o el tamaño de letra) la pantalla de Batallas cancela en el
   servidor el reto que la persona había enviado, porque `onDispose` se trata como "salir de la pantalla".
3. **Cuatro medios:** los avisos de turno no se anuncian a TalkBack (R-02, R-03), los botones pequeños miden 36 dp (R-04) y el "hoy" de la tira de
   la semana sale del reloj del teléfono (R-05).
4. **El resto son detalles bajos:** imports sin usar, colores repetidos, un bloque duplicado tres veces, un literal fuera de `strings.xml`, un
   `Modifier` que falta y un refresco sin guardia. Los que cuestan poco se corrigen en esta tanda; los demás quedan anotados.
5. **Sondeo, ciclo de vida y seguridad están bien resueltos** (ver sección 3), con las pruebas que lo demuestran. Las pruebas pasan (1294, 0 fallos)
   y `lintDebug` no añade ninguna advertencia nueva. Se corrigieron 8 de los 22 hallazgos (el alto, uno medio y seis bajos); 7 quedan pendientes y 7 aceptados.

## 2. Hallazgos

Gravedad: Crítico, Alto, Medio, Bajo. Esfuerzo: S (menos de una hora), M (unas horas), L (más de un día). "Estado" se completa al final de la tanda de correcciones.

| ID | Gravedad | Categoría | Archivo y línea | Qué pasa | Por qué importa (buena práctica) | Corrección sugerida | Esfuerzo | Estado |
|---|---|---|---|---|---|---|---|---|
| R-01 | Alto | Ciclo de vida | `BattlesScreen.kt:52`, `QuickSearchScreen.kt:54` | `DisposableEffect { onDispose { viewModel.onLeft() } }` corre también cuando la Activity se recrea (rotar, modo oscuro, idioma, tamaño de letra; el manifiesto no declara `configChanges`). En Batallas eso llama a `cancelChallenge()` y borra `sent`: el reto pendiente (dura 2 minutos) se pierde sin que la persona haga nada. | Un efecto que se destruye no significa que la persona salió: el ViewModel sobrevive a la rotación y el efecto no debe asumir lo contrario. Una acción destructiva en el servidor no puede colgar de un evento del ciclo de vida de la vista. | No llamar a `onLeft()` si `activity.isChangingConfigurations`. Un solo efecto compartido (`LeaveScreenEffect`) en las dos pantallas. | S | **Corregido** (`6863b9c`). No comprobado en el teléfono. |
| R-02 | Medio | Accesibilidad | `ArenaScreen.kt:355`, `:373` (`RivalMoveNotice`), `:385` (`Feedback`); `UltiViews.kt` (`UltiResultPanel`) | Los avisos que cambian durante la batalla ("Mateo acertó: -20", "Incorrecto", "Tiempo agotado", "Ulti recibida", "Esperando a Mateo") aparecen sin región viva: un lector de pantalla no los anuncia, y la persona ciega no sabe qué pasó. | Un cambio de estado que importa debe anunciarse (`liveRegion`), no solo verse. | `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` en esos textos, que son fijos (no llevan segundos). | S | **Corregido** (`5028f6f`). No comprobado con TalkBack. |
| R-03 | Medio | Accesibilidad | `ArenaScreen.kt:253` (`TurnPill`) | El cambio de turno ("Tu turno", "Turno de Mateo") tampoco se anuncia, y no se puede marcar como región viva tal cual: el mismo texto lleva la cuenta regresiva, que se redibuja cuatro veces por segundo y se anunciaría cada vez. | Igual que R-02, y una región viva con texto que cambia cada segundo es peor que ninguna. | Separar el texto del turno (región viva) de los segundos (sin región viva, o con `clearAndSetSemantics`) y revisar con TalkBack en el teléfono. | M | **Corregido** (`ee504c2`): el turno ("Tu turno", "Turno de Mateo", "Reto de Ulti") y los segundos son dos textos en la misma píldora y solo el turno es región viva. Se ve igual. No comprobado con TalkBack. |
| R-04 | Medio | Accesibilidad | `SocialComponents.kt:286` (`SocialPill`) | Las píldoras (Aceptar, Rechazar, Retar, Copiar, Buscar, Revancha...) miden 36 dp de alto, por debajo de los 48 dp táctiles. | Objetivo táctil mínimo de 48 dp (Material y guía de accesibilidad de Android). | `Modifier.minimumInteractiveComponentSize()` o subir la altura a 48 dp. Cambia lo que se ve respecto a Figma (36 dp), por eso es decisión de diseño del grupo. | M | **Corregido** (`1bb7818`) sin cambiar el aspecto: el área táctil es de 48 dp y la píldora se dibuja dentro con sus 36 dp (mismo patrón que "Reenviar"). Efecto lateral: cada fila con píldora ocupa 12 dp más de alto y el ripple al pulsar cubre los 48 dp. No comprobado en el teléfono. |
| R-05 | Medio | Hora del teléfono | `PairDetailViewModel.kt:78` y `:121`, `FriendProfileViewModel.kt:76` | La tira de la semana pone el "hoy" y coloca los días compartidos con `LocalDate.now(Clock.system(ColombiaZone))`, y "hace N días" usa `Clock.systemUTC()`: los dos leen el reloj del teléfono. Las rachas y los números los decide el servidor, pero si el reloj está mal el día marcado como hoy también. La prueba `SocialBoundaryTest` solo vigila `data/`, no `ui/social`, así que no lo detecta. | Regla del módulo: nada que dependa de la hora del teléfono. | Que `social_pairs` y `social_friends` devuelvan la fecha de hoy de Colombia (script `0011`) y que la prueba de frontera vigile también `ui/social`. Necesita servidor. | M | **Aceptado** (2026-10-22): todos los usuarios están en Colombia, así que el efecto de un reloj mal puesto es mínimo y no se escribe un script solo por esto. El número `0011` pasó a reportes y bloqueos. Se revisa si hay usuarios fuera de Colombia. |
| R-06 | Bajo | Concurrencia | `ArenaViewModel.kt:157` y `:166` | `lastMineHandled` y `lastRivalHandled` se modifican dentro del lambda de `_uiState.update { }`. Si el `update` reintenta (compare-and-set) el lambda corre otra vez y el aviso de tu turno no se mostraría. Hoy no ocurre: todo se actualiza desde el hilo principal. | Los lambdas de `update` deben ser puros. | Calcular el nuevo estado a partir de una copia y asignar el resultado; mover los dos contadores fuera. Hay 46 pruebas encima, de ahí el esfuerzo. | M | Pendiente |
| R-07 | Bajo | Eficiencia | `BattlesViewModel.kt:107` (`loadStatic`) | Cada `ON_START` lanza otra lectura de amigos y marcador sin mirar si hay una en curso (las demás listas sí: `if (job?.isActive == true) return`). Volver rápido de la arena a la pantalla duplica las llamadas. | Una sola lectura por vez; menos tráfico y menos parpadeo. | Guardar el `Job` y salir si sigue activo, con una prueba. | S | **Corregido** (`d48b402`, con 2 pruebas). |
| R-08 | Bajo | Ciclo de vida | `QuickSearchViewModel.kt:105`, `QuickSearchScreen.kt:52` | Al rotar durante la búsqueda, `ON_STOP` saca de la cola y al recrearse la pantalla se entra de nuevo: el cronómetro de "sugerir un amigo" (60 s) vuelve a 0. | Es coherente con la regla "al ocultarse se sale de la cola"; el efecto es pequeño. | Ninguna por ahora. | S | Aceptado: la búsqueda dura segundos y salir de la cola al ocultarse es una decisión de diseño. |
| R-09 | Bajo | Compose | `SocialComponents.kt:85` (`SocialScreen`) | El contenedor reutilizable de todas las pantallas no tiene parámetro `modifier`. | Los componentes reutilizables exponen `modifier`. | Añadir `modifier: Modifier = Modifier` y pasarlo al fondo. | S | **Corregido** (`fd03653`). |
| R-10 | Bajo | Duplicación | `FriendRequestsScreen.kt:80`, `PairStreaksScreen.kt:90`, `BattlesScreen.kt:219` | El bloque "Aceptar (dorado) + Rechazar (con borde)" está escrito tres veces con los mismos modificadores. | Un cambio de estilo hay que hacerlo en tres sitios. | Un `AcceptDeclinePills` en `SocialComponents`. | S | **Corregido** (`fd03653`). |
| R-11 | Bajo | Recursos | `PairDetailScreen.kt:167` | El "✓" de la tira de la semana es un literal en el código. | Los textos de la interfaz van en `strings.xml` (regla del proyecto). | Pasarlo a `strings_social_pantallas.xml`. | S | **Corregido** (`fd03653`). |
| R-12 | Bajo | Tema | `ArenaScreen.kt:53-56`, `UltiViews.kt:40,94,195`, `QuickSearchScreen.kt:116` | `Color(0xFFC0503E)` está repetido tres veces con tres nombres (`RivalRed`, `IncorrectBorder`, `FailRed`) y `Color(0xFFA1A1A1)` cuatro veces, que ya existe como `NightWatchSurfaceButtonMuted`. | Los colores salen de `ui/theme` o de una sola constante. | Una constante `SocialFail` y usar `NightWatchSurfaceButtonMuted`. | S | **Corregido** (`5a3a8a3`). |
| R-13 | Bajo | Código muerto | `HomeScreen.kt` (`statusBarsPadding`), `FriendsScreen.kt` (`fillMaxWidth`), `QuickSearchScreen.kt` (`fillMaxWidth`, `NightWatchButton`), `UltiViews.kt` (`Arrangement`) | Imports sin usar (el compilador de Kotlin no los avisa). | Limpieza. | Borrar los imports. | S | **Corregido** (`5a3a8a3`; dos más que quedaron tras `fd03653` se quitaron en ese mismo commit). |
| R-14 | Bajo | Compose | `ui/social/*Screen.kt` | Solo `SocialAvatar` tiene `@Preview`. Las 13 pantallas nuevas no, aunque ya tienen un `…Content` sin estado que se puede previsualizar. | Las vistas previas aceleran el diseño y detectan regresiones visuales. | Una vista previa por pantalla con estados de ejemplo. | M | Pendiente |
| R-15 | Bajo | Pruebas | `ui/social/`, `SocialPhotos.kt`, `SupabaseSocialAvatarRemote.kt` | No hay pruebas de Compose de las pantallas de Social ni de la carga de fotos en la interfaz, ni pruebas de las llamadas reales de Storage (F-15 y F-03 de la revisión anterior). La lógica de ViewModels, repositorios, parser y cachés sí está probada. | Cobertura de la capa visual. | Pruebas de Compose con los `…Content` y un `SocialPhotoSource` falso. | L | Pendiente (ya conocido: F-03 y F-15) |
| R-16 | Bajo | Sondeo | `SocialPolling.kt:59`, `:88`, `:123` | El intervalo de 2,5 s se cuenta desde que termina cada llamada: con red lenta el hueco real puede pasar de 3 s. El servidor da la batalla por perdida a los 60 s, así que no hay riesgo, pero el comentario promete "2 a 3 segundos". | El código y su comentario deben decir lo mismo. | Aclarar el comentario (no cambia el comportamiento). | S | Aceptado: es correcto contra la regla de 60 s; solo el comentario es optimista. |
| R-17 | Bajo | Compose | `FriendsScreen.kt`, `PairStreaksScreen.kt`, `FriendRequestsScreen.kt` | Las listas son `Column` con `forEach` dentro de un `verticalScroll`, no `LazyColumn` con claves. | En listas largas, `LazyColumn` con `key` evita trabajo de más. | Ninguna: el servidor limita a 50 amigos y 20 solicitudes. | S | Aceptado: los topes del servidor mantienen las listas cortas. |
| R-18 | Bajo | Accesibilidad | `SocialComponents.kt:172` (`Chip`, 10 sp); `NightWatchGold` sobre `NightWatchBackground` | El texto de las etiquetas mide 10 sp y el dorado sobre el verde de fondo da unos 4,1:1 de contraste (el mínimo para texto pequeño es 4,5:1). Viene de Figma y de componentes del módulo 3. | Contraste y tamaño mínimos del texto. | Subir a 12 sp y oscurecer el fondo o aclarar el dorado: lo decide el diseño. | M | Aceptado: es el diseño de Figma; se revisa con el grupo. |
| R-19 | Bajo | Documentación | `docs/superpowers/supabase-configuracion.md`, `CLAUDE.md` | No mencionan el script `0010` (columnas de la foto, `social_set_avatar`, bucket `social-avatars`) ni su fila del Security Advisor. | Documentación al día antes de fusionar. | Añadir la sección y las advertencias al aplicar `0010` y al cerrar el módulo. | S | **Corregido** (commit "Documenta el script 0010 y la regla de edición de archivos"): `supabase-configuracion.md` describe `0010`, su verificación del 2026-10-22, la advertencia S7 (10 advertencias, 0 errores), los riesgos aceptados y la limitación de moderación. `CLAUDE.md` se pone al día por completo en el cierre del módulo. |
| R-20 | Bajo | Fotos | `SocialAvatarRepository.kt` | Una foto que falla no deja caché negativa: se vuelve a pedir cuando la pantalla se compone de nuevo (no en cada recomposición). | Evita reintentos repetidos en una red mala. | Guardar el fallo durante unos segundos (necesita un reloj inyectable). | S | Aceptado: es poco frecuente y un reintento al volver a la pantalla es lo esperado. |
| R-21 | Bajo | Fotos | `SocialPhotoCache.kt` | La caché de disco saca primero los archivos más antiguos por fecha de escritura (FIFO), no los menos usados, porque el código de Social no puede leer la hora del teléfono. | Una caché LRU aprovecha mejor el espacio. | Ninguna. | S | Aceptado: con 16 MB caben cientos de fotos de unos 100 KB. |
| R-22 | Bajo | Arena | `ArenaScreen.kt:94` (`onRetry = viewModel::onStart`) | "Reintentar" llama a `onStart`, que no hace nada mientras el sondeo sigue vivo, y el sondeo ya reintenta solo cada 2,5 s. | Un botón que no hace nada confunde. | Ocultar el botón mientras el sondeo corre. | S | Aceptado: el error se borra solo en cuanto una lectura sale bien. |

## 3. Lo que está bien hecho

- **Capas.** `SocialBoundaryTest` comprueba sobre el código fuente que ninguna pantalla ni ViewModel importa Supabase o `BuildConfig`, que `data/social` no conoce Supabase, Ktor ni el reloj del teléfono, y que ninguna llamada envía un `user_id`. Las fotos siguen el mismo patrón: interfaz y lógica en `data/social`, la implementación de Storage en `data/supabase`.
- **El servidor decide.** El modelo de la pregunta no tiene dónde guardar la respuesta correcta (otra prueba lo vigila). La app manda la opción con el número del turno para el que se dibujó y el servidor rechaza un turno viejo.
- **Sondeo y ciclo de vida.** `BattlePoller`, `QuickMatchPoller` y `ChallengesPoller` son flujos fríos que se cancelan con su colector, con un tope de intervalo que se exige al construirlos (`require`). La arena empieza en `ON_START` y se detiene en `ON_STOP`, así que no corre en segundo plano ni cuando otra pantalla la tapa; un segundo `onStart` no duplica la corrutina; un estado viejo no pisa uno nuevo (comparación de `version`). Salir de la cola y cancelar un reto usan un alcance propio para terminar aunque la pantalla ya no exista.
- **Estado.** Todos los ViewModels exponen `StateFlow` inmutable, los eventos van por `Channel`, las pantallas usan `collectAsStateWithLifecycle`, las cuentas regresivas se calculan con reloj monótono desde los segundos del servidor, y el texto de un formulario vive en el ViewModel (sobrevive a la rotación).
- **Seguridad y privacidad.** El comando de la ulti no se muestra, no se guarda en el estado guardado de la navegación, no se escribe en ningún registro y se borra del estado al enviarlo; los errores son tipos del dominio sin la excepción de la librería (su mensaje puede traer el token). No hay secretos ni `Log` en el código nuevo. Las claves de las fotos solo pasan si tienen forma de UUID.
- **Textos.** Todo el texto de la interfaz está en `strings_social*.xml` en español con tildes (salvo R-11).
- **Pruebas.** 1294 pruebas, ningún `assert()` suelto, `MainDispatcherRule` y Turbine donde hay eventos, ViewModels creados dentro de la prueba, tiempo virtual para los plazos y el sondeo, y fakes para cada repositorio.
- **Firebase.** No queda nada: ni código, ni dependencias, ni plugin, ni recursos.
- **Lint.** `lintDebug` termina sin errores y sin advertencias nuevas en el módulo 4. Las 206 advertencias del compilador son del módulo 3 (pruebas de ViewModel sin `@OptIn`), y las 9 de recursos sin usar son de la plantilla de Android Studio.
- **SQL.** `0008` y `0009` están aplicados y verificados (219 de 219 comprobaciones). `0010` sigue el mismo estilo y está sin aplicar.

## 4. Qué corregir ahora y qué puede esperar

**Ahora (esta tanda, sin cambiar lo que se ve):** R-01 (alto), R-02 (medio), R-07, R-09, R-10, R-11, R-12 y R-13 (bajos de esfuerzo S).

**Puede esperar:**
- R-03 y R-04: dependen de probar con TalkBack y de una decisión de diseño sobre los 36 dp.
- R-05: necesita un campo nuevo del servidor (`0011`).
- R-06: refactor del `ArenaViewModel` con muchas pruebas encima; hoy no falla.
- R-14 y R-15: vistas previas y pruebas de Compose, trabajo de varios días.
- R-19: documentación; se hace al aplicar `0010` y cerrar el módulo.

**Aceptados** (con su razón en la tabla): R-08, R-16, R-17, R-18, R-20, R-21 y R-22.

Los hallazgos de la sección 2 son 22 (0 críticos, 1 alto, 4 medios y 17 bajos).

## 5. No comprobado desde aquí

- Nada se probó en un teléfono: ni el gorro y la varita (se dibujan con paths y se comprobó que compilan; su aspecto a 56 dp falta verlo), ni el margen superior, ni TalkBack.
- `0010` no está aplicado: las fotos de Social, la sincronización y las claves nuevas solo se probaron con fakes y con el parser. El comportamiento real de las políticas de Storage está por verificar con las consultas del final del script.
- La corrección de R-01 se probó por razonamiento (no hay pruebas de Compose ni de Activity para la rotación).

## 6. Estado final de la tanda de correcciones

| Estado | Hallazgos |
|---|---|
| Corregido (11) | R-01, R-02, R-03, R-04, R-07, R-09, R-10, R-11, R-12, R-13, R-19 |
| Pendiente (3) | R-06, R-14, R-15 |
| Aceptado (8) | R-05, R-08, R-16, R-17, R-18, R-20, R-21, R-22 (R-18 se acepta hasta que el grupo revise el diseño) |

Segunda tanda (2026-10-22): R-03 (`ee504c2`), R-04 (`1bb7818`) y R-19 (documentación de `0010`) corregidos; R-05 aceptado (todos los usuarios están en Colombia).
Además: el aviso al quitar la foto de Social cuando falla el borrado del archivo (`0423013`) y reportar y bloquear (`0011`, borrador sin aplicar, y su
app: `b861cef` y `5addcdd`). Resultado: `assembleDebug testDebugUnitTest lintDebug` en verde, **1334 pruebas, 0 fallos** (antes 1294), lint con 0 errores y
las mismas 45 advertencias. No comprobado: nada contra Supabase (ni `0011` ni su guion de escenarios), nada en el teléfono ni con TalkBack.

Resultado al cerrar la tanda: `./gradlew assembleDebug testDebugUnitTest lintDebug` en verde, **1294 pruebas, 0 fallos**, sin errores ni advertencias
de lint nuevas. Commits de las correcciones: `6863b9c` (R-01), `5028f6f` (R-02), `d48b402` (R-07), `5a3a8a3` (R-12 y R-13) y `fd03653` (R-09,
R-10 y R-11). Ninguna cambia lo que se ve, salvo que R-01 deja de cancelar el reto al rotar y R-02 hace que TalkBack lea los avisos.
