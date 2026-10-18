package com.example.nightwatchprotocol.ui.profile.confirmidentity

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.BottomActionScrollLayout
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.profile.changeemail.emailActionBottomPadding
import com.example.nightwatchprotocol.ui.profile.changeemail.emailFieldTopPadding
import com.example.nightwatchprotocol.ui.profile.changeemail.EmailScreenHeader
import com.example.nightwatchprotocol.ui.profile.changeemail.illustrationSize

/**
 * Not in Figma, but laid out exactly like "Confirmar Correo" (2106:50): the same header, illustration slot, field
 * distance and bottom button position (see `ChangeEmailScreens`), with the "id_card" icon as the illustration.
 */
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
            BottomActionScrollLayout(
                actionBottomPadding = emailActionBottomPadding,
                modifier = Modifier.weight(1f),
                action = {
                    ProfileButton(
                        text = "CONTINUAR",
                        onClick = onConfirm,
                        enabled = !uiState.isLoading,
                    )
                },
            ) {
                EmailScreenHeader(
                    title = "Confirma tu identidad",
                    message = "Por seguridad, ingresa tu contraseña actual para continuar.",
                )
                // The slot has the size of the envelope illustration; the icon is drawn a little smaller inside it
                // so its strokes look as big as the envelope's.
                Box(modifier = Modifier.size(illustrationSize), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_id_card),
                        contentDescription = null,
                        modifier = Modifier.size(190.dp),
                    )
                }
                NightWatchTextField(
                    label = "Contraseña actual",
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    isPassword = true,
                    isError = uiState.error != null,
                    supportingText = uiState.error,
                    modifier = Modifier.padding(top = emailFieldTopPadding),
                )
            }
        }
    }
}
