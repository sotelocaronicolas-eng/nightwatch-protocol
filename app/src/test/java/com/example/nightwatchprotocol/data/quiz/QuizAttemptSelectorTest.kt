package com.example.nightwatchprotocol.data.quiz

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class QuizAttemptSelectorTest {
    private val bank = QuizCategory.entries.flatMap { category ->
        (1..5).map { index ->
            QuizQuestion(
                id = "${category.name}-$index",
                category = category,
                difficulty = 1,
                text = "Q $index",
                options = listOf("A", "B", "C", "D"),
                correctIndex = 0,
            )
        }
    }

    @Test
    fun selectQuizAttempt_alwaysReturnsElevenQuestions() {
        repeat(50) { seed ->
            val attempt = selectQuizAttempt(bank, Random(seed))
            assertEquals(11, attempt.size)
        }
    }

    @Test
    fun selectQuizAttempt_exactlyOneCategoryHasTwoQuestions_restHaveThree() {
        repeat(50) { seed ->
            val attempt = selectQuizAttempt(bank, Random(seed))
            val counts = attempt.groupingBy { it.category }.eachCount()
            assertEquals(QuizCategory.entries.size, counts.size)
            assertEquals(listOf(2, 3, 3, 3), counts.values.sorted())
        }
    }

    @Test
    fun selectQuizAttempt_neverRepeatsAQuestionWithinOneAttempt() {
        val attempt = selectQuizAttempt(bank, Random(42))
        assertEquals(attempt.size, attempt.map { it.id }.toSet().size)
    }

    @Test
    fun selectQuizAttempt_shufflesOptions_butCorrectIndexStillPointsAtTheCorrectAnswerText() {
        val originalById = bank.associateBy { it.id }

        repeat(50) { seed ->
            val attempt = selectQuizAttempt(bank, Random(seed))
            attempt.forEach { question ->
                val original = originalById.getValue(question.id)
                val originalCorrectText = original.options[original.correctIndex]

                // The remapped correctIndex must still point at the original correct answer's text.
                assertEquals(originalCorrectText, question.options[question.correctIndex])

                // The shuffle must be a permutation: same size, same multiset of option strings.
                assertEquals(original.options.size, question.options.size)
                assertEquals(original.options.sorted(), question.options.sorted())
            }
        }
    }
}
