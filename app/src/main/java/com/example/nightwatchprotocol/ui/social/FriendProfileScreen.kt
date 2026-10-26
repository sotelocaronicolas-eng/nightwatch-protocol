package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/**
 * "Perfil amigo" (Figma 2446:265, replaced by "SOCIAL UPDATE - PERFIL AMIGO" 2638:374). The screen itself does not scroll: the
 * profile sections sit in the middle and the buttons stay fixed at the bottom. Only when the sections do not fit (a small phone, a
 * large font) does the middle area scroll, and the buttons stay in place.
 */
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

    val reportBlock = rememberReportBlockState()

    SocialProfileScreen(
        title = uiState.username.ifEmpty { stringResource(R.string.social_friends_title) },
        onClose = onClose,
        profileBg = uiState.profileBg,
    ) {
        FriendProfileContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onInvitePair = viewModel::onInvitePairClicked,
            onBattle = viewModel::onBattleClicked,
            onRemove = viewModel::onRemoveClicked,
            onReport = { reportBlock.openReport(uiState.username) },
            onBlock = { reportBlock.openBlock(uiState.username) },
        )
    }
    if (uiState.confirmingRemove) {
        RemoveDialog(
            username = uiState.username,
            onConfirm = viewModel::onRemoveConfirmed,
            onDismiss = viewModel::onRemoveDismissed,
        )
    }
    // Blocking a friend ends the friendship: there is no profile left to show.
    ReportBlockDialogs(reportBlock, onBlocked = { onClose() })
}

@Composable
internal fun ColumnScope.FriendProfileContent(
    uiState: FriendProfileUiState,
    onRetry: () -> Unit,
    onInvitePair: () -> Unit,
    onBattle: () -> Unit,
    onRemove: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    // The middle: a scroll container that only scrolls when its content does not fit, so the buttons below are never covered.
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        uiState.loadError?.let { ErrorNotice(it, onRetry) }
        if (!uiState.loaded) {
            if (uiState.loading) SocialLoading()
        } else {
            if (!uiState.extrasAvailable) InlineError(SocialError.Unavailable, Modifier.padding(bottom = 8.dp))
            ProfileSections(
                content = uiState.toProfileContent(hiddenNotice = stringResource(R.string.social_profile_hidden, uiState.username)),
                avatar = {
                    SocialPhotoAvatar(
                        avatarKey = uiState.avatarKey,
                        avatarVersion = uiState.avatarVersion,
                        emoji = stringResource(R.string.social_icon_person),
                        size = 130.dp,
                        withWizardGear = false,
                        contentDescription = stringResource(R.string.social_avatar_of, uiState.username),
                    )
                },
            )
        }
    }
    if (uiState.loaded) {
        FriendActions(uiState, onInvitePair, onBattle, onRemove, onReport, onBlock)
    }
}

/** The buttons that stay at the bottom: battle, invite to a pair streak, remove the friend, report and block. */
@Composable
private fun FriendActions(
    uiState: FriendProfileUiState,
    onInvitePair: () -> Unit,
    onBattle: () -> Unit,
    onRemove: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        uiState.actionError?.let { InlineError(it, Modifier.padding(bottom = 6.dp)) }
        NightWatchButton(
            text = stringResource(R.string.social_profile_battle),
            onClick = onBattle,
            enabled = !uiState.challenging,
        )
        if (uiState.pairInviteSent) {
            SocialBodyText(
                stringResource(R.string.social_profile_invite_pair_sent, uiState.username),
                alpha = 1f,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            SocialPill(
                text = stringResource(R.string.social_profile_invite_pair),
                onClick = onInvitePair,
                style = PillStyle.Gold,
                enabled = !uiState.invitingPair,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            text = stringResource(R.string.social_profile_remove),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .heightIn(min = 48.dp)
                .clickable(enabled = !uiState.removing, role = Role.Button, onClick = onRemove)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        ReportBlockLinks(username = uiState.username, onReport = onReport, onBlock = onBlock)
    }
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
