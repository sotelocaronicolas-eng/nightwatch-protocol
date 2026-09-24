package com.example.nightwatchprotocol.data.quiz

import kotlinx.serialization.Serializable

@Serializable
enum class QuizCategory {
    CONCEPTOS_BASICOS,
    CONTRASENAS,
    INGENIERIA_SOCIAL,
    REDES_MALWARE,
}
