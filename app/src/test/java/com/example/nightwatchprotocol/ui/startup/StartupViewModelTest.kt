package com.example.nightwatchprotocol.ui.startup

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startRoute_staysUndecidedUntilTheSessionIsReady() = runTest {
        val auth = FakeAuthRepository().apply { sessionReadyGate = CompletableDeferred() }
        val viewModel = StartupViewModel(auth, SessionPreferences.inMemory())

        runCurrent()
        assertEquals(1, auth.awaitSessionReadyCallCount)
        assertNull(viewModel.startRoute.value)

        auth.sessionReadyGate!!.complete(Unit)
        runCurrent()
        assertEquals(StartRoute.PostAuthGate, viewModel.startRoute.value)
    }

    @Test
    fun readyWithUser_goesToThePostAuthGate() = runTest {
        val viewModel = StartupViewModel(FakeAuthRepository(), SessionPreferences.inMemory())

        runCurrent()
        assertEquals(StartRoute.PostAuthGate, viewModel.startRoute.value)
    }

    @Test
    fun readyWithoutUser_goesToLogin() = runTest {
        val auth = FakeAuthRepository().apply { currentUserEmail = null }
        val viewModel = StartupViewModel(auth, SessionPreferences.inMemory())

        runCurrent()
        assertEquals(StartRoute.Login, viewModel.startRoute.value)
    }

    @Test
    fun userIsReadOnlyAfterTheSessionIsReady() = runTest {
        // The user only "appears" once the session finished loading, as with an asynchronously restored session.
        val gate = CompletableDeferred<Unit>()
        val auth = object : AuthRepository by FakeAuthRepository() {
            private var loaded = false
            override val currentUserEmail: String? get() = if (loaded) "test@example.com" else null
            override suspend fun awaitSessionReady() {
                gate.await()
                loaded = true
            }
        }
        val viewModel = StartupViewModel(auth, SessionPreferences.inMemory())

        runCurrent()
        assertNull(viewModel.startRoute.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(StartRoute.PostAuthGate, viewModel.startRoute.value)
    }

    // ---- an interrupted password recovery ----

    @Test
    fun withTheRecoveryMark_theSessionIsClosed_theMarkIsCleared_andItGoesToLogin() = runTest {
        val auth = FakeAuthRepository() // a session is open
        val preferences = SessionPreferences.inMemory().apply { setRecoveryInProgress(true) }
        val viewModel = StartupViewModel(auth, preferences)

        runCurrent()

        assertEquals(StartRoute.Login, viewModel.startRoute.value)
        assertEquals(1, auth.signOutCallCount)
        assertFalse(preferences.isRecoveryInProgress())
    }

    @Test
    fun theSessionIsClosed_afterItIsReady_andBeforeTheRouteIsDecided() = runTest {
        val auth = FakeAuthRepository().apply { sessionReadyGate = CompletableDeferred() }
        val viewModel = StartupViewModel(auth, SessionPreferences.inMemory().apply { setRecoveryInProgress(true) })

        runCurrent()
        assertEquals(0, auth.signOutCallCount)
        assertNull(viewModel.startRoute.value)

        auth.sessionReadyGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, auth.signOutCallCount)
        assertEquals(StartRoute.Login, viewModel.startRoute.value)
    }

    @Test
    fun withoutTheRecoveryMark_nothingIsSignedOut() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = StartupViewModel(auth, SessionPreferences.inMemory())

        runCurrent()

        assertEquals(StartRoute.PostAuthGate, viewModel.startRoute.value)
        assertEquals(0, auth.signOutCallCount)
    }
}
