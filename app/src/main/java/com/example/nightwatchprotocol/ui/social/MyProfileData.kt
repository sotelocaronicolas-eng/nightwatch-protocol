package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.data.learning.BadgeState
import com.example.nightwatchprotocol.data.learning.ProgressSummary
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.social.BattleStats
import com.example.nightwatchprotocol.data.social.FighterRepository
import com.example.nightwatchprotocol.data.social.ProfileDisplaySettings
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfileRepository
import com.example.nightwatchprotocol.data.social.SocialResult
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Everything the own profile screens ("Tu perfil" and "Editar tu perfil") read, with ALL the sections: which of them are drawn is
 * decided afterwards by [toProfileContent]. A read that failed leaves its part null and its error in [partialError]; the screen
 * shows what it has and a retry. [display] is what the server has saved; the editor works on a draft of it.
 */
data class MyProfileData(
    val username: String,
    val avatarKey: String?,
    val avatarVersion: Int?,
    val display: ProfileDisplaySettings,
    val fighterLevel: Int?,
    val streakDays: Int,
    val trainedToday: Boolean,
    val exercises: Int?,
    val labs: Int?,
    val badges: List<BadgeState>?,
    val stats: BattleStats?,
    val history: List<ProfileBattleRow>?,
    val partialError: SocialError? = null,
)

/**
 * The own profile as the shared sections draw it. With [forEditor] false a section whose eye is closed in [display] is null (not
 * drawn, the ones below move up); in the editor every section is there, and the screen only dims the closed ones.
 * "Ocultar mi progreso público" (Privacidad) is for what OTHER people see and does not change the own profile: only the eyes do.
 */
internal fun MyProfileData.toProfileContent(display: ProfileDisplaySettings = this.display, forEditor: Boolean = false): ProfileContent {
    val badgesShown = forEditor || display.showBadges
    return ProfileContent(
        username = username,
        isOwn = true,
        avatarKey = avatarKey,
        avatarVersion = avatarVersion,
        streakDays = streakDays,
        trainedToday = trainedToday,
        fighterLevel = fighterLevel.takeIf { badgesShown },
        exercises = exercises.takeIf { badgesShown },
        labs = labs.takeIf { badgesShown },
        badges = badges.takeIf { badgesShown },
        stats = stats.takeIf { forEditor || display.showStats },
        history = history.takeIf { forEditor || display.showHistory },
    )
}

/**
 * Reads the signed-in person's social profile, their progress (badges, streak, lessons, labs) and their battles (level, totals and
 * the last ones, from `battle_results`) in parallel. The profile itself is required; the other two may fail without losing it.
 * [readProgress] is the progress of the learning module; [clock] only turns the dates of the battles into "hace N días".
 */
class MyProfileLoader(
    private val profileRepository: SocialProfileRepository,
    private val fighterRepository: FighterRepository,
    private val readProgress: suspend () -> ProgressSummary,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun load(): SocialResult<MyProfileData> = coroutineScope {
        val profile = async { profileRepository.getMyProfile() }
        val fighter = async { fighterRepository.getSummary() }
        val progress = async { runCatchingKeepCancellation { readProgress() } }

        val me = when (val result = profile.await()) {
            is SocialResult.Failure -> return@coroutineScope result
            is SocialResult.Success -> result.value ?: return@coroutineScope SocialResult.Failure(SocialError.NotEnabled)
        }
        val summary = (fighter.await() as? SocialResult.Success)?.value
        val progressSummary = progress.await().getOrNull()
        val now = clock.instant()
        val error = (fighter.await() as? SocialResult.Failure)?.error ?: SocialError.Unknown.takeIf { progressSummary == null }

        SocialResult.Success(
            MyProfileData(
                username = me.username,
                avatarKey = me.avatarKey,
                avatarVersion = me.avatarVersion,
                display = me.display,
                fighterLevel = summary?.level,
                streakDays = progressSummary?.streak?.current ?: 0,
                trainedToday = progressSummary?.trainedToday ?: false,
                exercises = progressSummary?.viewedLessonCount,
                labs = progressSummary?.completedLabCount,
                badges = progressSummary?.badges?.filter { it.unlocked },
                stats = summary?.toBattleStats(),
                history = summary?.history?.take(PROFILE_HISTORY_LIMIT)?.map {
                    ProfileBattleRow(it.outcome, it.opponentUsername, daysAgo(it.finishedAt, now))
                },
                partialError = error,
            ),
        )
    }
}
