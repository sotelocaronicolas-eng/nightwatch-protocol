package com.example.nightwatchprotocol.ui.postauth

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostAuthGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun existingResult_navigatesToMainMenu() = runTest {
        val quizResultRepository = FakeQuizResultRepository()
        quizResultRepository.getResultResult = Result.success(PlacementResult(PlacementLevel.MEDIO, emptySet()))
        val viewModel = PostAuthGateViewModel(FakeAuthRepository(), quizResultRepository)

        viewModel.events.test {
            assertEquals(PostAuthGateEvent.NavigateToMainMenu, awaitItem())
        }
    }

    @Test
    fun noResult_navigatesToPlacementQuiz() = runTest {
        val quizResultRepository = FakeQuizResultRepository()
        quizResultRepository.getResultResult = Result.success(null)
        val viewModel = PostAuthGateViewModel(FakeAuthRepository(), quizResultRepository)

        viewModel.events.test {
            assertEquals(PostAuthGateEvent.NavigateToPlacementQuiz, awaitItem())
        }
    }

    @Test
    fun serverReadFails_failsOpenToPlacementQuiz() = runTest {
        val quizResultRepository = FakeQuizResultRepository()
        quizResultRepository.getResultResult = Result.failure(RuntimeException("network down"))
        val viewModel = PostAuthGateViewModel(FakeAuthRepository(), quizResultRepository)

        viewModel.events.test {
            assertEquals(PostAuthGateEvent.NavigateToPlacementQuiz, awaitItem())
        }
    }

    @Test
    fun serverReadNeverCompletes_failsOpenToPlacementQuiz_afterTimeout() = runTest {
        val quizResultRepository = FakeQuizResultRepository()
        quizResultRepository.getResultNeverCompletes = true
        val viewModel = PostAuthGateViewModel(FakeAuthRepository(), quizResultRepository)

        viewModel.events.test {
            // The read never completes on its own (simulating an offline server read);
            // advance past the 5s viewmodel-side timeout so the fail-open path fires.
            advanceTimeBy(5_001)
            assertEquals(PostAuthGateEvent.NavigateToPlacementQuiz, awaitItem())
        }
    }

    @Test
    fun nullUid_navigatesToMainMenuWithoutCallingRepository() = runTest {
        val quizResultRepository = FakeQuizResultRepository()
        val fakeAuthRepository = FakeAuthRepository().apply { currentUserUid = null }
        val viewModel = PostAuthGateViewModel(fakeAuthRepository, quizResultRepository)

        viewModel.events.test {
            assertEquals(PostAuthGateEvent.NavigateToMainMenu, awaitItem())
        }
        assertEquals(0, quizResultRepository.getResultCallCount)
    }
}
