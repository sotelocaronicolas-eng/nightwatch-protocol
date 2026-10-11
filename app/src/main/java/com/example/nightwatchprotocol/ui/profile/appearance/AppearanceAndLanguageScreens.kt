package com.example.nightwatchprotocol.ui.profile.appearance

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Gap between the header and the first item. The design has ~140dp; the options are brought closer on purpose. */
private val ContentTopPadding = 32.dp

/** Illustration boxes from Figma: 412x308 (Apariencia) and 412x287 (Idioma, drawn with "cover"). */
private const val AppearanceIllustrationRatio = 412f / 308f
private const val LanguageIllustrationRatio = 412f / 287f

@Composable
fun AppearanceScreen(onClose: () -> Unit) {
    SettingsScaffold(
        title = "Apariencia",
        onClose = onClose,
        topPadding = ContentTopPadding,
        footer = { Illustration(R.drawable.img_appearance_bg, AppearanceIllustrationRatio, alpha = 0.8f) },
    ) {
        Text(
            text = "Elige cómo se ve NightWatch Protocol en tu dispositivo.",
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            // Figma puts this line 8dp inside the cards' left edge.
            modifier = Modifier.padding(start = 8.dp),
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
        topPadding = ContentTopPadding,
        footer = { Illustration(R.drawable.img_language_bg, LanguageIllustrationRatio, alpha = 0.7f) },
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

/** Full-width illustration right below the options, with the proportions of its Figma frame. */
@Composable
private fun ColumnScope.Illustration(drawable: Int, aspectRatio: Float, alpha: Float) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .alpha(alpha),
    )
}
