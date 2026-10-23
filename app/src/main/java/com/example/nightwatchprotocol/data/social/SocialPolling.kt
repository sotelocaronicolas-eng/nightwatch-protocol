package com.example.nightwatchprotocol.data.social

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/*
 * Polling (decision D18: no Realtime, no new dependency). The server is the authority and the app asks it again and again.
 *
 * The rule that matters (supabase/migrations/0008_social.sql, header "SONDEO"): while a battle is open the app calls `battle_sync`
 * every 2 to 3 seconds, ALSO on its own turn and during the ulti. Each call is the person's heartbeat: with more than 60 seconds
 * without any call the server gives the battle as lost (not just the turn). So the interval is injectable (to test it with the
 * coroutines clock) but can never be set above [MAX_BATTLE_POLL_INTERVAL_MILLIS].
 */

const val BATTLE_POLL_INTERVAL_MILLIS = 2_500L
const val LOBBY_POLL_INTERVAL_MILLIS = 2_500L
const val CHALLENGES_POLL_INTERVAL_MILLIS = 7_500L

/** The longest wait between two heartbeats of a battle or two polls of the queue: 3 seconds. */
const val MAX_BATTLE_POLL_INTERVAL_MILLIS = 3_000L

/** The longest wait between two looks at the challenges list while Social is open (D16: they are seen when opening Social). */
const val MAX_CHALLENGES_POLL_INTERVAL_MILLIS = 10_000L

/** Failures after which asking again is pointless: the session is gone, Social is not enabled, or the battle is not the person's. */
private fun SocialError.stopsPolling(): Boolean =
    this == SocialError.NoSession || this == SocialError.NotEnabled || this == SocialError.Rejected(SocialRejection.NotFound)

/**
 * Keeps the open battle in sync. [observe] is a cold flow: it calls `battle_sync` right away, then every [intervalMillis]
 * (counted from the end of each call), and emits each answer, a failure included (a failed call is retried: the server only gives
 * the battle as lost after 60 seconds). It ends when the battle is no longer open (that last state is emitted) or when asking
 * again is pointless. It is cancelled with its collector.
 */
class BattlePoller(
    private val repository: BattleRepository,
    private val intervalMillis: Long = BATTLE_POLL_INTERVAL_MILLIS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    init {
        require(intervalMillis in 1..MAX_BATTLE_POLL_INTERVAL_MILLIS) {
            "El sondeo de la batalla debe ser de $MAX_BATTLE_POLL_INTERVAL_MILLIS ms o menos"
        }
    }

    fun observe(battleId: String): Flow<SocialResult<BattleState>> = flow {
        while (true) {
            val result = repository.sync(battleId)
            emit(result)
            val finished = when (result) {
                is SocialResult.Success -> !result.value.isOpen
                is SocialResult.Failure -> result.error.stopsPolling()
            }
            if (finished) break
            delay(intervalMillis)
        }
    }.flowOn(dispatcher)
}

/**
 * The search for a rival. [search] joins the queue once and then asks `quick_poll` every [intervalMillis]; each answer is emitted
 * (the server says by itself when 60 seconds passed without a rival: [QuickMatchStatus.Waiting.suggestFriend]). It ends on a
 * match, on being in a battle already, when the queue was left, or when asking again is pointless. Collecting it is not enough
 * to leave the queue: whoever cancels the search calls [BattleLobbyRepository.quickCancel].
 */
class QuickMatchPoller(
    private val repository: BattleLobbyRepository,
    private val intervalMillis: Long = LOBBY_POLL_INTERVAL_MILLIS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    init {
        require(intervalMillis in 1..MAX_BATTLE_POLL_INTERVAL_MILLIS) {
            "El sondeo de la cola debe ser de $MAX_BATTLE_POLL_INTERVAL_MILLIS ms o menos"
        }
    }

    fun search(): Flow<SocialResult<QuickMatchStatus>> = flow {
        val joined = repository.quickJoin()
        emit(joined)
        // Joining failed (offline, rate limit...) or the answer was not "waiting" (matched at once, already in a battle): done.
        if (joined !is SocialResult.Success || joined.value !is QuickMatchStatus.Waiting) return@flow
        var status: SocialResult<QuickMatchStatus> = joined
        while (!status.endsSearch()) {
            delay(intervalMillis)
            status = repository.quickPoll()
            emit(status)
        }
    }.flowOn(dispatcher)

    private fun SocialResult<QuickMatchStatus>.endsSearch(): Boolean = when (this) {
        is SocialResult.Success -> value !is QuickMatchStatus.Waiting
        // A poll that fails is retried (the server drops the person from the queue after a few seconds without polls) unless
        // asking again is pointless.
        is SocialResult.Failure -> error.stopsPolling()
    }
}

/**
 * The challenges list while Social is open: the received ones appear without reopening the screen and the sent one shows how it
 * ended. Asks every [intervalMillis] (5 to 10 seconds is enough; the challenge lasts 2 minutes) until its collector is cancelled
 * or asking again is pointless.
 */
class ChallengesPoller(
    private val repository: BattleLobbyRepository,
    private val intervalMillis: Long = CHALLENGES_POLL_INTERVAL_MILLIS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    init {
        require(intervalMillis in 1..MAX_CHALLENGES_POLL_INTERVAL_MILLIS) {
            "El sondeo de retos debe ser de $MAX_CHALLENGES_POLL_INTERVAL_MILLIS ms o menos"
        }
    }

    fun observe(): Flow<SocialResult<ChallengesView>> = flow {
        while (true) {
            val result = repository.challenges()
            emit(result)
            if (result is SocialResult.Failure && result.error.stopsPolling()) break
            delay(intervalMillis)
        }
    }.flowOn(dispatcher)
}
