package com.example.nightwatchprotocol.data.quiz

import kotlin.random.Random

private const val QUESTIONS_PER_ATTEMPT = 11
private const val GUARANTEED_PER_CATEGORY = 3

/**
 * Draws one placement-quiz attempt from the full question bank: 3 questions from
 * three of the four categories and 2 from the remaining one (chosen at random each
 * time), for 11 total, then shuffles the combined order.
 */
fun selectQuizAttempt(bank: List<QuizQuestion>, random: Random = Random.Default): List<QuizQuestion> {
    val byCategory = QuizCategory.entries.associateWith { category -> bank.filter { it.category == category } }
    val reducedCategory = QuizCategory.entries.toList().random(random)

    val selected = mutableListOf<QuizQuestion>()
    for (category in QuizCategory.entries) {
        val count = if (category == reducedCategory) GUARANTEED_PER_CATEGORY - 1 else GUARANTEED_PER_CATEGORY
        selected += byCategory.getValue(category).shuffled(random).take(count)
    }
    check(selected.size == QUESTIONS_PER_ATTEMPT)
    return selected.shuffled(random)
}
