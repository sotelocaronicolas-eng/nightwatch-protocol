package com.example.nightwatchprotocol.data.quiz

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.UiText

private val topicsByLevelAndCategory: Map<PlacementLevel, Map<QuizCategory, Int>> = mapOf(
    PlacementLevel.BAJO to mapOf(
        QuizCategory.CONCEPTOS_BASICOS to R.string.plan_low_basics,
        QuizCategory.CONTRASENAS to R.string.plan_low_passwords,
        QuizCategory.INGENIERIA_SOCIAL to R.string.plan_low_social,
        QuizCategory.REDES_MALWARE to R.string.plan_low_networks,
    ),
    PlacementLevel.MEDIO to mapOf(
        QuizCategory.CONCEPTOS_BASICOS to R.string.plan_medium_basics,
        QuizCategory.CONTRASENAS to R.string.plan_medium_passwords,
        QuizCategory.INGENIERIA_SOCIAL to R.string.plan_medium_social,
        QuizCategory.REDES_MALWARE to R.string.plan_medium_networks,
    ),
    PlacementLevel.ALTO to mapOf(
        QuizCategory.CONCEPTOS_BASICOS to R.string.plan_high_basics,
        QuizCategory.CONTRASENAS to R.string.plan_high_passwords,
        QuizCategory.INGENIERIA_SOCIAL to R.string.plan_high_social,
        QuizCategory.REDES_MALWARE to R.string.plan_high_networks,
    ),
)

/**
 * One topic per category for the given level, with categories the user scored
 * weak in promoted to the front (stable within each group, so ties keep
 * QuizCategory's declaration order). The topics are resource texts, resolved when shown.
 */
fun buildStudyPlan(level: PlacementLevel, weakCategories: Set<QuizCategory>): List<UiText> {
    val topicsForLevel = topicsByLevelAndCategory.getValue(level)
    val orderedCategories = QuizCategory.entries.sortedByDescending { it in weakCategories }
    return orderedCategories.map { category -> UiText.of(topicsForLevel.getValue(category)) }
}
