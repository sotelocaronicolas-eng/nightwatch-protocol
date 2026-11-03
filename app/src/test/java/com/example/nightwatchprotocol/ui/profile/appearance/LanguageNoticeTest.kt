package com.example.nightwatchprotocol.ui.profile.appearance

import com.example.nightwatchprotocol.data.language.AppLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notice of the Language screen (PROPUESTA, Stage 8 part 2): visible only with English, and written in both languages. */
class LanguageNoticeTest {

    @Test
    fun theNotice_isShownOnlyWithEnglish() {
        assertTrue(showsLanguageNotice(AppLanguage.EN))
        assertFalse(showsLanguageNotice(AppLanguage.ES))
        // Whatever language is added later does not show it until someone decides so.
        assertEquals(listOf(AppLanguage.EN), AppLanguage.entries.filter { showsLanguageNotice(it) })
    }

    private fun resource(folder: String): String =
        listOf(File("src/main/res/$folder/strings_perfil.xml"), File("app/src/main/res/$folder/strings_perfil.xml")).first { it.isFile }.readText()

    private fun notice(folder: String): String =
        Regex("""<string name="settings_language_notice_text">([^<]*)</string>""").find(resource(folder))!!.groupValues[1]

    @Test
    fun theNotice_existsInBothLanguages_andSaysTheSame() {
        val es = notice("values")
        val en = notice("values-en")

        assertTrue(es.contains("servidor") && es.contains("correos") && es.contains("español"))
        assertTrue(en.contains("server") && en.contains("emails") && en.contains("Spanish"))
    }
}
