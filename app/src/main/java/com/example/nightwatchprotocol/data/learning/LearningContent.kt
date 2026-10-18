package com.example.nightwatchprotocol.data.learning

import kotlinx.serialization.Serializable

/**
 * The six topics, in the Figma order (lessons "MÓDULO 1 a 6 de 6", map islands, exam questions). Each topic has one island,
 * one lesson, one lab and one exam question. Stored with `.name`, like `QuizCategory`; `lesson_progress.topic` and
 * `lab_progress.lab` (migration 0007) accept exactly these names.
 */
@Serializable
enum class LearningTopic {
    CONTRASENAS,
    PHISHING,
    MALWARE,
    INGENIERIA_SOCIAL,
    REDES_SEGURAS,
    FIREWALL;

    companion object {
        /** Null for a name this version does not know (the database only stores the six above). */
        fun fromName(name: String): LearningTopic? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One card of a lesson ("¿Qué es?", "¿Por qué importa?", "¿Cómo protegerte?"). [emoji] and [title] come apart because
 * Figma writes them in one text ("🧭 ¿Qué es?").
 */
@Serializable
data class LessonCard(val emoji: String, val title: String, val text: String)

/** A lesson and its island. Source: `learning_lessons.json` (Figma nodes in each element's `source`). */
@Serializable
data class Lesson(
    val topic: LearningTopic,
    val title: String,
    val islandName: String,
    val emoji: String,
    val cards: List<LessonCard>,
    val source: String,
)

/** The difficulty tag on a lab card (Figma: Fácil, Medio, Difícil). */
@Serializable
enum class LabDifficulty { FACIL, MEDIO, DIFICIL }

/**
 * One objective of a lab: what the person has to do ([title]), the command that fulfils it ([command]) and what the
 * simulated terminal prints for it ([output]; lines are separated by `\n`). [estimatedTime] is only for the passwords
 * lab: the time that appears in that command's output ("menos de 1 segundo", "4 horas", "300 años").
 */
@Serializable
data class LabObjective(
    val title: String,
    val command: String,
    val output: String,
    val reportLabel: String,
    val estimatedTime: String? = null,
)

/** The texts of the lab report that depend on the lab (Figma: LAB - INFORME ...). The failure title is common and not here. */
@Serializable
data class LabReportTexts(
    val successTitle: String,
    val successSummary: String,
    val failureSummary: String,
    val metricLabel: String,
)

/** A lab and everything its terminal and its report need. Source: `learning_labs.json`. */
@Serializable
data class Lab(
    val topic: LearningTopic,
    val cardTitle: String,
    val terminalTitle: String,
    val emoji: String,
    val difficulty: LabDifficulty,
    val briefing: String,
    val objectives: List<LabObjective>,
    val report: LabReportTexts,
    val source: String,
)

/**
 * A question of the final exam. [options] have no "A) " prefix (the letter comes from the position, see
 * [optionLabel]). Source: `learning_exam.json`.
 */
@Serializable
data class ExamQuestion(
    val topic: LearningTopic,
    val topicLabel: String,
    val emoji: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val source: String,
) {
    /** "C) k7#mQ!9zRt$2": the option as Figma shows it in the review of a single error. */
    fun optionLabel(index: Int): String = "${'A' + index}) ${options[index]}"
}

/** All the bundled learning content. */
data class LearningContent(
    val lessons: List<Lesson>,
    val labs: List<Lab>,
    val exam: List<ExamQuestion>,
) {
    fun lesson(topic: LearningTopic): Lesson = lessons.first { it.topic == topic }
    fun lab(topic: LearningTopic): Lab = labs.first { it.topic == topic }
}
