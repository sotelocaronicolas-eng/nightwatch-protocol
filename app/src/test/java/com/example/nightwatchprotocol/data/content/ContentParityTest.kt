package com.example.nightwatchprotocol.data.content

import com.example.nightwatchprotocol.data.language.AppLanguage
import com.example.nightwatchprotocol.data.learning.LearningAssets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity between the Spanish content (the base) and every other language folder under `assets/content/` (decision D18).
 * What must not change with the language: ids, order, structure, the correct answer, lab commands and the technical tokens of
 * the simulated terminal. What must change: every shown text, except the documented exceptions below.
 *
 * To add a language, add it to [AppLanguage] and copy the four files into `assets/content/<tag>/`: these tests pick it up.
 */
class ContentParityTest {
    private val files = listOf("learning_lessons.json", "learning_labs.json", "learning_exam.json", "quiz_questions.json")
    private val others = AppLanguage.entries.filter { it != AppLanguage.DEFAULT }

    /** Keys whose value is identical in every language (ids, enum names, emoji, commands, Figma sources). */
    private val sameInEveryLanguage = setOf("topic", "difficulty", "category", "id", "emoji", "command", "source", "correctIndex")

    /** Shown texts that are legitimately the same in Spanish and English (numbers, passwords, proper names, acronyms, log lines). */
    private val sameTextAllowed = setOf(
        "123456", "k7#mQ!9zRt$2", "Phishing", "Malware", "Firewall",
        "Smishing", "Vishing", "Whaling", "Tailgating",
        "203.0.113.55 - - \"GET /login\" 401\n203.0.113.55 - - \"GET /admin\" 403",
    )

    private fun parse(language: AppLanguage, file: String): JsonElement =
        Json.parseToJsonElement(LearningAssets.read(contentAssetPath(language, file)))

    @Test
    fun everyLanguage_hasTheFourFiles() {
        others.forEach { language ->
            files.forEach { file ->
                assertTrue("${language.tag}/$file", LearningAssets.exists(contentAssetPath(language, file)))
            }
        }
    }

    @Test
    fun structure_isTheSame_andFixedValuesDoNotChange() {
        others.forEach { language ->
            files.forEach { file ->
                compare("${language.tag}/$file", "", null, parse(AppLanguage.DEFAULT, file), parse(language, file))
            }
        }
    }

    private fun compare(where: String, path: String, key: String?, es: JsonElement, other: JsonElement) {
        when (es) {
            is JsonObject -> {
                assertTrue("$where $path: not an object", other is JsonObject)
                other as JsonObject
                assertEquals("$where $path: keys", es.keys, other.keys)
                es.forEach { (k, v) -> compare(where, "$path.$k", k, v, other.getValue(k)) }
            }
            is JsonArray -> {
                assertTrue("$where $path: not an array", other is JsonArray)
                other as JsonArray
                assertEquals("$where $path: size", es.size, other.size)
                es.indices.forEach { compare(where, "$path[$it]", key, es[it], other[it]) }
            }
            is JsonPrimitive -> {
                assertTrue("$where $path: not a primitive", other is JsonPrimitive)
                other as JsonPrimitive
                assertEquals("$where $path: isString", es.isString, other.isString)
                if (!es.isString || key in sameInEveryLanguage) {
                    assertEquals("$where $path: must not change with the language", es.content, other.content)
                } else {
                    assertTrue("$where $path: empty text", other.content.isNotBlank())
                    if (es.content !in sameTextAllowed) {
                        assertTrue("$where $path: not translated (\"${es.content.take(40)}\")", es.content != other.content)
                    }
                }
            }
        }
    }

    @Test
    fun correctAnswers_areTheSameInExamAndQuiz() {
        others.forEach { language ->
            listOf("learning_exam.json", "quiz_questions.json").forEach { file ->
                val es = (parse(AppLanguage.DEFAULT, file) as JsonArray).map { (it as JsonObject).getValue("correctIndex") }
                val other = (parse(language, file) as JsonArray).map { (it as JsonObject).getValue("correctIndex") }
                assertEquals("${language.tag}/$file", es, other)
                // Four options in both, so the index points at the same option.
                (parse(language, file) as JsonArray).forEach {
                    assertEquals(4, ((it as JsonObject).getValue("options") as JsonArray).size)
                }
            }
        }
    }

    @Test
    fun labCommands_areIdentical() {
        fun commands(language: AppLanguage) =
            (parse(language, "learning_labs.json") as JsonArray).flatMap { lab ->
                ((lab as JsonObject).getValue("objectives") as JsonArray).map { (it as JsonObject).getValue("command") }
            }
        others.forEach { assertEquals(commands(AppLanguage.DEFAULT), commands(it)) }
        assertEquals(18, commands(AppLanguage.DEFAULT).size)
    }

    /** Numbers, IPs, domains, file names and acronyms keep their value when the text around them is translated. */
    private val technicalToken = Regex("""[A-Za-z0-9_.:/\-]*\d[A-Za-z0-9_.:/\-]*|\b[\w\-]+(?:\.[\w\-]+)+\b|\b[A-Z]{3,}""")  // acronyms; the plural "VPNs" still counts as "VPN"

    private fun tokens(text: String): Set<String> =
        technicalToken.findAll(text).map { it.value.trimEnd('.', ':') }.toSet()

    private fun shownTexts(element: JsonElement, key: String? = null): List<String> = when (element) {
        is JsonObject -> element.flatMap { (k, v) -> shownTexts(v, k) }
        is JsonArray -> element.flatMap { shownTexts(it, key) }
        is JsonPrimitive -> if (element.isString && key !in sameInEveryLanguage) listOf(element.content) else emptyList()
    }

    @Test
    fun labsAndLessons_keepTheirTechnicalTokens() {
        others.forEach { language ->
            listOf("learning_labs.json", "learning_lessons.json").forEach { file ->
                val es = shownTexts(parse(AppLanguage.DEFAULT, file))
                val other = shownTexts(parse(language, file))
                assertEquals(es.size, other.size)
                es.indices.forEach {
                    assertEquals("${language.tag}/$file: ${es[it].take(40)}", tokens(es[it]), tokens(other[it]))
                }
            }
        }
    }

    @Test
    fun lessonParagraphsAndBullets_keepTheirFormat() {
        others.forEach { language ->
            val es = (parse(AppLanguage.DEFAULT, "learning_lessons.json") as JsonArray)
            val other = (parse(language, "learning_lessons.json") as JsonArray)
            es.indices.forEach { i ->
                val esCards = ((es[i] as JsonObject).getValue("cards") as JsonArray)
                val otherCards = ((other[i] as JsonObject).getValue("cards") as JsonArray)
                esCards.indices.forEach { c ->
                    val a = ((esCards[c] as JsonObject).getValue("text") as JsonPrimitive).content
                    val b = ((otherCards[c] as JsonObject).getValue("text") as JsonPrimitive).content
                    assertEquals("lesson $i card $c: paragraphs", a.split("\n\n").size, b.split("\n\n").size)
                    assertEquals("lesson $i card $c: bullets", a.count { it == '•' }, b.count { it == '•' })
                    assertTrue("lesson $i card $c: no double spaces or edge blanks", b == b.trim() && "  " !in b)
                }
            }
        }
    }
}
