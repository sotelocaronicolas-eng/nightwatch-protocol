package com.example.nightwatchprotocol.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key and the version of a person's photo in the answers of the server (`0010_fotos_social.sql`). The server of today does not
 * send them, so every reader must tolerate their absence, and a key that is not a UUID is never let through (it becomes a file name).
 */
class SocialAvatarParserTest {
    private val clock = TestMonotonicClock(nanos = 5_000_000_000L)

    private fun <T> SocialResult<T>.value(): T = (this as SocialResult.Success).value

    private val keyA = "11111111-1111-4111-8111-111111111111"
    private val keyB = "22222222-2222-4222-8222-222222222222"

    private fun photo(key: String = keyA, version: Int? = 2) =
        """"avatar_key":"$key","avatar_version":${version ?: "null"}"""

    // ---- the server of today: no photo fields at all ----

    @Test
    fun answersWithoutPhotoFields_parseWithNoPhoto() {
        val friends = parseFriends(json(FRIENDS_LIST)).value()
        assertTrue(friends.all { it.avatarKey == null && it.avatarVersion == null })

        val hit = parseFriendSearch(json(FRIEND_SEARCH_OK)).value()
        assertNull(hit.avatarKey)

        assertNull(parseFriendProfile(json(FRIEND_PROFILE_VISIBLE)).value().avatarKey)
        assertNull(parsePairOverview(json(PAIR_OVERVIEW)).value().pairs[0].avatarKey)
        assertNull(parseChallenges(json(CHALLENGES), clock).value().received[0].avatarKey)

        val battle = parseBattleState(json(BATTLE_MY_QUESTION_TURN), clock).value()
        assertNull(battle.myAvatarKey)
        assertNull(battle.rivalAvatarKey)
        assertNull(battle.rivalAvatarVersion)
    }

    // ---- the server of 0010 ----

    @Test
    fun friends_carryTheKeyAndTheVersionOfEachOne() {
        val body = """
            {"friends":[
              {"username":"vale","friend_code":"NIGHT-7F3K2A","streak":3,"trained_today":true,${photo(keyA, 2)}},
              {"username":"mateo","friend_code":"NIGHT-9H4M5P","streak":0,"trained_today":false,${photo(keyB, null)}}
            ]}
        """

        val friends = parseFriends(json(body)).value()

        assertEquals(keyA, friends[0].avatarKey)
        assertEquals(2, friends[0].avatarVersion)
        // A person without photo: the server sends the key and a null version.
        assertEquals(keyB, friends[1].avatarKey)
        assertNull(friends[1].avatarVersion)
    }

    @Test
    fun requests_searchProfilePairsAndChallenges_carryThePhoto() {
        val requests = parseFriendRequests(
            json("""{"received":[{"username":"camila","friend_code":"NIGHT-AAAAAA",${photo()}}],"sent":[]}"""),
        ).value()
        assertEquals(keyA, requests.received[0].avatarKey)
        assertEquals(2, requests.received[0].avatarVersion)

        val hit = parseFriendSearch(
            json("""{"status":"ok","found":true,"username":"vale","friend_code":"NIGHT-7F3K2A","relation":"none",${photo()}}"""),
        ).value()
        assertEquals(keyA, hit.avatarKey)

        val profile = parseFriendProfile(
            json("""{"username":"vale","friend_code":"NIGHT-7F3K2A","streak":2,"trained_today":true,"hidden":true,"progress":null,"history":[],${photo()}}"""),
        ).value()
        assertEquals(keyA, profile.avatarKey)
        // "Ocultar mi progreso" does not hide the photo.
        assertTrue(profile.hidden)
        assertEquals(2, profile.avatarVersion)

        val pairs = parsePairOverview(
            json(
                """{"pairs":[{"username":"mateo","friend_code":"NIGHT-9H4M5P","status":"pending_sent","expires_at":null,"metrics":null,${photo(keyB, 5)}}],"top_current":0}""",
            ),
        ).value()
        assertEquals(keyB, pairs.pairs[0].avatarKey)
        assertEquals(5, pairs.pairs[0].avatarVersion)

        val detail = parsePairDetail(
            json(
                """{"pair":{"username":"vale","friend_code":"NIGHT-7F3K2A","status":"pending_received","expires_at":null,"metrics":null,${photo()}}}""",
            ),
        ).value()
        assertEquals(keyA, detail.avatarKey)

        val challenges = parseChallenges(
            json(
                """{"active_battle_id":null,
                    "received":[{"username":"camila","friend_code":"NIGHT-AAAAAA","seconds_left":95,${photo(keyA, 1)}}],
                    "sent":{"username":"mateo","friend_code":"NIGHT-9H4M5P","status":"pending","seconds_left":100,"battle_id":null,${photo(keyB, 3)}}}""",
            ),
            clock,
        ).value()
        assertEquals(keyA, challenges.received[0].avatarKey)
        assertEquals(keyB, challenges.sent?.avatarKey)
        assertEquals(3, challenges.sent?.avatarVersion)
    }

    @Test
    fun aBattle_carriesThePhotoOfBothFighters() {
        val body = BATTLE_RIVAL_TURN
            .replace(""""me":{""", """"me":{${photo(keyA, 1)},""")
            .replace(""""rival":{""", """"rival":{${photo(keyB, 4)},""")

        val battle = parseBattleState(json(body), clock).value()

        assertEquals(keyA, battle.myAvatarKey)
        assertEquals(1, battle.myAvatarVersion)
        assertEquals(keyB, battle.rivalAvatarKey)
        assertEquals(4, battle.rivalAvatarVersion)
        assertEquals("mateo_ns", battle.rivalUsername)
    }

    // ---- what is never let through ----

    @Test
    fun aKeyThatIsNotAUuid_isDropped_andTheScreenStillParses() {
        listOf("../../etc/passwd", "NIGHT-7F3K2A", "", "11111111-1111-4111-8111-11111111111").forEach { bad ->
            val friend = parseFriends(
                json("""{"friends":[{"username":"vale","friend_code":"NIGHT-7F3K2A","streak":1,"trained_today":false,"avatar_key":"$bad","avatar_version":1}]}"""),
            ).value().single()

            assertNull("la clave '$bad' pasó", friend.avatarKey)
            assertEquals("vale", friend.username)
        }
    }

    @Test
    fun aKeyOrVersionOfTheWrongType_isDropped_insteadOfFailingTheWholeAnswer() {
        val friend = parseFriends(
            json("""{"friends":[{"username":"vale","friend_code":"NIGHT-7F3K2A","streak":1,"trained_today":false,"avatar_key":7,"avatar_version":"2"}]}"""),
        ).value().single()

        assertNull(friend.avatarKey)
        assertNull(friend.avatarVersion)
    }

    @Test
    fun aVersionBelowOne_meansNoPhoto() {
        val friend = parseFriends(
            json("""{"friends":[{"username":"vale","friend_code":"NIGHT-7F3K2A","streak":1,"trained_today":false,${photo(keyA, 0)}}]}"""),
        ).value().single()

        assertNull(friend.avatarVersion)
    }

    @Test
    fun isValidAvatarKey_acceptsOnlyTheShapeOfAUuid() {
        assertTrue(isValidAvatarKey(keyA))
        assertTrue(isValidAvatarKey("AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE"))
        assertFalse(isValidAvatarKey("../$keyA"))
        assertFalse(isValidAvatarKey("$keyA.jpg"))
        assertFalse(isValidAvatarKey(""))
    }

    // ---- social_set_avatar ----

    @Test
    fun avatarParams_areNamedLikeTheSqlFunction_andSendNoUserId() {
        assertEquals("""{"p_has_photo":true}""", avatarParams(true).toString())
        assertEquals("""{"p_has_photo":false}""", avatarParams(false).toString())
        assertEquals("social_set_avatar", SOCIAL_SET_AVATAR_RPC)
    }

    @Test
    fun theAnswerOfSetAvatar_isReadAndAnErrorIsAFailure() {
        val ok = parseAvatarMark(json("""{"status":"ok",${photo(keyA, 3)}}""")).value()
        assertEquals(AvatarMark(keyA, 3), ok)

        val removed = parseAvatarMark(json("""{"status":"ok","avatar_key":"$keyB","avatar_version":null}""")).value()
        assertEquals(AvatarMark(keyB, null), removed)

        assertEquals(SocialError.Unknown, (parseAvatarMark(json("""{"status":"raro"}""")) as SocialResult.Failure).error)
        assertEquals(
            SocialError.Rejected(SocialRejection.NotFound),
            (parseAvatarMark(json(errorBody("not_found"))) as SocialResult.Failure).error,
        )
    }
}
