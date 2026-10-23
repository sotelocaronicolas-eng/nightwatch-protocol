package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.social.BATTLE_LOBBY_RPC
import com.example.nightwatchprotocol.data.social.BATTLE_PLAY_RPC
import com.example.nightwatchprotocol.data.social.BATTLE_SYNC_RPC
import com.example.nightwatchprotocol.data.social.BattleLobbyRepository
import com.example.nightwatchprotocol.data.social.BattleRepository
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.ChallengesView
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.FriendProfile
import com.example.nightwatchprotocol.data.social.FriendRequests
import com.example.nightwatchprotocol.data.social.FriendSearchHit
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.MonotonicClock
import com.example.nightwatchprotocol.data.social.PairStreak
import com.example.nightwatchprotocol.data.social.PairStreakOverview
import com.example.nightwatchprotocol.data.social.PairStreakRepository
import com.example.nightwatchprotocol.data.social.PersonRef
import com.example.nightwatchprotocol.data.social.QuickMatchStatus
import com.example.nightwatchprotocol.data.social.REPORT_NOTE_MAX_LENGTH
import com.example.nightwatchprotocol.data.social.ReportReason
import com.example.nightwatchprotocol.data.social.SOCIAL_FRIENDS_RPC
import com.example.nightwatchprotocol.data.social.SOCIAL_PAIRS_RPC
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.SocialRpcClient
import com.example.nightwatchprotocol.data.social.SystemMonotonicClock
import com.example.nightwatchprotocol.data.social.actionParams
import com.example.nightwatchprotocol.data.social.flatMap
import com.example.nightwatchprotocol.data.social.parseAck
import com.example.nightwatchprotocol.data.social.parseBattleState
import com.example.nightwatchprotocol.data.social.parseBlocks
import com.example.nightwatchprotocol.data.social.parseChallenges
import com.example.nightwatchprotocol.data.social.parseFriendProfile
import com.example.nightwatchprotocol.data.social.parseFriendRequests
import com.example.nightwatchprotocol.data.social.parseFriendSearch
import com.example.nightwatchprotocol.data.social.parseFriends
import com.example.nightwatchprotocol.data.social.parseMatchedBattleId
import com.example.nightwatchprotocol.data.social.parsePairDetail
import com.example.nightwatchprotocol.data.social.parsePairOverview
import com.example.nightwatchprotocol.data.social.parseQuickMatch
import com.example.nightwatchprotocol.data.social.playParams
import com.example.nightwatchprotocol.data.social.reportArg
import com.example.nightwatchprotocol.data.social.syncParams
import java.util.UUID
import kotlinx.serialization.json.JsonElement

/*
 * The repositories that call the functions of `0008_social.sql` through a [SocialRpcClient]. They name the function and the action
 * exactly as the script does, send no user id (the server takes the person from the session) and never calculate anything of the
 * game: they validate only what the server would reject anyway (an empty or too long text, a letter outside A to D), so the
 * person gets the answer without a trip, and the server still validates everything.
 */

/** The same bounds the server checks for a username or a code typed by hand (`bad_query`). */
private const val MIN_QUERY_LENGTH = 3
private const val MAX_QUERY_LENGTH = 40

/** The server rejects an ulti command longer than this (`bad_command`). */
private const val MAX_COMMAND_LENGTH = 200

private fun isValidQuery(text: String): Boolean = text.trim().length in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH

private fun isUuid(text: String): Boolean = runCatching { UUID.fromString(text) }.isSuccess

private val invalidInput = SocialResult.Failure(SocialError.InvalidInput)

/** Calls [function] and reads the answer with [parse]; a failed call comes back as the same failure. */
private suspend fun <T> SocialRpcClient.callAndParse(
    function: String,
    params: kotlinx.serialization.json.JsonObject,
    parse: (JsonElement) -> SocialResult<T>,
): SocialResult<T> = call(function, params).flatMap(parse)

class SupabaseFriendsRepository(private val rpc: SocialRpcClient) : FriendsRepository {

    override suspend fun list(): SocialResult<List<Friend>> =
        rpc.callAndParse(SOCIAL_FRIENDS_RPC, actionParams("list"), ::parseFriends)

    override suspend fun requests(): SocialResult<FriendRequests> =
        rpc.callAndParse(SOCIAL_FRIENDS_RPC, actionParams("requests"), ::parseFriendRequests)

    override suspend fun search(query: String): SocialResult<FriendSearchHit> {
        if (!isValidQuery(query)) return invalidInput
        return rpc.callAndParse(SOCIAL_FRIENDS_RPC, actionParams("search", query.trim()), ::parseFriendSearch)
    }

    override suspend fun invite(target: String): SocialResult<Unit> {
        if (!isValidQuery(target)) return invalidInput
        return ack("invite", target.trim(), "sent")
    }

    override suspend fun accept(friendCode: String): SocialResult<Unit> = ack("accept", friendCode, "accepted")

    override suspend fun decline(friendCode: String): SocialResult<Unit> = ack("decline", friendCode, "declined")

    override suspend fun cancel(friendCode: String): SocialResult<Unit> = ack("cancel", friendCode, "cancelled")

    override suspend fun remove(friendCode: String): SocialResult<Unit> = ack("remove", friendCode, "removed")

    override suspend fun profile(friendCode: String): SocialResult<FriendProfile> =
        rpc.callAndParse(SOCIAL_FRIENDS_RPC, actionParams("profile", friendCode), ::parseFriendProfile)

    private suspend fun ack(action: String, arg: String, status: String): SocialResult<Unit> =
        rpc.callAndParse(SOCIAL_FRIENDS_RPC, actionParams(action, arg)) { parseAck(it, status) }

    // ---- 0011: report and block. A server without the script answers bad_action, which is SocialError.Unavailable. ----

    override suspend fun report(target: String, reason: ReportReason, note: String): SocialResult<Unit> {
        if (!isValidQuery(target) || note.trim().length > REPORT_NOTE_MAX_LENGTH) return invalidInput
        return newActionAck("report", reportArg(target, reason, note), "reported")
    }

    override suspend fun block(target: String): SocialResult<Unit> {
        if (!isValidQuery(target)) return invalidInput
        return newActionAck("block", target.trim(), "blocked")
    }

    override suspend fun unblock(target: String): SocialResult<Unit> {
        if (!isValidQuery(target)) return invalidInput
        return newActionAck("unblock", target.trim(), "unblocked")
    }

    override suspend fun blocks(): SocialResult<List<PersonRef>> =
        rpc.callNewAction(SOCIAL_FRIENDS_RPC, actionParams("blocks")).flatMap(::parseBlocks)

    private suspend fun newActionAck(action: String, arg: String, status: String): SocialResult<Unit> =
        rpc.callNewAction(SOCIAL_FRIENDS_RPC, actionParams(action, arg)).flatMap { parseAck(it, status) }
}

class SupabasePairStreakRepository(private val rpc: SocialRpcClient) : PairStreakRepository {

    override suspend fun overview(): SocialResult<PairStreakOverview> =
        rpc.callAndParse(SOCIAL_PAIRS_RPC, actionParams("overview"), ::parsePairOverview)

    override suspend fun detail(friendCode: String): SocialResult<PairStreak> =
        rpc.callAndParse(SOCIAL_PAIRS_RPC, actionParams("detail", friendCode), ::parsePairDetail)

    override suspend fun invite(target: String): SocialResult<Unit> {
        if (!isValidQuery(target)) return invalidInput
        return ack("invite", target.trim(), "sent")
    }

    override suspend fun accept(friendCode: String): SocialResult<Unit> = ack("accept", friendCode, "active")

    override suspend fun decline(friendCode: String): SocialResult<Unit> = ack("decline", friendCode, "declined")

    override suspend fun cancel(friendCode: String): SocialResult<Unit> = ack("cancel", friendCode, "cancelled")

    override suspend fun end(friendCode: String): SocialResult<Unit> = ack("end", friendCode, "ended")

    private suspend fun ack(action: String, arg: String, status: String): SocialResult<Unit> =
        rpc.callAndParse(SOCIAL_PAIRS_RPC, actionParams(action, arg)) { parseAck(it, status) }
}

class SupabaseBattleLobbyRepository(
    private val rpc: SocialRpcClient,
    private val clock: MonotonicClock = SystemMonotonicClock,
) : BattleLobbyRepository {

    override suspend fun quickJoin(): SocialResult<QuickMatchStatus> = quick("quick_join")

    override suspend fun quickPoll(): SocialResult<QuickMatchStatus> = quick("quick_poll")

    override suspend fun quickCancel(): SocialResult<QuickMatchStatus> = quick("quick_cancel")

    override suspend fun sendChallenge(friendCode: String): SocialResult<Unit> {
        if (!isValidQuery(friendCode)) return invalidInput
        return rpc.callAndParse(BATTLE_LOBBY_RPC, actionParams("challenge_send", friendCode.trim())) { parseAck(it, "sent") }
    }

    override suspend fun acceptChallenge(friendCode: String): SocialResult<String> =
        rpc.callAndParse(BATTLE_LOBBY_RPC, actionParams("challenge_accept", friendCode), ::parseMatchedBattleId)

    override suspend fun declineChallenge(friendCode: String): SocialResult<Unit> =
        rpc.callAndParse(BATTLE_LOBBY_RPC, actionParams("challenge_decline", friendCode)) { parseAck(it, "declined") }

    override suspend fun cancelChallenge(): SocialResult<Unit> =
        rpc.callAndParse(BATTLE_LOBBY_RPC, actionParams("challenge_cancel")) { parseAck(it, "cancelled") }

    override suspend fun challenges(): SocialResult<ChallengesView> =
        rpc.callAndParse(BATTLE_LOBBY_RPC, actionParams("challenges")) { parseChallenges(it, clock) }

    private suspend fun quick(action: String): SocialResult<QuickMatchStatus> =
        rpc.callAndParse(BATTLE_LOBBY_RPC, actionParams(action), ::parseQuickMatch)
}

class SupabaseBattleRepository(
    private val rpc: SocialRpcClient,
    private val clock: MonotonicClock = SystemMonotonicClock,
) : BattleRepository {

    override suspend fun sync(battleId: String): SocialResult<BattleState> {
        if (!isUuid(battleId)) return invalidInput
        return rpc.callAndParse(BATTLE_SYNC_RPC, syncParams(battleId)) { parseBattleState(it, clock) }
    }

    override suspend fun answer(battleId: String, turnNumber: Int, option: Char): SocialResult<BattleState> {
        if (option !in 'A'..'D') return invalidInput
        return play(battleId, "answer", turnNumber, option.toString())
    }

    override suspend fun useUlti(battleId: String): SocialResult<BattleState> = play(battleId, "use_ulti")

    override suspend fun submitUltiCommand(battleId: String, command: String): SocialResult<BattleState> {
        if (command.isBlank() || command.length > MAX_COMMAND_LENGTH) return invalidInput
        return play(battleId, "ulti_submit", arg = command)
    }

    override suspend fun abandon(battleId: String): SocialResult<BattleState> = play(battleId, "abandon")

    private suspend fun play(battleId: String, action: String, turnNumber: Int? = null, arg: String? = null): SocialResult<BattleState> {
        if (!isUuid(battleId)) return invalidInput
        return rpc.callAndParse(BATTLE_PLAY_RPC, playParams(battleId, action, turnNumber, arg)) { parseBattleState(it, clock) }
    }
}
