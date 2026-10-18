package com.example.nightwatchprotocol.data.profile

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The oldest age the profile accepts. */
const val MAX_AGE_YEARS = 120

/**
 * A calendar date without time or zone (no java.time: the app supports API 24 without desugaring).
 * Stored as ISO text `yyyy-MM-dd`; shown as `dd/MM/yyyy`.
 */
data class BirthDate(val year: Int, val month: Int, val day: Int) : Comparable<BirthDate> {

    /** `yyyy-MM-dd`, the format saved in the `birth_date` column (always 10 characters for a real date). */
    fun iso(): String = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    /** `dd/MM/yyyy`, the format shown to the user. */
    fun display(): String = String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year)

    fun isRealDate(): Boolean = year in 1..9999 && month in 1..12 && day in 1..daysInMonth(year, month)

    /** Midnight UTC of this date, the value the Material 3 date picker works with. */
    fun toUtcMillis(): Long = Calendar.getInstance(UTC).apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
    }.timeInMillis

    override fun compareTo(other: BirthDate): Int =
        compareValuesBy(this, other, BirthDate::year, BirthDate::month, BirthDate::day)

    companion object {
        private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        private val ISO_PATTERN = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

        /** Strict `yyyy-MM-dd`; null when the text is missing, malformed or not a real calendar date. */
        fun parseIso(text: String?): BirthDate? {
            if (text == null || !ISO_PATTERN.matches(text)) return null
            val date = BirthDate(text.substring(0, 4).toInt(), text.substring(5, 7).toInt(), text.substring(8, 10).toInt())
            return date.takeIf { it.isRealDate() }
        }

        /** The picker returns the selected day as midnight UTC. */
        fun fromUtcMillis(utcMillis: Long): BirthDate {
            val calendar = Calendar.getInstance(UTC).apply { timeInMillis = utcMillis }
            return BirthDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        }

        /** Today's date on the wall clock of [timeZone]. */
        fun today(nowMillis: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): BirthDate {
            val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
            return BirthDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}

private fun isLeapYear(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (isLeapYear(year)) 29 else 28
}

enum class BirthDateIssue { NotARealDate, InTheFuture, TooOld }

/** The oldest date allowed on [today]: exactly [MAX_AGE_YEARS] years back (29 Feb falls on 28 Feb if needed). */
fun earliestBirthDate(today: BirthDate): BirthDate {
    val year = today.year - MAX_AGE_YEARS
    return BirthDate(year, today.month, minOf(today.day, daysInMonth(year, today.month)))
}

/** Null when [date] is acceptable: a real date, not after [today] and not more than [MAX_AGE_YEARS] years ago. */
fun birthDateIssue(date: BirthDate, today: BirthDate): BirthDateIssue? = when {
    !date.isRealDate() -> BirthDateIssue.NotARealDate
    date > today -> BirthDateIssue.InTheFuture
    date < earliestBirthDate(today) -> BirthDateIssue.TooOld
    else -> null
}

const val MESSAGE_BIRTH_DATE_FUTURE = "La fecha de nacimiento no puede ser futura"
const val MESSAGE_BIRTH_DATE_TOO_OLD = "Revisa la fecha de nacimiento: la edad máxima es de 120 años"
const val MESSAGE_BIRTH_DATE_INVALID = "La fecha de nacimiento no es válida"

fun BirthDateIssue.message(): String = when (this) {
    BirthDateIssue.NotARealDate -> MESSAGE_BIRTH_DATE_INVALID
    BirthDateIssue.InTheFuture -> MESSAGE_BIRTH_DATE_FUTURE
    BirthDateIssue.TooOld -> MESSAGE_BIRTH_DATE_TOO_OLD
}
