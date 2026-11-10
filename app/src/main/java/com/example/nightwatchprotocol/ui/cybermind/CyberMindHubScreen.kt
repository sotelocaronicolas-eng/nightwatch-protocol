package com.example.nightwatchprotocol.ui.cybermind

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme

private val RowShape = RoundedCornerShape(16.dp)

/** "Prepárate con CyberMind" (Figma 2671:284): elegir entre evaluar desde un CSV o ingresar los datos a mano. */
@Composable
fun CyberMindHubScreen(
    onClose: () -> Unit,
    onOpenCsv: () -> Unit,
    onOpenManual: () -> Unit,
) {
    val colors = NightWatchTheme.colors
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = stringResource(R.string.cybermind_hub_title), onClose = onClose)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(196.dp)
                        .background(colors.surfaceButton, CircleShape)
                        .padding(10.dp)
                        .background(colors.bgElevated, CircleShape),
                ) {
                    Image(painter = painterResource(R.drawable.img_home_cibermynd), contentDescription = null, modifier = Modifier.size(120.dp))
                }
                Text(
                    text = stringResource(R.string.cybermind_hub_intro),
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 48.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxWidth()) {
                    OpcionHub("📜", stringResource(R.string.cybermind_hub_csv_icon), stringResource(R.string.cybermind_hub_csv), onOpenCsv)
                    OpcionHub("🖊", stringResource(R.string.cybermind_hub_manual_icon), stringResource(R.string.cybermind_hub_manual), onOpenManual)
                }
            }
        }
    }
}

@Composable
private fun OpcionHub(emoji: String, descripcionEmoji: String, texto: String, onClick: () -> Unit) {
    val colors = NightWatchTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceButton, RowShape)
            .border(BorderStroke(3.dp, NightWatchBorderAccent), RowShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            modifier = Modifier
                .padding(end = 14.dp)
                .semantics { contentDescription = descripcionEmoji },
        )
        Text(
            text = texto,
            color = colors.onSurfaceButton,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(text = "›", color = colors.onSurfaceButton, fontSize = 18.sp)
    }
}
