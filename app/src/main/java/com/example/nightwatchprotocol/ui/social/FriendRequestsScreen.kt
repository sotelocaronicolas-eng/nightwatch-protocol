package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R

/** "Solicitudes" (Figma 2446:242, without "Sugerencias"). */
@Composable
fun FriendRequestsScreen(
    onClose: () -> Unit,
    viewModel: FriendRequestsViewModel = viewModel(factory = FriendRequestsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SocialScreen(title = stringResource(R.string.social_requests_title), onClose = onClose) {
        FriendRequestsContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onAccept = viewModel::onAccept,
            onDecline = viewModel::onDecline,
            onCancel = viewModel::onCancel,
        )
    }
}

@Composable
internal fun FriendRequestsContent(
    uiState: FriendRequestsUiState,
    onRetry: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val requests = uiState.requests
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        uiState.loadError?.let { ErrorNotice(it, onRetry) }
        uiState.actionError?.let { InlineError(it) }
        if (requests == null) {
            if (uiState.loading) SocialLoading()
            return@Column
        }
        SocialBodyText(
            if (requests.received.isEmpty()) {
                stringResource(R.string.social_requests_none)
            } else {
                pluralStringResource(R.plurals.social_requests_pending, requests.received.size, requests.received.size)
            },
        )
        requests.received.forEach { person ->
            val busy = person.friendCode in uiState.busy
            PersonRow(
                name = person.username,
                subtitle = stringResource(R.string.social_requests_wants_friend),
                avatarKey = person.avatarKey,
                avatarVersion = person.avatarVersion,
                details = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        SocialPill(stringResource(R.string.social_requests_accept), { onAccept(person.friendCode) }, style = PillStyle.Gold, enabled = !busy)
                        SocialPill(stringResource(R.string.social_requests_decline), { onDecline(person.friendCode) }, style = PillStyle.Outlined, enabled = !busy)
                    }
                },
            )
        }
        if (requests.sent.isNotEmpty()) {
            SocialSectionTitle(stringResource(R.string.social_requests_sent_title))
            requests.sent.forEach { person ->
                PersonRow(
                    name = person.username,
                    subtitle = stringResource(R.string.social_requests_sent_label),
                    avatarKey = person.avatarKey,
                    avatarVersion = person.avatarVersion,
                    details = {
                        SocialPill(
                            text = stringResource(R.string.social_requests_cancel),
                            onClick = { onCancel(person.friendCode) },
                            style = PillStyle.Outlined,
                            enabled = person.friendCode !in uiState.busy,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    },
                )
            }
        }
    }
}
