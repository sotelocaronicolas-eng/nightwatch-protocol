package com.example.nightwatchprotocol.data.quiz

import kotlinx.coroutines.awaitCancellation

class FakeQuizResultRepository : QuizResultRepository {
    var getResultResult: Result<PlacementResult?> = Result.success(null)
    var saveResultResult: Result<Unit> = Result.success(Unit)

    /** When true, [getResult] never returns or throws -- it suspends forever, like a real
     * server read with no network. Used to test caller-side timeout handling. */
    var getResultNeverCompletes: Boolean = false

    /** When true, [saveResult] never returns or throws -- it suspends forever, like a real
     * server write with no network. Used to test caller-side timeout handling. */
    var saveResultNeverCompletes: Boolean = false

    var getResultCallCount = 0
    var saveResultCallCount = 0
    var lastSavedResult: PlacementResult? = null

    override suspend fun getResult(uid: String): PlacementResult? {
        getResultCallCount++
        if (getResultNeverCompletes) awaitCancellation()
        return getResultResult.getOrThrow()
    }

    override suspend fun saveResult(uid: String, result: PlacementResult) {
        saveResultCallCount++
        lastSavedResult = result
        if (saveResultNeverCompletes) awaitCancellation()
        saveResultResult.getOrThrow()
    }
}
