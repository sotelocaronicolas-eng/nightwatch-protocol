package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/** "Buscando oponente" (Figma 2448:223). */
@Composable
fun QuickSearchScreen(
    onClose: () -> Unit,
    onOpenBattle: (battleId: String) -> Unit,
    onChallengeFriend: () -> Unit,
    viewModel: QuickSearchViewModel = viewModel(factory = QuickSearchViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The search starts when the screen opens and ends when it is hidden or left: the queue is left each time.
    LaunchedEffect(viewModel) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    LeaveScreenEffect(viewModel) { viewModel.onLeft() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickSearchEvent.OpenBattle -> onOpenBattle(event.battleId)
                QuickSearchEvent.Cancelled -> onClose()
            }
        }
    }

    SocialScreen(title = stringResource(R.string.social_search_title), onClose = viewModel::onCancel) {
        QuickSearchContent(
            uiState = uiState,
            onCancel = viewModel::onCancel,
            onSearchAgain = viewModel::onStart,
            onChallengeFriend = onChallengeFriend,
        )
    }
}

@Composable
internal fun QuickSearchContent(
    uiState: QuickSearchUiState,
    onCancel: () -> Unit,
    onSearchAgain: () -> Unit,
    onChallengeFriend: () -> Unit,
) {
    val status = uiState.status
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
        ) {
            Text(text = stringResource(R.string.social_search_icon_search), fontSize = 56.sp)
            Text(
                text = stringResource(R.string.social_search_searching),
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            SocialBodyText(
                stringResource(R.string.social_search_body),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(top = 40.dp),
            ) {
                EmojiCircle(stringResource(R.string.social_search_icon_you), size = 80)
                Text(
                    text = stringResource(R.string.social_search_vs),
                    color = NightWatchGold,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp).background(Color(0xFFA1A1A1), CircleShape)) {
                    Text(text = stringResource(R.string.social_search_unknown), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
            }
            when (status) {
                is QuickSearchStatus.Searching -> if (status.suggestFriend) {
                    SocialBodyText(
                        stringResource(R.string.social_search_suggest),
                        alpha = 1f,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                    SocialPill(
                        text = stringResource(R.string.social_search_challenge_friend),
                        onClick = onChallengeFriend,
                        style = PillStyle.Gold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                is QuickSearchStatus.Failed -> ErrorNotice(status.error, onSearchAgain, Modifier.padding(top = 32.dp))
                QuickSearchStatus.Stopped -> {
                    SocialBodyText(stringResource(R.string.social_search_stopped), alpha = 1f, modifier = Modifier.padding(top = 32.dp))
                    SocialPill(
                        text = stringResource(R.string.social_search_again),
                        onClick = onSearchAgain,
                        style = PillStyle.Gold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                QuickSearchStatus.Idle -> Unit
            }
        }
        Text(
            text = stringResource(R.string.social_search_cancel),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
