package com.example.nightwatchprotocol.ui.profile.changeemail

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

// "Confirmar Correo" and "Verifica tu Correo". The design talks about a 6-digit code. Firebase sends a link instead
// (decision 2), so there the code field disappears and the texts and buttons are adapted (spec 4b); with a backend
// that sends the code (Supabase) the texts of the design are used and "Verifica tu Correo" is the shared code screen
// (ui/verifycode).

@Composable
fun ChangeEmailScreen(
    identityConfirmed: Boolean,
    onIdentityConfirmedHandled: () -> Unit,
    onClose: () -> Unit,
    onNavigateToConfirmIdentity: () -> Unit,
    onLinkSent: (String) -> Unit,
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
                is ChangeEmailEvent.LinkSent -> onLinkSent(event.newEmail)
                is ChangeEmailEvent.CodeSent -> onCodeSent(event.newEmail)
                ChangeEmailEvent.NavigateToConfirmIdentity -> onNavigateToConfirmIdentity()
            }
        }
    }

    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onClose = onClose)
            BottomActionScrollLayout(
                actionBottomPadding = EMAIL_ACTION_BOTTOM_PADDING,
                modifier = Modifier.weight(1f),
                action = {
                    if (uiState.hasPassword) {
                        ProfileButton(
                            text = if (uiState.usesCode) "ENVIAR CÓDIGO" else "ENVIAR ENLACE",
                            onClick = viewModel::onSendLinkClicked,
                            enabled = !uiState.isSending,
                        )
                    }
                },
            ) {
                EmailScreenHeader(
                    title = "Confirmar Correo",
                    message = if (uiState.usesCode) {
                        "Escribe tu nuevo correo. Te enviaremos un código de verificación para confirmarlo."
                    } else {
                        "Escribe tu nuevo correo. Te enviaremos un enlace de verificación para confirmarlo."
                    },
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
                        modifier = Modifier.padding(top = EMAIL_FIELD_TOP_PADDING),
                    )
                } else {
                    Text(
                        text = GOOGLE_ACCOUNT_NOTICE,
                        color = NightWatchHeaderPanel,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = EMAIL_FIELD_TOP_PADDING),
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
internal val EMAIL_FIELD_TOP_PADDING = 40.dp
internal val EMAIL_ACTION_BOTTOM_PADDING = 70.dp
internal val ILLUSTRATION_SIZE = 207.dp

@Composable
fun VerifyEmailLinkScreen(
    onClose: () -> Unit,
    onEmailUpdated: () -> Unit,
    viewModel: VerifyEmailLinkViewModel = viewModel(factory = VerifyEmailLinkViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                VerifyEmailLinkEvent.EmailUpdated -> onEmailUpdated()
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
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 41.dp),
                ) {
                    EmailScreenHeader(
                        title = "Verifica tu Correo",
                        message = "Enviamos un enlace de verificación a ${uiState.newEmail}. " +
                            "Revisa tu correo y toca el enlace.",
                    )
                    EnvelopeImage()
                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            color = NightWatchError,
                            fontFamily = NightWatchBodyFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }
                    Box(modifier = Modifier.padding(bottom = 180.dp))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 36.dp),
                ) {
                    ProfileButton(
                        text = if (uiState.error != null) "REINTENTAR" else "YA VERIFIQUÉ MI CORREO",
                        onClick = viewModel::onCheckClicked,
                        enabled = !uiState.isChecking,
                    )
                    Text(
                        text = if (uiState.resentNotice) MESSAGE_LINK_RESENT else "Reenviar el enlace",
                        color = NightWatchHeaderPanel,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .clickable(enabled = !uiState.isResending, role = Role.Button) { viewModel.onResendClicked() }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

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
        modifier = Modifier.size(ILLUSTRATION_SIZE),
    )
}
