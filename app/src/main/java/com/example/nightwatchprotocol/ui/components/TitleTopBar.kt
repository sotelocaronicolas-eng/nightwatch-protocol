package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/**
 * The header of the settings-style screens (Privacidad y Seguridad, Cambiar Contraseña, ...):
 * a 17sp title on the left, the "X" on the right and the white line below. No logo.
 */
@Composable
fun TitleTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text(
                text = title,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 20.dp, top = 30.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 19.dp, end = 17.dp)
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onClose),
            ) {
                Text(
                    text = "X",
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White),
        )
    }
}
