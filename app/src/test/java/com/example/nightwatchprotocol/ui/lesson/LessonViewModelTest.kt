package com.example.nightwatchprotocol.ui.lesson

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LessonViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val content = FakeLearningContentRepository()
    private val progress = FakeLearningProgressRepository()

    private fun viewModel(topic: LearningTopic = LearningTopic.PHISHING) = LessonViewModel(topic, content, progress)

    // ---- what is shown ----

    @Test
    fun beforeTheContentArrives_itIsLoading() = runTest {
        val vm = viewModel()

        assertEquals(LessonStatus.Loading, vm.uiState.value.status)
    }

    @Test
    fun showsTheLessonOfTheTopic_withItsFourCards() = runTest {
        val vm = viewModel(LearningTopic.MALWARE)
        advanceUntilIdle()

        val status = vm.uiState.value.status as LessonStatus.Ready
        assertEquals(LearningTopic.MALWARE, status.lesson.topic)
        assertEquals(
            listOf("¿Qué es?", "¿Por qué importa?", "Tipos y técnicas", "¿Cómo protegerte?"),
            status.lesson.cards.map { it.title },
        )
    }

    @Test
    fun theModuleNumber_isTheOrderOfTheTopic_outOfSix() = runTest {
        LearningTopic.entries.forEachIndexed { index, topic ->
            val vm = viewModel(topic)
            assertEquals(index + 1, vm.uiState.value.moduleNumber)
            assertEquals(6, vm.uiState.value.moduleTotal)
        }
    }

    // ---- "viewed" when it opens ----

    @Test
    fun opening_marksTheLessonAsViewed() = runTest {
        val vm = viewModel(LearningTopic.PHISHING)
        advanceUntilIdle()

        assertEquals(setOf(LearningTopic.PHISHING), progress.viewedLessons)
        assertEquals(1, progress.writeCallCount)
        assertTrue(vm.uiState.value.status is LessonStatus.Ready)
    }

    @Test
    fun opening_doesNotMarkTheLabOrTheExam() = runTest {
        viewModel()
        advanceUntilIdle()

        assertTrue(progress.completedLabs.isEmpty())
        assertTrue(progress.examAttempts.isEmpty())
    }

    @Test
    fun openingTheSameLessonAgain_isNotAnError_andAddsNoRow() = runTest {
        val first = viewModel(LearningTopic.PHISHING)
        advanceUntilIdle()
        val second = viewModel(LearningTopic.PHISHING)

        second.events.test {
            advanceUntilIdle()
            expectNoEvents()
        }

        assertTrue(first.uiState.value.status is LessonStatus.Ready)
        assertTrue(second.uiState.value.status is LessonStatus.Ready)
        assertEquals(setOf(LearningTopic.PHISHING), progress.viewedLessons)
        assertEquals(2, progress.writeCallCount)
    }

    @Test
    fun eachTopic_isMarkedOnItsOwn() = runTest {
        LearningTopic.entries.forEach { topic ->
            viewModel(topic)
            advanceUntilIdle()
        }

        assertEquals(LearningTopic.entries.toSet(), progress.viewedLessons)
    }

    @Test
    fun aSuccessfulSave_emitsNoNotice() = runTest {
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    // ---- a failed save never blocks reading ----

    @Test
    fun aFailedSave_offline_keepsTheLessonReadable_andSaysSoOnce() = runTest {
        progress.writeFailure = FailureKind.Offline
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            assertEquals(LessonEvent.SaveFailed(offline = true), awaitItem())
            expectNoEvents()
        }

        assertTrue(vm.uiState.value.status is LessonStatus.Ready)
        assertTrue(progress.viewedLessons.isEmpty())
    }

    @Test
    fun otherSaveFailures_areNotOffline() = runTest {
        listOf(FailureKind.PermissionDenied, FailureKind.Unknown).forEach { kind ->
            progress.writeFailure = kind
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                assertEquals(LessonEvent.SaveFailed(offline = false), awaitItem())
            }
            assertTrue(vm.uiState.value.status is LessonStatus.Ready)
        }
    }

    @Test
    fun aFailedSave_isNotRetried_untilTheLessonIsOpenedAgain() = runTest {
        progress.writeFailure = FailureKind.Offline
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1, progress.writeCallCount)

        // Even asking the screen to load again (the "Reintentar" of the content) does not write a second time.
        vm.load()
        advanceUntilIdle()
        assertEquals(1, progress.writeCallCount)

        // Opened again (a new view model): it tries again, and now it works.
        progress.writeFailure = null
        viewModel()
        advanceUntilIdle()
        assertEquals(2, progress.writeCallCount)
        assertEquals(setOf(LearningTopic.PHISHING), progress.viewedLessons)
    }

    // ---- content errors ----

    @Test
    fun ifTheContentCannotBeRead_itFails_andNothingIsSaved() = runTest {
        content.failure = IllegalStateException("asset dañado")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LessonStatus.Failed, vm.uiState.value.status)
        assertEquals(0, progress.writeCallCount)
    }

    @Test
    fun retryingTheContent_showsTheLesson_andSavesThen() = runTest {
        content.failure = IllegalStateException("asset dañado")
        val vm = viewModel()
        advanceUntilIdle()

        content.failure = null
        vm.load()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.status is LessonStatus.Ready)
        assertEquals(setOf(LearningTopic.PHISHING), progress.viewedLessons)
        assertEquals(1, progress.writeCallCount)
    }

    // ---- navigation ----

    @Test
    fun theX_closes() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onCloseClicked()
            assertEquals(LessonEvent.Close, awaitItem())
        }
    }

    @Test
    fun goToLab_opensTheLabOfThatTopic() = runTest {
        val vm = viewModel(LearningTopic.FIREWALL)
        advanceUntilIdle()

        vm.events.test {
            vm.onGoToLabClicked()
            assertEquals(LessonEvent.OpenLab(LearningTopic.FIREWALL), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun goToLab_opensTheRightLab_forEveryTopic() = runTest {
        LearningTopic.entries.forEach { topic ->
            val vm = viewModel(topic)
            advanceUntilIdle()

            vm.events.test {
                vm.onGoToLabClicked()
                assertEquals(LessonEvent.OpenLab(topic), awaitItem())
            }
        }
    }

    @Test
    fun goToLab_worksEvenIfTheSaveFailed() = runTest {
        progress.writeFailure = FailureKind.Offline
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            assertEquals(LessonEvent.SaveFailed(offline = true), awaitItem())
            vm.onGoToLabClicked()
            assertEquals(LessonEvent.OpenLab(LearningTopic.PHISHING), awaitItem())
        }
    }
}
