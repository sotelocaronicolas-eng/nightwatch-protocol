package com.example.nightwatchprotocol.ui.labreport

import com.example.nightwatchprotocol.data.learning.LearningTopic

/** The big number of the report: a percentage, or (passwords lab only) the estimated time of the strongest password tried. */
sealed interface ReportMetric {
    /** `round(100 x objectives done / 3)`: 0, 33, 67 or 100 (decision Q1). Calculated here, never stored. */
    data class Percent(val value: Int) : ReportMetric

    /** [text] is already formatted ("300 Años"); null when no password was tried (the screen shows "—", decision Q2). */
    data class EstimatedTime(val text: String?) : ReportMetric
}

/** One line of the list: the label of the objective and whether it was done (✅ or ❌, decision Q1). */
data class ReportItem(val label: String, val done: Boolean)

/**
 * What the report shows. [success] is true only with all the objectives done (decision P6). The failure title is common
 * to every lab ("Laboratorio no completado", decision P6) and is a string resource, so only [successTitle] is here.
 */
data class LabReportContent(
    val success: Boolean,
    val successTitle: String,
    /** The success or the failure sentence of the lab, already chosen. */
    val summary: String,
    val metricLabel: String,
    val metric: ReportMetric,
    val items: List<ReportItem>,
)

sealed interface LabReportStatus {
    data object Loading : LabReportStatus

    data class Ready(val content: LabReportContent) : LabReportStatus

    /** The lab content could not be read (it is a bundled file, so only a corrupt install gets here). */
    data object Failed : LabReportStatus
}

/** Saving a completed lab (the only case that saves: an incomplete lab is never registered, decision P6). */
sealed interface LabSaveStatus {
    /** Nothing to save: the attempt is incomplete, or the report is not ready. */
    data object NotNeeded : LabSaveStatus

    data object Saving : LabSaveStatus

    data object Saved : LabSaveStatus

    /** Only the kind of failure reaches the screen, never the exception. The lab is NOT completed until it is saved. */
    data class Failed(val offline: Boolean) : LabSaveStatus
}

data class LabReportUiState(
    val status: LabReportStatus = LabReportStatus.Loading,
    val save: LabSaveStatus = LabSaveStatus.NotNeeded,
)

sealed interface LabReportEvent {
    /** "Volver a la ruta". */
    data object BackToPath : LabReportEvent

    /** "Reintentar": the same lab again, from scratch. */
    data class RetryLab(val topic: LearningTopic) : LabReportEvent
}
