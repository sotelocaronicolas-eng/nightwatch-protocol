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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

private val DeviceLabelColor = Color(0xFF404040)

/**
 * Fixed list (decision 4, P7): the first device is "this" one and does nothing when tapped; tapping the other
 * one, or "CERRAR SESIÓN EN TODOS", opens the static "¡Sesión cerrada!" screen. No real session is closed.
 */
@Composable
fun DevicesScreen(
    onClose: () -> Unit,
    onDeviceSignedOut: () -> Unit,
) {
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
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 160.dp),
                    ) {
                        DeviceCard(name = "iPhone de Nicolas", status = "Actual", onClick = null)
                        DeviceCard(name = "Samsung S26 Ultra", status = "Hace 2h", onClick = onDeviceSignedOut)
                    }
                }
                ProfileButton(
                    text = "CERRAR SESIÓN EN TODOS",
                    onClick = onDeviceSignedOut,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 60.dp),
                )
            }
        }
    }
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
            modifier = Modifier.weight(1f),
        )
        Text(
            text = status,
            color = DeviceLabelColor,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text = "›", color = Color.Black, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}
