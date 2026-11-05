package com.example.nightwatchprotocol.data.learning

import com.example.nightwatchprotocol.data.language.AppLanguage
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Reads the real bundled JSON files from the source tree, so the tests check the content that ships in the APK. Gradle runs
 * unit tests from the module folder (`app`); the second path covers a run from the repository root.
 */
object LearningAssets {
    /** [path] is relative to `assets/` (for example `content/en/learning_exam.json`); a bare file name means the Spanish copy. */
    fun read(path: String): String {
        val relative = if ('/' in path) path else "content/es/$path"
        val candidates = listOf(File("src/main/assets/$relative"), File("app/src/main/assets/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("No se encontró el asset $relative (probado: ${candidates.joinToString { it.path }})")
        return file.readText(Charsets.UTF_8)
    }

    fun exists(path: String): Boolean = runCatching { read(path) }.isSuccess
}

/** The real content (Spanish unless [language] says otherwise), parsed with the real repository. */
fun realLearningContent(language: AppLanguage? = null): LearningContent =
    runBlocking { LocalLearningContentRepository(language = { language }, readAsset = LearningAssets::read).getContent() }

/** A small valid content (six topics, three objectives, six questions) for tests that do not care about the real texts. */
fun sampleLearningContent(): LearningContent = LearningContent(
    lessons = LearningTopic.entries.map { topic ->
        Lesson(
            topic = topic,
            title = "Lección ${topic.name}",
            islandName = "Isla ${topic.name}",
            emoji = "📘",
            cards = listOf(LessonCard("🧭", "¿Qué es?", "texto"), LessonCard("⚠️", "¿Por qué importa?", "texto"), LessonCard("🔍", "Tipos y técnicas", "texto"), LessonCard("🛡️", "¿Cómo protegerte?", "texto")),
            source = "prueba",
        )
    },
    labs = LearningTopic.entries.map { topic ->
        Lab(
            topic = topic,
            cardTitle = "Lab ${topic.name}",
            terminalTitle = "Terminal ${topic.name}",
            emoji = "🧪",
            difficulty = LabDifficulty.FACIL,
            briefing = "briefing",
            objectives = (1..3).map { LabObjective("Objetivo $it", "cmd$it ${topic.name}", "salida $it", "Informe $it") },
            report = LabReportTexts("éxito", "resumen éxito", "resumen fallo", "Precisión"),
            source = "prueba",
        )
    },
    exam = LearningTopic.entries.map { topic ->
        ExamQuestion(topic, "Tema ${topic.name}", "❓", "Pregunta ${topic.name}", listOf("uno", "dos", "tres", "cuatro"), 1, "prueba")
    },
)
