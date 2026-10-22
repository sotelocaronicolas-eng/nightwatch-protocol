package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.ChallengeStatus
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.ReceivedChallenge
import com.example.nightwatchprotocol.data.social.SentChallenge
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/** "Batallas" (Figma 2448:187), with the challenges received and the wait for the one the person sent. */
@Composable
fun BattlesScreen(
    onClose: () -> Unit,
    onQuickSearch: () -> Unit,
    onOpenBattle: (battleId: String) -> Unit,
    viewModel: BattlesViewModel = viewModel(factory = BattlesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The challenges are read again every few seconds only while the screen is visible.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    // Leaving the screen for good cancels the challenge that is still waiting.
    DisposableEffect(viewModel) { onDispose { viewModel.onLeft() } }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BattlesEvent.OpenBattle -> onOpenBattle(event.battleId)
            }
        }
    }

    SocialScreen(title = stringResource(R.string.social_battles_title), onClose = onClose) {
        BattlesContent(
            uiState = uiState,
            onQuickSearch = onQuickSearch,
            onChallenge = viewModel::onChallenge,
            onCancelWaiting = viewModel::onCancelWaiting,
            onAccept = viewModel::onAccept,
            onDecline = viewModel::onDecline,
        )
    }
}

@Composable
internal fun BattlesContent(
    uiState: BattlesUiState,
    onQuickSearch: () -> Unit,
    onChallenge: (String) -> Unit,
    onCancelWaiting: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    val placeholder = stringResource(R.string.social_unavailable_value)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        uiState.loadError?.let { ErrorNotice(it, null) }
        uiState.actionError?.let { InlineError(it) }
        ScoreCard(uiState.wins?.toString() ?: placeholder, uiState.losses?.toString() ?: placeholder)
        uiState.level?.let { level ->
            val next = uiState.nextLevelXp
            SocialBodyText(
                text = if (next != null) {
                    stringResource(R.string.social_end_level_next, level, uiState.xp, next)
                } else {
                    stringResource(R.string.social_end_level, level, uiState.xp)
                },
                alpha = 1f,
            )
        }
        uiState.waiting?.let { WaitingCard(it, onCancelWaiting) }
        uiState.sent?.let { SentNotice(it) }
        QuickMatchCard(onQuickSearch)

        if (uiState.received.isNotEmpty()) {
            SocialSectionTitle(stringResource(R.string.social_battles_received_title))
            uiState.received.forEach { challenge -> ReceivedRow(challenge, challenge.friendCode in uiState.busy, onAccept, onDecline) }
        }

        SocialSectionTitle(stringResource(R.string.social_battles_friends_title))
        val friends = uiState.friends
        when {
            friends == null -> if (uiState.loadError == null) SocialLoading()
            friends.isEmpty() -> EmptyState(stringResource(R.string.social_battles_friends_empty))
            else -> friends.forEach { friend -> FriendChallengeRow(friend, uiState.waiting != null, friend.friendCode in uiState.busy, onChallenge) }
        }
    }
}

@Composable
private fun ScoreCard(wins: String, losses: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .background(SocialCardTint, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp),
    ) {
        ScoreItem(wins, stringResource(R.string.social_battles_wins))
        ScoreItem(losses, stringResource(R.string.social_battles_losses))
    }
}

@Composable
private fun ScoreItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(text = label, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
    }
}

@Composable
private fun QuickMatchCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SocialElevated, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.social_battles_quick_title),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        SocialBodyText(stringResource(R.string.social_battles_quick_body), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        SocialPill(
            text = stringResource(R.string.social_battles_quick_button),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "Esperando a Mateo…" with the 2 minutes the server left (decision D16). */
@Composable
private fun WaitingCard(sent: SentChallenge, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SocialElevated, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.social_battles_waiting_title, sent.username),
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            CountdownText(sent.timeLeft, color = NightWatchGold, fontSize = 16.sp)
        }
        SocialBodyText(stringResource(R.string.social_battles_waiting_body), modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        SocialPill(stringResource(R.string.social_battles_waiting_cancel), onCancel, style = PillStyle.Outlined)
    }
}

/** What happened to the challenge the person sent, when it is not waiting any more. */
@Composable
private fun SentNotice(sent: SentChallenge) {
    when (sent.status) {
        ChallengeStatus.Declined -> SocialBodyText(stringResource(R.string.social_battles_declined, sent.username), alpha = 1f)
        ChallengeStatus.Expired -> SocialBodyText(stringResource(R.string.social_battles_expired, sent.username), alpha = 1f)
        ChallengeStatus.Pending, ChallengeStatus.Accepted, ChallengeStatus.Cancelled -> Unit
    }
}

@Composable
private fun ReceivedRow(
    challenge: ReceivedChallenge,
    busy: Boolean,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    PersonRow(
        name = challenge.username,
        subtitle = stringResource(R.string.social_battles_received_body),
        trailing = { CountdownText(challenge.timeLeft) },
        details = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                SocialPill(stringResource(R.string.social_battles_accept), { onAccept(challenge.friendCode) }, style = PillStyle.Gold, enabled = !busy)
                SocialPill(stringResource(R.string.social_battles_decline), { onDecline(challenge.friendCode) }, style = PillStyle.Outlined, enabled = !busy)
            }
        },
    )
}

@Composable
private fun FriendChallengeRow(friend: Friend, blocked: Boolean, busy: Boolean, onChallenge: (String) -> Unit) {
    PersonRow(
        name = friend.username,
        subtitle = if (friend.streakDays > 0) {
            stringResource(R.string.social_friend_streak_active, pluralStringResource(R.plurals.social_days, friend.streakDays, friend.streakDays))
        } else {
            null
        },
        trailing = {
            SocialPill(
                text = stringResource(R.string.social_battles_challenge),
                onClick = { onChallenge(friend.friendCode) },
                style = PillStyle.Gold,
                enabled = !blocked && !busy,
            )
        },
    )
}
