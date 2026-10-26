package com.example.nightwatchprotocol.data.social

import java.time.LocalDate

/*
 * Fakes of the Social repositories, for the view model tests of the next stages. They are in-memory and answer like the real
 * server does (same rejections), and every one has a `failure` that makes all its calls fail with a given error and a counter of
 * calls. The scenarios that matter (a friend that accepts, a rival that does not answer, an expired turn, an ulti with shield, a
 * defeat by disconnection) are in [BattleScenarios] and in the helpers of each fake.
 */

private fun <T> ok(value: T): SocialResult<T> = SocialResult.Success(value)

private fun rejected(reason: SocialRejection): SocialResult<Nothing> = SocialResult.Failure(SocialError.Rejected(reason))

class FakeSocialProfileRepository(var profile: SocialProfile? = null) : SocialProfileRepository {
    /** Usernames already taken by other people (compared without regard to case, like the unique index). */
    val takenUsernames = mutableSetOf<String>()
    var failure: SocialError? = null
    var getCalls = 0
    var createCalls = 0
    var hideCalls = 0
    var displayCalls = 0

    override suspend fun updateDisplaySettings(settings: ProfileDisplaySettings): SocialResult<Unit> {
        displayCalls++
        failure?.let { return SocialResult.Failure(it) }
        val current = profile ?: return SocialResult.Failure(SocialError.NotEnabled)
        profile = current.copy(display = settings)
        return ok(Unit)
    }

    override suspend fun getMyProfile(): SocialResult<SocialProfile?> {
        getCalls++
        failure?.let { return SocialResult.Failure(it) }
        return ok(profile)
    }

    override suspend fun createProfile(username: String): SocialResult<SocialProfile> {
        createCalls++
        failure?.let { return SocialResult.Failure(it) }
        val name = username.trim()
        if (!Regex("^[A-Za-z0-9_]{3,20}$").matches(name)) return rejected(SocialRejection.InvalidUsername)
        if (takenUsernames.any { it.equals(name, ignoreCase = true) }) return rejected(SocialRejection.UsernameTaken)
        val created = SocialProfile(name, "NIGHT-FAKE22", hideProgress = false)
        profile = created
        return ok(created)
    }

    override suspend fun setHideProgress(hide: Boolean): SocialResult<Unit> {
        hideCalls++
        failure?.let { return SocialResult.Failure(it) }
        val current = profile ?: return SocialResult.Failure(SocialError.NotEnabled)
        profile = current.copy(hideProgress = hide)
        return ok(Unit)
    }
}

class FakeFighterRepository(
    var summary: FighterSummary = FighterSummary(0, 1, 100, 0, 0, 0, 0, emptyList()),
) : FighterRepository {
    var failure: SocialError? = null
    var calls = 0

    override suspend fun getSummary(): SocialResult<FighterSummary> {
        calls++
        failure?.let { return SocialResult.Failure(it) }
        return ok(summary)
    }
}

/**
 * Friends in memory. [people] are the accounts that exist (by friend code); the signed-in person is not one of them. A request
 * the person sends stays in [sent] until [otherAccepts] (scenario "amigo aceptado") or [otherDeclines] moves it.
 */
class FakeFriendsRepository : FriendsRepository {
    val people = mutableMapOf<String, PersonRef>()
    val friends = mutableListOf<Friend>()
    val received = mutableListOf<PersonRef>()
    val sent = mutableListOf<PersonRef>()
    var failure: SocialError? = null
    var calls = 0

    fun addPerson(username: String, friendCode: String): PersonRef = PersonRef(username, friendCode).also { people[friendCode] = it }

    /** The other person accepts a request the signed-in person sent. */
    fun otherAccepts(friendCode: String) {
        val person = sent.firstOrNull { it.friendCode == friendCode } ?: return
        sent.remove(person)
        friends += Friend(person.username, person.friendCode, streakDays = 0, trainedToday = false)
    }

    fun otherDeclines(friendCode: String) {
        sent.removeAll { it.friendCode == friendCode }
    }

    private fun find(target: String): PersonRef? {
        val clean = target.trim()
        return people.values.firstOrNull { it.friendCode.equals(clean, ignoreCase = true) || it.username.equals(clean, ignoreCase = true) }
    }

    private fun begin(): SocialResult.Failure? {
        calls++
        return failure?.let { SocialResult.Failure(it) }
    }

    override suspend fun list(): SocialResult<List<Friend>> = begin() ?: ok(friends.toList())

    override suspend fun requests(): SocialResult<FriendRequests> = begin() ?: ok(FriendRequests(received.toList(), sent.toList()))

    override suspend fun search(query: String): SocialResult<FriendSearchHit> {
        begin()?.let { return it }
        if (query.trim().length !in 3..40) return SocialResult.Failure(SocialError.InvalidInput)
        val person = find(query) ?: return rejected(SocialRejection.NotFound)
        val relation = when {
            friends.any { it.friendCode == person.friendCode } -> FriendRelation.Friends
            sent.any { it.friendCode == person.friendCode } -> FriendRelation.Sent
            received.any { it.friendCode == person.friendCode } -> FriendRelation.Received
            else -> FriendRelation.None
        }
        return ok(FriendSearchHit(person.username, person.friendCode, relation))
    }

    override suspend fun invite(target: String): SocialResult<Unit> {
        begin()?.let { return it }
        if (target.trim().length !in 3..40) return SocialResult.Failure(SocialError.InvalidInput)
        val person = find(target) ?: return rejected(SocialRejection.NotFound)
        return when {
            friends.any { it.friendCode == person.friendCode } -> rejected(SocialRejection.AlreadyFriends)
            sent.any { it.friendCode == person.friendCode } -> rejected(SocialRejection.RequestExists)
            received.any { it.friendCode == person.friendCode } -> rejected(SocialRejection.RequestIncoming)
            else -> {
                sent += person
                ok(Unit)
            }
        }
    }

    override suspend fun accept(friendCode: String): SocialResult<Unit> {
        begin()?.let { return it }
        val person = received.firstOrNull { it.friendCode == friendCode } ?: return rejected(SocialRejection.RequestGone)
        received.remove(person)
        friends += Friend(person.username, person.friendCode, streakDays = 0, trainedToday = false)
        return ok(Unit)
    }

    override suspend fun decline(friendCode: String): SocialResult<Unit> {
        begin()?.let { return it }
        return if (received.removeAll { it.friendCode == friendCode }) ok(Unit) else rejected(SocialRejection.RequestGone)
    }

    override suspend fun cancel(friendCode: String): SocialResult<Unit> {
        begin()?.let { return it }
        return if (sent.removeAll { it.friendCode == friendCode }) ok(Unit) else rejected(SocialRejection.RequestGone)
    }

    override suspend fun remove(friendCode: String): SocialResult<Unit> {
        begin()?.let { return it }
        return if (friends.removeAll { it.friendCode == friendCode }) ok(Unit) else rejected(SocialRejection.NotFound)
    }

    /** What [profile] answers for each friend; a friend without an entry gets a visible profile with no progress to show. */
    val profiles = mutableMapOf<String, FriendProfile>()

    override suspend fun profile(friendCode: String): SocialResult<FriendProfile> {
        begin()?.let { return it }
        val friend = friends.firstOrNull { it.friendCode == friendCode } ?: return rejected(SocialRejection.NotFound)
        return ok(
            profiles[friendCode]
                ?: FriendProfile(friend.username, friend.friendCode, friend.streakDays, friend.trainedToday, false, null, emptyList()),
        )
    }

    // ---- report and block (0011). Set `failure = SocialError.Unavailable` for a server without the script. ----

    data class ReportCall(val target: String, val reason: ReportReason, val note: String)

    val reports = mutableListOf<ReportCall>()
    val blocked = mutableListOf<PersonRef>()
    var blockCalls = 0
    var unblockCalls = 0

    /** Reports can be made against anybody that exists, friend or not, and blocked or not (the script does not look at blocks). */
    override suspend fun report(target: String, reason: ReportReason, note: String): SocialResult<Unit> {
        begin()?.let { return it }
        if (target.trim().length !in 3..40 || note.trim().length > REPORT_NOTE_MAX_LENGTH) return SocialResult.Failure(SocialError.InvalidInput)
        val person = find(target) ?: return rejected(SocialRejection.NotFound)
        reports += ReportCall(person.friendCode, reason, note.trim())
        return ok(Unit)
    }

    /** Like the script: the friendship and the requests with that person end, and the person goes first in [blocked]. */
    override suspend fun block(target: String): SocialResult<Unit> {
        begin()?.let { return it }
        blockCalls++
        if (target.trim().length !in 3..40) return SocialResult.Failure(SocialError.InvalidInput)
        val person = find(target) ?: return rejected(SocialRejection.NotFound)
        friends.removeAll { it.friendCode == person.friendCode }
        sent.removeAll { it.friendCode == person.friendCode }
        received.removeAll { it.friendCode == person.friendCode }
        blocked.removeAll { it.friendCode == person.friendCode }
        blocked.add(0, person)
        return ok(Unit)
    }

    override suspend fun unblock(target: String): SocialResult<Unit> {
        begin()?.let { return it }
        unblockCalls++
        if (target.trim().length !in 3..40) return SocialResult.Failure(SocialError.InvalidInput)
        val person = find(target) ?: return rejected(SocialRejection.NotFound)
        return if (blocked.removeAll { it.friendCode == person.friendCode }) ok(Unit) else rejected(SocialRejection.NotFound)
    }

    override suspend fun blocks(): SocialResult<List<PersonRef>> = begin() ?: ok(blocked.toList())
}

/** Pair streaks in memory: the numbers are whatever the test sets (the server calculates them in the real one). */
class FakePairStreakRepository : PairStreakRepository {
    val pairs = mutableListOf<PairStreak>()
    var failure: SocialError? = null
    var calls = 0

    /** The friends that can be invited (by friend code). */
    val friends = mutableMapOf<String, String>()

    private fun begin(): SocialResult.Failure? {
        calls++
        return failure?.let { SocialResult.Failure(it) }
    }

    override suspend fun overview(): SocialResult<PairStreakOverview> {
        begin()?.let { return it }
        val top = pairs.mapNotNull { it.metrics?.currentDays }.maxOrNull() ?: 0
        return ok(PairStreakOverview(pairs.toList(), top))
    }

    override suspend fun detail(friendCode: String): SocialResult<PairStreak> {
        begin()?.let { return it }
        return pairs.firstOrNull { it.friendCode == friendCode }?.let { ok(it) } ?: rejected(SocialRejection.PairGone)
    }

    override suspend fun invite(target: String): SocialResult<Unit> {
        begin()?.let { return it }
        val code = friends.keys.firstOrNull { it.equals(target.trim(), true) || friends[it].equals(target.trim(), true) }
            ?: return rejected(SocialRejection.NotFound)
        if (pairs.any { it.friendCode == code }) return rejected(SocialRejection.PairExists)
        pairs += PairStreak(friends.getValue(code), code, PairStatus.PendingSent, expiresAt = null, metrics = null)
        return ok(Unit)
    }

    override suspend fun accept(friendCode: String): SocialResult<Unit> {
        begin()?.let { return it }
        val index = pairs.indexOfFirst { it.friendCode == friendCode && it.status == PairStatus.PendingReceived }
        if (index < 0) return rejected(SocialRejection.PairGone)
        pairs[index] = pairs[index].copy(status = PairStatus.Active, metrics = PairMetrics(0, 0, false, false, PairState.None, emptyList()))
        return ok(Unit)
    }

    override suspend fun decline(friendCode: String): SocialResult<Unit> = removePending(friendCode, PairStatus.PendingReceived)

    override suspend fun cancel(friendCode: String): SocialResult<Unit> = removePending(friendCode, PairStatus.PendingSent)

    override suspend fun end(friendCode: String): SocialResult<Unit> {
        begin()?.let { return it }
        return if (pairs.removeAll { it.friendCode == friendCode && it.status == PairStatus.Active }) ok(Unit) else rejected(SocialRejection.PairGone)
    }

    private fun removePending(friendCode: String, status: PairStatus): SocialResult<Unit> {
        begin()?.let { return it }
        return if (pairs.removeAll { it.friendCode == friendCode && it.status == status }) ok(Unit) else rejected(SocialRejection.PairGone)
    }

    companion object {
        fun activePair(username: String, friendCode: String, currentDays: Int, state: PairState = PairState.Active) = PairStreak(
            username, friendCode, PairStatus.Active, null,
            PairMetrics(currentDays, currentDays, meDoneToday = true, partnerDoneToday = state == PairState.Active, state = state, sharedDays = emptyList<LocalDate>()),
        )
    }
}

/**
 * The lobby. [quickPollScript] is what each `quickPoll` answers, in order (the last one repeats): a search that never finds a
 * rival is a script of `Waiting`; one that ends is `Waiting`, `Waiting`, `Matched`.
 */
class FakeBattleLobbyRepository : BattleLobbyRepository {
    var quickJoinResult: SocialResult<QuickMatchStatus> = ok(QuickMatchStatus.Waiting(0, suggestFriend = false))
    val quickPollScript = mutableListOf<SocialResult<QuickMatchStatus>>()
    var cancelResult: SocialResult<QuickMatchStatus> = ok(QuickMatchStatus.Cancelled)
    var challengesResult: SocialResult<ChallengesView> = ok(ChallengesView(null, emptyList(), null))
    var sendChallengeResult: SocialResult<Unit> = ok(Unit)
    var acceptChallengeResult: SocialResult<String> = ok(BATTLE_ID)
    var failure: SocialError? = null

    var joinCalls = 0
    var pollCalls = 0
    var cancelCalls = 0
    var challengesCalls = 0
    val sentChallengesTo = mutableListOf<String>()

    private fun begin(): SocialResult.Failure? = failure?.let { SocialResult.Failure(it) }

    override suspend fun quickJoin(): SocialResult<QuickMatchStatus> {
        joinCalls++
        return begin() ?: quickJoinResult
    }

    override suspend fun quickPoll(): SocialResult<QuickMatchStatus> {
        pollCalls++
        begin()?.let { return it }
        return when {
            quickPollScript.isEmpty() -> ok(QuickMatchStatus.Waiting(pollCalls * 3, suggestFriend = pollCalls * 3 >= 60))
            quickPollScript.size == 1 -> quickPollScript[0]
            else -> quickPollScript.removeAt(0)
        }
    }

    override suspend fun quickCancel(): SocialResult<QuickMatchStatus> {
        cancelCalls++
        return begin() ?: cancelResult
    }

    override suspend fun sendChallenge(friendCode: String): SocialResult<Unit> {
        begin()?.let { return it }
        sentChallengesTo += friendCode
        return sendChallengeResult
    }

    var acceptCalls = 0
    var declineCalls = 0
    var cancelChallengeCalls = 0

    override suspend fun acceptChallenge(friendCode: String): SocialResult<String> {
        acceptCalls++
        return begin() ?: acceptChallengeResult
    }

    override suspend fun declineChallenge(friendCode: String): SocialResult<Unit> {
        declineCalls++
        return begin() ?: ok(Unit)
    }

    override suspend fun cancelChallenge(): SocialResult<Unit> {
        cancelChallengeCalls++
        return begin() ?: ok(Unit)
    }

    override suspend fun challenges(): SocialResult<ChallengesView> {
        challengesCalls++
        return begin() ?: challengesResult
    }
}

/**
 * A battle that follows a script: every `sync` answers the next state of [script] (the last one repeats) and the actions
 * ([answer], [useUlti]...) are recorded and answered with the next state too. The app decides nothing: a test that wants "the
 * answer was correct" writes the state the server would send after it (see [BattleScenarios]).
 */
class FakeBattleRepository(script: List<SocialResult<BattleState>> = emptyList()) : BattleRepository {
    private val script = script.toMutableList()
    var failure: SocialError? = null
    val syncTimes = mutableListOf<Long>()

    /** Every action received, as text: `answer:3:B`, `use_ulti`, `ulti_submit:crack 123456`, `abandon`. */
    val actions = mutableListOf<String>()
    var syncCalls = 0

    /** Called at every sync with the number of the call (1, 2...): lets a test move a clock or fail a call. */
    var onSync: ((Int) -> Unit)? = null

    fun enqueue(vararg states: SocialResult<BattleState>) {
        script.addAll(states)
    }

    private fun next(): SocialResult<BattleState> {
        failure?.let { return SocialResult.Failure(it) }
        return when {
            script.isEmpty() -> SocialResult.Failure(SocialError.Unknown)
            script.size == 1 -> script[0]
            else -> script.removeAt(0)
        }
    }

    override suspend fun sync(battleId: String): SocialResult<BattleState> {
        syncCalls++
        onSync?.invoke(syncCalls)
        return next()
    }

    override suspend fun answer(battleId: String, turnNumber: Int, option: Char): SocialResult<BattleState> {
        actions += "answer:$turnNumber:$option"
        return next()
    }

    override suspend fun useUlti(battleId: String): SocialResult<BattleState> {
        actions += "use_ulti"
        return next()
    }

    override suspend fun submitUltiCommand(battleId: String, command: String): SocialResult<BattleState> {
        actions += "ulti_submit:$command"
        return next()
    }

    override suspend fun abandon(battleId: String): SocialResult<BattleState> {
        actions += "abandon"
        return next()
    }
}

/** States of a battle as the server would send them, for the scenarios the screens must handle. */
object BattleScenarios {
    fun countdown(seconds: Double) = ServerCountdown(seconds, receivedAtNanos = 0L)

    private val question = BattleQuestion(
        12,
        "¿Cuál de estas contraseñas es más segura?",
        listOf(
            BattleOption('A', "123456"),
            BattleOption('B', "Tu_Nombre2024"),
            BattleOption('C', "k7#mQ!9zRt$2"),
            BattleOption('D', "contraseña"),
        ),
    )

    fun state(
        turnNumber: Int = 3,
        status: BattleStatus = BattleStatus.Active,
        myHp: Int = 100,
        rivalHp: Int = 100,
        myUltiCharged: Boolean = false,
        rivalSecondsSinceSeen: Double = 0.5,
        turn: BattleTurn? = BattleTurn(isMine = true, phase = TurnPhase.Question, timeLeft = countdown(60.0)),
        question: BattleQuestion? = this.question,
        ulti: UltiView? = null,
        lastTurn: LastTurn? = null,
        lastUlti: LastUlti? = null,
        result: BattleResultView? = null,
        endReason: BattleEndReason? = null,
        version: Int = turnNumber,
    ) = BattleState(
        battleId = BATTLE_ID, mode = BattleMode.Quick, status = status, version = version, turnNumber = turnNumber,
        myHp = myHp, myCorrectStreak = 0, myUltiCharged = myUltiCharged,
        rivalUsername = "mateo_ns", rivalHp = rivalHp, rivalSecondsSinceSeen = rivalSecondsSinceSeen,
        turn = turn, question = if (turn?.isMine == true && turn.phase == TurnPhase.Question) question else null,
        ulti = ulti, lastTurn = lastTurn, lastUlti = lastUlti, result = result, endReason = endReason,
    )

    private fun rivalTurn(secondsLeft: Double, turnNumber: Int = 4, lastTurn: LastTurn? = null, rivalSeen: Double = 0.5) = state(
        turnNumber = turnNumber,
        turn = BattleTurn(isMine = false, phase = TurnPhase.Question, timeLeft = countdown(secondsLeft)),
        lastTurn = lastTurn,
        rivalSecondsSinceSeen = rivalSeen,
    )

    /** The rival's minute runs out without an answer: the server passes the turn and records a timeout (no damage). */
    fun rivalDoesNotAnswer(): List<SocialResult<BattleState>> = listOf(
        rivalTurn(44.0),
        rivalTurn(2.5),
        state(
            turnNumber = 5,
            lastTurn = LastTurn(number = 4, byMe = false, outcome = TurnOutcome.Timeout, damage = 0),
        ),
    ).map { ok(it) }

    /** The person's own minute runs out: the server records the lost turn and it is the rival's turn now. */
    fun myTurnExpires(): List<SocialResult<BattleState>> = listOf(
        state(turn = BattleTurn(true, TurnPhase.Question, countdown(3.0))),
        rivalTurn(secondsLeft = 60.0, turnNumber = 4, lastTurn = LastTurn(3, byMe = true, outcome = TurnOutcome.Timeout, damage = 0)),
    ).map { ok(it) }

    /** An ulti with the defender's shield: it hits for 23 instead of 45 and the turn passes. */
    fun ultiWithShield(): List<SocialResult<BattleState>> = listOf(
        state(
            turnNumber = 10, rivalHp = 40, myUltiCharged = true,
            turn = BattleTurn(true, TurnPhase.Ulti, countdown(118.0)),
            ulti = UltiView(UltiRole.Attacker, "Bloquear el SSH expuesto", attemptsLeft = 2, myResult = UltiAttemptResult.Pending),
        ),
        rivalTurn(
            secondsLeft = 60.0, turnNumber = 11,
            lastTurn = LastTurn(10, byMe = true, outcome = TurnOutcome.Ulti, damage = 23),
        ).copy(
            rivalHp = 17,
            lastUlti = LastUlti(shield = true, attackerResult = UltiAttemptResult.Solved, defenderResult = UltiAttemptResult.Solved),
        ),
    ).map { ok(it) }

    /** The person stops calling for over 60 seconds: the server gives the battle as lost (reason: disconnection). */
    fun lostByDisconnection(): List<SocialResult<BattleState>> = listOf(
        ok(state(turnNumber = 5)),
        ok(
            state(
                turnNumber = 5, status = BattleStatus.Finished, turn = null, rivalHp = 100, myHp = 60,
                rivalSecondsSinceSeen = 0.3, endReason = BattleEndReason.ForfeitDisconnect,
                result = BattleResultView(BattleOutcome.Loss, xp = 0, hpLeft = 60, xpTotal = 60, level = 1, weakTopics = emptyList()),
            ),
        ),
    )

    /** The rival disappears: after 60 seconds the server gives the person the win. */
    fun wonBecauseRivalDisconnected(): List<SocialResult<BattleState>> = listOf(
        ok(rivalTurn(secondsLeft = 10.0, rivalSeen = 40.0)),
        ok(
            state(
                turnNumber = 5, status = BattleStatus.Finished, turn = null, endReason = BattleEndReason.ForfeitDisconnect,
                result = BattleResultView(BattleOutcome.Win, xp = 50, hpLeft = 100, xpTotal = 110, level = 2, weakTopics = emptyList()),
            ),
        ),
    )
}
