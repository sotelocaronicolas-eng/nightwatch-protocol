package com.example.nightwatchprotocol.data.quiz

private val topicsByLevelAndCategory: Map<PlacementLevel, Map<QuizCategory, String>> = mapOf(
    PlacementLevel.BAJO to mapOf(
        QuizCategory.CONCEPTOS_BASICOS to "Fundamentos de ciberseguridad",
        QuizCategory.CONTRASENAS to "Higiene básica de contraseñas",
        QuizCategory.INGENIERIA_SOCIAL to "Cómo reconocer un intento de phishing",
        QuizCategory.REDES_MALWARE to "Introducción a redes y malware",
    ),
    PlacementLevel.MEDIO to mapOf(
        QuizCategory.CONCEPTOS_BASICOS to "Principios de defensa en profundidad",
        QuizCategory.CONTRASENAS to "Autenticación multifactor y gestión de credenciales",
        QuizCategory.INGENIERIA_SOCIAL to "Ingeniería social dirigida (spear phishing y pretexting)",
        QuizCategory.REDES_MALWARE to "Fundamentos de redes seguras y tipos de malware",
    ),
    PlacementLevel.ALTO to mapOf(
        QuizCategory.CONCEPTOS_BASICOS to "Modelado de amenazas y gestión de vulnerabilidades",
        QuizCategory.CONTRASENAS to "Arquitecturas de autenticación y almacenamiento seguro de credenciales",
        QuizCategory.INGENIERIA_SOCIAL to "Análisis avanzado de ataques de ingeniería social",
        QuizCategory.REDES_MALWARE to "Endurecimiento de redes y respuesta a incidentes de malware",
    ),
)

/**
 * One topic per category for the given level, with categories the user scored
 * weak in promoted to the front (stable within each group, so ties keep
 * QuizCategory's declaration order).
 */
fun buildStudyPlan(level: PlacementLevel, weakCategories: Set<QuizCategory>): List<String> {
    val topicsForLevel = topicsByLevelAndCategory.getValue(level)
    val orderedCategories = QuizCategory.entries.sortedByDescending { it in weakCategories }
    return orderedCategories.map { category -> topicsForLevel.getValue(category) }
}
