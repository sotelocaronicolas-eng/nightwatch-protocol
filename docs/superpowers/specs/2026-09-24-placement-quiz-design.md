# Placement Quiz & Basic Study Plan — Design Spec

> **Documento histórico.** Describe el proyecto cuando usaba Firebase (versión 1.x). La app hoy usa solo Supabase: ver
> [`migracion-supabase.md`](../reports/migracion-supabase.md).

## Context

NightWatch Protocol is a gamified cybersecurity-learning Android app being
ported from a Figma design
(`https://www.figma.com/design/wd3fQTzF5NqsfGfdR4I1lb/NightWatch-Protocol--copia-`)
into Kotlin + Jetpack Compose. The project is split into one sub-project per
subsystem: **Auth & Onboarding** (shipped — Login, Register, Forgot Password,
Google Sign-In, session persistence, a placeholder Main Menu), **Core
learning** (map, lessons, labs, final exam), **Profile & Settings**, and
**Social/Gamification**.

The Auth & Onboarding spec explicitly deferred the adaptive placement quiz
(BAJO/MEDIO/ALTO) and study-plan generation that follows login in the Figma
flow to "its own sub-project once a user can sign in." That is now the case,
and this spec covers that sub-project.

The Figma placement-quiz screens are intentionally basic (simple single-choice
question cards). Per product decision, this spec upgrades the actual question
*content* to be more rigorous than the Figma mock while keeping the
underlying data model/storage as simple as the rest of the project (no new
backend infrastructure beyond Firestore, which this spec introduces).

## Goal

After a user's first successful login or registration, present an adaptive
placement quiz that scores their cybersecurity knowledge, assigns a level
(BAJO/MEDIO/ALTO), and shows a basic study plan (a prioritized list of
recommended topics) based on that level and their weakest categories. Returning
users who already completed the quiz skip straight to Main Menu.

## Scope

**In scope:**
- A 20-question local question bank covering 4 categories: Conceptos básicos
  de seguridad, Contraseñas/autenticación, Ingeniería social/phishing, Redes y
  malware básico (5 questions per category).
- A `PostAuthGate` step that decides, after login/register success and on
  cold start with a remembered session, whether the user goes to the
  placement quiz or straight to Main Menu.
- `PlacementQuizScreen`: one question at a time, single-choice answers, a
  disabled-until-selected "Siguiente" button, a simple progress indicator, no
  timer.
- Weighted scoring by difficulty and per-category minimums, mapping to
  BAJO/MEDIO/ALTO.
- `PlacementResultScreen`: shows the assigned level and a basic study plan — a
  prioritized list of recommended topics driven by the level and the
  category(ies) the user scored weakest in.
- Persistence of the quiz result (level, weak categories, completion
  timestamp) in Firestore, keyed by Firebase UID, so the quiz is not repeated
  on future logins or other devices.

**Explicitly out of scope (deferred to later sub-projects):**
- The real Core Learning map, navigable lessons, labs, and final exam. The
  study plan produced here is a static list of topic names, not navigable
  content — the same way Main Menu was left a placeholder in the Auth
  sub-project.
- Retaking the quiz later to update one's level. There is no UI entry point
  to redo the quiz once completed; that belongs with a future Profile &
  Settings sub-project.
- Any backend-editable question bank (e.g., authoring questions from a web
  console). Questions are bundled with the app; changing them requires a new
  build, which is acceptable at this stage of development.
- Per-category remediation logic beyond surfacing weak categories in the
  plan's ordering (e.g., no adaptive re-testing of weak areas).

## User Flows

### Post-auth gate
1. On cold start, if `authRepository.currentUserEmail` is non-null (a
   remembered session), the app's start destination is `PostAuthGate` instead
   of `MainMenu` directly.
2. On successful Login or Register, navigation also goes to `PostAuthGate`
   (replacing the previous direct-to-`MainMenu` navigation), popping the auth
   back stack as before.
3. `PostAuthGate` calls `QuizResultRepository.getResult(uid)` once (a single
   Firestore read) while showing a brief loading state.
   - Result exists → navigate to `MainMenu`, popping `PostAuthGate` off the
     back stack (`popUpTo<PostAuthGate> { inclusive = true }`).
   - No result → navigate to `PlacementQuiz`, same pop behavior.
   - Read fails (network/unknown error) → treat as "no result" and proceed to
     the quiz; a user is never permanently blocked from reaching Main Menu by
     a transient Firestore error, since the quiz can simply be completed (or
     its result overwritten on next successful save) without harm.

### Placement Quiz (frame reference: Figma's placement-quiz screens, adapted —
content is more advanced than the Figma mock; visual style/components reused
from the existing Login/Register screens)
1. `QuizContentRepository` loads the 20-question bank from the bundled JSON
   asset (parsed once, cached in memory) and draws 11 questions per attempt:
   3 questions each from 3 of the 4 categories and 2 from the remaining one
   (which category gets 2 is randomized per attempt), then shuffles the order
   of the 11.
2. One question shown at a time with 4 single-choice options; "Siguiente" is
   disabled until an option is selected. No back navigation between
   questions, no timer.
3. After the last question, the ViewModel scores the attempt (see Scoring
   below), saves the result via `QuizResultRepository.saveResult(uid, result)`,
   then emits a one-time navigation event to `PlacementResult`.
4. If the save fails, the result is still shown to the user (from in-memory
   state) but a small inline note indicates it may not have been saved; the
   user proceeds to Main Menu regardless — a failed save is not allowed to
   block progress, matching the gate's fail-open behavior above. (The user
   will simply see the quiz again next login, which is an acceptable
   trade-off over a hard blocker.)

### Placement Result / Basic Study Plan
1. Shows the assigned level ("Tu nivel: MEDIO") and a study plan: a static,
   per-level list of topic names (defined in code, not derived from
   questions) reordered so that topics matching the user's weak categories
   are listed first.
2. A single "Continuar" button navigates to `MainMenu`.

## Architecture

- **Pattern:** unchanged from Auth — stateful `XScreen` + stateless
  `XScreenContent` Composables, `ViewModel` exposing one `StateFlow<XUiState>`
  plus a `Channel<XEvent>` for one-time navigation events, manual DI through
  `AppContainer`.
- **Navigation:** two new type-safe routes, `PostAuthGate` and
  `PlacementQuiz`/`PlacementResult` (the result screen carries the computed
  level/plan via the navigation argument or a ViewModel shared through the
  nav graph scope — implementation detail for the plan to pin down), added to
  the existing `NightWatchDestinations`/`NightWatchNavHost`. `MainActivity`'s
  start-destination check changes from `currentUserEmail != null -> MainMenu`
  to `currentUserEmail != null -> PostAuthGate`. Login/Register's
  `onLoginSuccess`/`onRegisterSuccess` callbacks navigate to `PostAuthGate`
  instead of `MainMenu`, still popping the auth back stack.
- **Data layer:**
  - `QuizContentRepository` (interface + local-asset implementation): loads
    and parses `assets/quiz_questions.json` via `kotlinx.serialization`,
    exposes a function to draw one randomized 11-question attempt respecting
    the per-category minimum described above.
  - `QuizResultRepository` (interface + Firestore implementation): reads and
    writes a single document per user at `users/{uid}/placement/result` with
    fields `level` (string enum), `weakCategories` (list of strings),
    `completedAt` (timestamp). No other Firestore collections are introduced
    by this spec.
  - Both repositories are added to `AppContainer` alongside the existing
    `authRepository`/`sessionPreferences`, following the same manual-DI
    pattern.
- **Scoring (pure function, no Android/Firebase dependency):**
  - Each question has a difficulty weight (e.g., 1–3). A correct answer
    scores its weight; an incorrect answer scores 0.
  - Per-category percentage = (points earned in that category) / (points
    possible in that category) for the questions actually drawn in this
    attempt.
  - Overall percentage = total points earned / total points possible across
    all 11 drawn questions.
  - Level mapping: 0–40% → BAJO, 41–75% → MEDIO, 76–100% → ALTO (exact
    boundaries confirmed at plan time, easy to tune later since it's one pure
    function).
  - Weak categories = any category scoring below a fixed threshold (e.g.,
    60%) among the categories actually represented in that attempt.
- **Question data model:**
  ```json
  {
    "id": "string",
    "category": "CONCEPTOS_BASICOS | CONTRASENAS | INGENIERIA_SOCIAL | REDES_MALWARE",
    "difficulty": 1,
    "text": "string",
    "options": ["string", "string", "string", "string"],
    "correctIndex": 0
  }
  ```

## Content

The 20 questions (more advanced than the Figma mock's placeholder copy) are
authored during implementation as part of the plan's tasks, reviewed for
technical accuracy the same way any other content in this codebase is
reviewed — not hand-waved as "TBD." The basic study plan's per-level topic
lists are similarly authored at that time, aligned with the categories above
so weak-category reordering has real topics to promote.

## Testing Strategy

- **Unit tests** for the scoring function: covers boundary values at each
  level threshold, an attempt with no weak categories, an attempt where every
  category is weak, and the category-with-2-questions edge case not skewing
  its percentage unfairly.
- **ViewModel unit tests** for the quiz flow and `PostAuthGate`, each against
  `FakeQuizContentRepository`/`FakeQuizResultRepository` (in-memory fakes,
  matching the existing `FakeAuthRepository` pattern): happy path through all
  11 questions, gate routing to `MainMenu` vs `PlacementQuiz`, and the
  fail-open behavior when the Firestore read/write fails.
- **Compose UI tests**: answering a question enables "Siguiente", the full
  quiz-to-result navigation flow, and the result screen rendering the
  level/plan it's given.
- No instrumented test talks to real Firestore; `QuizResultRepository`'s
  Firestore implementation is exercised only indirectly through the interface
  contract its fake mirrors, matching the Auth sub-project's approach to
  `FirebaseAuthRepository`.

## Global Constraints

- Same Kotlin/AGP/SDK versions as the rest of the project (unchanged).
- New dependency: `firebase-firestore`, added via the existing
  `firebase-bom` already in the project (verify current BOM covers a recent
  stable Firestore artifact at implementation time).
- Firestore must be enabled in the Firebase console (Firestore Database →
  Create database) and its security rules restricted so a user can only
  read/write their own `users/{uid}/placement/result` document. This is a
  manual, interactive step for the human partner, the same way enabling
  Email/Password and Google providers was in the Auth sub-project — the plan
  must call this out as a runbook step, with the agent verifying the rules
  deployed afterward.
- The bundled question JSON ships inside the APK; updating question content
  requires a new app build/release (accepted trade-off, see Scope).

## Open Risks / Notes

- **Fail-open on Firestore errors** (both the gate's read and the quiz's
  save) means a user could, in rare cases, see the placement quiz more than
  once if a save silently failed. This is a deliberate trade-off favoring
  "never block the user from reaching Main Menu" over "never repeat the
  quiz" — worth a one-line mention in QA notes.
- **Content quality is load-bearing for this spec's value** — the placement
  quiz only does its job if the 20 questions are genuinely more rigorous than
  Figma's placeholders and the per-category weighting is balanced. This
  should get real scrutiny during implementation/review, not be treated as
  filler text.
- **Result-screen data passing** (navigation argument vs. shared ViewModel
  scoped to the quiz→result nav graph) is left as an implementation-time
  decision for the plan, since either is consistent with the existing
  architecture and the choice doesn't affect user-facing behavior.
