package com.example.nightwatchprotocol.data.content

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the DATA of `supabase/migrations/0013_batallas_ingles.sql` against the Spanish bank of `0009_banco_batallas.sql`, without a
 * database: every question and ulti challenge has exactly one English version, the four options are all there and never empty, and the
 * translations are tied to the original by its Spanish text (so the correct answer, which lives only in the original, is the same).
 * It also pins the compatibility promises of the script (`p_lang` with a default, old signatures dropped, closed tables).
 */
class BattleTranslationsMigrationTest {
    private fun read(name: String): String {
        val candidates = listOf(File("../supabase/migrations/$name"), File("supabase/migrations/$name"))
        val file = candidates.firstOrNull { it.isFile } ?: error("No se encontró $name (probado: ${candidates.joinToString { it.path }})")
        return file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    }

    /** The quoted strings of one SQL row such as `('a', 1, 'it''s')`; numbers are skipped, `''` is an escaped quote. */
    private fun strings(row: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < row.length) {
            if (row[i] == '\'') {
                val sb = StringBuilder()
                i++
                while (i < row.length) {
                    if (row[i] == '\'') {
                        if (i + 1 < row.length && row[i + 1] == '\'') {
                            sb.append('\'')
                            i += 2
                            continue
                        }
                        break
                    }
                    sb.append(row[i])
                    i++
                }
                out += sb.toString()
            }
            i++
        }
        return out
    }

    private fun rows(sql: String): List<String> =
        sql.lines().map { it.trim() }.filter { it.startsWith("('") && (it.endsWith("),") || it.endsWith(");") || it.endsWith(")")) }

    private val original = read("0009_banco_batallas.sql")
    private val migration = read("0013_batallas_ingles.sql")

    /** Rows of 0009 with 7 strings (topic, question, A, B, C, D, correct) and with 4 (kind, topic, title, command). */
    private val questions = rows(original).map(::strings).filter { it.size == 7 }
    private val challenges = rows(original).map(::strings).filter { it.size == 4 }

    /** Rows of 0013 with 6 strings (Spanish question, question, A, B, C, D) and with 3 (kind, topic, title). */
    private val translated = rows(migration).map(::strings).filter { it.size == 6 }
    private val translatedChallenges = rows(migration).map(::strings).filter { it.size == 3 }

    @Test
    fun theOriginalBank_is72Questions_and12Challenges() {
        assertEquals(72, questions.size)
        assertEquals(12, challenges.size)
    }

    @Test
    fun everyQuestion_hasExactlyOneEnglishVersion_tiedByItsSpanishText() {
        assertEquals(72, translated.size)
        assertEquals(questions.map { it[1] }.toSet(), translated.map { it[0] }.toSet())
        assertEquals(72, translated.map { it[0] }.toSet().size)
    }

    @Test
    fun theEnglishQuestions_haveFourFilledOptionsInTheSameOrder_andFitTheColumns() {
        translated.forEach { row ->
            val question = row[1]
            val (a, b, c, d) = row.subList(2, 6)
            listOf(question, a, b, c, d).forEach { assertTrue(row[0], it.isNotBlank()) }
            assertTrue(row[0], question.length <= 400)
            listOf(a, b, c, d).forEach { assertTrue(row[0], it.length <= 200) }
            assertEquals("opciones repetidas en: ${row[0]}", 4, setOf(a, b, c, d).size)
        }
    }

    @Test
    fun optionsThatAreNumbersCommandsOrNames_matchThePositionOfTheSpanishOne() {
        // An option that is the same in both languages (a port, a command, a password) must sit in the same position, or the
        // correct letter (stored only in the original) would point at a different option.
        translated.forEach { row ->
            val original = questions.first { it[1] == row[0] }
            (0..3).forEach { i ->
                val es = original[2 + i]
                val en = row[2 + i]
                if (es.matches(Regex("""[0-9]+|ufw .*|banco-ejemp.*|app\.banco.*|www\.banco.*"""))) {
                    assertEquals("${row[0]} opción ${'A' + i}", es, en)
                }
            }
        }
    }

    @Test
    fun noEnglishQuestion_isIdenticalToTheSpanishOne() {
        translated.forEach { assertTrue(it[0], it[0] != it[1]) }
    }

    @Test
    fun everyUltiChallenge_hasExactlyOneEnglishTitle_byKindAndTopic() {
        assertEquals(12, translatedChallenges.size)
        assertEquals(challenges.map { it[0] to it[1] }.toSet(), translatedChallenges.map { it[0] to it[1] }.toSet())
        translatedChallenges.forEach { assertTrue(it.toString(), it[2].isNotBlank() && it[2].length <= 120) }
    }

    @Test
    fun theTranslationTables_doNotHoldTheCorrectAnswerNorTheCommand() {
        val tables = migration.substringAfter("create table if not exists public.battle_question_translations")
            .substringBefore("alter table public.battle_question_translations")
        assertFalse(tables.contains("correct "))
        assertFalse(tables.contains("expected_command"))
    }

    @Test
    fun theScript_keepsOldClientsWorking_andTheTablesClosed() {
        // p_lang has a default, so v2.3.0 (no p_lang) still resolves; the old signatures are dropped so PostgREST never sees two.
        assertTrue(migration.contains("battle_sync(p_battle uuid, p_lang text default 'es')"))
        assertTrue(migration.contains("p_arg text default null, p_lang text default 'es')"))
        assertTrue(migration.contains("drop function if exists public.battle_sync(uuid);"))
        assertTrue(migration.contains("drop function if exists public.battle_play(uuid, text, integer, text);"))
        // Idempotent and closed.
        assertTrue(migration.contains("create table if not exists public.battle_question_translations"))
        assertTrue(migration.contains("on conflict (question_id, lang) do update"))
        assertTrue(migration.contains("enable row level security"))
        assertFalse(migration.contains("create policy"))
        assertFalse(Regex("""grant\s+select\s+on\s+public\.(battle_question|ulti_challenge)_translations""", RegexOption.IGNORE_CASE).containsMatchIn(migration))
        // One transaction, and a rollback script exists.
        assertTrue(migration.contains("\nbegin;") && migration.contains("\ncommit;"))
        val rollback = File("../supabase/rollback/0013_rollback.sql").takeIf { it.isFile } ?: File("supabase/rollback/0013_rollback.sql")
        assertTrue(rollback.isFile)
    }
}
