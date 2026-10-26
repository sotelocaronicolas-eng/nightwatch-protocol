package com.example.nightwatchprotocol.data.social

import com.example.nightwatchprotocol.data.learning.BadgeFacts
import com.example.nightwatchprotocol.data.learning.LearningTopic
import java.time.Instant
import java.time.LocalDate

/*
 * What the Social functions of the server (`0008_social.sql`) answer, as the app sees it. The server is the authority:
 * nothing here is calculated by the app (hits, damage, turns, deadlines, pair streaks). The auth id of another person never
 * appears: people are identified by their [friendCode] and shown by their username.
 */

// ---- own profile (table social_profile) and fighter progress (tables battle_results and fighter_level_curve) ----

/*
 * The photo of a person in Social is identified by [avatarKey] (a random key the server makes up, NEVER an Auth id) and
 * [avatarVersion] (null = no photo; it goes up each time the photo changes). Both are null when the server does not send them
 * (`0010_fotos_social.sql` not applied yet): the screens then show the emoji. See [SocialAvatarRepository].
 */

/*
 * How a person shows their Social profile (`0012_perfil_social.sql`): the background picture and which sections stay visible.
 * The server keeps only a number for the background (0 = none, 1 to 5 = the pictures `profile_bg_1` to `profile_bg_5`) and one
 * boolean per section; a number the app does not know is "no background".
 */

/** How many background pictures the app has. */
const val PROFILE_BG_COUNT = 5

/** [value] if it is a background the app has (0 to [PROFILE_BG_COUNT]), otherwise 0 ("sin fondo"). */
fun normalizeProfileBg(value: Int?): Int = if (value != null && value in 0..PROFILE_BG_COUNT) value else 0

/** The four choices of "Editar tu perfil". Badges, stats and history are the sections that can be hidden (the eyes). */
data class ProfileDisplaySettings(
    val profileBg: Int = 0,
    val showBadges: Boolean = true,
    val showStats: Boolean = true,
    val showHistory: Boolean = true,
)

/** The signed-in person's Social profile. There is none until they create a username. */
data class SocialProfile(
    val username: String,
    val friendCode: String,
    val hideProgress: Boolean,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
    val display: ProfileDisplaySettings = ProfileDisplaySettings(),
)

/**
 * The totals of a person's finished battles against anyone. [winratePercent] is wins over played rounded to a whole number, or
 * null when nothing was played (the screen shows "-"; there is no division by zero).
 */
data class BattleStats(val played: Int, val wins: Int, val losses: Int, val draws: Int) {
    val winratePercent: Int? get() = if (played <= 0) null else Math.round(wins * 100.0 / played).toInt()
}

/** One finished battle of the signed-in person (a row of `battle_results`). */
data class BattleHistoryEntry(
    val battleId: String,
    val outcome: BattleOutcome,
    val xpAwarded: Int,
    val hpLeft: Int,
    val opponentUsername: String,
    val finishedAt: Instant?,
)

/**
 * XP, combatant level, score and battle streak, from the person's own rows. [level] is the highest level of the curve whose
 * `min_xp` the XP reaches (1 if the curve is empty); [nextLevelXp] is the XP that the next level asks for, or null at the top.
 * [battleStreak] is the wins in a row counting back from the latest battle; a draw neither breaks nor adds to it (decision O2).
 */
data class FighterSummary(
    val xp: Int,
    val level: Int,
    val nextLevelXp: Int?,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val battleStreak: Int,
    val history: List<BattleHistoryEntry>,
) {
    /** The totals of the profile ("Estadísticas"): every finished battle of the person, whoever the rival was. */
    fun toBattleStats(): BattleStats = BattleStats(wins + losses + draws, wins, losses, draws)
}

enum class BattleOutcome { Win, Loss, Draw }

// ---- friends (social_friends) ----

data class PersonRef(
    val username: String,
    val friendCode: String,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
)

/** [streakDays] is the friend's personal streak (Colombian time) and [trainedToday] whether they practiced today. */
data class Friend(
    val username: String,
    val friendCode: String,
    val streakDays: Int,
    val trainedToday: Boolean,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
)

data class FriendRequests(val received: List<PersonRef>, val sent: List<PersonRef>)

/** Why a person is reported (`0011_reportes_bloqueos.sql`). [code] is what the server expects in the second part of the `report` argument. */
enum class ReportReason(val code: String) {
    InappropriatePhoto("inappropriate_photo"),
    OffensiveName("offensive_name"),
    Harassment("harassment"),
    Other("other"),
}

/** The note of a report is optional and at most this long (the server's check; the form shows a counter). */
const val REPORT_NOTE_MAX_LENGTH = 200

enum class FriendRelation { None, Sent, Received, Friends }

data class FriendSearchHit(
    val username: String,
    val friendCode: String,
    val relation: FriendRelation,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
)

/**
 * What a friend shares of their progress: the combatant level and the two counters ([viewedCount], [labsCount]; null when an older
 * server sent neither them nor the lists), and, only when the eye of the badges is open, the [badgeFacts] with which the app
 * evaluates the 9 badges (no badge rules in SQL).
 */
data class FriendProgress(
    val fighterLevel: Int,
    val viewedCount: Int?,
    val labsCount: Int?,
    val badgeFacts: BadgeFacts? = null,
)

/** A battle between the signed-in person and this friend, seen from the signed-in person. */
data class FriendBattleEntry(val outcome: BattleOutcome, val finishedAt: Instant?)

/**
 * [progress] is null when the friend hides it ([hidden], decision D11); the eye of the badges only takes the badge facts out of it
 * ([FriendProgress.badgeFacts]); streak and username stay visible. With `0012_perfil_social.sql` the server also sends the [display] choices and omits what the friend
 * hid: [stats] is null when the stats are hidden and [history] is empty when the history is. [extrasAvailable] is false when the
 * server answered without them (the script is not applied yet): the screen then says the feature is not available for now.
 */
data class FriendProfile(
    val username: String,
    val friendCode: String,
    val streakDays: Int,
    val trainedToday: Boolean,
    val hidden: Boolean,
    val progress: FriendProgress?,
    val history: List<FriendBattleEntry>,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
    val display: ProfileDisplaySettings = ProfileDisplaySettings(),
    val stats: BattleStats? = null,
    val extrasAvailable: Boolean = true,
)

// ---- pair streaks (social_pairs) ----

enum class PairStatus { Active, PendingSent, PendingReceived }

enum class PairState { Active, AtRisk, None, RouteComplete }

/**
 * The numbers of an active pair, all of them from the server (Colombian time, from the date each island was completed):
 * [currentDays] and [bestDays] never pass 6 (decision D14). The app does not know which island anyone completed.
 */
data class PairMetrics(
    val currentDays: Int,
    val bestDays: Int,
    val meDoneToday: Boolean,
    val partnerDoneToday: Boolean,
    val state: PairState,
    val sharedDays: List<LocalDate>,
)

/** [metrics] is null while the invitation is pending; [expiresAt] is set only then. */
data class PairStreak(
    val username: String,
    val friendCode: String,
    val status: PairStatus,
    val expiresAt: Instant?,
    val metrics: PairMetrics?,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
)

/** [topCurrentDays] is the current streak of the pair with the most days: the big number of the Social home (D15). */
data class PairStreakOverview(val pairs: List<PairStreak>, val topCurrentDays: Int)

// ---- lobby: quick match and challenges (battle_lobby) ----

sealed interface QuickMatchStatus {
    /** Looking for a rival. [suggestFriend] turns true at 60 s without one (decision D6): the search keeps going. */
    data class Waiting(val waitedSeconds: Int, val suggestFriend: Boolean) : QuickMatchStatus

    data class Matched(val battleId: String) : QuickMatchStatus

    /** The person is already in a battle: the screen goes back to it. */
    data class InBattle(val battleId: String) : QuickMatchStatus

    data object Cancelled : QuickMatchStatus

    /** Not in the queue and not in a battle. */
    data object Idle : QuickMatchStatus
}

enum class ChallengeStatus { Pending, Accepted, Declined, Cancelled, Expired }

data class ReceivedChallenge(
    val username: String,
    val friendCode: String,
    val timeLeft: ServerCountdown,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
)

data class SentChallenge(
    val username: String,
    val friendCode: String,
    val status: ChallengeStatus,
    val timeLeft: ServerCountdown,
    val battleId: String?,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
)

/** [activeBattleId] is set when the person is in a battle (for example the friend accepted their challenge). */
data class ChallengesView(
    val activeBattleId: String?,
    val received: List<ReceivedChallenge>,
    val sent: SentChallenge?,
)

// ---- battle (battle_sync and battle_play) ----

enum class BattleMode { Quick, Friend }

enum class BattleStatus { Active, Finished, Cancelled }

enum class TurnPhase { Question, Ulti }

enum class UltiRole { Attacker, Defender }

enum class UltiAttemptResult { Pending, Solved, Failed }

enum class TurnOutcome { Correct, Incorrect, Timeout, Ulti }

enum class BattleEndReason { HpZero, TurnLimit, ForfeitDisconnect, Abandon, BothDisconnected }

/** One option of a question. The question has NO correct answer: it never reaches the phone before the person answers (D7). */
data class BattleOption(val letter: Char, val text: String)

data class BattleQuestion(val id: Int, val text: String, val options: List<BattleOption>)

/** Whose turn it is and how long is left of it. [timeLeft] is the server's: the app only counts down from it. */
data class BattleTurn(val isMine: Boolean, val phase: TurnPhase, val timeLeft: ServerCountdown)

/**
 * The ulti challenge of the person: only its title ([objective]), never the command (it is known by heart, decision O10).
 * [myResult] is `Failed` after the 3 attempts are used up.
 */
data class UltiView(
    val role: UltiRole,
    val objective: String,
    val attemptsLeft: Int,
    val myResult: UltiAttemptResult,
)

data class LastTurn(val number: Int, val byMe: Boolean, val outcome: TurnOutcome, val damage: Int)

data class LastUlti(val shield: Boolean, val attackerResult: UltiAttemptResult, val defenderResult: UltiAttemptResult)

/** Shown when the battle is over. [weakTopics] are the (up to) two topics the person missed the most: "Repasa X y Y". */
data class BattleResultView(
    val outcome: BattleOutcome,
    val xp: Int,
    val hpLeft: Int,
    val xpTotal: Int,
    val level: Int,
    val weakTopics: List<LearningTopic>,
)

/**
 * The personal view of a battle, exactly as the server builds it (`sx_battle_state`). [turn], [question] and [ulti] exist only
 * while the battle is active (and the question only on the person's own turn); [result] only when it is over (and not cancelled).
 * Whoever holds this state decides nothing: a hit, a damage or a timeout is whatever the server says next.
 */
data class BattleState(
    val battleId: String,
    val mode: BattleMode,
    val status: BattleStatus,
    val version: Int,
    val turnNumber: Int,
    val myHp: Int,
    val myCorrectStreak: Int,
    val myUltiCharged: Boolean,
    val rivalUsername: String,
    val rivalHp: Int,
    val rivalSecondsSinceSeen: Double,
    val turn: BattleTurn?,
    val question: BattleQuestion?,
    val ulti: UltiView?,
    val lastTurn: LastTurn?,
    val lastUlti: LastUlti?,
    val result: BattleResultView?,
    val endReason: BattleEndReason?,
    val myAvatarKey: String? = null,
    val myAvatarVersion: Int? = null,
    val rivalAvatarKey: String? = null,
    val rivalAvatarVersion: Int? = null,
) {
    val isOpen: Boolean get() = status == BattleStatus.Active
}
