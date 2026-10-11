package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val READ_TIMEOUT_MILLIS = 5_000L

/** Where the exported file goes. [destination] is the document the user picked (a content URI as text). */
interface DataExportWriter {
    /** Throws when the destination cannot be written. */
    suspend fun write(destination: String, content: String)
}

/**
 * Builds the "Descargar mis datos" file: name, email, recovery email, member since, level and weak
 * categories, plus the export date. A value that cannot be read (offline, no quiz taken) is exported as null
 * rather than blocking the export.
 */
class ProfileDataExporter(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val quizResultRepository: QuizResultRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { prettyPrint = true }

    suspend fun buildJson(uid: String): String {
        val account = accountRepository.currentProfile()
        val recoveryEmail = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
            runCatchingKeepCancellation { profileRepository.getProfile(uid) }.getOrNull()
        }?.recoveryEmail?.trim()?.takeIf { it.isNotEmpty() }
        val quizResult = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
            runCatchingKeepCancellation { quizResultRepository.getResult(uid) }.getOrNull()
        }

        val document: JsonObject = buildJsonObject {
            put("nombre", account?.displayName)
            put("correo", account?.email)
            put("correoDeRecuperacion", recoveryEmail)
            put("miembroDesde", account?.creationTimeMillis?.let { memberSinceLabel(it) })
            put("nivel", levelLabel(quizResult))
            put(
                "categoriasDebiles",
                buildJsonArray { quizResult?.weakCategories?.forEach { add(JsonPrimitive(it.name)) } },
            )
            put("fechaDeExportacion", isoUtc(clock()))
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
}
