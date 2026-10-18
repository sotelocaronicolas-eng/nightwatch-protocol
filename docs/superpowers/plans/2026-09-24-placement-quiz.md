# Placement Quiz & Basic Study Plan Implementation Plan

> **Documento histórico.** Describe el proyecto cuando usaba Firebase (versión 1.x). La app hoy usa solo Supabase: ver
> [`migracion-supabase.md`](../reports/migracion-supabase.md).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After first login/register, show a scored placement quiz (BAJO/MEDIO/ALTO) with a basic per-level study plan, persisted per-user in Firestore so returning users skip straight to Main Menu.

**Architecture:** Same MVVM pattern as Auth (stateful `XScreen` + stateless `XScreenContent`, `StateFlow` UI state, `Channel`-based one-shot events, manual DI via `AppContainer`). A new `PostAuthGate` screen sits between successful auth and Main Menu, doing one Firestore read to decide whether to route to the quiz or straight through. Quiz content (20 questions) ships bundled as a local JSON asset; scoring and study-plan selection are pure functions with no Android/Firebase dependency; only the quiz *result* (level + weak categories) is persisted, to Firestore.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose, `firebase-firestore` (added to the existing `firebase-bom`), `kotlinx.serialization` (JSON asset parsing + type-safe nav routes, both already in the project), JUnit4 + kotlinx-coroutines-test + Turbine + Compose UI testing (all already in the project from the Auth sub-project).

**Spec:** `docs/superpowers/specs/2026-09-24-placement-quiz-design.md`

## Global Constraints

- Same Kotlin/AGP/SDK versions as the rest of the project (Kotlin 2.2.10, AGP 9.3.3, compileSdk/targetSdk 37, minSdk 24) — unchanged.
- No Hilt — dependency injection stays a hand-written `AppContainer` (course convention, matching Auth).
- Every screen is a stateful `XScreen` (owns the `ViewModel`) plus a stateless `XScreenContent` (pure function of state + lambdas).
- Question content ships bundled in the APK (`app/src/main/assets/quiz_questions.json`); changing it requires a new build — accepted trade-off (see spec's Scope).
- No instrumented test talks to real Firestore. `FirestoreQuizResultRepository` is exercised only indirectly, through the interface contract its fake mirrors — same rule the Auth sub-project applied to `FirebaseAuthRepository`. Any screen whose ViewModel makes a real Firestore call on load (only `PostAuthGateScreen`) is covered by ViewModel unit tests against fakes, not a Compose UI test.
- Firestore must be enabled in the Firebase console and its security rules restricted to per-user access before Task 4's manual step; this is a human-partner action (interactive console login), the same way enabling Auth providers was in the Auth sub-project.
- No retake UI, no navigable Core Learning content, and no backend-editable question bank in this sub-project (see spec's Scope — all deferred).

## Review Focus

- **Firestore read failure in `PostAuthGate`** (offline, transient error). A reasonable person expects to reach the quiz (and eventually Main Menu) regardless, never get stuck on a spinner. Covered by Task 5's `firestoreReadFails_failsOpenToPlacementQuiz` test.
- **Firestore save failure at the end of the quiz.** The user just spent several minutes answering 11 questions; losing that to a network blip and getting stuck is unacceptable. Covered by Task 6's `quizResultSaveFails_stillNavigatesToResult_withSaveFailedTrue` test, and the result screen surfaces this to the user (Task 7).
- **The randomized "one category gets 2 questions instead of 3" draw.** A reasonable person expects every attempt to have exactly 11 questions with every category represented — never a category skipped, never more than 11 total. Covered by Task 2's `QuizAttemptSelectorTest` running the draw across 50 random seeds.
- **A user reaching `PostAuthGate`/quiz screens with no signed-in UID** (a defensive edge case — e.g. a stale nav state). Must not NPE or crash; should route to Main Menu, which already tolerates a null email. Covered by Task 5's `nullUid_navigatesToMainMenuWithoutCallingRepository` test.
- **Score boundary values** (exactly 40%, 41%, 75%, 76%). Since the level directly gates what study plan a user sees, an off-by-one in the range comparison silently mis-levels people right at the edges. Covered by Task 3's `PlacementScorerTest` boundary tests.

---

### Task 1: Firestore dependency & `currentUserUid`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthRepository.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/data/auth/FirebaseAuthRepository.kt`
- Modify: `app/src/test/java/com/example/nightwatchprotocol/data/auth/FakeAuthRepository.kt`

**Interfaces:**
- Consumes: nothing (first task of this sub-project).
- Produces: `firebase-firestore` on the `app` module's classpath; `AuthRepository.currentUserUid: String?` (and its `FirebaseAuthRepository`/`FakeAuthRepository` implementations) — every later task that touches Firestore keys its document path off this.

- [ ] **Step 1 (human partner action — cannot be automated): Enable Cloud Firestore**

  This requires an interactive login to the Firebase console, which no agent can perform. Do this yourself, then hand control back:
  1. Open the same Firebase project used for Auth (console → your project).
  2. In the left sidebar, go to **Build → Firestore Database** → **Create database**.
  3. Choose a location (any region close to you is fine — it can't be changed later, but this project has no user base yet) and start in **production mode** (we'll set explicit rules in Task 4, not rely on the test-mode default that's open to everyone).
  4. Confirm creation. No further console action is needed until Task 4's security-rules step.

- [ ] **Step 2: Add the Firestore version-catalog entry**

  Edit `gradle/libs.versions.toml`, adding this line to `[libraries]` (right after the existing `firebase-auth` line):

  ```toml
  firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
  ```

  (No new `[versions]` entry needed — it resolves through the existing `firebase-bom` platform, same as `firebase-auth`.)

- [ ] **Step 3: Add the dependency**

  Edit `app/build.gradle.kts`, adding this line right after `implementation(libs.firebase.auth)`:

  ```kotlin
      implementation(libs.firebase.firestore)
  ```

- [ ] **Step 4: Add `currentUserUid` to the repository contract**

  Edit `app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.auth

  interface AuthRepository {
      val currentUserEmail: String?
      val currentUserUid: String?
      suspend fun signIn(email: String, password: String)
      suspend fun register(name: String, email: String, password: String)
      suspend fun sendPasswordResetEmail(email: String)
      suspend fun signInWithGoogle(idToken: String)
      fun signOut()
  }
  ```

- [ ] **Step 5: Implement it in `FirebaseAuthRepository`**

  Edit `app/src/main/java/com/example/nightwatchprotocol/data/auth/FirebaseAuthRepository.kt`, adding this property right after `currentUserEmail`:

  ```kotlin
      override val currentUserUid: String?
          get() = firebaseAuth.currentUser?.uid
  ```

- [ ] **Step 6: Implement it in `FakeAuthRepository`**

  Edit `app/src/test/java/com/example/nightwatchprotocol/data/auth/FakeAuthRepository.kt`, adding this property right after `currentUserEmail` (as a `var`, not `val` — later tests need to set it to `null` to exercise the defensive no-UID path):

  ```kotlin
      override var currentUserUid: String? = "test-uid"
  ```

- [ ] **Step 7: Verify the project still builds**

  Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

  ```bash
  git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/nightwatchprotocol/data/auth/AuthRepository.kt app/src/main/java/com/example/nightwatchprotocol/data/auth/FirebaseAuthRepository.kt app/src/test/java/com/example/nightwatchprotocol/data/auth/FakeAuthRepository.kt
  git commit -m "build: add Firestore dependency and expose currentUserUid on AuthRepository"
  ```

---

### Task 2: Quiz question bank & content repository

**Files:**
- Create: `app/src/main/assets/quiz_questions.json`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizCategory.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizQuestion.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizAttemptSelector.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizContentRepository.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/LocalQuizContentRepository.kt`
- Create: `app/src/test/java/com/example/nightwatchprotocol/data/quiz/FakeQuizContentRepository.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/data/quiz/QuizAttemptSelectorTest.kt`
- Test: `app/src/androidTest/java/com/example/nightwatchprotocol/data/quiz/LocalQuizContentRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing beyond the Kotlin/coroutines/serialization already in the project.
- Produces: `enum class QuizCategory { CONCEPTOS_BASICOS, CONTRASENAS, INGENIERIA_SOCIAL, REDES_MALWARE }`; `data class QuizQuestion(id: String, category: QuizCategory, difficulty: Int, text: String, options: List<String>, correctIndex: Int)`; `fun selectQuizAttempt(bank: List<QuizQuestion>, random: Random = Random.Default): List<QuizQuestion>`; `interface QuizContentRepository { suspend fun getAttempt(): List<QuizQuestion> }`; `class LocalQuizContentRepository(context: Context) : QuizContentRepository`; `class FakeQuizContentRepository(attempt: List<QuizQuestion> = sampleAttempt()) : QuizContentRepository` and `fun sampleAttempt(): List<QuizQuestion>` (11 fixed questions, `correctIndex = 0` on every one, for deterministic ViewModel tests). Task 6's `PlacementQuizViewModel` is the main consumer.

- [ ] **Step 1: Author the question bank JSON**

  Create `app/src/main/assets/quiz_questions.json` (this needs the `assets` folder created — Android Studio/the write tool creates intermediate directories automatically):

  ```json
  [
    {
      "id": "cb1",
      "category": "CONCEPTOS_BASICOS",
      "difficulty": 1,
      "text": "¿Qué representa la sigla CIA en el contexto de seguridad de la información?",
      "options": ["Confidencialidad, Integridad y Disponibilidad", "Control, Inspección y Auditoría", "Cifrado, Identidad y Autenticación", "Confianza, Integridad y Anonimato"],
      "correctIndex": 0
    },
    {
      "id": "cb2",
      "category": "CONCEPTOS_BASICOS",
      "difficulty": 2,
      "text": "¿Cuál es la diferencia principal entre una vulnerabilidad y una amenaza?",
      "options": ["Una vulnerabilidad es una debilidad explotable; una amenaza es lo que podría explotarla", "Son sinónimos y se usan indistintamente", "Una amenaza es un tipo de malware; una vulnerabilidad es un firewall mal configurado", "Una vulnerabilidad solo existe en software; una amenaza solo existe en hardware"],
      "correctIndex": 0
    },
    {
      "id": "cb3",
      "category": "CONCEPTOS_BASICOS",
      "difficulty": 2,
      "text": "¿Qué principio de seguridad establece que un usuario o proceso debe tener solo los permisos mínimos necesarios para realizar su tarea?",
      "options": ["Defensa en profundidad", "Principio de mínimo privilegio", "Separación de funciones", "No repudio"],
      "correctIndex": 1
    },
    {
      "id": "cb4",
      "category": "CONCEPTOS_BASICOS",
      "difficulty": 3,
      "text": "¿Qué es una vulnerabilidad de día cero (zero-day)?",
      "options": ["Una falla de seguridad conocida públicamente desde hace más de un año", "Una vulnerabilidad que ya tiene un parche oficial disponible", "Una falla desconocida por el fabricante, sin parche disponible al momento de ser explotada", "Un error de configuración detectado por un antivirus"],
      "correctIndex": 2
    },
    {
      "id": "cb5",
      "category": "CONCEPTOS_BASICOS",
      "difficulty": 2,
      "text": "¿Qué es la 'defensa en profundidad' (defense in depth)?",
      "options": ["Usar una sola herramienta de seguridad muy robusta", "Aplicar múltiples capas de controles de seguridad independientes", "Cifrar únicamente la capa de red", "Confiar solo en la seguridad perimetral del firewall"],
      "correctIndex": 1
    },
    {
      "id": "pw1",
      "category": "CONTRASENAS",
      "difficulty": 1,
      "text": "¿Por qué una frase de contraseña larga (passphrase) suele ser más segura que una contraseña corta con símbolos?",
      "options": ["Porque tiene mayor entropía al ser más larga, aunque use palabras simples", "Porque las frases nunca se pueden adivinar por fuerza bruta", "Porque los símbolos especiales debilitan la seguridad", "Porque los sistemas no permiten contraseñas cortas"],
      "correctIndex": 0
    },
    {
      "id": "pw2",
      "category": "CONTRASENAS",
      "difficulty": 2,
      "text": "¿Qué es el 'credential stuffing'?",
      "options": ["Un ataque que prueba combinaciones de usuario/contraseña filtradas de otros sitios para acceder a una cuenta", "Un método para generar contraseñas aleatorias seguras", "Una técnica de cifrado de contraseñas en la base de datos", "Un tipo de firewall que bloquea inicios de sesión sospechosos"],
      "correctIndex": 0
    },
    {
      "id": "pw3",
      "category": "CONTRASENAS",
      "difficulty": 1,
      "text": "En autenticación multifactor (MFA), ¿qué representan 'algo que sabes, algo que tienes y algo que eres'?",
      "options": ["Los tres tipos de firewall", "Las tres capas del modelo OSI relacionadas con el login", "Las tres categorías clásicas de factores de autenticación", "Los tres pasos de un ataque de phishing"],
      "correctIndex": 2
    },
    {
      "id": "pw4",
      "category": "CONTRASENAS",
      "difficulty": 3,
      "text": "¿Para qué sirve agregar 'sal' (salt) al hashear contraseñas antes de guardarlas?",
      "options": ["Para acelerar el proceso de verificación de login", "Para evitar que contraseñas iguales generen el mismo hash y frustrar ataques con tablas precalculadas (rainbow tables)", "Para comprimir el tamaño del hash almacenado", "Para permitir recuperar la contraseña original fácilmente"],
      "correctIndex": 1
    },
    {
      "id": "pw5",
      "category": "CONTRASENAS",
      "difficulty": 2,
      "text": "¿Cuál es el principal riesgo de reutilizar la misma contraseña en varios sitios?",
      "options": ["Ninguno, si la contraseña es suficientemente larga", "Si un sitio sufre una filtración, un atacante puede probar esa misma contraseña en tus otras cuentas", "Los navegadores bloquean automáticamente la reutilización", "Solo afecta el rendimiento del sitio web"],
      "correctIndex": 1
    },
    {
      "id": "is1",
      "category": "INGENIERIA_SOCIAL",
      "difficulty": 1,
      "text": "¿Qué diferencia al 'spear phishing' del phishing genérico?",
      "options": ["El spear phishing está dirigido y personalizado a una persona u organización específica", "El spear phishing solo ocurre por teléfono", "El phishing genérico siempre incluye malware adjunto", "No hay ninguna diferencia real"],
      "correctIndex": 0
    },
    {
      "id": "is2",
      "category": "INGENIERIA_SOCIAL",
      "difficulty": 2,
      "text": "¿Qué es el 'pretexting' en ingeniería social?",
      "options": ["Enviar correos masivos sin personalizar", "Crear una historia o identidad falsa para ganar la confianza de la víctima y obtener información", "Un tipo de cifrado usado por atacantes", "Escanear puertos de una red antes de un ataque"],
      "correctIndex": 1
    },
    {
      "id": "is3",
      "category": "INGENIERIA_SOCIAL",
      "difficulty": 2,
      "text": "¿Cómo se llama el ataque de ingeniería social realizado por teléfono o mensajes de voz?",
      "options": ["Smishing", "Vishing", "Whaling", "Tailgating"],
      "correctIndex": 1
    },
    {
      "id": "is4",
      "category": "INGENIERIA_SOCIAL",
      "difficulty": 1,
      "text": "¿Cuál de las siguientes NO es una señal típica de un correo de phishing?",
      "options": ["Urgencia extrema para actuar de inmediato", "Dominio del remitente ligeramente distinto al oficial", "El correo fue firmado digitalmente por la empresa de forma verificable", "Enlaces que no coinciden con el dominio esperado al pasar el cursor"],
      "correctIndex": 2
    },
    {
      "id": "is5",
      "category": "INGENIERIA_SOCIAL",
      "difficulty": 3,
      "text": "¿Qué es el 'baiting' en ingeniería social?",
      "options": ["Ofrecer algo atractivo (como una USB o una descarga gratuita) para inducir a la víctima a comprometer su seguridad", "Espiar conversaciones en redes sociales", "Enviar spam publicitario sin intención maliciosa", "Bloquear el acceso a un sistema hasta que se pague un rescate"],
      "correctIndex": 0
    },
    {
      "id": "nm1",
      "category": "REDES_MALWARE",
      "difficulty": 1,
      "text": "¿Cuál es la diferencia principal entre un virus y un gusano (worm)?",
      "options": ["El gusano se propaga automáticamente por la red sin necesitar un archivo anfitrión; el virus sí lo necesita", "Son exactamente lo mismo", "El virus solo afecta a servidores, el gusano solo a computadoras personales", "El gusano no puede dañar archivos"],
      "correctIndex": 0
    },
    {
      "id": "nm2",
      "category": "REDES_MALWARE",
      "difficulty": 1,
      "text": "A nivel básico, ¿qué función cumple un firewall?",
      "options": ["Elimina virus ya instalados en el sistema", "Filtra y controla el tráfico de red permitido según reglas definidas", "Cifra todos los archivos del disco duro", "Genera contraseñas seguras automáticamente"],
      "correctIndex": 1
    },
    {
      "id": "nm3",
      "category": "REDES_MALWARE",
      "difficulty": 2,
      "text": "¿Qué caracteriza a un ataque de ransomware?",
      "options": ["Cifra los archivos de la víctima y exige un pago para restaurarlos", "Roba únicamente contraseñas guardadas en el navegador", "Es un tipo de firewall mal configurado", "Solo afecta a servidores de correo electrónico"],
      "correctIndex": 0
    },
    {
      "id": "nm4",
      "category": "REDES_MALWARE",
      "difficulty": 3,
      "text": "¿En qué consiste un ataque de 'hombre en el medio' (man-in-the-middle)?",
      "options": ["Un atacante intercepta y potencialmente altera la comunicación entre dos partes sin que lo noten", "Un atacante satura un servidor con tráfico hasta tumbarlo", "Un atacante instala un keylogger físico", "Un atacante adivina contraseñas por fuerza bruta"],
      "correctIndex": 0
    },
    {
      "id": "nm5",
      "category": "REDES_MALWARE",
      "difficulty": 2,
      "text": "¿Qué protege principalmente el uso de HTTPS/TLS en la comunicación web?",
      "options": ["La disponibilidad del servidor ante caídas", "La confidencialidad e integridad de los datos en tránsito, mediante cifrado", "La velocidad de carga de la página", "La creación de copias de seguridad automáticas"],
      "correctIndex": 1
    }
  ]
  ```

- [ ] **Step 2: Define the category and question types**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizCategory.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import kotlinx.serialization.Serializable

  @Serializable
  enum class QuizCategory {
      CONCEPTOS_BASICOS,
      CONTRASENAS,
      INGENIERIA_SOCIAL,
      REDES_MALWARE,
  }
  ```

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizQuestion.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import kotlinx.serialization.Serializable

  @Serializable
  data class QuizQuestion(
      val id: String,
      val category: QuizCategory,
      val difficulty: Int,
      val text: String,
      val options: List<String>,
      val correctIndex: Int,
  )
  ```

- [ ] **Step 3: Write the failing attempt-selection tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/data/quiz/QuizAttemptSelectorTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import org.junit.Assert.assertEquals
  import org.junit.Test
  import kotlin.random.Random

  class QuizAttemptSelectorTest {
      private val bank = QuizCategory.entries.flatMap { category ->
          (1..5).map { index ->
              QuizQuestion(
                  id = "${category.name}-$index",
                  category = category,
                  difficulty = 1,
                  text = "Q $index",
                  options = listOf("A", "B", "C", "D"),
                  correctIndex = 0,
              )
          }
      }

      @Test
      fun selectQuizAttempt_alwaysReturnsElevenQuestions() {
          repeat(50) { seed ->
              val attempt = selectQuizAttempt(bank, Random(seed))
              assertEquals(11, attempt.size)
          }
      }

      @Test
      fun selectQuizAttempt_exactlyOneCategoryHasTwoQuestions_restHaveThree() {
          repeat(50) { seed ->
              val attempt = selectQuizAttempt(bank, Random(seed))
              val counts = attempt.groupingBy { it.category }.eachCount()
              assertEquals(QuizCategory.entries.size, counts.size)
              assertEquals(listOf(2, 3, 3, 3), counts.values.sorted())
          }
      }

      @Test
      fun selectQuizAttempt_neverRepeatsAQuestionWithinOneAttempt() {
          val attempt = selectQuizAttempt(bank, Random(42))
          assertEquals(attempt.size, attempt.map { it.id }.toSet().size)
      }
  }
  ```

- [ ] **Step 4: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.quiz.QuizAttemptSelectorTest"`
  Expected: FAIL to compile — `selectQuizAttempt` doesn't exist yet.

- [ ] **Step 5: Implement the attempt selector**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizAttemptSelector.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import kotlin.random.Random

  private const val QUESTIONS_PER_ATTEMPT = 11
  private const val GUARANTEED_PER_CATEGORY = 3

  /**
   * Draws one placement-quiz attempt from the full question bank: 3 questions from
   * three of the four categories and 2 from the remaining one (chosen at random each
   * time), for 11 total, then shuffles the combined order.
   */
  fun selectQuizAttempt(bank: List<QuizQuestion>, random: Random = Random.Default): List<QuizQuestion> {
      val byCategory = QuizCategory.entries.associateWith { category -> bank.filter { it.category == category } }
      val reducedCategory = QuizCategory.entries.toList().random(random)

      val selected = mutableListOf<QuizQuestion>()
      for (category in QuizCategory.entries) {
          val count = if (category == reducedCategory) GUARANTEED_PER_CATEGORY - 1 else GUARANTEED_PER_CATEGORY
          selected += byCategory.getValue(category).shuffled(random).take(count)
      }
      check(selected.size == QUESTIONS_PER_ATTEMPT)
      return selected.shuffled(random)
  }
  ```

- [ ] **Step 6: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.quiz.QuizAttemptSelectorTest"`
  Expected: `3 tests completed, 0 failed`.

- [ ] **Step 7: Add the repository interface and local implementation**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizContentRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  interface QuizContentRepository {
      suspend fun getAttempt(): List<QuizQuestion>
  }
  ```

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/LocalQuizContentRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import android.content.Context
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext
  import kotlinx.serialization.json.Json

  class LocalQuizContentRepository(
      private val context: Context,
  ) : QuizContentRepository {
      private var cachedBank: List<QuizQuestion>? = null

      override suspend fun getAttempt(): List<QuizQuestion> = withContext(Dispatchers.IO) {
          val bank = cachedBank ?: loadBank().also { cachedBank = it }
          selectQuizAttempt(bank)
      }

      private fun loadBank(): List<QuizQuestion> {
          val json = context.assets.open("quiz_questions.json").bufferedReader().use { it.readText() }
          return Json.decodeFromString(json)
      }
  }
  ```

- [ ] **Step 8: Write the failing asset-loading instrumented test**

  Create `app/src/androidTest/java/com/example/nightwatchprotocol/data/quiz/LocalQuizContentRepositoryTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import androidx.test.ext.junit.runners.AndroidJUnit4
  import androidx.test.platform.app.InstrumentationRegistry
  import kotlinx.coroutines.runBlocking
  import org.junit.Assert.assertEquals
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class LocalQuizContentRepositoryTest {
      @Test
      fun getAttempt_returnsElevenQuestionsFromTheBundledBank() = runBlocking {
          val context = InstrumentationRegistry.getInstrumentation().targetContext
          val repository = LocalQuizContentRepository(context)

          val attempt = repository.getAttempt()

          assertEquals(11, attempt.size)
      }
  }
  ```

- [ ] **Step 9: Run the instrumented test to verify it passes**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.data.quiz.LocalQuizContentRepositoryTest"`
  Expected: `1 test completed, 0 failed`. If it fails with an asset-not-found error, confirm `quiz_questions.json` landed at `app/src/main/assets/quiz_questions.json` (not `app/assets/` or another path).

- [ ] **Step 10: Add the test fixture used by every later ViewModel test**

  Create `app/src/test/java/com/example/nightwatchprotocol/data/quiz/FakeQuizContentRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  class FakeQuizContentRepository(
      private val attempt: List<QuizQuestion> = sampleAttempt(),
  ) : QuizContentRepository {
      var getAttemptCallCount = 0

      override suspend fun getAttempt(): List<QuizQuestion> {
          getAttemptCallCount++
          return attempt
      }
  }

  /**
   * 11 questions matching the real 3/3/3/2-per-category shape, every option list
   * ["A","B","C","D"] with correctIndex = 0, so tests can drive a deterministic
   * all-correct or all-wrong run by always selecting index 0 or index 1.
   */
  fun sampleAttempt(): List<QuizQuestion> = QuizCategory.entries.flatMapIndexed { categoryIndex, category ->
      val countForCategory = if (categoryIndex == 0) 2 else 3
      (1..countForCategory).map { index ->
          QuizQuestion(
              id = "${category.name}-$index",
              category = category,
              difficulty = 1,
              text = "Pregunta de prueba $index de ${category.name}",
              options = listOf("A", "B", "C", "D"),
              correctIndex = 0,
          )
      }
  }
  ```

- [ ] **Step 11: Commit**

  ```bash
  git add app/src/main/assets/quiz_questions.json app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizCategory.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizQuestion.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizAttemptSelector.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizContentRepository.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/LocalQuizContentRepository.kt app/src/test/java/com/example/nightwatchprotocol/data/quiz/FakeQuizContentRepository.kt app/src/test/java/com/example/nightwatchprotocol/data/quiz/QuizAttemptSelectorTest.kt app/src/androidTest/java/com/example/nightwatchprotocol/data/quiz/LocalQuizContentRepositoryTest.kt
  git commit -m "feat: add 20-question placement quiz bank and content repository"
  ```

---

### Task 3: Scoring engine & study plan builder

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementLevel.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementScorer.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/StudyPlan.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/data/quiz/PlacementScorerTest.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/data/quiz/StudyPlanTest.kt`

**Interfaces:**
- Consumes: `QuizQuestion`/`QuizCategory` (Task 2).
- Produces: `enum class PlacementLevel { BAJO, MEDIO, ALTO }`; `data class PlacementScore(level: PlacementLevel, overallPercentage: Int, categoryPercentages: Map<QuizCategory, Int>, weakCategories: Set<QuizCategory>)`; `fun scoreAttempt(questions: List<QuizQuestion>, answers: Map<String, Int>): PlacementScore`; `fun buildStudyPlan(level: PlacementLevel, weakCategories: Set<QuizCategory>): List<String>`. Task 4's `PlacementResult` and Task 6/7's ViewModels consume all of these.

- [ ] **Step 1: Define the level type**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementLevel.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  enum class PlacementLevel { BAJO, MEDIO, ALTO }
  ```

- [ ] **Step 2: Write the failing scorer tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/data/quiz/PlacementScorerTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class PlacementScorerTest {
      private fun question(id: String, category: QuizCategory, difficulty: Int = 1, correctIndex: Int = 0) =
          QuizQuestion(id, category, difficulty, "text", listOf("a", "b", "c", "d"), correctIndex)

      @Test
      fun allCorrect_scoresAlto_withNoWeakCategories() {
          val questions = listOf(
              question("1", QuizCategory.CONCEPTOS_BASICOS),
              question("2", QuizCategory.CONTRASENAS),
          )
          val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 0))

          assertEquals(PlacementLevel.ALTO, score.level)
          assertEquals(100, score.overallPercentage)
          assertTrue(score.weakCategories.isEmpty())
      }

      @Test
      fun allWrong_scoresBajo_withEveryCategoryWeak() {
          val questions = listOf(
              question("1", QuizCategory.CONCEPTOS_BASICOS),
              question("2", QuizCategory.CONTRASENAS),
          )
          val score = scoreAttempt(questions, mapOf("1" to 1, "2" to 1))

          assertEquals(PlacementLevel.BAJO, score.level)
          assertEquals(0, score.overallPercentage)
          assertEquals(setOf(QuizCategory.CONCEPTOS_BASICOS, QuizCategory.CONTRASENAS), score.weakCategories)
      }

      @Test
      fun exactlyFortyPercent_isBajo() {
          val questions = listOf(
              question("1", QuizCategory.REDES_MALWARE, difficulty = 40),
              question("2", QuizCategory.REDES_MALWARE, difficulty = 60),
          )
          val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

          assertEquals(40, score.overallPercentage)
          assertEquals(PlacementLevel.BAJO, score.level)
      }

      @Test
      fun exactlyFortyOnePercent_isMedio() {
          val questions = listOf(
              question("1", QuizCategory.REDES_MALWARE, difficulty = 41),
              question("2", QuizCategory.REDES_MALWARE, difficulty = 59),
          )
          val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

          assertEquals(41, score.overallPercentage)
          assertEquals(PlacementLevel.MEDIO, score.level)
      }

      @Test
      fun exactlySeventyFivePercent_isMedio() {
          val questions = listOf(
              question("1", QuizCategory.REDES_MALWARE, difficulty = 75),
              question("2", QuizCategory.REDES_MALWARE, difficulty = 25),
          )
          val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

          assertEquals(PlacementLevel.MEDIO, score.level)
      }

      @Test
      fun exactlySeventySixPercent_isAlto() {
          val questions = listOf(
              question("1", QuizCategory.REDES_MALWARE, difficulty = 76),
              question("2", QuizCategory.REDES_MALWARE, difficulty = 24),
          )
          val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

          assertEquals(PlacementLevel.ALTO, score.level)
      }

      @Test
      fun categoryWithFewerQuestions_isScoredByPercentageNotRawCount() {
          val questions = listOf(
              question("1", QuizCategory.INGENIERIA_SOCIAL),
              question("2", QuizCategory.INGENIERIA_SOCIAL),
              question("3", QuizCategory.REDES_MALWARE),
          )
          val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 0, "3" to 0))

          assertEquals(100, score.categoryPercentages.getValue(QuizCategory.INGENIERIA_SOCIAL))
          assertEquals(100, score.categoryPercentages.getValue(QuizCategory.REDES_MALWARE))
      }

      @Test
      fun unansweredQuestion_countsAsIncorrect() {
          val questions = listOf(question("1", QuizCategory.CONTRASENAS))
          val score = scoreAttempt(questions, emptyMap())

          assertEquals(0, score.overallPercentage)
      }
  }
  ```

- [ ] **Step 3: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.quiz.PlacementScorerTest"`
  Expected: FAIL to compile — `scoreAttempt` doesn't exist yet.

- [ ] **Step 4: Implement the scorer**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementScorer.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import kotlin.math.roundToInt

  private const val WEAK_CATEGORY_THRESHOLD_PERCENT = 60
  private const val BAJO_MAX_PERCENT = 40
  private const val MEDIO_MAX_PERCENT = 75

  data class PlacementScore(
      val level: PlacementLevel,
      val overallPercentage: Int,
      val categoryPercentages: Map<QuizCategory, Int>,
      val weakCategories: Set<QuizCategory>,
  )

  fun scoreAttempt(questions: List<QuizQuestion>, answers: Map<String, Int>): PlacementScore {
      val categoryPercentages = questions.groupBy { it.category }
          .mapValues { (_, categoryQuestions) -> percentageFor(categoryQuestions, answers) }
      val overallPercentage = percentageFor(questions, answers)
      val weakCategories = categoryPercentages.filterValues { it < WEAK_CATEGORY_THRESHOLD_PERCENT }.keys

      val level = when {
          overallPercentage <= BAJO_MAX_PERCENT -> PlacementLevel.BAJO
          overallPercentage <= MEDIO_MAX_PERCENT -> PlacementLevel.MEDIO
          else -> PlacementLevel.ALTO
      }

      return PlacementScore(level, overallPercentage, categoryPercentages, weakCategories)
  }

  private fun percentageFor(questions: List<QuizQuestion>, answers: Map<String, Int>): Int {
      if (questions.isEmpty()) return 0
      val possiblePoints = questions.sumOf { it.difficulty }
      if (possiblePoints == 0) return 0
      val earnedPoints = questions.sumOf { question ->
          if (answers[question.id] == question.correctIndex) question.difficulty else 0
      }
      return (earnedPoints * 100.0 / possiblePoints).roundToInt()
  }
  ```

- [ ] **Step 5: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.quiz.PlacementScorerTest"`
  Expected: `8 tests completed, 0 failed`.

- [ ] **Step 6: Write the failing study plan tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/data/quiz/StudyPlanTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import org.junit.Assert.assertEquals
  import org.junit.Test

  class StudyPlanTest {
      @Test
      fun noWeakCategories_returnsOneTopicPerCategory_inDeclarationOrder() {
          val plan = buildStudyPlan(PlacementLevel.MEDIO, emptySet())

          assertEquals(QuizCategory.entries.size, plan.size)
          assertEquals(plan.distinct(), plan)
      }

      @Test
      fun weakCategories_arePromotedToTheFront() {
          val defaultPlan = buildStudyPlan(PlacementLevel.BAJO, emptySet())
          val plan = buildStudyPlan(
              PlacementLevel.BAJO,
              setOf(QuizCategory.REDES_MALWARE, QuizCategory.CONCEPTOS_BASICOS),
          )

          val expectedFirstTwo = listOf(
              defaultPlan[QuizCategory.entries.indexOf(QuizCategory.CONCEPTOS_BASICOS)],
              defaultPlan[QuizCategory.entries.indexOf(QuizCategory.REDES_MALWARE)],
          )
          assertEquals(expectedFirstTwo, plan.take(2))
      }

      @Test
      fun allCategoriesWeak_returnsSameOrderAsNoWeakCategories() {
          val allWeak = buildStudyPlan(PlacementLevel.ALTO, QuizCategory.entries.toSet())
          val noneWeak = buildStudyPlan(PlacementLevel.ALTO, emptySet())

          assertEquals(noneWeak, allWeak)
      }

      @Test
      fun everyLevel_hasATopicForEveryCategory() {
          for (level in PlacementLevel.entries) {
              val plan = buildStudyPlan(level, emptySet())
              assertEquals(QuizCategory.entries.size, plan.size)
          }
      }
  }
  ```

- [ ] **Step 7: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.quiz.StudyPlanTest"`
  Expected: FAIL to compile — `buildStudyPlan` doesn't exist yet.

- [ ] **Step 8: Implement the study plan builder**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/StudyPlan.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  private val topicsByLevelAndCategory: Map<PlacementLevel, Map<QuizCategory, String>> = mapOf(
      PlacementLevel.BAJO to mapOf(
          QuizCategory.CONCEPTOS_BASICOS to "Fundamentos de ciberseguridad",
          QuizCategory.CONTRASENAS to "Higiene básica de contraseñas",
          QuizCategory.INGENIERIA_SOCIAL to "Cómo reconocer un intento de phishing",
          QuizCategory.REDES_MALWARE to "Introducción a redes y malware",
      ),
      PlacementLevel.MEDIO to mapOf(
          QuizCategory.CONCEPTOS_BASICOS to "Principios de defensa en profundidad",
          QuizCategory.CONTRASENAS to "Autenticación multifactor y gestión de credenciales",
          QuizCategory.INGENIERIA_SOCIAL to "Ingeniería social dirigida (spear phishing y pretexting)",
          QuizCategory.REDES_MALWARE to "Fundamentos de redes seguras y tipos de malware",
      ),
      PlacementLevel.ALTO to mapOf(
          QuizCategory.CONCEPTOS_BASICOS to "Modelado de amenazas y gestión de vulnerabilidades",
          QuizCategory.CONTRASENAS to "Arquitecturas de autenticación y almacenamiento seguro de credenciales",
          QuizCategory.INGENIERIA_SOCIAL to "Análisis avanzado de ataques de ingeniería social",
          QuizCategory.REDES_MALWARE to "Endurecimiento de redes y respuesta a incidentes de malware",
      ),
  )

  /**
   * One topic per category for the given level, with categories the user scored
   * weak in promoted to the front (stable within each group, so ties keep
   * QuizCategory's declaration order).
   */
  fun buildStudyPlan(level: PlacementLevel, weakCategories: Set<QuizCategory>): List<String> {
      val topicsForLevel = topicsByLevelAndCategory.getValue(level)
      val orderedCategories = QuizCategory.entries.sortedByDescending { it in weakCategories }
      return orderedCategories.map { category -> topicsForLevel.getValue(category) }
  }
  ```

- [ ] **Step 9: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.data.quiz.StudyPlanTest"`
  Expected: `4 tests completed, 0 failed`.

- [ ] **Step 10: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementLevel.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementScorer.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/StudyPlan.kt app/src/test/java/com/example/nightwatchprotocol/data/quiz/PlacementScorerTest.kt app/src/test/java/com/example/nightwatchprotocol/data/quiz/StudyPlanTest.kt
  git commit -m "feat: add placement scoring engine and per-level study plan builder"
  ```

---

### Task 4: Quiz result persistence (Firestore) & DI wiring

**Files:**
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementResult.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizResultRepository.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/data/quiz/FirestoreQuizResultRepository.kt`
- Create: `app/src/test/java/com/example/nightwatchprotocol/data/quiz/FakeQuizResultRepository.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/di/AppContainer.kt`

**Interfaces:**
- Consumes: `PlacementLevel`/`QuizCategory` (Tasks 2-3), `AuthRepository.currentUserUid` (Task 1).
- Produces: `data class PlacementResult(level: PlacementLevel, weakCategories: Set<QuizCategory>)`; `interface QuizResultRepository { suspend fun getResult(uid: String): PlacementResult?; suspend fun saveResult(uid: String, result: PlacementResult) }`; `class FirestoreQuizResultRepository(firestore: FirebaseFirestore) : QuizResultRepository`; `class FakeQuizResultRepository : QuizResultRepository` (with `getResultResult`/`saveResultResult: Result<...>` and call-count fields, matching `FakeAuthRepository`'s pattern); `AppContainer.quizContentRepository: QuizContentRepository` and `AppContainer.quizResultRepository: QuizResultRepository`. Tasks 5-7's ViewModels consume both new `AppContainer` properties through their `Factory`.

- [ ] **Step 1 (human partner action — cannot be automated): Restrict Firestore security rules**

  In the Firebase console (same project as Task 1's Step 1): go to **Firestore Database → Rules**, replace the default rules with:

  ```
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      match /users/{userId}/placement/{document=**} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
  ```

  Click **Publish**. This ensures a signed-in user can only read/write their own placement result, never anyone else's.

- [ ] **Step 2: Define the domain result type**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementResult.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  data class PlacementResult(
      val level: PlacementLevel,
      val weakCategories: Set<QuizCategory>,
  )
  ```

- [ ] **Step 3: Define the repository contract**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizResultRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  interface QuizResultRepository {
      suspend fun getResult(uid: String): PlacementResult?
      suspend fun saveResult(uid: String, result: PlacementResult)
  }
  ```

- [ ] **Step 4: Implement it against Firestore**

  Create `app/src/main/java/com/example/nightwatchprotocol/data/quiz/FirestoreQuizResultRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  import com.google.firebase.firestore.FieldValue
  import com.google.firebase.firestore.FirebaseFirestore
  import kotlinx.coroutines.tasks.await

  class FirestoreQuizResultRepository(
      private val firestore: FirebaseFirestore,
  ) : QuizResultRepository {

      override suspend fun getResult(uid: String): PlacementResult? {
          val snapshot = resultDocument(uid).get().await()
          if (!snapshot.exists()) return null

          val levelName = snapshot.getString("level") ?: return null
          val level = runCatching { PlacementLevel.valueOf(levelName) }.getOrNull() ?: return null

          @Suppress("UNCHECKED_CAST")
          val categoryNames = snapshot.get("weakCategories") as? List<String> ?: emptyList()
          val weakCategories = categoryNames
              .mapNotNull { name -> runCatching { QuizCategory.valueOf(name) }.getOrNull() }
              .toSet()

          return PlacementResult(level, weakCategories)
      }

      override suspend fun saveResult(uid: String, result: PlacementResult) {
          val data = mapOf(
              "level" to result.level.name,
              "weakCategories" to result.weakCategories.map { it.name },
              "completedAt" to FieldValue.serverTimestamp(),
          )
          resultDocument(uid).set(data).await()
      }

      private fun resultDocument(uid: String) =
          firestore.collection("users").document(uid).collection("placement").document("result")
  }
  ```

  `completedAt` is written for future sub-projects (e.g. a Profile screen showing "completado el X") but not read back here — nothing in this sub-project's UI displays it yet, so the domain `PlacementResult` type intentionally omits it rather than threading an unused field through every consumer.

- [ ] **Step 5: Add the test fixture used by every later ViewModel test**

  Create `app/src/test/java/com/example/nightwatchprotocol/data/quiz/FakeQuizResultRepository.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.data.quiz

  class FakeQuizResultRepository : QuizResultRepository {
      var getResultResult: Result<PlacementResult?> = Result.success(null)
      var saveResultResult: Result<Unit> = Result.success(Unit)

      var getResultCallCount = 0
      var saveResultCallCount = 0
      var lastSavedResult: PlacementResult? = null

      override suspend fun getResult(uid: String): PlacementResult? {
          getResultCallCount++
          return getResultResult.getOrThrow()
      }

      override suspend fun saveResult(uid: String, result: PlacementResult) {
          saveResultCallCount++
          lastSavedResult = result
          saveResultResult.getOrThrow()
      }
  }
  ```

- [ ] **Step 6: Wire both new repositories into `AppContainer`**

  Replace the contents of `app/src/main/java/com/example/nightwatchprotocol/di/AppContainer.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.di

  import android.content.Context
  import com.example.nightwatchprotocol.data.auth.AuthRepository
  import com.example.nightwatchprotocol.data.auth.FirebaseAuthRepository
  import com.example.nightwatchprotocol.data.quiz.FirestoreQuizResultRepository
  import com.example.nightwatchprotocol.data.quiz.LocalQuizContentRepository
  import com.example.nightwatchprotocol.data.quiz.QuizContentRepository
  import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
  import com.example.nightwatchprotocol.data.session.SessionPreferences
  import com.example.nightwatchprotocol.data.session.sessionDataStore
  import com.google.firebase.auth.FirebaseAuth
  import com.google.firebase.firestore.FirebaseFirestore

  interface AppContainer {
      val authRepository: AuthRepository
      val sessionPreferences: SessionPreferences
      val quizContentRepository: QuizContentRepository
      val quizResultRepository: QuizResultRepository
  }

  class DefaultAppContainer(context: Context) : AppContainer {
      override val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())
      override val sessionPreferences: SessionPreferences = SessionPreferences(context.sessionDataStore)
      override val quizContentRepository: QuizContentRepository = LocalQuizContentRepository(context)
      override val quizResultRepository: QuizResultRepository = FirestoreQuizResultRepository(FirebaseFirestore.getInstance())
  }
  ```

- [ ] **Step 7: Verify the project builds**

  Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/data/quiz/PlacementResult.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/QuizResultRepository.kt app/src/main/java/com/example/nightwatchprotocol/data/quiz/FirestoreQuizResultRepository.kt app/src/test/java/com/example/nightwatchprotocol/data/quiz/FakeQuizResultRepository.kt app/src/main/java/com/example/nightwatchprotocol/di/AppContainer.kt
  git commit -m "feat: add Firestore-backed QuizResultRepository and wire it into AppContainer"
  ```

---

### Task 5: PostAuthGate

**Files:**
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateUiState.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateViewModel.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateScreen.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`/`FakeAuthRepository` (Task 1), `QuizResultRepository`/`FakeQuizResultRepository`/`PlacementResult` (Task 4), `NightWatchApplication`/`AppContainer` (existing + Task 4), `NightWatchScreenBackground` (existing).
- Produces: `@Serializable object PostAuthGate` (nav route); `data class PostAuthGateUiState(isLoading: Boolean = true)`; `sealed interface PostAuthGateEvent { NavigateToMainMenu; NavigateToPlacementQuiz }`; `class PostAuthGateViewModel(authRepository: AuthRepository, quizResultRepository: QuizResultRepository) : ViewModel()`; `@Composable fun PostAuthGateScreen(onNavigateToMainMenu: () -> Unit, onNavigateToPlacementQuiz: () -> Unit, viewModel: PostAuthGateViewModel = viewModel(factory = PostAuthGateViewModel.Factory))`. Task 8's `NightWatchNavHost` wires this in as the new post-login/cold-start-with-session target.

- [ ] **Step 1: Add the `PostAuthGate` route**

  Edit `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`, adding this at the end of the file:

  ```kotlin

  @Serializable
  object PostAuthGate
  ```

- [ ] **Step 2: Define the UI state and event types**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateUiState.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.postauth

  data class PostAuthGateUiState(
      val isLoading: Boolean = true,
  )

  sealed interface PostAuthGateEvent {
      data object NavigateToMainMenu : PostAuthGateEvent
      data object NavigateToPlacementQuiz : PostAuthGateEvent
  }
  ```

- [ ] **Step 3: Write the failing ViewModel tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateViewModelTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.postauth

  import app.cash.turbine.test
  import com.example.nightwatchprotocol.MainDispatcherRule
  import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
  import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
  import com.example.nightwatchprotocol.data.quiz.PlacementLevel
  import com.example.nightwatchprotocol.data.quiz.PlacementResult
  import kotlinx.coroutines.test.runTest
  import org.junit.Assert.assertEquals
  import org.junit.Rule
  import org.junit.Test

  class PostAuthGateViewModelTest {
      @get:Rule
      val mainDispatcherRule = MainDispatcherRule()

      @Test
      fun existingResult_navigatesToMainMenu() = runTest {
          val quizResultRepository = FakeQuizResultRepository()
          quizResultRepository.getResultResult = Result.success(PlacementResult(PlacementLevel.MEDIO, emptySet()))
          val viewModel = PostAuthGateViewModel(FakeAuthRepository(), quizResultRepository)

          viewModel.events.test {
              assertEquals(PostAuthGateEvent.NavigateToMainMenu, awaitItem())
          }
      }

      @Test
      fun noResult_navigatesToPlacementQuiz() = runTest {
          val quizResultRepository = FakeQuizResultRepository()
          quizResultRepository.getResultResult = Result.success(null)
          val viewModel = PostAuthGateViewModel(FakeAuthRepository(), quizResultRepository)

          viewModel.events.test {
              assertEquals(PostAuthGateEvent.NavigateToPlacementQuiz, awaitItem())
          }
      }

      @Test
      fun firestoreReadFails_failsOpenToPlacementQuiz() = runTest {
          val quizResultRepository = FakeQuizResultRepository()
          quizResultRepository.getResultResult = Result.failure(RuntimeException("network down"))
          val viewModel = PostAuthGateViewModel(FakeAuthRepository(), quizResultRepository)

          viewModel.events.test {
              assertEquals(PostAuthGateEvent.NavigateToPlacementQuiz, awaitItem())
          }
      }

      @Test
      fun nullUid_navigatesToMainMenuWithoutCallingRepository() = runTest {
          val quizResultRepository = FakeQuizResultRepository()
          val fakeAuthRepository = FakeAuthRepository().apply { currentUserUid = null }
          val viewModel = PostAuthGateViewModel(fakeAuthRepository, quizResultRepository)

          viewModel.events.test {
              assertEquals(PostAuthGateEvent.NavigateToMainMenu, awaitItem())
          }
          assertEquals(0, quizResultRepository.getResultCallCount)
      }
  }
  ```

- [ ] **Step 4: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.postauth.PostAuthGateViewModelTest"`
  Expected: FAIL to compile — `PostAuthGateViewModel` doesn't exist yet.

- [ ] **Step 5: Implement the ViewModel**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateViewModel.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.postauth

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import androidx.lifecycle.viewmodel.initializer
  import androidx.lifecycle.viewmodel.viewModelFactory
  import com.example.nightwatchprotocol.NightWatchApplication
  import com.example.nightwatchprotocol.data.auth.AuthRepository
  import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
  import kotlinx.coroutines.channels.Channel
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.receiveAsFlow
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.launch

  class PostAuthGateViewModel(
      authRepository: AuthRepository,
      quizResultRepository: QuizResultRepository,
  ) : ViewModel() {

      private val _uiState = MutableStateFlow(PostAuthGateUiState())
      val uiState = _uiState.asStateFlow()

      private val _events = Channel<PostAuthGateEvent>(Channel.BUFFERED)
      val events: Flow<PostAuthGateEvent> = _events.receiveAsFlow()

      init {
          val uid = authRepository.currentUserUid
          viewModelScope.launch {
              val event = if (uid == null) {
                  PostAuthGateEvent.NavigateToMainMenu
              } else {
                  val result = runCatching { quizResultRepository.getResult(uid) }.getOrNull()
                  if (result != null) PostAuthGateEvent.NavigateToMainMenu else PostAuthGateEvent.NavigateToPlacementQuiz
              }
              _uiState.update { it.copy(isLoading = false) }
              _events.send(event)
          }
      }

      companion object {
          val Factory: ViewModelProvider.Factory = viewModelFactory {
              initializer {
                  val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                  PostAuthGateViewModel(application.container.authRepository, application.container.quizResultRepository)
              }
          }
      }
  }
  ```

- [ ] **Step 6: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.postauth.PostAuthGateViewModelTest"`
  Expected: `4 tests completed, 0 failed`.

- [ ] **Step 7: Build the screen**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/postauth/PostAuthGateScreen.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.postauth

  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.wrapContentSize
  import androidx.compose.material3.CircularProgressIndicator
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground

  @Composable
  fun PostAuthGateScreen(
      onNavigateToMainMenu: () -> Unit,
      onNavigateToPlacementQuiz: () -> Unit,
      viewModel: PostAuthGateViewModel = viewModel(factory = PostAuthGateViewModel.Factory),
  ) {
      LaunchedEffect(viewModel) {
          viewModel.events.collect { event ->
              when (event) {
                  PostAuthGateEvent.NavigateToMainMenu -> onNavigateToMainMenu()
                  PostAuthGateEvent.NavigateToPlacementQuiz -> onNavigateToPlacementQuiz()
              }
          }
      }

      NightWatchScreenBackground {
          CircularProgressIndicator(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
      }
  }
  ```

  No Compose UI test is added for this screen: it fires a real Firestore/Auth call the instant it's composed (via the real `AppContainer` in any instrumented test), which the project's testing strategy explicitly disallows (see this plan's Global Constraints and the spec's Testing Strategy). The routing logic is fully covered by Step 3's ViewModel tests instead.

- [ ] **Step 8: Verify the project builds**

  Run: `./gradlew :app:compileDebugKotlin`
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt app/src/main/java/com/example/nightwatchprotocol/ui/postauth
  git add app/src/test/java/com/example/nightwatchprotocol/ui/postauth
  git commit -m "feat: add PostAuthGate to route between placement quiz and Main Menu"
  ```

---

### Task 6: Placement Quiz screen

**Files:**
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizUiState.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizViewModel.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizScreen.kt`
- Test: `app/src/test/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizScreenTest.kt`

**Interfaces:**
- Consumes: `QuizContentRepository`/`FakeQuizContentRepository`/`sampleAttempt` (Task 2), `scoreAttempt`/`PlacementScore` (Task 3), `QuizResultRepository`/`FakeQuizResultRepository`/`PlacementResult` (Task 4), `AuthRepository`/`FakeAuthRepository` (Task 1), `NightWatchButton`/`NightWatchScreenBackground` (existing).
- Produces: `@Serializable object PlacementQuiz` (nav route); `data class PlacementQuizUiState(questions: List<QuizQuestion>, currentIndex: Int, selectedOptionIndex: Int?, isLoading: Boolean)` with `currentQuestion`/`progressLabel` derived properties; `sealed interface PlacementQuizEvent { data class NavigateToResult(level: PlacementLevel, weakCategories: Set<QuizCategory>, saveFailed: Boolean) }`; `class PlacementQuizViewModel(...) : ViewModel()`; `@Composable fun PlacementQuizScreen(onFinished: (PlacementLevel, Set<QuizCategory>, Boolean) -> Unit, viewModel: ... = viewModel(factory = PlacementQuizViewModel.Factory))`. Task 8's `NightWatchNavHost` wires `onFinished` into navigation to the result route.

- [ ] **Step 1: Add the `PlacementQuiz` route**

  Edit `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`, adding this at the end of the file:

  ```kotlin

  @Serializable
  object PlacementQuiz
  ```

- [ ] **Step 2: Define the UI state and event types**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizUiState.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import com.example.nightwatchprotocol.data.quiz.PlacementLevel
  import com.example.nightwatchprotocol.data.quiz.QuizCategory
  import com.example.nightwatchprotocol.data.quiz.QuizQuestion

  data class PlacementQuizUiState(
      val questions: List<QuizQuestion> = emptyList(),
      val currentIndex: Int = 0,
      val selectedOptionIndex: Int? = null,
      val isLoading: Boolean = true,
  ) {
      val currentQuestion: QuizQuestion?
          get() = questions.getOrNull(currentIndex)

      val progressLabel: String
          get() = "Pregunta ${currentIndex + 1} de ${questions.size}"
  }

  sealed interface PlacementQuizEvent {
      data class NavigateToResult(
          val level: PlacementLevel,
          val weakCategories: Set<QuizCategory>,
          val saveFailed: Boolean,
      ) : PlacementQuizEvent
  }
  ```

- [ ] **Step 3: Write the failing ViewModel tests**

  Create `app/src/test/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizViewModelTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import app.cash.turbine.test
  import com.example.nightwatchprotocol.MainDispatcherRule
  import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
  import com.example.nightwatchprotocol.data.quiz.FakeQuizContentRepository
  import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
  import com.example.nightwatchprotocol.data.quiz.PlacementLevel
  import com.example.nightwatchprotocol.data.quiz.QuizCategory
  import kotlinx.coroutines.test.advanceUntilIdle
  import kotlinx.coroutines.test.runTest
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNull
  import org.junit.Rule
  import org.junit.Test

  class PlacementQuizViewModelTest {
      @get:Rule
      val mainDispatcherRule = MainDispatcherRule()

      private fun createViewModel(
          quizResultRepository: FakeQuizResultRepository = FakeQuizResultRepository(),
          authRepository: FakeAuthRepository = FakeAuthRepository(),
      ) = PlacementQuizViewModel(authRepository, FakeQuizContentRepository(), quizResultRepository)

      @Test
      fun answeringEveryQuestionCorrectly_navigatesToResultWithAlto() = runTest {
          val viewModel = createViewModel()
          advanceUntilIdle()

          viewModel.events.test {
              repeat(11) {
                  viewModel.onOptionSelected(0)
                  viewModel.onNextClicked()
              }
              val event = awaitItem() as PlacementQuizEvent.NavigateToResult
              assertEquals(PlacementLevel.ALTO, event.level)
              assertEquals(emptySet<QuizCategory>(), event.weakCategories)
              assertEquals(false, event.saveFailed)
          }
      }

      @Test
      fun nextClicked_withoutSelection_doesNotAdvance() = runTest {
          val viewModel = createViewModel()
          advanceUntilIdle()

          viewModel.onNextClicked()

          assertEquals(0, viewModel.uiState.value.currentIndex)
      }

      @Test
      fun selectingAnOption_clearsOnAdvancingToNextQuestion() = runTest {
          val viewModel = createViewModel()
          advanceUntilIdle()

          viewModel.onOptionSelected(0)
          viewModel.onNextClicked()

          assertEquals(1, viewModel.uiState.value.currentIndex)
          assertNull(viewModel.uiState.value.selectedOptionIndex)
      }

      @Test
      fun quizResultSaveFails_stillNavigatesToResult_withSaveFailedTrue() = runTest {
          val quizResultRepository = FakeQuizResultRepository()
          quizResultRepository.saveResultResult = Result.failure(RuntimeException("offline"))
          val viewModel = createViewModel(quizResultRepository = quizResultRepository)
          advanceUntilIdle()

          viewModel.events.test {
              repeat(11) {
                  viewModel.onOptionSelected(0)
                  viewModel.onNextClicked()
              }
              val event = awaitItem() as PlacementQuizEvent.NavigateToResult
              assertEquals(true, event.saveFailed)
          }
      }

      @Test
      fun nullUid_stillNavigatesToResult_withSaveFailedTrue() = runTest {
          val authRepository = FakeAuthRepository().apply { currentUserUid = null }
          val viewModel = createViewModel(authRepository = authRepository)
          advanceUntilIdle()

          viewModel.events.test {
              repeat(11) {
                  viewModel.onOptionSelected(0)
                  viewModel.onNextClicked()
              }
              val event = awaitItem() as PlacementQuizEvent.NavigateToResult
              assertEquals(true, event.saveFailed)
          }
      }
  }
  ```

- [ ] **Step 4: Run the tests to verify they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.quiz.PlacementQuizViewModelTest"`
  Expected: FAIL to compile — `PlacementQuizViewModel` doesn't exist yet.

- [ ] **Step 5: Implement the ViewModel**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizViewModel.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import androidx.lifecycle.viewmodel.initializer
  import androidx.lifecycle.viewmodel.viewModelFactory
  import com.example.nightwatchprotocol.NightWatchApplication
  import com.example.nightwatchprotocol.data.auth.AuthRepository
  import com.example.nightwatchprotocol.data.quiz.PlacementResult
  import com.example.nightwatchprotocol.data.quiz.QuizContentRepository
  import com.example.nightwatchprotocol.data.quiz.QuizQuestion
  import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
  import com.example.nightwatchprotocol.data.quiz.scoreAttempt
  import kotlinx.coroutines.channels.Channel
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.receiveAsFlow
  import kotlinx.coroutines.flow.update
  import kotlinx.coroutines.launch

  class PlacementQuizViewModel(
      private val authRepository: AuthRepository,
      private val quizContentRepository: QuizContentRepository,
      private val quizResultRepository: QuizResultRepository,
  ) : ViewModel() {

      private val _uiState = MutableStateFlow(PlacementQuizUiState())
      val uiState = _uiState.asStateFlow()

      private val _events = Channel<PlacementQuizEvent>(Channel.BUFFERED)
      val events: Flow<PlacementQuizEvent> = _events.receiveAsFlow()

      private val answers = mutableMapOf<String, Int>()

      init {
          viewModelScope.launch {
              val questions = quizContentRepository.getAttempt()
              _uiState.update { it.copy(questions = questions, isLoading = false) }
          }
      }

      fun onOptionSelected(index: Int) {
          _uiState.update { it.copy(selectedOptionIndex = index) }
      }

      fun onNextClicked() {
          val state = _uiState.value
          val question = state.currentQuestion ?: return
          val selected = state.selectedOptionIndex ?: return
          answers[question.id] = selected

          val nextIndex = state.currentIndex + 1
          if (nextIndex < state.questions.size) {
              _uiState.update { it.copy(currentIndex = nextIndex, selectedOptionIndex = null) }
          } else {
              finishQuiz(state.questions)
          }
      }

      private fun finishQuiz(questions: List<QuizQuestion>) {
          val score = scoreAttempt(questions, answers)
          val result = PlacementResult(score.level, score.weakCategories)
          val uid = authRepository.currentUserUid

          viewModelScope.launch {
              val saveFailed = if (uid == null) {
                  true
              } else {
                  runCatching { quizResultRepository.saveResult(uid, result) }.isFailure
              }
              _events.send(
                  PlacementQuizEvent.NavigateToResult(
                      level = score.level,
                      weakCategories = score.weakCategories,
                      saveFailed = saveFailed,
                  ),
              )
          }
      }

      companion object {
          val Factory: ViewModelProvider.Factory = viewModelFactory {
              initializer {
                  val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                  PlacementQuizViewModel(
                      application.container.authRepository,
                      application.container.quizContentRepository,
                      application.container.quizResultRepository,
                  )
              }
          }
      }
  }
  ```

- [ ] **Step 6: Run the tests to verify they pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.example.nightwatchprotocol.ui.quiz.PlacementQuizViewModelTest"`
  Expected: `5 tests completed, 0 failed`.

- [ ] **Step 7: Build the screen**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizScreen.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.selection.selectable
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.RadioButton
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.collectAsState
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.platform.testTag
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.example.nightwatchprotocol.data.quiz.PlacementLevel
  import com.example.nightwatchprotocol.data.quiz.QuizCategory
  import com.example.nightwatchprotocol.ui.components.NightWatchButton
  import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

  @Composable
  fun PlacementQuizScreen(
      onFinished: (level: PlacementLevel, weakCategories: Set<QuizCategory>, saveFailed: Boolean) -> Unit,
      viewModel: PlacementQuizViewModel = viewModel(factory = PlacementQuizViewModel.Factory),
  ) {
      val uiState by viewModel.uiState.collectAsState()

      LaunchedEffect(viewModel) {
          viewModel.events.collect { event ->
              when (event) {
                  is PlacementQuizEvent.NavigateToResult ->
                      onFinished(event.level, event.weakCategories, event.saveFailed)
              }
          }
      }

      PlacementQuizScreenContent(
          uiState = uiState,
          onOptionSelected = viewModel::onOptionSelected,
          onNextClicked = viewModel::onNextClicked,
      )
  }

  @Composable
  private fun PlacementQuizScreenContent(
      uiState: PlacementQuizUiState,
      onOptionSelected: (Int) -> Unit,
      onNextClicked: () -> Unit,
  ) {
      val question = uiState.currentQuestion

      NightWatchScreenBackground {
          Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
              Text(uiState.progressLabel, color = NightWatchOnBackground)
              if (question != null) {
                  Text(
                      text = question.text,
                      color = NightWatchOnBackground,
                      style = MaterialTheme.typography.titleLarge,
                      modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                  )
                  question.options.forEachIndexed { index, option ->
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier
                              .fillMaxWidth()
                              .selectable(
                                  selected = uiState.selectedOptionIndex == index,
                                  onClick = { onOptionSelected(index) },
                              )
                              .testTag("quiz_option_$index")
                              .padding(vertical = 4.dp),
                      ) {
                          RadioButton(
                              selected = uiState.selectedOptionIndex == index,
                              onClick = { onOptionSelected(index) },
                          )
                          Text(option, color = NightWatchOnBackground)
                      }
                  }
              }
              NightWatchButton(
                  text = "Siguiente",
                  onClick = onNextClicked,
                  enabled = uiState.selectedOptionIndex != null,
                  modifier = Modifier.padding(top = 24.dp),
              )
          }
      }
  }
  ```

- [ ] **Step 8: Write the Compose UI test**

  This mounts `PlacementQuizScreen` directly (not through `NightWatchNavHost`, which isn't wired to this route until Task 8) — the screen takes no nav args, so it needs no `NavHost` to test in isolation. Create `app/src/androidTest/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizScreenTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import androidx.compose.ui.test.assertIsEnabled
  import androidx.compose.ui.test.assertIsNotEnabled
  import androidx.compose.ui.test.junit4.createComposeRule
  import androidx.compose.ui.test.onNodeWithTag
  import androidx.compose.ui.test.onNodeWithText
  import androidx.compose.ui.test.performClick
  import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
  import org.junit.Rule
  import org.junit.Test

  class PlacementQuizScreenTest {
      @get:Rule
      val composeTestRule = createComposeRule()

      @Test
      fun selectingAnOption_enablesNextButton() {
          composeTestRule.setContent {
              NightWatchProtocolTheme {
                  PlacementQuizScreen(onFinished = { _, _, _ -> })
              }
          }

          composeTestRule.onNodeWithText("Siguiente").assertIsNotEnabled()

          composeTestRule.onNodeWithTag("quiz_option_0").performClick()

          composeTestRule.onNodeWithText("Siguiente").assertIsEnabled()
      }
  }
  ```

- [ ] **Step 9: Run the test to verify it passes**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.ui.quiz.PlacementQuizScreenTest"`
  Expected: `1 test completed, 0 failed`.

- [ ] **Step 10: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt app/src/main/java/com/example/nightwatchprotocol/ui/quiz
  git add app/src/test/java/com/example/nightwatchprotocol/ui/quiz app/src/androidTest/java/com/example/nightwatchprotocol/ui/quiz/PlacementQuizScreenTest.kt
  git commit -m "feat: add Placement Quiz screen"
  ```

---

### Task 7: Placement Result screen

**Files:**
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultUiState.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultViewModel.kt`
- Create: `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultScreen.kt`
- Test: `app/src/androidTest/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultScreenTest.kt`

**Interfaces:**
- Consumes: `buildStudyPlan`/`PlacementLevel`/`QuizCategory` (Task 3), `NightWatchButton`/`NightWatchScreenBackground` (existing).
- Produces: `@Serializable data class PlacementResultRoute(level: String, weakCategories: List<String>, saveFailed: Boolean = false)` (nav route — primitives only, so it round-trips through nav-arg serialization without depending on the domain enums directly); `data class PlacementResultUiState(level: String, studyPlan: List<String>, saveFailed: Boolean)`; `sealed interface PlacementResultEvent { NavigateToMainMenu }`; `class PlacementResultViewModel(savedStateHandle: SavedStateHandle) : ViewModel()`; `@Composable fun PlacementResultScreen(onContinue: () -> Unit, viewModel: ... = viewModel(factory = PlacementResultViewModel.Factory))`. Task 8's `NightWatchNavHost` constructs `PlacementResultRoute` from Task 6's `onFinished` callback and wires `onContinue` to Main Menu.

- [ ] **Step 1: Add the `PlacementResultRoute` route**

  Edit `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`, adding this at the end of the file:

  ```kotlin

  @Serializable
  data class PlacementResultRoute(
      val level: String,
      val weakCategories: List<String>,
      val saveFailed: Boolean = false,
  )
  ```

- [ ] **Step 2: Define the UI state and event types**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultUiState.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  data class PlacementResultUiState(
      val level: String = "",
      val studyPlan: List<String> = emptyList(),
      val saveFailed: Boolean = false,
  )

  sealed interface PlacementResultEvent {
      data object NavigateToMainMenu : PlacementResultEvent
  }
  ```

- [ ] **Step 3: Implement the ViewModel**

  This ViewModel has no test of its own: its only job is decoding nav-route args and calling Task 3's already-tested `buildStudyPlan`, and its one behavior (a click sends one event) is exercised end-to-end by Step 5's Compose UI test. Create `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultViewModel.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import androidx.lifecycle.SavedStateHandle
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.createSavedStateHandle
  import androidx.lifecycle.viewModelScope
  import androidx.lifecycle.viewmodel.initializer
  import androidx.lifecycle.viewmodel.viewModelFactory
  import androidx.navigation.toRoute
  import com.example.nightwatchprotocol.data.quiz.PlacementLevel
  import com.example.nightwatchprotocol.data.quiz.QuizCategory
  import com.example.nightwatchprotocol.data.quiz.buildStudyPlan
  import com.example.nightwatchprotocol.ui.navigation.PlacementResultRoute
  import kotlinx.coroutines.channels.Channel
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.receiveAsFlow
  import kotlinx.coroutines.launch

  class PlacementResultViewModel(
      savedStateHandle: SavedStateHandle,
  ) : ViewModel() {

      private val route = savedStateHandle.toRoute<PlacementResultRoute>()
      private val level = runCatching { PlacementLevel.valueOf(route.level) }.getOrDefault(PlacementLevel.BAJO)
      private val weakCategories = route.weakCategories
          .mapNotNull { name -> runCatching { QuizCategory.valueOf(name) }.getOrNull() }
          .toSet()

      private val _uiState = MutableStateFlow(
          PlacementResultUiState(
              level = level.name,
              studyPlan = buildStudyPlan(level, weakCategories),
              saveFailed = route.saveFailed,
          ),
      )
      val uiState: StateFlow<PlacementResultUiState> = _uiState.asStateFlow()

      private val _events = Channel<PlacementResultEvent>(Channel.BUFFERED)
      val events: Flow<PlacementResultEvent> = _events.receiveAsFlow()

      fun onContinueClicked() {
          viewModelScope.launch { _events.send(PlacementResultEvent.NavigateToMainMenu) }
      }

      companion object {
          val Factory: ViewModelProvider.Factory = viewModelFactory {
              initializer {
                  PlacementResultViewModel(createSavedStateHandle())
              }
          }
      }
  }
  ```

  `runCatching { PlacementLevel.valueOf(...) }.getOrDefault(PlacementLevel.BAJO)` is defensive against a route arg that somehow doesn't match an enum name — it can't happen through this app's own navigation calls (Task 8 always passes a real `PlacementLevel.name`), but nav args are just strings at the type-system boundary, so a silent, safe default is more appropriate here than a crash.

- [ ] **Step 4: Build the screen**

  Create `app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultScreen.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.collectAsState
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.example.nightwatchprotocol.ui.components.NightWatchButton
  import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
  import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

  @Composable
  fun PlacementResultScreen(
      onContinue: () -> Unit,
      viewModel: PlacementResultViewModel = viewModel(factory = PlacementResultViewModel.Factory),
  ) {
      val uiState by viewModel.uiState.collectAsState()

      LaunchedEffect(viewModel) {
          viewModel.events.collect { event ->
              when (event) {
                  PlacementResultEvent.NavigateToMainMenu -> onContinue()
              }
          }
      }

      PlacementResultScreenContent(
          uiState = uiState,
          onContinueClicked = viewModel::onContinueClicked,
      )
  }

  @Composable
  private fun PlacementResultScreenContent(
      uiState: PlacementResultUiState,
      onContinueClicked: () -> Unit,
  ) {
      NightWatchScreenBackground {
          Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
              Text("Tu nivel: ${uiState.level}", color = NightWatchOnBackground)
              Text(
                  "Tu plan de estudio recomendado:",
                  color = NightWatchOnBackground,
                  modifier = Modifier.padding(top = 16.dp),
              )
              uiState.studyPlan.forEach { topic ->
                  Text("• $topic", color = NightWatchOnBackground, modifier = Modifier.padding(top = 8.dp))
              }
              if (uiState.saveFailed) {
                  Text(
                      "No pudimos guardar tu resultado; es posible que veas este quiz de nuevo la próxima vez.",
                      color = NightWatchOnBackground,
                      modifier = Modifier.padding(top = 16.dp),
                  )
              }
              NightWatchButton(
                  text = "Continuar",
                  onClick = onContinueClicked,
                  modifier = Modifier.padding(top = 24.dp),
              )
          }
      }
  }
  ```

- [ ] **Step 5: Write the Compose UI test**

  `PlacementResultViewModel` reads its level/plan from nav-route args via `SavedStateHandle`, which only get populated inside a real `composable<T>` entry — so this test builds a minimal single-route `NavHost` of its own instead of depending on the full `NightWatchNavHost` (not wired to this route until Task 8). Create `app/src/androidTest/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultScreenTest.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.quiz

  import androidx.compose.ui.test.junit4.createComposeRule
  import androidx.compose.ui.test.onNodeWithText
  import androidx.compose.ui.test.performClick
  import androidx.navigation.compose.NavHost
  import androidx.navigation.compose.composable
  import androidx.navigation.compose.rememberNavController
  import com.example.nightwatchprotocol.ui.navigation.PlacementResultRoute
  import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
  import org.junit.Rule
  import org.junit.Test

  class PlacementResultScreenTest {
      @get:Rule
      val composeTestRule = createComposeRule()

      @Test
      fun showsLevelAndStudyPlan_andContinueFiresCallback() {
          var continueClicked = false

          composeTestRule.setContent {
              NightWatchProtocolTheme {
                  val navController = rememberNavController()
                  NavHost(
                      navController = navController,
                      startDestination = PlacementResultRoute(
                          level = "MEDIO",
                          weakCategories = listOf("CONTRASENAS"),
                          saveFailed = false,
                      ),
                  ) {
                      composable<PlacementResultRoute> {
                          PlacementResultScreen(onContinue = { continueClicked = true })
                      }
                  }
              }
          }

          composeTestRule.onNodeWithText("Tu nivel: MEDIO").assertExists()

          composeTestRule.onNodeWithText("Continuar").performClick()

          assert(continueClicked) { "onContinue was not called after tapping Continuar" }
      }
  }
  ```

- [ ] **Step 6: Run the test to verify it passes**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.nightwatchprotocol.ui.quiz.PlacementResultScreenTest"`
  Expected: `1 test completed, 0 failed`.

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultUiState.kt app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultViewModel.kt app/src/main/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultScreen.kt app/src/androidTest/java/com/example/nightwatchprotocol/ui/quiz/PlacementResultScreenTest.kt
  git commit -m "feat: add Placement Result screen with basic study plan"
  ```

---

### Task 8: Navigation wiring

**Files:**
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchDestinations.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchNavHost.kt`
- Modify: `app/src/main/java/com/example/nightwatchprotocol/MainActivity.kt`

**Interfaces:**
- Consumes: every route, screen, and ViewModel from Tasks 5-7.
- Produces: the completed navigation graph — `Login`/`Register` success now target `PostAuthGate` instead of `MainMenu` directly; cold start with a remembered session targets `PostAuthGate` instead of `MainMenu` directly. No new public interface — this is the integration point.

- [ ] **Step 1: Confirm all three new routes exist**

  `NightWatchDestinations.kt` should now contain `PostAuthGate` (from Task 5), `PlacementQuiz` (from Task 6), and `PlacementResultRoute` (from Task 7) alongside the original `Login`/`Register`/`ForgotPassword`/`MainMenu`. No changes needed here — this step is just a sanity check before wiring the `NavHost`. Read the file and confirm all six route declarations are present.

- [ ] **Step 2: Wire the new screens into the nav graph**

  Replace the contents of `app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchNavHost.kt`:

  ```kotlin
  package com.example.nightwatchprotocol.ui.navigation

  import androidx.compose.runtime.Composable
  import androidx.navigation.NavHostController
  import androidx.navigation.compose.NavHost
  import androidx.navigation.compose.composable
  import androidx.navigation.compose.rememberNavController
  import com.example.nightwatchprotocol.data.quiz.QuizCategory
  import com.example.nightwatchprotocol.ui.auth.forgotpassword.ForgotPasswordScreen
  import com.example.nightwatchprotocol.ui.auth.login.LoginScreen
  import com.example.nightwatchprotocol.ui.auth.register.RegisterScreen
  import com.example.nightwatchprotocol.ui.mainmenu.MainMenuScreen
  import com.example.nightwatchprotocol.ui.postauth.PostAuthGateScreen
  import com.example.nightwatchprotocol.ui.quiz.PlacementQuizScreen
  import com.example.nightwatchprotocol.ui.quiz.PlacementResultScreen

  @Composable
  fun NightWatchNavHost(
      navController: NavHostController = rememberNavController(),
      startDestination: Any = Login,
  ) {
      NavHost(navController = navController, startDestination = startDestination) {
          composable<Login> {
              LoginScreen(
                  onLoginSuccess = {
                      navController.navigate(PostAuthGate) { popUpTo<Login> { inclusive = true } }
                  },
                  onNavigateToRegister = { navController.navigate(Register) },
                  onNavigateToForgotPassword = { navController.navigate(ForgotPassword) },
              )
          }
          composable<Register> {
              RegisterScreen(
                  onRegisterSuccess = {
                      navController.navigate(PostAuthGate) { popUpTo<Login> { inclusive = true } }
                  },
                  onNavigateBack = { navController.popBackStack() },
              )
          }
          composable<ForgotPassword> {
              ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
          }
          composable<PostAuthGate> {
              PostAuthGateScreen(
                  onNavigateToMainMenu = {
                      navController.navigate(MainMenu) { popUpTo<PostAuthGate> { inclusive = true } }
                  },
                  onNavigateToPlacementQuiz = {
                      navController.navigate(PlacementQuiz) { popUpTo<PostAuthGate> { inclusive = true } }
                  },
              )
          }
          composable<PlacementQuiz> {
              PlacementQuizScreen(
                  onFinished = { level, weakCategories, saveFailed ->
                      navController.navigate(
                          PlacementResultRoute(
                              level = level.name,
                              weakCategories = weakCategories.map(QuizCategory::name),
                              saveFailed = saveFailed,
                          ),
                      ) { popUpTo<PlacementQuiz> { inclusive = true } }
                  },
              )
          }
          composable<PlacementResultRoute> {
              PlacementResultScreen(
                  onContinue = {
                      navController.navigate(MainMenu) { popUpTo<PlacementResultRoute> { inclusive = true } }
                  },
              )
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

- [ ] **Step 3: Route cold starts with a remembered session through the gate**

  Edit `app/src/main/java/com/example/nightwatchprotocol/MainActivity.kt`:

  ```kotlin
  package com.example.nightwatchprotocol

  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.activity.enableEdgeToEdge
  import com.example.nightwatchprotocol.ui.navigation.Login
  import com.example.nightwatchprotocol.ui.navigation.NightWatchNavHost
  import com.example.nightwatchprotocol.ui.navigation.PostAuthGate
  import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme

  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          enableEdgeToEdge()
          val application = application as NightWatchApplication
          val startDestination = if (application.container.authRepository.currentUserEmail != null) {
              PostAuthGate
          } else {
              Login
          }
          setContent {
              NightWatchProtocolTheme {
                  NightWatchNavHost(startDestination = startDestination)
              }
          }
      }
  }
  ```

- [ ] **Step 4: Run the full unit and instrumented suites**

  Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
  Expected: `BUILD SUCCESSFUL`, every test from Tasks 1-8 passing, including the pre-existing Auth sub-project's tests (unaffected by these changes other than Login/Register's navigation target, which those tests don't assert on).

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/example/nightwatchprotocol/ui/navigation/NightWatchNavHost.kt app/src/main/java/com/example/nightwatchprotocol/MainActivity.kt
  git commit -m "feat: wire PostAuthGate and Placement Quiz into the navigation graph"
  ```

---
