package com.example.nightwatchprotocol.ui.profile.usermenu

import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.profile.FixedProfileData

data class UserMenuUiState(
    val displayName: String = "",
    val streakDays: Int = FixedProfileData.STREAK_DAYS,
    val avatar: AvatarDisplay = AvatarDisplay.Initial("?"),
)

sealed interface UserMenuEvent {
    data object SignedOut : UserMenuEvent
}
