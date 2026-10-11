package com.example.nightwatchprotocol.ui.profile.myprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.components.UserAvatar
import com.example.nightwatchprotocol.ui.theme.NightWatchBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

private val LabelColor = Color(0xFF404040)
private val DividerColor = Color(0xFF808080)

@Composable
fun MyProfileScreen(
    onClose: () -> Unit,
    onEditProfile: () -> Unit,
    viewModel: MyProfileViewModel = viewModel(factory = MyProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccount() }
    MyProfileContent(uiState = uiState, onClose = onClose, onEditProfile = onEditProfile)
}

@Composable
private fun MyProfileContent(
    uiState: MyProfileUiState,
    onClose: () -> Unit,
    onEditProfile: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onClose = onClose)
            // Coordinates below are the Figma ones, measured from the white line under the top bar.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // The height comes from the card and the button below it, so extra rows push the button down
                // instead of overlapping it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    HeaderPanel(displayName = uiState.displayName)

                    UserAvatar(
                        display = uiState.avatar,
                        size = 155.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 130.dp)
                            .shadow(4.dp, CircleShape),
                    )
                    Text(
                        text = "Mi Avatar",
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = 298.dp),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 347.dp, bottom = 32.dp),
                    ) {
                        InfoCard(uiState = uiState)
                        ProfileButton(
                            text = "EDITAR PERFIL",
                            onClick = onEditProfile,
                            modifier = Modifier.padding(top = 55.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Light header with the rounded cut-outs from the design; the dark body overlaps its lower part. */
@Composable
private fun HeaderPanel(displayName: String) {
    Box(modifier = Modifier.fillMaxWidth().height(161.dp)) {
        Box(Modifier.fillMaxSize().background(NightWatchHeaderPanel))
        Box(
            Modifier
                .offset(y = 37.dp)
                .width(180.dp)
                .height(124.dp)
                .background(NightWatchBackground),
        )
        Box(
            Modifier
                .offset(y = 98.dp)
                .fillMaxWidth()
                .height(744.dp)
                .background(NightWatchBackground, RoundedCornerShape(60.dp)),
        )
        Box(
            Modifier
                .offset(x = (-2).dp)
                .fillMaxWidth()
                .height(101.dp)
                .background(NightWatchHeaderPanel, RoundedCornerShape(100.dp)),
        )
        Text(
            text = "Mi Perfil",
            color = Color.Black,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            modifier = Modifier.padding(start = 24.dp, top = 14.dp),
        )
        Text(
            text = displayName,
            color = Color(0xFF5E5E5E),
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 37.dp, top = 57.dp, end = 37.dp),
        )
    }
}

@Composable
private fun InfoCard(uiState: MyProfileUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .width(330.dp)
            .shadow(4.dp, shape)
            .background(NightWatchSurfaceButtonMuted, shape)
            .border(2.dp, NightWatchBorderAccent, shape),
    ) {
        InfoRow("Correo electrónico", uiState.email, rowHeight = 55.dp, valueSize = 10.sp, bold = true)
        InfoDivider()
        InfoRow("Nivel actual", uiState.level ?: "…", rowHeight = 43.dp)
        InfoDivider()
        InfoRow("Racha activa", "${uiState.streakDays} días 🔥", rowHeight = 43.dp)
        InfoDivider()
        InfoRow("Miembro desde", uiState.memberSince, rowHeight = 41.dp)
        InfoDivider()
        InfoRow("Género", uiState.gender ?: "—", rowHeight = 43.dp)
        InfoDivider()
        InfoRow("Fecha de nacimiento", uiState.birthDate ?: "—", rowHeight = 43.dp)
        InfoDivider()
        InfoRow(
            // (Figma writes "recuperacion" without the accent; the correct spelling is used.)
            "Correo de recuperación",
            uiState.recoveryEmail ?: "—",
            rowHeight = 49.dp,
            valueSize = 10.sp,
        )
    }
}

/** Values longer than this do not fit next to their label at 10sp, so the row stacks label above value. */
private const val STACK_VALUE_FROM_CHARS = 22

@Composable
private fun InfoRow(
    label: String,
    value: String,
    rowHeight: androidx.compose.ui.unit.Dp,
    valueSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    bold: Boolean = true,
) {
    if (value.length > STACK_VALUE_FROM_CHARS) {
        // A long address (e.g. "sotelocaronicolas@gmail.com") gets its own line instead of being cut with "...".
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = rowHeight).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                color = LabelColor,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            Text(
                text = value,
                color = Color.Black,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = valueSize,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(rowHeight).padding(horizontal = 20.dp),
    ) {
        Text(
            text = label,
            color = LabelColor,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Text(
            text = value,
            color = Color.Black,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = valueSize,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}

@Composable
private fun InfoDivider() {
    Box(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor),
    )
}
