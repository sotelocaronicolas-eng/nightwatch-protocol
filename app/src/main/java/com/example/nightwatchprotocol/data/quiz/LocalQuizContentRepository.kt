package com.example.nightwatchprotocol.data.quiz

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class LocalQuizContentRepository(
    private val context: Context,
) : QuizContentRepository {
    private var cachedBank: List<QuizQuestion>? = null

    override suspend fun getAttempt(): List<QuizQuestion> = withContext(Dispatchers.IO) {
        val bank = cachedBank ?: loadBank().also { cachedBank = it }
        selectQuizAttempt(bank)
    }

    private fun loadBank(): List<QuizQuestion> {
        val json = context.assets.open("quiz_questions.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString(json)
    }
}
