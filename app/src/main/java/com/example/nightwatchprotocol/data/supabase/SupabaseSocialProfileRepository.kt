package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.social.BattleHistoryEntry
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.FighterRepository
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import com.example.nightwatchprotocol.data.social.SocialProfileRepository
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.flatMap
import com.example.nightwatchprotocol.data.social.isValidAvatarKey
import com.example.nightwatchprotocol.data.social.isValidUsername
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.OffsetDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The three tables of `0008_social.sql` the app reads or writes directly (the rest are closed and only the functions touch them).
 * As in `SupabaseLearningProgressRepository`, no property has a default value on purpose (supabase-kt does not send properties
 * that equal their default) and the write rows carry ONLY the columns `authenticated` may write:
 *  - social_profile: INSERT (user_id, username) and UPDATE (hide_progress). `friend_code`, `created_at` and the username after
 *    creating it belong to the server.
 *  - battle_results and fighter_level_curve: read only.
 */

@Serializable
data class SocialProfileInsert(@SerialName("user_id") val userId: String, val username: String)

@Serializable
data class HideProgressUpdate(@SerialName("hide_progress") val hideProgress: Boolean)

@Serializable
data class SocialProfileRow(
    @SerialName("user_id") val userId: String? = null,
    val username: String,
    @SerialName("friend_code") val friendCode: String,
    @SerialName("hide_progress") val hideProgress: Boolean,
    // Default values: the columns exist only once `0010_fotos_social.sql` is applied, and the row is only ever read.
    @SerialName("avatar_key") val avatarKey: String? = null,
    @SerialName("avatar_version") val avatarVersion: Int? = null,
)

@Serializable
data class BattleResultRow(
    @SerialName("battle_id") val battleId: String,
    @SerialName("user_id") val userId: String? = null,
    val result: String,
    @SerialName("xp_awarded") val xpAwarded: Int,
    @SerialName("hp_left") val hpLeft: Int,
    @SerialName("opponent_username") val opponentUsername: String,
    @SerialName("finished_at") val finishedAt: String? = null,
)

@Serializable
data class LevelCurveRow(val level: Int, @SerialName("min_xp") val minXp: Int)

fun SocialProfileRow.toDomain(): SocialProfile =
    SocialProfile(username, friendCode, hideProgress, avatarKey?.takeIf(::isValidAvatarKey), avatarVersion?.takeIf { it >= 1 })

/** Own Social profile (table `social_profile`). The row is the session's: it is filtered by the session id, never by a screen's. */
class SupabaseSocialProfileRepository(
    private val postgrest: Postgrest,
    private val currentUserId: () -> String?,
) : SocialProfileRepository {

    override suspend fun getMyProfile(): SocialResult<SocialProfile?> {
        val userId = currentUserId() ?: return SocialResult.Failure(SocialError.NoSession)
        return socialCall {
            postgrest.from(PROFILE).select { filter { eq("user_id", userId) } }
                .decodeList<SocialProfileRow>().firstOrNull()?.toDomain()
        }
    }

    override suspend fun createProfile(username: String): SocialResult<SocialProfile> {
        val userId = currentUserId() ?: return SocialResult.Failure(SocialError.NoSession)
        val name = username.trim()
        if (!isValidUsername(name)) return SocialResult.Failure(SocialError.Rejected(SocialRejection.InvalidUsername))
        // 23505 (taken) and 23514 (invalid) come back from the server as classified rejections (see classifySocialFailure).
        val inserted = socialCall { postgrest.from(PROFILE).insert(SocialProfileInsert(userId, name)) }
        return inserted.flatMap {
            when (val profile = getMyProfile()) {
                is SocialResult.Success -> profile.value?.let { SocialResult.Success(it) } ?: SocialResult.Failure(SocialError.Unknown)
                is SocialResult.Failure -> profile
            }
        }
    }

    override suspend fun setHideProgress(hide: Boolean): SocialResult<Unit> {
        val userId = currentUserId() ?: return SocialResult.Failure(SocialError.NoSession)
        return socialCall {
            postgrest.from(PROFILE).update(HideProgressUpdate(hide)) { filter { eq("user_id", userId) } }
            Unit
        }
    }

    private companion object {
        const val PROFILE = "social_profile"
    }
}

private val serverInstant = { text: String? -> text?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } }

private val OUTCOME_NAMES = mapOf("win" to BattleOutcome.Win, "loss" to BattleOutcome.Loss, "draw" to BattleOutcome.Draw)

/** The most recent battles kept in [FighterSummary.history]; the score and the streak use all of them. */
const val FIGHTER_HISTORY_LIMIT = 50

/**
 * The summary of the person's battles from their own rows (newest first) and the XP curve. The server decided every result and
 * every XP: this only adds them up and reads the curve. Rows with an unknown result are dropped, like unknown topics elsewhere.
 * A draw neither breaks nor adds to the streak of wins (decision O2).
 */
fun buildFighterSummary(rowsNewestFirst: List<BattleResultRow>, curve: List<LevelCurveRow>): FighterSummary {
    val entries = rowsNewestFirst.mapNotNull { row ->
        val outcome = OUTCOME_NAMES[row.result] ?: return@mapNotNull null
        BattleHistoryEntry(row.battleId, outcome, row.xpAwarded, row.hpLeft, row.opponentUsername, serverInstant(row.finishedAt))
    }
    val xp = entries.sumOf { it.xpAwarded }
    val level = curve.filter { it.minXp <= xp }.maxOfOrNull { it.level } ?: 1
    val nextLevelXp = curve.filter { it.minXp > xp }.minOfOrNull { it.minXp }
    var streak = 0
    for (entry in entries) {
        when (entry.outcome) {
            BattleOutcome.Win -> streak++
            BattleOutcome.Draw -> Unit
            BattleOutcome.Loss -> break
        }
    }
    return FighterSummary(
        xp = xp,
        level = level,
        nextLevelXp = nextLevelXp,
        wins = entries.count { it.outcome == BattleOutcome.Win },
        losses = entries.count { it.outcome == BattleOutcome.Loss },
        draws = entries.count { it.outcome == BattleOutcome.Draw },
        battleStreak = streak,
        history = entries.take(FIGHTER_HISTORY_LIMIT),
    )
}

/** XP, level and score from `battle_results` (own rows only, by RLS) and `fighter_level_curve` (both read only). */
class SupabaseFighterRepository(
    private val postgrest: Postgrest,
    private val currentUserId: () -> String?,
) : FighterRepository {

    override suspend fun getSummary(): SocialResult<FighterSummary> {
        val userId = currentUserId() ?: return SocialResult.Failure(SocialError.NoSession)
        return socialCall {
            coroutineScope {
                val results = async {
                    postgrest.from(RESULTS).select {
                        filter { eq("user_id", userId) }
                        order("finished_at", Order.DESCENDING)
                    }.decodeList<BattleResultRow>()
                }
                val curve = async { postgrest.from(CURVE).select().decodeList<LevelCurveRow>() }
                buildFighterSummary(results.await(), curve.await())
            }
        }
    }

    private companion object {
        const val RESULTS = "battle_results"
        const val CURVE = "fighter_level_curve"
    }
}
