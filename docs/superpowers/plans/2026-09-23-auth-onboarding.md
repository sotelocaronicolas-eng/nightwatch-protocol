# Auth & Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a working, tested Login / Register / Forgot-password flow backed by Firebase Authentication, including Google Sign-In, landing on a placeholder Main Menu.

**Architecture:** MVVM with `StateFlow`-based `UiState` per screen, one-shot navigation/dialog events via `Channel`, manual DI through an `AppContainer` (no Hilt), Compose Navigation with type-safe `@Serializable` routes, and a Firebase-backed `AuthRepository` that translates every Firebase exception into an app-level `AuthError` before it reaches a ViewModel.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01), Firebase Auth, Android Credential Manager (Google Sign-In), AndroidX Navigation Compose + kotlinx.serialization, AndroidX DataStore Preferences, JUnit4 + kotlinx-coroutines-test + Compose UI testing.

**Spec:** `docs/superpowers/specs/2026-09-23-auth-onboarding-design.md`

## Global Constraints

- Kotlin 2.2.10, AGP 9.3.3, compileSdk/targetSdk 37, minSdk 24 — unchanged from the existing template.
- Package/applicationId stays `com.example.nightwatchprotocol`.
- No Hilt — dependency injection is a hand-written `AppContainer` (course convention).
- Every screen is a stateful `XScreen` (owns the `ViewModel`) plus a stateless `XScreenContent` (pure function of state + lambdas).
- No `FirebaseException` subtype may be thrown, caught, or referenced above `FirebaseAuthRepository` — everything crossing that boundary is an `AuthError`.
- Dependency versions below are current as of this plan's writing (Sep 2026); check Maven Central / Firebase release notes for newer stable patches before Task 1's first build.
- A Firebase project does not exist yet; creating it requires an interactive Google-account login only the human partner can perform (Task 1, Step 1).
- Password recovery uses Firebase's own hosted reset-password web page — no in-app "new password" screen, no deep link, no custom domain required (see spec's Scope and Open Risks sections).

## Review Focus

- **Blank/whitespace-only fields submitted on Login or Register.** A reasonable person expects an inline validation message, not a network round-trip that comes back as "user not found" or a raw Firebase error. Covered by Task 5's `blank fields show BlankFields error and never call the repository` test and Task 6's equivalent.
- **Malformed email format (`"not-an-email"`).** Firebase reports this as `ERROR_INVALID_EMAIL`, a different problem from "wrong password" or "user not found." Covered by Task 3's mapper test and Task 5/6's ViewModel tests asserting the distinct `InvalidEmail` field error.
- **Double-tapping the submit button while a request is already in flight.** Two overlapping `signIn`/`register` calls, or two navigation events, is a real risk with a naive click handler. Covered by Task 5's `second click while loading is ignored` test (asserted via the fake repository's call count) and Task 6's equivalent.
- **No network connectivity.** A `FirebaseNetworkException` is not the same failure as "your password is wrong," and a person deserves to know the difference. Covered by Task 5's `network failure shows the network error dialog, not a field error` test.
- **Configuration change (rotation) while a request is in flight or just after it completes.** A one-shot navigation event must fire exactly once, not zero times (lost on rotation) or twice (re-delivered on recomposition). Covered by Task 5's event-collection test, which asserts exactly one `NavigateToMainMenu` event for one successful sign-in.

---

## Design Tokens Reference

Pulled from the Figma file (`wd3fQTzF5NqsfGfdR4I1lb`) via `get_design_context`/`get_variable_defs` on nodes `1:2` (Login) and `3:5` (Register) during spec/plan authoring. Every task below references these by name instead of re-deriving them.

| Token | Value | Figma source |
|---|---|---|
| `background` | `#2C595D` | fill on frame `1:2` |
| `surfaceButton` | `#D9D9D9` | `color/surface/button` |
| `surfaceButtonMuted` (input fill) | `#A1A1A1` | `color/surface/button-muted` |
| `borderAccent` (input border) | `#597F82` | `color/border/accent` |
| `linkAccent` | `#A2DBFF` | text `3:12` "Regístrate aquí" |
| `headerPanel` (Register card top) | `#CAD5D7` | fill `182:113` |
| Body font | Inter (Medium/SemiBold/Regular) | `var(--sds-typography-body-font-family)` |
| Display font (wordmark/headers) | Iceland | text `3:4` "Nightwatch Protocol" |
| Field corner radius | `12.dp` | `radius/field` |
| Button shape | capsule/pill (`RoundedCornerShape(50)`) | `radius/pill = 100` on a 40dp-tall button — a percent-based shape reproduces this at any height instead of hardcoding `100.dp` |
| Button height | `40.dp` | `size/button-height` |

**Deliberate simplifications vs. the raw Figma file** (call these out to the human partner, don't silently "fix" them back):
- The decorative background artwork (the repeated "aña" vector group behind every auth screen) is replaced with a solid `background` fill. Hand-porting arbitrary SVG path data into Android vector drawables for a purely decorative element isn't worth the risk of a broken asset; the interactive elements (buttons, fields, text) get the real design fidelity.
- Register's primary button used a `20.dp` corner radius in the Figma file while Login's buttons use a full pill. This plan uses the pill shape everywhere for a consistent design system rather than reproducing what reads as a one-off inconsistency in the source file.
- The Google Sign-In "account picker" and "confirm your identity" screens (Figma frames `29:94`, `72:94`) are not built — Android's Credential Manager shows the real native picker instead, which is both less work and avoids reconstructing Google's own branded UI.

Two downloaded static assets already live in the repo (fetched during planning, not part of any task's steps):
- `app/src/main/res/drawable-nodpi/img_logo_nightwatch.png` — the shield/lock badge logo.
- `app/src/main/res/drawable-nodpi/img_google_logo.png` — the multicolor Google "G" mark for the Google Sign-In button.

---

### Task 1: Firebase project setup & Gradle dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/google-services.json` (human partner action, not written by the implementer)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `com.google.firebase:firebase-bom` platform + `firebase-auth` on the `app` module's classpath; `androidx.navigation:navigation-compose` + `kotlinx-serialization-json` for later navigation tasks; `androidx.credentials`/`googleid` for Google Sign-In; `androidx.datastore:datastore-preferences` for session persistence; `kotlinx-coroutines-play-services` (`Task<T>.await()`) and `kotlinx-coroutines-test` (ViewModel tests) for every later task.

- [ ] **Step 1 (human partner action — cannot be automated): Create the Firebase project**

  This requires an interactive login to a Google account, which no agent can perform. Do this yourself, then hand control back:
  1. Go to the Firebase console and create a new project (any name, e.g. "NightWatch Protocol").
  2. Inside the project, click "Add app" → Android. Enter package name `com.example.nightwatchprotocol`. Skip the debug signing certificate for now (added in Step 2 below, needed for Google Sign-In).
  3. Download the generated `google-services.json` and place it at `app/google-services.json` in this repo (same folder as `app/build.gradle.kts`).
  4. In the Firebase console, go to **Authentication → Sign-in method** and enable both **Email/Password** and **Google** providers.
  5. Still in the Google provider's settings, Firebase will have created a "Web client" OAuth client ID automatically — leave it as is, it's what Credential Manager uses later.
  6. Get your debug keystore's SHA-1 fingerprint by running `./gradlew signingReport` in this project's root and copying the SHA-1 value under the `debug` variant. Add that SHA-1 in the Firebase console under **Project settings → Your apps → (your Android app) → Add fingerprint**. Without this step, Google Sign-In fails on every device using the default debug keystore (i.e. every dev machine until release signing is set up).
  7. Confirm `app/google-services.json` is present before continuing to Step 2 below.

- [ ] **Step 2: Add version catalog entries**

  Edit `gradle/libs.versions.toml`:

  ```toml
  [versions]
  agp = "9.3.3"
  coreKtx = "1.19.0"
  junit = "4.13.2"
  junitVersion = "1.3.0"
  espressoCore = "3.7.0"
  lifecycleRuntimeKtx = "2.11.0"
  activityCompose = "1.13.0"
  kotlin = "2.2.10"
  composeBom = "2026.02.01"
  firebaseBom = "34.19.0"
  navigationCompose = "2.9.7"
  kotlinxSerializationJson = "1.8.0"
  credentials = "1.5.0"
  googleid = "1.1.1"
  datastorePreferences = "1.2.1"
  kotlinxCoroutinesPlayServices = "1.10.2"
  kotlinxCoroutinesTest = "1.10.2"
  googleServicesPlugin = "4.4.4"

  [libraries]
  androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
  junit = { group = "junit", name = "junit", version.ref = "junit" }
  androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
  androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
  androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
  androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
  androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
  androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
  androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
  androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
  androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
  androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
  androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
  androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
  androidx-compose-ui-text-google-fonts = { group = "androidx.compose.ui", name = "ui-text-google-fonts" }
  androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
  firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
  firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
  androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
  kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
  androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
  androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
  googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
  androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
  kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "kotlinxCoroutinesPlayServices" }
  kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }

  [plugins]
  android-application = { id = "com.android.application", version.ref = "agp" }
  kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
  kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
  google-services = { id = "com.google.gms.google-services", version.ref = "googleServicesPlugin" }
  ```

- [ ] **Step 3: Apply the new Gradle plugins**

  Edit `build.gradle.kts` (root):

  ```kotlin
  plugins {
      alias(libs.plugins.android.application) apply false
      alias(libs.plugins.kotlin.compose) apply false
      alias(libs.plugins.kotlin.serialization) apply false
      alias(libs.plugins.google.services) apply false
  }
  ```

  Edit `app/build.gradle.kts`'s `plugins` block:

  ```kotlin
  plugins {
      alias(libs.plugins.android.application)
      alias(libs.plugins.kotlin.compose)
      alias(libs.plugins.kotlin.serialization)
      alias(libs.plugins.google.services)
  }
  ```

- [ ] **Step 4: Add the new dependencies**

  Append to `app/build.gradle.kts`'s `dependencies` block (keep every existing line):

  ```kotlin
      implementation(platform(libs.firebase.bom))
      implementation(libs.firebase.auth)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.androidx.credentials)
      implementation(libs.androidx.credentials.play.services.auth)
      implementation(libs.googleid)
      implementation(libs.androidx.datastore.preferences)
      implementation(libs.kotlinx.coroutines.play.services)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.compose.ui.text.google.fonts)
      testImplementation(libs.kotlinx.coroutines.test)
  ```

- [ ] **Step 5: Add the internet permission**

  Edit `app/src/main/AndroidManifest.xml`, adding this line right after the opening `<manifest ...>` tag and before `<application`:

  ```xml
      <uses-permission android:name="android.permission.INTERNET" />
  ```

- [ ] **Step 6: Verify the build picks up Firebase**

  Run: `./gradlew :app:assembleDebug`

  Expected: `BUILD SUCCESSFUL`. If it fails with a message about a missing `google-services.json`, stop and confirm Step 1 was completed — this task cannot proceed without it.

- [ ] **Step 7: Commit**

  ```bash
  git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml app/google-services.json
  git commit -m "build: add Firebase, Navigation Compose, Credential Manager, and DataStore dependencies"
  ```

  Note: `google-services.json` contains only public client identifiers (no secrets) and is safe to commit — this is Firebase's documented practice.

---

### Task 2: Design tokens & shared UI components

**Files:**
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/components/NightWatchBackground.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/components/NightWatchTextField.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/components/NightWatchButton.kt`
- Test: `app/src/androidTest/java/com/example/nightwatchprotocol/ui/components/NightWatchButtonTest.kt`

**Interfaces:**
- Consumes: nothing beyond the Compose BOM from Task 1.
- Produces: `NightWatchScreenBackground(content: @Composable BoxScope.() -> Unit)`, `NightWatchTextField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, isPassword: Boolean = false, isError: Boolean = false, supportingText: String? = null)`, `NightWatchButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`, `GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)` — every later screen task builds on these instead of styling raw Material components.

This task has no ViewModel logic to unit-test; its one behavioral contract (a disabled button doesn't fire `onClick`) is verified with a Compose UI test.

- [ ] **Step 1: Replace the template's color palette**

  Replace the entire contents of `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Color.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.theme

  import androidx.compose.ui.graphics.Color

  val NightWatchBackground = Color(0xFF2C595D)
  val NightWatchSurfaceButton = Color(0xFFD9D9D9)
  val NightWatchSurfaceButtonMuted = Color(0xFFA1A1A1)
  val NightWatchBorderAccent = Color(0xFF597F82)
  val NightWatchLinkAccent = Color(0xFFA2DBFF)
  val NightWatchHeaderPanel = Color(0xFFCAD5D7)
  val NightWatchOnBackground = Color.White
  val NightWatchOnButton = Color.Black
  val NightWatchError = Color(0xFFFFB4AB)
  ```

- [ ] **Step 2: Add the shape tokens**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Shape.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.theme

  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.ui.unit.dp

  val NightWatchFieldShape = RoundedCornerShape(12.dp)
  val NightWatchPillShape = RoundedCornerShape(percent = 50)
  val NightWatchButtonHeight = 40.dp
  ```

- [ ] **Step 3: Wire the display and body fonts**

  Replace the entire contents of `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Type.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.theme

  import androidx.compose.material3.Typography
  import androidx.compose.ui.text.TextStyle
  import androidx.compose.ui.text.font.Font
  import androidx.compose.ui.text.font.FontFamily
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.googlefonts.GoogleFont
  import androidx.compose.ui.unit.sp
  import com.example.nightwatchprotocol.R

  private val fontProvider = GoogleFont.Provider(
      providerAuthority = "com.google.android.gms.fonts",
      providerPackage = "com.google.android.gms",
      certificates = R.array.com_google_android_gms_fonts_certs,
  )

  val NightWatchDisplayFontFamily = FontFamily(
      Font(googleFont = GoogleFont("Iceland"), fontProvider = fontProvider, weight = FontWeight.Normal),
  )

  val NightWatchBodyFontFamily = FontFamily(
      Font(googleFont = GoogleFont("Inter"), fontProvider = fontProvider, weight = FontWeight.Normal),
      Font(googleFont = GoogleFont("Inter"), fontProvider = fontProvider, weight = FontWeight.Medium),
      Font(googleFont = GoogleFont("Inter"), fontProvider = fontProvider, weight = FontWeight.SemiBold),
      Font(googleFont = GoogleFont("Inter"), fontProvider = fontProvider, weight = FontWeight.Bold),
  )

  val NightWatchDisplayStyle = TextStyle(
      fontFamily = NightWatchDisplayFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 40.sp,
  )

  val Typography = Typography(
      bodyLarge = TextStyle(
          fontFamily = NightWatchBodyFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 16.sp,
          lineHeight = 24.sp,
          letterSpacing = 0.5.sp,
      ),
      labelLarge = TextStyle(
          fontFamily = NightWatchBodyFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 16.sp,
          lineHeight = 20.sp,
      ),
      titleLarge = TextStyle(
          fontFamily = NightWatchBodyFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 30.sp,
          lineHeight = 36.sp,
      ),
  )
  ```

  `R.array.com_google_android_gms_fonts_certs` is generated by Google Play Services when the downloadable-fonts dependency is present — it does not need to be created by hand.

- [ ] **Step 4: Simplify the theme to a single fixed dark palette**

  The design has one fixed dark look — no light/dark toggle, no dynamic color. Replace the entire contents of `app/src/main/java/com/example/nightwatchprotocol/ui/theme/Theme.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.theme

  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.darkColorScheme
  import androidx.compose.runtime.Composable

  private val NightWatchColorScheme = darkColorScheme(
      background = NightWatchBackground,
      surface = NightWatchBackground,
      primary = NightWatchSurfaceButton,
      onPrimary = NightWatchOnButton,
      onBackground = NightWatchOnBackground,
      onSurface = NightWatchOnBackground,
      error = NightWatchError,
  )

  @Composable
  fun NightWatchProtocolTheme(content: @Composable () -> Unit) {
      MaterialTheme(
          colorScheme = NightWatchColorScheme,
          typography = Typography,
          content = content,
      )
  }
  ```

- [ ] **Step 5: Add the shared background composable**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/components/NightWatchBackground.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.components

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.BoxScope
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Modifier

  @Composable
  fun NightWatchScreenBackground(
      modifier: Modifier = Modifier,
      content: @Composable BoxScope.() -> Unit,
  ) {
      Box(
          modifier = modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background),
          content = content,
      )
  }
  ```

- [ ] **Step 6: Add the shared text field**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/components/NightWatchTextField.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.components

  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.text.KeyboardOptions
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.OutlinedTextField
  import androidx.compose.material3.OutlinedTextFieldDefaults
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.platform.testTag
  import androidx.compose.ui.text.input.KeyboardType
  import androidx.compose.ui.text.input.PasswordVisualTransformation
  import androidx.compose.ui.text.input.VisualTransformation
  import androidx.compose.ui.unit.dp
  import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
  import com.example.nightwatchprotocol.ui.theme.NightWatchFieldShape
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
  import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

  @Composable
  fun NightWatchTextField(
      label: String,
      value: String,
      onValueChange: (String) -> Unit,
      modifier: Modifier = Modifier,
      isPassword: Boolean = false,
      isError: Boolean = false,
      supportingText: String? = null,
  ) {
      Column(modifier = modifier.fillMaxWidth()) {
          Text(
              text = label,
              color = NightWatchOnBackground,
              style = MaterialTheme.typography.labelLarge,
          )
          OutlinedTextField(
              value = value,
              onValueChange = onValueChange,
              modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(label),
              shape = NightWatchFieldShape,
              isError = isError,
              singleLine = true,
              visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
              keyboardOptions = if (isPassword) {
                  KeyboardOptions(keyboardType = KeyboardType.Password)
              } else {
                  KeyboardOptions.Default
              },
              supportingText = supportingText?.let { { Text(it) } },
              colors = OutlinedTextFieldDefaults.colors(
                  unfocusedContainerColor = NightWatchSurfaceButtonMuted,
                  focusedContainerColor = NightWatchSurfaceButtonMuted,
                  unfocusedBorderColor = NightWatchBorderAccent,
                  focusedBorderColor = NightWatchBorderAccent,
                  unfocusedTextColor = NightWatchOnBackground,
                  focusedTextColor = NightWatchOnBackground,
              ),
          )
      }
  }
  ```

  The label is a sibling `Text` above the field (matching the Figma design, where the label always sits above the box rather than floating inside it like Material's default `label` slot), not part of the field's own semantics. `.testTag(label)` gives Compose UI tests a reliable way to target the actual input — `onNodeWithText(label)` would otherwise match the sibling `Text`, not the editable field.

- [ ] **Step 7: Add the shared buttons**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/components/NightWatchButton.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.components

  import androidx.compose.foundation.Image
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.size
  import androidx.compose.material3.Button
  import androidx.compose.material3.ButtonDefaults
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.res.painterResource
  import androidx.compose.ui.unit.dp
  import com.example.nightwatchprotocol.R
  import com.example.nightwatchprotocol.ui.theme.NightWatchButtonHeight
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnButton
  import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
  import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

  @Composable
  fun NightWatchButton(
      text: String,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
      enabled: Boolean = true,
  ) {
      Button(
          onClick = onClick,
          enabled = enabled,
          shape = NightWatchPillShape,
          modifier = modifier.fillMaxWidth().height(NightWatchButtonHeight),
          colors = ButtonDefaults.buttonColors(
              containerColor = NightWatchSurfaceButton,
              contentColor = NightWatchOnButton,
          ),
      ) {
          Text(text)
      }
  }

  @Composable
  fun GoogleSignInButton(
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
      enabled: Boolean = true,
  ) {
      Button(
          onClick = onClick,
          enabled = enabled,
          shape = NightWatchPillShape,
          modifier = modifier.fillMaxWidth().height(NightWatchButtonHeight),
          colors = ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = NightWatchOnButton,
          ),
      ) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Image(
                  painter = painterResource(id = R.drawable.img_google_logo),
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
              )
              Text("Continuar con Google")
          }
      }
  }
  ```

- [ ] **Step 8: Write the failing disabled-button test**

  Create `app/src/androidTest/java/com/example/nightwatchprotocol/ui/components/NightWatchButtonTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.components

  import androidx.compose.ui.test.junit4.createComposeRule
  import androidx.compose.ui.test.onNodeWithText
  import androidx.compose.ui.test.performClick
  import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
  import org.junit.Rule
  import org.junit.Test

  class NightWatchButtonTest {
      @get:Rule
      val composeTestRule = createComposeRule()

      @Test
      fun disabledButton_doesNotFireOnClick() {
          var clicked = false
          composeTestRule.setContent {
              NightWatchProtocolTheme {
                  NightWatchButton(text = "INICIAR SESIÓN", onClick = { clicked = true }, enabled = false)
              }
          }

          composeTestRule.onNodeWithText("INICIAR SESIÓN").performClick()

          assert(!clicked) { "onClick fired on a disabled button" }
      }
  }
  ```

- [ ] **Step 9: Run the test to verify it passes**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.ui.components.NightWatchButtonTest"`
  Expected: `1 test completed, 0 failed` (a disabled Material `Button` already ignores clicks, so this test should pass immediately — it exists to pin that behavior against future regressions, e.g. someone wrapping the button in a clickable `Row` that bypasses the `enabled` flag).

- [ ] **Step 10: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/theme app/src/main/java/com/example/nightwatchprotocol/ui/components app/src/androidTest/java/com/example/nightwatchprotocol/ui/components
  git commit -m "feat: add NightWatch theme tokens and shared auth UI components"
  ```

---

### Task 3: Auth error model

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthError.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthErrorMapper.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/data/auth/AuthErrorMapperTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `sealed class AuthError : Exception` with objects `WrongPassword`, `UserNotFound`, `InvalidEmail`, `EmailAlreadyInUse`, `WeakPassword`, `NetworkError`, `Unknown`; `fun mapAuthErrorCode(errorCode: String?): AuthError`. Task 4's `FirebaseAuthRepository` is the only caller of `mapAuthErrorCode`; every later ViewModel task catches `AuthError` subtypes by name.

- [ ] **Step 1: Define the error type**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthError.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.auth

  sealed class AuthError(message: String) : Exception(message) {
      data object WrongPassword : AuthError("Contraseña incorrecta")
      data object UserNotFound : AuthError("Correo no encontrado")
      data object InvalidEmail : AuthError("Correo electrónico inválido")
      data object EmailAlreadyInUse : AuthError("Ya tienes una cuenta con este correo")
      data object WeakPassword : AuthError("La contraseña no cumple con los requerimientos")
      data object NetworkError : AuthError("Sin conexión a internet")
      data object Unknown : AuthError("Se produjo un error inesperado. Intenta de nuevo.")
  }
  ```

- [ ] **Step 2: Write the failing mapper test**

  Create `app/src/test/java/com/example/nightwatchprotocol/data/auth/AuthErrorMapperTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.auth

  import org.junit.Assert.assertEquals
  import org.junit.Test

  class AuthErrorMapperTest {
      @Test
      fun wrongPasswordCode_mapsToWrongPassword() {
          assertEquals(AuthError.WrongPassword, mapAuthErrorCode("ERROR_WRONG_PASSWORD"))
      }

      @Test
      fun invalidCredentialCode_mapsToWrongPassword() {
          assertEquals(AuthError.WrongPassword, mapAuthErrorCode("ERROR_INVALID_CREDENTIAL"))
      }

      @Test
      fun userNotFoundCode_mapsToUserNotFound() {
          assertEquals(AuthError.UserNotFound, mapAuthErrorCode("ERROR_USER_NOT_FOUND"))
      }

      @Test
      fun invalidEmailCode_mapsToInvalidEmail() {
          assertEquals(AuthError.InvalidEmail, mapAuthErrorCode("ERROR_INVALID_EMAIL"))
      }

      @Test
      fun emailAlreadyInUseCode_mapsToEmailAlreadyInUse() {
          assertEquals(AuthError.EmailAlreadyInUse, mapAuthErrorCode("ERROR_EMAIL_ALREADY_IN_USE"))
      }

      @Test
      fun weakPasswordCode_mapsToWeakPassword() {
          assertEquals(AuthError.WeakPassword, mapAuthErrorCode("ERROR_WEAK_PASSWORD"))
      }

      @Test
      fun unrecognizedCode_mapsToUnknown() {
          assertEquals(AuthError.Unknown, mapAuthErrorCode("ERROR_SOMETHING_NEW"))
      }

      @Test
      fun nullCode_mapsToUnknown() {
          assertEquals(AuthError.Unknown, mapAuthErrorCode(null))
      }
  }
  ```

- [ ] **Step 3: Run the test to verify it fails**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.auth.AuthErrorMapperTest"`
  Expected: FAIL — `mapAuthErrorCode` is unresolved (doesn't exist yet).

- [ ] **Step 4: Implement the mapper**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthErrorMapper.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.auth

  fun mapAuthErrorCode(errorCode: String?): AuthError = when (errorCode) {
      "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthError.WrongPassword
      "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound
      "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
      "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailAlreadyInUse
      "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
      else -> AuthError.Unknown
  }
  ```

- [ ] **Step 5: Run the test to verify it passes**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.auth.AuthErrorMapperTest"`
  Expected: `8 tests completed, 0 failed`.

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthError.kt app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthErrorMapper.kt app/src/test/java/com/example/nightwatchprotocol/data/auth/AuthErrorMapperTest.kt
  git commit -m "feat: add AuthError model and Firebase error-code mapper"
  ```

---

### Task 4: Data layer & dependency injection

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthRepository.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/auth/FirebaseAuthRepository.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/session/SessionPreferences.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/di/AppContainer.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/NightWatchApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/androidTest/java/com/example/nightwatchprotocol/data/session/SessionPreferencesTest.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/data/auth/FakeAuthRepository.kt` (test fixture, not a test class itself)

**Interfaces:**
- Consumes: `AuthError` and `mapAuthErrorCode` from Task 3.
- Produces: `interface AuthRepository { val currentUserEmail: String?; suspend fun signIn(email: String, password: String); suspend fun register(name: String, email: String, password: String); suspend fun sendPasswordResetEmail(email: String); suspend fun signInWithGoogle(idToken: String); fun signOut() }`; `class FakeAuthRepository : AuthRepository` (test fixture with public `signInResult`/`registerResult`/`sendPasswordResetEmailResult`/`signInWithGoogleResult: Result<Unit>` fields and a `signInCallCount: Int`); `interface AppContainer { val authRepository: AuthRepository; val sessionPreferences: SessionPreferences }`; `class NightWatchApplication : Application()` exposing `val container: AppContainer`. Every screen task (5-8) depends on `AppContainer`/`NightWatchApplication` for its ViewModel factory.

- [ ] **Step 1: Define the repository contract**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.auth

  interface AuthRepository {
      val currentUserEmail: String?
      suspend fun signIn(email: String, password: String)
      suspend fun register(name: String, email: String, password: String)
      suspend fun sendPasswordResetEmail(email: String)
      suspend fun signInWithGoogle(idToken: String)
      fun signOut()
  }
  ```

- [ ] **Step 2: Implement it against Firebase**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/auth/FirebaseAuthRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.auth

  import com.google.firebase.FirebaseNetworkException
  import com.google.firebase.auth.FirebaseAuth
  import com.google.firebase.auth.FirebaseAuthException
  import com.google.firebase.auth.GoogleAuthProvider
  import com.google.firebase.auth.UserProfileChangeRequest
  import kotlinx.coroutines.tasks.await

  class FirebaseAuthRepository(
      private val firebaseAuth: FirebaseAuth,
  ) : AuthRepository {

      override val currentUserEmail: String?
          get() = firebaseAuth.currentUser?.email

      override suspend fun signIn(email: String, password: String) {
          runCatchingFirebase {
              firebaseAuth.signInWithEmailAndPassword(email, password).await()
          }
      }

      override suspend fun register(name: String, email: String, password: String) {
          runCatchingFirebase {
              val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
              val profileUpdate = UserProfileChangeRequest.Builder().setDisplayName(name).build()
              result.user?.updateProfile(profileUpdate)?.await()
          }
      }

      override suspend fun sendPasswordResetEmail(email: String) {
          runCatchingFirebase {
              firebaseAuth.sendPasswordResetEmail(email).await()
          }
      }

      override suspend fun signInWithGoogle(idToken: String) {
          runCatchingFirebase {
              val credential = GoogleAuthProvider.getCredential(idToken, null)
              firebaseAuth.signInWithCredential(credential).await()
          }
      }

      override fun signOut() {
          firebaseAuth.signOut()
      }

      private inline fun <T> runCatchingFirebase(block: () -> T): T {
          try {
              return block()
          } catch (e: FirebaseNetworkException) {
              throw AuthError.NetworkError
          } catch (e: FirebaseAuthException) {
              throw mapAuthErrorCode(e.errorCode)
          }
      }
  }
  ```

  `runCatchingFirebase` is a `suspend`-safe wrapper only because it's called directly inside each `suspend` function body (not stored or passed across suspension points), so an ordinary inline function is correct here — no `suspend` modifier needed on the lambda parameter.

- [ ] **Step 3: Add the test fixture used by every later ViewModel test**

  Create `app/src/test/java/com/example/nightwatchprotocol/data/auth/FakeAuthRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.auth

  class FakeAuthRepository : AuthRepository {
      var signInResult: Result<Unit> = Result.success(Unit)
      var registerResult: Result<Unit> = Result.success(Unit)
      var sendPasswordResetEmailResult: Result<Unit> = Result.success(Unit)
      var signInWithGoogleResult: Result<Unit> = Result.success(Unit)

      var signInCallCount = 0
      var registerCallCount = 0
      var sendPasswordResetEmailCallCount = 0

      override val currentUserEmail: String = "test@example.com"

      override suspend fun signIn(email: String, password: String) {
          signInCallCount++
          signInResult.getOrThrow()
      }

      override suspend fun register(name: String, email: String, password: String) {
          registerCallCount++
          registerResult.getOrThrow()
      }

      override suspend fun sendPasswordResetEmail(email: String) {
          sendPasswordResetEmailCallCount++
          sendPasswordResetEmailResult.getOrThrow()
      }

      override suspend fun signInWithGoogle(idToken: String) {
          signInWithGoogleResult.getOrThrow()
      }

      override fun signOut() = Unit
  }
  ```

- [ ] **Step 4: Add session persistence**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/session/SessionPreferences.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.session

  import android.content.Context
  import androidx.datastore.core.DataStore
  import androidx.datastore.preferences.core.Preferences
  import androidx.datastore.preferences.core.booleanPreferencesKey
  import androidx.datastore.preferences.core.edit
  import androidx.datastore.preferences.preferencesDataStore
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.map

  val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_preferences")

  class SessionPreferences(private val dataStore: DataStore<Preferences>) {
      private val rememberSessionKey = booleanPreferencesKey("remember_session")

      val rememberSessionFlow: Flow<Boolean> = dataStore.data.map { preferences ->
          preferences[rememberSessionKey] ?: true
      }

      suspend fun setRememberSession(remember: Boolean) {
          dataStore.edit { preferences -> preferences[rememberSessionKey] = remember }
      }
  }
  ```

- [ ] **Step 5: Write the failing DataStore round-trip test**

  Create `app/src/androidTest/java/com/example/nightwatchprotocol/data/session/SessionPreferencesTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.session

  import androidx.test.ext.junit.runners.AndroidJUnit4
  import androidx.test.platform.app.InstrumentationRegistry
  import kotlinx.coroutines.flow.first
  import kotlinx.coroutines.runBlocking
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertTrue

  @RunWith(AndroidJUnit4::class)
  class SessionPreferencesTest {
      @Test
      fun defaultValue_isTrue() = runBlocking {
          val context = InstrumentationRegistry.getInstrumentation().targetContext
          val preferences = SessionPreferences(context.sessionDataStore)

          assertTrue(preferences.rememberSessionFlow.first())
      }

      @Test
      fun setRememberSession_persistsValue() = runBlocking {
          val context = InstrumentationRegistry.getInstrumentation().targetContext
          val preferences = SessionPreferences(context.sessionDataStore)

          preferences.setRememberSession(false)

          assertEquals(false, preferences.rememberSessionFlow.first())

          preferences.setRememberSession(true)
      }
  }
  ```

- [ ] **Step 6: Run the test to verify it fails**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.data.session.SessionPreferencesTest"`
  Expected: FAIL to compile — `SessionPreferences`/`sessionDataStore` already exist from Step 4, so instead this should already pass. Run it once here specifically to confirm Step 4's implementation is correct before moving on; if it fails, fix `SessionPreferences` (not the test) before proceeding.

- [ ] **Step 7: Run the test to verify it passes**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.data.session.SessionPreferencesTest"`
  Expected: `2 tests completed, 0 failed`.

- [ ] **Step 8: Add the DI container**

  Create `app/src/main/java/com/example/nightwatchprotocol/di/AppContainer.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.di

  import android.content.Context
  import com.example.nightwatchprotocol.data.auth.AuthRepository
  import com.example.nightwatchprotocol.data.auth.FirebaseAuthRepository
  import com.example.nightwatchprotocol.data.session.SessionPreferences
  import com.example.nightwatchprotocol.data.session.sessionDataStore
  import com.google.firebase.auth.FirebaseAuth

  interface AppContainer {
      val authRepository: AuthRepository
      val sessionPreferences: SessionPreferences
  }

  class DefaultAppContainer(context: Context) : AppContainer {
      override val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())
      override val sessionPreferences: SessionPreferences = SessionPreferences(context.sessionDataStore)
  }
  ```

- [ ] **Step 9: Wire the Application class and startup sign-out check**

  Create `app/src/main/java/com/example/nightwatchprotocol/NightWatchApplication.kt`:

  ```kotlin
  package com.example.nightwatchprotocol

  import android.app.Application
  import com.example.nightwatchprotocol.di.AppContainer
  import com.example.nightwatchprotocol.di.DefaultAppContainer
  import com.google.firebase.auth.FirebaseAuth
  import kotlinx.coroutines.flow.first
  import kotlinx.coroutines.runBlocking

  class NightWatchApplication : Application() {
      lateinit var container: AppContainer

      override fun onCreate() {
          super.onCreate()
          container = DefaultAppContainer(this)

          val shouldRemainSignedIn = runBlocking { container.sessionPreferences.rememberSessionFlow.first() }
          if (!shouldRemainSignedIn) {
              FirebaseAuth.getInstance().signOut()
          }
      }
  }
  ```

  `runBlocking` here is a deliberate, one-time synchronous read of a single boolean at cold start, before any UI is shown — an accepted trade-off for avoiding a splash/loading state just to gate one preference read.

- [ ] **Step 10: Register the Application class**

  Edit `app/src/main/AndroidManifest.xml`'s `<application ...>` tag to add `android:name=".NightWatchApplication"`:

  ```xml
      <application
          android:name=".NightWatchApplication"
          android:allowBackup="true"
  ```

- [ ] **Step 11: Run the full unit and instrumented suites**

  Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
  Expected: `BUILD SUCCESSFUL`, all prior tests from Tasks 2-4 still passing.

- [ ] **Step 12: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/data app/src/main/java/com/example/nightwatchprotocol/di app/src/main/java/com/example/nightwatchprotocol/NightWatchApplication.kt app/src/main/AndroidManifest.xml app/src/androidTest/java/com/example/nightwatchprotocol/data
  git commit -m "feat: add Firebase-backed AuthRepository, session persistence, and manual DI container"
  ```

---

### Task 5: Login screen

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/login/LoginUiState.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/login/LoginViewModel.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/login/LoginScreen.kt`
- Create: `app/src/test/java/com/example/nightwatchprotocol/MainDispatcherRule.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/data/session/SessionPreferences.kt` (add an `inMemory()` test factory)
- Modify: `gradle/libs.versions.toml` (add Turbine)
- Modify: `app/build.gradle.kts` (add Turbine)
- Test: `app/src/test/java/com/example/nightwatchprotocol/ui/auth/login/LoginViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`/`AuthError` (Task 4/3), `FakeAuthRepository` (Task 4), `NightWatchApplication`/`AppContainer` (Task 4), `NightWatchTextField`/`NightWatchButton`/`GoogleSignInButton`/`NightWatchScreenBackground` (Task 2), `SessionPreferences` (Task 4).
- Produces: `data class LoginUiState`, `sealed interface LoginFieldError`, `sealed interface LoginEvent`, `enum class ErrorDialogKind`, `class LoginViewModel(authRepository: AuthRepository, sessionPreferences: SessionPreferences) : ViewModel()` with `uiState: StateFlow<LoginUiState>`, `events: Flow<LoginEvent>`, and functions `onUsernameChanged`, `onPasswordChanged`, `onRememberSessionChanged`, `onSignInClicked`, `onGoogleIdTokenReceived`, `onGoogleSignInFailed`, `onRegisterLinkClicked`, `onForgotPasswordClicked`; `@Composable fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToRegister: () -> Unit, onNavigateToForgotPassword: () -> Unit, viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory))`. `ErrorDialogKind` and the `LoginEvent`/screen-callback shape are reused verbatim by Tasks 6 and 7.

- [ ] **Step 1: Add the shared test dispatcher rule**

  Create `app/src/test/java/com/example/nightwatchprotocol/MainDispatcherRule.kt`:

  ```kotlin
  package com.example.nightwatchprotocol

  import kotlinx.coroutines.ExperimentalCoroutinesApi
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.test.StandardTestDispatcher
  import kotlinx.coroutines.test.TestDispatcher
  import kotlinx.coroutines.test.resetMain
  import kotlinx.coroutines.test.setMain
  import org.junit.rules.TestWatcher
  import org.junit.runner.Description

  @OptIn(ExperimentalCoroutinesApi::class)
  class MainDispatcherRule(
      private val testDispatcher: TestDispatcher = StandardTestDispatcher(),
  ) : TestWatcher() {
      override fun starting(description: Description) {
          Dispatchers.setMain(testDispatcher)
      }

      override fun finished(description: Description) {
          Dispatchers.resetMain()
      }
  }
  ```

- [ ] **Step 2: Define the UI state and event types**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/login/LoginUiState.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.login

  data class LoginUiState(
      val usernameOrEmail: String = "",
      val password: String = "",
      val rememberSession: Boolean = false,
      val isLoading: Boolean = false,
      val fieldError: LoginFieldError? = null,
  )

  sealed interface LoginFieldError {
      data object BlankFields : LoginFieldError
      data object WrongPassword : LoginFieldError
      data object UserNotFound : LoginFieldError
      data object InvalidEmail : LoginFieldError
  }

  enum class ErrorDialogKind {
      NETWORK,
      UNKNOWN,
  }

  sealed interface LoginEvent {
      data object NavigateToMainMenu : LoginEvent
      data object NavigateToRegister : LoginEvent
      data object NavigateToForgotPassword : LoginEvent
      data class ShowErrorDialog(val kind: ErrorDialogKind) : LoginEvent
  }
  ```

- [ ] **Step 3: Write the failing ViewModel tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/ui/auth/login/LoginViewModelTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.login

  import app.cash.turbine.test
  import com.example.nightwatchprotocol.MainDispatcherRule
  import com.example.nightwatchprotocol.data.auth.AuthError
  import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
  import com.example.nightwatchprotocol.data.session.SessionPreferences
  import kotlinx.coroutines.test.runTest
  import org.junit.Assert.assertEquals
  import org.junit.Rule
  import org.junit.Test

  class LoginViewModelTest {
      @get:Rule
      val mainDispatcherRule = MainDispatcherRule()

      private fun createViewModel(
          fakeAuthRepository: FakeAuthRepository = FakeAuthRepository(),
      ): Pair<LoginViewModel, FakeAuthRepository> {
          val viewModel = LoginViewModel(fakeAuthRepository, InMemorySessionPreferences())
          return viewModel to fakeAuthRepository
      }

      @Test
      fun signIn_success_navigatesToMainMenu() = runTest {
          val (viewModel, _) = createViewModel()
          viewModel.onUsernameChanged("user@example.com")
          viewModel.onPasswordChanged("Sup3r$ecret")

          viewModel.events.test {
              viewModel.onSignInClicked()
              assertEquals(LoginEvent.NavigateToMainMenu, awaitItem())
          }
      }

      @Test
      fun signIn_blankFields_showsBlankFieldsErrorAndNeverCallsRepository() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          viewModel.onUsernameChanged("   ")
          viewModel.onPasswordChanged("")

          viewModel.onSignInClicked()

          assertEquals(LoginFieldError.BlankFields, viewModel.uiState.value.fieldError)
          assertEquals(0, fakeRepo.signInCallCount)
      }

      @Test
      fun signIn_wrongPassword_showsWrongPasswordFieldError() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          fakeRepo.signInResult = Result.failure(AuthError.WrongPassword)
          viewModel.onUsernameChanged("user@example.com")
          viewModel.onPasswordChanged("wrong")

          viewModel.onSignInClicked()

          assertEquals(LoginFieldError.WrongPassword, viewModel.uiState.value.fieldError)
      }

      @Test
      fun signIn_invalidEmail_showsInvalidEmailFieldError() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          fakeRepo.signInResult = Result.failure(AuthError.InvalidEmail)
          viewModel.onUsernameChanged("not-an-email")
          viewModel.onPasswordChanged("Sup3r$ecret")

          viewModel.onSignInClicked()

          assertEquals(LoginFieldError.InvalidEmail, viewModel.uiState.value.fieldError)
      }

      @Test
      fun signIn_networkError_showsNetworkErrorDialogNotFieldError() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          fakeRepo.signInResult = Result.failure(AuthError.NetworkError)
          viewModel.onUsernameChanged("user@example.com")
          viewModel.onPasswordChanged("Sup3r$ecret")

          viewModel.events.test {
              viewModel.onSignInClicked()
              assertEquals(LoginEvent.ShowErrorDialog(ErrorDialogKind.NETWORK), awaitItem())
          }
          assertEquals(null, viewModel.uiState.value.fieldError)
      }

      @Test
      fun signIn_secondClickWhileLoading_isIgnored() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          viewModel.onUsernameChanged("user@example.com")
          viewModel.onPasswordChanged("Sup3r$ecret")

          viewModel.onSignInClicked()
          viewModel.onSignInClicked()

          assertEquals(1, fakeRepo.signInCallCount)
      }
  }
  ```

  This test uses Turbine (`app.cash.turbine:turbine`) to assert on the one-shot `events` flow — add it now: append to `gradle/libs.versions.toml`'s `[versions]` `turbine = "1.2.1"`, to `[libraries]` `turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }`, and to `app/build.gradle.kts`'s dependencies `testImplementation(libs.turbine)`.

  `InMemorySessionPreferences` doesn't exist yet — add it to the bottom of this same test file (it's a tiny fake used only by this test, not shared):

  ```kotlin
  private fun InMemorySessionPreferences(): SessionPreferences {
      throw NotImplementedError("replaced by a real in-memory fake in Step 4 below")
  }
  ```

- [ ] **Step 4: Make `SessionPreferences` fakeable, then replace the placeholder**

  `SessionPreferences` (Task 4) wraps a real `DataStore<Preferences>`, which isn't constructible in a JVM unit test. Change its constructor to take the data source as an interface instead of a concrete DataStore, so tests can substitute an in-memory one.

  Modify `app/src/main/java/com/example/nightwatchprotocol/data/session/SessionPreferences.kt` so the whole file reads:

  ```kotlin
  package com.example.nightwatchprotocol.data.session

  import android.content.Context
  import androidx.datastore.core.DataStore
  import androidx.datastore.preferences.core.Preferences
  import androidx.datastore.preferences.core.booleanPreferencesKey
  import androidx.datastore.preferences.core.edit
  import androidx.datastore.preferences.core.preferencesOf
  import androidx.datastore.preferences.preferencesDataStore
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.map

  val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_preferences")

  class SessionPreferences(private val dataStore: DataStore<Preferences>) {
      private val rememberSessionKey = booleanPreferencesKey("remember_session")

      val rememberSessionFlow: Flow<Boolean> = dataStore.data.map { preferences ->
          preferences[rememberSessionKey] ?: true
      }

      suspend fun setRememberSession(remember: Boolean) {
          dataStore.edit { preferences -> preferences[rememberSessionKey] = remember }
      }

      companion object {
          fun inMemory(initialRememberSession: Boolean = true): SessionPreferences =
              SessionPreferences(InMemoryPreferencesDataStore(initialRememberSession))
      }
  }

  private class InMemoryPreferencesDataStore(
      initialRememberSession: Boolean,
  ) : DataStore<Preferences> {
      private val key = booleanPreferencesKey("remember_session")
      private val state = MutableStateFlow(preferencesOf(key to initialRememberSession))

      override val data: Flow<Preferences> = state

      override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
          val updated = transform(state.value)
          state.value = updated
          return updated
      }
  }
  ```

  `InMemoryPreferencesDataStore` is a minimal `DataStore<Preferences>` backed by a `MutableStateFlow` — this small amount of boilerplate is cheaper than pulling in a mocking library for one boolean. Production code (`DefaultAppContainer`, Task 4) is untouched: it still constructs `SessionPreferences` with a real `DataStore<Preferences>` via `context.sessionDataStore`; only tests use `SessionPreferences.inMemory()`.

  Now go back to `LoginViewModelTest.kt` (Step 3) and replace the placeholder `InMemorySessionPreferences()` function with a direct call: replace every `InMemorySessionPreferences()` with `SessionPreferences.inMemory()`, and delete the placeholder function.

- [ ] **Step 5: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.auth.login.LoginViewModelTest"`
  Expected: FAIL to compile — `LoginViewModel` doesn't exist yet.

- [ ] **Step 6: Implement the ViewModel**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/login/LoginViewModel.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.login

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import androidx.lifecycle.viewmodel.initializer
  import androidx.lifecycle.viewmodel.viewModelFactory
  import com.example.nightwatchprotocol.NightWatchApplication
  import com.example.nightwatchprotocol.data.auth.AuthError
  import com.example.nightwatchprotocol.data.auth.AuthRepository
  import com.example.nightwatchprotocol.data.session.SessionPreferences
  import kotlinx.coroutines.channels.Channel
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.receiveAsFlow
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.launch

  class LoginViewModel(
      private val authRepository: AuthRepository,
      private val sessionPreferences: SessionPreferences,
  ) : ViewModel() {

      private val _uiState = MutableStateFlow(LoginUiState())
      val uiState = _uiState.asStateFlow()

      private val _events = Channel<LoginEvent>(Channel.BUFFERED)
      val events: Flow<LoginEvent> = _events.receiveAsFlow()

      fun onUsernameChanged(value: String) {
          _uiState.update { it.copy(usernameOrEmail = value, fieldError = null) }
      }

      fun onPasswordChanged(value: String) {
          _uiState.update { it.copy(password = value, fieldError = null) }
      }

      fun onRememberSessionChanged(value: Boolean) {
          _uiState.update { it.copy(rememberSession = value) }
      }

      fun onSignInClicked() {
          val state = _uiState.value
          if (state.isLoading) return

          val username = state.usernameOrEmail.trim()
          val password = state.password
          if (username.isBlank() || password.isBlank()) {
              _uiState.update { it.copy(fieldError = LoginFieldError.BlankFields) }
              return
          }

          _uiState.update { it.copy(isLoading = true, fieldError = null) }
          viewModelScope.launch {
              try {
                  authRepository.signIn(username, password)
                  sessionPreferences.setRememberSession(state.rememberSession)
                  _uiState.update { it.copy(isLoading = false) }
                  _events.send(LoginEvent.NavigateToMainMenu)
              } catch (error: AuthError) {
                  handleSignInError(error)
              }
          }
      }

      fun onGoogleIdTokenReceived(idToken: String) {
          if (_uiState.value.isLoading) return
          _uiState.update { it.copy(isLoading = true) }
          viewModelScope.launch {
              try {
                  authRepository.signInWithGoogle(idToken)
                  sessionPreferences.setRememberSession(true)
                  _uiState.update { it.copy(isLoading = false) }
                  _events.send(LoginEvent.NavigateToMainMenu)
              } catch (error: AuthError) {
                  _uiState.update { it.copy(isLoading = false) }
                  _events.send(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN))
              }
          }
      }

      fun onGoogleSignInFailed() {
          viewModelScope.launch { _events.send(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN)) }
      }

      fun onRegisterLinkClicked() {
          viewModelScope.launch { _events.send(LoginEvent.NavigateToRegister) }
      }

      fun onForgotPasswordClicked() {
          viewModelScope.launch { _events.send(LoginEvent.NavigateToForgotPassword) }
      }

      private suspend fun handleSignInError(error: AuthError) {
          val fieldError = when (error) {
              AuthError.WrongPassword -> LoginFieldError.WrongPassword
              AuthError.UserNotFound -> LoginFieldError.UserNotFound
              AuthError.InvalidEmail -> LoginFieldError.InvalidEmail
              else -> null
          }
          _uiState.update { it.copy(isLoading = false, fieldError = fieldError) }
          if (fieldError == null) {
              val kind = if (error == AuthError.NetworkError) ErrorDialogKind.NETWORK else ErrorDialogKind.UNKNOWN
              _events.send(LoginEvent.ShowErrorDialog(kind))
          }
      }

      companion object {
          val Factory: ViewModelProvider.Factory = viewModelFactory {
              initializer {
                  val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                  LoginViewModel(application.container.authRepository, application.container.sessionPreferences)
              }
          }
      }
  }
  ```

- [ ] **Step 7: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.auth.login.LoginViewModelTest"`
  Expected: `6 tests completed, 0 failed`.

- [ ] **Step 8: Build the screen**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/login/LoginScreen.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.login

  import androidx.compose.foundation.Image
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.layout.wrapContentSize
  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.Checkbox
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.collectAsState
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.rememberCoroutineScope
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.res.painterResource
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.unit.dp
  import androidx.credentials.CredentialManager
  import androidx.credentials.CustomCredential
  import androidx.credentials.GetCredentialRequest
  import androidx.credentials.exceptions.GetCredentialException
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.example.nightwatchprotocol.R
  import com.example.nightwatchprotocol.ui.components.GoogleSignInButton
  import com.example.nightwatchprotocol.ui.components.NightWatchButton
  import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
  import com.example.nightwatchprotocol.ui.components.NightWatchTextField
  import com.example.nightwatchprotocol.ui.theme.NightWatchDisplayStyle
  import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
  import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
  import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
  import kotlinx.coroutines.launch

  @Composable
  fun LoginScreen(
      onLoginSuccess: () -> Unit,
      onNavigateToRegister: () -> Unit,
      onNavigateToForgotPassword: () -> Unit,
      viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
  ) {
      val uiState by viewModel.uiState.collectAsState()
      val context = LocalContext.current
      val coroutineScope = rememberCoroutineScope()
      var dialogKind by remember { mutableStateOf<ErrorDialogKind?>(null) }

      LaunchedEffect(viewModel) {
          viewModel.events.collect { event ->
              when (event) {
                  LoginEvent.NavigateToMainMenu -> onLoginSuccess()
                  LoginEvent.NavigateToRegister -> onNavigateToRegister()
                  LoginEvent.NavigateToForgotPassword -> onNavigateToForgotPassword()
                  is LoginEvent.ShowErrorDialog -> dialogKind = event.kind
              }
          }
      }

      LoginScreenContent(
          uiState = uiState,
          onUsernameChanged = viewModel::onUsernameChanged,
          onPasswordChanged = viewModel::onPasswordChanged,
          onRememberSessionChanged = viewModel::onRememberSessionChanged,
          onSignInClicked = viewModel::onSignInClicked,
          onRegisterLinkClicked = viewModel::onRegisterLinkClicked,
          onForgotPasswordClicked = viewModel::onForgotPasswordClicked,
          onGoogleSignInClicked = {
              coroutineScope.launch {
                  val credentialManager = CredentialManager.create(context)
                  val option = GetSignInWithGoogleOption.Builder(
                      serverClientId = context.getString(R.string.default_web_client_id),
                  ).build()
                  val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                  try {
                      val result = credentialManager.getCredential(context, request)
                      val credential = result.credential
                      if (credential is CustomCredential &&
                          credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                      ) {
                          val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                          viewModel.onGoogleIdTokenReceived(googleIdTokenCredential.idToken)
                      } else {
                          viewModel.onGoogleSignInFailed()
                      }
                  } catch (e: GetCredentialException) {
                      viewModel.onGoogleSignInFailed()
                  }
              }
          },
      )

      dialogKind?.let { kind ->
          AlertDialog(
              onDismissRequest = { dialogKind = null },
              title = { Text("Error de inicio de sesión") },
              text = {
                  Text(
                      if (kind == ErrorDialogKind.NETWORK) {
                          "Sin conexión a internet. Verifica tu conexión e intenta de nuevo."
                      } else {
                          "Se produjo un error inesperado. Intenta iniciar sesión de nuevo."
                      },
                  )
              },
              confirmButton = {
                  TextButton(onClick = { dialogKind = null }) { Text("Aceptar") }
              },
          )
      }
  }

  @Composable
  private fun LoginScreenContent(
      uiState: LoginUiState,
      onUsernameChanged: (String) -> Unit,
      onPasswordChanged: (String) -> Unit,
      onRememberSessionChanged: (Boolean) -> Unit,
      onSignInClicked: () -> Unit,
      onRegisterLinkClicked: () -> Unit,
      onForgotPasswordClicked: () -> Unit,
      onGoogleSignInClicked: () -> Unit,
  ) {
      NightWatchScreenBackground {
          Column(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 32.dp)
                  .wrapContentSize(Alignment.Center),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
              Image(
                  painter = painterResource(id = R.drawable.img_logo_nightwatch),
                  contentDescription = null,
                  modifier = Modifier.size(180.dp),
              )
              Text(
                  text = "Nightwatch Protocol",
                  style = NightWatchDisplayStyle,
                  color = NightWatchOnBackground,
                  modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
              )
              NightWatchTextField(
                  label = "Nombre de usuario/correo electrónico",
                  value = uiState.usernameOrEmail,
                  onValueChange = onUsernameChanged,
                  isError = uiState.fieldError == LoginFieldError.UserNotFound ||
                      uiState.fieldError == LoginFieldError.InvalidEmail,
                  supportingText = when (uiState.fieldError) {
                      LoginFieldError.UserNotFound -> "Correo no encontrado"
                      LoginFieldError.InvalidEmail -> "Correo electrónico inválido"
                      LoginFieldError.BlankFields -> "Completa ambos campos"
                      else -> null
                  },
              )
              NightWatchTextField(
                  label = "Contraseña",
                  value = uiState.password,
                  onValueChange = onPasswordChanged,
                  isPassword = true,
                  isError = uiState.fieldError == LoginFieldError.WrongPassword,
                  supportingText = if (uiState.fieldError == LoginFieldError.WrongPassword) {
                      "Contraseña incorrecta"
                  } else {
                      null
                  },
                  modifier = Modifier.padding(top = 12.dp),
              )
              Text(
                  text = "¿Olvidaste tu contraseña?",
                  color = NightWatchLinkAccent,
                  modifier = Modifier
                      .padding(top = 8.dp)
                      .align(Alignment.End)
                      .clickableText(onForgotPasswordClicked),
              )
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(top = 8.dp),
              ) {
                  Checkbox(checked = uiState.rememberSession, onCheckedChange = onRememberSessionChanged)
                  Text("Mantener sesión iniciada", color = NightWatchOnBackground)
              }
              NightWatchButton(
                  text = "INICIAR SESIÓN",
                  onClick = onSignInClicked,
                  enabled = !uiState.isLoading,
                  modifier = Modifier.padding(top = 16.dp),
              )
              Text("O", color = NightWatchOnBackground, modifier = Modifier.padding(vertical = 8.dp))
              GoogleSignInButton(onClick = onGoogleSignInClicked, enabled = !uiState.isLoading)
              Row(modifier = Modifier.padding(top = 24.dp)) {
                  Text("¿Aún no tienes cuenta? ", color = NightWatchOnBackground)
                  Text(
                      text = "Regístrate aquí",
                      color = NightWatchLinkAccent,
                      textAlign = TextAlign.Center,
                      modifier = Modifier.clickableText(onRegisterLinkClicked),
                  )
              }
          }
      }
  }

  private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
      this.then(Modifier.clickable(onClick = onClick))
  ```

- [ ] **Step 9: Build the module to catch compile errors**

  Run: `./gradlew :app:compileDebugKotlin`
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/auth/login app/src/main/java/com/example/nightwatchprotocol/data/session/SessionPreferences.kt app/src/test/java/com/example/nightwatchprotocol/MainDispatcherRule.kt app/src/test/java/com/example/nightwatchprotocol/ui/auth/login gradle/libs.versions.toml app/build.gradle.kts
  git commit -m "feat: add Login screen with Firebase email/password and Google sign-in"
  ```

---

### Task 6: Register screen

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/register/RegisterUiState.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/register/RegisterViewModel.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/register/RegisterScreen.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/ui/auth/register/RegisterViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/nightwatchprotocol/ui/auth/register/RegisterScreenTest.kt`

**Interfaces:**
- Consumes: everything Task 5 produces (`ErrorDialogKind`, `AuthError`, `FakeAuthRepository`, `SessionPreferences.inMemory()`, `MainDispatcherRule`, Task 2's shared components, Task 4's DI).
- Produces: `data class RegisterUiState`, `data class PasswordRules`, `sealed interface RegisterFieldError`, `sealed interface RegisterEvent`, `class RegisterViewModel(authRepository: AuthRepository, sessionPreferences: SessionPreferences) : ViewModel()` (including Google Sign-In handlers `onGoogleIdTokenReceived`/`onGoogleSignInFailed`, matching Login's — the spec requires "Continuar con Google" on both screens), `@Composable fun RegisterScreen(onRegisterSuccess: () -> Unit, onNavigateBack: () -> Unit, viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory))`. Task 8's `NightWatchNavHost` wires both callbacks.

- [ ] **Step 1: Define the UI state**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/register/RegisterUiState.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.register

  data class PasswordRules(
      val hasMinLength: Boolean,
      val hasUpperCaseAndDigit: Boolean,
      val hasSpecialChar: Boolean,
  ) {
      val allSatisfied: Boolean
          get() = hasMinLength && hasUpperCaseAndDigit && hasSpecialChar

      companion object {
          fun from(password: String) = PasswordRules(
              hasMinLength = password.length >= 8,
              hasUpperCaseAndDigit = password.any { it.isUpperCase() } && password.any { it.isDigit() },
              hasSpecialChar = password.any { !it.isLetterOrDigit() },
          )
      }
  }

  data class RegisterUiState(
      val fullName: String = "",
      val email: String = "",
      val password: String = "",
      val confirmPassword: String = "",
      val isLoading: Boolean = false,
      val fieldError: RegisterFieldError? = null,
  ) {
      val passwordRules: PasswordRules
          get() = PasswordRules.from(password)

      val isRegisterEnabled: Boolean
          get() = fullName.isNotBlank() &&
              email.isNotBlank() &&
              passwordRules.allSatisfied &&
              password == confirmPassword &&
              !isLoading
  }

  sealed interface RegisterFieldError {
      data object EmailAlreadyInUse : RegisterFieldError
      data object InvalidEmail : RegisterFieldError
      data object PasswordMismatch : RegisterFieldError
      data object WeakPassword : RegisterFieldError
  }

  sealed interface RegisterEvent {
      data object NavigateToMainMenu : RegisterEvent
      data object NavigateBack : RegisterEvent
      data class ShowErrorDialog(val kind: com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind) : RegisterEvent
  }
  ```

- [ ] **Step 2: Write the failing ViewModel tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/ui/auth/register/RegisterViewModelTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.register

  import app.cash.turbine.test
  import com.example.nightwatchprotocol.MainDispatcherRule
  import com.example.nightwatchprotocol.data.auth.AuthError
  import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
  import com.example.nightwatchprotocol.data.session.SessionPreferences
  import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
  import kotlinx.coroutines.test.runTest
  import org.junit.Assert.assertEquals
  import org.junit.Rule
  import org.junit.Test

  class RegisterViewModelTest {
      @get:Rule
      val mainDispatcherRule = MainDispatcherRule()

      private fun createViewModel(
          fakeAuthRepository: FakeAuthRepository = FakeAuthRepository(),
      ): Pair<RegisterViewModel, FakeAuthRepository> {
          val viewModel = RegisterViewModel(fakeAuthRepository, SessionPreferences.inMemory())
          return viewModel to fakeAuthRepository
      }

      private fun RegisterViewModel.fillValidForm() {
          onFullNameChanged("Nicolás Sotelo")
          onEmailChanged("nicolas@example.com")
          onPasswordChanged("Sup3r$ecret!")
          onConfirmPasswordChanged("Sup3r$ecret!")
      }

      @Test
      fun register_success_navigatesToMainMenu() = runTest {
          val (viewModel, _) = createViewModel()
          viewModel.fillValidForm()

          viewModel.events.test {
              viewModel.onRegisterClicked()
              assertEquals(RegisterEvent.NavigateToMainMenu, awaitItem())
          }
      }

      @Test
      fun register_blankFields_neverCallsRepository() = runTest {
          val (viewModel, fakeRepo) = createViewModel()

          viewModel.onRegisterClicked()

          assertEquals(0, fakeRepo.registerCallCount)
      }

      @Test
      fun register_passwordMismatch_showsPasswordMismatchWithoutCallingRepository() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          viewModel.fillValidForm()
          viewModel.onConfirmPasswordChanged("Different1!")

          viewModel.onRegisterClicked()

          assertEquals(RegisterFieldError.PasswordMismatch, viewModel.uiState.value.fieldError)
          assertEquals(0, fakeRepo.registerCallCount)
      }

      @Test
      fun register_emailAlreadyInUse_showsEmailAlreadyInUseError() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          fakeRepo.registerResult = Result.failure(AuthError.EmailAlreadyInUse)
          viewModel.fillValidForm()

          viewModel.onRegisterClicked()

          assertEquals(RegisterFieldError.EmailAlreadyInUse, viewModel.uiState.value.fieldError)
      }

      @Test
      fun register_invalidEmail_showsInvalidEmailError() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          fakeRepo.registerResult = Result.failure(AuthError.InvalidEmail)
          viewModel.fillValidForm()

          viewModel.onRegisterClicked()

          assertEquals(RegisterFieldError.InvalidEmail, viewModel.uiState.value.fieldError)
      }

      @Test
      fun register_networkError_showsNetworkErrorDialog() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          fakeRepo.registerResult = Result.failure(AuthError.NetworkError)
          viewModel.fillValidForm()

          viewModel.events.test {
              viewModel.onRegisterClicked()
              assertEquals(RegisterEvent.ShowErrorDialog(ErrorDialogKind.NETWORK), awaitItem())
          }
      }

      @Test
      fun register_secondClickWhileLoading_isIgnored() = runTest {
          val (viewModel, fakeRepo) = createViewModel()
          viewModel.fillValidForm()

          viewModel.onRegisterClicked()
          viewModel.onRegisterClicked()

          assertEquals(1, fakeRepo.registerCallCount)
      }

      @Test
      fun passwordRules_allSatisfied_whenAllThreeRulesPass() {
          val rules = PasswordRules.from("Sup3r$ecret!")

          assert(rules.allSatisfied)
      }

      @Test
      fun passwordRules_notSatisfied_whenMissingSpecialChar() {
          val rules = PasswordRules.from("Sup3rSecret1")

          assert(!rules.allSatisfied)
      }
  }
  ```

- [ ] **Step 3: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.auth.register.RegisterViewModelTest"`
  Expected: FAIL to compile — `RegisterViewModel` doesn't exist yet.

- [ ] **Step 4: Implement the ViewModel**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/register/RegisterViewModel.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.register

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import androidx.lifecycle.viewmodel.initializer
  import androidx.lifecycle.viewmodel.viewModelFactory
  import com.example.nightwatchprotocol.NightWatchApplication
  import com.example.nightwatchprotocol.data.auth.AuthError
  import com.example.nightwatchprotocol.data.auth.AuthRepository
  import com.example.nightwatchprotocol.data.session.SessionPreferences
  import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
  import kotlinx.coroutines.channels.Channel
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.receiveAsFlow
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.launch

  class RegisterViewModel(
      private val authRepository: AuthRepository,
      private val sessionPreferences: SessionPreferences,
  ) : ViewModel() {

      private val _uiState = MutableStateFlow(RegisterUiState())
      val uiState = _uiState.asStateFlow()

      private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
      val events: Flow<RegisterEvent> = _events.receiveAsFlow()

      fun onFullNameChanged(value: String) = _uiState.update { it.copy(fullName = value, fieldError = null) }
      fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, fieldError = null) }
      fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, fieldError = null) }
      fun onConfirmPasswordChanged(value: String) =
          _uiState.update { it.copy(confirmPassword = value, fieldError = null) }

      fun onRegisterClicked() {
          val state = _uiState.value
          if (state.isLoading) return
          if (state.fullName.isBlank() || state.email.isBlank() ||
              state.password.isBlank() || state.confirmPassword.isBlank()
          ) {
              return
          }
          if (state.password != state.confirmPassword) {
              _uiState.update { it.copy(fieldError = RegisterFieldError.PasswordMismatch) }
              return
          }
          if (!state.passwordRules.allSatisfied) {
              _uiState.update { it.copy(fieldError = RegisterFieldError.WeakPassword) }
              return
          }

          _uiState.update { it.copy(isLoading = true, fieldError = null) }
          viewModelScope.launch {
              try {
                  authRepository.register(state.fullName.trim(), state.email.trim(), state.password)
                  sessionPreferences.setRememberSession(true)
                  _uiState.update { it.copy(isLoading = false) }
                  _events.send(RegisterEvent.NavigateToMainMenu)
              } catch (error: AuthError) {
                  handleRegisterError(error)
              }
          }
      }

      fun onNavigateBackClicked() {
          viewModelScope.launch { _events.send(RegisterEvent.NavigateBack) }
      }

      fun onGoogleIdTokenReceived(idToken: String) {
          if (_uiState.value.isLoading) return
          _uiState.update { it.copy(isLoading = true) }
          viewModelScope.launch {
              try {
                  authRepository.signInWithGoogle(idToken)
                  sessionPreferences.setRememberSession(true)
                  _uiState.update { it.copy(isLoading = false) }
                  _events.send(RegisterEvent.NavigateToMainMenu)
              } catch (error: AuthError) {
                  _uiState.update { it.copy(isLoading = false) }
                  _events.send(RegisterEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN))
              }
          }
      }

      fun onGoogleSignInFailed() {
          viewModelScope.launch { _events.send(RegisterEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN)) }
      }

      private suspend fun handleRegisterError(error: AuthError) {
          val fieldError = when (error) {
              AuthError.EmailAlreadyInUse -> RegisterFieldError.EmailAlreadyInUse
              AuthError.InvalidEmail -> RegisterFieldError.InvalidEmail
              AuthError.WeakPassword -> RegisterFieldError.WeakPassword
              else -> null
          }
          _uiState.update { it.copy(isLoading = false, fieldError = fieldError) }
          if (fieldError == null) {
              val kind = if (error == AuthError.NetworkError) ErrorDialogKind.NETWORK else ErrorDialogKind.UNKNOWN
              _events.send(RegisterEvent.ShowErrorDialog(kind))
          }
      }

      companion object {
          val Factory: ViewModelProvider.Factory = viewModelFactory {
              initializer {
                  val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                  RegisterViewModel(application.container.authRepository, application.container.sessionPreferences)
              }
          }
      }
  }
  ```

- [ ] **Step 5: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.auth.register.RegisterViewModelTest"`
  Expected: `9 tests completed, 0 failed`.

- [ ] **Step 6: Build the screen**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/register/RegisterScreen.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.register

  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Check
  import androidx.compose.material.icons.filled.Close
  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.collectAsState
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.runtime.rememberCoroutineScope
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.unit.dp
  import androidx.credentials.CredentialManager
  import androidx.credentials.CustomCredential
  import androidx.credentials.GetCredentialRequest
  import androidx.credentials.exceptions.GetCredentialException
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.example.nightwatchprotocol.R
  import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
  import com.example.nightwatchprotocol.ui.components.GoogleSignInButton
  import com.example.nightwatchprotocol.ui.components.NightWatchButton
  import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
  import com.example.nightwatchprotocol.ui.components.NightWatchTextField
  import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
  import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
  import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
  import kotlinx.coroutines.launch

  @Composable
  fun RegisterScreen(
      onRegisterSuccess: () -> Unit,
      onNavigateBack: () -> Unit,
      viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
  ) {
      val uiState by viewModel.uiState.collectAsState()
      val context = LocalContext.current
      val coroutineScope = rememberCoroutineScope()
      var dialogKind by remember { mutableStateOf<ErrorDialogKind?>(null) }

      LaunchedEffect(viewModel) {
          viewModel.events.collect { event ->
              when (event) {
                  RegisterEvent.NavigateToMainMenu -> onRegisterSuccess()
                  RegisterEvent.NavigateBack -> onNavigateBack()
                  is RegisterEvent.ShowErrorDialog -> dialogKind = event.kind
              }
          }
      }

      RegisterScreenContent(
          uiState = uiState,
          onFullNameChanged = viewModel::onFullNameChanged,
          onEmailChanged = viewModel::onEmailChanged,
          onPasswordChanged = viewModel::onPasswordChanged,
          onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
          onRegisterClicked = viewModel::onRegisterClicked,
          onCloseClicked = viewModel::onNavigateBackClicked,
          onGoogleSignInClicked = {
              coroutineScope.launch {
                  val credentialManager = CredentialManager.create(context)
                  val option = GetSignInWithGoogleOption.Builder(
                      serverClientId = context.getString(R.string.default_web_client_id),
                  ).build()
                  val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                  try {
                      val result = credentialManager.getCredential(context, request)
                      val credential = result.credential
                      if (credential is CustomCredential &&
                          credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                      ) {
                          val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                          viewModel.onGoogleIdTokenReceived(googleIdTokenCredential.idToken)
                      } else {
                          viewModel.onGoogleSignInFailed()
                      }
                  } catch (e: GetCredentialException) {
                      viewModel.onGoogleSignInFailed()
                  }
              }
          },
      )

      dialogKind?.let { kind ->
          AlertDialog(
              onDismissRequest = { dialogKind = null },
              title = { Text("Error de registro") },
              text = {
                  Text(
                      if (kind == ErrorDialogKind.NETWORK) {
                          "Sin conexión a internet. Verifica tu conexión e intenta de nuevo."
                      } else {
                          "Se produjo un error inesperado. Intenta registrarte de nuevo."
                      },
                  )
              },
              confirmButton = { TextButton(onClick = { dialogKind = null }) { Text("Aceptar") } },
          )
      }
  }

  @Composable
  private fun RegisterScreenContent(
      uiState: RegisterUiState,
      onFullNameChanged: (String) -> Unit,
      onEmailChanged: (String) -> Unit,
      onPasswordChanged: (String) -> Unit,
      onConfirmPasswordChanged: (String) -> Unit,
      onRegisterClicked: () -> Unit,
      onCloseClicked: () -> Unit,
      onGoogleSignInClicked: () -> Unit,
  ) {
      NightWatchScreenBackground {
          Column(modifier = Modifier.fillMaxSize()) {
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(180.dp)
                      .background(NightWatchHeaderPanel, RoundedCornerShape(bottomStart = 60.dp, bottomEnd = 60.dp)),
              ) {
                  Column(modifier = Modifier.padding(24.dp)) {
                      Text("Registro de Usuario", style = MaterialTheme.typography.titleLarge, color = Color.Black)
                      Text(
                          "Ingrese los datos requeridos a continuación",
                          color = Color.Black,
                          modifier = Modifier.padding(top = 4.dp),
                      )
                  }
                  IconButton(onClick = onCloseClicked, modifier = Modifier.align(Alignment.TopEnd)) {
                      Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.Black)
                  }
              }
              Column(
                  modifier = Modifier
                      .fillMaxSize()
                      .padding(horizontal = 24.dp, vertical = 16.dp),
              ) {
                  NightWatchTextField(
                      label = "Nombre Completo",
                      value = uiState.fullName,
                      onValueChange = onFullNameChanged,
                  )
                  NightWatchTextField(
                      label = "Correo electrónico",
                      value = uiState.email,
                      onValueChange = onEmailChanged,
                      isError = uiState.fieldError == RegisterFieldError.EmailAlreadyInUse ||
                          uiState.fieldError == RegisterFieldError.InvalidEmail,
                      supportingText = when (uiState.fieldError) {
                          RegisterFieldError.EmailAlreadyInUse -> "Ya tienes una cuenta con este correo"
                          RegisterFieldError.InvalidEmail -> "Correo electrónico inválido"
                          else -> null
                      },
                      modifier = Modifier.padding(top = 12.dp),
                  )
                  NightWatchTextField(
                      label = "Contraseña",
                      value = uiState.password,
                      onValueChange = onPasswordChanged,
                      isPassword = true,
                      modifier = Modifier.padding(top = 12.dp),
                  )
                  NightWatchTextField(
                      label = "Verifica la contraseña",
                      value = uiState.confirmPassword,
                      onValueChange = onConfirmPasswordChanged,
                      isPassword = true,
                      isError = uiState.fieldError == RegisterFieldError.PasswordMismatch,
                      supportingText = if (uiState.fieldError == RegisterFieldError.PasswordMismatch) {
                          "Contraseña no coincide"
                      } else {
                          null
                      },
                      modifier = Modifier.padding(top = 12.dp),
                  )
                  Column(modifier = Modifier.padding(top = 16.dp)) {
                      PasswordRuleRow("Mínimo 8 caracteres", uiState.passwordRules.hasMinLength)
                      PasswordRuleRow("Incluye una mayúscula y un número", uiState.passwordRules.hasUpperCaseAndDigit)
                      PasswordRuleRow("Incluye al menos un carácter especial", uiState.passwordRules.hasSpecialChar)
                  }
                  NightWatchButton(
                      text = "REGISTRARSE",
                      onClick = onRegisterClicked,
                      enabled = uiState.isRegisterEnabled,
                      modifier = Modifier.padding(top = 24.dp),
                  )
                  Text(
                      "O",
                      color = NightWatchOnBackground,
                      modifier = Modifier.padding(vertical = 8.dp),
                  )
                  GoogleSignInButton(onClick = onGoogleSignInClicked, enabled = !uiState.isLoading)
              }
          }
      }
  }

  @Composable
  private fun PasswordRuleRow(text: String, satisfied: Boolean) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(vertical = 2.dp),
      ) {
          Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              tint = if (satisfied) Color(0xFF4CAF50) else NightWatchOnBackground.copy(alpha = 0.4f),
          )
          Text(text, color = NightWatchOnBackground)
      }
  }
  ```

- [ ] **Step 7: Write the failing Compose UI test**

  Create `app/src/androidTest/java/com/example/nightwatchprotocol/ui/auth/register/RegisterScreenTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.register

  import androidx.compose.ui.test.assertIsEnabled
  import androidx.compose.ui.test.assertIsNotEnabled
  import androidx.compose.ui.test.junit4.createComposeRule
  import androidx.compose.ui.test.onNodeWithTag
  import androidx.compose.ui.test.onNodeWithText
  import androidx.compose.ui.test.performTextInput
  import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
  import org.junit.Rule
  import org.junit.Test

  class RegisterScreenTest {
      @get:Rule
      val composeTestRule = createComposeRule()

      @Test
      fun registerButton_disabledUntilAllPasswordRulesAndMatchAreSatisfied() {
          composeTestRule.setContent {
              NightWatchProtocolTheme {
                  RegisterScreenContentForTest()
              }
          }

          composeTestRule.onNodeWithText("REGISTRARSE").assertIsNotEnabled()

          composeTestRule.onNodeWithTag("Nombre Completo").performTextInput("Nicolás Sotelo")
          composeTestRule.onNodeWithTag("Correo electrónico").performTextInput("nicolas@example.com")
          composeTestRule.onNodeWithTag("Contraseña").performTextInput("Sup3r$ecret!")
          composeTestRule.onNodeWithTag("Verifica la contraseña").performTextInput("Sup3r$ecret!")

          composeTestRule.onNodeWithText("REGISTRARSE").assertIsEnabled()
      }

      @Test
      fun passwordChecklist_ticksOffAsRulesAreSatisfied() {
          composeTestRule.setContent {
              NightWatchProtocolTheme {
                  RegisterScreenContentForTest()
              }
          }

          composeTestRule.onNodeWithTag("Contraseña").performTextInput("short")

          composeTestRule.onNodeWithText("Mínimo 8 caracteres").assertExists()
      }
  }
  ```

  This calls a `RegisterScreenContentForTest()` helper that doesn't exist yet — `RegisterScreenContent` is `private`, and a real test shouldn't depend on a live `RegisterViewModel` (which needs `NightWatchApplication`). Add a small internal (not `private`) wrapper next to `RegisterScreenContent` in `RegisterScreen.kt` for exactly this purpose:

  ```kotlin
  @Composable
  internal fun RegisterScreenContentForTest() {
      var uiState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(RegisterUiState()) }
      RegisterScreenContent(
          uiState = uiState,
          onFullNameChanged = { uiState = uiState.copy(fullName = it) },
          onEmailChanged = { uiState = uiState.copy(email = it) },
          onPasswordChanged = { uiState = uiState.copy(password = it) },
          onConfirmPasswordChanged = { uiState = uiState.copy(confirmPassword = it) },
          onRegisterClicked = {},
          onCloseClicked = {},
          onGoogleSignInClicked = {},
      )
  }
  ```

- [ ] **Step 8: Run the test to verify it fails**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.ui.auth.register.RegisterScreenTest"`
  Expected: FAIL to compile until Step 7's `RegisterScreenContentForTest` helper is added (do Step 7 in full before running this).

- [ ] **Step 9: Run the test to verify it passes**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.ui.auth.register.RegisterScreenTest"`
  Expected: `2 tests completed, 0 failed`.

- [ ] **Step 10: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/auth/register app/src/test/java/com/example/nightwatchprotocol/ui/auth/register app/src/androidTest/java/com/example/nightwatchprotocol/ui/auth/register
  git commit -m "feat: add Register screen with live password validation checklist"
  ```

---

### Task 7: Forgot Password screen

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordUiState.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordViewModel.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordScreen.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`/`AuthError` (Task 3/4), `FakeAuthRepository` (Task 4), `MainDispatcherRule` (Task 5), `NightWatchTextField`/`NightWatchButton`/`NightWatchScreenBackground` (Task 2).
- Produces: `data class ForgotPasswordUiState`, `class ForgotPasswordViewModel(authRepository: AuthRepository) : ViewModel()`, `@Composable fun ForgotPasswordScreen(onNavigateBack: () -> Unit, viewModel: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.Factory))`. Task 8's `NightWatchNavHost` wires `onNavigateBack`.

- [ ] **Step 1: Define the UI state**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordUiState.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.forgotpassword

  data class ForgotPasswordUiState(
      val email: String = "",
      val isLoading: Boolean = false,
      val isEmailSent: Boolean = false,
      val invalidEmailError: Boolean = false,
      val networkError: Boolean = false,
  )
  ```

- [ ] **Step 2: Write the failing ViewModel tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordViewModelTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.forgotpassword

  import com.example.nightwatchprotocol.MainDispatcherRule
  import com.example.nightwatchprotocol.data.auth.AuthError
  import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
  import kotlinx.coroutines.test.advanceUntilIdle
  import kotlinx.coroutines.test.runTest
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertTrue
  import org.junit.Rule
  import org.junit.Test

  class ForgotPasswordViewModelTest {
      @get:Rule
      val mainDispatcherRule = MainDispatcherRule()

      @Test
      fun send_success_marksEmailSent() = runTest {
          val fakeRepo = FakeAuthRepository()
          val viewModel = ForgotPasswordViewModel(fakeRepo)
          viewModel.onEmailChanged("user@example.com")

          viewModel.onSendClicked()
          advanceUntilIdle()

          assertTrue(viewModel.uiState.value.isEmailSent)
          assertEquals(1, fakeRepo.sendPasswordResetEmailCallCount)
      }

      @Test
      fun send_userNotFound_stillMarksEmailSent_toAvoidLeakingAccountExistence() = runTest {
          val fakeRepo = FakeAuthRepository()
          fakeRepo.sendPasswordResetEmailResult = Result.failure(AuthError.UserNotFound)
          val viewModel = ForgotPasswordViewModel(fakeRepo)
          viewModel.onEmailChanged("nobody@example.com")

          viewModel.onSendClicked()
          advanceUntilIdle()

          assertTrue(viewModel.uiState.value.isEmailSent)
      }

      @Test
      fun send_invalidEmail_showsInvalidEmailErrorWithoutClaimingSent() = runTest {
          val fakeRepo = FakeAuthRepository()
          fakeRepo.sendPasswordResetEmailResult = Result.failure(AuthError.InvalidEmail)
          val viewModel = ForgotPasswordViewModel(fakeRepo)
          viewModel.onEmailChanged("not-an-email")

          viewModel.onSendClicked()
          advanceUntilIdle()

          assertTrue(viewModel.uiState.value.invalidEmailError)
          assertEquals(false, viewModel.uiState.value.isEmailSent)
      }

      @Test
      fun send_networkError_showsNetworkErrorWithoutClaimingSent() = runTest {
          val fakeRepo = FakeAuthRepository()
          fakeRepo.sendPasswordResetEmailResult = Result.failure(AuthError.NetworkError)
          val viewModel = ForgotPasswordViewModel(fakeRepo)
          viewModel.onEmailChanged("user@example.com")

          viewModel.onSendClicked()
          advanceUntilIdle()

          assertTrue(viewModel.uiState.value.networkError)
          assertEquals(false, viewModel.uiState.value.isEmailSent)
      }

      @Test
      fun send_blankEmail_neverCallsRepository() = runTest {
          val fakeRepo = FakeAuthRepository()
          val viewModel = ForgotPasswordViewModel(fakeRepo)

          viewModel.onSendClicked()
          advanceUntilIdle()

          assertEquals(0, fakeRepo.sendPasswordResetEmailCallCount)
      }
  }
  ```

- [ ] **Step 3: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.auth.forgotpassword.ForgotPasswordViewModelTest"`
  Expected: FAIL to compile — `ForgotPasswordViewModel` doesn't exist yet.

- [ ] **Step 4: Implement the ViewModel**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordViewModel.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.forgotpassword

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import androidx.lifecycle.viewmodel.initializer
  import androidx.lifecycle.viewmodel.viewModelFactory
  import com.example.nightwatchprotocol.NightWatchApplication
  import com.example.nightwatchprotocol.data.auth.AuthError
  import com.example.nightwatchprotocol.data.auth.AuthRepository
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.launch

  class ForgotPasswordViewModel(
      private val authRepository: AuthRepository,
  ) : ViewModel() {

      private val _uiState = MutableStateFlow(ForgotPasswordUiState())
      val uiState = _uiState.asStateFlow()

      fun onEmailChanged(value: String) {
          _uiState.update {
              it.copy(email = value, invalidEmailError = false, networkError = false, isEmailSent = false)
          }
      }

      fun onSendClicked() {
          val state = _uiState.value
          if (state.isLoading || state.email.isBlank()) return

          _uiState.update { it.copy(isLoading = true, invalidEmailError = false, networkError = false) }
          viewModelScope.launch {
              try {
                  authRepository.sendPasswordResetEmail(state.email.trim())
                  _uiState.update { it.copy(isLoading = false, isEmailSent = true) }
              } catch (error: AuthError) {
                  when (error) {
                      AuthError.InvalidEmail -> _uiState.update { it.copy(isLoading = false, invalidEmailError = true) }
                      AuthError.NetworkError -> _uiState.update { it.copy(isLoading = false, networkError = true) }
                      else -> _uiState.update { it.copy(isLoading = false, isEmailSent = true) }
                  }
              }
          }
      }

      companion object {
          val Factory: ViewModelProvider.Factory = viewModelFactory {
              initializer {
                  val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                  ForgotPasswordViewModel(application.container.authRepository)
              }
          }
      }
  }
  ```

  The `else -> ... isEmailSent = true` branch is deliberate: per the spec, `sendPasswordResetEmail` must never reveal whether an account exists, so any failure other than a client-input problem (invalid email format) or a network failure is treated the same as success from the user's point of view.

- [ ] **Step 5: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.auth.forgotpassword.ForgotPasswordViewModelTest"`
  Expected: `5 tests completed, 0 failed`.

- [ ] **Step 6: Build the screen**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/auth/forgotpassword/ForgotPasswordScreen.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.auth.forgotpassword

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.wrapContentSize
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.collectAsState
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.example.nightwatchprotocol.ui.components.NightWatchButton
  import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
  import com.example.nightwatchprotocol.ui.components.NightWatchTextField
  import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

  @Composable
  fun ForgotPasswordScreen(
      onNavigateBack: () -> Unit,
      viewModel: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.Factory),
  ) {
      val uiState by viewModel.uiState.collectAsState()

      NightWatchScreenBackground {
          Column(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 32.dp)
                  .wrapContentSize(Alignment.Center),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
              Text(
                  "Recuperación de contraseña",
                  style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                  color = NightWatchOnBackground,
              )
              Text(
                  text = if (uiState.isEmailSent) {
                      "Si el correo existe, te enviamos un enlace para restablecer tu contraseña. Revisa tu bandeja de entrada."
                  } else {
                      "Ingresa tu correo electrónico para recibir un enlace de restablecimiento."
                  },
                  color = NightWatchOnBackground,
                  modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
              )
              if (!uiState.isEmailSent) {
                  NightWatchTextField(
                      label = "Correo electrónico",
                      value = uiState.email,
                      onValueChange = viewModel::onEmailChanged,
                      isError = uiState.invalidEmailError,
                      supportingText = when {
                          uiState.invalidEmailError -> "Correo electrónico inválido"
                          uiState.networkError -> "Sin conexión a internet"
                          else -> null
                      },
                  )
                  NightWatchButton(
                      text = "Confirmar",
                      onClick = viewModel::onSendClicked,
                      enabled = !uiState.isLoading,
                      modifier = Modifier.padding(top = 16.dp),
                  )
              }
              Text(
                  "Volver al menú",
                  color = NightWatchLinkAccent,
                  modifier = Modifier
                      .padding(top = 16.dp)
                      .clickable(onClick = onNavigateBack),
              )
          }
      }
  }
  ```

- [ ] **Step 7: Build the module to catch compile errors**

  Run: `./gradlew :app:compileDebugKotlin`
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/auth/forgotpassword app/src/test/java/com/example/nightwatchprotocol/ui/auth/forgotpassword
  git commit -m "feat: add Forgot Password screen with email-enumeration-safe messaging"
  ```

---

### Task 8: Navigation graph, Main Menu, and end-to-end wiring

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchNavHost.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/mainmenu/MainMenuScreen.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/MainActivity.kt`
- Test: `app/src/androidTest/java/com/example/nightwatchprotocol/ui/navigation/NightWatchNavHostTest.kt`

**Interfaces:**
- Consumes: `LoginScreen` (Task 5), `RegisterScreen` (Task 6), `ForgotPasswordScreen` (Task 7), `NightWatchProtocolTheme` (Task 2), `AppContainer`/`NightWatchApplication` (Task 4).
- Produces: the assembled app entry point — nothing later in this sub-project depends on this task, but the next sub-project's Main Menu work replaces `MainMenuScreen`.

- [ ] **Step 1: Define the routes**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.navigation

  import kotlinx.serialization.Serializable

  @Serializable
  object Login

  @Serializable
  object Register

  @Serializable
  object ForgotPassword

  @Serializable
  object MainMenu
  ```

- [ ] **Step 2: Build the Main Menu stub**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/mainmenu/MainMenuScreen.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.mainmenu

  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.wrapContentSize
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.unit.dp
  import com.example.nightwatchprotocol.NightWatchApplication
  import com.example.nightwatchprotocol.ui.components.NightWatchButton
  import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

  @Composable
  fun MainMenuScreen(onSignOut: () -> Unit) {
      val context = LocalContext.current
      val application = context.applicationContext as NightWatchApplication
      val userEmail = application.container.authRepository.currentUserEmail ?: "usuario"

      NightWatchScreenBackground {
          Column(
              modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
              Text("Sesión iniciada como $userEmail", color = NightWatchOnBackground)
              NightWatchButton(
                  text = "Cerrar sesión",
                  onClick = {
                      application.container.authRepository.signOut()
                      onSignOut()
                  },
                  modifier = Modifier.padding(top = 24.dp),
              )
          }
      }
  }
  ```

  This is a deliberate placeholder — the real Main Menu is a future sub-project (see spec).

- [ ] **Step 3: Wire the NavHost**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchNavHost.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.navigation

  import androidx.compose.runtime.Composable
  import androidx.navigation.NavHostController
  import androidx.navigation.compose.NavHost
  import androidx.navigation.compose.composable
  import androidx.navigation.compose.rememberNavController
  import com.example.nightwatchprotocol.ui.auth.forgotpassword.ForgotPasswordScreen
  import com.example.nightwatchprotocol.ui.auth.login.LoginScreen
  import com.example.nightwatchprotocol.ui.auth.register.RegisterScreen
  import com.example.nightwatchprotocol.ui.mainmenu.MainMenuScreen

  @Composable
  fun NightWatchNavHost(navController: NavHostController = rememberNavController()) {
      NavHost(navController = navController, startDestination = Login) {
          composable<Login> {
              LoginScreen(
                  onLoginSuccess = {
                      navController.navigate(MainMenu) { popUpTo<Login> { inclusive = true } }
                  },
                  onNavigateToRegister = { navController.navigate(Register) },
                  onNavigateToForgotPassword = { navController.navigate(ForgotPassword) },
              )
          }
          composable<Register> {
              RegisterScreen(
                  onRegisterSuccess = {
                      navController.navigate(MainMenu) { popUpTo<Login> { inclusive = true } }
                  },
                  onNavigateBack = { navController.popBackStack() },
              )
          }
          composable<ForgotPassword> {
              ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
          }
          composable<MainMenu> {
              MainMenuScreen(
                  onSignOut = {
                      navController.navigate(Login) { popUpTo<MainMenu> { inclusive = true } }
                  },
              )
          }
      }
  }
  ```

- [ ] **Step 4: Wire MainActivity**

  Replace the entire contents of `app/src/main/java/com/example/nightwatchprotocol/MainActivity.kt`:

  ```kotlin
  package com.example.nightwatchprotocol

  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.activity.enableEdgeToEdge
  import com.example.nightwatchprotocol.ui.navigation.NightWatchNavHost
  import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme

  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          enableEdgeToEdge()
          setContent {
              NightWatchProtocolTheme {
                  NightWatchNavHost()
              }
          }
      }
  }
  ```

  This deletes the template's `Greeting`/`GreetingPreview` — delete `app/src/test/java/com/example/nightwatchprotocol/ExampleUnitTest.kt` and `app/src/androidTest/java/com/example/nightwatchprotocol/ExampleInstrumentedTest.kt` too if either references `Greeting`; if they're still the unmodified template placeholders (`assertEquals(4, 2 + 2)` and a package-name check), leave them — they're harmless and out of this plan's scope either way.

- [ ] **Step 5: Write the failing navigation test**

  Create `app/src/androidTest/java/com/example/nightwatchprotocol/ui/navigation/NightWatchNavHostTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.navigation

  import androidx.compose.ui.test.assertExists
  import androidx.compose.ui.test.junit4.createAndroidComposeRule
  import androidx.compose.ui.test.onNodeWithText
  import androidx.compose.ui.test.performClick
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import com.example.nightwatchprotocol.MainActivity
  import org.junit.Rule
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class NightWatchNavHostTest {
      @get:Rule
      val composeTestRule = createAndroidComposeRule<MainActivity>()

      @Test
      fun startDestinationIsLogin() {
          composeTestRule.onNodeWithText("INICIAR SESIÓN").assertExists()
      }

      @Test
      fun registerLink_navigatesToRegisterScreen_andBackReturnsToLogin() {
          composeTestRule.onNodeWithText("Regístrate aquí").performClick()

          composeTestRule.onNodeWithText("Registro de Usuario").assertExists()

          composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

          composeTestRule.onNodeWithText("INICIAR SESIÓN").assertExists()
      }
  }
  ```

  This test requires `app/google-services.json` from Task 1 to be present — `MainActivity`'s `NightWatchApplication` initializes `FirebaseAuth.getInstance()` at startup, which needs a real (even if unconfigured-for-this-test) Firebase project to exist.

- [ ] **Step 6: Run the test to verify it fails**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.ui.navigation.NightWatchNavHostTest"`
  Expected: FAIL — `NightWatchNavHost`/`MainActivity` wiring from Steps 3-4 not yet in place (run this before Steps 3-4 if following strict TDD ordering; since Steps 3-4 are themselves plain wiring with no separate test of their own, it's acceptable to do Steps 3-4 first and treat this as the confirming test — either order satisfies the spirit of TDD here because there is no intermediate implementation to under-build).

- [ ] **Step 7: Run the test to verify it passes**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.ui.navigation.NightWatchNavHostTest"`
  Expected: `2 tests completed, 0 failed`.

- [ ] **Step 8: Run the entire test suite**

  Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
  Expected: `BUILD SUCCESSFUL` — every test from Tasks 2 through 8 passes together.

- [ ] **Step 9: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/navigation app/src/main/java/com/example/nightwatchprotocol/ui/mainmenu app/src/main/java/com/example/nightwatchprotocol/MainActivity.kt app/src/androidTest/java/com/example/nightwatchprotocol/ui/navigation
  git commit -m "feat: wire Login/Register/ForgotPassword/MainMenu into a navigation graph"
  ```
