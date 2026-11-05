package com.example.nightwatchprotocol.data.content

import com.example.nightwatchprotocol.data.language.AppLanguage

/** Folder of the bundled content inside `assets/`. Each language has its own subfolder: `content/es/`, `content/en/`... (decision D18). */
const val CONTENT_ROOT = "content"

/** The asset path of [file] for [language], for example `content/en/learning_exam.json`. */
fun contentAssetPath(language: AppLanguage, file: String): String = "$CONTENT_ROOT/${language.tag}/$file"

/**
 * Reads and parses [file] in [language]. When the file is missing or cannot be parsed in that language, it falls back to the base
 * language (Spanish) instead of failing, so a language with incomplete content never crashes the app. The base language itself is
 * not caught: if it is broken the error surfaces, as it did before.
 */
fun <T> loadLocalizedContent(
    language: AppLanguage,
    file: String,
    readAsset: (path: String) -> String,
    parse: (json: String) -> T,
): T {
    if (language != AppLanguage.DEFAULT) {
        try {
            return parse(readAsset(contentAssetPath(language, file)))
        } catch (_: Exception) {
            // Missing or broken file in this language: use the base language below.
        }
    }
    return parse(readAsset(contentAssetPath(AppLanguage.DEFAULT, file)))
}
