package com.example.nightwatchprotocol.ui.profile.appearance

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.preferences.ThemeMode
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme

// "Apariencia" chooses the look (Module 5, Stage 4). "Idioma" still chooses nothing: the app stays in Spanish until
// Stage 7, so tapping "English" does nothing.

/**
 * Both screens center their whole block (options + illustration) in the space under the header, with the scroll as a
 * fallback. The top padding equals the scaffold's bottom padding (24dp) so the block is centered, not pushed down.
 * (Figma pins the options ~140dp below the header and leaves the empty space at the bottom; this is a deliberate change.)
 */
private val ContentTopPadding = 24.dp

/** Illustration boxes from Figma: 412x308 (Apariencia) and 412x287 (Idioma, drawn with "cover"). */
private const val AppearanceIllustrationRatio = 412f / 308f
private const val LanguageIllustrationRatio = 412f / 287f

/** "Apariencia": reads and saves the look with [AppearanceViewModel]; the change shows at once (no activity restart). */
@Composable
fun AppearanceScreen(
    onClose: () -> Unit,
    viewModel: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceContent(themeMode = uiState.themeMode, onThemeSelected = viewModel::onThemeSelected, onClose = onClose)
}

/** The stateless body of [AppearanceScreen]: the marked card (✓ and 3 dp border) is the one of [themeMode]. */
@Composable
fun AppearanceContent(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onClose: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_menu_appearance),
        onClose = onClose,
        topPadding = ContentTopPadding,
        centerVertically = true,
        footer = { Illustration(R.drawable.img_appearance_bg, AppearanceIllustrationRatio, alpha = 0.8f) },
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_intro),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            // Figma puts this line 8dp inside the cards' left edge.
            modifier = Modifier.padding(start = 8.dp),
        )
        // "Automático" selected has no frame in the Figma: it uses the same marked-card style (PROPUESTA).
        ThemeOption("🌙", R.string.settings_appearance_dark, R.string.settings_appearance_dark_subtitle, ThemeMode.DARK, themeMode, onThemeSelected)
        ThemeOption("☀️", R.string.settings_appearance_light, R.string.settings_appearance_light_subtitle, ThemeMode.LIGHT, themeMode, onThemeSelected)
        ThemeOption("⚙️", R.string.settings_appearance_auto, R.string.settings_appearance_auto_subtitle, ThemeMode.SYSTEM, themeMode, onThemeSelected)
    }
}

@Composable
private fun ThemeOption(
    emoji: String,
    @StringRes label: Int,
    @StringRes subtitle: Int,
    option: ThemeMode,
    current: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val isSelected = option == current
    SettingsRow(
        emoji = emoji,
        label = stringResource(label),
        subtitle = stringResource(subtitle),
        highlighted = isSelected,
        trailing = { if (isSelected) CheckMark() else NoTrailing() },
        onClick = { onSelected(option) },
        modifier = Modifier.semantics { selected = isSelected },
    )
}

@Composable
fun LanguageScreen(onClose: () -> Unit) {
    SettingsScaffold(
        title = stringResource(R.string.settings_menu_language),
        onClose = onClose,
        topPadding = ContentTopPadding,
        centerVertically = true,
        footer = { Illustration(R.drawable.img_language_bg, LanguageIllustrationRatio, alpha = 0.7f) },
    ) {
        SettingsRow(emoji = "🇪🇸", label = stringResource(R.string.settings_language_spanish), highlighted = true, trailing = { CheckMark() })
        SettingsRow(emoji = "🇺🇸", label = stringResource(R.string.settings_language_english), trailing = { NoTrailing() })
    }
}

@Composable
private fun CheckMark() {
    Text(text = "✓", color = NightWatchTheme.colors.selectionCheck, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
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
