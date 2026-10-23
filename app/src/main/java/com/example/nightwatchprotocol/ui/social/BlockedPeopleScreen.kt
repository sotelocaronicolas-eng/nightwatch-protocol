package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R

/** "Personas bloqueadas" (reached from Amigos): who the person blocked, each with "Desbloquear" (script 0011). */
@Composable
fun BlockedPeopleScreen(
    onClose: () -> Unit,
    viewModel: BlockedPeopleViewModel = viewModel(factory = BlockedPeopleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SocialScreen(title = stringResource(R.string.social_blocked_title), onClose = onClose) {
        BlockedPeopleContent(uiState = uiState, onRetry = viewModel::refresh, onUnblock = viewModel::onUnblock)
    }
}

@Composable
internal fun BlockedPeopleContent(
    uiState: BlockedPeopleUiState,
    onRetry: () -> Unit,
    onUnblock: (username: String) -> Unit,
) {
    val people = uiState.people
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        // "Función no disponible por ahora" while 0011 is not applied: a message, not a broken screen.
        uiState.loadError?.let { ErrorNotice(it, onRetry) }
        uiState.actionError?.let { InlineError(it) }
        if (people == null) {
            if (uiState.loading) SocialLoading()
            return@Column
        }
        if (people.isEmpty()) {
            EmptyState(stringResource(R.string.social_blocked_empty))
            return@Column
        }
        SocialBodyText(stringResource(R.string.social_blocked_intro))
        people.forEach { person ->
            PersonRow(
                name = person.username,
                subtitle = null,
                avatarKey = person.avatarKey,
                avatarVersion = person.avatarVersion,
                trailing = {
                    SocialPill(
                        text = stringResource(R.string.social_blocked_unblock),
                        onClick = { onUnblock(person.username) },
                        style = PillStyle.Outlined,
                        enabled = person.username !in uiState.busy,
                    )
                },
            )
        }
    }
}
