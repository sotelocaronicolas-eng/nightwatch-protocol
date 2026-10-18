package com.example.nightwatchprotocol.ui.home

import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.profile.FixedProfileData
import kotlinx.serialization.Serializable

/** The three tabs that live inside Home. "Social" and "Perfil" of the bar are not tabs: one is "Próximamente", the other opens the user menu. */
@Serializable
enum class HomeTab {
    Inicio,
    Ruta,
    Labs,
    ;

    companion object {
        fun fromName(name: String?): HomeTab? = entries.firstOrNull { it.name == name }
    }
}

/** What the "Lección diaria" card of Inicio knows. It never blocks the rest of the screen. */
sealed interface DailyLessonState {
    data object Loading : DailyLessonState

    /** The first unlocked island whose lesson has not been seen (decision Q3). */
    data class Pending(val topic: LearningTopic, val title: String) : DailyLessonState

    /** Nothing to open: all the lessons are seen, or the lesson is seen and the lab is missing. */
    data object NoneLeft : DailyLessonState

    data class Failed(val offline: Boolean) : DailyLessonState
}

data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.Inicio,
    val displayName: String = "",
    val avatar: AvatarDisplay = AvatarDisplay.Initial("?"),
    /** BAJO / MEDIO / ALTO, "Sin evaluar", or null while it is being read. */
    val level: String? = null,
    val streakDays: Int = FixedProfileData.STREAK_DAYS,
    val dailyLesson: DailyLessonState = DailyLessonState.Loading,
)

sealed interface HomeEvent {
    /** The user's own sign-out finished, or another device revoked this session: back to Login. */
    data object SignedOut : HomeEvent

    /** "Perfil" of the bar: opens the existing user menu on top of Home. */
    data object OpenUserMenu : HomeEvent

    /** Social and the two cards that are not built yet: one Snackbar "Próximamente". */
    data object ShowComingSoon : HomeEvent

    /** "Lección diaria" with a pending lesson: opens it. */
    data class OpenLesson(val topic: LearningTopic) : HomeEvent

    /** "Lección diaria" with nothing pending: Snackbar "No tienes lecciones pendientes" (decision Q3). */
    data object ShowNoPendingLessons : HomeEvent
}
