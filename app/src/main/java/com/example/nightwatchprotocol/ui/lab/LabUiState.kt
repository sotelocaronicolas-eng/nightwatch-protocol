package com.example.nightwatchprotocol.ui.lab

import com.example.nightwatchprotocol.data.learning.LabTerminal

sealed interface LabStatus {
    data object Loading : LabStatus

    /** The terminal of the lab: the objectives done and everything printed so far. */
    data class Ready(val terminal: LabTerminal) : LabStatus

    /** The lab content could not be read (it is a bundled file, so only a corrupt install gets here). */
    data object Failed : LabStatus
}

data class LabUiState(
    val status: LabStatus = LabStatus.Loading,
    /** The text of the command field. It keeps its text after "Enviar", as the solved frame of Figma does (2374:329). */
    val input: String = "",
)

sealed interface LabEvent {
    /** The "X": back to where the person was, without saving and without a dialog (SUPUESTO). */
    data object Close : LabEvent

    /** "Finalizar laboratorio" is provisional in stage 4A: it only says "Próximamente" (stage 4B opens the report). */
    data object ShowComingSoon : LabEvent
}
