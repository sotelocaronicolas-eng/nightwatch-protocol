package com.example.nightwatchprotocol.ui.home

import com.example.nightwatchprotocol.data.profile.levelLabel
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import kotlinx.coroutines.withTimeoutOrNull

private const val READ_TIMEOUT_MILLIS = 5_000L

/**
 * BAJO / MEDIO / ALTO as the quiz computed it, or "Sin evaluar". A failed or slow read counts as "not assessed", like Mi
 * Perfil: the level is informational on Inicio and on the map. Shared by both so the text is always the same.
 */
suspend fun readLevelLabel(quizResultRepository: QuizResultRepository, uid: String): String {
    val result = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
        runCatchingKeepCancellation { quizResultRepository.getResult(uid) }.getOrNull()
    }
    return levelLabel(result)
}
