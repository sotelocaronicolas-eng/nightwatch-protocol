package com.example.nightwatchprotocol.ui.path

import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.TOTAL_ISLANDS

/** One island of the map as the screen draws it: [name] and [emoji] come from the lesson content. */
data class IslandUi(
    val topic: LearningTopic,
    val name: String,
    val emoji: String,
    val state: IslandState,
)

sealed interface PathStatus {
    /** The first read is running: the map is shown but taps are ignored (a lock could be wrong). */
    data object Loading : PathStatus

    data object Ready : PathStatus

    /** The progress (or the content) could not be read: the map shows island 1 open and a notice with "Reintentar". */
    data class Failed(val offline: Boolean) : PathStatus
}

data class LearningPathUiState(
    /** BAJO / MEDIO / ALTO, "Sin evaluar", or null while it is being read. */
    val level: String? = null,
    val status: PathStatus = PathStatus.Loading,
    /** The six islands in order; empty only if the lesson content could not be read. */
    val islands: List<IslandUi> = emptyList(),
    /** The "Prueba Final": Locked until the six islands are completed, Completed with a passed attempt. */
    val finalTest: IslandState = IslandState.Locked,
    val completedCount: Int = 0,
    val totalCount: Int = TOTAL_ISLANDS,
)

/** The Snackbar texts of the map; the screen turns each one into a string resource. */
enum class PathMessage {
    /** Tapping a locked island or lab (decision Q6). */
    CompletePreviousIsland,

    /** Tapping the locked "Prueba Final" (decision Q6). */
    CompleteAllIslands,

    /** Tapping the unlocked "Prueba Final": provisional until the exam exists (stage 5). */
    ComingSoon,
}

sealed interface LearningPathEvent {
    data class OpenLesson(val topic: LearningTopic) : LearningPathEvent

    data class ShowMessage(val message: PathMessage) : LearningPathEvent
}
