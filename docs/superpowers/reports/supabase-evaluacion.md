# Evaluación de migración a Supabase — Etapa 0 (solo diagnóstico)

> **Informe histórico de la migración (Etapa 0).** Es el diagnóstico previo, cuando Firebase seguía en el código; varias
> cifras ya cambiaron (por ejemplo `minSdk`, que hoy es 26). El resultado final está en
> [`migracion-supabase.md`](migracion-supabase.md).

Rama: `experimento/supabase` (creada desde `main`, solo local, sin push). Fecha: 2026-10-17.
En esta etapa **no se modificó código de producción**: lo único que cambia en el repositorio es este informe.

## Veredicto

**Viable, con condiciones.** Reemplazar Firebase Auth + Firestore por Supabase se puede hacer sin tocar ViewModels ni las 373 pruebas, porque todo el acceso a Firebase ya está detrás de interfaces de repositorio. Las condiciones son estas:

1. **Versión de supabase-kt.** La última estable (3.8.0) **no compila** con el Kotlin actual (2.2.10). Hay dos caminos (sección 1); el que se verificó es supabase-kt **3.2.4** sin tocar Kotlin.
2. **minSdk.** El proyecto está en 24 y supabase-kt documenta 26 como mínimo. Hay que subirlo a 26 o activar desugaring (decisión del equipo).
3. **Tres flujos cambian de comportamiento y requieren cambios de UI**, no solo de datos: recuperación de contraseña, cambio de correo y el texto/lógica de cierre de sesión remoto (sección 8).
4. **Dos puntos fuera de los repositorios dependen de Firebase** y hay que cambiarlos (sección 3): `NightWatchApplication` y las pantallas de Login/Registro (por `default_web_client_id`).

**Qué se verificó y qué no.** Verificado con ejecución real: compilación y pruebas de la línea base, el choque de Kotlin con 3.8.0, la compatibilidad de 3.2.4 (compila, empaqueta y pasa 373/373), que Gradle **no** bloquea minSdk 24, y el snippet de `BuildConfig`. Estos experimentos se hicieron en una **copia desechable fuera del repositorio**. **No se verificó**: ningún comportamiento contra un proyecto Supabase real (no hay uno creado), nada en dispositivo o emulador, el SQL de las secciones 4 y 5 (no se ejecutó contra Postgres), ni el comportamiento de supabase-kt en Android 7.x. Lo marcado como "de la documentación" viene de documentación o de conocimiento general de Supabase, no de una prueba aquí.

## 0. Punto de partida (antes de escribir el informe)

| Comprobación | Resultado |
|---|---|
| `./gradlew assembleDebug testDebugUnitTest` en `main` limpio | `BUILD SUCCESSFUL` (1 min 49 s) |
| Pruebas unitarias | **373 ejecutadas, 0 fallos, 0 errores, 0 omitidas** (40 suites; contado desde los XML de resultados) |
| JDK usado por Gradle | OpenJDK 25.0.3 |
| `git status` | limpio antes y después |
| `google-services.json`, `local.properties`, `.idea/` | no están versionados (`git ls-files` no los lista; `.gitignore` los cubre) |

## 1. Versiones y compatibilidad

### Versiones actuales del proyecto

| Elemento | Valor | Dónde |
|---|---|---|
| `minSdk` | **24** | `app/build.gradle.kts:16` |
| `compileSdk` / `targetSdk` | 37 / 37 | `app/build.gradle.kts:10-17` |
| Kotlin | **2.2.10** | `gradle/libs.versions.toml` |
| AGP | 9.3.3 | `gradle/libs.versions.toml` |
| Gradle | 9.5.0 | `gradle-wrapper.properties` |
| Compilación Java | 11 | `compileOptions` |
| Compose BOM | 2026.02.01 | `libs.versions.toml` |
| kotlinx-serialization-json | 1.8.0 | `libs.versions.toml` |
| Firebase BOM | 34.19.0 | `libs.versions.toml` |

### supabase-kt: versiones y qué Kotlin/Ktor usa cada una

Última estable en Maven Central (`io.github.jan-tennert.supabase:bom`): **3.8.0** (`3.9.0-beta-1` es preliminar). Cada fila sale del `libs.versions.toml` del tag correspondiente en GitHub:

| supabase-kt | Compilada con Kotlin | Ktor | ¿Compila con Kotlin 2.2.10? |
|---|---|---|---|
| 3.2.0 | 2.2.0 | 3.2.0 | sin probar |
| **3.2.4** | **2.2.20** | **3.3.0** | **Sí, verificado** |
| 3.3.0 | 2.3.0 | 3.3.3 | sin probar |
| 3.4.1 | 2.3.10 | 3.4.0 | sin probar |
| 3.5.0 | 2.3.20 | 3.4.2 | sin probar |
| 3.6.0 | 2.3.21 | 3.4.3 | sin probar |
| 3.7.0 y **3.8.0** | **2.4.0** | **3.5.1** | **No, verificado** |

### Qué se comprobó (copia desechable, fuera del repo)

- **3.8.0 + Kotlin 2.2.10: falla.** `compileDebugKotlin` termina con `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0, expected version is 2.2.0` y `the compiler version 2.2.0 can read versions up to 2.3.0`. Peor: arrastra `kotlin-stdlib 2.4.0` al proyecto completo, y por eso también falla código que ya existía (`MainActivity.kt`, por `kotlin.Lazy`). No es un problema solo de las clases de Supabase.
- **3.2.4 + Ktor 3.3.0 (`ktor-client-okhttp`) + Kotlin 2.2.10 + AGP 9.3.3: funciona.** `assembleDebug` OK y **373/373 pruebas pasan** con las dependencias agregadas y un archivo que usa `createSupabaseClient` con `Auth` y `Postgrest`.
- **minSdk 24 no lo bloquea Gradle.** `processDebugMainManifest` con minSdk 24 y supabase-kt 3.8.0 termina bien (el *manifest merger* no se queja). La documentación oficial dice "el mínimo es API 26; para versiones menores hay que activar *core library desugaring*". Es decir, el límite es documental: **en Android 7.x (API 24-25) no se probó nada y podría fallar en ejecución**, no al compilar.

### Compatibilidad: decisiones para el equipo

| Tema | Veredicto | Opciones |
|---|---|---|
| minSdk 24 vs 26 | **No compatible según la documentación** | (a) subir `minSdk` a 26 (recomendado: más simple, nada del código actual lo impide); (b) quedarse en 24 + desugaring, sin garantía hasta probarlo en un dispositivo API 24/25 |
| Kotlin 2.2.10 | Compatible **solo** con supabase-kt ≤ 3.2.x (verificado 3.2.4) | (A) **supabase-kt 3.2.4 + Ktor 3.3.0**, sin tocar Kotlin (verificado, recomendado para esta evaluación); (B) subir Kotlin a ≥ 2.4.0 y usar 3.8.0 + Ktor 3.5.1 (**no probado**: toca el plugin de Compose, `kotlinx-serialization` y la compatibilidad con AGP 9.3.3) |
| AGP / Gradle | Sin cambios | Se respeta la regla del equipo: ninguna opción pide actualizarlos |
| Motor HTTP | `ktor-client-okhttp` en Android | La versión de Ktor debe ser **la misma** que usa supabase-kt (3.3.0 para 3.2.4) |

Recomendación: camino **A** más minSdk 26. Si más adelante se quiere la 3.8.0, subir Kotlin es un cambio de proyecto aparte que merece su propia etapa y su propia aprobación. No se evaluó qué correcciones de Auth trae la 3.8.0 respecto de la 3.2.4.

## 2. Inventario de TODOS los usos de Firebase

Búsqueda sobre `app/src` (importaciones de `com.google.firebase`, `FirebaseAuth`, `FirebaseFirestore`, `verifyBeforeUpdateEmail`, Credential Manager / Google ID, `google-services`).

### 2.1 Código de producción (`app/src/main`)

| Archivo | Clase / función | API de Firebase usada |
|---|---|---|
| `NightWatchApplication.kt:25` | `onCreate` | `FirebaseAuth.getInstance().signOut()` (cuando "recordar sesión" está apagado) |
| `di/AppContainer.kt:67,71` | `DefaultAppContainer` | `FirebaseAuth.getInstance()` ×2 |
| `di/AppContainer.kt:70,72,87` | `DefaultAppContainer` | `FirebaseFirestore.getInstance()` ×3 |
| `data/auth/FirebaseAuthRepository.kt` | `FirebaseAuthRepository` | `signInWithEmailAndPassword`, `createUserWithEmailAndPassword`, `UserProfileChangeRequest` + `updateProfile` (nombre), `sendPasswordResetEmail`, `GoogleAuthProvider.getCredential` + `signInWithCredential`, `signOut`, `currentUser.email/uid`, `fetchSignInMethodsForEmail` (**obsoleta**; solo sirve con la protección contra enumeración de correos apagada), `EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD`, `FirebaseException` |
| `data/auth/AuthErrorMapper.kt` | `mapFirebaseThrowable`, `mapAuthErrorCode`, `mapAuthErrorMessage` | `FirebaseException`, `FirebaseNetworkException`, `FirebaseAuthException.errorCode` (cadenas `ERROR_WRONG_PASSWORD`, `ERROR_INVALID_CREDENTIAL`, `ERROR_USER_NOT_FOUND`, etc.) y reconocimiento del código `PASSWORD_DOES_NOT_MEET_REQUIREMENTS` dentro del mensaje |
| `data/account/FirebaseAccountRepository.kt` | `FirebaseAccountRepository` | `currentUser` (`displayName`, `email`, `metadata.creationTimestamp`, `providerData`), `updateProfile`, `reauthenticate(EmailAuthProvider.getCredential)`, `updatePassword`, **`verifyBeforeUpdateEmail`**, `reload`, `FirebaseUser` |
| `data/quiz/FirestoreQuizResultRepository.kt` | `FirestoreQuizResultRepository` | `users/{uid}/placement/result`: `get`, `set`, `FieldValue.serverTimestamp()`. **No usa `firestoreCall`**: sin límite de tiempo, a diferencia de los otros dos repositorios de Firestore |
| `data/profile/FirestoreProfileRepository.kt` | `FirestoreProfileRepository` | `users/{uid}/profile/main`: `get`, `set(..., SetOptions.merge())`, `FieldValue.delete()`, `FieldValue.serverTimestamp()` |
| `data/device/FirestoreDeviceRepository.kt` | `FirestoreDeviceRepository` | `users/{uid}/devices/{id}`: `get`, lista de la colección, `set` con merge, `update`, `delete`, `DocumentSnapshot`, `getTimestamp` |
| `data/firestore/FirestoreAccess.kt` | `firestoreCall`, `classifyFailure` | `FirebaseFirestoreException` (por el nombre del código: `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `PERMISSION_DENIED`, `UNAUTHENTICATED`) y un límite de 5 s |

### 2.2 Relacionado con Google, no con el SDK de Firebase

| Archivo | API | Nota |
|---|---|---|
| `ui/auth/login/LoginScreen.kt:92-93` y `ui/auth/register/RegisterScreen.kt:99-100` | `CredentialManager`, `GetSignInWithGoogleOption`, `GoogleIdTokenCredential` | El `serverClientId` sale de **`R.string.default_web_client_id`, un recurso que genera el plugin `google-services` a partir de `google-services.json`**. Quitar Firebase rompe estas dos pantallas aunque no importen nada de `com.google.firebase`. |

### 2.3 Configuración de compilación

| Archivo | Qué |
|---|---|
| `build.gradle.kts` (raíz) | plugin `google.services` (`apply false`) |
| `app/build.gradle.kts:5` | plugin `com.google.gms.google-services` 4.4.4 |
| `app/build.gradle.kts:60-62` | `firebase-bom`, `firebase-auth`, `firebase-firestore` |
| `app/build.gradle.kts:69` | `kotlinx-coroutines-play-services` (solo para `Task.await()`; solo lo usa el código de Firebase) |
| `app/build.gradle.kts:65-67` | `androidx.credentials`, `credentials-play-services-auth`, `googleid` (**se conservan** con Supabase: el login con Google sigue pasando por Credential Manager) |
| `app/google-services.json` | credenciales locales; no versionado |
| `firestore.rules` | reglas de seguridad (se reemplazan por SQL, sección 5) |

### 2.4 Pruebas

| Archivo | Uso |
|---|---|
| `androidTest/.../AuthErrorMapperInstrumentedTest.kt` | construye `FirebaseException` y `FirebaseNetworkException` reales; **es instrumentada**, no forma parte de las 373 unitarias |
| `test/.../data/firestore/FirestoreAccessTest.kt` | solo prueba `classifyCodeName` con cadenas; no importa el SDK |
| `test/.../ui/profile/recoveryemail/RecoveryEmailViewModelTest.kt`, `test/.../data/device/FakeDeviceRepository.kt` | importan `FailureKind` y `FirestoreAccessException` (tipos propios con nombre "Firestore", sin SDK) |
| `test/.../RegisterViewModelTest.kt` | usa `PasswordRules.FIREBASE_SPECIAL_CHARACTERS` (solo el nombre) |

## 3. ViewModels y UI frente a `com.google.firebase`

**Resultado: ningún ViewModel ni pantalla importa nada de `com.google.firebase`.** Las 9 clases de `main` que importan ese paquete son `NightWatchApplication`, `AppContainer`, y 7 archivos bajo `data/` (`FirebaseAuthRepository`, `AuthErrorMapper`, `FirebaseAccountRepository`, `FirestoreQuizResultRepository`, `FirestoreProfileRepository`, `FirestoreDeviceRepository`, `FirestoreAccess`). La búsqueda en mayúsculas/minúsculas sobre todo `app/src/main` fuera de `data/` solo encuentra comentarios y textos (detallados abajo).

### Fugas fuera de la capa de datos

| # | Dónde | Gravedad | Detalle y arreglo |
|---|---|---|---|
| 1 | `NightWatchApplication.kt:7,25` | **Fuga real** | Llama `FirebaseAuth.getInstance().signOut()` directamente, saltándose `AuthRepository`. Arreglo: `container.authRepository.signOut()`. Es el único acceso directo al SDK fuera de `data/` y `di/`. |
| 2 | `LoginScreen.kt:93`, `RegisterScreen.kt:100` | **Acoplamiento a la configuración de Firebase** | `R.string.default_web_client_id` lo genera el plugin de Firebase. Arreglo: un campo `BuildConfig.GOOGLE_WEB_CLIENT_ID` (sección 6). Toca UI (una línea por pantalla). |
| 3 | `ui/profile/recoveryemail/RecoveryEmailViewModel.kt:10-11,31` | Nombre, no SDK | Importa `data.firestore.FailureKind` y `FirestoreAccessException`. No hay dependencia del SDK, pero si la implementación Supabase lanza esos mismos tipos no hay que tocar el ViewModel ni sus pruebas. Renombrarlos a algo neutro (p. ej. `DataAccessException`) queda para la etapa final. |
| 4 | `DevicesViewModel.kt:31-35`, `ConfirmationScreens.kt:58` | Texto visible al usuario | Dicen "No revoca el acceso de tu cuenta de Firebase". Con Supabase este texto deja de ser cierto (sección 8.1) y hay que cambiarlo. |
| 5 | `RegisterUiState.kt:12-15` (`FIREBASE_SPECIAL_CHARACTERS`) | Reglas de contraseña | La validación local copia la política de contraseñas de Firebase. Con Supabase hay que configurar el servidor con una política **al menos igual de estricta** (sección 8.7). |
| 6 | Comentarios en `ChangePasswordViewModel.kt:94`, `VerifyEmailLinkViewModel.kt:29,59`, `EditProfileViewModel.kt:207-208`, `DeviceSessionViewModel.kt:16`, `ProfileValidation.kt:7`, `ChangeEmailScreens.kt:41`, `DevicesScreen.kt:52` | Solo comentarios | Sin efecto; se actualizan en la etapa final. |

Hay además dos dependencias de comportamiento (no de imports) que conviene conocer: **19 lecturas síncronas** de `authRepository.currentUserUid`/`currentUserEmail` (en `MainActivity.onCreate`, `init` de varios ViewModels, `ReminderWorker`, etc.). La implementación Supabase tiene que garantizar que la sesión ya esté cargada cuando se lean (sección 4.1).

## 4. Interfaz por interfaz: implementación Supabase y tablas

Todas las implementaciones nuevas deben lanzar **los mismos tipos de error** que hoy (`AuthError`, `FirestoreAccessException`), para no tocar ViewModels ni fakes.

### 4.1 `AuthRepository` → `SupabaseAuthRepository(client: SupabaseClient)`

| Miembro | Implementación Supabase | Notas / riesgos |
|---|---|---|
| `currentUserEmail`, `currentUserUid` (síncronas) | `client.auth.currentUserOrNull()?.email` / `?.id` | La sesión se carga de forma asíncrona desde el disco. Hay que hacer `runBlocking { auth.awaitInitialization() }` al arrancar (`NightWatchApplication` ya usa `runBlocking` para DataStore). **Sin verificar**: qué pasa con un token vencido y sin red durante ese `await`. |
| `signIn` | `auth.signInWith(Email) { email; password }` | |
| `register(name, email, password)` | `auth.signUpWith(Email) { email; password; data = {"display_name": name} }` | Si el proyecto tiene "Confirm email" **activado**, `signUp` no abre sesión y el flujo actual (entrar directo tras registrarse) se rompe. Para el proyecto del curso: desactivarlo. |
| `sendPasswordResetEmail` | `auth.resetPasswordForEmail(email, redirectUrl)` | **Cambia de comportamiento** (sección 8.2). |
| `signInWithGoogle(idToken)` | `auth.signInWith(IDToken) { this.idToken = idToken; provider = Google }` | Sección 8.4. |
| `signOut()` (**no** suspend) | Lanzar en un `CoroutineScope` interno: `runCatching { auth.signOut() }` y **siempre** `auth.clearSession()` | **Trampa verificada en el código fuente de 3.2.4:** `Auth.signOut()` y `clearSession()` son `suspend`, y `signOut()` sin red lanza una excepción y **no limpia la sesión local** (solo ignora errores HTTP 401/403/404). Hay que llamar `clearSession()` aparte; si no, "Cerrar sesión" sin internet dejaría la sesión abierta. Firebase hoy limpia al instante y sin red. |

Mapeo de errores (reemplaza a `AuthErrorMapper`): `AuthRestException.errorCode` (`InvalidCredentials`, `UserAlreadyExists`, `WeakPassword`, `EmailAddressInvalid`, `ReauthenticationNeeded`, …), `AuthWeakPasswordException` y fallas de red (`IOException`, timeouts de Ktor) hacia `AuthError`. `AuthError.WrongPassword`, `UserNotFound` y `GoogleAccountOnly` **no se pueden producir** con Supabase (ver 8.4).

### 4.2 `AccountRepository` → `SupabaseAccountRepository(client)`

| Miembro | Implementación Supabase |
|---|---|
| `currentProfile()` | `currentUserOrNull()`: `displayName` ← `userMetadata["display_name"]`; `email` ← `email`; `creationTimeMillis` ← `createdAt.toEpochMilliseconds()`; `hasPassword` ← `identities.any { it.provider == "email" }` |
| `updateDisplayName` | `auth.updateUser { data = {"display_name": name} }` |
| `reauthenticate(currentPassword)` | volver a hacer `signInWith(Email)` con el correo actual y esa contraseña (comprueba que la sabe). Supabase no tiene "ventana de login reciente" como Firebase |
| `updatePassword` | `auth.updateUser { password = new }` |
| `changePassword` | `reauthenticate` + `updatePassword` (igual que hoy) |
| `requestEmailChange(newEmail)` | `auth.updateUser { email = newEmail }` (sección 8.3) |
| `isEmailChanged(newEmail)` | `auth.retrieveUserForCurrentSession(updateSession = true)` y comparar `email` |

`AuthError.RecentLoginRequired` se mapea desde `reauthentication_needed`; solo aparece si el proyecto exige reautenticación para cambiar la contraseña.

### 4.3 `QuizResultRepository` → `SupabasePostgrestQuizResultRepository(client)`

`getResult(uid)`: `from("placement_results").select { filter { eq("user_id", uid) } }` con `.decodeSingleOrNull`. `saveResult`: `upsert` de `level`, `weak_categories`. `completed_at` lo pone el servidor (`default now()`), como hoy `serverTimestamp()`. El DTO usa los mismos nombres de enum (`BAJO/MEDIO/ALTO`, `QuizCategory.name`).

### 4.4 `ProfileRepository` → `SupabasePostgrestProfileRepository(client)`

| Método | Implementación |
|---|---|
| `getProfile` | `select` de la fila por `user_id`; si no hay fila, `RemoteProfile()` vacío. `birth_date` (tipo `date`) viaja como `'YYYY-MM-DD'`, igual al formato actual, así que `RemoteProfile.birthDate` sigue siendo `String?` |
| `saveExtras(gender, birthDate)` | `upsert` parcial; un `null` se envía como `null` explícito para borrar la columna (equivale a `FieldValue.delete()`). Hay que construir el cuerpo con `buildJsonObject` para que el `null` no se omita |
| `saveAvatar` / `removeAvatar` | `upsert` de `avatar_base64` (o `null`) |
| `saveRecoveryEmail` | `upsert` de `recovery_email` |
| `updated_at` | lo pone un *trigger* (equivale a `serverTimestamp()` de `merge`) |

Un `upsert` de PostgREST solo actualiza las columnas enviadas, lo que reproduce el `SetOptions.merge()` actual. **No se probó contra un servidor real.**

### 4.5 `DeviceRepository` → `SupabasePostgrestDeviceRepository(client)`

| Método | Implementación |
|---|---|
| `getDevice`, `listDevices` | `select` con filtro por `user_id` (y `id`); `last_seen` → `lastSeenMillis` |
| `saveDevice(name, revoked?)` | **RPC `save_device`** (abajo): conserva la regla "un simple *visto de nuevo* nunca borra una marca de revocado" y que `last_seen` lo ponga el servidor |
| `setRevoked` | `update { set("revoked", v) } { filter { eq("id", deviceId) } }` (no toca `last_seen`, como el `update` actual) |
| `deleteDevice` | `delete` con filtro |

### 4.6 Esquema de Postgres

Propuesta (no ejecutada). Reemplaza `users/{uid}/placement/result`, `users/{uid}/profile/main` y `users/{uid}/devices/{id}`.

```sql
-- Resultado del quiz de nivelación (era users/{uid}/placement/result)
create table public.placement_results (
  user_id         uuid        primary key references auth.users (id) on delete cascade,
  level           text        not null check (level in ('BAJO', 'MEDIO', 'ALTO')),
  weak_categories text[]      not null default '{}'
                  check (weak_categories <@ array['CONCEPTOS_BASICOS', 'CONTRASENAS',
                                                  'INGENIERIA_SOCIAL', 'REDES_MALWARE']),
  completed_at    timestamptz not null default now()
);

-- Perfil (era users/{uid}/profile/main)
create table public.profiles (
  user_id        uuid        primary key references auth.users (id) on delete cascade,
  recovery_email text        check (char_length(recovery_email) <= 254),
  avatar_base64  text        check (char_length(avatar_base64) <= 150000),
  gender         text        check (gender in ('male', 'female', 'other', 'prefer_not_to_say')),
  birth_date     date,
  updated_at     timestamptz not null default now()
);

-- Dispositivos (era users/{uid}/devices/{deviceId})
create table public.devices (
  user_id   uuid        not null references auth.users (id) on delete cascade,
  id        uuid        not null,
  name      text        not null check (char_length(name) between 1 and 80),
  last_seen timestamptz not null default now(),
  revoked   boolean     not null default false,
  primary key (user_id, id)
);

-- updated_at del lado del servidor (equivale a FieldValue.serverTimestamp() en un merge)
create function public.set_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end $$;

create trigger profiles_set_updated_at
  before insert or update on public.profiles
  for each row execute function public.set_updated_at();

-- "Visto de nuevo": actualiza nombre y last_seen; solo toca revoked si llega un valor
create function public.save_device(p_id uuid, p_name text, p_revoked boolean default null)
returns void
language sql security invoker set search_path = '' as $$
  insert into public.devices (user_id, id, name, revoked)
  values ((select auth.uid()), p_id, p_name, coalesce(p_revoked, false))
  on conflict (user_id, id) do update
    set name      = excluded.name,
        last_seen = now(),
        revoked   = coalesce(p_revoked, public.devices.revoked);
$$;
```

Correspondencia de tipos: `string` → `text`; `timestamp` → `timestamptz`; `bool` → `boolean`; lista de cadenas → `text[]`; `birthDate` (cadena `yyyy-MM-dd`) → `date`; id de documento con forma de UUID → `uuid`; uid de Firebase (cadena) → `uuid` de `auth.users`.

Dos diferencias a propósito, ambas más estrictas que hoy: (1) `placement_results` valida `level` y `weak_categories`, algo que `firestore.rules` **no** hacía (solo comprobaba el dueño); (2) `date` rechaza fechas imposibles como `2026-02-31`, mientras que la regla de Firestore solo comprobaba la forma `dddd-dd-dd`.

## 5. Políticas RLS equivalentes a `firestore.rules`

La regla de Firestore `request.auth != null && request.auth.uid == userId` pasa a `to authenticated` + `auth.uid() = user_id`. `hasOnly([...])` (conjunto fijo de campos) lo cubre el propio esquema de la tabla; los `is string`, `size()`, `in [...]` y `matches(...)` los cubren los `CHECK` de la sección 4.6. `(select auth.uid())` evita reevaluar la función por fila.

```sql
alter table public.placement_results enable row level security;
alter table public.profiles          enable row level security;
alter table public.devices           enable row level security;

-- Sin sesión (rol anon) no hay acceso a nada, como "request.auth != null"
revoke all on public.placement_results, public.profiles, public.devices from anon;

create policy placement_owner on public.placement_results
  for all to authenticated
  using      ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy profiles_owner on public.profiles
  for all to authenticated
  using      ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy devices_owner on public.devices
  for all to authenticated
  using      ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
```

| Regla de Firestore | Equivalente |
|---|---|
| `allow read, write` solo el dueño (placement) | política `placement_owner` |
| `allow read, delete` solo el dueño; `create, update` con validación (profile) | política `profiles_owner` + `CHECK` (largo del correo ≤ 254, avatar ≤ 150000, `gender` en la lista, `birth_date` tipo `date`) |
| `deviceId.matches('^[0-9a-f]{8}-…$')` | columna `id uuid` |
| `keys().hasOnly(['name','lastSeen','revoked'])` y `hasAll(['name'])` | columnas fijas y `name not null` |
| `name.size() > 0 && <= 80` | `check (char_length(name) between 1 and 80)` |
| `lastSeen is timestamp`, `revoked is bool` | tipos `timestamptz` y `boolean` |

Antes de confiar en esto, hay que probar con dos usuarios de prueba que cada uno **no** puede leer, escribir ni borrar las filas del otro. La clave `anon` viaja en el APK, así que la seguridad real es RLS y solo RLS (sección 6). **Nada de este SQL se ejecutó.**

## 6. URL y clave `anon`: `local.properties` + `BuildConfig`

Qué es la clave `anon`: una clave **pública por diseño** que cualquiera puede extraer del APK; lo que protege los datos es RLS. Se mantiene fuera de git por la regla del equipo y por higiene (cambiar de proyecto sin tocar código), **no porque sea secreta**. La clave `service_role` **nunca** va en la app.

### `local.properties.example` (propuesto; se crea en la Etapa 1)

Comprobado: `.gitignore` ignora `local.properties` pero **no** ignora `local.properties.example`, así que este sí se versiona.

```properties
# Copia este archivo como local.properties (ese NO se sube a git) y completa tus valores.
sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk

# Supabase: Project Settings > API (la clave "anon" / "publishable", NUNCA la service_role)
supabase.url=https://TU-PROYECTO.supabase.co
supabase.anonKey=TU_CLAVE_ANON

# Cliente OAuth de tipo "Web" de Google (el mismo que hoy usa default_web_client_id)
google.webClientId=TU_ID.apps.googleusercontent.com

# Qué backend usa la app: firebase (por defecto) o supabase
backend=firebase
```

### `app/build.gradle.kts` (propuesto)

Verificado en la copia desechable: compila, genera los campos, y **un cambio en `local.properties` invalida el configuration cache** (mensaje de Gradle: `configuration cache cannot be reused because properties file … has changed`). Esto importa porque `gradle.properties` activa `org.gradle.configuration-cache=true`: leer el archivo con `File` a secas dejaría valores viejos; por eso se usa `providers.fileContents`. El bloque `plugins {}` debe seguir siendo lo primero (salvo el `import`).

```kotlin
import java.util.Properties

// ... plugins { ... } primero ...

val localProps = Properties().apply {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
    if (text.isPresent) load(text.get().reader())
}

fun localProp(name: String, default: String = ""): String = localProps.getProperty(name, default)

android {
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("supabase.anonKey")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("google.webClientId")}\"")
        buildConfigField("String", "BACKEND", "\"${localProp("backend", "firebase")}\"")
    }
    buildFeatures {
        buildConfig = true   // en AGP 9 viene apagado por defecto
        compose = true
    }
}
```

Si faltan las propiedades, quedan en `""`: la app con `backend=firebase` sigue compilando y funcionando igual. Con `backend=supabase` y la URL vacía, `AppContainer` debería fallar al arrancar con un mensaje claro en vez de un error de red confuso.

### Otras notas

- **Respaldo de Android:** `backup_rules.xml` y `data_extraction_rules.xml` son las plantillas por defecto, sin exclusiones, con `allowBackup="true"`. supabase-kt guarda la sesión (token de acceso y de refresco) en SharedPreferences, que quedaría dentro del respaldo en la nube. Hay que excluir ese archivo en las dos reglas (Etapa de dispositivo). No se verificó qué guarda hoy Firebase Auth en el respaldo.
- Antes de commitear, comprobar con `git ls-files` que no aparezcan `local.properties`, `google-services.json` ni la clave.

## 7. Elegir Firebase o Supabase desde `AppContainer` sin tocar ViewModels ni pruebas

Se verificó que **ninguna prueba construye `AppContainer`**: solo lo usan `NightWatchApplication` y los `Factory` de los ViewModels en `main`. Las 373 pruebas usan fakes. Por eso elegir el backend dentro de `DefaultAppContainer` no puede romperlas.

Diseño propuesto:

```kotlin
// di/Backend.kt  (interfaz interna, no la ven los ViewModels)
interface Backend {
    val authRepository: AuthRepository
    val accountRepository: AccountRepository
    val quizResultRepository: QuizResultRepository
    val profileRepository: ProfileRepository
    val deviceRepository: DeviceRepository
}

class FirebaseBackend : Backend { /* lo que hoy construye DefaultAppContainer */ }
class SupabaseBackend(context: Context) : Backend { /* createSupabaseClient + repos Supabase */ }

// DefaultAppContainer
private val backend: Backend = when (BuildConfig.BACKEND) {
    "supabase" -> SupabaseBackend(context)
    else -> FirebaseBackend()
}
override val authRepository = backend.authRepository
// ... igual para las otras cuatro
```

- Los cinco repositorios cambian juntos (la autenticación y los datos deben coincidir en el uid), por eso el interruptor es **uno solo**.
- Los ViewModels y fakes no cambian. Lo que sí hay que tocar, y conviene decirlo claro: `NightWatchApplication` (fuga 1) y `LoginScreen`/`RegisterScreen` (fuga 2, por `BuildConfig.GOOGLE_WEB_CLIENT_ID`). Eso es UI y `Application`, no ViewModels ni pruebas.
- Mientras convivan, el plugin `google-services` y las dependencias de Firebase se quedan; se quitan en la etapa final. Con el cliente Supabase construido de forma perezosa, en modo Firebase no se crea.
- Alternativa descartada por ahora: *product flavors* `firebase`/`supabase` con dependencias separadas. Dan un APK sin Firebase en la variante Supabase, pero duplican variantes de compilación y de pruebas; no compensa para un experimento. Se puede reconsiderar si el equipo decide quedarse con ambos.
- Las implementaciones Supabase deben envolver sus llamadas en un equivalente de `firestoreCall` (límite de 5 s, `FailureKind.Offline` para falta de red/timeouts, `PermissionDenied` para 401/403, `Unknown` para el resto) y lanzar `FirestoreAccessException`. Hoy `classifyFailure` importa `FirebaseFirestoreException`, así que la versión Supabase debe ser una función aparte hasta que se retire Firebase.

## 8. Funciones que cambian de comportamiento y riesgos

### 8.1 Cierre de sesión remoto (dispositivos)

Hoy es **cooperativo**: la marca `revoked` solo se cumple cuando el otro teléfono abre la app, y un cliente modificado podría ignorarla (`DeviceSessionManager`). Supabase sí puede cerrar sesiones de verdad: `auth.signOut(SignOutScope.OTHERS)` invalida en el servidor los tokens de refresco de **todas las demás** sesiones (verificado que existe en 3.2.4: `GLOBAL`, `LOCAL`, `OTHERS`).

| Aspecto | Efecto / riesgo |
|---|---|
| Alcance | `OTHERS` cierra **todas** las otras sesiones; no existe una llamada del cliente para cerrar **una** sesión concreta, ni para listarlas. La pantalla "Dispositivos" por dispositivo sigue necesitando la tabla `devices` (y la marca `revoked` cooperativa) para identificar y nombrar. Se puede ofrecer además un "Cerrar todas las demás sesiones" real. |
| Retraso | **De la documentación de Supabase:** el token de acceso (JWT) ya emitido sigue siendo válido hasta que vence (por defecto 1 hora, configurable); lo que muere de inmediato es el de refresco. Quien fue "cerrado" aún puede llamar a la API un rato. |
| El otro dispositivo | Cuando intenta refrescar y falla, `sessionStatus` pasa a no autenticado. Hoy `DeviceSessionManager.onAppActive()` devuelve `Unknown` si no puede leer el registro y **deja la sesión como está** a propósito; con una sesión muerta el teléfono revocado se vería "con sesión" y fallaría en cada llamada hasta que se observe `sessionStatus`. Hace falta un observador nuevo que lleve a esa persona al login. |
| Texto | `DevicesViewModel` y `ConfirmationScreens` dicen que **no** revoca el acceso. Con `OTHERS` eso deja de ser cierto: hay que reescribirlo. |

### 8.2 Recuperación de contraseña (no estaba en la lista; es un cambio importante)

Firebase aloja la página donde se escribe la contraseña nueva. **Supabase no aloja ninguna**: el enlace del correo redirige a la *Site URL* configurada (por defecto una dirección local que no sirve a un usuario real). Opciones: (a) *deep link* a la app (intent-filter nuevo en el manifest, URL permitida en el panel, y una pantalla para escribir la contraseña nueva) o (b) plantilla de correo con código de 6 dígitos (`{{ .Token }}`), `verifyEmailOtp(OtpType.Email.RECOVERY, …)` y luego `updateUser { password }`. En ambos casos hay que **agregar pantalla o flujo**; hoy `ForgotPasswordViewModel` solo "envía el correo y termina".

### 8.3 Cambio de correo (enlace u OTP)

| Aspecto | Hoy (Firebase) | Con Supabase |
|---|---|---|
| Envío | `verifyBeforeUpdateEmail` manda un enlace al correo **nuevo** | `updateUser { email = nuevo }` |
| Confirmación | un solo enlace | Con "Secure email change" (activado por defecto, de la documentación) se piden confirmaciones en el correo **viejo y el nuevo**. Si el usuario confirma solo uno, el correo no cambia y la pantalla dirá "no se ha verificado". |
| Enlace u OTP | enlace (el diseño de Figma pide código de 6 dígitos; el comentario de `ChangeEmailScreens.kt:41` dice que se adaptó al enlace) | **OTP** (`verifyEmailOtp(OtpType.Email.EMAIL_CHANGE, …)` con plantilla `{{ .Token }}`) coincide más con Figma pero **devuelve el campo de código a la UI**. El enlace exige *deep link* y URL permitida |
| Sesión después | Firebase puede invalidar la sesión vieja (de ahí `MESSAGE_SESSION_EXPIRED`) | la sesión sigue siendo válida; ese mensaje pierde sentido |
| Límite de correos | no es un problema práctico | **De la documentación:** el SMTP integrado de Supabase tiene un límite muy bajo de correos por hora. Para pruebas con varias personas hace falta configurar un SMTP propio. Aplica también a registro y recuperación. |

### 8.4 Inicio de sesión con Google

- Se puede conservar el Credential Manager actual: se pasa el `idToken` con `signInWith(IDToken)`. El proveedor Google debe configurarse en el panel de Supabase con el ID de cliente Web; probablemente sirva el mismo cliente OAuth que hoy usa Firebase, y la huella SHA-1 del cliente Android no depende de Firebase. **Sin verificar.**
- **Nonce:** si Credential Manager no envía nonce, hay que activar "Skip nonce checks" en el proveedor Google o generar un nonce propio (más seguro). Sin verificar.
- `R.string.default_web_client_id` desaparece junto con `google-services` (fuga 2).
- **Se pierden mensajes de error específicos:** Supabase responde `invalid_credentials` igual para "correo inexistente" y "contraseña mala", y no tiene equivalente de `fetchSignInMethodsForEmail`. `AuthError.WrongPassword`, `UserNotFound` y `GoogleAccountOnly` ("Esta cuenta se creó con Google…") no se podrán producir; todo cae en `InvalidCredentials`. Es mejor contra enumeración de cuentas y peor para el usuario.
- Supabase enlaza automáticamente identidades con el mismo correo verificado; hay que probar el caso "se registró con correo y luego entra con Google".

### 8.5 Foto de perfil: Storage en lugar de Base64

Hoy `ProfileRepository.saveAvatar(uid, avatarBase64: String)` y `RemoteProfile.avatarBase64` llevan el texto Base64 (máx. 150000 caracteres) y `AvatarRepository` decodifica. **Recomendación: en la migración conservar Base64 en la columna `avatar_base64`** (sección 4.6): no cambia ninguna interfaz. Pasar a Supabase Storage (bucket privado, ruta `{uid}/avatar.jpg`, política por carpeta con `storage.foldername(name)[1] = auth.uid()::text`) obliga a cambiar `ProfileRepository`, `RemoteProfile`, `AvatarRepository`, el caché y sus pruebas. Conviene hacerlo como etapa **opcional y posterior**. Sin probar.

### 8.6 Sin escritura pendiente sin conexión (regresión)

Firestore encola escrituras sin red y las sincroniza después; el código lo asume (comentarios en `EditProfileViewModel:207-208`, "la escritura puede sincronizarse más tarde" en `ProfileRepository`/`AvatarRepository`). **Supabase no tiene cola offline.** Si una escritura falla sin conexión, el dato queda solo en el teléfono (`AvatarSyncResult.LocalOnly`, copia local de género y fecha) y **nunca llega al servidor** a menos que el usuario lo vuelva a guardar. Los ViewModels ya manejan el fallo, pero la promesa de "se sincroniza después" desaparece. El equipo debe decidir si lo acepta o si agrega un reintento.

### 8.7 Otros cambios

- **Usuarios actuales:** tendrían que **registrarse de nuevo**. supabase-kt no importa los hashes de contraseña de Firebase, los uid cambian (cadena de Firebase → UUID de Supabase) y los datos de Firestore no se trasladan en este plan; existe una herramienta oficial de migración de usuarios de Firebase a Supabase que **no se evaluó aquí**. Para un proyecto de curso con cuentas de prueba es aceptable; para usuarios reales no. Se pierden también resultado del quiz, perfil y dispositivos.
- **Política de contraseñas:** `RegisterUiState` valida localmente con la política de Firebase. En el panel de Supabase (Auth › Password) hay que exigir **al menos lo mismo** (largo mínimo y clases de caracteres); por defecto Supabase es mucho más laxo. Si la local es más estricta que la del servidor no hay error, pero conviene que coincidan.
- **Proyecto gratuito en pausa:** de la documentación, los proyectos gratuitos se pausan tras una semana sin actividad. Relevante para una demo del curso.
- **Android 7.x:** sin probar (sección 1).

## 9. Plan de etapas de migración (un commit por etapa)

Cada etapa se hace en `experimento/supabase`, termina con `assembleDebug` OK y **373 pruebas (más las nuevas) en verde**, y lleva su commit en español con `Co-Authored-By`. Ninguna etapa actualiza AGP ni Gradle. Sin push sin aviso previo.

| Etapa | Qué | Criterio de salida |
|---|---|---|
| 0 | Este informe | Hecho |
| 1 | **Preparación sin comportamiento:** `minSdk` 26 (si el equipo lo aprueba), dependencias supabase-kt 3.2.4 + `ktor-client-okhttp` 3.3.0 en `libs.versions.toml`, campos `BuildConfig` y `local.properties.example` (sección 6). Nadie usa Supabase todavía | compila; 373 en verde; `git ls-files` sin secretos |
| 2 | **Quitar las fugas y el interruptor:** `NightWatchApplication` usa `authRepository.signOut()`; el ID de cliente de Google sale de `BuildConfig`; se introduce `Backend`/`FirebaseBackend` en `AppContainer` (solo Firebase, mismo comportamiento) | 373 en verde; ningún `FirebaseAuth` fuera de `data/` y `di/` |
| 3 | **Proyecto y esquema Supabase:** `supabase/migrations/*.sql` (secciones 4.6 y 5), desactivar "Confirm email", política de contraseñas, SMTP. Sin código de la app | prueba manual con dos usuarios: ninguno ve ni modifica los datos del otro |
| 4 | **`SupabaseAuthRepository`** + mapeo de errores + `awaitInitialization` + `signOut` con `clearSession`, tras `backend=supabase` | pruebas unitarias del mapeo de errores; registro/entrada/salida en un dispositivo |
| 5 | **`SupabaseAccountRepository`** (nombre, contraseña, `hasPassword`, cambio de correo con la opción elegida: OTP o enlace) | pruebas de mapeo; flujo de correo probado de punta a punta |
| 6 | **Repositorios Postgres:** quiz, perfil, dispositivos (+ RPC `save_device`), equivalente de `firestoreCall` | pruebas de DTO/mapeo y de clasificación de errores; datos de dos usuarios aislados |
| 7 | **Cambios de UI por comportamiento:** observador de `sessionStatus`, cierre remoto con `OTHERS`, textos de "Dispositivos", recuperación de contraseña (8.2) | revisado en dispositivo |
| 8 | **Google con ID token:** configuración del proveedor, nonce, caso de cuentas enlazadas | entra con Google en dispositivo real |
| 9 | **Lista de verificación en dispositivo** (API 26+ y, si se mantiene 24, API 24/25), exclusión de la sesión del respaldo | informe con lo probado y lo no probado |
| 10 | *(Opcional)* Foto en Supabase Storage (8.5) | `AvatarRepository` y pruebas adaptados |
| 11 | **Puerta de decisión y limpieza:** si se adopta Supabase, quitar Firebase (dependencias, plugin, `google-services.json`, `firestore.rules`, `kotlinx-coroutines-play-services`), renombrar `FirestoreAccessException`/`FIREBASE_SPECIAL_CHARACTERS`, actualizar README y comentarios | sin referencias a Firebase; 373+ en verde |

## 10. Pendientes y decisiones que necesita el equipo

1. ¿Subir `minSdk` a 26? (recomendado) ¿O quedarse en 24 y probar en dispositivo?
2. ¿supabase-kt 3.2.4 sin tocar Kotlin (verificado) o subir Kotlin a ≥ 2.4.0 para usar 3.8.0 (sin probar)?
3. Cambio de correo y recuperación de contraseña: ¿enlace con *deep link* o código OTP? (OTP coincide con Figma pero reintroduce un campo de código.)
4. ¿Se acepta que el cierre remoto sea "cerrar todas las demás" más la marca cooperativa por dispositivo, y que un JWT ya emitido siga vivo hasta que venza?
5. ¿Se acepta que los usuarios y sus datos actuales no se migren?
6. ¿Se acepta perder la sincronización diferida sin conexión, o se agrega un reintento?
7. Falta crear el proyecto Supabase real: sin él, todo lo de las secciones 4, 5 y 8 sigue siendo propuesta.
