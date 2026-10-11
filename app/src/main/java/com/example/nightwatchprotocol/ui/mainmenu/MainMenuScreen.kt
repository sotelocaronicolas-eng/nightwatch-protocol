package com.example.nightwatchprotocol.ui.mainmenu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.device.SessionCheck
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import kotlinx.coroutines.launch

@Composable
fun MainMenuScreen(onSignOut: () -> Unit, onOpenAccount: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as NightWatchApplication
    val scope = rememberCoroutineScope()
    val userEmail = application.container.authRepository.currentUserEmail ?: "usuario"

    // Registers this device in the cooperative device list (and honours a pending sign-out request).
    LaunchedEffect(Unit) {
        if (application.container.deviceSessionManager.onAppActive() == SessionCheck.Revoked) onSignOut()
    }

    NightWatchScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sesión iniciada como $userEmail", color = NightWatchOnBackground)
            NightWatchButton(
                text = "Mi cuenta",
                onClick = onOpenAccount,
                modifier = Modifier.padding(top = 24.dp),
            )
            NightWatchButton(
                text = "Cerrar sesión",
                onClick = {
                    scope.launch {
                        application.container.deviceSessionManager.endSession()
                        onSignOut()
                    }
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
