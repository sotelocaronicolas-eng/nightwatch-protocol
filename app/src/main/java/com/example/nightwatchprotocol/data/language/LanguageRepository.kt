package com.example.nightwatchprotocol.data.language

/** The two languages of the app. Spanish is the base language (`res/values`); English arrives with `res/values-en` (Stage 8). */
enum class AppLanguage(val tag: String) {
    ES("es"),
    EN("en"),
    ;

    companion object {
        val DEFAULT: AppLanguage = ES

        /** The language for a BCP 47 [tag] such as "es", "en" or "en-US"; null when the app does not offer it. */
        fun fromTag(tag: String?): AppLanguage? =
            entries.firstOrNull { it.tag.equals(tag?.substringBefore('-'), ignoreCase = true) }
    }
}

/**
 * The language the app is shown in. The real implementation (Stage 7 of Module 5) wraps
 * `AppCompatDelegate.getApplicationLocales()` and `setApplicationLocales()`, so the language lives in AppCompat and not in
 * DataStore (one source of truth). Until then there is only the test double; nothing in the app uses this interface yet.
 */
interface LanguageRepository {
    /** The language chosen for the app, or null when none was fixed yet (a fresh install). */
    fun currentLanguage(): AppLanguage?

    fun setLanguage(language: AppLanguage)
}
