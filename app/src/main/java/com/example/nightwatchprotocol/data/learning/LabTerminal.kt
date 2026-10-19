package com.example.nightwatchprotocol.data.learning

import java.util.Locale

/**
 * Text shown when the typed command is not one of the lab's three. SUPUESTO: this text is not in Figma.
 */
const val UNRECOGNIZED_COMMAND_MESSAGE = "Comando no reconocido. Toca un objetivo para usar su comando."

/** What the terminal answered to one submission. */
sealed interface TerminalResult {
    /** The command fulfils the objective at [objectiveIndex] for the first time. */
    data class ObjectiveCompleted(val objectiveIndex: Int, val output: String) : TerminalResult

    /** The command fulfils an objective that was already done: the same output is shown, the count does not change. */
    data class ObjectiveRepeated(val objectiveIndex: Int, val output: String) : TerminalResult

    /** Not one of the lab's commands. */
    data class Unrecognized(val message: String = UNRECOGNIZED_COMMAND_MESSAGE) : TerminalResult

    /** Nothing was typed (or only spaces): nothing is added to the transcript. */
    data object Empty : TerminalResult
}

/** A line group of the transcript: the typed text and what the terminal printed back. */
data class TerminalEntry(val input: String, val output: String, val recognized: Boolean)

/** The new terminal and the answer to the command that produced it. */
data class TerminalSubmission(val terminal: LabTerminal, val result: TerminalResult)

/**
 * The simulated terminal of one lab, as an immutable value (a `UiState` can hold it). It only recognises the lab's
 * commands from Figma; nothing is executed.
 *
 * Normalisation (SUPUESTO, Figma says nothing about it): spaces at the ends are dropped and runs of spaces become one;
 * the first word (the command name) ignores upper/lower case, because phone keyboards capitalise the first letter; the
 * arguments are case-sensitive, because in the passwords lab `Nightwatch2024` and `nightwatch2024` are different
 * passwords.
 *
 * Order (SUPUESTO): the objectives can be done in any order.
 */
data class LabTerminal(
    val lab: Lab,
    val completedObjectives: Set<Int> = emptySet(),
    val transcript: List<TerminalEntry> = emptyList(),
) {
    val completedCount: Int get() = completedObjectives.size

    val totalObjectives: Int get() = lab.objectives.size

    /** True with all the objectives done: the report is a success and the lab counts as completed (decision P6). */
    val isComplete: Boolean get() = completedCount == totalObjectives

    /** `round(100 x objectives / total)`: 0, 33, 67 or 100 for three objectives (decision Q1). Not stored. */
    val precisionPercent: Int get() = labPrecisionPercent(completedCount, totalObjectives)

    /**
     * The estimated time of the strongest password tried, from the real outputs of the terminal (decision Q2): the one of
     * the last objective done in the objectives order (weak < pattern < robust). Null when none was tried (the report
     * shows "—") and for every lab but the passwords one.
     */
    val strongestEstimatedTime: String?
        get() = completedObjectives.sortedDescending().firstNotNullOfOrNull { lab.objectives[it].estimatedTime }

    fun submit(input: String): TerminalSubmission {
        val typed = input.trim()
        if (typed.isEmpty()) return TerminalSubmission(this, TerminalResult.Empty)

        val normalized = normalizeCommand(typed)
        val index = lab.objectives.indexOfFirst { normalizeCommand(it.command) == normalized }
        if (index < 0) {
            val result = TerminalResult.Unrecognized()
            return TerminalSubmission(
                copy(transcript = transcript + TerminalEntry(typed, result.message, recognized = false)),
                result,
            )
        }

        val objective = lab.objectives[index]
        val entry = TerminalEntry(objective.command, objective.output, recognized = true)
        val result = if (index in completedObjectives) {
            TerminalResult.ObjectiveRepeated(index, objective.output)
        } else {
            TerminalResult.ObjectiveCompleted(index, objective.output)
        }
        return TerminalSubmission(
            copy(completedObjectives = completedObjectives + index, transcript = transcript + entry),
            result,
        )
    }
}

/** See [LabTerminal]: trims, collapses spaces and lower-cases only the first word. Empty for blank input. */
fun normalizeCommand(input: String): String {
    val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    return (listOf(words.first().lowercase(Locale.ROOT)) + words.drop(1)).joinToString(" ")
}

/** `round(100 x completed / total)`. Zero objectives give 0. */
fun labPrecisionPercent(completed: Int, total: Int): Int =
    if (total <= 0) 0 else Math.round(100.0 * completed / total).toInt()
