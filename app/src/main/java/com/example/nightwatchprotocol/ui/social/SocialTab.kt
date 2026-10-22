package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.home.HomeTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/**
 * The "Social" tab of Home. Without a username it is the "crear nombre de usuario" screen (Social is not enabled until then);
 * with one it is "Social - Inicio" (Figma 2440:163): three tiles that open the pair streaks, the friends and the battles.
 */
@Composable
fun SocialTab(
    onOpenPairStreaks: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenBattles: () -> Unit,
    viewModel: SocialHomeViewModel = viewModel(factory = SocialHomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Read again each time the tab shows or Home comes back (after the friends, the pairs or a battle), so the numbers are current.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SocialTabContent(
        uiState = uiState,
        onRetry = viewModel::refresh,
        onUsernameChanged = viewModel::onUsernameChanged,
        onCreateUsername = viewModel::onCreateUsername,
        onOpenPairStreaks = onOpenPairStreaks,
        onOpenFriends = onOpenFriends,
        onOpenBattles = onOpenBattles,
    )
}

@Composable
internal fun SocialTabContent(
    uiState: SocialHomeUiState,
    onRetry: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onCreateUsername: () -> Unit,
    onOpenPairStreaks: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenBattles: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(title = stringResource(R.string.social_title), titleTop = 25.dp)
        when (val status = uiState.status) {
            SocialHomeStatus.Loading -> SocialLoading()
            is SocialHomeStatus.Failed -> ErrorNotice(status.error, onRetry, Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            SocialHomeStatus.NeedsUsername -> CreateUsernameContent(uiState, onUsernameChanged, onCreateUsername)
            SocialHomeStatus.Ready -> ReadyContent(uiState, onRetry, onOpenPairStreaks, onOpenFriends, onOpenBattles)
        }
    }
}

@Composable
private fun ReadyContent(
    uiState: SocialHomeUiState,
    onRetry: () -> Unit,
    onOpenPairStreaks: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenBattles: () -> Unit,
) {
    val loading = stringResource(R.string.social_loading_dots)
    val unavailable = stringResource(R.string.social_unavailable_value)
    val failed = uiState.tilesError != null

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
    ) {
        SocialBodyText(stringResource(R.string.social_intro))
        uiState.tilesError?.let { ErrorNotice(it, onRetry) }
        SocialCard(
            emoji = stringResource(R.string.social_icon_pair),
            title = stringResource(R.string.social_card_pair_title),
            chip = when (val days = uiState.pairStreakDays) {
                null -> if (failed) unavailable else loading
                0 -> stringResource(R.string.social_card_pair_none)
                else -> pluralStringResource(R.plurals.social_days, days, days)
            },
            buttonText = stringResource(R.string.social_card_pair_button),
            onClick = onOpenPairStreaks,
        )
        SocialCard(
            emoji = stringResource(R.string.social_icon_friends),
            title = stringResource(R.string.social_card_friends_title),
            chip = uiState.friendsCount?.let { pluralStringResource(R.plurals.social_friends_count, it, it) }
                ?: if (failed) unavailable else loading,
            buttonText = stringResource(R.string.social_card_friends_button),
            onClick = onOpenFriends,
        )
        SocialCard(
            emoji = stringResource(R.string.social_icon_battles),
            title = stringResource(R.string.social_card_battles_title),
            chip = scoreText(uiState.wins, uiState.losses, uiState.draws, if (failed) unavailable else loading),
            buttonText = stringResource(R.string.social_card_battles_button),
            onClick = onOpenBattles,
        )
    }
}

/** "5V · 2D", with "· 1E" only when there are draws (difference S14 of the spec). */
@Composable
internal fun scoreText(wins: Int?, losses: Int?, draws: Int, placeholder: String): String = when {
    wins == null || losses == null -> placeholder
    draws > 0 -> stringResource(R.string.social_score_with_draws, wins, losses, draws)
    else -> stringResource(R.string.social_score, wins, losses)
}

/** The screen to create the username (M1 of the spec): the same form fields and button as the other forms of the app. */
@Composable
private fun CreateUsernameContent(
    uiState: SocialHomeUiState,
    onUsernameChanged: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.social_username_title),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
        )
        SocialBodyText(stringResource(R.string.social_username_body), modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        NightWatchTextField(
            label = stringResource(R.string.social_username_label),
            value = uiState.username,
            onValueChange = onUsernameChanged,
            isError = uiState.usernameError != null,
            supportingText = uiState.usernameError?.let { stringResource(it.messageRes()) }
                ?: stringResource(R.string.social_username_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate() }),
        )
        NightWatchButton(
            text = stringResource(if (uiState.creating) R.string.social_username_creating else R.string.social_username_create),
            onClick = onCreate,
            enabled = !uiState.creating && uiState.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
    }
}
