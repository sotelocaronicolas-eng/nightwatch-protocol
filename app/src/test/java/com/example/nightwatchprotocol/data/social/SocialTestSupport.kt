package com.example.nightwatchprotocol.data.social

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A monotonic clock the test moves by hand. */
class TestMonotonicClock(var nanos: Long = 0L) : MonotonicClock {
    override fun nanoTime(): Long = nanos

    fun advanceMillis(millis: Long) {
        nanos += millis * 1_000_000L
    }
}

fun json(text: String): JsonElement = Json.parseToJsonElement(text.trimIndent())

/**
 * A [SocialRpcClient] that records every call (function and named parameters) and answers what the test says: [responses] in
 * order (the last one repeats) or, when empty, [defaultResponse].
 */
class RecordingSocialRpc(
    private val responses: MutableList<SocialResult<JsonElement>> = mutableListOf(),
    private val defaultResponse: SocialResult<JsonElement> = SocialResult.Success(json("{}")),
) : SocialRpcClient {
    data class Call(val function: String, val params: JsonObject)

    val calls = mutableListOf<Call>()

    fun answer(vararg next: SocialResult<JsonElement>) {
        responses.clear()
        responses.addAll(next)
    }

    fun answerJson(text: String) = answer(SocialResult.Success(json(text)))

    override suspend fun call(function: String, params: JsonObject): SocialResult<JsonElement> {
        calls += Call(function, params)
        return when {
            responses.isEmpty() -> defaultResponse
            responses.size == 1 -> responses[0]
            else -> responses.removeAt(0)
        }
    }
}

const val BATTLE_ID = "6f1c1c40-2c1b-4d43-9a54-2b7a3f6f9a10"

// ---- sample answers, as the server writes them (supabase/migrations/0008_social.sql) ----

val FRIENDS_LIST = """
    {"friends":[
      {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","streak":3,"trained_today":true},
      {"username":"mateo_ns","friend_code":"NIGHT-9H4M5P","streak":0,"trained_today":false}
    ]}
""".trimIndent()

val FRIEND_REQUESTS = """
    {"received":[{"username":"camila","friend_code":"NIGHT-AAAAAA"}],
     "sent":[{"username":"andres","friend_code":"NIGHT-BBBBBB"},{"username":"maria","friend_code":"NIGHT-CCCCCC"}]}
""".trimIndent()

val FRIEND_SEARCH_OK = """{"status":"ok","found":true,"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","relation":"received"}"""

val FRIEND_PROFILE_VISIBLE = """
    {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","streak":2,"trained_today":true,"hidden":false,
     "progress":{"viewed_lessons":["CONTRASENAS","PHISHING","TEMA_NUEVO"],"completed_labs":["CONTRASENAS"],
                 "exam_passed":true,"exam_perfect":false,"best_streak":4,"fighter_level":3},
     "history":[{"result":"win","finished_at":"2026-10-21T15:30:00.123456+00:00"},{"result":"loss","finished_at":"no es fecha"}]}
""".trimIndent()

/** `0012` with the eye of the badges open: the counters plus the facts. */
val FRIEND_PROFILE_COUNTERS_AND_FACTS = """
    {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","streak":2,"trained_today":true,"hidden":false,
     "progress":{"fighter_level":3,"viewed_count":5,"labs_count":4,
                 "viewed_lessons":["CONTRASENAS","PHISHING"],"completed_labs":["CONTRASENAS"],
                 "exam_passed":true,"exam_perfect":false,"best_streak":4},
     "history":[]}
""".trimIndent()

/** `0012` with the eye of the badges closed: only the level and the counters. */
val FRIEND_PROFILE_COUNTERS_ONLY = """
    {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","streak":2,"trained_today":true,"hidden":false,
     "progress":{"fighter_level":3,"viewed_count":5,"labs_count":4},"show_badges":false,"profile_bg":0,
     "history":[]}
""".trimIndent()

val FRIEND_PROFILE_LEVEL_ONLY = """
    {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","streak":2,"trained_today":true,"hidden":false,
     "progress":{"fighter_level":3},"history":[]}
""".trimIndent()

val FRIEND_PROFILE_HIDDEN = """
    {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","streak":2,"trained_today":false,"hidden":true,
     "progress":null,"history":[]}
""".trimIndent()

val PAIR_OVERVIEW = """
    {"pairs":[
      {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","status":"active","expires_at":null,
       "metrics":{"current":2,"best":3,"me_today":true,"partner_today":false,"state":"at_risk",
                  "shared_days":["2026-10-19","2026-10-20"]}},
      {"username":"mateo_ns","friend_code":"NIGHT-9H4M5P","status":"pending_sent","expires_at":"2026-10-24T10:00:00+00:00","metrics":null}
    ],"top_current":2}
""".trimIndent()

val PAIR_DETAIL = """
    {"pair":{"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","status":"active","expires_at":null,
     "metrics":{"current":6,"best":6,"me_today":true,"partner_today":true,"state":"route_complete","shared_days":[]}}}
""".trimIndent()

val CHALLENGES = """
    {"active_battle_id":null,
     "received":[{"username":"camila","friend_code":"NIGHT-AAAAAA","seconds_left":95}],
     "sent":{"username":"mateo_ns","friend_code":"NIGHT-9H4M5P","status":"accepted","seconds_left":0,"battle_id":"$BATTLE_ID"}}
""".trimIndent()

/** The turn of the person: a question with four options, no correct answer anywhere. */
val BATTLE_MY_QUESTION_TURN = """
    {"battle_id":"$BATTLE_ID","mode":"quick","status":"active","version":4,"server_now":"2026-10-21T15:30:00+00:00",
     "my_seat":1,"turn_number":3,
     "me":{"hp":80,"correct_streak":2,"ulti_charged":false},
     "rival":{"username":"mateo_ns","hp":60,"seconds_since_seen":1.5},
     "end_reason":null,
     "turn":{"is_mine":true,"phase":"question","seconds_left":57.4},
     "question":{"id":12,"text":"¿Cuál de estas contraseñas es más segura?",
       "options":{"A":"123456","B":"Tu_Nombre2024","C":"k7#mQ!9zRt${'$'}2","D":"contraseña"}},
     "last_turn":{"number":2,"by_me":false,"outcome":"incorrect","damage":0}}
""".trimIndent()

/** The rival's turn: the waiting screen (no question). */
val BATTLE_RIVAL_TURN = """
    {"battle_id":"$BATTLE_ID","mode":"friend","status":"active","version":5,"server_now":"2026-10-21T15:30:05+00:00",
     "my_seat":1,"turn_number":4,
     "me":{"hp":80,"correct_streak":0,"ulti_charged":true},
     "rival":{"username":"mateo_ns","hp":60,"seconds_since_seen":31.0},
     "end_reason":null,
     "turn":{"is_mine":false,"phase":"question","seconds_left":44.0}}
""".trimIndent()

val BATTLE_ULTI_ATTACKER = """
    {"battle_id":"$BATTLE_ID","mode":"quick","status":"active","version":8,"server_now":"2026-10-21T15:31:00+00:00",
     "my_seat":1,"turn_number":10,
     "me":{"hp":100,"correct_streak":3,"ulti_charged":true},
     "rival":{"username":"mateo_ns","hp":40,"seconds_since_seen":0.5},
     "end_reason":null,
     "turn":{"is_mine":true,"phase":"ulti","seconds_left":118.0},
     "ulti":{"role":"attacker","objective":"Bloquear el SSH expuesto","attempts_left":2,"my_result":"pending"}}
""".trimIndent()

val BATTLE_ULTI_RESOLVED_WITH_SHIELD = """
    {"battle_id":"$BATTLE_ID","mode":"quick","status":"active","version":9,"server_now":"2026-10-21T15:31:20+00:00",
     "my_seat":1,"turn_number":11,
     "me":{"hp":100,"correct_streak":0,"ulti_charged":false},
     "rival":{"username":"mateo_ns","hp":17,"seconds_since_seen":0.2},
     "end_reason":null,
     "turn":{"is_mine":false,"phase":"question","seconds_left":60.0},
     "last_turn":{"number":10,"by_me":true,"outcome":"ulti","damage":23},
     "last_ulti":{"shield":true,"attacker_result":"solved","defender_result":"solved"}}
""".trimIndent()

val BATTLE_FINISHED_LOST = """
    {"battle_id":"$BATTLE_ID","mode":"quick","status":"finished","version":14,"server_now":"2026-10-21T15:40:00+00:00",
     "my_seat":2,"turn_number":12,
     "me":{"hp":0,"correct_streak":0,"ulti_charged":false},
     "rival":{"username":"mateo_ns","hp":100,"seconds_since_seen":0.4},
     "end_reason":"hp_zero",
     "last_turn":{"number":12,"by_me":false,"outcome":"correct","damage":20},
     "result":{"outcome":"loss","xp":10,"hp_left":0,"xp_total":60,"level":1,"weak_topics":["CONTRASENAS","PHISHING"]}}
""".trimIndent()

val BATTLE_FINISHED_BY_DISCONNECT = """
    {"battle_id":"$BATTLE_ID","mode":"quick","status":"finished","version":15,"server_now":"2026-10-21T15:41:00+00:00",
     "my_seat":2,"turn_number":5,
     "me":{"hp":60,"correct_streak":1,"ulti_charged":false},
     "rival":{"username":"mateo_ns","hp":100,"seconds_since_seen":75.0},
     "end_reason":"forfeit_disconnect",
     "result":{"outcome":"loss","xp":0,"hp_left":60,"xp_total":60,"level":1,"weak_topics":[]}}
""".trimIndent()

val BATTLE_CANCELLED = """
    {"battle_id":"$BATTLE_ID","mode":"quick","status":"cancelled","version":6,"server_now":"2026-10-21T15:42:00+00:00",
     "my_seat":1,"turn_number":2,
     "me":{"hp":100,"correct_streak":0,"ulti_charged":false},
     "rival":{"username":"mateo_ns","hp":100,"seconds_since_seen":80.0},
     "end_reason":"both_disconnected"}
""".trimIndent()

fun errorBody(code: String) = """{"status":"error","code":"$code"}"""
