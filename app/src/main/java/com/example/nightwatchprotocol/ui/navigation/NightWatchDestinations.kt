package com.example.nightwatchprotocol.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object Login

@Serializable
object Register

@Serializable
object ForgotPassword

@Serializable
object MainMenu

@Serializable
object PostAuthGate

@Serializable
object PlacementQuiz

@Serializable
data class PlacementResultRoute(
    val level: String,
    val weakCategories: List<String>,
    val saveFailed: Boolean = false,
)
