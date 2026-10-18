package com.example.nightwatchprotocol.ui.lesson

import com.example.nightwatchprotocol.data.learning.Lesson
import com.example.nightwatchprotocol.data.learning.LearningTopic

sealed interface LessonStatus {
    data object Loading : LessonStatus

    data class Ready(val lesson: Lesson) : LessonStatus

    /** The lesson content could not be read (it is a bundled file, so only a corrupt install gets here). */
    data object Failed : LessonStatus
}

data class LessonUiState(
    /** "MÓDULO [moduleNumber] de [moduleTotal]": the order of the topic (Figma, E1-8). */
    val moduleNumber: Int,
    val moduleTotal: Int,
    val status: LessonStatus = LessonStatus.Loading,
)

sealed interface LessonEvent {
    /** The "X": back to where the person was. */
    data object Close : LessonEvent

    /** "Ir al laboratorio →": the lab of this lesson's topic. */
    data class OpenLab(val topic: LearningTopic) : LessonEvent

    /** Saving that the lesson was seen failed. The lesson stays readable; the screen shows a short notice. */
    data class SaveFailed(val offline: Boolean) : LessonEvent
}
