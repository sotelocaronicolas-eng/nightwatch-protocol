package com.example.nightwatchprotocol.data.social

/**
 * Why a Social call failed, in terms the UI can explain. It is a plain value, not an exception: it carries no cause, so
 * nothing of the library's exception (whose message includes the request headers, token and apikey) can reach a screen or a log.
 *
 * Two kinds of failure end up here, both classified (see `0008_social.sql`, header "RESPUESTAS DE ERROR"):
 *  - Business results, answered as `{"status":"error","code":"..."}` WITHOUT an exception: [Rejected].
 *  - Server exceptions (`no_session`, `social_not_enabled`, `bad_action`, `bad_query`, `rate_limited`) and network failures.
 */
sealed interface SocialError {
    /** No connection, or the call took too long. */
    data object Offline : SocialError

    /** No session (before any request) or the server answered `no_session`. */
    data object NoSession : SocialError

    /** The person has not created a username yet (`social_not_enabled`). */
    data object NotEnabled : SocialError

    /** Too many searches, invitations or challenges in a short time (`rate_limited`). */
    data object RateLimited : SocialError

    /** The text typed is not acceptable (`bad_query`, or checked before the request). */
    data object InvalidInput : SocialError

    /** The server refused the call for lack of permission (HTTP 401/403, Postgres 42501, expired token). */
    data object PermissionDenied : SocialError

    /** Anything not classified, including a response the app cannot read. */
    data object Unknown : SocialError

    /**
     * The server does not have the action the app asked for yet: it answered `bad_action` to report, block, unblock or the list of
     * blocked people, which exist only after `0011_reportes_bloqueos.sql` is applied. The screens say "Función no disponible por
     * ahora" and keep working. Only the calls of those actions can produce it (see [SocialRpcClient.callNewAction]); for any other
     * call `bad_action` stays [Unknown], a bug of the app.
     */
    data object Unavailable : SocialError

    /** The server understood the request and said no, for a reason the person can act on. */
    data class Rejected(val reason: SocialRejection) : SocialError
}

/**
 * The business results of the Social functions. [code] is what the server sends; it is never shown: every reason has its own
 * message in `strings_social.xml`. [UsernameTaken] and [InvalidUsername] have no server code: they come from the Postgres error
 * of the `social_profile` insert (unique index on `lower(username)` and the length/characters check).
 */
enum class SocialRejection(val code: String?) {
    NotFound("not_found"),
    InvalidTarget("invalid_target"),
    AlreadyFriends("already_friends"),
    RequestExists("request_exists"),
    RequestIncoming("request_incoming"),
    RequestGone("request_gone"),
    Cooldown("cooldown"),
    TooManyPending("too_many_pending"),
    FriendsLimit("friends_limit"),
    PairExists("pair_exists"),
    PairGone("pair_gone"),
    ChallengePending("challenge_pending"),
    ChallengeGone("challenge_gone"),
    AlreadyInBattle("already_in_battle"),
    RivalBusy("rival_busy"),
    BattleOver("battle_over"),
    NotYourTurn("not_your_turn"),
    StaleTurn("stale_turn"),
    BadOption("bad_option"),
    UltiNotCharged("ulti_not_charged"),
    NoUlti("no_ulti"),
    BadCommand("bad_command"),
    AlreadyResolved("already_resolved"),
    BankEmpty("bank_empty"),
    UsernameTaken(null),
    InvalidUsername(null),
    ;

    companion object {
        fun fromCode(code: String?): SocialRejection? = entries.firstOrNull { it.code != null && it.code == code }
    }
}

/**
 * The one place that turns a code the server sent, in a business answer or as the message of an exception, into an error.
 * A code this version does not know (a newer server) is [SocialError.Unknown]; `bad_action` means the app sent an action the
 * server does not have, which is a bug of the app, so it is [SocialError.Unknown] too.
 */
fun socialErrorFromCode(code: String?): SocialError = when (val clean = code?.trim()) {
    "no_session" -> SocialError.NoSession
    "social_not_enabled" -> SocialError.NotEnabled
    "rate_limited" -> SocialError.RateLimited
    "bad_query" -> SocialError.InvalidInput
    // `report` with a malformed argument (0011): the form checks the same bounds, so this is the same "revisa el texto".
    "bad_report" -> SocialError.InvalidInput
    else -> SocialRejection.fromCode(clean)?.let { SocialError.Rejected(it) } ?: SocialError.Unknown
}

/** The result of a Social call: a value, or a classified [SocialError]. A repository never throws for a failed call. */
sealed interface SocialResult<out T> {
    data class Success<out T>(val value: T) : SocialResult<T>

    data class Failure(val error: SocialError) : SocialResult<Nothing>
}

inline fun <T, R> SocialResult<T>.map(transform: (T) -> R): SocialResult<R> = when (this) {
    is SocialResult.Success -> SocialResult.Success(transform(value))
    is SocialResult.Failure -> this
}

inline fun <T, R> SocialResult<T>.flatMap(transform: (T) -> SocialResult<R>): SocialResult<R> = when (this) {
    is SocialResult.Success -> transform(value)
    is SocialResult.Failure -> this
}

fun <T> SocialResult<T>.valueOrNull(): T? = (this as? SocialResult.Success)?.value

fun <T> SocialResult<T>.errorOrNull(): SocialError? = (this as? SocialResult.Failure)?.error
