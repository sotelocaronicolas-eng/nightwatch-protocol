package com.example.nightwatchprotocol.data.learning

/** Questions in the final exam: one per topic (Figma: "Responde las 6 preguntas, una por cada tema"). */
const val EXAM_QUESTION_COUNT = 6

/** Wrong answers allowed to pass (Figma: "Puedes fallar como máximo 1 para aprobar"). Passing is 5 or 6 correct. */
const val EXAM_MAX_ERRORS = 1

/**
 * What the result screen shows for a single error (Figma 2498:512 to 2498:572): "Tema: ..." and "Correcta: C) ...".
 * It exists only when exactly one answer was wrong.
 */
data class ExamSingleError(
    val topic: LearningTopic,
    val topicLabel: String,
    val correctOptionLabel: String,
)

/**
 * The result of an exam. With two or more errors Figma does not say which ones failed ("no te diremos cuáles fueron"), so
 * this type carries nothing about them: [singleError] is null unless there was exactly one error. Only [correctCount]
 * is stored in `exam_attempts`.
 */
data class ExamGrade(
    val correctCount: Int,
    val total: Int,
    val passed: Boolean,
    val singleError: ExamSingleError?,
)

/**
 * Grades the exam. [selectedOptions] has one option index (0 to 3) per question, in the order of [questions].
 * Passing means at most [EXAM_MAX_ERRORS] wrong answers (with 6 questions: `correctCount >= 5`, as `exam_attempts.passed`).
 */
fun gradeExam(questions: List<ExamQuestion>, selectedOptions: List<Int>): ExamGrade {
    require(selectedOptions.size == questions.size) {
        "Se esperaban ${questions.size} respuestas y llegaron ${selectedOptions.size}"
    }
    require(selectedOptions.indices.all { selectedOptions[it] in questions[it].options.indices }) {
        "Una respuesta no corresponde a ninguna opción"
    }

    val wrong = questions.indices.filter { selectedOptions[it] != questions[it].correctIndex }
    val correctCount = questions.size - wrong.size
    val singleError = wrong.singleOrNull()?.let { index ->
        val question = questions[index]
        ExamSingleError(question.topic, question.topicLabel, question.optionLabel(question.correctIndex))
    }
    return ExamGrade(
        correctCount = correctCount,
        total = questions.size,
        passed = wrong.size <= EXAM_MAX_ERRORS,
        singleError = singleError,
    )
}
