package com.example.nightwatchprotocol.data.quiz

interface QuizContentRepository {
    suspend fun getAttempt(): List<QuizQuestion>
}
