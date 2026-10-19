package com.example.nightwatchprotocol.ui.labreport

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
import com.example.nightwatchprotocol.data.learning.deriveLearningProgress
import com.example.nightwatchprotocol.data.learning.realLearningContent
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.ui.home.DailyLessonState
import com.example.nightwatchprotocol.ui.home.HomeTab
import com.example.nightwatchprotocol.ui.home.HomeViewModel
import com.example.nightwatchprotocol.ui.lab.LabEvent
import com.example.nightwatchprotocol.ui.lab.LabViewModel
import com.example.nightwatchprotocol.ui.labs.LabsViewModel
import com.example.nightwatchprotocol.ui.path.LearningPathViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The whole road of stage 4B with the real lab texts and the in-memory repositories: the terminal, "Finalizar laboratorio",
 * the report that saves, and the three tabs that read the progress again when Home comes back (ON_RESUME: `refresh()` on
 * Ruta and Labs, `refreshProgress()` on Inicio).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LabCompletionFlowTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val quiz = FakeQuizResultRepository()
    private val progress = FakeLearningProgressRepository()
    private val content = FakeLearningContentRepository(realLearningContent())

    private val profile = FakeProfileRepository()
    private val avatars = AvatarRepository(profile, FakeAvatarCache(), UnconfinedTestDispatcher())
    private val devices = FakeDeviceRepository()
    private val sessionManager = DeviceSessionManager(
        auth,
        devices,
        DeviceIdStore.inMemory { "device-me" },
        FakeDeviceInfoProvider(),
        avatars,
    )

    // Lazy: a ViewModel must be created after MainDispatcherRule sets the Main dispatcher, which happens after the fields.
    private val path by lazy { LearningPathViewModel(auth, quiz, progress, content) }
    private val labs by lazy { LabsViewModel(progress, content) }
    private val home by lazy { newHome() }

    private fun newHome() = HomeViewModel(
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

    private fun refreshAllTabs() {
        path.refresh()
        labs.refresh()
        home.refreshProgress()
    }

    /** Plays the lab in the terminal, taps "Finalizar" and opens the report the way the screens do. */
    private suspend fun kotlinx.coroutines.test.TestScope.finishLab(topic: LearningTopic, objectives: List<Int>): LabReportViewModel {
        val lab = realLearningContent().lab(topic)
        val terminal = LabViewModel(topic, SavedStateHandle(), content)
        advanceUntilIdle()
        objectives.forEach {
            terminal.onObjectiveClicked(it)
            terminal.onSendClicked()
        }
        var report: LabReportViewModel? = null
        terminal.events.test {
            terminal.onFinishClicked()
            val event = awaitItem() as LabEvent.OpenReport
            assertEquals(topic, event.topic)
            assertEquals(lab.objectives.indices.filter { it in objectives }, event.completedObjectives)
            report = LabReportViewModel(event.topic, event.completedObjectives.toSet(), content, progress)
        }
        advanceUntilIdle()
        return report!!
    }

    // ---- the pure rules ----

    @Test
    fun registeringTheLab_completesTheIsland_andUnlocksTheNext_inTheDerivedProgress() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        val before = deriveLearningProgress(progress.getSnapshot())
        assertEquals(IslandState.Available, before.island(LearningTopic.CONTRASENAS).state)
        assertEquals(IslandState.Locked, before.island(LearningTopic.PHISHING).state)

        progress.markLabCompleted(LearningTopic.CONTRASENAS)

        val after = deriveLearningProgress(progress.getSnapshot())
        assertEquals(IslandState.Completed, after.island(LearningTopic.CONTRASENAS).state)
        assertEquals(IslandState.Available, after.island(LearningTopic.PHISHING).state)
        assertEquals(1, after.completedCount)
    }

    // ---- the three tabs ----

    @Test
    fun completingALab_updatesRuta_Labs_andInicio_onTheNextRead() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        refreshAllTabs()
        advanceUntilIdle()
        assertEquals(IslandState.Available, path.uiState.value.islands[0].state)
        assertEquals(IslandState.Locked, path.uiState.value.islands[1].state)
        assertEquals(0, path.uiState.value.completedCount)
        assertEquals(IslandState.Available, labs.uiState.value.labs[0].state)
        assertEquals(IslandState.Locked, labs.uiState.value.labs[1].state)
        // Lesson 1 seen, lab 1 missing, island 2 locked: nothing is pending.
        assertEquals(DailyLessonState.NoneLeft, home.uiState.value.dailyLesson)

        val report = finishLab(LearningTopic.CONTRASENAS, listOf(0, 1, 2))
        assertEquals(LabSaveStatus.Saved, report.uiState.value.save)
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(IslandState.Completed, path.uiState.value.islands[0].state)
        assertEquals(IslandState.Available, path.uiState.value.islands[1].state)
        assertEquals(1, path.uiState.value.completedCount)
        assertEquals(IslandState.Completed, labs.uiState.value.labs[0].state)
        assertEquals(IslandState.Available, labs.uiState.value.labs[1].state)
        val daily = home.uiState.value.dailyLesson as DailyLessonState.Pending
        assertEquals(LearningTopic.PHISHING, daily.topic)
    }

    @Test
    fun anIncompleteAttempt_changesNothingInTheTabs() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        refreshAllTabs()
        advanceUntilIdle()

        finishLab(LearningTopic.CONTRASENAS, listOf(0, 1))
        refreshAllTabs()
        advanceUntilIdle()

        assertTrue(progress.completedLabs.isEmpty())
        assertEquals(IslandState.Available, path.uiState.value.islands[0].state)
        assertEquals(IslandState.Locked, path.uiState.value.islands[1].state)
        assertEquals(IslandState.Available, labs.uiState.value.labs[0].state)
    }

    @Test
    fun aLabDoneBeforeItsLessonWasSeen_doesNotCompleteTheIsland() = runTest {
        // Decision Q4: the lab opens without the lesson, but the island needs both.
        refreshAllTabs()
        advanceUntilIdle()

        finishLab(LearningTopic.CONTRASENAS, listOf(0, 1, 2))
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(IslandState.Available, path.uiState.value.islands[0].state)
        assertEquals(IslandState.Locked, path.uiState.value.islands[1].state)
        assertEquals(IslandState.Completed, labs.uiState.value.labs[0].state)
        assertEquals(IslandState.Locked, labs.uiState.value.labs[1].state)
    }

    @Test
    fun aSaveThatFailed_leavesTheTabsAsTheyWere_untilTheRetrySucceeds() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        progress.writeFailure = FailureKind.Offline

        val report = finishLab(LearningTopic.CONTRASENAS, listOf(0, 1, 2))
        refreshAllTabs()
        advanceUntilIdle()
        assertEquals(LabSaveStatus.Failed(offline = true), report.uiState.value.save)
        assertEquals(IslandState.Available, path.uiState.value.islands[0].state)

        progress.writeFailure = null
        report.onRetrySaveClicked()
        advanceUntilIdle()
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(IslandState.Completed, path.uiState.value.islands[0].state)
        assertEquals(IslandState.Available, path.uiState.value.islands[1].state)
    }

    @Test
    fun repeatingACompletedLab_keepsTheProgress_withoutErrors() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        finishLab(LearningTopic.CONTRASENAS, listOf(0, 1, 2))
        val second = finishLab(LearningTopic.CONTRASENAS, listOf(0, 1, 2))
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(LabSaveStatus.Saved, second.uiState.value.save)
        assertEquals(setOf(LearningTopic.CONTRASENAS), progress.completedLabs)
        assertEquals(1, path.uiState.value.completedCount)
    }

    @Test
    fun sixLabsAndLessons_openTheFinalExam_inRutaAndLabs() = runTest {
        LearningTopic.entries.forEach { topic ->
            progress.viewedLessons += topic
            finishLab(topic, listOf(0, 1, 2))
        }
        refreshAllTabs()
        advanceUntilIdle()

        assertEquals(IslandState.Available, path.uiState.value.finalTest)
        assertEquals(IslandState.Available, labs.uiState.value.finalTest)
        assertEquals(6, path.uiState.value.completedCount)
        assertEquals(DailyLessonState.NoneLeft, home.uiState.value.dailyLesson)
    }
}
