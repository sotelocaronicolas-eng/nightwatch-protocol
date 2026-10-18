package com.example.nightwatchprotocol.ui.profile.changeemail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.BottomActionScrollLayout
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.profile.editprofile.GOOGLE_ACCOUNT_NOTICE
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

// "Confirmar Correo": the new address is typed here and a 6-digit code is sent to it. "Verifica tu Correo" is the shared
// code screen (ui/verifycode).

@Composable
fun ChangeEmailScreen(
    identityConfirmed: Boolean,
    onIdentityConfirmedHandled: () -> Unit,
    onClose: () -> Unit,
    onNavigateToConfirmIdentity: () -> Unit,
    onCodeSent: (String) -> Unit,
    viewModel: ChangeEmailViewModel = viewModel(factory = ChangeEmailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(identityConfirmed) {
        if (identityConfirmed) {
            onIdentityConfirmedHandled()
            viewModel.onIdentityConfirmed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChangeEmailEvent.CodeSent -> onCodeSent(event.newEmail)
                ChangeEmailEvent.NavigateToConfirmIdentity -> onNavigateToConfirmIdentity()
            }
        }
    }

    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onClose = onClose)
            BottomActionScrollLayout(
                actionBottomPadding = emailActionBottomPadding,
                modifier = Modifier.weight(1f),
                action = {
                    if (uiState.hasPassword) {
                        ProfileButton(
                            text = "ENVIAR CÓDIGO",
                            onClick = viewModel::onSendCodeClicked,
                            enabled = !uiState.isSending,
                        )
                    }
                },
            ) {
                EmailScreenHeader(
                    title = "Confirmar Correo",
                    message = "Escribe tu nuevo correo. Te enviaremos un código de verificación para confirmarlo.",
                )
                EnvelopeImage()
                if (uiState.hasPassword) {
                    NightWatchTextField(
                        label = "Nuevo correo electrónico",
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        isError = uiState.error != null,
                        supportingText = uiState.error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.padding(top = emailFieldTopPadding),
                    )
                } else {
                    Text(
                        text = GOOGLE_ACCOUNT_NOTICE,
                        color = NightWatchHeaderPanel,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = emailFieldTopPadding),
                    )
                }
            }
        }
    }
}

/*
 * Layout of "Confirmar Correo" (Figma 2106:50), shared by the screens built the same way: "Verifica tu Correo",
 * "Correo de Recuperación" and "Confirma tu identidad". The header, the 207 dp illustration slot and these two
 * distances are the Figma positions: the field 40 dp under the illustration, the button 70 dp above the bottom.
 */
internal val emailFieldTopPadding = 40.dp
internal val emailActionBottomPadding = 70.dp
internal val illustrationSize = 207.dp

@Composable
internal fun EmailScreenHeader(title: String, message: String) {
    Text(
        text = title,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 80.dp),
    )
    Text(
        text = message,
        color = NightWatchHeaderPanel,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
    )
}

@Composable
internal fun EnvelopeImage() {
    Image(
        painter = painterResource(R.drawable.img_envelope),
        contentDescription = null,
        modifier = Modifier.size(illustrationSize),
    )
}
