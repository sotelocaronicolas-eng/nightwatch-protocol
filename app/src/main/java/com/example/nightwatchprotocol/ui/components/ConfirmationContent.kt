package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

private val ConfirmationPanelColor = Color(0xFF447579)

/**
 * The "¡... actualizado!" screens from Figma: gear background, check circle, title, message and one button.
 * With [headerTitle] the top bar is the plain title + "X" used by "Contraseña actualizada", and the button
 * is the wide "Listo" pill of that frame; without it, the usual logo bar and the 299x47 button are used.
 */
@Composable
fun ConfirmationContent(
    title: String,
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerTitle: String? = null,
    messageSizeSp: Int = 13,
) {
    NightWatchScreenBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (headerTitle == null) {
                ProfileTopBar(onClose = onClose)
            } else {
                TitleTopBar(title = headerTitle, onClose = onClose)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(ConfirmationPanelColor)) {
                // Decorative gears: bottom-right large and top-left small, both at 25% like the design.
                Image(
                    painter = painterResource(R.drawable.img_gear_large),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 160.dp, y = 120.dp)
                        .size(width = 492.dp, height = 378.dp)
                        .rotate(-33.18f)
                        .alpha(0.25f),
                )
                Image(
                    painter = painterResource(R.drawable.img_gear_small),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = (-162).dp, y = 0.dp)
                        .size(width = 371.dp, height = 285.dp)
                        .alpha(0.25f),
                )
                Image(
                    painter = painterResource(R.drawable.ic_confirm_check),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 129.dp)
                        .size(203.dp),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 357.dp, start = 40.dp, end = 40.dp),
                ) {
                    Text(
                        text = title,
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = message,
                        color = NightWatchHeaderPanel,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = messageSizeSp.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 110.dp),
                ) {
                    if (headerTitle == null) {
                        ProfileButton(text = buttonText, onClick = onButtonClick)
                    } else {
                        WidePillButton(text = buttonText, onClick = onButtonClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidePillButton(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(372.dp)
            .height(48.dp)
            .shadow(4.dp, NightWatchPillShape)
            .background(NightWatchSurfaceButton, NightWatchPillShape)
            .clip(NightWatchPillShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            color = Color(0xFF162523),
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}
