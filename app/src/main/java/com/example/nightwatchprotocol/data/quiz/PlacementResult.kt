package com.example.nightwatchprotocol.data.quiz

data class PlacementResult(
    val level: PlacementLevel,
    val weakCategories: Set<QuizCategory>,
)
