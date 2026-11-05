package com.example.nightwatchprotocol.data.quiz

import android.content.Context
import com.example.nightwatchprotocol.data.content.loadLocalizedContent
import com.example.nightwatchprotocol.data.language.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** File name of the placement quiz bank; each language keeps its own copy under `assets/content/<tag>/`. */
const val QUIZ_ASSET = "quiz_questions.json"

class LocalQuizContentRepository(
    private val readAsset: (path: String) -> String,
    private val language: () -> AppLanguage? = { null },
) : QuizContentRepository {
    constructor(context: Context, language: () -> AppLanguage? = { null }) : this(
        readAsset = { path -> context.assets.open(path).bufferedReader().use { it.readText() } },
        language = language,
    )

    // One bank per language: the language can change while the app lives.
    private val cachedBanks = mutableMapOf<AppLanguage, List<QuizQuestion>>()

    override suspend fun getAttempt(): List<QuizQuestion> = withContext(Dispatchers.IO) {
        val active = language() ?: AppLanguage.DEFAULT
        val bank = synchronized(cachedBanks) { cachedBanks[active] }
            ?: loadBank(active).also { synchronized(cachedBanks) { cachedBanks[active] = it } }
        selectQuizAttempt(bank)
    }

    private fun loadBank(active: AppLanguage): List<QuizQuestion> =
        loadLocalizedContent(active, QUIZ_ASSET, readAsset) { Json.decodeFromString<List<QuizQuestion>>(it) }
}
