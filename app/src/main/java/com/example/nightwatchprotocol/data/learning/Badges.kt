package com.example.nightwatchprotocol.data.learning

/**
 * What the badges look at. Everything comes from saved history, so a condition that is met once stays met: lessons and labs
 * are never un-saved, an exam attempt is never deleted and the best streak never goes down.
 */
data class BadgeFacts(
    val viewedLessons: Set<LearningTopic>,
    val completedLabs: Set<LearningTopic>,
    val examPassed: Boolean,
    val examPerfect: Boolean,
    val bestStreak: Int,
) {
    fun islandCompleted(topic: LearningTopic): Boolean = topic in viewedLessons && topic in completedLabs
}

/** How one badge is earned. To change a badge, change its line in [BadgeCatalog]. */
sealed interface BadgeCondition {
    fun isMet(facts: BadgeFacts): Boolean

    /** At least [count] lessons seen ("Primeros pasos": 1). */
    data class LessonsViewedAtLeast(val count: Int) : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = facts.viewedLessons.size >= count
    }

    /** At least [count] labs completed (6 = all of them). */
    data class LabsCompletedAtLeast(val count: Int) : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = facts.completedLabs.size >= count
    }

    /** At least one attempt with `passed = true`. */
    data object ExamPassed : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = facts.examPassed
    }

    /** At least one attempt with `correct_count = 6`. */
    data object ExamPerfect : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = facts.examPerfect
    }

    /** The island of [topic]: its lesson seen AND its lab completed. */
    data class IslandCompleted(val topic: LearningTopic) : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = facts.islandCompleted(topic)
    }

    /** The BEST streak of all the history is at least [days] (so a lost day does not lock the badge again). */
    data class BestStreakAtLeast(val days: Int) : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = facts.bestStreak >= days
    }

    /** Depends on social features (friends, groups, ranking): never met until module 4. */
    data object AvailableFromModule4 : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = false
    }

    /** The six islands completed: lesson seen AND lab completed in every topic. */
    data object AllIslandsCompleted : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = LearningTopic.entries.all { facts.islandCompleted(it) }
    }

    /** Every condition of [conditions] is met (each one is monotonic, so the whole is too). */
    data class AllOf(val conditions: List<BadgeCondition>) : BadgeCondition {
        override fun isMet(facts: BadgeFacts) = conditions.all { it.isMet(facts) }
    }
}

/**
 * One badge of the "Logros e Insignias" screen. [emoji] is the one shown once unlocked (a locked badge shows 🔒, as in
 * Figma); [source] says where the badge comes from and [note] explains a decision that is not obvious (every badge that is
 * blocked on purpose has one).
 */
data class BadgeDefinition(
    val emoji: String,
    val name: String,
    val condition: BadgeCondition,
    val source: String,
    val note: String? = null,
)

/**
 * THE catalog: the nine badges of Figma "LOGROS E INSIGNIAS" (2287:100) in the order of the screen, each with its
 * condition. Figma gives only the names (no descriptions), so the condition of each one is chosen from its name with the
 * rules of the group's prompt for stage 5.5 (see the spec section "Insignias y racha reales").
 */
val BadgeCatalog: List<BadgeDefinition> = listOf(
    BadgeDefinition("🥇", "Primeros pasos", BadgeCondition.LessonsViewedAtLeast(1), "Figma 2287:108", "Regla 1: al menos 1 lección vista"),
    BadgeDefinition("🔥", "Racha de 7 días", BadgeCondition.BestStreakAtLeast(7), "Figma 2287:112", "Regla 6: mejor racha de 7 días (N sale del nombre)"),
    BadgeDefinition("🔑", "Maestro de Contraseñas", BadgeCondition.IslandCompleted(LearningTopic.CONTRASENAS), "Figma 2287:116", "Regla 5: isla de Contraseñas completada"),
    BadgeDefinition("🎣", "Cazador de Phishing", BadgeCondition.IslandCompleted(LearningTopic.PHISHING), "Figma 2287:120", "Regla 5: isla de Phishing completada. Emoji desbloqueado: SUPUESTO"),
    BadgeDefinition("🌐", "Guardián de Redes", BadgeCondition.IslandCompleted(LearningTopic.REDES_SEGURAS), "Figma 2287:124", "Regla 5: isla de Redes Seguras completada. Emoji desbloqueado: SUPUESTO"),
    BadgeDefinition(
        "🌟",
        "Leyenda NightWatch",
        BadgeCondition.AllOf(listOf(BadgeCondition.AllIslandsCompleted, BadgeCondition.ExamPerfect)),
        "Figma 2287:128",
        "SUPUESTO elegido por el grupo: las 6 islas completadas Y al menos un intento de examen con 6 aciertos. Emoji desbloqueado: SUPUESTO",
    ),
    BadgeDefinition("📞", "Detective de Ingeniería Social", BadgeCondition.IslandCompleted(LearningTopic.INGENIERIA_SOCIAL), "Figma 2540:255", "Regla 5: isla de Ingeniería Social completada. Emoji desbloqueado: SUPUESTO"),
    BadgeDefinition("🔥", "Racha de 30 días", BadgeCondition.BestStreakAtLeast(30), "Figma 2540:259", "Regla 6: mejor racha de 30 días (N sale del nombre)"),
    BadgeDefinition("🎓", "Graduado NightWatch", BadgeCondition.ExamPassed, "Figma 2540:263", "Regla 4: al menos un intento de examen aprobado. Emoji desbloqueado: SUPUESTO"),
)

/** A badge with its state. [unlocked] is derived every time from the history: nothing is stored. */
data class BadgeState(val definition: BadgeDefinition, val unlocked: Boolean)

/** The state of the nine badges, in the order of [BadgeCatalog]. */
fun evaluateBadges(facts: BadgeFacts, catalog: List<BadgeDefinition> = BadgeCatalog): List<BadgeState> =
    catalog.map { BadgeState(it, it.condition.isMet(facts)) }
