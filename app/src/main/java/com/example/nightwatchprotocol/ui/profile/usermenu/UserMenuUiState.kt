package com.example.nightwatchprotocol.ui.profile.usermenu

import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.learning.StreakState

data class UserMenuUiState(
    val displayName: UiText = UiText.dynamic(""),
    val streak: StreakState = StreakState.Loading,
    val avatar: AvatarDisplay = AvatarDisplay.Initial("?"),
)

sealed interface UserMenuEvent {
    data object SignedOut : UserMenuEvent
}
