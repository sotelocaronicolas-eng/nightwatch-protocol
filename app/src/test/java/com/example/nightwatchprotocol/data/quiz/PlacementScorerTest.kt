package com.example.nightwatchprotocol.data.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementScorerTest {
    private fun question(id: String, category: QuizCategory, difficulty: Int = 1, correctIndex: Int = 0) =
        QuizQuestion(id, category, difficulty, "text", listOf("a", "b", "c", "d"), correctIndex)

    @Test
    fun allCorrect_scoresAlto_withNoWeakCategories() {
        val questions = listOf(
            question("1", QuizCategory.CONCEPTOS_BASICOS),
            question("2", QuizCategory.CONTRASENAS),
        )
        val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 0))

        assertEquals(PlacementLevel.ALTO, score.level)
        assertEquals(100, score.overallPercentage)
        assertTrue(score.weakCategories.isEmpty())
    }

    @Test
    fun allWrong_scoresBajo_withEveryCategoryWeak() {
        val questions = listOf(
            question("1", QuizCategory.CONCEPTOS_BASICOS),
            question("2", QuizCategory.CONTRASENAS),
        )
        val score = scoreAttempt(questions, mapOf("1" to 1, "2" to 1))

        assertEquals(PlacementLevel.BAJO, score.level)
        assertEquals(0, score.overallPercentage)
        assertEquals(setOf(QuizCategory.CONCEPTOS_BASICOS, QuizCategory.CONTRASENAS), score.weakCategories)
    }

    @Test
    fun exactlyFortyPercent_isBajo() {
        val questions = listOf(
            question("1", QuizCategory.REDES_MALWARE, difficulty = 40),
            question("2", QuizCategory.REDES_MALWARE, difficulty = 60),
        )
        val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

        assertEquals(40, score.overallPercentage)
        assertEquals(PlacementLevel.BAJO, score.level)
    }

    @Test
    fun exactlyFortyOnePercent_isMedio() {
        val questions = listOf(
            question("1", QuizCategory.REDES_MALWARE, difficulty = 41),
            question("2", QuizCategory.REDES_MALWARE, difficulty = 59),
        )
        val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

        assertEquals(41, score.overallPercentage)
        assertEquals(PlacementLevel.MEDIO, score.level)
    }

    @Test
    fun exactlySeventyFivePercent_isMedio() {
        val questions = listOf(
            question("1", QuizCategory.REDES_MALWARE, difficulty = 75),
            question("2", QuizCategory.REDES_MALWARE, difficulty = 25),
        )
        val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

        assertEquals(PlacementLevel.MEDIO, score.level)
    }

    @Test
    fun exactlySeventySixPercent_isAlto() {
        val questions = listOf(
            question("1", QuizCategory.REDES_MALWARE, difficulty = 76),
            question("2", QuizCategory.REDES_MALWARE, difficulty = 24),
        )
        val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 1))

        assertEquals(PlacementLevel.ALTO, score.level)
    }

    @Test
    fun categoryWithFewerQuestions_isScoredByPercentageNotRawCount() {
        val questions = listOf(
            question("1", QuizCategory.INGENIERIA_SOCIAL),
            question("2", QuizCategory.INGENIERIA_SOCIAL),
            question("3", QuizCategory.REDES_MALWARE),
        )
        val score = scoreAttempt(questions, mapOf("1" to 0, "2" to 0, "3" to 0))

        assertEquals(100, score.categoryPercentages.getValue(QuizCategory.INGENIERIA_SOCIAL))
        assertEquals(100, score.categoryPercentages.getValue(QuizCategory.REDES_MALWARE))
    }

    @Test
    fun unansweredQuestion_countsAsIncorrect() {
        val questions = listOf(question("1", QuizCategory.CONTRASENAS))
        val score = scoreAttempt(questions, emptyMap())

        assertEquals(0, score.overallPercentage)
    }
}
