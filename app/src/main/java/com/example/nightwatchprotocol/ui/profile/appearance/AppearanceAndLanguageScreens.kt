package com.example.nightwatchprotocol.ui.profile.appearance

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

// Both screens look like the design but choose nothing: the app stays dark and in Spanish (decision 3).
// Tapping "Claro", "Automático" or "English" does nothing.

@Composable
fun AppearanceScreen(onClose: () -> Unit) {
    SettingsScaffold(
        title = "Apariencia",
        onClose = onClose,
        topPadding = 125.dp,
        decoration = { BottomIllustration(R.drawable.img_appearance_bg, height = 308, alpha = 0.8f) },
    ) {
        Text(
            text = "Elige cómo se ve NightWatch Protocol en tu dispositivo.",
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp, top = 0.dp),
        )
        SettingsRow(
            emoji = "🌙",
            label = "Oscuro",
            subtitle = "Fondo oscuro, ideal de noche",
            highlighted = true,
            trailing = { CheckMark() },
        )
        SettingsRow(emoji = "☀️", label = "Claro", subtitle = "Fondo claro de alto contraste", trailing = { NoTrailing() })
        SettingsRow(emoji = "⚙️", label = "Automático", subtitle = "Sigue el ajuste del sistema", trailing = { NoTrailing() })
    }
}

@Composable
fun LanguageScreen(onClose: () -> Unit) {
    SettingsScaffold(
        title = "Idioma",
        onClose = onClose,
        topPadding = 170.dp,
        decoration = { BottomIllustration(R.drawable.img_language_bg, height = 287, alpha = 0.7f) },
    ) {
        SettingsRow(emoji = "🇪🇸", label = "Español", highlighted = true, trailing = { CheckMark() })
        SettingsRow(emoji = "🇺🇸", label = "English", trailing = { NoTrailing() })
    }
}

@Composable
private fun CheckMark() {
    Text(text = "✓", color = NightWatchGold, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
}

/** Rows without a chevron: these are choices, not links. */
@Composable
private fun NoTrailing() = Box(modifier = Modifier)

@Composable
private fun BoxScope.BottomIllustration(drawable: Int, height: Int, alpha: Float) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height.dp)
            .alpha(alpha),
    )
}
