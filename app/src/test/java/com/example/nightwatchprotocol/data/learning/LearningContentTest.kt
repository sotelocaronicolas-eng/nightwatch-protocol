package com.example.nightwatchprotocol.data.learning

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled JSON, read from the source tree: what the tests check is what ships in the APK. */
class LearningContentTest {
    private val content = realLearningContent()

    private val figmaTopicsInOrder = listOf(
        LearningTopic.CONTRASENAS,
        LearningTopic.PHISHING,
        LearningTopic.MALWARE,
        LearningTopic.INGENIERIA_SOCIAL,
        LearningTopic.REDES_SEGURAS,
        LearningTopic.FIREWALL,
    )

    @Test
    fun topics_areTheSixOfFigma_inFigmaOrder() {
        assertEquals(6, LearningTopic.entries.size)
        assertEquals(figmaTopicsInOrder, LearningTopic.entries)
    }

    @Test
    fun fromName_roundTripsEveryTopic_andRejectsUnknownNames() {
        LearningTopic.entries.forEach { assertEquals(it, LearningTopic.fromName(it.name)) }
        assertNull(LearningTopic.fromName("REDES"))
        assertNull(LearningTopic.fromName(""))
    }

    @Test
    fun lessons_areSix_oneCardTriplePerTopic_inOrder() {
        assertEquals(figmaTopicsInOrder, content.lessons.map { it.topic })
        content.lessons.forEach { lesson ->
            assertEquals(lesson.topic.name, listOf("¿Qué es?", "¿Por qué importa?", "¿Cómo protegerte?"), lesson.cards.map { it.title })
            assertEquals(lesson.topic.name, listOf("🧭", "⚠️", "🛡️"), lesson.cards.map { it.emoji })
        }
    }

    @Test
    fun lessons_haveTheFigmaTitlesAndIslandNames() {
        assertEquals(
            listOf(
                "Contraseñas y Autenticación",
                "Phishing",
                "Malware",
                "Ingeniería Social",
                "Islas de Redes Seguras",
                "Fortaleza del Firewall",
            ),
            content.lessons.map { it.title },
        )
        assertEquals(
            listOf(
                "Isla de las Contraseñas",
                "Bahía del Phishing",
                "Arrecife del Malware",
                "Cueva del Engaño Social",
                "Islas de Redes Seguras",
                "Fortaleza del Firewall",
            ),
            content.lessons.map { it.islandName },
        )
    }

    @Test
    fun labs_areSix_withThreeObjectivesEach_inOrder() {
        assertEquals(figmaTopicsInOrder, content.labs.map { it.topic })
        content.labs.forEach { lab -> assertEquals(lab.topic.name, 3, lab.objectives.size) }
    }

    @Test
    fun labs_haveTheFigmaCardTitles_terminalTitles_andDifficulties() {
        assertEquals(
            listOf(
                "Fuerza de Contraseñas",
                "Detecta el Correo Falso",
                "Analiza el Archivo Sospechoso",
                "Simulación de Llamada",
                "Seguridad en Redes",
                "Configura tu Firewall",
            ),
            content.labs.map { it.cardTitle },
        )
        assertEquals(
            listOf(
                "Fuerza de Contraseñas",
                "Correo Sospechoso",
                "Archivo Sospechoso",
                "Simulación de Llamada",
                "Seguridad en Redes",
                "Configura tu Firewall",
            ),
            content.labs.map { it.terminalTitle },
        )
        assertEquals(
            listOf(
                LabDifficulty.FACIL,
                LabDifficulty.FACIL,
                LabDifficulty.MEDIO,
                LabDifficulty.MEDIO,
                LabDifficulty.DIFICIL,
                LabDifficulty.DIFICIL,
            ),
            content.labs.map { it.difficulty },
        )
    }

    @Test
    fun labs_commandsOfFigma_areExactlyThese() {
        assertEquals(
            listOf("crack 123456", "crack Nightwatch2024", "crack N!ghtW4tch#7kQ2"),
            content.lab(LearningTopic.CONTRASENAS).objectives.map { it.command },
        )
        assertEquals(
            listOf("whois nightwatch-secure-verify.com", "curl -I hxxp://nw-verify.io", "diff nightwatch-verify.io nw.app"),
            content.lab(LearningTopic.PHISHING).objectives.map { it.command },
        )
        assertEquals(
            listOf("scan sospechoso.exe", "ps aux | grep sospechoso", "quarantine sospechoso.exe"),
            content.lab(LearningTopic.MALWARE).objectives.map { it.command },
        )
        assertEquals(
            listOf("lookup +1-555-0134", "check-log usuario47", "report-incident social-engineering"),
            content.lab(LearningTopic.INGENIERIA_SOCIAL).objectives.map { it.command },
        )
        assertEquals(
            listOf("cat access.log", "grep failed access.log", "whois 203.0.113.55"),
            content.lab(LearningTopic.REDES_SEGURAS).objectives.map { it.command },
        )
        assertEquals(
            listOf("ufw deny 203.0.113.77 port 22", "ufw deny 198.51.100.23 port 3389", "iptables -L INPUT"),
            content.lab(LearningTopic.FIREWALL).objectives.map { it.command },
        )
    }

    @Test
    fun labs_networksCatOutput_hasTwoLines_andTheRestOneLine() {
        val networks = content.lab(LearningTopic.REDES_SEGURAS)
        assertEquals(
            listOf("203.0.113.55 - - \"GET /login\" 401", "203.0.113.55 - - \"GET /admin\" 403"),
            networks.objectives[0].output.split("\n"),
        )
        content.labs.flatMap { lab -> lab.objectives.map { lab.topic to it } }
            .filterNot { (topic, objective) -> topic == LearningTopic.REDES_SEGURAS && objective.command == "cat access.log" }
            .forEach { (topic, objective) -> assertFalse("$topic ${objective.command}", "\n" in objective.output) }
    }

    @Test
    fun labs_estimatedTime_existsOnlyInThePasswordsLab() {
        assertEquals(
            listOf("menos de 1 segundo", "4 horas", "300 años"),
            content.lab(LearningTopic.CONTRASENAS).objectives.map { it.estimatedTime },
        )
        content.labs.filter { it.topic != LearningTopic.CONTRASENAS }.forEach { lab ->
            lab.objectives.forEach { assertNull("${lab.topic} ${it.command}", it.estimatedTime) }
        }
        // The estimated time is the one the output says.
        content.lab(LearningTopic.CONTRASENAS).objectives.forEach {
            assertTrue(it.output, "Tiempo estimado: ${it.estimatedTime}" in it.output)
        }
    }

    @Test
    fun labs_commandsAreUniqueInsideEachLab() {
        content.labs.forEach { lab ->
            assertEquals(lab.topic.name, lab.objectives.size, lab.objectives.map { normalizeCommand(it.command) }.toSet().size)
        }
    }

    @Test
    fun exam_isSixQuestions_oneForEachTopic_inOrder_withFourOptions() {
        assertEquals(EXAM_QUESTION_COUNT, content.exam.size)
        assertEquals(figmaTopicsInOrder, content.exam.map { it.topic })
        content.exam.forEach { question ->
            assertEquals(question.text, 4, question.options.size)
            assertTrue(question.text, question.correctIndex in 0..3)
            assertEquals("Las opciones no repiten texto: ${question.text}", 4, question.options.toSet().size)
        }
    }

    @Test
    fun exam_correctAnswers_areTheOnesVerifiedByTheGroup() {
        // P1 = C, P2 = B, P3 = C, P4 = B, P5 = B, P6 = B (decision P10; Figma 2498:512 to 2498:572).
        assertEquals(listOf(2, 1, 2, 1, 1, 1), content.exam.map { it.correctIndex })
    }

    @Test
    fun exam_optionsHaveNoLetterPrefix_andTheLabelAddsIt() {
        content.exam.forEach { question ->
            question.options.forEach { assertFalse(it, Regex("^[A-D]\\) ").containsMatchIn(it)) }
        }
        val first = content.exam.first()
        assertEquals("C) k7#mQ!9zRt$2", first.optionLabel(first.correctIndex))
    }

    @Test
    fun exam_topicLabels_areTheFigmaOnes() {
        assertEquals(
            listOf("Contraseñas y Autenticación", "Phishing", "Malware", "Ingeniería Social", "Redes Seguras", "Firewall"),
            content.exam.map { it.topicLabel },
        )
    }

    @Test
    fun noTextIsBlank_andEveryElementNamesItsFigmaSource() {
        content.lessons.forEach { lesson ->
            assertNotBlank(lesson.title, lesson.islandName, lesson.emoji, lesson.source)
            lesson.cards.forEach { assertNotBlank(it.emoji, it.title, it.text) }
            assertTrue(lesson.source, "Figma" in lesson.source && ":" in lesson.source)
        }
        content.labs.forEach { lab ->
            assertNotBlank(lab.cardTitle, lab.terminalTitle, lab.emoji, lab.briefing, lab.source)
            assertNotBlank(lab.report.successTitle, lab.report.successSummary, lab.report.failureSummary, lab.report.metricLabel)
            lab.objectives.forEach { assertNotBlank(it.title, it.command, it.output, it.reportLabel) }
            assertTrue(lab.source, "Figma" in lab.source && ":" in lab.source)
        }
        content.exam.forEach { question ->
            assertNotBlank(question.topicLabel, question.emoji, question.text, question.source)
            question.options.forEach { assertNotBlank(it) }
            assertTrue(question.source, "Figma" in question.source && ":" in question.source)
        }
    }

    @Test
    fun repository_readsEachAssetOnce_andKeepsTheContentInMemory() = runTest {
        val reads = mutableListOf<String>()
        val repository = LocalLearningContentRepository { name ->
            reads += name
            LearningAssets.read(name)
        }

        val first = repository.getContent()
        val second = repository.getContent()

        assertEquals(listOf(LESSONS_ASSET, LABS_ASSET, EXAM_ASSET).sorted(), reads.sorted())
        assertTrue(first === second)
    }

    private fun assertNotBlank(vararg texts: String) {
        texts.forEach { assertTrue("Texto vacío", it.isNotBlank()) }
    }
}
