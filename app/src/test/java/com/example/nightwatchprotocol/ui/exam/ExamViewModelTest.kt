package com.example.nightwatchprotocol.ui.exam

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.realLearningContent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExamViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The real JSON, so the questions and their order are the ones that ship.
    private val real = realLearningContent()
    private val content = FakeLearningContentRepository(real)
    private val questions = real.exam

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = ExamViewModel(handle, content)

    private fun ExamViewModel.question(): ExamQuestionUi = (uiState.value.status as ExamStatus.Question).question

    private fun correct(index: Int) = questions[index].correctIndex

    private fun wrong(index: Int) = (correct(index) + 1) % 4

    // ---- the questions ----

    @Test
    fun beforeTheContentArrives_itIsLoading() = runTest {
        val vm = viewModel()

        assertEquals(ExamStatus.Loading, vm.uiState.value.status)
    }

    @Test
    fun opensOnQuestionOneOfSix_withItsTopicTextAndFourLetteredOptions() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val question = vm.question()
        assertEquals(1, question.number)
        assertEquals(6, question.total)
        assertEquals("🔑", question.emoji)
        assertEquals("Contraseñas y Autenticación", question.topicLabel)
        assertEquals("¿Cuál de estas contraseñas es más segura?", question.text)
        assertEquals(listOf("A) 123456", "B) Tu_Nombre2024", "C) k7#mQ!9zRt$2", "D) contraseña"), question.options)
    }

    @Test
    fun answeringOneByOne_walksTheSixQuestionsInOrder_andThenFinishes() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            questions.indices.forEach { index ->
                assertEquals(index + 1, vm.question().number)
                assertEquals(questions[index].text, vm.question().text)
                assertEquals(questions[index].topicLabel, vm.question().topicLabel)
                vm.onOptionSelected(index, correct(index))
            }
            assertEquals(ExamStatus.Finished, vm.uiState.value.status)
            assertEquals(ExamEvent.Finished(questions.map { it.correctIndex }), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun theFinishedEvent_carriesTheOptionChosenForEachQuestion_inOrder() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val chosen = listOf(0, 3, 1, 2, 0, 3)

        vm.events.test {
            chosen.forEachIndexed { index, option -> vm.onOptionSelected(index, option) }
            assertEquals(ExamEvent.Finished(chosen), awaitItem())
        }
    }

    // ---- no hint of right or wrong during the exam (decision P8) ----

    @Test
    fun theScreen_isTheSameAfterARightAndAWrongAnswer() = runTest {
        questions.indices.forEach { index ->
            val right = viewModel()
            val mistaken = viewModel()
            advanceUntilIdle()
            (0 until index).forEach {
                right.onOptionSelected(it, correct(it))
                mistaken.onOptionSelected(it, correct(it))
            }

            right.onOptionSelected(index, correct(index))
            mistaken.onOptionSelected(index, wrong(index))

            assertEquals(right.uiState.value, mistaken.uiState.value)
        }
    }

    @Test
    fun theQuestionOnScreen_neverCarriesTheCorrectOption() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        // The state holds only the texts and the position: no field of it says which option is right.
        val fields = ExamQuestionUi::class.java.declaredFields.map { it.name }.toSet()
        assertFalse(fields.any { it.contains("correct", ignoreCase = true) })
        assertEquals(4, vm.question().options.size)
    }

    // ---- taps ----

    @Test
    fun aDoubleTap_answersOnlyOneQuestion() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOptionSelected(0, 1)
        vm.onOptionSelected(0, 1)

        assertEquals(2, vm.question().number)
    }

    @Test
    fun anAnswerCannotBeChanged_byTouchingAnotherOptionOfTheSameQuestion() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.events.test {
            vm.onOptionSelected(0, 1)
            vm.onOptionSelected(0, 3)
            (1..5).forEach { vm.onOptionSelected(it, 0) }

            assertEquals(ExamEvent.Finished(listOf(1, 0, 0, 0, 0, 0)), awaitItem())
        }
    }

    @Test
    fun aTapDrawnOnAnotherQuestion_isIgnored() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOptionSelected(3, 0)
        vm.onOptionSelected(-1, 0)

        assertEquals(1, vm.question().number)
    }

    @Test
    fun anOptionThatDoesNotExist_isIgnored() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOptionSelected(0, 4)
        vm.onOptionSelected(0, -1)

        assertEquals(1, vm.question().number)
    }

    @Test
    fun afterTheSixthAnswer_moreTapsDoNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.events.test {
            questions.indices.forEach { vm.onOptionSelected(it, 0) }
            assertTrue(awaitItem() is ExamEvent.Finished)

            vm.onOptionSelected(5, 1)
            vm.onOptionSelected(6, 1)
            expectNoEvents()
        }
    }

    @Test
    fun tapsBeforeTheContentArrives_doNothing() = runTest {
        val vm = viewModel()

        vm.onOptionSelected(0, 1)
        advanceUntilIdle()

        assertEquals(1, vm.question().number)
    }

    // ---- rotation ----

    @Test
    fun theSameViewModel_keepsTheAttempt_whenTheScreenResubscribes() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOptionSelected(0, 2)
        vm.onOptionSelected(1, 1)

        vm.uiState.test {
            val status = awaitItem().status as ExamStatus.Question
            assertEquals(3, status.question.number)
        }
    }

    @Test
    fun aNewViewModel_withTheSameSavedState_continuesOnTheSameQuestion_withTheSameAnswers() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        listOf(2, 1, 0).forEachIndexed { index, option -> first.onOptionSelected(index, option) }

        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(4, second.question().number)
        assertEquals(first.uiState.value, second.uiState.value)
        second.events.test {
            listOf(3, 2, 1).forEachIndexed { offset, option -> second.onOptionSelected(3 + offset, option) }
            assertEquals(ExamEvent.Finished(listOf(2, 1, 0, 3, 2, 1)), awaitItem())
        }
    }

    @Test
    fun aViewModelRestoredAfterTheLastAnswer_opensTheResultAgain() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        questions.indices.forEach { first.onOptionSelected(it, 1) }

        val second = viewModel(handle)
        second.events.test {
            advanceUntilIdle()
            assertEquals(ExamEvent.Finished(List(6) { 1 }), awaitItem())
        }
        assertEquals(ExamStatus.Finished, second.uiState.value.status)
    }

    // ---- "X" and a new attempt ----

    @Test
    fun close_leavesWithoutAnyQuestion_andNothingIsSavedAnywhere() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOptionSelected(0, 1)

        vm.events.test {
            vm.onCloseClicked()
            assertEquals(ExamEvent.Close, awaitItem())
            expectNoEvents()
        }
        // This ViewModel has no progress repository: it cannot register anything (only the result screen does).
    }

    @Test
    fun anAbandonedExam_leavesNoTrace_theNextOneStartsOnQuestionOne_withTheSameOrder() = runTest {
        val abandoned = viewModel()
        advanceUntilIdle()
        val firstOrder = questions.map { it.text }
        abandoned.onOptionSelected(0, 1)
        abandoned.onOptionSelected(1, 1)

        val next = viewModel()
        advanceUntilIdle()

        assertEquals(1, next.question().number)
        val seen = mutableListOf<String>()
        questions.indices.forEach {
            seen += next.question().text
            next.onOptionSelected(it, 0)
        }
        assertEquals(firstOrder, seen)
    }

    // ---- read error ----

    @Test
    fun contentThatCannotBeRead_fails_andRetryRecovers() = runTest {
        content.failure = DataAccessException(FailureKind.Unknown)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ExamStatus.Failed, vm.uiState.value.status)

        content.failure = null
        vm.load()
        advanceUntilIdle()

        assertEquals(1, vm.question().number)
    }
}
