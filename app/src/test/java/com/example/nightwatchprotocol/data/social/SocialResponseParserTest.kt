package com.example.nightwatchprotocol.data.social

import com.example.nightwatchprotocol.data.learning.LearningTopic
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialResponseParserTest {
    private val clock = TestMonotonicClock(nanos = 5_000_000_000L)

    private fun <T> SocialResult<T>.value(): T = (this as SocialResult.Success).value

    private fun <T> SocialResult<T>.error(): SocialError = (this as SocialResult.Failure).error

    // ---- the parameters sent: names of 0008, never a user id ----

    @Test
    fun actionParams_areNamedLikeTheSqlFunction_andOmitAnEmptyArgument() {
        assertEquals("""{"p_action":"list"}""", actionParams("list").toString())
        assertEquals("""{"p_action":"invite","p_arg":"valeNightwatch"}""", actionParams("invite", "valeNightwatch").toString())
    }

    @Test
    fun playParams_carryOnlyWhatTheActionUses() {
        assertEquals("""{"p_battle":"$BATTLE_ID","p_action":"abandon"}""", playParams(BATTLE_ID, "abandon").toString())
        assertEquals(
            """{"p_battle":"$BATTLE_ID","p_action":"answer","p_turn":3,"p_arg":"B"}""",
            playParams(BATTLE_ID, "answer", 3, "B").toString(),
        )
        assertEquals("""{"p_battle":"$BATTLE_ID"}""", syncParams(BATTLE_ID).toString())
    }

    // ---- the envelope: business errors and unreadable bodies ----

    @Test
    fun aBusinessError_isAFailure_notAnException() {
        val result = parseFriends(json(errorBody("not_found")))

        assertEquals(SocialError.Rejected(SocialRejection.NotFound), result.error())
    }

    @Test
    fun everyBusinessCodeOfTheScript_hasItsRejection() {
        val codes = listOf(
            "not_found", "invalid_target", "already_friends", "request_exists", "request_incoming", "request_gone", "cooldown",
            "too_many_pending", "friends_limit", "pair_exists", "pair_gone", "challenge_pending", "challenge_gone",
            "already_in_battle", "rival_busy", "battle_over", "not_your_turn", "stale_turn", "bad_option", "ulti_not_charged",
            "no_ulti", "bad_command", "already_resolved", "bank_empty",
        )

        codes.forEach { code ->
            val error = socialErrorFromCode(code)
            assertTrue("$code no tiene rechazo propio", error is SocialError.Rejected)
            assertEquals(code, (error as SocialError.Rejected).reason.code)
        }
        assertEquals(codes.size, SocialRejection.entries.count { it.code != null })
    }

    @Test
    fun theServerExceptionCodes_mapToTheirOwnErrors() {
        assertEquals(SocialError.NoSession, socialErrorFromCode("no_session"))
        assertEquals(SocialError.NotEnabled, socialErrorFromCode("social_not_enabled"))
        assertEquals(SocialError.RateLimited, socialErrorFromCode("rate_limited"))
        assertEquals(SocialError.InvalidInput, socialErrorFromCode("bad_query"))
        // bad_action is a bug of the app, not something to explain.
        assertEquals(SocialError.Unknown, socialErrorFromCode("bad_action"))
    }

    @Test
    fun anUnknownCode_orNoCode_isUnknown_neverARawCode() {
        assertEquals(SocialError.Unknown, socialErrorFromCode("codigo_del_futuro"))
        assertEquals(SocialError.Unknown, socialErrorFromCode(null))
        assertEquals(SocialError.Unknown, parseFriends(json("""{"status":"error"}""")).error())
    }

    @Test
    fun aBodyThatIsNotAnObject_orMissesFields_isUnknown() {
        assertEquals(SocialError.Unknown, parseFriends(json("[1,2]")).error())
        assertEquals(SocialError.Unknown, parseFriends(json("""{"friends":[{"username":"a"}]}""")).error())
        assertEquals(SocialError.Unknown, parseFriends(json("""{"friends":"no"}""")).error())
        assertEquals(SocialError.Unknown, parseBattleState(json("""{"battle_id":"x"}"""), clock).error())
    }

    // ---- friends ----

    @Test
    fun friendList_readsUsernameCodeStreakAndTrainedToday() {
        val friends = parseFriends(json(FRIENDS_LIST)).value()

        assertEquals(
            listOf(
                Friend("valeNightwatch", "NIGHT-7F3K2A", 3, true),
                Friend("mateo_ns", "NIGHT-9H4M5P", 0, false),
            ),
            friends,
        )
        assertEquals(emptyList<Friend>(), parseFriends(json("""{"friends":[]}""")).value())
    }

    @Test
    fun friendRequests_splitReceivedAndSent() {
        val requests = parseFriendRequests(json(FRIEND_REQUESTS)).value()

        assertEquals(listOf(PersonRef("camila", "NIGHT-AAAAAA")), requests.received)
        assertEquals(listOf("andres", "maria"), requests.sent.map { it.username })
    }

    @Test
    fun friendSearch_readsTheRelation_andNotFoundIsARejection() {
        assertEquals(
            FriendSearchHit("valeNightwatch", "NIGHT-7F3K2A", FriendRelation.Received),
            parseFriendSearch(json(FRIEND_SEARCH_OK)).value(),
        )
        assertEquals(SocialError.Rejected(SocialRejection.NotFound), parseFriendSearch(json(errorBody("not_found"))).error())
        assertEquals(
            SocialError.Unknown,
            parseFriendSearch(json("""{"status":"ok","found":true,"username":"a","friend_code":"b","relation":"enemigos"}""")).error(),
        )
    }

    @Test
    fun friendProfile_visible_hasProgress_historyAndDropsUnknownTopics() {
        val profile = parseFriendProfile(json(FRIEND_PROFILE_VISIBLE)).value()

        assertFalse(profile.hidden)
        val progress = profile.progress!!
        val facts = progress.badgeFacts!!
        assertEquals(setOf(LearningTopic.CONTRASENAS, LearningTopic.PHISHING), facts.viewedLessons)
        assertEquals(setOf(LearningTopic.CONTRASENAS), facts.completedLabs)
        assertTrue(facts.examPassed)
        assertFalse(facts.examPerfect)
        assertEquals(4, facts.bestStreak)
        assertEquals(3, progress.fighterLevel)
        assertEquals(2, profile.streakDays)
        assertEquals(BattleOutcome.Win, profile.history[0].outcome)
        assertEquals(Instant.parse("2026-10-21T15:30:00.123456Z"), profile.history[0].finishedAt)
        // An unreadable date only loses the date, not the entry.
        assertEquals(BattleOutcome.Loss, profile.history[1].outcome)
        assertNull(profile.history[1].finishedAt)
    }

    @Test
    fun friendProgress_feedsTheBadges_withoutDuplicatingTheRules() {
        val facts = parseFriendProfile(json(FRIEND_PROFILE_VISIBLE)).value().progress!!.badgeFacts!!

        assertEquals(setOf(LearningTopic.CONTRASENAS, LearningTopic.PHISHING), facts.viewedLessons)
        assertTrue(facts.islandCompleted(LearningTopic.CONTRASENAS))
        assertFalse(facts.islandCompleted(LearningTopic.PHISHING))
        assertEquals(4, facts.bestStreak)
        assertTrue(facts.examPassed)
    }

    @Test
    fun friendProgress_withTheCountersAndTheFacts_usesTheCountersNotTheLists() {
        val progress = parseFriendProfile(json(FRIEND_PROFILE_COUNTERS_AND_FACTS)).value().progress!!

        assertEquals(5, progress.viewedCount)
        assertEquals(4, progress.labsCount)
        assertEquals(2, progress.badgeFacts!!.viewedLessons.size)
        assertEquals(3, progress.fighterLevel)
    }

    @Test
    fun friendProgress_withTheBadgesEyeClosed_hasTheLevelAndTheCounters_butNoFacts() {
        val progress = parseFriendProfile(json(FRIEND_PROFILE_COUNTERS_ONLY)).value().progress!!

        assertEquals(3, progress.fighterLevel)
        assertEquals(5, progress.viewedCount)
        assertEquals(4, progress.labsCount)
        assertNull(progress.badgeFacts)
    }

    @Test
    fun friendProgress_ofAnOlderServerWithLists_countsTheLists() {
        val progress = parseFriendProfile(json(FRIEND_PROFILE_VISIBLE)).value().progress!!

        // TEMA_NUEVO is dropped, so two lessons and one lab.
        assertEquals(2, progress.viewedCount)
        assertEquals(1, progress.labsCount)
    }

    @Test
    fun friendProgress_withoutCountersNorLists_hasNullCounters_andStillTheLevel() {
        val progress = parseFriendProfile(json(FRIEND_PROFILE_LEVEL_ONLY)).value().progress!!

        assertEquals(3, progress.fighterLevel)
        assertNull(progress.viewedCount)
        assertNull(progress.labsCount)
        assertNull(progress.badgeFacts)
    }

    @Test
    fun friendProfile_hidden_hasNoProgress_butKeepsStreakAndName() {
        val profile = parseFriendProfile(json(FRIEND_PROFILE_HIDDEN)).value()

        assertTrue(profile.hidden)
        assertNull(profile.progress)
        assertEquals("valeNightwatch", profile.username)
        assertEquals(2, profile.streakDays)
        assertEquals(emptyList<FriendBattleEntry>(), profile.history)
    }

    @Test
    fun anAcknowledgement_mustCarryTheExpectedStatus() {
        assertEquals(Unit, parseAck(json("""{"status":"sent"}"""), "sent").value())
        assertEquals(SocialError.Unknown, parseAck(json("""{"status":"accepted"}"""), "sent").error())
        assertEquals(SocialError.Rejected(SocialRejection.Cooldown), parseAck(json(errorBody("cooldown")), "sent").error())
    }

    // ---- pair streaks ----

    @Test
    fun pairOverview_readsActiveAndPendingPairs_andTheTopStreak() {
        val overview = parsePairOverview(json(PAIR_OVERVIEW)).value()

        assertEquals(2, overview.topCurrentDays)
        val active = overview.pairs[0]
        assertEquals(PairStatus.Active, active.status)
        assertEquals(
            PairMetrics(2, 3, meDoneToday = true, partnerDoneToday = false, state = PairState.AtRisk,
                sharedDays = listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20))),
            active.metrics,
        )
        val pending = overview.pairs[1]
        assertEquals(PairStatus.PendingSent, pending.status)
        assertNull(pending.metrics)
        assertEquals(Instant.parse("2026-10-24T10:00:00Z"), pending.expiresAt)
    }

    @Test
    fun pairDetail_readsTheRouteCompleteState_atTheSixDayLimit() {
        val pair = parsePairDetail(json(PAIR_DETAIL)).value()

        assertEquals(PairState.RouteComplete, pair.metrics!!.state)
        assertEquals(6, pair.metrics!!.currentDays)
        assertEquals(SocialError.Rejected(SocialRejection.PairGone), parsePairDetail(json(errorBody("pair_gone"))).error())
    }

    // ---- lobby ----

    @Test
    fun quickMatch_readsEveryStatus() {
        assertEquals(
            QuickMatchStatus.Waiting(61, suggestFriend = true),
            parseQuickMatch(json("""{"status":"waiting","waited_seconds":61,"suggest_friend":true}""")).value(),
        )
        assertEquals(QuickMatchStatus.Matched(BATTLE_ID), parseQuickMatch(json("""{"status":"matched","battle_id":"$BATTLE_ID"}""")).value())
        assertEquals(QuickMatchStatus.InBattle(BATTLE_ID), parseQuickMatch(json("""{"status":"in_battle","battle_id":"$BATTLE_ID"}""")).value())
        assertEquals(QuickMatchStatus.Cancelled, parseQuickMatch(json("""{"status":"cancelled"}""")).value())
        assertEquals(QuickMatchStatus.Idle, parseQuickMatch(json("""{"status":"none"}""")).value())
        assertEquals(SocialError.Unknown, parseQuickMatch(json("""{"status":"otra"}""")).error())
    }

    @Test
    fun acceptingAChallenge_answersTheBattleId() {
        assertEquals(BATTLE_ID, parseMatchedBattleId(json("""{"status":"matched","battle_id":"$BATTLE_ID"}""")).value())
        assertEquals(SocialError.Rejected(SocialRejection.ChallengeGone), parseMatchedBattleId(json(errorBody("challenge_gone"))).error())
    }

    @Test
    fun challenges_readReceivedAndSent_withCountdownsFromTheReceiptTime() {
        val view = parseChallenges(json(CHALLENGES), clock).value()

        assertNull(view.activeBattleId)
        assertEquals("camila", view.received[0].username)
        assertEquals(ServerCountdown(95.0, 5_000_000_000L), view.received[0].timeLeft)
        val sent = view.sent!!
        assertEquals(ChallengeStatus.Accepted, sent.status)
        assertEquals(BATTLE_ID, sent.battleId)
        assertEquals(
            ChallengesView(BATTLE_ID, emptyList(), null),
            parseChallenges(json("""{"active_battle_id":"$BATTLE_ID","received":[],"sent":null}"""), clock).value(),
        )
    }

    // ---- battle ----

    @Test
    fun myTurn_hasTheQuestionWithFourOptions_andNoCorrectAnswer() {
        val state = parseBattleState(json(BATTLE_MY_QUESTION_TURN), clock).value()

        assertEquals(BattleStatus.Active, state.status)
        assertTrue(state.turn!!.isMine)
        assertEquals(TurnPhase.Question, state.turn!!.phase)
        assertEquals(listOf('A', 'B', 'C', 'D'), state.question!!.options.map { it.letter })
        assertEquals("k7#mQ!9zRt\$2", state.question!!.options[2].text)
        assertEquals(80, state.myHp)
        assertEquals(60, state.rivalHp)
        assertEquals(2, state.myCorrectStreak)
        assertEquals(LastTurn(2, byMe = false, outcome = TurnOutcome.Incorrect, damage = 0), state.lastTurn)
        // The countdown starts from the moment the answer was received, in the monotonic clock.
        assertEquals(ServerCountdown(57.4, 5_000_000_000L), state.turn!!.timeLeft)
        assertEquals(57_400L, state.turn!!.timeLeft.remainingMillis(5_000_000_000L))
    }

    @Test
    fun theBattleState_hasNoPlaceForTheCorrectAnswer() {
        val fields = BattleQuestion::class.java.declaredFields.map { it.name.lowercase() } +
            BattleOption::class.java.declaredFields.map { it.name.lowercase() } +
            BattleState::class.java.declaredFields.map { it.name.lowercase() }

        fields.forEach { field -> assertFalse("hay un campo $field", field.contains("correct") && field != "mycorrectstreak") }
    }

    @Test
    fun rivalsTurn_hasNoQuestion_andShowsTheRivalCountdown() {
        val state = parseBattleState(json(BATTLE_RIVAL_TURN), clock).value()

        assertFalse(state.turn!!.isMine)
        assertNull(state.question)
        assertTrue(state.myUltiCharged)
        assertEquals(BattleMode.Friend, state.mode)
        assertEquals(31.0, state.rivalSecondsSinceSeen, 0.0)
        assertEquals(44_000L, state.turn!!.timeLeft.remainingMillis(5_000_000_000L))
    }

    @Test
    fun ulti_showsOnlyMyChallengeTitle_andTheAttemptsLeft() {
        val state = parseBattleState(json(BATTLE_ULTI_ATTACKER), clock).value()

        assertEquals(TurnPhase.Ulti, state.turn!!.phase)
        assertEquals(UltiView(UltiRole.Attacker, "Bloquear el SSH expuesto", 2, UltiAttemptResult.Pending), state.ulti)
        assertNull(state.question)
    }

    @Test
    fun anUltiWithShield_isReadFromLastTurnAndLastUlti() {
        val state = parseBattleState(json(BATTLE_ULTI_RESOLVED_WITH_SHIELD), clock).value()

        assertEquals(LastTurn(10, byMe = true, outcome = TurnOutcome.Ulti, damage = 23), state.lastTurn)
        assertEquals(LastUlti(shield = true, attackerResult = UltiAttemptResult.Solved, defenderResult = UltiAttemptResult.Solved), state.lastUlti)
        assertEquals(17, state.rivalHp)
    }

    @Test
    fun aFinishedBattle_hasTheResult_theWeakTopics_andNoTurn() {
        val state = parseBattleState(json(BATTLE_FINISHED_LOST), clock).value()

        assertEquals(BattleStatus.Finished, state.status)
        assertFalse(state.isOpen)
        assertNull(state.turn)
        assertEquals(BattleEndReason.HpZero, state.endReason)
        assertEquals(
            BattleResultView(BattleOutcome.Loss, xp = 10, hpLeft = 0, xpTotal = 60, level = 1,
                weakTopics = listOf(LearningTopic.CONTRASENAS, LearningTopic.PHISHING)),
            state.result,
        )
    }

    @Test
    fun defeatByDisconnection_isAFinishedBattleWithThatReason_andNoXp() {
        val state = parseBattleState(json(BATTLE_FINISHED_BY_DISCONNECT), clock).value()

        assertEquals(BattleEndReason.ForfeitDisconnect, state.endReason)
        assertEquals(BattleOutcome.Loss, state.result!!.outcome)
        assertEquals(0, state.result!!.xp)
        assertEquals(75.0, state.rivalSecondsSinceSeen, 0.0)
    }

    @Test
    fun aCancelledBattle_hasNoResult() {
        val state = parseBattleState(json(BATTLE_CANCELLED), clock).value()

        assertEquals(BattleStatus.Cancelled, state.status)
        assertEquals(BattleEndReason.BothDisconnected, state.endReason)
        assertNull(state.result)
    }

    @Test
    fun anUnknownWordInABattle_isUnknown_notACrash() {
        val broken = BATTLE_MY_QUESTION_TURN.replace("\"phase\":\"question\"", "\"phase\":\"fase_nueva\"")

        assertEquals(SocialError.Unknown, parseBattleState(json(broken), clock).error())
    }

    @Test
    fun battleActions_answerBusinessErrors_asRejections() {
        assertEquals(SocialError.Rejected(SocialRejection.StaleTurn), parseBattleState(json(errorBody("stale_turn")), clock).error())
        assertEquals(SocialError.Rejected(SocialRejection.BattleOver), parseBattleState(json(errorBody("battle_over")), clock).error())
    }
}
