package com.example.nightwatchprotocol.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabTerminalTest {
    private val content = realLearningContent()

    private fun terminal(topic: LearningTopic) = LabTerminal(content.lab(topic))

    @Test
    fun everyObjectiveOfEveryLab_isRecognised_withTheOutputOfFigma() {
        content.labs.forEach { lab ->
            lab.objectives.forEachIndexed { index, objective ->
                val submission = LabTerminal(lab).submit(objective.command)

                assertEquals(
                    "${lab.topic} ${objective.command}",
                    TerminalResult.ObjectiveCompleted(index, objective.output),
                    submission.result,
                )
                assertEquals(setOf(index), submission.terminal.completedObjectives)
            }
        }
    }

    @Test
    fun doingTheThreeObjectives_completesTheLab_inAnyOrder() {
        content.labs.forEach { lab ->
            listOf(listOf(0, 1, 2), listOf(2, 1, 0), listOf(1, 2, 0)).forEach { order ->
                var terminal = LabTerminal(lab)
                order.forEach { terminal = terminal.submit(lab.objectives[it].command).terminal }

                assertTrue("${lab.topic} $order", terminal.isComplete)
                assertEquals(3, terminal.completedCount)
                assertEquals(100, terminal.precisionPercent)
            }
        }
    }

    @Test
    fun aNewTerminal_hasNothingDone() {
        val terminal = terminal(LearningTopic.FIREWALL)

        assertEquals(0, terminal.completedCount)
        assertFalse(terminal.isComplete)
        assertEquals(0, terminal.precisionPercent)
        assertTrue(terminal.transcript.isEmpty())
    }

    @Test
    fun unknownCommand_isNotRecognised_addsAnEntry_andDoesNotCount() {
        val submission = terminal(LearningTopic.REDES_SEGURAS).submit("rm -rf /")

        assertEquals(TerminalResult.Unrecognized(), submission.result)
        assertEquals(UNRECOGNIZED_COMMAND_MESSAGE, (submission.result as TerminalResult.Unrecognized).message)
        assertEquals(0, submission.terminal.completedCount)
        assertEquals(listOf(TerminalEntry("rm -rf /", UNRECOGNIZED_COMMAND_MESSAGE, recognized = false)), submission.terminal.transcript)
    }

    @Test
    fun aCommandOfAnotherLab_isNotRecognised() {
        val submission = terminal(LearningTopic.FIREWALL).submit("crack 123456")

        assertTrue(submission.result is TerminalResult.Unrecognized)
        assertEquals(0, submission.terminal.completedCount)
    }

    @Test
    fun repeatedCommand_showsTheOutputAgain_butTheCountDoesNotChange() {
        val lab = content.lab(LearningTopic.MALWARE)
        val first = LabTerminal(lab).submit("scan sospechoso.exe")
        val second = first.terminal.submit("scan sospechoso.exe")

        assertEquals(TerminalResult.ObjectiveRepeated(0, lab.objectives[0].output), second.result)
        assertEquals(1, second.terminal.completedCount)
        assertEquals(2, second.terminal.transcript.size)
        assertEquals(first.terminal.transcript[0], second.terminal.transcript[1])
    }

    @Test
    fun normalisation_ignoresSpacesAtTheEnds_andRunsOfSpaces() {
        val terminal = terminal(LearningTopic.FIREWALL)

        val result = terminal.submit("   ufw   deny  203.0.113.77   port 22  ").result

        assertEquals(TerminalResult.ObjectiveCompleted(0, terminal.lab.objectives[0].output), result)
        assertEquals("ufw deny 203.0.113.77 port 22", normalizeCommand("  ufw \t deny 203.0.113.77  port 22 "))
    }

    @Test
    fun normalisation_commandNameIgnoresCase_soAPhoneKeyboardCapitalisingIsFine() {
        val terminal = terminal(LearningTopic.CONTRASENAS)

        assertTrue(terminal.submit("Crack 123456").result is TerminalResult.ObjectiveCompleted)
        assertTrue(terminal.submit("CRACK 123456").result is TerminalResult.ObjectiveCompleted)
        assertTrue(terminal(LearningTopic.FIREWALL).submit("IPTABLES -L INPUT").result is TerminalResult.ObjectiveCompleted)
    }

    @Test
    fun normalisation_argumentsAreCaseSensitive() {
        val terminal = terminal(LearningTopic.CONTRASENAS)

        assertTrue(terminal.submit("crack nightwatch2024").result is TerminalResult.Unrecognized)
        assertTrue(terminal.submit("crack NIGHTWATCH2024").result is TerminalResult.Unrecognized)
        assertTrue(terminal.submit("crack N!ghtW4tch#7kQ2").result is TerminalResult.ObjectiveCompleted)
        assertTrue(terminal.submit("crack n!ghtw4tch#7kq2").result is TerminalResult.Unrecognized)
        assertTrue(terminal(LearningTopic.FIREWALL).submit("iptables -l INPUT").result is TerminalResult.Unrecognized)
    }

    @Test
    fun blankInput_isEmpty_andChangesNothing() {
        val terminal = terminal(LearningTopic.PHISHING)

        listOf("", "   ", "\t\n").forEach { blank ->
            val submission = terminal.submit(blank)
            assertEquals(TerminalResult.Empty, submission.result)
            assertTrue(submission.terminal === terminal)
        }
    }

    @Test
    fun theTranscript_echoesTheCanonicalCommand_ofARecognisedCommand() {
        val lab = content.lab(LearningTopic.PHISHING)
        val submission = LabTerminal(lab).submit("  WHOIS   nightwatch-secure-verify.com ")

        assertEquals(
            listOf(TerminalEntry("whois nightwatch-secure-verify.com", lab.objectives[0].output, recognized = true)),
            submission.terminal.transcript,
        )
    }

    @Test
    fun precision_isRoundedFromThreeObjectives() {
        assertEquals(0, labPrecisionPercent(0, 3))
        assertEquals(33, labPrecisionPercent(1, 3))
        assertEquals(67, labPrecisionPercent(2, 3))
        assertEquals(100, labPrecisionPercent(3, 3))
        assertEquals(0, labPrecisionPercent(0, 0))

        val lab = content.lab(LearningTopic.REDES_SEGURAS)
        val one = LabTerminal(lab).submit(lab.objectives[0].command).terminal
        val two = one.submit(lab.objectives[1].command).terminal
        assertEquals(33, one.precisionPercent)
        assertEquals(67, two.precisionPercent)
        assertFalse(two.isComplete)
    }

    @Test
    fun strongestEstimatedTime_isNull_untilAPasswordIsTried() {
        assertNull(terminal(LearningTopic.CONTRASENAS).strongestEstimatedTime)
    }

    @Test
    fun strongestEstimatedTime_isTheOneOfTheStrongestPasswordTried_whateverTheOrder() {
        val lab = content.lab(LearningTopic.CONTRASENAS)
        val weak = LabTerminal(lab).submit("crack 123456").terminal
        assertEquals("menos de 1 segundo", weak.strongestEstimatedTime)

        val pattern = weak.submit("crack Nightwatch2024").terminal
        assertEquals("4 horas", pattern.strongestEstimatedTime)

        val robustFirst = LabTerminal(lab).submit("crack N!ghtW4tch#7kQ2").terminal.submit("crack 123456").terminal
        assertEquals("300 años", robustFirst.strongestEstimatedTime)
    }

    @Test
    fun strongestEstimatedTime_isNullInEveryOtherLab() {
        content.labs.filter { it.topic != LearningTopic.CONTRASENAS }.forEach { lab ->
            var terminal = LabTerminal(lab)
            lab.objectives.forEach { terminal = terminal.submit(it.command).terminal }
            assertNull(lab.topic.name, terminal.strongestEstimatedTime)
        }
    }

    @Test
    fun anUnrecognisedCommand_doesNotChangeTheProgressMadeSoFar() {
        val lab = content.lab(LearningTopic.INGENIERIA_SOCIAL)
        val done = LabTerminal(lab).submit(lab.objectives[0].command).terminal

        val after = done.submit("hola").terminal

        assertEquals(done.completedObjectives, after.completedObjectives)
        assertEquals(2, after.transcript.size)
    }
}
