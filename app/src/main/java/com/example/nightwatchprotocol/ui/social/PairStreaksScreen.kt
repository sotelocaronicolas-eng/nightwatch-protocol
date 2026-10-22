package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.nightwatchprotocol.data.social.PairState
import com.example.nightwatchprotocol.data.social.PairStatus
import com.example.nightwatchprotocol.data.social.PairStreak
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily

/** "Racha en pareja": the list that replaces "Racha grupal" (decision D15). Tapping an active pair opens its detail. */
@Composable
fun PairStreaksScreen(
    onClose: () -> Unit,
    onOpenPair: (friendCode: String) -> Unit,
    viewModel: PairStreaksViewModel = viewModel(factory = PairStreaksViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SocialScreen(title = stringResource(R.string.social_pairs_title), onClose = onClose) {
        PairStreaksContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onOpenPair = onOpenPair,
            onAccept = viewModel::onAccept,
            onDecline = viewModel::onDecline,
            onCancel = viewModel::onCancel,
            onInvite = viewModel::onInvite,
        )
    }
}

@Composable
internal fun PairStreaksContent(
    uiState: PairStreaksUiState,
    onRetry: () -> Unit,
    onOpenPair: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit,
    onInvite: (String) -> Unit,
) {
    val pairs = uiState.pairs
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        SocialBodyText(stringResource(R.string.social_pairs_intro))
        uiState.loadError?.let { ErrorNotice(it, onRetry) }
        uiState.actionError?.let { InlineError(it) }
        if (pairs == null) {
            if (uiState.loading) SocialLoading()
            return@Column
        }
        if (pairs.isEmpty()) EmptyState(stringResource(R.string.social_pairs_empty))
        pairs.forEach { pair ->
            val busy = pair.friendCode in uiState.busy
            when (pair.status) {
                PairStatus.Active -> ActivePairRow(pair, onClick = { onOpenPair(pair.friendCode) })
                PairStatus.PendingReceived -> PersonRow(
                    name = pair.username,
                    subtitle = stringResource(R.string.social_pairs_pending_received),
                    avatarKey = pair.avatarKey,
                    avatarVersion = pair.avatarVersion,
                    details = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            SocialPill(stringResource(R.string.social_pairs_accept), { onAccept(pair.friendCode) }, style = PillStyle.Gold, enabled = !busy)
                            SocialPill(stringResource(R.string.social_pairs_decline), { onDecline(pair.friendCode) }, style = PillStyle.Outlined, enabled = !busy)
                        }
                    },
                )
                PairStatus.PendingSent -> PersonRow(
                    name = pair.username,
                    subtitle = stringResource(R.string.social_pairs_pending_sent),
                    avatarKey = pair.avatarKey,
                    avatarVersion = pair.avatarVersion,
                    details = {
                        SocialPill(
                            text = stringResource(R.string.social_pairs_cancel),
                            onClick = { onCancel(pair.friendCode) },
                            style = PillStyle.Outlined,
                            enabled = !busy,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    },
                )
            }
        }
        SocialSectionTitle(stringResource(R.string.social_pairs_invite_title))
        if (uiState.inviteCandidates.isEmpty()) {
            SocialBodyText(stringResource(R.string.social_pairs_invite_none))
        }
        uiState.inviteCandidates.forEach { friend ->
            PersonRow(
                name = friend.username,
                subtitle = null,
                avatarKey = friend.avatarKey,
                avatarVersion = friend.avatarVersion,
                trailing = {
                    SocialPill(
                        text = stringResource(R.string.social_pairs_invite_button),
                        onClick = { onInvite(friend.friendCode) },
                        style = PillStyle.Gold,
                        enabled = friend.friendCode !in uiState.busy,
                    )
                },
            )
        }
    }
}

@Composable
private fun ActivePairRow(pair: PairStreak, onClick: () -> Unit) {
    val metrics = pair.metrics
    PersonRow(
        name = pair.username,
        subtitle = null,
        avatarKey = pair.avatarKey,
        avatarVersion = pair.avatarVersion,
        onClick = onClick,
        details = {
            if (metrics != null) {
                Text(
                    text = if (metrics.currentDays > 0) {
                        stringResource(
                            R.string.social_friend_streak_active,
                            pluralStringResource(R.plurals.social_days, metrics.currentDays, metrics.currentDays),
                        )
                    } else {
                        stringResource(R.string.social_card_pair_none)
                    },
                    color = if (metrics.currentDays > 0) NightWatchGold else SocialOnLight,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
                Text(
                    text = stringResource(
                        when (metrics.state) {
                            PairState.Active -> R.string.social_pairs_state_active
                            PairState.AtRisk -> R.string.social_pairs_state_at_risk
                            PairState.None -> R.string.social_pairs_state_none
                            PairState.RouteComplete -> R.string.social_pairs_state_route_complete
                        },
                    ),
                    color = SocialOnLight,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 11.sp,
                )
            }
        },
    )
}
