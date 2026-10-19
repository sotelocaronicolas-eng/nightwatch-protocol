package com.example.nightwatchprotocol.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma BottomNav (2264:116): light bar, top corners 28, shadow (0, -2, 8, 20%), padding-top 14. */
private val BarShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Figma text/on-light (#162523), the colour of an inactive label. */
private val LabelColor = Color(0xFF162523)

/** An inactive tab is drawn at 55% (Figma, e.g. 49:15 "Tab Ruta"). */
private const val INACTIVE_ALPHA = 0.55f

/**
 * SUPUESTO: Figma has no "disabled" tab. Social is drawn at the opacity of the locked islands and labs (60%, 2271:84) on
 * top of the inactive 55%, so it reads as dimmer than its neighbours.
 */
private const val COMING_SOON_ALPHA = INACTIVE_ALPHA * 0.6f

/**
 * The bottom bar of Home: Inicio, Ruta, Labs, Social, Perfil (Figma 49:15, 2266:72, 2271:84, 2440:163).
 * Only Inicio, Ruta and Labs are tabs ([selected]). Social is dimmed and only reports [onSocialClick]; Perfil is not a tab
 * either: it reports [onProfileClick] and never becomes the selected one.
 */
@Composable
fun NightWatchBottomBar(
    selected: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onSocialClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, BarShape)
            .background(NightWatchSurfaceButton, BarShape)
            .navigationBarsPadding()
            .padding(top = 14.dp)
            .height(54.dp),
    ) {
        TabItem(
            emoji = stringResource(R.string.learning_icon_home),
            label = stringResource(R.string.learning_tab_home),
            isSelected = selected == HomeTab.Inicio,
            onClick = { onTabSelected(HomeTab.Inicio) },
        )
        TabItem(
            emoji = stringResource(R.string.learning_icon_path),
            label = stringResource(R.string.learning_tab_path),
            isSelected = selected == HomeTab.Ruta,
            onClick = { onTabSelected(HomeTab.Ruta) },
        )
        TabItem(
            emoji = stringResource(R.string.learning_icon_labs),
            label = stringResource(R.string.learning_tab_labs),
            isSelected = selected == HomeTab.Labs,
            onClick = { onTabSelected(HomeTab.Labs) },
        )
        ComingSoonTabItem(
            emoji = stringResource(R.string.learning_icon_social),
            label = stringResource(R.string.learning_tab_social),
            description = stringResource(R.string.learning_social_tab_description),
            state = stringResource(R.string.learning_state_unavailable),
            onClick = onSocialClick,
        )
        ActionItem(
            emoji = stringResource(R.string.learning_icon_profile),
            label = stringResource(R.string.learning_tab_profile),
            onClick = onProfileClick,
        )
    }
}

@Composable
private fun RowScope.TabItem(emoji: String, label: String, isSelected: Boolean, onClick: () -> Unit) {
    TabContent(
        emoji = emoji,
        label = label,
        isSelected = isSelected,
        alpha = if (isSelected) 1f else INACTIVE_ALPHA,
        modifier = Modifier.selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
    )
}

/** "Perfil": looks like an inactive tab, but it is a button (it opens another screen and is never selected). */
@Composable
private fun RowScope.ActionItem(emoji: String, label: String, onClick: () -> Unit) {
    TabContent(
        emoji = emoji,
        label = label,
        isSelected = false,
        alpha = INACTIVE_ALPHA,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * Social: dimmed, never selected, and tapping it only shows "Próximamente". It stays focusable so TalkBack reads
 * "Social, próximamente" and "No disponible" instead of skipping it.
 */
@Composable
private fun RowScope.ComingSoonTabItem(
    emoji: String,
    label: String,
    description: String,
    state: String,
    onClick: () -> Unit,
) {
    TabContent(
        emoji = emoji,
        label = label,
        isSelected = false,
        alpha = COMING_SOON_ALPHA,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                stateDescription = state
            },
    )
}

@Composable
private fun RowScope.TabContent(
    emoji: String,
    label: String,
    isSelected: Boolean,
    alpha: Float,
    modifier: Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .weight(1f)
            .height(54.dp),
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(alpha).clearAndSetSemantics {},
        )
        Text(
            text = label,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = if (isSelected) NightWatchGold else LabelColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (isSelected) 1f else alpha),
        )
    }
}
