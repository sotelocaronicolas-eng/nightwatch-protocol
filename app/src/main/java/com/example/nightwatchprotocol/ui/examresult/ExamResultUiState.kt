package com.example.nightwatchprotocol.ui.examresult

/**
 * What the result tells. Passing is at most one error (decision P11 and Figma); only with exactly one error the screen names
 * the topic and the right option (Figma 2498:512). With two or more errors it says nothing about which ones failed
 * ("no te diremos cuáles fueron"), so [NotPassed] carries nothing.
 */
sealed interface ExamOutcome {
    /** Six right answers (Figma 2491:357). */
    data object Perfect : ExamOutcome

    /** One error (Figma 2498:512): [topicLabel] ("Contraseñas y Autenticación") and [correctOption] ("C) k7#mQ!9zRt$2"). */
    data class OneError(val topicLabel: String, val correctOption: String) : ExamOutcome

    /** Two or more errors (Figma 2491:345). */
    data object NotPassed : ExamOutcome
}

sealed interface ExamResultStatus {
    data object Loading : ExamResultStatus

    data class Ready(val outcome: ExamOutcome) : ExamResultStatus

    /** The exam content could not be read, or the answers do not match it. */
    data object Failed : ExamResultStatus
}

/** Saving the attempt: every finished attempt, passed or not, is one row of `exam_attempts` with only the right-answer count. */
sealed interface ExamSaveStatus {
    /** The result is not ready yet, so nothing has been sent. */
    data object NotStarted : ExamSaveStatus

    data object Saving : ExamSaveStatus

    data object Saved : ExamSaveStatus

    /** Only the kind of failure reaches the screen, never the exception. The result is still shown. */
    data class Failed(val offline: Boolean) : ExamSaveStatus
}

data class ExamResultUiState(
    val status: ExamResultStatus = ExamResultStatus.Loading,
    val save: ExamSaveStatus = ExamSaveStatus.NotStarted,
)

/** Where the buttons and the rows of the result lead. */
enum class ExamDestination {
    /** Home with Ruta: "ACEPTAR" and "Repasa la Ruta de Aprendizaje". */
    Path,

    /** Home with Labs: "Practica en los Laboratorios". */
    Labs,

    /** Home with Inicio: "Volver al inicio". */
    Home,

    /** A new attempt from question 1: "Reintentar" and "Vuelve a intentar el examen". */
    NewAttempt,
}

sealed interface ExamResultEvent {
    data class Go(val destination: ExamDestination) : ExamResultEvent
}
