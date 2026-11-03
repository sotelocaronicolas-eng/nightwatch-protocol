package com.example.nightwatchprotocol.data.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.nightwatchprotocol.data.preferences.AppearanceRepository
import kotlinx.coroutines.flow.first

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
 * The language the app is shown in. The real implementation ([AppCompatLanguageRepository]) wraps
 * `AppCompatDelegate.getApplicationLocales()` and `setApplicationLocales()`, so the language lives in AppCompat and NOT in
 * DataStore (one source of truth, decision A3).
 */
interface LanguageRepository {
    /** The language chosen for the app, or null when none was fixed yet (a fresh install). */
    fun currentLanguage(): AppLanguage?

    /** Chooses [language]. The running Activity is recreated by AppCompat to show it. */
    fun setLanguage(language: AppLanguage)
}

/**
 * The first tag of a list of language tags such as "en-US,es" (what `LocaleListCompat.toLanguageTags()` returns), as one of the app's
 * languages; null for an empty list or a language the app does not offer.
 */
fun languageOfTags(tags: String): AppLanguage? = AppLanguage.fromTag(tags.substringBefore(',').takeIf { it.isNotBlank() })

/**
 * [LanguageRepository] over AppCompat. Android 13+ keeps the choice in the system (it also shows in the phone's per-app language
 * settings); older versions keep it in AppCompat's own store (`AppLocalesMetadataHolderService` with `autoStoreLocales`).
 * Until Stage 8 adds `values-en`, choosing English changes the saved language but not the texts (they fall back to Spanish).
 */
class AppCompatLanguageRepository : LanguageRepository {
    override fun currentLanguage(): AppLanguage? = languageOfTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    override fun setLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}

/**
 * The first launch (decision A4): fixes Spanish explicitly so a phone set to English does not open the app in English by itself once
 * `values-en` exists. It runs once ([AppearanceRepository.languageInitialized]); a language already chosen (for example in the phone's
 * per-app settings before the first launch) is respected and not overwritten.
 */
class LanguageBootstrap(
    private val languages: LanguageRepository,
    private val appearance: AppearanceRepository,
) {
    /** Returns true when it fixed Spanish now (AppCompat may recreate the Activity once), false when there was nothing to do. */
    suspend fun ensureInitialized(): Boolean {
        if (appearance.languageInitialized.first()) return false
        val fixedNow = languages.currentLanguage() == null
        if (fixedNow) languages.setLanguage(AppLanguage.DEFAULT)
        appearance.markLanguageInitialized()
        return fixedNow
    }
}
