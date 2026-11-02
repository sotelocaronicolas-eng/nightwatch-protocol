package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import java.util.Calendar
import java.util.TimeZone

private val SHORT_MONTHS = listOf(
    R.string.common_month_short_1, R.string.common_month_short_2, R.string.common_month_short_3,
    R.string.common_month_short_4, R.string.common_month_short_5, R.string.common_month_short_6,
    R.string.common_month_short_7, R.string.common_month_short_8, R.string.common_month_short_9,
    R.string.common_month_short_10, R.string.common_month_short_11, R.string.common_month_short_12,
)

// PENDING_PART_B: Inicio and the learning path still read this String; Part B replaces them with levelText() and removes it.
const val LEVEL_NOT_ASSESSED = "Sin evaluar" // PENDING_PART_B

/** "Ago. 2026". The month name comes from the resources, so it follows the language of the app. */
fun memberSinceLabel(creationTimeMillis: Long?, timeZone: TimeZone = TimeZone.getDefault()): UiText {
    if (creationTimeMillis == null || creationTimeMillis <= 0) return UiText.dynamic("—")
    val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = creationTimeMillis }
    return UiText.of(
        R.string.profile_member_since_value,
        UiText.of(SHORT_MONTHS[calendar.get(Calendar.MONTH)]),
        calendar.get(Calendar.YEAR),
    )
}

/** The name to show: the real one, else the part of the email before "@", else "Usuario". */
fun profileDisplayText(displayName: String?, email: String?): UiText {
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
    return name?.let { UiText.dynamic(it) } ?: UiText.of(R.string.common_default_user_name)
}

// PENDING_PART_B: Inicio and Social still read this String.
fun profileDisplayName(displayName: String?, email: String?): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Usuario" // PENDING_PART_B

/** BAJO / MEDIO / ALTO exactly as the quiz computed it, or "Sin evaluar" when there is no result. */
fun levelText(result: PlacementResult?): UiText =
    result?.level?.name?.let { UiText.dynamic(it) } ?: UiText.of(R.string.common_level_not_assessed)

// PENDING_PART_B
fun levelLabel(result: PlacementResult?): String = result?.level?.name ?: LEVEL_NOT_ASSESSED
