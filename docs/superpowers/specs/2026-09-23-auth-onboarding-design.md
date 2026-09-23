# Auth & Onboarding — Design Spec

## Context

NightWatch Protocol is a gamified cybersecurity-learning Android app being ported
from a Figma design
(`https://www.figma.com/design/wd3fQTzF5NqsfGfdR4I1lb/NightWatch-Protocol--copia-`)
into Kotlin + Jetpack Compose, following the practices taught in Google's
"Android Basics with Compose" course (MVVM, unidirectional state flow, manual
dependency injection, stateless/stateful Composable separation).

The Figma file contains roughly 176 top-level screens across four independent
subsystems: **Auth & Onboarding**, **Core learning** (map, lessons, labs, final
exam), **Profile & Settings**, and **Social/Gamification**. That scope is too
large for one spec/plan, so the project is split into one sub-project per
subsystem, each with its own spec → plan → implementation cycle. This document
covers only the first: **Auth**. The adaptive placement quiz and study-plan
generation that follow login in the Figma flow are intentionally **out of
scope** here and will be their own sub-project once a user can sign in.

The Android project already exists as an empty Compose template
(`app/src/main/java/com/example/nightwatchprotocol/`, package
`com.example.nightwatchprotocol`, minSdk 24, targetSdk/compileSdk 37, AGP
9.3.3, Kotlin 2.2.10, Compose BOM 2026.02.01). This spec builds on top of that
template; the package name and applicationId are kept as-is (renaming is a
cheap Android Studio refactor that can happen any time before release and
isn't worth blocking this work on).

## Goal

Ship a working, tested Login / Register / Forgot-password flow backed by
Firebase Authentication, including Google Sign-In, that lands the user on a
placeholder Main Menu screen — matching the Figma design's visual style and
copy, adapted where the original design assumed native OS UI or infrastructure
this project doesn't have.

## Scope

**In scope:**
- Login screen: username/email + password, "mantener sesión iniciada"
  checkbox, inline error states (wrong password, email not found), a generic
  error dialog for unexpected failures, and a "Continuar con Google" button.
- Register screen: full name, email, password, confirm password, a live
  password-requirements checklist, and error states (passwords don't match,
  email already registered, password doesn't meet requirements).
- Forgot-password screen: enter email, trigger Firebase's password-reset
  email, show a confirmation message.
- Google Sign-In via Android Credential Manager (native account picker).
- Session persistence tied to the "mantener sesión iniciada" checkbox.
- A minimal placeholder Main Menu screen as the post-login/register
  navigation target.

**Explicitly out of scope (deferred to later sub-projects):**
- The adaptive placement quiz (BAJO/MEDIO/ALTO) and study-plan generation.
- The custom "Elegir una cuenta" / "Agregar Google correo" / "Agregar Google
  contraseña" screens from the Figma file — these mock Google's own native
  account-picker UI. Rebuilding that UI by hand would both duplicate what
  Credential Manager already provides for free and risk violating Google's
  branding guidelines for sign-in buttons/UI. Credential Manager's native
  picker replaces all three of these Figma frames.
- An in-app "set new password" screen reached via a deep link from the reset
  email. Firebase Dynamic Links (the mechanism that used to make this easy) is
  discontinued, and a same-app deep link now requires Android App Links backed
  by a verified domain, which this project doesn't have. Firebase's own hosted
  reset-password web page is used instead — the user finishes resetting their
  password in a browser, not back inside the app. This can be revisited later
  if the project acquires a domain.
- Full Profile, Social, and Core-learning subsystems.

## User Flows

Each flow below references its Figma top-level frame id(s) (file key
`wd3fQTzF5NqsfGfdR4I1lb`) so the implementer can pull exact colors, spacing,
and assets with `get_design_context` at plan-writing/implementation time —
this spec intentionally does not duplicate that pixel-level detail.

### Login (frame `1:2`, error variants `97:293`, `106:72`, `9:77`)
1. User enters username-or-email and password.
2. "Mantener sesión iniciada" checkbox, unchecked by default.
3. Tapping "INICIAR SESIÓN" calls `AuthRepository.signIn(email, password)`.
   - Success → navigate to Main Menu, clearing the auth back stack.
   - `WrongPassword` → inline error under the password field: "Contraseña
     incorrecta" (matches frame `97:293`).
   - `UserNotFound` → inline error under the username field: reuse the same
     visual style as `106:72`.
   - Any other failure (network, unknown) → the generic error dialog from
     frame `9:77`: title "Error de inicio de sesión", body "Se produjo un
     error inesperado. Intenta iniciar sesión de nuevo.", single "Aceptar"
     button.
4. "Continuar con Google" triggers the Credential Manager flow (see below).
5. "¿Aún no tienes cuenta? Regístrate aquí" navigates to Register.

### Register (frame `3:5`, `10:68`, error variants `97:184`, `106:16`, `97:214`)
1. Fields: Nombre Completo, Correo electrónico, Contraseña, Verifica la
   contraseña.
2. Live checklist under the password field, each item ticking on/off as the
   user types (matches frame `10:68`): "Mínimo 8 caracteres", "Incluye una
   mayúscula y un número", "Incluye al menos un carácter especial".
3. "REGISTRARSE" is disabled until all three password rules pass and the two
   password fields match.
4. On tap, calls `AuthRepository.register(name, email, password)`.
   - Success → navigate to Main Menu.
   - `EmailAlreadyInUse` → inline error "Ya tienes una cuenta con este correo"
     (frame `106:16`'s copy).
   - `PasswordMismatch` (client-side, before hitting the repository) → inline
     error "Contraseña no coincide" (frame `97:184`).
   - `WeakPassword` → inline error "La contraseña no cumple con los
     requerimientos" (frame `97:214`) — this is a defense-in-depth check;
     the button being disabled should normally prevent reaching it.

### Forgot Password (frame `113:38`, adapted)
The Figma design shows a two-step phone/SMS OTP flow (`113:38` →
`110:332`). Per product decision, this is replaced with Firebase's built-in
email-link reset, which needs no SMS provider, no Play Integrity setup, and no
per-message cost:
1. Single screen, visually consistent with `113:38`'s card style: a title
   ("Recuperación de contraseña"), an email input, and a "Confirmar" button.
2. On tap, calls `AuthRepository.sendPasswordResetEmail(email)`.
3. Regardless of whether the email is registered, show the same confirmation
   message ("Si el correo existe, te enviamos un enlace para restablecer tu
   contraseña"). Firebase Auth's email-enumeration protection (default since
   2023) makes `sendPasswordResetEmail` succeed unconditionally, so the UI
   must not claim to know whether the account exists.
4. The user completes the actual password change on Firebase's own hosted web
   page (opened from the email), outside the app. No in-app screen or deep
   link is built for this step.
5. "Volver al menú" returns to Login.

### Google Sign-In (replaces frames `29:94`, `72:94`, `9:27`, `202:30`)
1. "Continuar con Google" (present on Login and Register) calls Credential
   Manager's `GetCredentialRequest` with a `GetSignInWithGoogleOption`,
   showing Android's native account picker — not a custom Compose screen.
2. On success, exchange the returned Google ID token for a Firebase
   credential via `AuthRepository.signInWithGoogle(idToken)`
   (`FirebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(...))`).
3. On success → Main Menu. On failure (user cancels picker, or the exchange
   fails) → the same generic error dialog as Login's fallback error.

### Main Menu (stub)
A minimal Composable showing "Sesión iniciada como {email}" and a "Cerrar
sesión" button that calls `AuthRepository.signOut()` and returns to Login.
This is a placeholder; the real Main Menu is a future sub-project.

## Architecture

- **Pattern:** MVVM, matching "Android Basics with Compose": each screen has
  a stateful `XScreen` Composable (obtains its `ViewModel`, collects
  `StateFlow`) and a stateless `XScreenContent` Composable (pure function of
  state + lambdas), so the stateless half is directly previewable/testable
  without a ViewModel.
- **State:** each `ViewModel` exposes one `data class XUiState` via
  `StateFlow`. One-time events (navigate away, show a snackbar) go through a
  `Channel<XEvent>` exposed as a `Flow`, consumed once via
  `LaunchedEffect`/`collectLatest` — never modeled as part of `UiState`, so
  they don't re-fire on rotation.
- **Dependency injection:** manual, no Hilt (not part of the referenced
  course). A `NightWatchApplication` builds an `AppContainer` holding the
  singletons (`FirebaseAuth`, `AuthRepository`, `SessionPreferences`,
  `CredentialManager`). ViewModels receive their dependencies through a
  `ViewModelProvider.Factory` sourced from the container, resolved via
  `AndroidViewModel`/`LocalContext` in each `Screen` Composable, matching the
  course's guided-project pattern.
- **Navigation:** `androidx.navigation:navigation-compose` (2.9.x) with
  type-safe routes via `kotlinx.serialization` `@Serializable` route objects
  (`Login`, `Register`, `ForgotPassword`, `MainMenu`) inside one `NavHost`.
  Successful auth pops the entire auth back stack via
  `popUpTo<Login> { inclusive = true }` before navigating to `MainMenu`, so
  the back button never returns to a pre-auth screen.
- **Data layer:** `AuthRepository` is an interface; `FirebaseAuthRepository`
  is the only implementation, wrapping `FirebaseAuth` with suspend functions
  (no callback APIs exposed upward). All Firebase exceptions are caught at
  this layer and translated to a sealed `AuthError`
  (`WrongPassword`, `UserNotFound`, `EmailAlreadyInUse`, `WeakPassword`,
  `NetworkError`, `Unknown`) — no `FirebaseException` subtype is ever visible
  above the repository.
- **Theming:** the existing `Color.kt` / `Type.kt` / `Theme.kt` are extended
  with the palette observed in the Figma screens (dark desaturated teal
  background, pill-shaped light-gray inputs and off-white buttons, a
  stencil/tech display font for the "Nightwatch Protocol" wordmark and
  headers, a standard sans-serif for body text). Exact hex/typography values
  are pulled per-screen with `get_design_context` during implementation, not
  hand-guessed here.

## Session Persistence

Firebase Auth on Android persists the session across process death by
default — there's no "don't remember me" concept at the SDK level for mobile.
"Mantener sesión iniciada" is implemented as an app-level preference in
DataStore (`SessionPreferences`, one boolean key): if unchecked at login
time, `NightWatchApplication.onCreate()` calls `FirebaseAuth.signOut()`
before the UI ever shows, so the user is forced back to Login on next app
launch even though Firebase itself would have kept them signed in.

## Testing Strategy

- **ViewModel unit tests** for `LoginViewModel`, `RegisterViewModel`,
  `ForgotPasswordViewModel`, each against a `FakeAuthRepository` (an in-memory
  fake, not a mock) covering: happy path, every `AuthError` variant relevant
  to that screen, and the client-side validation rules (password
  match/strength) that never reach the repository.
- **Compose UI tests** (`createComposeRule`) for: the Register screen's live
  checklist reacting to typed input, the Register button's disabled/enabled
  state, and navigation from Login → Register → back.
- No instrumented test talks to real Firebase; `FirebaseAuthRepository` is
  exercised only indirectly through the interface contract its fake mirrors.

## Global Constraints

- Kotlin 2.2.10, AGP 9.3.3, compileSdk/targetSdk 37, minSdk 24 (unchanged
  from the existing template).
- Package/applicationId stays `com.example.nightwatchprotocol`.
- New dependencies (versions current as of this spec's writing — verify
  against Maven Central/Firebase release notes at implementation time, since
  patch releases ship frequently):
  - `com.google.firebase:firebase-bom:34.19.0`, using `firebase-auth` (not
    the deprecated `-ktx` artifact — Kotlin extensions are merged into the
    main artifact).
  - `androidx.navigation:navigation-compose:2.9.7`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0` +
    `org.jetbrains.kotlin.plugin.serialization` Gradle plugin (for type-safe
    nav routes).
  - `androidx.credentials:credentials:1.5.0`,
    `androidx.credentials:credentials-play-services-auth:1.5.0`,
    `com.google.android.libraries.identity.googleid:googleid:1.1.1`.
  - `androidx.datastore:datastore-preferences:1.2.1`.
  - `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2` (for
    `Task<T>.await()`).
  - `com.google.gms:google-services` Gradle plugin (reads
    `google-services.json`).
- A Firebase project does not exist yet. Creating it (Firebase console: new
  project → add Android app with package `com.example.nightwatchprotocol` →
  download `google-services.json` into `app/` → enable Authentication's
  Email/Password and Google providers) requires an interactive login to the
  user's Google account and cannot be done by an agent — the plan must call
  this out as a manual step for the human partner, with exact console
  navigation instructions.
- Google Sign-In additionally needs a Web-application OAuth client ID
  (created automatically by Firebase when you enable the Google provider) and
  the project's debug/release SHA-1 fingerprints registered in the Firebase
  console — also manual, interactive steps.
- `AndroidManifest.xml` needs `<uses-permission
  android:name="android.permission.INTERNET"/>` (not present in the current
  template).

## Open Risks / Notes

- **Version drift:** several dependencies above (Firebase BOM 34.x,
  Credential Manager 1.5.0, googleid) are recent enough that pinned patch
  versions may already be superseded by the time this plan is implemented.
  The plan should treat these as a starting point and check for newer stable
  patches before running the first build.
- **Firebase/Google Cloud console steps are manual.** No agent can complete
  interactive Google account logins; the plan must produce a clear runbook
  the human partner executes themselves, with the agent verifying the
  resulting `google-services.json` and configuration afterward.
- **Password-reset UX regression vs. the Figma design:** the original design
  promised a fully in-app phone-OTP reset flow; the shipped behavior instead
  hands the user off to a browser. This is a deliberate, product-approved
  trade-off (see Scope), not an oversight — worth a one-line mention in
  release notes/QA so nobody "fixes" it back to the old design by accident.
