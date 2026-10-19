package com.example.nightwatchprotocol.ui.examresult

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.device.DeviceIdStore
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.FakeDeviceInfoProvider
import com.example.nightwatchprotocol.data.device.FakeDeviceRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.realLearningContent
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import com.example.nightwatchprotocol.ui.exam.ExamEvent
import com.example.nightwatchprotocol.ui.exam.ExamViewModel
import com.example.nightwatchprotocol.ui.home.HomeTab
import com.example.nightwatchprotocol.ui.home.HomeViewModel
import com.example.nightwatchprotocol.ui.labs.LabsViewModel
import com.example.nightwatchprotocol.ui.path.LearningPathViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The whole road of stage 5 with the real exam and the in-memory repositories: the questions, the result that registers the
 * attempt, and the three tabs that read the progress again when Home comes back (same mechanism as stage 4B).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamCompletionFlowTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val quiz = FakeQuizResultRepository()
    private val progress = FakeLearningProgressRepository()
    private val content = FakeLearningContentRepository(realLearningContent())
    private val questions = realLearningContent().exam

    private val profile = FakeProfileRepository()
    private val avatars = AvatarRepository(profile, FakeAvatarCache(), UnconfinedTestDispatcher())
    private val sessionManager = DeviceSessionManager(
        auth,
        FakeDeviceRepository(),
        DeviceIdStore.inMemory { "device-me" },
        FakeDeviceInfoProvider(),
        avatars,
    )

    // Lazy: a ViewModel must be created after MainDispatcherRule sets the Main dispatcher, which happens after the fields.
    private val path by lazy { LearningPathViewModel(auth, quiz, progress, content) }
    private val labs by lazy { LabsViewModel(progress, content) }
    private val home by lazy {
        HomeViewModel(
            initialTab = HomeTab.Inicio,
            savedStateHandle = SavedStateHandle(),
            authRepository = auth,
            accountRepository = FakeAccountRepository(),
            avatarRepository = avatars,
            quizResultRepository = quiz,
            learningProgressRepository = progress,
            learningContentRepository = content,
            sessionManager = sessionManager,
        )
    }

    private fun completeSixIslands() {
        LearningTopic.entries.forEach {
            progress.viewedLessons += it
            progress.completedLabs += it
        }
    }

    private fun refreshAllTabs() {
        path.refresh()
        labs.refresh()
        home.refreshProgress()
    }

    /** Answers the six questions in the screen, with [wrongAt] wrong, and opens the result the way the screens do. */
    private suspend fun TestScope.takeExam(vararg wrongAt: Int): ExamResultViewModel {
        val exam = ExamViewModel(SavedStateHandle(), content)
        advanceUntilIdle()
        var result: ExamResultViewModel? = null
        exam.events.test {
            questions.forEachIndexed { index, question ->
                val option = if (index in wrongAt) (question.correctIndex + 1) % 4 else question.correctIndex
                exam.onOptionSelected(index, option)
            }
            val finished = awaitItem() as ExamEvent.Finished
            result = ExamResultViewModel(finished.answers, content, progress)
        }
        advanceUntilIdle()
        return result!!
    }

    @Test
    fun aPassedAttempt_completesTheFinalTest_inRuta_Labs_andShowsSevenOfSeven() = runTest {
        completeSixIslands()
        refreshAllTabs()
        advanceUntilIdle()
        assertEquals(IslandState.Available, path.uiState.value.finalTest)
        assertEquals(6, path.uiState.value.completedCount)

        val result = takeExam()
        assertEquals(ExamSaveStatus.Saved, result.uiState.value.save)
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(IslandState.Completed, path.uiState.value.finalTest)
        assertEquals(7, path.uiState.value.completedCount)
        assertEquals(7, path.uiState.value.totalCount)
        assertEquals(IslandState.Completed, labs.uiState.value.finalTest)
        assertTrue(path.uiState.value.islands.all { it.state == IslandState.Completed })
    }

    @Test
    fun aPassedAttemptWithOneError_countsTheSame() = runTest {
        completeSixIslands()

        takeExam(3)
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(IslandState.Completed, path.uiState.value.finalTest)
        assertEquals(7, path.uiState.value.completedCount)
    }

    @Test
    fun aNotPassedAttempt_doesNotCompleteTheFinalTest_inAnyTab() = runTest {
        completeSixIslands()

        val result = takeExam(0, 1)
        assertEquals(ExamSaveStatus.Saved, result.uiState.value.save)
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(listOf(4), progress.examAttempts)
        assertEquals(IslandState.Available, path.uiState.value.finalTest)
        assertEquals(6, path.uiState.value.completedCount)
        assertEquals(IslandState.Available, labs.uiState.value.finalTest)
    }

    @Test
    fun aNewAttemptAfterNotPassing_canPass_andThenCounts() = runTest {
        completeSixIslands()

        takeExam(0, 1)
        takeExam()
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(listOf(4, 6), progress.examAttempts)
        assertEquals(IslandState.Completed, path.uiState.value.finalTest)
    }

    @Test
    fun afterPassing_theExamCanBeTakenAgain_andStaysCompleted() = runTest {
        completeSixIslands()
        takeExam()

        takeExam(0, 1)
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(listOf(6, 4), progress.examAttempts)
        // A passed attempt counts for good: a later failed one does not take it back.
        assertEquals(IslandState.Completed, path.uiState.value.finalTest)
    }

    @Test
    fun aFailedSave_doesNotCompleteTheFinalTest_untilTheRetrySucceeds() = runTest {
        completeSixIslands()
        progress.writeFailure = FailureKind.Offline

        val result = takeExam()
        refreshAllTabs()
        advanceUntilIdle()
        assertEquals(ExamSaveStatus.Failed(offline = true), result.uiState.value.save)
        assertEquals(IslandState.Available, path.uiState.value.finalTest)

        progress.writeFailure = null
        result.onRetrySaveClicked()
        advanceUntilIdle()
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(IslandState.Completed, path.uiState.value.finalTest)
        assertEquals(7, path.uiState.value.completedCount)
    }

    @Test
    fun anAbandonedExam_registersNothing() = runTest {
        completeSixIslands()
        val exam = ExamViewModel(SavedStateHandle(), content)
        advanceUntilIdle()

        exam.onOptionSelected(0, 2)
        exam.onOptionSelected(1, 1)
        exam.onCloseClicked()
        refreshAllTabs()
        advanceUntilIdle()

        assertTrue(progress.examAttempts.isEmpty())
        assertEquals(IslandState.Available, path.uiState.value.finalTest)
    }

    @Test
    fun passingNeverTouchesThePlacementOrTheLevel_norLessonsNorLabs() = runTest {
        completeSixIslands()
        quiz.getResultResult = Result.success(PlacementResult(PlacementLevel.BAJO, emptySet()))
        val viewedBefore = progress.viewedLessons.toSet()
        val labsBefore = progress.completedLabs.toSet()
        val writesBefore = progress.writeCallCount

        takeExam()
        takeExam(1, 2)
        refreshAllTabs()
        advanceUntilIdle()

        // Only the two attempts were written, to exam_attempts; the quiz result was never saved and the level is the same.
        assertEquals(writesBefore + 2, progress.writeCallCount)
        assertEquals(0, quiz.saveResultCallCount)
        assertEquals(null, quiz.lastSavedResult)
        assertEquals(PlacementLevel.BAJO.name, path.uiState.value.level)
        assertEquals(PlacementLevel.BAJO.name, home.uiState.value.level)
        assertEquals(viewedBefore, progress.viewedLessons)
        assertEquals(labsBefore, progress.completedLabs)
        assertFalse(progress.examAttempts.isEmpty())
    }
}
