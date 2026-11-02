package com.example.nightwatchprotocol.ui.profile.myprofile

import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.learning.StreakState

data class MyProfileUiState(
    val displayName: UiText = UiText.dynamic(""),
    val email: String = "",
    val memberSince: UiText = UiText.dynamic("—"),
    /** The real current streak: "…" while it is read, "No disponible" if it never could be. */
    val streak: StreakState = StreakState.Loading,
    /** Null while the quiz result is still loading; "Sin evaluar" when there is none or it could not be read. */
    val level: UiText? = null,
    /** Null when there is none, it could not be read, or it is still loading. */
    val recoveryEmail: String? = null,
    /** Label of the chosen gender; null when not defined (shown as "—"). */
    val gender: UiText? = null,
    /** `dd/MM/yyyy`; null when not defined (shown as "—"). */
    val birthDate: String? = null,
    val avatar: AvatarDisplay = AvatarDisplay.Initial("?"),
)
