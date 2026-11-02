package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import java.util.Calendar
import java.util.TimeZone
import com.example.nightwatchprotocol.text
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFormattersTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(year, month, day, 12, 0, 0) }.timeInMillis

    @Test
    fun memberSince_formatsMonthAbbreviationAndYear() {
        assertEquals("Ago. 2026", memberSinceLabel(millis(2026, Calendar.AUGUST, 15), utc).text())
        assertEquals("Ene. 2025", memberSinceLabel(millis(2025, Calendar.JANUARY, 2), utc).text())
        assertEquals("Dic. 2024", memberSinceLabel(millis(2024, Calendar.DECEMBER, 31), utc).text())
    }

    @Test
    fun memberSince_missingOrInvalidDate_showsDash() {
        assertEquals("—", memberSinceLabel(null, utc).text())
        assertEquals("—", memberSinceLabel(0L, utc).text())
    }

    @Test
    fun displayText_matchesTheStringVersion() {
        assertEquals("Nicolás Sotelo", profileDisplayText("  Nicolás Sotelo ", "n@example.com").text())
        assertEquals("euzuki1205", profileDisplayText(null, "euzuki1205@gmail.com").text())
        assertEquals("Usuario", profileDisplayText(null, null).text())
        assertEquals("Usuario", profileDisplayText("", "@gmail.com").text())
    }

    @Test
    fun levelText_matchesTheStringVersion() {
        assertEquals("BAJO", levelText(PlacementResult(PlacementLevel.BAJO, emptySet())).text())
        assertEquals("Sin evaluar", levelText(null).text())
        assertEquals(levelLabel(null), levelText(null).text())
    }

    @Test
    fun displayName_prefersTheRealNameThenTheEmailPrefixThenAGenericName() {
        assertEquals("Nicolás Sotelo", profileDisplayName("  Nicolás Sotelo ", "n@example.com"))
        assertEquals("euzuki1205", profileDisplayName(null, "euzuki1205@gmail.com"))
        assertEquals("euzuki1205", profileDisplayName("   ", "euzuki1205@gmail.com"))
        assertEquals("Usuario", profileDisplayName(null, null))
        assertEquals("Usuario", profileDisplayName("", "@gmail.com"))
    }

    @Test
    fun level_showsExactlyWhatTheQuizComputed() {
        assertEquals("BAJO", levelLabel(PlacementResult(PlacementLevel.BAJO, emptySet())))
        assertEquals("MEDIO", levelLabel(PlacementResult(PlacementLevel.MEDIO, emptySet())))
        assertEquals("ALTO", levelLabel(PlacementResult(PlacementLevel.ALTO, emptySet())))
    }

    @Test
    fun level_withoutQuizResult_isNotAssessed() {
        assertEquals("Sin evaluar", levelLabel(null))
    }
}
