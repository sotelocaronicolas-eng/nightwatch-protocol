package com.example.nightwatchprotocol.ui.profile.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

private val DeviceLabelColor = Color(0xFF404040)

/** Cooperative device list (see [DEVICES_LIMITATION_NOTICE]): the registered devices of the account, not the backend's sessions. */
@Composable
fun DevicesScreen(
    onClose: () -> Unit,
    onDeviceSignedOut: () -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DevicesEvent.DeviceSignedOut -> onDeviceSignedOut()
                DevicesEvent.SessionEnded -> onSessionEnded()
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
                    Text(
                        text = "Administrar Dispositivos",
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 36.dp),
                    )
                    Text(
                        text = "Sesiones activas en tu cuenta",
                        color = NightWatchHeaderPanel,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                    )
                    Image(
                        painter = painterResource(R.drawable.img_devices),
                        contentDescription = null,
                        modifier = Modifier.size(172.dp),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                    ) {
                        if (uiState.isLoading && uiState.devices.isEmpty()) {
                            InfoText("Cargando dispositivos…", NightWatchHeaderPanel)
                        }
                        uiState.devices.forEach { device ->
                            DeviceCard(
                                name = device.name,
                                status = device.statusLabel,
                                onClick = if (device.isCurrent || device.isRevoked) null else {
                                    { viewModel.onDeviceClicked(device.id) }
                                },
                            )
                        }
                        uiState.error?.let { InfoText(it, NightWatchError) }
                        uiState.notice?.let { InfoText(it, NightWatchHeaderPanel) }
                        InfoText(DEVICES_LIMITATION_NOTICE, NightWatchHeaderPanel, size = 11)
                    }
                    Box(modifier = Modifier.padding(bottom = 120.dp))
                }
                ProfileButton(
                    text = "CERRAR SESIÓN EN TODOS",
                    onClick = viewModel::onSignOutAllClicked,
                    enabled = !uiState.isWorking,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 40.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoText(text: String, color: Color, size: Int = 12) {
    Text(
        text = text,
        color = color,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = size.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeviceCard(name: String, status: String, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(4.dp, shape)
            .background(NightWatchSurfaceButtonMuted, shape)
            .border(2.dp, NightWatchBorderAccent, shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text = "📱  $name",
            color = Color.Black,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = status,
            color = DeviceLabelColor,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
        if (onClick != null) {
            Text(text = "›", color = Color.Black, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}
