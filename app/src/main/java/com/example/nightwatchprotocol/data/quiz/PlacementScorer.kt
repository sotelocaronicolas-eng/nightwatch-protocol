package com.example.nightwatchprotocol.data.quiz

import kotlin.math.roundToInt

private const val WEAK_CATEGORY_THRESHOLD_PERCENT = 60
private const val BAJO_MAX_PERCENT = 40
private const val MEDIO_MAX_PERCENT = 75

data class PlacementScore(
    val level: PlacementLevel,
    val overallPercentage: Int,
    val categoryPercentages: Map<QuizCategory, Int>,
    val weakCategories: Set<QuizCategory>,
)

fun scoreAttempt(questions: List<QuizQuestion>, answers: Map<String, Int>): PlacementScore {
    val categoryPercentages = questions.groupBy { it.category }
        .mapValues { (_, categoryQuestions) -> percentageFor(categoryQuestions, answers) }
    val overallPercentage = percentageFor(questions, answers)
    val weakCategories = categoryPercentages.filterValues { it < WEAK_CATEGORY_THRESHOLD_PERCENT }.keys

    val level = when {
        overallPercentage <= BAJO_MAX_PERCENT -> PlacementLevel.BAJO
        overallPercentage <= MEDIO_MAX_PERCENT -> PlacementLevel.MEDIO
        else -> PlacementLevel.ALTO
    }

    return PlacementScore(level, overallPercentage, categoryPercentages, weakCategories)
}

private fun percentageFor(questions: List<QuizQuestion>, answers: Map<String, Int>): Int {
    if (questions.isEmpty()) return 0
    val possiblePoints = questions.sumOf { it.difficulty }
    if (possiblePoints == 0) return 0
    val earnedPoints = questions.sumOf { question ->
        if (answers[question.id] == question.correctIndex) question.difficulty else 0
    }
    return (earnedPoints * 100.0 / possiblePoints).roundToInt()
}
