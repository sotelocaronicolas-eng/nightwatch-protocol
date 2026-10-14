package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.quiz.PlacementResult
import java.util.Calendar
import java.util.TimeZone

private val SPANISH_MONTHS = listOf(
    "Ene.", "Feb.", "Mar.", "Abr.", "May.", "Jun.", "Jul.", "Ago.", "Sep.", "Oct.", "Nov.", "Dic.",
)

const val LEVEL_NOT_ASSESSED = "Sin evaluar"

/** "Ago. 2026". Months are fixed Spanish abbreviations so the text does not depend on the phone's language. */
fun memberSinceLabel(creationTimeMillis: Long?, timeZone: TimeZone = TimeZone.getDefault()): String {
    if (creationTimeMillis == null || creationTimeMillis <= 0) return "—"
    val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = creationTimeMillis }
    return "${SPANISH_MONTHS[calendar.get(Calendar.MONTH)]} ${calendar.get(Calendar.YEAR)}"
}

/** The name to show: the real one, else the part of the email before "@", else "Usuario". */
fun profileDisplayName(displayName: String?, email: String?): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Usuario"

/** BAJO / MEDIO / ALTO exactly as the quiz computed it, or "Sin evaluar" when there is no result. */
fun levelLabel(result: PlacementResult?): String = result?.level?.name ?: LEVEL_NOT_ASSESSED
