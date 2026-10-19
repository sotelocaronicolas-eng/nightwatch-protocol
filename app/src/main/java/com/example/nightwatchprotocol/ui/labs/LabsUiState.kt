package com.example.nightwatchprotocol.ui.labs

import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LabDifficulty
import com.example.nightwatchprotocol.data.learning.LearningTopic

/**
 * One lab card as the screen draws it. [state] reuses [IslandState]: Completed ("✅ Completado"), Available ("Comenzar →")
 * or Locked (dimmed, tapping only says why).
 */
data class LabCardUi(
    val topic: LearningTopic,
    val title: String,
    val emoji: String,
    val difficulty: LabDifficulty,
    val state: IslandState,
)

sealed interface LabsStatus {
    /** The first read is running: taps are ignored (a lock could be wrong). */
    data object Loading : LabsStatus

    data object Ready : LabsStatus

    /** The progress (or the content) could not be read: lab 1 is open, the rest locked, and a notice offers "Reintentar". */
    data class Failed(val offline: Boolean) : LabsStatus
}

data class LabsUiState(
    val status: LabsStatus = LabsStatus.Loading,
    /** The six labs in the order of the topics; empty only if the content could not be read. */
    val labs: List<LabCardUi> = emptyList(),
    /** The "Examen Final" card: Locked until the six islands are completed, Completed with a passed attempt. */
    val finalTest: IslandState = IslandState.Locked,
)

/** The Snackbar texts of the list; the screen turns each one into a string resource. */
enum class LabsMessage {
    /** Tapping a locked lab (decision Q6). */
    CompletePreviousIsland,

    /** Tapping the locked "Examen Final" (decision Q6). */
    CompleteAllIslands,
}

sealed interface LabsEvent {
    data class OpenLab(val topic: LearningTopic) : LabsEvent

    /** The unlocked "Examen Final": the exam introduction. */
    data object OpenExam : LabsEvent

    data class ShowMessage(val message: LabsMessage) : LabsEvent
}
