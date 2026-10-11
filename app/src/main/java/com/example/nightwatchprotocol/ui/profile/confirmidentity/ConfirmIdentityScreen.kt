package com.example.nightwatchprotocol.ui.profile.confirmidentity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/** Not in Figma: built with the same top bar, text and button styles as the other profile screens. */
@Composable
fun ConfirmIdentityScreen(
    onClose: () -> Unit,
    onConfirmed: () -> Unit,
    viewModel: ConfirmIdentityViewModel = viewModel(factory = ConfirmIdentityViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConfirmIdentityEvent.Confirmed -> onConfirmed()
            }
        }
    }

    ConfirmIdentityContent(
        uiState = uiState,
        onClose = onClose,
        onPasswordChange = viewModel::onPasswordChanged,
        onConfirm = viewModel::onConfirmClicked,
    )
}

@Composable
private fun ConfirmIdentityContent(
    uiState: ConfirmIdentityUiState,
    onClose: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onClose = onClose)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 41.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = "Confirma tu identidad",
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp),
                )
                Text(
                    text = "Por seguridad, ingresa tu contraseña actual para continuar.",
                    color = NightWatchHeaderPanel,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
                )
                NightWatchTextField(
                    label = "Contraseña actual",
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    isPassword = true,
                    isError = uiState.error != null,
                    supportingText = uiState.error,
                )
                ProfileButton(
                    text = "CONTINUAR",
                    onClick = onConfirm,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.padding(top = 32.dp, bottom = 32.dp),
                )
            }
        }
    }
}
