package com.example.nightwatchprotocol.data.learning

import com.example.nightwatchprotocol.data.content.loadLocalizedContent
import com.example.nightwatchprotocol.data.language.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The bundled lessons, labs and exam. Nothing here is editable from a server: changing it needs a new build. */
interface LearningContentRepository {
    suspend fun getContent(): LearningContent
}

/** File names; each language keeps its own copy under `assets/content/<tag>/` (see `data/content`). */
const val LESSONS_ASSET = "learning_lessons.json"
const val LABS_ASSET = "learning_labs.json"
const val EXAM_ASSET = "learning_exam.json"

private val contentJson = Json { ignoreUnknownKeys = true }

/**
 * Reads the three JSON assets of the active language and keeps them in memory (one copy per language, because the language can
 * change while the app lives). [readAsset] is the only thing that touches Android (the container gives it `context.assets`), so the
 * parsing is tested on the JVM with the real files. A file missing in the active language falls back to Spanish.
 *
 * Lessons and labs are returned in the [LearningTopic] order; the exam keeps the order of its file (questions 1 to 6).
 */
class LocalLearningContentRepository(
    private val language: () -> AppLanguage? = { null },
    private val readAsset: (path: String) -> String,
) : LearningContentRepository {
    private val cache = mutableMapOf<AppLanguage, LearningContent>()

    override suspend fun getContent(): LearningContent = withContext(Dispatchers.IO) {
        val active = language() ?: AppLanguage.DEFAULT
        synchronized(cache) { cache[active] } ?: load(active).also { synchronized(cache) { cache[active] = it } }
    }

    private fun load(active: AppLanguage): LearningContent = LearningContent(
        lessons = loadLocalizedContent(active, LESSONS_ASSET, readAsset) { contentJson.decodeFromString<List<Lesson>>(it) }
            .sortedBy { it.topic.ordinal },
        labs = loadLocalizedContent(active, LABS_ASSET, readAsset) { contentJson.decodeFromString<List<Lab>>(it) }
            .sortedBy { it.topic.ordinal },
        exam = loadLocalizedContent(active, EXAM_ASSET, readAsset) { contentJson.decodeFromString<List<ExamQuestion>>(it) },
    )
}
