package com.example.nightwatchprotocol.ui.social

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.FriendRelation
import com.example.nightwatchprotocol.data.social.FriendSearchHit
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import kotlinx.coroutines.launch

/** "Agregar amigos" (Figma 2446:212, without "Sugerencias"). */
@Composable
fun AddFriendScreen(
    onClose: () -> Unit,
    onOpenRequests: () -> Unit,
    viewModel: AddFriendViewModel = viewModel(factory = AddFriendViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SocialScreen(title = stringResource(R.string.social_add_title), onClose = onClose) {
        AddFriendContent(
            uiState = uiState,
            onQueryChanged = viewModel::onQueryChanged,
            onSearch = viewModel::onSearch,
            onInvite = viewModel::onInvite,
            onAcceptIncoming = viewModel::onAcceptIncoming,
            onOpenRequests = onOpenRequests,
        )
    }
}

@Composable
internal fun AddFriendContent(
    uiState: AddFriendUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onInvite: () -> Unit,
    onAcceptIncoming: () -> Unit,
    onOpenRequests: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
    ) {
        SocialBodyText(stringResource(R.string.social_add_intro))
        NightWatchTextField(
            label = stringResource(R.string.social_add_search_label),
            value = uiState.query,
            onValueChange = onQueryChanged,
            isError = uiState.error != null && uiState.hit == null,
            supportingText = uiState.error.takeIf { uiState.hit == null }?.let { stringResource(it.messageRes()) }
                ?: stringResource(R.string.social_add_search_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        SocialPill(
            text = stringResource(R.string.social_add_search_button),
            onClick = onSearch,
            style = PillStyle.Gold,
            enabled = !uiState.searching && uiState.query.isNotBlank(),
        )
        uiState.hit?.let { hit -> HitRow(hit, uiState.working, onInvite, onAcceptIncoming) }
        uiState.error?.takeIf { uiState.hit != null }?.let { InlineError(it) }
        uiState.notice?.let { notice -> NoticeText(notice) }
        MyCodeCard(uiState.myFriendCode)
        RequestsLink(uiState.pendingRequests, onOpenRequests)
    }
}

@Composable
private fun HitRow(hit: FriendSearchHit, working: Boolean, onInvite: () -> Unit, onAccept: () -> Unit) {
    PersonRow(
        name = hit.username,
        subtitle = hit.friendCode,
        trailing = {
            when (hit.relation) {
                FriendRelation.None -> SocialPill(stringResource(R.string.social_add_invite), onInvite, style = PillStyle.Gold, enabled = !working)
                FriendRelation.Received -> SocialPill(stringResource(R.string.social_add_accept_request), onAccept, style = PillStyle.Gold, enabled = !working)
                FriendRelation.Sent -> StateText(stringResource(R.string.social_add_sent))
                FriendRelation.Friends -> StateText(stringResource(R.string.social_add_already_friends))
            }
        },
    )
}

@Composable
private fun StateText(text: String) {
    Text(text = text, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun NoticeText(notice: AddFriendNotice) {
    val text = when (notice) {
        is AddFriendNotice.Invited -> stringResource(R.string.social_add_invited_ok, notice.username)
        is AddFriendNotice.Accepted -> stringResource(R.string.social_add_accepted_ok, notice.username)
    }
    SocialBodyText(text, alpha = 1f)
}

/** "Tu código de amigo" with "Copiar" (Figma 2446:212). The code is not a secret: it is what the person shares. */
@Composable
private fun MyCodeCard(code: String?) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(code) { mutableStateOf(false) }
    val label = stringResource(R.string.social_add_my_code)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SocialCardTint, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
            Text(
                text = code ?: stringResource(R.string.social_loading_dots),
                color = SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
        SocialPill(
            text = stringResource(if (copied) R.string.social_add_copied else R.string.social_add_copy),
            enabled = code != null,
            onClick = {
                if (code != null) {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, code)))
                        copied = true
                    }
                }
            },
        )
    }
}

/** "📥 Solicitudes de amistad" with the count of requests received. */
@Composable
private fun RequestsLink(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SocialElevated, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.social_add_requests_link),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp).background(NightWatchGold, CircleShape)) {
                Text(text = count.toString(), color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
