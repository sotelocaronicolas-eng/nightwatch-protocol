package com.example.nightwatchprotocol.data.quiz

class FakeQuizResultRepository : QuizResultRepository {
    var getResultResult: Result<PlacementResult?> = Result.success(null)
    var saveResultResult: Result<Unit> = Result.success(Unit)

    var getResultCallCount = 0
    var saveResultCallCount = 0
    var lastSavedResult: PlacementResult? = null

    override suspend fun getResult(uid: String): PlacementResult? {
        getResultCallCount++
        return getResultResult.getOrThrow()
    }

    override suspend fun saveResult(uid: String, result: PlacementResult) {
        saveResultCallCount++
        lastSavedResult = result
        saveResultResult.getOrThrow()
    }
}
