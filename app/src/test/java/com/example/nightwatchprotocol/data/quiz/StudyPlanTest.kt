package com.example.nightwatchprotocol.data.quiz

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanTest {
    @Test
    fun noWeakCategories_returnsOneTopicPerCategory_inDeclarationOrder() {
        val plan = buildStudyPlan(PlacementLevel.MEDIO, emptySet())

        assertEquals(QuizCategory.entries.size, plan.size)
        assertEquals(plan.distinct(), plan)
    }

    @Test
    fun weakCategories_arePromotedToTheFront() {
        val defaultPlan = buildStudyPlan(PlacementLevel.BAJO, emptySet())
        val plan = buildStudyPlan(
            PlacementLevel.BAJO,
            setOf(QuizCategory.REDES_MALWARE, QuizCategory.CONCEPTOS_BASICOS),
        )

        val expectedFirstTwo = listOf(
            defaultPlan[QuizCategory.entries.indexOf(QuizCategory.CONCEPTOS_BASICOS)],
            defaultPlan[QuizCategory.entries.indexOf(QuizCategory.REDES_MALWARE)],
        )
        assertEquals(expectedFirstTwo, plan.take(2))
    }

    @Test
    fun allCategoriesWeak_returnsSameOrderAsNoWeakCategories() {
        val allWeak = buildStudyPlan(PlacementLevel.ALTO, QuizCategory.entries.toSet())
        val noneWeak = buildStudyPlan(PlacementLevel.ALTO, emptySet())

        assertEquals(noneWeak, allWeak)
    }

    @Test
    fun everyLevel_hasATopicForEveryCategory() {
        for (level in PlacementLevel.entries) {
            val plan = buildStudyPlan(level, emptySet())
            assertEquals(QuizCategory.entries.size, plan.size)
        }
    }
}
