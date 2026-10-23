package com.example.nightwatchprotocol.data.social

import com.example.nightwatchprotocol.data.learning.LearningTopic
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/*
 * Reading the answers of the Social functions (`0008_social.sql`) and building what the app sends. Pure functions on JSON:
 * they know nothing about Supabase, so every shape is tested without a network.
 *
 * A business result is `{"status":"error","code":"..."}` and becomes a [SocialError]; a body the app cannot read (a missing
 * field, an unknown word, a bad date) is [SocialError.Unknown], never an exception.
 */

/** The names of the five public functions of `0008_social.sql`. */
const val SOCIAL_FRIENDS_RPC = "social_friends"
const val SOCIAL_PAIRS_RPC = "social_pairs"
const val BATTLE_LOBBY_RPC = "battle_lobby"
const val BATTLE_SYNC_RPC = "battle_sync"
const val BATTLE_PLAY_RPC = "battle_play"

// ---- what is sent: the named parameters of each function (never a user id) ----

/** `social_friends`, `social_pairs` and `battle_lobby` take `p_action` and an optional `p_arg`. */
fun actionParams(action: String, arg: String? = null): JsonObject = buildJsonObject {
    put("p_action", action)
    if (arg != null) put("p_arg", arg)
}

fun syncParams(battleId: String): JsonObject = buildJsonObject { put("p_battle", battleId) }

/** `battle_play(p_battle, p_action, p_turn, p_arg)`: the turn and the argument only go when the action uses them. */
fun playParams(battleId: String, action: String, turnNumber: Int? = null, arg: String? = null): JsonObject = buildJsonObject {
    put("p_battle", battleId)
    put("p_action", action)
    if (turnNumber != null) put("p_turn", turnNumber)
    if (arg != null) put("p_arg", arg)
}

// ---- the envelope ----

internal class MalformedResponse(message: String) : Exception(message)

/** Business error first; otherwise [block] reads the body, and any problem reading it is [SocialError.Unknown]. */
internal inline fun <T> parseResponse(response: JsonElement, block: (JsonObject) -> T): SocialResult<T> {
    val body = response as? JsonObject ?: return SocialResult.Failure(SocialError.Unknown)
    if ((body["status"] as? JsonPrimitive)?.contentOrNull == "error") {
        return SocialResult.Failure(socialErrorFromCode((body["code"] as? JsonPrimitive)?.contentOrNull))
    }
    return try {
        SocialResult.Success(block(body))
    } catch (e: Exception) {
        SocialResult.Failure(SocialError.Unknown)
    }
}

/** An action that only says what it did: `{"status":"sent"}`. Anything else is not what was asked for. */
fun parseAck(response: JsonElement, expectedStatus: String): SocialResult<Unit> = parseResponse(response) { body ->
    if (body.str("status") != expectedStatus) throw MalformedResponse("estado inesperado")
    Unit
}

private fun JsonObject.field(key: String): JsonElement = this[key] ?: throw MalformedResponse("falta $key")

private fun JsonObject.str(key: String): String =
    (field(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw MalformedResponse("$key no es texto")

private fun JsonObject.strOrNull(key: String): String? {
    val element = this[key]
    if (element == null || element is JsonNull) return null
    return (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw MalformedResponse("$key no es texto")
}

private fun JsonObject.int(key: String): Int =
    (field(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: throw MalformedResponse("$key no es un entero")

private fun JsonObject.double(key: String): Double =
    (field(key) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: throw MalformedResponse("$key no es un número")

private fun JsonObject.bool(key: String): Boolean =
    (field(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw MalformedResponse("$key no es verdadero o falso")

private fun JsonObject.obj(key: String): JsonObject = field(key) as? JsonObject ?: throw MalformedResponse("$key no es un objeto")

private fun JsonObject.objOrNull(key: String): JsonObject? {
    val element = this[key]
    if (element == null || element is JsonNull) return null
    return element as? JsonObject ?: throw MalformedResponse("$key no es un objeto")
}

private fun JsonObject.arr(key: String): JsonArray = field(key) as? JsonArray ?: throw MalformedResponse("$key no es una lista")

/**
 * The key of a person's photo, or null when the server does not send it (before `0010`), sends null or sends something that is
 * not a UUID: the key becomes a file name, so only that shape gets through. Never throws: a photo is decoration.
 */
private fun JsonObject.photoKey(): String? =
    (this["avatar_key"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(::isValidAvatarKey)

/** The version of the photo (1 or more), or null when there is no photo or the server does not send it. */
private fun JsonObject.photoVersion(): Int? =
    (this["avatar_version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it >= 1 }

private fun JsonArray.strings(): List<String> = map {
    (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw MalformedResponse("elemento no es texto")
}

private fun JsonArray.objects(): List<JsonObject> = map { it as? JsonObject ?: throw MalformedResponse("elemento no es un objeto") }

private fun <T> String.word(table: Map<String, T>): T = table[this] ?: throw MalformedResponse("valor desconocido: $this")

/** A timestamptz as the server writes it (`2026-10-21T15:30:00.123456+00:00`); null if it cannot be read. */
private fun instantOrNull(text: String?): Instant? = text?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

private val OUTCOMES = mapOf("win" to BattleOutcome.Win, "loss" to BattleOutcome.Loss, "draw" to BattleOutcome.Draw)

private val ATTEMPT_RESULTS = mapOf(
    "pending" to UltiAttemptResult.Pending,
    "solved" to UltiAttemptResult.Solved,
    "failed" to UltiAttemptResult.Failed,
)

private val OPTION_LETTERS = listOf('A', 'B', 'C', 'D')

// ---- friends ----

fun parseFriends(response: JsonElement): SocialResult<List<Friend>> = parseResponse(response) { body ->
    body.arr("friends").objects().map {
        Friend(it.str("username"), it.str("friend_code"), it.int("streak"), it.bool("trained_today"), it.photoKey(), it.photoVersion())
    }
}

private fun JsonArray.people(): List<PersonRef> =
    objects().map { PersonRef(it.str("username"), it.str("friend_code"), it.photoKey(), it.photoVersion()) }

fun parseFriendRequests(response: JsonElement): SocialResult<FriendRequests> = parseResponse(response) { body ->
    FriendRequests(received = body.arr("received").people(), sent = body.arr("sent").people())
}

/** `{"blocked":[{username, friend_code, avatar_key, avatar_version}, ...]}` (0011): the people the person blocked. */
fun parseBlocks(response: JsonElement): SocialResult<List<PersonRef>> = parseResponse(response) { body ->
    body.arr("blocked").people()
}

/**
 * The argument of the `report` action: `target|reason|note`, three parts always (the note may be empty, and it may contain `|`:
 * the server takes everything after the second bar). Neither a username nor a friend code can contain a bar.
 */
fun reportArg(target: String, reason: ReportReason, note: String): String = "${target.trim()}|${reason.code}|${note.trim()}"

private val RELATIONS = mapOf(
    "none" to FriendRelation.None,
    "sent" to FriendRelation.Sent,
    "received" to FriendRelation.Received,
    "friends" to FriendRelation.Friends,
)

fun parseFriendSearch(response: JsonElement): SocialResult<FriendSearchHit> = parseResponse(response) { body ->
    if (!body.bool("found")) throw MalformedResponse("sin resultado")
    FriendSearchHit(
        username = body.str("username"),
        friendCode = body.str("friend_code"),
        relation = body.str("relation").word(RELATIONS),
        avatarKey = body.photoKey(),
        avatarVersion = body.photoVersion(),
    )
}

/** Topics this version does not know (a newer server) are dropped, like in the progress of the learning path. */
private fun JsonArray.topics(): Set<LearningTopic> = strings().mapNotNull { LearningTopic.fromName(it) }.toSet()

fun parseFriendProfile(response: JsonElement): SocialResult<FriendProfile> = parseResponse(response) { body ->
    FriendProfile(
        username = body.str("username"),
        friendCode = body.str("friend_code"),
        streakDays = body.int("streak"),
        trainedToday = body.bool("trained_today"),
        hidden = body.bool("hidden"),
        progress = body.objOrNull("progress")?.let {
            FriendProgress(
                viewedLessons = it.arr("viewed_lessons").topics(),
                completedLabs = it.arr("completed_labs").topics(),
                examPassed = it.bool("exam_passed"),
                examPerfect = it.bool("exam_perfect"),
                bestStreak = it.int("best_streak"),
                fighterLevel = it.int("fighter_level"),
            )
        },
        history = body.arr("history").objects().map {
            FriendBattleEntry(it.str("result").word(OUTCOMES), instantOrNull(it.strOrNull("finished_at")))
        },
        avatarKey = body.photoKey(),
        avatarVersion = body.photoVersion(),
    )
}

// ---- pair streaks ----

private val PAIR_STATUSES = mapOf(
    "active" to PairStatus.Active,
    "pending_sent" to PairStatus.PendingSent,
    "pending_received" to PairStatus.PendingReceived,
)

private val PAIR_STATES = mapOf(
    "active" to PairState.Active,
    "at_risk" to PairState.AtRisk,
    "none" to PairState.None,
    "route_complete" to PairState.RouteComplete,
)

private fun pairFrom(item: JsonObject): PairStreak = PairStreak(
    username = item.str("username"),
    friendCode = item.str("friend_code"),
    status = item.str("status").word(PAIR_STATUSES),
    expiresAt = instantOrNull(item.strOrNull("expires_at")),
    metrics = item.objOrNull("metrics")?.let {
        PairMetrics(
            currentDays = it.int("current"),
            bestDays = it.int("best"),
            meDoneToday = it.bool("me_today"),
            partnerDoneToday = it.bool("partner_today"),
            state = it.str("state").word(PAIR_STATES),
            sharedDays = it.arr("shared_days").strings().map { day -> LocalDate.parse(day) },
        )
    },
    avatarKey = item.photoKey(),
    avatarVersion = item.photoVersion(),
)

fun parsePairOverview(response: JsonElement): SocialResult<PairStreakOverview> = parseResponse(response) { body ->
    PairStreakOverview(pairs = body.arr("pairs").objects().map(::pairFrom), topCurrentDays = body.int("top_current"))
}

fun parsePairDetail(response: JsonElement): SocialResult<PairStreak> = parseResponse(response) { body -> pairFrom(body.obj("pair")) }

// ---- lobby ----

/** `matched`, `in_battle`, `waiting`, `cancelled` and `none` are the answers of the quick match actions. */
fun parseQuickMatch(response: JsonElement): SocialResult<QuickMatchStatus> = parseResponse(response) { body ->
    when (body.str("status")) {
        "matched" -> QuickMatchStatus.Matched(body.str("battle_id"))
        "in_battle" -> QuickMatchStatus.InBattle(body.str("battle_id"))
        "waiting" -> QuickMatchStatus.Waiting(body.int("waited_seconds"), body.bool("suggest_friend"))
        "cancelled" -> QuickMatchStatus.Cancelled
        "none" -> QuickMatchStatus.Idle
        else -> throw MalformedResponse("estado desconocido")
    }
}

/** The id of the battle that accepting a challenge creates. */
fun parseMatchedBattleId(response: JsonElement): SocialResult<String> = parseResponse(response) { body ->
    if (body.str("status") != "matched") throw MalformedResponse("estado inesperado")
    body.str("battle_id")
}

private val CHALLENGE_STATUSES = mapOf(
    "pending" to ChallengeStatus.Pending,
    "accepted" to ChallengeStatus.Accepted,
    "declined" to ChallengeStatus.Declined,
    "cancelled" to ChallengeStatus.Cancelled,
    "expired" to ChallengeStatus.Expired,
)

fun parseChallenges(response: JsonElement, clock: MonotonicClock): SocialResult<ChallengesView> = parseResponse(response) { body ->
    val receivedAt = clock.nanoTime()
    ChallengesView(
        activeBattleId = body.strOrNull("active_battle_id"),
        received = body.arr("received").objects().map {
            ReceivedChallenge(
                username = it.str("username"),
                friendCode = it.str("friend_code"),
                timeLeft = ServerCountdown(it.double("seconds_left"), receivedAt),
                avatarKey = it.photoKey(),
                avatarVersion = it.photoVersion(),
            )
        },
        sent = body.objOrNull("sent")?.let {
            SentChallenge(
                username = it.str("username"),
                friendCode = it.str("friend_code"),
                status = it.str("status").word(CHALLENGE_STATUSES),
                timeLeft = ServerCountdown(it.double("seconds_left"), receivedAt),
                battleId = it.strOrNull("battle_id"),
                avatarKey = it.photoKey(),
                avatarVersion = it.photoVersion(),
            )
        },
    )
}

// ---- battle ----

private val BATTLE_MODES = mapOf("quick" to BattleMode.Quick, "friend" to BattleMode.Friend)

private val BATTLE_STATUSES = mapOf(
    "active" to BattleStatus.Active,
    "finished" to BattleStatus.Finished,
    "cancelled" to BattleStatus.Cancelled,
)

private val TURN_PHASES = mapOf("question" to TurnPhase.Question, "ulti" to TurnPhase.Ulti)

private val ULTI_ROLES = mapOf("attacker" to UltiRole.Attacker, "defender" to UltiRole.Defender)

private val TURN_OUTCOMES = mapOf(
    "correct" to TurnOutcome.Correct,
    "incorrect" to TurnOutcome.Incorrect,
    "timeout" to TurnOutcome.Timeout,
    "ulti" to TurnOutcome.Ulti,
)

private val END_REASONS = mapOf(
    "hp_zero" to BattleEndReason.HpZero,
    "turn_limit" to BattleEndReason.TurnLimit,
    "forfeit_disconnect" to BattleEndReason.ForfeitDisconnect,
    "abandon" to BattleEndReason.Abandon,
    "both_disconnected" to BattleEndReason.BothDisconnected,
)

/**
 * The answer of `battle_sync` and of every `battle_play` action. [clock] stamps the moment it was received, so the countdown
 * runs from it with a monotonic clock (never the phone's date).
 */
fun parseBattleState(response: JsonElement, clock: MonotonicClock): SocialResult<BattleState> = parseResponse(response) { body ->
    val receivedAt = clock.nanoTime()
    val me = body.obj("me")
    val rival = body.obj("rival")
    BattleState(
        battleId = body.str("battle_id"),
        mode = body.str("mode").word(BATTLE_MODES),
        status = body.str("status").word(BATTLE_STATUSES),
        version = body.int("version"),
        turnNumber = body.int("turn_number"),
        myHp = me.int("hp"),
        myCorrectStreak = me.int("correct_streak"),
        myUltiCharged = me.bool("ulti_charged"),
        rivalUsername = rival.str("username"),
        rivalHp = rival.int("hp"),
        rivalSecondsSinceSeen = rival.double("seconds_since_seen"),
        turn = body.objOrNull("turn")?.let {
            BattleTurn(it.bool("is_mine"), it.str("phase").word(TURN_PHASES), ServerCountdown(it.double("seconds_left"), receivedAt))
        },
        question = body.objOrNull("question")?.let { question ->
            val options = question.obj("options")
            BattleQuestion(
                id = question.int("id"),
                text = question.str("text"),
                options = OPTION_LETTERS.map { letter -> BattleOption(letter, options.str(letter.toString())) },
            )
        },
        ulti = body.objOrNull("ulti")?.let {
            UltiView(
                role = it.str("role").word(ULTI_ROLES),
                objective = it.str("objective"),
                attemptsLeft = it.int("attempts_left"),
                myResult = it.str("my_result").word(ATTEMPT_RESULTS),
            )
        },
        lastTurn = body.objOrNull("last_turn")?.let {
            LastTurn(it.int("number"), it.bool("by_me"), it.str("outcome").word(TURN_OUTCOMES), it.int("damage"))
        },
        lastUlti = body.objOrNull("last_ulti")?.let {
            LastUlti(
                shield = it.bool("shield"),
                attackerResult = it.str("attacker_result").word(ATTEMPT_RESULTS),
                defenderResult = it.str("defender_result").word(ATTEMPT_RESULTS),
            )
        },
        result = body.objOrNull("result")?.let {
            BattleResultView(
                outcome = it.str("outcome").word(OUTCOMES),
                xp = it.int("xp"),
                hpLeft = it.int("hp_left"),
                xpTotal = it.int("xp_total"),
                level = it.int("level"),
                weakTopics = it.arr("weak_topics").strings().mapNotNull { name -> LearningTopic.fromName(name) },
            )
        },
        endReason = body.strOrNull("end_reason")?.word(END_REASONS),
        myAvatarKey = me.photoKey(),
        myAvatarVersion = me.photoVersion(),
        rivalAvatarKey = rival.photoKey(),
        rivalAvatarVersion = rival.photoVersion(),
    )
}

// ---- the photo of the own profile ----

/** The function that tells the server the person uploaded (true) or deleted (false) their photo. */
const val SOCIAL_SET_AVATAR_RPC = "social_set_avatar"

fun avatarParams(hasPhoto: Boolean): JsonObject = buildJsonObject { put("p_has_photo", hasPhoto) }

/** What the server answers after `social_set_avatar`: the (possibly new) key and the new version, null when there is no photo. */
data class AvatarMark(val avatarKey: String?, val avatarVersion: Int?)

fun parseAvatarMark(response: JsonElement): SocialResult<AvatarMark> = parseResponse(response) { body ->
    if (body.str("status") != "ok") throw MalformedResponse("estado inesperado")
    AvatarMark(body.photoKey(), body.photoVersion())
}
