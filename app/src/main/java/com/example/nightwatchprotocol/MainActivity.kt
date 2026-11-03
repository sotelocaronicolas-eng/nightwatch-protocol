package com.example.nightwatchprotocol

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.nightwatchprotocol.data.preferences.ThemeMode
import com.example.nightwatchprotocol.ui.applock.AppLockScreen
import com.example.nightwatchprotocol.ui.applock.AppLockViewModel
import com.example.nightwatchprotocol.ui.biometric.BiometricPrompter
import com.example.nightwatchprotocol.ui.biometric.BiometricResult
import com.example.nightwatchprotocol.ui.navigation.Login
import com.example.nightwatchprotocol.ui.navigation.NightWatchNavHost
import com.example.nightwatchprotocol.ui.navigation.PostAuthGate
import com.example.nightwatchprotocol.ui.session.DeviceSessionViewModel
import com.example.nightwatchprotocol.ui.social.LocalSocialPhotoSource
import com.example.nightwatchprotocol.ui.social.RepositorySocialPhotoSource
import com.example.nightwatchprotocol.ui.startup.StartRoute
import com.example.nightwatchprotocol.ui.startup.StartupViewModel
import com.example.nightwatchprotocol.ui.theme.DarkNightWatchColors
import com.example.nightwatchprotocol.ui.theme.LightNightWatchColors
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import com.example.nightwatchprotocol.ui.theme.SystemChromeEffect
import com.example.nightwatchprotocol.ui.theme.applyThemeChrome
import com.example.nightwatchprotocol.ui.theme.isDarkTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// AppCompatActivity extends FragmentActivity (the biometric prompt still gets what it needs) and is what
// AppCompatDelegate.setApplicationLocales needs to apply the language and recreate the Activity.
class MainActivity : AppCompatActivity() {
    private val appLockViewModel: AppLockViewModel by viewModels { AppLockViewModel.Factory }
    private val deviceSessionViewModel: DeviceSessionViewModel by viewModels { DeviceSessionViewModel.Factory }
    private val startupViewModel: StartupViewModel by viewModels { StartupViewModel.Factory }
    private var isUnlockPromptShowing = false

    // How the screens of Social get the photos of the people (the repository keeps them in memory and on disk).
    private val socialPhotoSource by lazy {
        RepositorySocialPhotoSource((application as NightWatchApplication).container.socialAvatarRepository)
    }

    // The view models that read the session in their init are only touched once the session is ready.
    private var sessionReady = false

    // The saved look, read before the first frame (null until then).
    @Volatile
    private var initialThemeMode: ThemeMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Until the saved look is read the window is dark (the splash covers it); then the chosen look replaces it (below).
        applyThemeChrome(dark = true, windowBackground = DarkNightWatchColors.bgBase.toArgb())
        // The splash stays up (the main thread is never blocked) until the session is readable, the route is known and the
        // chosen look is read, so the first frame already has the right theme (no dark -> light flash).
        splashScreen.setKeepOnScreenCondition { startupViewModel.startRoute.value == null || initialThemeMode == null }
        lifecycleScope.launch {
            // First launch: Spanish is fixed explicitly (A4). It is the first thing so that, if AppCompat recreates the Activity to apply it,
            // it happens behind the splash and only once. A failed write is not fatal: the next launch tries again.
            try {
                (application as NightWatchApplication).container.languageBootstrap.ensureInitialized()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The language stays as AppCompat has it.
            }
            val route = startupViewModel.startRoute.filterNotNull().first()
            val startDestination: Any = when (route) {
                StartRoute.PostAuthGate -> PostAuthGate
                StartRoute.Login -> Login
            }
            val appearance = (application as NightWatchApplication).container.appearanceRepository
            val themeMode = appearance.themeMode.first()
            initialThemeMode = themeMode
            // The window background and the system bars already match the chosen look in the first frame.
            val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = isDarkTheme(themeMode, systemDark)
            applyThemeChrome(dark, (if (dark) DarkNightWatchColors else LightNightWatchColors).bgBase.toArgb())
            showContent(startDestination, themeMode)
            sessionReady = true
            // onStart already ran while the session was loading: report it now.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) reportActivityStarted()
        }
    }

    private fun showContent(startDestination: Any, initialThemeMode: ThemeMode) {
        val appearance = (application as NightWatchApplication).container.appearanceRepository
        setContent {
            // The whole app follows the look the person chose in "Apariencia". A change of the look is a new value here:
            // the activity is not recreated and nothing below it is cancelled (R-01).
            val chosenMode by appearance.themeMode.collectAsStateWithLifecycle(initialValue = initialThemeMode)
            NightWatchProtocolTheme(chosenMode) {
                val lockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
                val sessionEnded by deviceSessionViewModel.sessionEndedByAnotherDevice.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                // System bars and window background follow the look the person chose, not the phone's (unless "Automático").
                val systemDark = isSystemInDarkTheme()
                SystemChromeEffect(dark = isDarkTheme(chosenMode, systemDark))

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
                            TextButton(onClick = deviceSessionViewModel::onNoticeDismissed) { Text(stringResource(R.string.common_understood)) }
                        },
                        title = { Text(stringResource(R.string.nav_session_ended_title)) },
                        text = { Text(stringResource(R.string.nav_session_ended_message)) },
                    )
                }

                CompositionLocalProvider(LocalSocialPhotoSource provides socialPhotoSource) {
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
            title = getString(R.string.applock_prompt_title),
            subtitle = getString(R.string.applock_prompt_subtitle),
            allowDeviceCredential = true,
        ) { result ->
            isUnlockPromptShowing = false
            if (result == BiometricResult.Success) appLockViewModel.onAuthenticationSucceeded()
        }
    }
}
