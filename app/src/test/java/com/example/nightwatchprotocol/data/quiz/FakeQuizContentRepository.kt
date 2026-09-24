package com.example.nightwatchprotocol.data.quiz

class FakeQuizContentRepository(
    private val attempt: List<QuizQuestion> = sampleAttempt(),
) : QuizContentRepository {
    var getAttemptCallCount = 0

    override suspend fun getAttempt(): List<QuizQuestion> {
        getAttemptCallCount++
        return attempt
    }
}

/**
 * 11 questions matching the real 3/3/3/2-per-category shape, every option list
 * ["A","B","C","D"] with correctIndex = 0, so tests can drive a deterministic
 * all-correct or all-wrong run by always selecting index 0 or index 1.
 */
fun sampleAttempt(): List<QuizQuestion> = QuizCategory.entries.flatMapIndexed { categoryIndex, category ->
    val countForCategory = if (categoryIndex == 0) 2 else 3
    (1..countForCategory).map { index ->
        QuizQuestion(
            id = "${category.name}-$index",
            category = category,
            difficulty = 1,
            text = "Pregunta de prueba $index de ${category.name}",
            options = listOf("A", "B", "C", "D"),
            correctIndex = 0,
        )
    }
}
