package com.example.nightwatchprotocol.ui.labreport

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningSnapshot
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.realLearningContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LabReportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The real JSON, so the texts and the passwords outputs are the ones that ship.
    private val content = FakeLearningContentRepository(realLearningContent())

    private val progress = FakeLearningProgressRepository()

    private fun viewModel(
        topic: LearningTopic,
        completed: Set<Int>,
        repository: LearningProgressRepository = progress,
    ) = LabReportViewModel(topic, completed, content, repository)

    private fun LabReportViewModel.report(): LabReportContent =
        (uiState.value.status as LabReportStatus.Ready).content

    // ---- success and failure ----

    @Test
    fun beforeTheContentArrives_itIsLoading() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))

        assertEquals(LabReportStatus.Loading, vm.uiState.value.status)
    }

    @Test
    fun threeOfThree_isASuccess_withTheSuccessTextsOfTheLab() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        val report = vm.report()
        assertTrue(report.success)
        assertEquals("¡Misión completada!", report.successTitle)
        assertEquals("Confirmaste que el correo no viene de un dominio real de NightWatch.", report.summary)
        assertEquals("Precisión de análisis", report.metricLabel)
    }

    @Test
    fun lessThanThree_isAFailure_withTheFailureSentenceOfTheLab() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 2))
        advanceUntilIdle()

        val report = vm.report()
        assertFalse(report.success)
        assertEquals("No confirmaste que el correo no viene de un dominio real de NightWatch.", report.summary)
    }

    @Test
    fun theItems_followTheObjectives_withTheirReportLabelAndTheirState() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 2))
        advanceUntilIdle()

        assertEquals(
            listOf(
                ReportItem("Dominio del remitente investigado", done = true),
                ReportItem("Enlace del correo verificado", done = false),
                ReportItem("Comparación con el dominio oficial hecha", done = true),
            ),
            vm.report().items,
        )
    }

    @Test
    fun everyLab_hasItsOwnTextsAndThreeItems() = runTest {
        LearningTopic.entries.forEach { topic ->
            val vm = viewModel(topic, setOf(0, 1, 2))
            advanceUntilIdle()

            val lab = realLearningContent().lab(topic)
            assertEquals(lab.report.successTitle, vm.report().successTitle)
            assertEquals(lab.report.successSummary, vm.report().summary)
            assertEquals(3, vm.report().items.size)
            assertTrue(vm.report().items.all { it.done })
        }
    }

    @Test
    fun objectiveNumbersThatDoNotExist_areIgnored() = runTest {
        val vm = viewModel(LearningTopic.MALWARE, setOf(0, 1, 7, -1))
        advanceUntilIdle()

        assertFalse(vm.report().success)
        assertEquals(ReportMetric.Percent(67), vm.report().metric)
    }

    // ---- precision (decision Q1) ----

    @Test
    fun precision_withZeroOneTwoAndThreeObjectives_is0_33_67_100() = runTest {
        val expected = mapOf(emptySet<Int>() to 0, setOf(1) to 33, setOf(0, 2) to 67, setOf(0, 1, 2) to 100)
        expected.forEach { (done, percent) ->
            val vm = viewModel(LearningTopic.FIREWALL, done)
            advanceUntilIdle()

            assertEquals(ReportMetric.Percent(percent), vm.report().metric)
        }
    }

    @Test
    fun precision_doesNotDependOnWhichObjectivesWereDone() = runTest {
        listOf(setOf(0), setOf(1), setOf(2)).forEach { done ->
            val vm = viewModel(LearningTopic.REDES_SEGURAS, done)
            advanceUntilIdle()

            assertEquals(ReportMetric.Percent(33), vm.report().metric)
        }
    }

    // ---- passwords lab: time of the strongest password (decision Q2) ----

    @Test
    fun passwords_showTheTimeOfTheStrongestTried_notAPercent() = runTest {
        val expected = mapOf(
            setOf(0) to "Menos de 1 Segundo",
            setOf(1) to "4 Horas",
            setOf(2) to "300 Años",
            setOf(0, 1) to "4 Horas",
            setOf(0, 2) to "300 Años",
            setOf(0, 1, 2) to "300 Años",
        )
        expected.forEach { (done, time) ->
            val vm = viewModel(LearningTopic.CONTRASENAS, done)
            advanceUntilIdle()

            assertEquals(ReportMetric.EstimatedTime(time), vm.report().metric)
        }
    }

    @Test
    fun passwords_withNoneTried_haveNoTime_thatTheScreenShowsAsADash() = runTest {
        val vm = viewModel(LearningTopic.CONTRASENAS, emptySet())
        advanceUntilIdle()

        assertEquals(ReportMetric.EstimatedTime(null), vm.report().metric)
        assertFalse(vm.report().success)
        assertEquals("Tiempo estimado de tu contraseña más fuerte", vm.report().metricLabel)
    }

    @Test
    fun passwords_failure_neverShowsATimeThatIsNotFromAnOutput() = runTest {
        // Figma shows "3 Horas" in the error report; no output says it, so it is never produced (decision Q2).
        val outputs = realLearningContent().lab(LearningTopic.CONTRASENAS).objectives.map { it.output }
        setOf(emptySet(), setOf(0), setOf(1), setOf(0, 1), setOf(2)).forEach { done ->
            val vm = viewModel(LearningTopic.CONTRASENAS, done)
            advanceUntilIdle()

            val time = (vm.report().metric as ReportMetric.EstimatedTime).text
            assertFalse(time == "3 Horas")
            if (time != null) assertTrue(outputs.any { it.contains(time, ignoreCase = true) })
        }
    }

    @Test
    fun otherLabs_neverShowATime() = runTest {
        LearningTopic.entries.filter { it != LearningTopic.CONTRASENAS }.forEach { topic ->
            val vm = viewModel(topic, setOf(0, 1, 2))
            advanceUntilIdle()

            assertTrue(vm.report().metric is ReportMetric.Percent)
        }
    }

    @Test
    fun formatEstimatedTime_capitalisesEachWord_exceptDe() {
        assertEquals("300 Años", formatEstimatedTime("300 años"))
        assertEquals("4 Horas", formatEstimatedTime("4 horas"))
        assertEquals("Menos de 1 Segundo", formatEstimatedTime("menos de 1 segundo"))
    }

    // ---- buttons ----

    @Test
    fun backToPath_sendsItsEvent() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        vm.events.test {
            vm.onBackToPathClicked()
            assertEquals(LabReportEvent.BackToPath, awaitItem())
        }
    }

    @Test
    fun retry_asksForTheSameLab() = runTest {
        val vm = viewModel(LearningTopic.MALWARE, setOf(0))
        advanceUntilIdle()

        vm.events.test {
            vm.onRetryLabClicked()
            assertEquals(LabReportEvent.RetryLab(LearningTopic.MALWARE), awaitItem())
        }
    }

    // ---- read error ----

    @Test
    fun contentThatCannotBeRead_fails_andRetryRecovers() = runTest {
        content.failure = DataAccessException(FailureKind.Unknown)
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()
        assertEquals(LabReportStatus.Failed, vm.uiState.value.status)

        content.failure = null
        vm.load()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.status is LabReportStatus.Ready)
    }

    // ---- saving: only a completed lab, with the session's row ----

    @Test
    fun success_registersTheLabAsCompleted() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        assertEquals(setOf(LearningTopic.PHISHING), progress.completedLabs)
        assertEquals(1, progress.writeCallCount)
        assertEquals(LabSaveStatus.Saved, vm.uiState.value.save)
    }

    @Test
    fun failure_doesNotRegisterAnything() = runTest {
        listOf(emptySet(), setOf(0), setOf(0, 2)).forEach { done ->
            val vm = viewModel(LearningTopic.PHISHING, done)
            advanceUntilIdle()

            assertEquals(LabSaveStatus.NotNeeded, vm.uiState.value.save)
        }
        assertTrue(progress.completedLabs.isEmpty())
        assertEquals(0, progress.writeCallCount)
    }

    @Test
    fun failure_neverOffersToRetryTheSave() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1))
        advanceUntilIdle()

        vm.onRetrySaveClicked()
        advanceUntilIdle()

        assertEquals(0, progress.writeCallCount)
        assertEquals(LabSaveStatus.NotNeeded, vm.uiState.value.save)
    }

    @Test
    fun aFailedSave_stillShowsTheSuccessReport_andTheLabIsNotCompleted() = runTest {
        mapOf(
            FailureKind.Offline to true,
            FailureKind.PermissionDenied to false,
            FailureKind.Unknown to false,
        ).forEach { (kind, offline) ->
            progress.completedLabs.clear()
            progress.writeFailure = kind
            val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
            advanceUntilIdle()

            assertEquals(LabSaveStatus.Failed(offline), vm.uiState.value.save)
            assertTrue(vm.report().success)
            assertTrue(progress.completedLabs.isEmpty())
        }
    }

    @Test
    fun aFailedSave_keepsNoTraceOfTheException() = runTest {
        progress.writeFailure = FailureKind.Offline
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.toString().contains("servidor", ignoreCase = true))
    }

    @Test
    fun retrySave_afterAFailure_registersTheLab() = runTest {
        progress.writeFailure = FailureKind.Offline
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        progress.writeFailure = null
        vm.onRetrySaveClicked()
        advanceUntilIdle()

        assertEquals(LabSaveStatus.Saved, vm.uiState.value.save)
        assertEquals(setOf(LearningTopic.PHISHING), progress.completedLabs)
        assertEquals(2, progress.writeCallCount)
    }

    @Test
    fun retrySave_thatFailsAgain_staysFailed_andCanBeTriedOnceMore() = runTest {
        progress.writeFailure = FailureKind.Offline
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        vm.onRetrySaveClicked()
        advanceUntilIdle()
        assertEquals(LabSaveStatus.Failed(offline = true), vm.uiState.value.save)

        progress.writeFailure = null
        vm.onRetrySaveClicked()
        advanceUntilIdle()
        assertEquals(LabSaveStatus.Saved, vm.uiState.value.save)
    }

    @Test
    fun retrySave_afterItWasSaved_doesNothing() = runTest {
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        vm.onRetrySaveClicked()
        advanceUntilIdle()

        assertEquals(1, progress.writeCallCount)
    }

    @Test
    fun repeatingAnAlreadyCompletedLab_isNotAnError_andAddsNoSecondEntry() = runTest {
        progress.completedLabs += LearningTopic.PHISHING
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2))
        advanceUntilIdle()

        assertEquals(LabSaveStatus.Saved, vm.uiState.value.save)
        assertEquals(setOf(LearningTopic.PHISHING), progress.completedLabs)
    }

    @Test
    fun aFailedAttempt_overACompletedLab_doesNotUndoIt() = runTest {
        progress.completedLabs += LearningTopic.PHISHING
        viewModel(LearningTopic.PHISHING, setOf(0))
        advanceUntilIdle()

        assertEquals(setOf(LearningTopic.PHISHING), progress.completedLabs)
        assertEquals(0, progress.writeCallCount)
    }

    // ---- double tap ----

    @Test
    fun whileSaving_aRetryTap_doesNotSaveAgain() = runTest {
        val gated = GatedProgress().apply { gate = CompletableDeferred() }
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2), gated)
        advanceUntilIdle()
        assertEquals(LabSaveStatus.Saving, vm.uiState.value.save)

        vm.onRetrySaveClicked()
        vm.onRetrySaveClicked()
        advanceUntilIdle()
        gated.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, gated.writes)
        assertEquals(LabSaveStatus.Saved, vm.uiState.value.save)
    }

    @Test
    fun aDoubleTapOnRetrySave_afterAFailure_savesOnlyOnce() = runTest {
        val gated = GatedProgress().apply { failure = FailureKind.Offline }
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2), gated)
        advanceUntilIdle()
        assertEquals(LabSaveStatus.Failed(offline = true), vm.uiState.value.save)

        gated.failure = null
        gated.gate = CompletableDeferred()
        vm.onRetrySaveClicked()
        vm.onRetrySaveClicked()
        advanceUntilIdle()
        gated.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, gated.writes)
        assertEquals(setOf(LearningTopic.PHISHING), gated.completed)
        assertEquals(LabSaveStatus.Saved, vm.uiState.value.save)
    }

    @Test
    fun theButtons_waitForTheSave_andWorkAfterSavedOrFailed() = runTest {
        val gated = GatedProgress().apply { gate = CompletableDeferred() }
        val vm = viewModel(LearningTopic.PHISHING, setOf(0, 1, 2), gated)
        advanceUntilIdle()

        vm.events.test {
            vm.onBackToPathClicked()
            vm.onRetryLabClicked()
            expectNoEvents()

            gated.gate!!.complete(Unit)
            advanceUntilIdle()
            vm.onBackToPathClicked()
            assertEquals(LabReportEvent.BackToPath, awaitItem())
        }

        progress.writeFailure = FailureKind.Offline
        val failed = viewModel(LearningTopic.MALWARE, setOf(0, 1, 2))
        advanceUntilIdle()
        failed.events.test {
            failed.onRetryLabClicked()
            assertEquals(LabReportEvent.RetryLab(LearningTopic.MALWARE), awaitItem())
        }
    }
}

/** A repository whose write can wait on a gate and fail on demand, to test taps while saving. */
private class GatedProgress : LearningProgressRepository {
    val completed = mutableSetOf<LearningTopic>()
    var writes = 0
    var failure: FailureKind? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getSnapshot() = LearningSnapshot.Empty

    override suspend fun markLessonViewed(topic: LearningTopic) = Unit

    override suspend fun markLabCompleted(lab: LearningTopic) {
        writes++
        gate?.await()
        failure?.let { throw DataAccessException(it) }
        completed += lab
    }

    override suspend fun recordExamAttempt(correctCount: Int) = Unit
}
