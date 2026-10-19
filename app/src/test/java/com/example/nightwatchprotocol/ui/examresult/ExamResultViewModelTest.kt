package com.example.nightwatchprotocol.ui.examresult

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningSnapshot
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.realLearningContent
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExamResultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The real JSON, so the questions, the right options and their labels are the ones that ship.
    private val real = realLearningContent()
    private val content = FakeLearningContentRepository(real)
    private val questions = real.exam
    private val progress = RecordingProgress()

    private fun viewModel(answers: List<Int>, repository: RecordingProgress = progress) =
        ExamResultViewModel(answers, content, repository)

    private fun ExamResultViewModel.outcome(): ExamOutcome = (uiState.value.status as ExamResultStatus.Ready).outcome

    /** All the answers right, except the [wrongAt] questions. */
    private fun answers(vararg wrongAt: Int): List<Int> =
        questions.indices.map { if (it in wrongAt) (questions[it].correctIndex + 1) % 4 else questions[it].correctIndex }

    /** Nothing but the attempt may be written: no lesson, no lab and (by construction) no placement. */
    private fun assertOnlyAttemptsWritten(repository: RecordingProgress = progress) {
        assertEquals(0, repository.lessonWrites)
        assertEquals(0, repository.labWrites)
    }

    // ---- grading and what the result says ----

    @Test
    fun sixRight_isPerfect_andRegistersSix() = runTest {
        val vm = viewModel(answers())
        advanceUntilIdle()

        assertEquals(ExamOutcome.Perfect, vm.outcome())
        assertEquals(listOf(6), progress.attempts)
    }

    @Test
    fun oneError_inAnyQuestion_passes_andNamesTheTopicAndTheRightOption() = runTest {
        questions.indices.forEach { failed ->
            val repository = RecordingProgress()
            val vm = viewModel(answers(failed), repository)
            advanceUntilIdle()

            val question = questions[failed]
            assertEquals(
                ExamOutcome.OneError(question.topicLabel, question.optionLabel(question.correctIndex)),
                vm.outcome(),
            )
            assertEquals(listOf(5), repository.attempts)
        }
    }

    @Test
    fun theFirstQuestionFailed_showsTheFigmaExample() = runTest {
        val vm = viewModel(answers(0))
        advanceUntilIdle()

        assertEquals(ExamOutcome.OneError("Contraseñas y Autenticación", "C) k7#mQ!9zRt$2"), vm.outcome())
    }

    @Test
    fun fiveRight_isTheEdgeOfPassing() = runTest {
        val vm = viewModel(answers(3))
        advanceUntilIdle()

        assertTrue(vm.outcome() is ExamOutcome.OneError)
        assertEquals(listOf(5), progress.attempts)
    }

    @Test
    fun twoErrors_doNotPass_andNameNothing() = runTest {
        val vm = viewModel(answers(1, 4))
        advanceUntilIdle()

        assertEquals(ExamOutcome.NotPassed, vm.outcome())
        assertEquals(listOf(4), progress.attempts)
        val shown = vm.uiState.value.toString()
        assertTrue(questions.none { shown.contains(it.topicLabel) || shown.contains(it.optionLabel(it.correctIndex)) })
    }

    @Test
    fun sixErrors_doNotPass_andRegisterZero() = runTest {
        val vm = viewModel(answers(0, 1, 2, 3, 4, 5))
        advanceUntilIdle()

        assertEquals(ExamOutcome.NotPassed, vm.outcome())
        assertEquals(listOf(0), progress.attempts)
    }

    @Test
    fun everyPairOfErrors_isTheSameNotPassedResult() = runTest {
        val results = mutableSetOf<ExamOutcome>()
        for (a in questions.indices) for (b in a + 1 until questions.size) {
            val vm = viewModel(answers(a, b), RecordingProgress())
            advanceUntilIdle()
            results += vm.outcome()
        }
        assertEquals(setOf<ExamOutcome>(ExamOutcome.NotPassed), results)
    }

    // ---- saving: one attempt, only correct_count ----

    @Test
    fun aFinishedAttempt_registersExactlyOne_withTheCountOfRightAnswers() = runTest {
        val vm = viewModel(answers(2, 5))
        advanceUntilIdle()

        assertEquals(listOf(4), progress.attempts)
        assertEquals(ExamSaveStatus.Saved, vm.uiState.value.save)
        assertOnlyAttemptsWritten()
    }

    @Test
    fun beforeTheContentArrives_itIsLoading_andNothingIsSent() = runTest {
        val vm = viewModel(answers())

        assertEquals(ExamResultStatus.Loading, vm.uiState.value.status)
        assertEquals(ExamSaveStatus.NotStarted, vm.uiState.value.save)
        assertTrue(progress.attempts.isEmpty())
    }

    @Test
    fun aFailedSave_stillShowsTheResult_andTheAttemptIsNotRegistered() = runTest {
        mapOf(
            FailureKind.Offline to true,
            FailureKind.PermissionDenied to false,
            FailureKind.Unknown to false,
        ).forEach { (kind, offline) ->
            val repository = RecordingProgress().apply { failure = kind }
            val vm = viewModel(answers(1), repository)
            advanceUntilIdle()

            assertEquals(ExamSaveStatus.Failed(offline), vm.uiState.value.save)
            assertTrue(vm.outcome() is ExamOutcome.OneError)
            assertTrue(repository.attempts.isEmpty())
        }
    }

    @Test
    fun aFailedSave_keepsNoTraceOfTheException() = runTest {
        progress.failure = FailureKind.Offline
        val vm = viewModel(answers())
        advanceUntilIdle()

        assertFalse(vm.uiState.value.toString().contains("servidor", ignoreCase = true))
    }

    @Test
    fun retrySave_afterAFailure_registersTheAttempt() = runTest {
        progress.failure = FailureKind.Offline
        val vm = viewModel(answers())
        advanceUntilIdle()

        progress.failure = null
        vm.onRetrySaveClicked()
        advanceUntilIdle()

        assertEquals(ExamSaveStatus.Saved, vm.uiState.value.save)
        assertEquals(listOf(6), progress.attempts)
        assertEquals(2, progress.attemptCalls)
    }

    @Test
    fun retrySave_thatFailsAgain_staysFailed_andCanBeTriedOnceMore() = runTest {
        progress.failure = FailureKind.Unknown
        val vm = viewModel(answers(0, 1))
        advanceUntilIdle()

        vm.onRetrySaveClicked()
        advanceUntilIdle()
        assertEquals(ExamSaveStatus.Failed(offline = false), vm.uiState.value.save)

        progress.failure = null
        vm.onRetrySaveClicked()
        advanceUntilIdle()
        assertEquals(ExamSaveStatus.Saved, vm.uiState.value.save)
        assertEquals(listOf(4), progress.attempts)
    }

    @Test
    fun retrySave_afterItWasSaved_doesNothing() = runTest {
        val vm = viewModel(answers())
        advanceUntilIdle()

        vm.onRetrySaveClicked()
        advanceUntilIdle()

        assertEquals(1, progress.attemptCalls)
    }

    @Test
    fun whileSaving_aRetryTap_doesNotSaveAgain() = runTest {
        val gated = RecordingProgress().apply { gate = CompletableDeferred() }
        val vm = viewModel(answers(), gated)
        advanceUntilIdle()
        assertEquals(ExamSaveStatus.Saving, vm.uiState.value.save)

        vm.onRetrySaveClicked()
        vm.onRetrySaveClicked()
        advanceUntilIdle()
        gated.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, gated.attemptCalls)
        assertEquals(ExamSaveStatus.Saved, vm.uiState.value.save)
    }

    @Test
    fun aDoubleTapOnRetrySave_afterAFailure_savesOnlyOnce() = runTest {
        val gated = RecordingProgress().apply { failure = FailureKind.Offline }
        val vm = viewModel(answers(), gated)
        advanceUntilIdle()

        gated.failure = null
        gated.gate = CompletableDeferred()
        vm.onRetrySaveClicked()
        vm.onRetrySaveClicked()
        advanceUntilIdle()
        gated.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, gated.attemptCalls)
        assertEquals(listOf(6), gated.attempts)
    }

    // ---- new attempts ----

    @Test
    fun aNewAttempt_afterNotPassing_registersItsOwnRow() = runTest {
        viewModel(answers(0, 1))
        advanceUntilIdle()
        viewModel(answers())
        advanceUntilIdle()

        assertEquals(listOf(4, 6), progress.attempts)
    }

    @Test
    fun aNewAttempt_afterPassing_registersItsOwnRow_withoutError() = runTest {
        viewModel(answers())
        advanceUntilIdle()
        val second = viewModel(answers(2))
        advanceUntilIdle()

        assertEquals(listOf(6, 5), progress.attempts)
        assertEquals(ExamSaveStatus.Saved, second.uiState.value.save)
        assertOnlyAttemptsWritten()
    }

    // ---- buttons and rows ----

    @Test
    fun everyDestination_isSent_afterTheSave() = runTest {
        val vm = viewModel(answers(0, 1))
        advanceUntilIdle()

        vm.events.test {
            ExamDestination.entries.forEach { destination ->
                vm.onDestinationClicked(destination)
                assertEquals(ExamResultEvent.Go(destination), awaitItem())
            }
        }
    }

    @Test
    fun theButtons_waitForTheSave_andWorkAfterSavedOrFailed() = runTest {
        val gated = RecordingProgress().apply { gate = CompletableDeferred() }
        val vm = viewModel(answers(), gated)
        advanceUntilIdle()

        vm.events.test {
            vm.onDestinationClicked(ExamDestination.Path)
            expectNoEvents()

            gated.gate!!.complete(Unit)
            advanceUntilIdle()
            vm.onDestinationClicked(ExamDestination.Path)
            assertEquals(ExamResultEvent.Go(ExamDestination.Path), awaitItem())
        }

        val failed = viewModel(answers(0, 1), RecordingProgress().apply { failure = FailureKind.Offline })
        advanceUntilIdle()
        failed.events.test {
            failed.onDestinationClicked(ExamDestination.NewAttempt)
            assertEquals(ExamResultEvent.Go(ExamDestination.NewAttempt), awaitItem())
        }
    }

    // ---- read error ----

    @Test
    fun answersThatDoNotMatchTheExam_failWithoutSavingAnything() = runTest {
        val vm = viewModel(listOf(0, 1))
        advanceUntilIdle()

        assertEquals(ExamResultStatus.Failed, vm.uiState.value.status)
        assertTrue(progress.attempts.isEmpty())
    }

    @Test
    fun contentThatCannotBeRead_fails_andRetryRecovers_savingOnce() = runTest {
        content.failure = DataAccessException(FailureKind.Unknown)
        val vm = viewModel(answers())
        advanceUntilIdle()
        assertEquals(ExamResultStatus.Failed, vm.uiState.value.status)
        assertTrue(progress.attempts.isEmpty())

        content.failure = null
        vm.load()
        advanceUntilIdle()

        assertEquals(listOf(6), progress.attempts)
    }

    // ---- decision P5: nothing but exam_attempts ----

    @Test
    fun theExamScreens_haveNoWayToWriteThePlacementOrAnyOtherTable() {
        // The result and the exam classes only receive the content and the progress repository: no quiz result, no profile.
        val folders = listOf("ui/exam", "ui/examresult")
        val forbidden = listOf("QuizResult", "PlacementResult", "PlacementLevel", "ProfileRepository", "AccountRepository", "supabase", "postgrest")
        folders.forEach { folder ->
            val directory = listOf(
                File("src/main/java/com/example/nightwatchprotocol/$folder"),
                File("app/src/main/java/com/example/nightwatchprotocol/$folder"),
            ).first { it.isDirectory }
            directory.listFiles { file -> file.extension == "kt" }!!.forEach { file ->
                val code = file.readLines().filterNot { it.trim().let { line -> line.startsWith("*") || line.startsWith("/*") || line.startsWith("//") } }
                    .joinToString("\n")
                forbidden.forEach { word ->
                    assertFalse("${file.name} menciona $word", code.contains(word, ignoreCase = true))
                }
                assertFalse("${file.name} menciona placement", code.contains("placement", ignoreCase = true))
                assertFalse("${file.name} usa markLab/markLesson", code.contains("markLab") || code.contains("markLesson"))
            }
        }
    }
}

/**
 * A progress repository that counts every kind of write (only [recordExamAttempt] is allowed here), can fail on demand and
 * can hold the write on a gate to test taps while saving. [attempts] keeps only what was really registered.
 */
class RecordingProgress : LearningProgressRepository {
    val attempts = mutableListOf<Int>()
    var attemptCalls = 0
    var lessonWrites = 0
    var labWrites = 0
    var failure: FailureKind? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getSnapshot() = LearningSnapshot.Empty

    override suspend fun markLessonViewed(topic: LearningTopic) {
        lessonWrites++
    }

    override suspend fun markLabCompleted(lab: LearningTopic) {
        labWrites++
    }

    override suspend fun recordExamAttempt(correctCount: Int) {
        attemptCalls++
        gate?.await()
        failure?.let { throw DataAccessException(it) }
        attempts += correctCount
    }
}
