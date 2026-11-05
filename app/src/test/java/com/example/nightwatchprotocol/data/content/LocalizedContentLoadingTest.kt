package com.example.nightwatchprotocol.data.content

import com.example.nightwatchprotocol.data.language.AppLanguage
import com.example.nightwatchprotocol.data.learning.EXAM_ASSET
import com.example.nightwatchprotocol.data.learning.LearningAssets
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.LocalLearningContentRepository
import com.example.nightwatchprotocol.data.quiz.LocalQuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QUIZ_ASSET
import com.example.nightwatchprotocol.data.quiz.QuizQuestion
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repositories read the folder of the active language and fall back to Spanish without crashing. */
class LocalizedContentLoadingTest {
    private fun learning(language: () -> AppLanguage?, read: (String) -> String = LearningAssets::read) =
        LocalLearningContentRepository(language = language, readAsset = read)

    @Test
    fun assetPath_isOneFolderPerLanguage() {
        assertEquals("content/es/learning_exam.json", contentAssetPath(AppLanguage.ES, "learning_exam.json"))
        assertEquals("content/en/learning_exam.json", contentAssetPath(AppLanguage.EN, "learning_exam.json"))
    }

    @Test
    fun learning_inEnglish_showsTheEnglishContent_andInSpanishTheSpanishOne() = runTest {
        val en = learning({ AppLanguage.EN }).getContent()
        val es = learning({ AppLanguage.ES }).getContent()
        val none = learning({ null }).getContent()

        assertEquals("Passwords and Authentication", en.lesson(LearningTopic.CONTRASENAS).title)
        assertEquals("Contraseñas y Autenticación", es.lesson(LearningTopic.CONTRASENAS).title)
        assertEquals(es, none) // no language chosen yet means the base language
        assertNotEquals(es, en)
        assertEquals(es.exam.map { it.correctIndex }, en.exam.map { it.correctIndex })
    }

    @Test
    fun learning_followsTheLanguageWhileTheAppLives_andKeepsOneCopyPerLanguage() = runTest {
        var current = AppLanguage.ES
        val reads = mutableListOf<String>()
        val repository = learning({ current }) { path -> reads += path; LearningAssets.read(path) }

        val es = repository.getContent()
        current = AppLanguage.EN
        val en = repository.getContent()
        current = AppLanguage.ES
        val esAgain = repository.getContent()
        current = AppLanguage.EN
        val enAgain = repository.getContent()

        assertTrue(es === esAgain)
        assertTrue(en === enAgain)
        assertNotEquals(es, en)
        assertEquals(6, reads.size) // three files per language, each read once
    }

    @Test
    fun learning_whenAnEnglishFileIsMissing_fallsBackToSpanishForThatFile() = runTest {
        val repository = learning({ AppLanguage.EN }) { path ->
            if (path == contentAssetPath(AppLanguage.EN, EXAM_ASSET)) throw java.io.FileNotFoundException(path)
            LearningAssets.read(path)
        }

        val content = repository.getContent()

        assertEquals("Passwords and Authentication", content.lesson(LearningTopic.CONTRASENAS).title) // still English
        assertEquals("¿Cuál de estas contraseñas es más segura?", content.exam.first().text) // Spanish fallback
    }

    @Test
    fun learning_whenTheEnglishFolderIsMissingOrBroken_fallsBackToSpanish() = runTest {
        val missing = learning({ AppLanguage.EN }) { path ->
            if ("/en/" in path) throw java.io.FileNotFoundException(path)
            LearningAssets.read(path)
        }.getContent()
        val broken = learning({ AppLanguage.EN }) { path ->
            if ("/en/" in path) "{ esto no es JSON" else LearningAssets.read(path)
        }.getContent()
        val spanish = learning({ AppLanguage.ES }).getContent()

        assertEquals(spanish, missing)
        assertEquals(spanish, broken)
    }

    @Test
    fun loadLocalizedContent_doesNotTouchTheEnglishFolder_inSpanish() {
        val reads = mutableListOf<String>()
        val value = loadLocalizedContent(AppLanguage.ES, "x.json", { reads += it; "1" }) { it.toInt() }

        assertEquals(1, value)
        assertEquals(listOf("content/es/x.json"), reads)
        assertFalse(reads.any { "/en/" in it })
    }

    @Test
    fun loadLocalizedContent_whenTheBaseLanguageIsBroken_theErrorSurfaces() {
        val result = runCatching { loadLocalizedContent(AppLanguage.ES, "x.json", { throw java.io.IOException("sin archivo") }) { it } }

        assertTrue(result.isFailure)
    }

    @Test
    fun quiz_readsTheBankOfTheActiveLanguage_andFallsBack() = runTest {
        val english = LocalQuizContentRepository(readAsset = LearningAssets::read, language = { AppLanguage.EN }).getAttempt()
        val spanish = LocalQuizContentRepository(readAsset = LearningAssets::read, language = { AppLanguage.ES }).getAttempt()
        val fallback = LocalQuizContentRepository(
            readAsset = { path -> if ("/en/" in path) throw java.io.FileNotFoundException(path) else LearningAssets.read(path) },
            language = { AppLanguage.EN },
        ).getAttempt()

        assertEquals(spanish.size, english.size)
        assertTrue(english.none { "¿" in it.text })
        assertTrue(spanish.all { "¿" in it.text })
        assertTrue(fallback.all { "¿" in it.text }) // English folder missing: the whole bank is Spanish
    }

    @Test
    fun quiz_theSameIdHasTheSameCorrectAnswerInBothLanguages() = runTest {
        // Read the whole bank: the attempt is a selection, so compare through the raw bank of each language.
        fun bank(language: AppLanguage) =
            Json.decodeFromString<List<QuizQuestion>>(LearningAssets.read(contentAssetPath(language, QUIZ_ASSET)))
        val es = bank(AppLanguage.ES)
        val en = bank(AppLanguage.EN)

        assertEquals(es.map { it.id }, en.map { it.id })
        assertEquals(es.map { it.correctIndex }, en.map { it.correctIndex })
        assertEquals(es.map { it.category }, en.map { it.category })
    }
}
