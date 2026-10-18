package com.example.nightwatchprotocol.data.learning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The bundled lessons, labs and exam. Nothing here is editable from a server: changing it needs a new build. */
interface LearningContentRepository {
    suspend fun getContent(): LearningContent
}

const val LESSONS_ASSET = "learning_lessons.json"
const val LABS_ASSET = "learning_labs.json"
const val EXAM_ASSET = "learning_exam.json"

private val contentJson = Json { ignoreUnknownKeys = true }

/**
 * Reads the three JSON assets once and keeps them in memory. [readAsset] is the only thing that touches Android (the
 * container gives it `context.assets`), so the parsing is tested on the JVM with the real files.
 *
 * Lessons and labs are returned in the [LearningTopic] order; the exam keeps the order of its file (questions 1 to 6).
 */
class LocalLearningContentRepository(
    private val readAsset: (name: String) -> String,
) : LearningContentRepository {
    private var cached: LearningContent? = null

    override suspend fun getContent(): LearningContent = withContext(Dispatchers.IO) {
        cached ?: load().also { cached = it }
    }

    private fun load(): LearningContent = LearningContent(
        lessons = contentJson.decodeFromString<List<Lesson>>(readAsset(LESSONS_ASSET)).sortedBy { it.topic.ordinal },
        labs = contentJson.decodeFromString<List<Lab>>(readAsset(LABS_ASSET)).sortedBy { it.topic.ordinal },
        exam = contentJson.decodeFromString(readAsset(EXAM_ASSET)),
    )
}
