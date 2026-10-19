package com.example.nightwatchprotocol.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.StreakState

/** "Racha Activa de N días 🔥" (Inicio and the user menu): "día" with 1. Loading and unavailable say so briefly. */
@Composable
fun activeStreakText(streak: StreakState): String = when (streak) {
    StreakState.Loading -> stringResource(R.string.learning_streak_loading)
    StreakState.Unavailable -> stringResource(R.string.learning_streak_unavailable)
    is StreakState.Days -> pluralStringResource(R.plurals.learning_streak_active, streak.current, streak.current)
}

/** "N días 🔥" (the "Racha activa" row of Mi Perfil). */
@Composable
fun streakValueText(streak: StreakState): String = when (streak) {
    StreakState.Loading -> stringResource(R.string.learning_loading_dots)
    StreakState.Unavailable -> stringResource(R.string.learning_streak_value_unavailable)
    is StreakState.Days -> pluralStringResource(R.plurals.learning_streak_value, streak.current, streak.current)
}
