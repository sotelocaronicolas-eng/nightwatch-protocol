package com.example.nightwatchprotocol.data.social

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * The Social repositories. Rules shared by all of them (supabase/migrations/0008_social.sql):
 *  - The person is the session's: no function receives a user id, and other people are named by username or friend code.
 *  - Every failure is a [SocialResult.Failure] with a classified [SocialError]; nothing is thrown (a cancelled coroutine
 *    still cancels). Nothing is queued: without a connection the call fails as [SocialError.Offline].
 *  - The server decides everything about the game (hits, damage, turns, deadlines, pair streaks): these interfaces only ask
 *    and show.
 */

/** The server's `jsonb` functions called by name. The only thing that knows how a call travels (Supabase) is its implementation. */
interface SocialRpcClient {
    /** Calls the SQL function [function] with the named [params] and returns its `jsonb` answer, or the classified failure. */
    suspend fun call(function: String, params: JsonObject): SocialResult<JsonElement>

    /**
     * Like [call], for an action that only exists after `0011_reportes_bloqueos.sql` (report, block, unblock, blocks): a server
     * without it answers `bad_action`, which here is [SocialError.Unavailable] and not a bug of the app. The default is [call], for
     * clients that do not tell the two apart.
     */
    suspend fun callNewAction(function: String, params: JsonObject): SocialResult<JsonElement> = call(function, params)
}

/** The own profile (table `social_profile`). Created with INSERT of (user_id, username); only `hide_progress` can be updated. */
interface SocialProfileRepository {
    /** Null when the person has not created a username yet (Social is not enabled). */
    suspend fun getMyProfile(): SocialResult<SocialProfile?>

    /** Enables Social. The username is 3 to 20 letters, digits or `_`, unique without regard to case, and cannot be changed later (O3). */
    suspend fun createProfile(username: String): SocialResult<SocialProfile>

    /** "Ocultar mi progreso público": saved on the server (decision D11). */
    suspend fun setHideProgress(hide: Boolean): SocialResult<Unit>
}

/** XP, level, score and streak of battles, from the person's own `battle_results` rows and the XP curve (read only). */
interface FighterRepository {
    suspend fun getSummary(): SocialResult<FighterSummary>
}

/** Friends (`social_friends`). The other person is named by [friendCode] (or by username where it says so). */
interface FriendsRepository {
    suspend fun list(): SocialResult<List<Friend>>

    suspend fun requests(): SocialResult<FriendRequests>

    /** Exact username or friend code, 3 to 40 characters; no prefix search and no lists (and 20 searches every 10 minutes). */
    suspend fun search(query: String): SocialResult<FriendSearchHit>

    /** By exact username or friend code. */
    suspend fun invite(target: String): SocialResult<Unit>

    suspend fun accept(friendCode: String): SocialResult<Unit>

    suspend fun decline(friendCode: String): SocialResult<Unit>

    /** Cancels the request the person sent. */
    suspend fun cancel(friendCode: String): SocialResult<Unit>

    suspend fun remove(friendCode: String): SocialResult<Unit>

    suspend fun profile(friendCode: String): SocialResult<FriendProfile>

    /*
     * Reporting and blocking (`0011_reportes_bloqueos.sql`, not applied yet when this was written). Until it is, the server answers
     * `bad_action` and every one of these fails as [SocialError.Unavailable]. Nothing else in Social depends on them.
     */

    /**
     * Reports any person (not only friends) by exact username or friend code. [note] is optional, at most [REPORT_NOTE_MAX_LENGTH]
     * characters. Reporting changes nothing for the reporter, and nothing hides the other person's photo by itself: the team reads
     * the reports and decides. At most 10 a day.
     */
    suspend fun report(target: String, reason: ReportReason, note: String): SocialResult<Unit>

    /**
     * Blocks a person (by exact username or friend code). It ends the friendship and the pair streak and cancels the pending
     * challenges between the two; it does NOT end a battle in progress. From then on neither of them can find, invite or challenge
     * the other, and the quick match never pairs them.
     */
    suspend fun block(target: String): SocialResult<Unit>

    /** Lifts the person's own block. The friendship and the pair streak that the block ended do not come back. */
    suspend fun unblock(target: String): SocialResult<Unit>

    /** The people the signed-in person blocked, newest first (never the people who blocked them). */
    suspend fun blocks(): SocialResult<List<PersonRef>>
}

/** Pair streaks (`social_pairs`): the server calculates them in Colombian time; they never pass 6 days. */
interface PairStreakRepository {
    suspend fun overview(): SocialResult<PairStreakOverview>

    suspend fun detail(friendCode: String): SocialResult<PairStreak>

    /** Only a friend can be invited; "does not exist" and "is not my friend" answer the same (`not_found`). */
    suspend fun invite(target: String): SocialResult<Unit>

    suspend fun accept(friendCode: String): SocialResult<Unit>

    suspend fun decline(friendCode: String): SocialResult<Unit>

    suspend fun cancel(friendCode: String): SocialResult<Unit>

    suspend fun end(friendCode: String): SocialResult<Unit>
}

/** Quick match and challenges (`battle_lobby`). Both end in the same battle (decision D6). */
interface BattleLobbyRepository {
    suspend fun quickJoin(): SocialResult<QuickMatchStatus>

    suspend fun quickPoll(): SocialResult<QuickMatchStatus>

    suspend fun quickCancel(): SocialResult<QuickMatchStatus>

    /** Challenges a friend; the challenge lasts 2 minutes (D16). */
    suspend fun sendChallenge(friendCode: String): SocialResult<Unit>

    /** Accepting creates the battle; the answer is its id. */
    suspend fun acceptChallenge(friendCode: String): SocialResult<String>

    suspend fun declineChallenge(friendCode: String): SocialResult<Unit>

    /** Cancels the challenge the person sent and is still waiting for. */
    suspend fun cancelChallenge(): SocialResult<Unit>

    suspend fun challenges(): SocialResult<ChallengesView>
}

/**
 * A battle (`battle_sync` and `battle_play`). [sync] is also the heartbeat: the app must call it every 2 to 3 seconds while a
 * battle is open, on its own turn and during the ulti included (see [BattlePoller]); over 60 seconds without any call the
 * server gives the battle as lost. Every action returns the new state of the battle.
 */
interface BattleRepository {
    suspend fun sync(battleId: String): SocialResult<BattleState>

    /** [turnNumber] is the turn the screen was drawn for: an answer for a turn that already passed is rejected (`stale_turn`). */
    suspend fun answer(battleId: String, turnNumber: Int, option: Char): SocialResult<BattleState>

    /** Turns the current turn into an ulti (only with the ulti charged). "Guardar para después" calls nothing. */
    suspend fun useUlti(battleId: String): SocialResult<BattleState>

    /** One attempt of 3 (decision O10); the command is typed from memory. */
    suspend fun submitUltiCommand(battleId: String, command: String): SocialResult<BattleState>

    suspend fun abandon(battleId: String): SocialResult<BattleState>
}
