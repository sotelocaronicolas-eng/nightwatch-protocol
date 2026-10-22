package com.example.nightwatchprotocol.ui.social

import androidx.annotation.StringRes
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection

/**
 * The message of an error, from `strings_social.xml`. A code the server sent (`no_session`, `rate_limited`...) is never shown:
 * every [SocialError] and every [SocialRejection] has its own text. It is a plain function, not a screen.
 */
@StringRes
fun SocialError.messageRes(): Int = when (this) {
    SocialError.Offline -> R.string.social_error_offline
    SocialError.NoSession -> R.string.social_error_no_session
    SocialError.NotEnabled -> R.string.social_error_not_enabled
    SocialError.RateLimited -> R.string.social_error_rate_limited
    SocialError.InvalidInput -> R.string.social_error_invalid_input
    SocialError.PermissionDenied -> R.string.social_error_permission_denied
    SocialError.Unknown -> R.string.social_error_unknown
    is SocialError.Rejected -> reason.messageRes()
}

@StringRes
fun SocialRejection.messageRes(): Int = when (this) {
    SocialRejection.NotFound -> R.string.social_rejected_not_found
    SocialRejection.InvalidTarget -> R.string.social_rejected_invalid_target
    SocialRejection.AlreadyFriends -> R.string.social_rejected_already_friends
    SocialRejection.RequestExists -> R.string.social_rejected_request_exists
    SocialRejection.RequestIncoming -> R.string.social_rejected_request_incoming
    SocialRejection.RequestGone -> R.string.social_rejected_request_gone
    SocialRejection.Cooldown -> R.string.social_rejected_cooldown
    SocialRejection.TooManyPending -> R.string.social_rejected_too_many_pending
    SocialRejection.FriendsLimit -> R.string.social_rejected_friends_limit
    SocialRejection.PairExists -> R.string.social_rejected_pair_exists
    SocialRejection.PairGone -> R.string.social_rejected_pair_gone
    SocialRejection.ChallengePending -> R.string.social_rejected_challenge_pending
    SocialRejection.ChallengeGone -> R.string.social_rejected_challenge_gone
    SocialRejection.AlreadyInBattle -> R.string.social_rejected_already_in_battle
    SocialRejection.RivalBusy -> R.string.social_rejected_rival_busy
    SocialRejection.BattleOver -> R.string.social_rejected_battle_over
    SocialRejection.NotYourTurn -> R.string.social_rejected_not_your_turn
    SocialRejection.StaleTurn -> R.string.social_rejected_stale_turn
    SocialRejection.BadOption -> R.string.social_rejected_bad_option
    SocialRejection.UltiNotCharged -> R.string.social_rejected_ulti_not_charged
    SocialRejection.NoUlti -> R.string.social_rejected_no_ulti
    SocialRejection.BadCommand -> R.string.social_rejected_bad_command
    SocialRejection.AlreadyResolved -> R.string.social_rejected_already_resolved
    SocialRejection.BankEmpty -> R.string.social_rejected_bank_empty
    SocialRejection.UsernameTaken -> R.string.social_rejected_username_taken
    SocialRejection.InvalidUsername -> R.string.social_rejected_invalid_username
}
