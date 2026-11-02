package com.example.nightwatchprotocol.data.language

/** Test double for [LanguageRepository]: the language is held in memory (null = none fixed yet). */
class FakeLanguageRepository(private var language: AppLanguage? = null) : LanguageRepository {
    override fun currentLanguage(): AppLanguage? = language

    override fun setLanguage(language: AppLanguage) {
        this.language = language
    }
}
