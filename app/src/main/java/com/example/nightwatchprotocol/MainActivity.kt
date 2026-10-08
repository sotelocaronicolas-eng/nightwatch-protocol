package com.example.nightwatchprotocol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.nightwatchprotocol.ui.navigation.Login
import com.example.nightwatchprotocol.ui.navigation.NightWatchNavHost
import com.example.nightwatchprotocol.ui.navigation.PostAuthGate
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as NightWatchApplication
        val startDestination = if (application.container.authRepository.currentUserEmail != null) {
            PostAuthGate
        } else {
            Login
        }
        setContent {
            NightWatchProtocolTheme {
                NightWatchNavHost(startDestination = startDestination)
            }
        }
    }
}
