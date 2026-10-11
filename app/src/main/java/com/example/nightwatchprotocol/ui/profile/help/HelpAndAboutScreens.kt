package com.example.nightwatchprotocol.ui.profile.help

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchDisplayStyle
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

// "Ayuda y Soporte" and "Acerca de" are static: the design has no answers, links or addresses, so none of
// the rows or the support button do anything yet.

@Composable
fun HelpScreen(onClose: () -> Unit) {
    SettingsScaffold(title = "Ayuda y Soporte", onClose = onClose, topPadding = 164.dp) {
        SettingsRow("❓", "¿Cómo funciona la Ruta de Aprendizaje?")
        SettingsRow("❓", "¿Por qué perdí mi racha?")
        SettingsRow("❓", "¿Los laboratorios afectan mi progreso?")
        SettingsRow("❓", "¿Cómo reporto un error en la app?")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(48.dp)
                .shadow(4.dp, NightWatchPillShape)
                .background(NightWatchSurfaceButton, NightWatchPillShape)
                .clip(NightWatchPillShape)
                .clickable(role = Role.Button) {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.img_support_mail),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 36.dp, height = 24.dp),
                )
                Text(
                    text = "Contactar a soporte",
                    color = Color(0xFF162523),
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
fun AboutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    SettingsScaffold(title = "Acerca de", onClose = onClose, topPadding = 28.dp) {
        Image(
            painter = painterResource(R.drawable.img_logo_nightwatch),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(50.dp),
        )
        Text(
            text = "Nightwatch Protocol",
            style = NightWatchDisplayStyle,
            color = NightWatchOnBackground,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Text(
            text = "Versión $versionName",
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        SettingsRow("📄", "Términos y Condiciones")
        SettingsRow("🔏", "Política de Privacidad")
        SettingsRow("⭐", "Calificar la app")
        SettingsRow("🐞", "Reportar un problema")
        Text(
            text = "Hecho con 🛡️ para futuros defensores de la ciberseguridad.",
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        )
    }
}
