package com.example.nightwatchprotocol.ui.labreport

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.realLearningContent
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

    private fun viewModel(topic: LearningTopic, completed: Set<Int>) = LabReportViewModel(topic, completed, content)

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
}
