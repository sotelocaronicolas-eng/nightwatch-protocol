package com.example.nightwatchprotocol.data.profile

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthDateTest {
    private val today = BirthDate(2026, 10, 11)

    // ---- Formats ----

    @Test
    fun theIsoFormat_isTenCharactersWithZeroPadding() {
        assertEquals("2000-05-07", BirthDate(2000, 5, 7).iso())
        assertEquals("1999-12-31", BirthDate(1999, 12, 31).iso())
        assertEquals(10, BirthDate(1905, 1, 2).iso().length)
    }

    @Test
    fun theDisplayFormat_isDdMmYyyy() {
        assertEquals("07/05/2000", BirthDate(2000, 5, 7).display())
        assertEquals("31/12/1999", BirthDate(1999, 12, 31).display())
    }

    @Test
    fun isoRoundTrips() {
        val date = BirthDate(1988, 2, 29)

        assertEquals(date, BirthDate.parseIso(date.iso()))
    }

    // ---- Parsing ----

    @Test
    fun parseIso_acceptsOnlyRealCalendarDates() {
        assertNotNull(BirthDate.parseIso("2000-02-29")) // leap year
        assertNull(BirthDate.parseIso("1900-02-29")) // 1900 is not a leap year
        assertNotNull(BirthDate.parseIso("2000-12-31"))
        assertNull(BirthDate.parseIso("2001-02-29"))
        assertNull(BirthDate.parseIso("2001-04-31"))
        assertNull(BirthDate.parseIso("2001-13-01"))
        assertNull(BirthDate.parseIso("2001-00-10"))
        assertNull(BirthDate.parseIso("2001-05-00"))
    }

    @Test
    fun parseIso_rejectsMalformedText() {
        assertNull(BirthDate.parseIso(null))
        assertNull(BirthDate.parseIso(""))
        assertNull(BirthDate.parseIso("07/05/2000"))
        assertNull(BirthDate.parseIso("2000-5-7"))
        assertNull(BirthDate.parseIso("2000-05-07T00:00"))
        assertNull(BirthDate.parseIso(" 2000-05-07"))
        assertNull(BirthDate.parseIso("abcd-ef-gh"))
    }

    // ---- Validation: future dates and unrealistic ages ----

    @Test
    fun todayAndThePast_areAccepted() {
        assertNull(birthDateIssue(today, today))
        assertNull(birthDateIssue(BirthDate(2026, 10, 10), today))
        assertNull(birthDateIssue(BirthDate(1990, 6, 15), today))
    }

    @Test
    fun aFutureDate_isRejected() {
        assertEquals(BirthDateIssue.InTheFuture, birthDateIssue(BirthDate(2026, 10, 12), today))
        assertEquals(BirthDateIssue.InTheFuture, birthDateIssue(BirthDate(2026, 11, 1), today))
        assertEquals(BirthDateIssue.InTheFuture, birthDateIssue(BirthDate(2027, 1, 1), today))
    }

    @Test
    fun exactly120YearsAgo_isTheOldestAccepted() {
        assertNull(birthDateIssue(BirthDate(1906, 10, 11), today))
        assertEquals(BirthDateIssue.TooOld, birthDateIssue(BirthDate(1906, 10, 10), today))
        assertEquals(BirthDateIssue.TooOld, birthDateIssue(BirthDate(1850, 1, 1), today))
    }

    @Test
    fun theLimitFollowsTodayAndHandlesFebruary29() {
        assertEquals(BirthDate(1906, 10, 11), earliestBirthDate(today))
        // 29 Feb 2028 minus 120 years is 29 Feb 1908, and 1908 is a leap year, so the day is kept.
        assertEquals(BirthDate(1908, 2, 29), earliestBirthDate(BirthDate(2028, 2, 29)))
        // 29 Feb 2020 minus 120 years would be 29 Feb 1900, which does not exist (1900 is not a leap year).
        assertEquals(BirthDate(1900, 2, 28), earliestBirthDate(BirthDate(2020, 2, 29)))
    }

    @Test
    fun anImpossibleDate_isReportedAsNotReal() {
        assertEquals(BirthDateIssue.NotARealDate, birthDateIssue(BirthDate(2001, 2, 30), today))
        assertEquals(BirthDateIssue.NotARealDate, birthDateIssue(BirthDate(2001, 14, 1), today))
    }

    @Test
    fun everyIssueHasItsOwnSpanishMessage() {
        assertEquals("La fecha de nacimiento no puede ser futura", BirthDateIssue.InTheFuture.message())
        assertEquals("Revisa la fecha de nacimiento: la edad máxima es de 120 años", BirthDateIssue.TooOld.message())
        assertEquals("La fecha de nacimiento no es válida", BirthDateIssue.NotARealDate.message())
    }

    // ---- Comparison and the date picker's UTC milliseconds ----

    @Test
    fun datesAreOrderedByYearThenMonthThenDay() {
        assertTrue(BirthDate(2000, 1, 1) < BirthDate(2000, 1, 2))
        assertTrue(BirthDate(2000, 1, 31) < BirthDate(2000, 2, 1))
        assertTrue(BirthDate(1999, 12, 31) < BirthDate(2000, 1, 1))
        assertFalse(BirthDate(2000, 1, 1) < BirthDate(2000, 1, 1))
    }

    @Test
    fun theUtcMillisFromThePicker_roundTripWhateverTheDeviceTimeZone() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("UTC", "America/Bogota", "Pacific/Auckland", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val date = BirthDate(1995, 8, 20)

                assertEquals("zona $zone", date, BirthDate.fromUtcMillis(date.toUtcMillis()))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun midnightUtcOfADay_isThatDay() {
        val millis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2001, Calendar.SEPTEMBER, 9, 0, 0, 0)
        }.timeInMillis

        assertEquals(BirthDate(2001, 9, 9), BirthDate.fromUtcMillis(millis))
    }

    @Test
    fun today_usesTheWallClockOfTheGivenZone() {
        val instant = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.OCTOBER, 11, 3, 0, 0) // 22:00 on the 10th in Bogotá
        }.timeInMillis

        assertEquals(BirthDate(2026, 10, 11), BirthDate.today(instant, TimeZone.getTimeZone("UTC")))
        assertEquals(BirthDate(2026, 10, 10), BirthDate.today(instant, TimeZone.getTimeZone("America/Bogota")))
    }
}
