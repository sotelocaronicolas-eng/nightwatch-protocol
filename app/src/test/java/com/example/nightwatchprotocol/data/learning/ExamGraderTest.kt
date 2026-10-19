package com.example.nightwatchprotocol.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExamGraderTest {
    private val questions = realLearningContent().exam
    private val allCorrect = questions.map { it.correctIndex }

    /** A wrong answer for [question]: any option that is not the right one. */
    private fun wrongFor(index: Int): Int = (questions[index].correctIndex + 1) % 4

    private fun answersWithErrorsAt(vararg wrong: Int): List<Int> =
        allCorrect.mapIndexed { index, correct -> if (index in wrong) wrongFor(index) else correct }

    @Test
    fun zeroErrors_sixCorrect_passes_withNothingToReview() {
        val grade = gradeExam(questions, allCorrect)

        assertEquals(6, grade.correctCount)
        assertEquals(6, grade.total)
        assertTrue(grade.passed)
        assertNull(grade.singleError)
    }

    @Test
    fun oneError_fiveCorrect_passes_andShowsTheTopicAndTheRightAnswerOfFigma() {
        // The "Tema" and "Correcta" of Figma 2498:512 to 2498:572, one frame per question.
        val expected = listOf(
            ExamSingleError(LearningTopic.CONTRASENAS, "Contraseñas y Autenticación", "C) k7#mQ!9zRt$2"),
            ExamSingleError(LearningTopic.PHISHING, "Phishing", "B) Un enlace que no coincide con el dominio oficial"),
            ExamSingleError(LearningTopic.MALWARE, "Malware", "C) Analizarlo con un antivirus antes de abrirlo"),
            ExamSingleError(LearningTopic.INGENIERIA_SOCIAL, "Ingeniería Social", "B) Cuelgas y verificas por un canal oficial"),
            ExamSingleError(LearningTopic.REDES_SEGURAS, "Redes Seguras", "B) Usar una VPN para cifrar tu tráfico"),
            ExamSingleError(LearningTopic.FIREWALL, "Firewall", "B) Filtrar el tráfico de red no autorizado"),
        )

        questions.indices.forEach { index ->
            val grade = gradeExam(questions, answersWithErrorsAt(index))

            assertEquals("pregunta ${index + 1}", 5, grade.correctCount)
            assertTrue("pregunta ${index + 1}", grade.passed)
            assertEquals("pregunta ${index + 1}", expected[index], grade.singleError)
        }
    }

    @Test
    fun twoErrors_fourCorrect_failsAndRevealsNothing() {
        val grade = gradeExam(questions, answersWithErrorsAt(0, 3))

        assertEquals(4, grade.correctCount)
        assertFalse(grade.passed)
        assertNull(grade.singleError)
    }

    @Test
    fun everyPairOfErrors_failsAndRevealsNothing() {
        for (a in questions.indices) {
            for (b in a + 1 until questions.size) {
                val grade = gradeExam(questions, answersWithErrorsAt(a, b))

                assertFalse("$a y $b", grade.passed)
                assertNull("$a y $b", grade.singleError)
            }
        }
    }

    @Test
    fun sixErrors_zeroCorrect_fails() {
        val grade = gradeExam(questions, questions.indices.map { wrongFor(it) })

        assertEquals(0, grade.correctCount)
        assertFalse(grade.passed)
        assertNull(grade.singleError)
    }

    @Test
    fun theBorder_isFiveCorrect_passes_andFourCorrect_fails() {
        assertEquals(5, gradeExam(questions, answersWithErrorsAt(2)).correctCount)
        assertTrue(gradeExam(questions, answersWithErrorsAt(2)).passed)
        assertEquals(4, gradeExam(questions, answersWithErrorsAt(2, 4)).correctCount)
        assertFalse(gradeExam(questions, answersWithErrorsAt(2, 4)).passed)
        // The same rule as exam_attempts.passed (correct_count >= 5) in migration 0007.
        assertEquals(EXAM_QUESTION_COUNT - EXAM_MAX_ERRORS, 5)
    }

    @Test
    fun theGradeCarriesNoWayToKnowWhichQuestionsFailed_withTwoOrMoreErrors() {
        // Same count, different wrong questions: the grades are identical, so nothing can leak.
        val first = gradeExam(questions, answersWithErrorsAt(0, 1))
        val second = gradeExam(questions, answersWithErrorsAt(4, 5))

        assertEquals(first, second)
    }

    @Test
    fun theAnswersMustMatchTheQuestions() {
        try {
            gradeExam(questions, allCorrect.dropLast(1))
            fail("debía lanzar")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            gradeExam(questions, allCorrect.take(5) + 4)
            fail("debía lanzar")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            gradeExam(questions, allCorrect.take(5) + -1)
            fail("debía lanzar")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
