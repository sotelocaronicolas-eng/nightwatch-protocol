package com.example.nightwatchprotocol.data.quiz

import com.example.nightwatchprotocol.data.supabase.requireSessionUserId
import com.example.nightwatchprotocol.data.supabase.supabaseCall
import io.github.jan.supabase.postgrest.Postgrest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A row of `public.placement`. There are no default values on purpose: supabase-kt does not send properties that
 * equal their default, and an empty `weak_categories` must still overwrite the previous list.
 */
@Serializable
data class PlacementRow(
    @SerialName("user_id") val userId: String,
    val level: String,
    @SerialName("weak_categories") val weakCategories: List<String>,
    @SerialName("completed_at") val completedAt: String? = null,
)

/** Enum names are stored as they are (`.name`); the table's CHECK constraints accept exactly these values. */
fun PlacementResult.toRow(userId: String, completedAt: Instant): PlacementRow = PlacementRow(
    userId = userId,
    level = level.name,
    weakCategories = weakCategories.map { it.name },
    completedAt = completedAt.toString(),
)

/** Null for an unknown level; unknown categories are dropped, like the Firestore repository does. */
fun PlacementRow.toDomain(): PlacementResult? {
    val placementLevel = PlacementLevel.entries.firstOrNull { it.name == level } ?: return null
    val categories = weakCategories.mapNotNull { name -> QuizCategory.entries.firstOrNull { it.name == name } }.toSet()
    return PlacementResult(placementLevel, categories)
}

class SupabasePlacementRepository(
    private val postgrest: Postgrest,
    private val currentUserId: () -> String?,
    private val clock: () -> Instant = Instant::now,
) : QuizResultRepository {

    // The uid argument is ignored: the row is always the session user's.
    override suspend fun getResult(uid: String): PlacementResult? {
        val userId = requireSessionUserId(currentUserId)
        val rows = supabaseCall {
            postgrest.from(TABLE).select { filter { eq("user_id", userId) } }.decodeList<PlacementRow>()
        }
        return rows.firstOrNull()?.toDomain()
    }

    override suspend fun saveResult(uid: String, result: PlacementResult) {
        val row = result.toRow(requireSessionUserId(currentUserId), clock())
        supabaseCall { postgrest.from(TABLE).upsert(row) }
    }

    private companion object {
        const val TABLE = "placement"
    }
}
