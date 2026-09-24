package com.example.nightwatchprotocol.data.quiz

import kotlinx.serialization.Serializable

@Serializable
data class QuizQuestion(
    val id: String,
    val category: QuizCategory,
    val difficulty: Int,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
)
