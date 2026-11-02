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
    fun displayName_prefersTheRealNameThenTheEmailPrefixThenAGenericName() {
        assertEquals("Nicolás Sotelo", profileDisplayText("  Nicolás Sotelo ", "n@example.com").text())
        assertEquals("euzuki1205", profileDisplayText(null, "euzuki1205@gmail.com").text())
        assertEquals("euzuki1205", profileDisplayText("   ", "euzuki1205@gmail.com").text())
        assertEquals("Usuario", profileDisplayText(null, null).text())
        assertEquals("Usuario", profileDisplayText("", "@gmail.com").text())
    }

    @Test
    fun level_showsExactlyWhatTheQuizComputed() {
        assertEquals("BAJO", levelText(PlacementResult(PlacementLevel.BAJO, emptySet())).text())
        assertEquals("MEDIO", levelText(PlacementResult(PlacementLevel.MEDIO, emptySet())).text())
        assertEquals("ALTO", levelText(PlacementResult(PlacementLevel.ALTO, emptySet())).text())
    }

    @Test
    fun level_withoutQuizResult_isNotAssessed() {
        assertEquals("Sin evaluar", levelText(null).text())
    }
}
