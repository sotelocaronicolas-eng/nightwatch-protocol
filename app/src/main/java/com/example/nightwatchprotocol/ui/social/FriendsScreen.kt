package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily

/** "Amigos" (Figma 2446:181). Tapping a row opens the profile of that friend. */
@Composable
fun FriendsScreen(
    onClose: () -> Unit,
    onOpenFriend: (friendCode: String) -> Unit,
    onAddFriends: () -> Unit,
    viewModel: FriendsViewModel = viewModel(factory = FriendsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SocialScreen(title = stringResource(R.string.social_friends_title), onClose = onClose) {
        Box(modifier = Modifier.fillMaxSize()) {
            FriendsContent(uiState, viewModel::refresh, onOpenFriend)
            NightWatchButton(
                text = stringResource(R.string.social_friends_add),
                onClick = onAddFriends,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
internal fun FriendsContent(uiState: FriendsUiState, onRetry: () -> Unit, onOpenFriend: (String) -> Unit) {
    val friends = uiState.friends
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 100.dp),
    ) {
        uiState.error?.let { ErrorNotice(it, onRetry) }
        when {
            friends == null && uiState.loading -> SocialLoading()
            friends == null -> Unit
            friends.isEmpty() -> EmptyState(stringResource(R.string.social_friends_empty))
            else -> {
                SocialBodyText(
                    stringResource(
                        R.string.social_friends_summary,
                        pluralStringResource(R.plurals.social_friends_count, friends.size, friends.size),
                        pluralStringResource(R.plurals.social_friends_keeping_today, uiState.keepingToday, uiState.keepingToday),
                    ),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                friends.forEach { friend -> FriendRow(friend, onClick = { onOpenFriend(friend.friendCode) }) }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit) {
    PersonRow(
        name = friend.username,
        subtitle = null,
        avatarKey = friend.avatarKey,
        avatarVersion = friend.avatarVersion,
        onClick = onClick,
        details = {
            Text(
                text = if (friend.streakDays > 0) {
                    stringResource(
                        R.string.social_friend_streak_active,
                        pluralStringResource(R.plurals.social_days, friend.streakDays, friend.streakDays),
                    )
                } else {
                    stringResource(R.string.social_friend_no_streak)
                },
                color = if (friend.streakDays > 0) NightWatchGold else SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            )
            Text(
                text = stringResource(if (friend.trainedToday) R.string.social_friend_trained_today else R.string.social_friend_pending_today),
                color = SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 11.sp,
            )
        },
    )
}
