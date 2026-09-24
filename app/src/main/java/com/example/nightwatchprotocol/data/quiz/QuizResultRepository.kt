package com.example.nightwatchprotocol.data.quiz

interface QuizResultRepository {
    suspend fun getResult(uid: String): PlacementResult?
    suspend fun saveResult(uid: String, result: PlacementResult)
}
