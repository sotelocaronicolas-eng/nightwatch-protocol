package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.BadgeState
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** "Perfil amigo" (Figma 2446:265). */
@Composable
fun FriendProfileScreen(
    onClose: () -> Unit,
    onChallengeSent: () -> Unit,
    viewModel: FriendProfileViewModel = viewModel(factory = FriendProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FriendProfileEvent.Removed -> onClose()
                FriendProfileEvent.ChallengeSent -> onChallengeSent()
            }
        }
    }

    SocialScreen(title = uiState.username.ifEmpty { stringResource(R.string.social_friends_title) }, onClose = onClose) {
        FriendProfileContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onInvitePair = viewModel::onInvitePairClicked,
            onBattle = viewModel::onBattleClicked,
            onRemove = viewModel::onRemoveClicked,
        )
    }
    if (uiState.confirmingRemove) {
        RemoveDialog(
            username = uiState.username,
            onConfirm = viewModel::onRemoveConfirmed,
            onDismiss = viewModel::onRemoveDismissed,
        )
    }
}

@Composable
internal fun FriendProfileContent(
    uiState: FriendProfileUiState,
    onRetry: () -> Unit,
    onInvitePair: () -> Unit,
    onBattle: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        uiState.loadError?.let { ErrorNotice(it, onRetry) }
        if (!uiState.loaded) {
            if (uiState.loading) SocialLoading()
            return@Column
        }
        EmojiCircle(stringResource(R.string.social_icon_person), size = 100, modifier = Modifier.padding(top = 12.dp))
        Text(
            text = uiState.username,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (uiState.streakDays > 0) {
            Text(
                text = stringResource(
                    R.string.social_profile_streak,
                    pluralStringResource(R.plurals.social_days, uiState.streakDays, uiState.streakDays),
                ),
                color = NightWatchGold,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 14.sp,
            )
        } else {
            SocialBodyText(stringResource(R.string.social_friend_no_streak))
        }
        SocialBodyText(
            stringResource(if (uiState.trainedToday) R.string.social_profile_trained_today else R.string.social_profile_pending_today),
        )

        if (uiState.hidden) {
            EmptyState(stringResource(R.string.social_profile_hidden, uiState.username))
        } else {
            ProgressBlock(uiState)
        }

        SocialSectionTitle(stringResource(R.string.social_profile_history), modifier = Modifier.align(Alignment.Start))
        if (uiState.history.isEmpty()) {
            SocialBodyText(stringResource(R.string.social_profile_history_empty), modifier = Modifier.align(Alignment.Start))
        } else {
            Column(verticalArrangement = SocialListSpacing) {
                uiState.history.forEach { HistoryRow(it, uiState.username) }
            }
        }

        uiState.actionError?.let { InlineError(it, Modifier.padding(top = 10.dp)) }
        NightWatchButton(
            text = stringResource(R.string.social_profile_battle),
            onClick = onBattle,
            enabled = !uiState.challenging,
            modifier = Modifier.padding(top = 20.dp),
        )
        if (uiState.pairInviteSent) {
            SocialBodyText(
                stringResource(R.string.social_profile_invite_pair_sent, uiState.username),
                alpha = 1f,
                modifier = Modifier.padding(top = 20.dp),
            )
        } else {
            SocialPill(
                text = stringResource(R.string.social_profile_invite_pair),
                onClick = onInvitePair,
                style = PillStyle.Gold,
                enabled = !uiState.invitingPair,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        Text(
            text = stringResource(R.string.social_profile_remove),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 28.dp)
                .heightIn(min = 48.dp)
                .clickable(enabled = !uiState.removing, role = Role.Button, onClick = onRemove)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Level, the two counters and the badges: only when the friend shows their progress (D11). */
@Composable
private fun ColumnScope.ProgressBlock(uiState: FriendProfileUiState) {
    uiState.fighterLevel?.let {
        SocialBodyText(stringResource(R.string.social_profile_level, it), modifier = Modifier.padding(top = 4.dp), alpha = 1f)
    }
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .background(SocialCardTint, RoundedCornerShape(16.dp))
            .padding(vertical = 18.dp),
    ) {
        Counter(uiState.exercises ?: 0, stringResource(R.string.social_profile_exercises))
        Counter(uiState.labs ?: 0, stringResource(R.string.social_profile_labs))
    }
    SocialSectionTitle(stringResource(R.string.social_profile_badges), modifier = Modifier.align(Alignment.Start))
    if (uiState.unlockedBadges.isEmpty()) {
        SocialBodyText(stringResource(R.string.social_profile_no_badges), modifier = Modifier.align(Alignment.Start))
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            uiState.unlockedBadges.forEach { BadgeCard(it) }
        }
    }
}

@Composable
private fun Counter(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(text = label, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
    }
}

@Composable
private fun BadgeCard(badge: BadgeState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .background(NightWatchSurfaceButton, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        EmojiCircle(badge.definition.emoji, size = 44)
        Text(
            text = badge.definition.name,
            color = SocialOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HistoryRow(row: FriendBattleRow, friendName: String) {
    val title = when (row.outcome) {
        BattleOutcome.Win -> stringResource(R.string.social_profile_won, friendName)
        BattleOutcome.Loss -> stringResource(R.string.social_profile_lost, friendName)
        BattleOutcome.Draw -> stringResource(R.string.social_profile_draw, friendName)
    }
    val age = row.daysAgo?.let { days ->
        if (days == 0) stringResource(R.string.social_ago_today) else pluralStringResource(R.plurals.social_ago_days, days, days)
    }
    PersonRow(name = title, subtitle = age, showAvatar = false)
}

@Composable
private fun RemoveDialog(username: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.social_profile_remove_title, username)) },
        text = { Text(stringResource(R.string.social_profile_remove_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.social_profile_remove_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.social_cancel)) } },
    )
}
