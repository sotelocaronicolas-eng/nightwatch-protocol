package com.example.nightwatchprotocol.ui.exam

/**
 * One question as the screen draws it. Nothing here says which option is right: during the exam the app never tells
 * whether an answer is correct (decision P8), and the correction is done afterwards with `gradeExam`.
 */
data class ExamQuestionUi(
    /** 0-based position, also the id of the question for a tap (a late second tap carries the old one and is ignored). */
    val index: Int,
    val total: Int,
    val emoji: String,
    val topicLabel: String,
    val text: String,
    /** "A) 123456", "B) ...": the four options with their letter, as Figma draws them. */
    val options: List<String>,
) {
    val number: Int get() = index + 1
}

sealed interface ExamStatus {
    data object Loading : ExamStatus

    data class Question(val question: ExamQuestionUi) : ExamStatus

    /** All the questions are answered: the result opens. */
    data object Finished : ExamStatus

    /** The exam content could not be read (it is a bundled file, so only a corrupt install gets here). */
    data object Failed : ExamStatus
}

data class ExamUiState(
    val status: ExamStatus = ExamStatus.Loading,
)

sealed interface ExamEvent {
    /** The "X": leaves without saving and without a dialog (SUPUESTO). An abandoned exam is not registered. */
    data object Close : ExamEvent

    /** The sixth answer was given: the result, with one option index (0 to 3) per question, in order. */
    data class Finished(val answers: List<Int>) : ExamEvent
}
