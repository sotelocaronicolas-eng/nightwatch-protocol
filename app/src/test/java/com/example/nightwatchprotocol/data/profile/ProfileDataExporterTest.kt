package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDataExporterTest {
    private val account = FakeAccountRepository(
        AccountProfile("Nicolás Sotelo", "nicolas@example.com", 1_785_000_000_000L, hasPassword = true),
    )
    private val profile = FakeProfileRepository()
    private val quiz = FakeQuizResultRepository()
    private val fixedNow = 1_790_000_000_000L // 2026-09-21T14:13:20Z

    private val extras = ProfileExtrasRepository(profile, DataStoreProfileExtrasCache.inMemory())

    private fun exporter() = ProfileDataExporter(account, profile, quiz, extras, clock = { fixedNow })

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun exportsEveryDocumentedField() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(recoveryEmail = "respaldo@example.com"))
        quiz.getResultResult = Result.success(
            PlacementResult(PlacementLevel.MEDIO, setOf(QuizCategory.entries.first())),
        )

        val json = parse(exporter().buildJson("uid-1"))

        assertEquals("Nicolás Sotelo", json.getValue("nombre").jsonPrimitive.content)
        assertEquals("nicolas@example.com", json.getValue("correo").jsonPrimitive.content)
        assertEquals("respaldo@example.com", json.getValue("correoDeRecuperacion").jsonPrimitive.content)
        assertTrue(json.getValue("miembroDesde").jsonPrimitive.content.isNotBlank())
        assertEquals("MEDIO", json.getValue("nivel").jsonPrimitive.content)
        assertEquals(
            listOf(QuizCategory.entries.first().name),
            json.getValue("categoriasDebiles").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("2026-09-21T14:13:20Z", json.getValue("fechaDeExportacion").jsonPrimitive.content)
    }

    @Test
    fun withoutQuizResultOrRecoveryEmail_exportsNotAssessedAndNulls() = runTest {
        val json = parse(exporter().buildJson("uid-1"))

        assertEquals("Sin evaluar", json.getValue("nivel").jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), json.getValue("categoriasDebiles"))
        assertEquals(JsonNull, json.getValue("correoDeRecuperacion"))
    }

    @Test
    fun aFailedFirestoreRead_doesNotBlockTheExport() = runTest {
        profile.getProfileResult = Result.failure(RuntimeException("sin red"))
        quiz.getResultResult = Result.failure(RuntimeException("sin red"))

        val json = parse(exporter().buildJson("uid-1"))

        assertEquals("nicolas@example.com", json.getValue("correo").jsonPrimitive.content)
        assertEquals("Sin evaluar", json.getValue("nivel").jsonPrimitive.content)
        assertEquals(JsonNull, json.getValue("correoDeRecuperacion"))
    }

    @Test
    fun genderAndBirthDate_areExported() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(gender = "female", birthDate = "1999-03-07"))

        val json = parse(exporter().buildJson("uid-1"))

        assertEquals("Femenino", json.getValue("genero").jsonPrimitive.content)
        assertEquals("1999-03-07", json.getValue("fechaDeNacimiento").jsonPrimitive.content)
    }

    @Test
    fun whenNotDefined_theyAreExportedAsNull() = runTest {
        val json = parse(exporter().buildJson("uid-1"))

        assertEquals(JsonNull, json.getValue("genero"))
        assertEquals(JsonNull, json.getValue("fechaDeNacimiento"))
    }

    @Test
    fun theyAreExportedFromTheLocalCopyWhenFirestoreFails() = runTest {
        profile.getProfileResult = Result.failure(RuntimeException("sin red"))
        extras.save("uid-1", ProfileExtras(Gender.OTHER, BirthDate(1985, 5, 5)))
        profile.saveExtrasResult = Result.success(Unit)

        val json = parse(exporter().buildJson("uid-1"))

        assertEquals("Otro", json.getValue("genero").jsonPrimitive.content)
        assertEquals("1985-05-05", json.getValue("fechaDeNacimiento").jsonPrimitive.content)
    }

    @Test
    fun missingDisplayName_isExportedAsNull() = runTest {
        account.profile = AccountProfile(null, "solo@example.com", null, hasPassword = true)

        val json = parse(exporter().buildJson("uid-1"))

        assertEquals(JsonNull, json.getValue("nombre"))
        assertEquals(JsonNull, json.getValue("miembroDesde"))
        assertEquals(JsonPrimitive("solo@example.com"), json.getValue("correo"))
    }
}
