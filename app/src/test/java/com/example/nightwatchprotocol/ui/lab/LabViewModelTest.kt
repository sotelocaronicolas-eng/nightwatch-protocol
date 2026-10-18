package com.example.nightwatchprotocol.ui.lab

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.LabTerminal
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.UNRECOGNIZED_COMMAND_MESSAGE
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LabViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val content = FakeLearningContentRepository()
    private val topic = LearningTopic.MALWARE

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = LabViewModel(topic, handle, content)

    private fun LabViewModel.terminal(): LabTerminal = (uiState.value.status as LabStatus.Ready).terminal

    private fun command(index: Int) = content.content.lab(topic).objectives[index].command

    private fun output(index: Int) = content.content.lab(topic).objectives[index].output

    private fun LabViewModel.send(text: String) {
        onInputChanged(text)
        onSendClicked()
    }

    // ---- opening ----

    @Test
    fun beforeTheContentArrives_itIsLoading() = runTest {
        val vm = viewModel()

        assertEquals(LabStatus.Loading, vm.uiState.value.status)
    }

    @Test
    fun opens_withTheLabOfTheTopic_threeObjectives_nothingDone_andNoTranscript() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val terminal = vm.terminal()
        assertEquals(topic, terminal.lab.topic)
        assertEquals(3, terminal.totalObjectives)
        assertEquals(0, terminal.completedCount)
        assertTrue(terminal.transcript.isEmpty())
        assertEquals("", vm.uiState.value.input)
    }

    // ---- using a command ----

    @Test
    fun tappingAnObjective_fillsTheField_withItsCommand_andDoesNotSendIt() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onObjectiveClicked(1)

        assertEquals(command(1), vm.uiState.value.input)
        assertEquals(0, vm.terminal().completedCount)
        assertTrue(vm.terminal().transcript.isEmpty())
    }

    @Test
    fun tappingAnotherObjective_replacesTheText() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onObjectiveClicked(0)
        vm.onObjectiveClicked(2)

        assertEquals(command(2), vm.uiState.value.input)
    }

    @Test
    fun tappingAnObjectiveThatDoesNotExist_changesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onInputChanged("texto")

        vm.onObjectiveClicked(7)

        assertEquals("texto", vm.uiState.value.input)
    }

    @Test
    fun aValidCommand_marksTheObjective_andPrintsItsOutput() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onObjectiveClicked(0)
        vm.onSendClicked()

        val terminal = vm.terminal()
        assertEquals(setOf(0), terminal.completedObjectives)
        assertEquals(command(0), terminal.transcript.last().input)
        assertEquals(output(0), terminal.transcript.last().output)
        assertTrue(terminal.transcript.last().recognized)
    }

    @Test
    fun theFieldKeepsItsText_afterSending() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onObjectiveClicked(0)
        vm.onSendClicked()

        assertEquals(command(0), vm.uiState.value.input)
    }

    @Test
    fun anUnknownCommand_isAnsweredWithTheMessage_andMarksNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.send("rm -rf /")

        val terminal = vm.terminal()
        assertEquals(0, terminal.completedCount)
        assertEquals("rm -rf /", terminal.transcript.last().input)
        assertEquals(UNRECOGNIZED_COMMAND_MESSAGE, terminal.transcript.last().output)
        assertFalse(terminal.transcript.last().recognized)
    }

    @Test
    fun anEmptyField_sendsNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.send("   ")

        assertTrue(vm.terminal().transcript.isEmpty())
        assertEquals(0, vm.terminal().completedCount)
    }

    @Test
    fun aRepeatedCommand_printsItsOutputAgain_butDoesNotCountTwice() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.send(command(1))
        vm.send(command(1))

        val terminal = vm.terminal()
        assertEquals(1, terminal.completedCount)
        assertEquals(2, terminal.transcript.size)
        assertEquals(output(1), terminal.transcript.last().output)
    }

    @Test
    fun theThreeObjectives_inAnyOrder_completeTheLab() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.send(command(2))
        vm.send(command(0))
        assertFalse(vm.terminal().isComplete)
        vm.send(command(1))

        assertTrue(vm.terminal().isComplete)
        assertEquals(setOf(0, 1, 2), vm.terminal().completedObjectives)
        assertEquals(3, vm.terminal().transcript.size)
    }

    // ---- rotation ----

    @Test
    fun theSameViewModel_keepsTheAttempt_whenTheScreenResubscribes() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.send(command(0))

        // A rotation keeps the ViewModel and only collects its state again.
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(setOf(0), (state.status as LabStatus.Ready).terminal.completedObjectives)
            assertEquals(command(0), state.input)
        }
    }

    @Test
    fun aNewViewModel_withTheSameSavedState_rebuildsTheSameAttempt() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.send(command(0))
        first.send("desconocido")
        first.send(command(2))
        first.onInputChanged("texto a medias")

        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(first.terminal(), second.terminal())
        assertEquals(setOf(0, 2), second.terminal().completedObjectives)
        assertEquals(3, second.terminal().transcript.size)
        assertEquals("texto a medias", second.uiState.value.input)
    }

    // ---- closing and finishing ----

    @Test
    fun close_leavesWithoutAnyQuestion() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.send(command(0))

        vm.events.test {
            vm.onCloseClicked()
            assertEquals(LabEvent.Close, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun finish_isAlwaysEnabled_opensTheReport_evenWithNothingDone() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onFinishClicked()
            assertEquals(LabEvent.OpenReport(topic, emptyList()), awaitItem())
        }
        assertEquals(0, vm.terminal().completedCount)
    }

    @Test
    fun finish_opensTheReport_withTheObjectivesDone_sorted() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.send(command(2))
        vm.send(command(0))

        vm.events.test {
            vm.onFinishClicked()
            assertEquals(LabEvent.OpenReport(topic, listOf(0, 2)), awaitItem())
        }
    }

    @Test
    fun finish_withTheThreeDone_opensTheReportWithAllOfThem() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        (0..2).forEach { vm.send(command(it)) }

        vm.events.test {
            vm.onFinishClicked()
            assertEquals(LabEvent.OpenReport(topic, listOf(0, 1, 2)), awaitItem())
        }
    }

    @Test
    fun finish_beforeTheLabIsReady_doesNothing() = runTest {
        content.failure = DataAccessException(FailureKind.Unknown)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onFinishClicked()
            expectNoEvents()
        }
    }

    // ---- read error ----

    @Test
    fun contentThatCannotBeRead_fails_andRetryRecovers() = runTest {
        content.failure = DataAccessException(FailureKind.Unknown)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LabStatus.Failed, vm.uiState.value.status)

        content.failure = null
        vm.load()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.status is LabStatus.Ready)
    }

    @Test
    fun sendingOrTappingWhileItIsNotReady_doesNothing() = runTest {
        content.failure = DataAccessException(FailureKind.Unknown)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onObjectiveClicked(0)
        vm.onSendClicked()

        assertEquals(LabStatus.Failed, vm.uiState.value.status)
        assertEquals("", vm.uiState.value.input)
    }
}
