package com.example.nightwatchprotocol.ui.quiz

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizContentRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
