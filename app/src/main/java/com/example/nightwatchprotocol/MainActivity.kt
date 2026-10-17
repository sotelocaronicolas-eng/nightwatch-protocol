package com.example.nightwatchprotocol

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.nightwatchprotocol.ui.applock.AppLockScreen
import com.example.nightwatchprotocol.ui.applock.AppLockViewModel
import com.example.nightwatchprotocol.ui.biometric.BiometricPrompter
import com.example.nightwatchprotocol.ui.biometric.BiometricResult
import com.example.nightwatchprotocol.ui.navigation.Login
import com.example.nightwatchprotocol.ui.navigation.NightWatchNavHost
import com.example.nightwatchprotocol.ui.navigation.PostAuthGate
import com.example.nightwatchprotocol.ui.session.DeviceSessionViewModel
import com.example.nightwatchprotocol.ui.startup.StartRoute
import com.example.nightwatchprotocol.ui.startup.StartupViewModel
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val appLockViewModel: AppLockViewModel by viewModels { AppLockViewModel.Factory }
    private val deviceSessionViewModel: DeviceSessionViewModel by viewModels { DeviceSessionViewModel.Factory }
    private val startupViewModel: StartupViewModel by viewModels { StartupViewModel.Factory }
    private var isUnlockPromptShowing = false

    // The view models that read the session in their init are only touched once the session is ready.
    private var sessionReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The splash stays up (the main thread is never blocked) until the session is readable and the route is known.
        splashScreen.setKeepOnScreenCondition { startupViewModel.startRoute.value == null }
        lifecycleScope.launch {
            val route = startupViewModel.startRoute.filterNotNull().first()
            val startDestination: Any = when (route) {
                StartRoute.PostAuthGate -> PostAuthGate
                StartRoute.Login -> Login
            }
            showContent(startDestination)
            sessionReady = true
            // onStart already ran while the session was loading: report it now.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) reportActivityStarted()
        }
    }

    private fun showContent(startDestination: Any) {
        setContent {
            NightWatchProtocolTheme {
                val lockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
                val sessionEnded by deviceSessionViewModel.sessionEndedByAnotherDevice.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                // Another device asked this one to sign out: back to Login, wherever the user was.
                // (Only once the graph exists: while the app lock hides the content there is nothing to navigate.)
                LaunchedEffect(sessionEnded, lockState.showContent) {
                    if (sessionEnded && lockState.showContent) {
                        navController.navigate(Login) { popUpTo(0) { inclusive = true } }
                    }
                }
                if (sessionEnded) {
                    AlertDialog(
                        onDismissRequest = deviceSessionViewModel::onNoticeDismissed,
                        confirmButton = {
                            TextButton(onClick = deviceSessionViewModel::onNoticeDismissed) { Text("Entendido") }
                        },
                        title = { Text("Sesión cerrada") },
                        text = { Text("Se cerró tu sesión porque lo pediste desde otro dispositivo.") },
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (lockState.showContent) {
                        NightWatchNavHost(navController = navController, startDestination = startDestination)
                    }
                    if (lockState.isLocked) {
                        // Back leaves the app instead of reaching the screens under the lock.
                        BackHandler { finish() }
                        AppLockScreen(onRequestUnlock = ::showUnlockPrompt)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (sessionReady) reportActivityStarted()
    }

    override fun onStop() {
        super.onStop()
        if (sessionReady) appLockViewModel.onActivityStopped(SystemClock.elapsedRealtime())
    }

    private fun reportActivityStarted() {
        appLockViewModel.onActivityStarted(SystemClock.elapsedRealtime())
        deviceSessionViewModel.onActivityStarted(SystemClock.elapsedRealtime())
    }

    private fun showUnlockPrompt() {
        if (isUnlockPromptShowing || !appLockViewModel.requestUnlock()) return
        isUnlockPromptShowing = true
        BiometricPrompter(this).authenticate(
            title = "Desbloquear NightWatch",
            subtitle = "Usa tu huella para continuar",
            allowDeviceCredential = true,
        ) { result ->
            isUnlockPromptShowing = false
            if (result == BiometricResult.Success) appLockViewModel.onAuthenticationSucceeded()
        }
    }
}
