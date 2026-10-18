package com.example.nightwatchprotocol.ui.profile.recoveryemail

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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

@Composable
fun RecoveryEmailScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onCodeSent: (email: String) -> Unit,
    viewModel: RecoveryEmailViewModel = viewModel(factory = RecoveryEmailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecoveryEmailEvent.Saved -> onSaved()
                is RecoveryEmailEvent.CodeSent -> onCodeSent(event.email)
            }
        }
    }

    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onClose = onClose)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        // The scrollable area ends above the bottom button, so a focused field is brought into view
                        // above it, not under it.
                        .padding(bottom = 140.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 41.dp),
                ) {
                    Text(
                        text = "Correo de Recuperación",
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 80.dp),
                    )
                    Text(
                        text = "Agrega un correo alterno para recuperar tu cuenta si pierdes acceso a la principal.",
                        color = NightWatchHeaderPanel,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 56.dp),
                    )
                    NightWatchTextField(
                        label = "Correo de recuperación",
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        isError = uiState.error != null,
                        supportingText = uiState.error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
                ProfileButton(
                    text = "GUARDAR CORREO",
                    onClick = viewModel::onSaveClicked,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 70.dp),
                )
            }
        }
    }
}
