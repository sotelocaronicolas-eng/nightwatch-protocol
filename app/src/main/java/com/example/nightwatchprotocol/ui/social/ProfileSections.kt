package com.example.nightwatchprotocol.ui.social

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.BadgeState
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattleStats
import com.example.nightwatchprotocol.data.social.PROFILE_BG_COUNT
import com.example.nightwatchprotocol.data.social.normalizeProfileBg
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/*
 * The pieces shared by "Perfil amigo" and "Tu perfil" (Figma SOCIAL UPDATE 2638:374 and 2653:543) and by "Editar tu perfil"
 * (2653:611): the screen with its background picture, the header, the sections (badges, stats, history), the eye that shows or
 * hides a section and the picker of the background (component "Editar fondo", 2653:701). They are stateless: the view models decide
 * what is shown (a section that is null is not drawn) and the screens only pass it on.
 */

// ---- the background picture ----

private val ProfileBackgrounds = intArrayOf(
    R.drawable.profile_bg_1,
    R.drawable.profile_bg_2,
    R.drawable.profile_bg_3,
    R.drawable.profile_bg_4,
    R.drawable.profile_bg_5,
)

/** The picture of a background (1 to [PROFILE_BG_COUNT]), or null for "sin fondo". A number the app does not have is also null. */
@DrawableRes
internal fun profileBackgroundRes(profileBg: Int): Int? = ProfileBackgrounds.getOrNull(normalizeProfileBg(profileBg) - 1)

/** A soft dark layer over the pictures so the light text and the cards stay readable on every one of them. */
private val BackgroundScrim = Color(0x73000000)

/**
 * The picture of [profileBg] cropped to the center, under a soft dark layer; nothing for "sin fondo" (the green of the app shows).
 * The pictures are small (412 x 844) and are decoded once by the resource system, so there is no large bitmap to hold.
 */
@Composable
fun ProfileBackground(profileBg: Int, modifier: Modifier = Modifier) {
    val resource = profileBackgroundRes(profileBg) ?: return
    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(BackgroundScrim))
    }
}

/**
 * A full profile screen: the header with the title and the "X" keeps the normal green, and the background picture covers
 * everything under the white line, BEHIND the content. The content gets [SocialContentTopSpacing] above and stays clear of the
 * navigation bar, like in [SocialScreen].
 */
@Composable
fun SocialProfileScreen(
    title: String,
    onClose: () -> Unit,
    profileBg: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NightWatchScreenBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = title, onClose = onClose)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ProfileBackground(profileBg)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(top = SocialContentTopSpacing),
                    content = content,
                )
            }
        }
    }
}

// ---- what a profile shows ----

/** One battle of the history, from the point of view of the person whose profile it is ([rival] is the other side). */
data class ProfileBattleRow(val outcome: BattleOutcome, val rival: String, val daysAgo: Int?)

/** The sections a person can hide with an eye. */
enum class ProfileSection { Badges, Stats, History }

/**
 * Everything one profile draws. A section that is null is not drawn at all (the server did not send it, or the person hid it).
 * [fighterLevel], [exercises] and [labs] belong to the progress block, which only "Ocultar mi progreso público" hides; the eye of the
 * badges only takes [badges] out.
 */
data class ProfileContent(
    val username: String,
    val isOwn: Boolean,
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
    val streakDays: Int = 0,
    val trainedToday: Boolean = false,
    val fighterLevel: Int? = null,
    val exercises: Int? = null,
    val labs: Int? = null,
    val badges: List<BadgeState>? = null,
    val stats: BattleStats? = null,
    val history: List<ProfileBattleRow>? = null,
    /** "X oculta su progreso." in place of the progress block, when a friend hides it with the setting of Privacidad. */
    val hiddenNotice: String? = null,
)

/** The eyes of the editor: which sections are visible now and what to do when one is tapped. */
data class ProfileEyes(
    val badgesVisible: Boolean,
    val statsVisible: Boolean,
    val historyVisible: Boolean,
    val onToggle: (ProfileSection) -> Unit,
)

/** The most battles a profile lists. */
const val PROFILE_HISTORY_LIMIT = 3

private val HistoryBoxHeight = 160.dp
private val SectionShape = RoundedCornerShape(16.dp)
private val DimmedAlpha = 0.4f

/**
 * The header and the sections in the order of the design: header, exercises and labs, badges, stats and history. [eyes] is null on
 * the two profiles (a hidden section is simply not there) and set in the editor (a hidden section stays, dimmed).
 */
@Composable
fun ProfileSections(
    content: ProfileContent,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    eyes: ProfileEyes? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ProfileHeader(content, avatar)
        content.hiddenNotice?.let {
            SocialBodyText(it, alpha = 1f, modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally))
        }
        if (content.exercises != null && content.labs != null) {
            ProfileCounters(content.exercises, content.labs, modifier = Modifier.padding(top = 12.dp))
        }
        content.badges?.let { badges ->
            ProfileSectionTitle(stringResource(R.string.social_profile_badges), ProfileSection.Badges, eyes)
            ProfileBadges(badges, modifier = Modifier.alpha(if (eyes != null && !eyes.badgesVisible) DimmedAlpha else 1f))
        }
        content.stats?.let { stats ->
            ProfileSectionTitle(stringResource(R.string.social_profile_stats), ProfileSection.Stats, eyes)
            ProfileStatsRow(stats, modifier = Modifier.alpha(if (eyes != null && !eyes.statsVisible) DimmedAlpha else 1f))
        }
        content.history?.let { rows ->
            ProfileSectionTitle(stringResource(R.string.social_profile_history), ProfileSection.History, eyes)
            ProfileHistory(rows, modifier = Modifier.alpha(if (eyes != null && !eyes.historyVisible) DimmedAlpha else 1f))
        }
    }
}

/** Photo (130 dp) over the end of a light pill with the name, the combatant level and today's state; the streak below it. */
@Composable
fun ProfileHeader(content: ProfileContent, avatar: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(140.dp)) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(top = 26.dp, start = 59.dp)
                .fillMaxWidth()
                .height(78.dp)
                .background(NightWatchSurfaceButton, RoundedCornerShape(100.dp))
                .padding(start = 80.dp, end = 20.dp),
        ) {
            Text(
                text = content.username,
                color = SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            content.fighterLevel?.let {
                Text(
                    text = stringResource(R.string.social_profile_level, it),
                    color = SocialOnLight,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(todayText(content)),
                color = SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 0.dp)) { avatar() }
        if (content.streakDays > 0) {
            Text(
                text = stringResource(
                    R.string.social_profile_streak,
                    pluralStringResource(R.plurals.social_days, content.streakDays, content.streakDays),
                ),
                color = NightWatchGold,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            SocialBodyText(stringResource(R.string.social_friend_no_streak), modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun todayText(content: ProfileContent): Int = when {
    content.isOwn && content.trainedToday -> R.string.social_myprofile_you_trained_today
    content.isOwn -> R.string.social_myprofile_pending_today
    content.trainedToday -> R.string.social_profile_trained_today
    else -> R.string.social_profile_pending_today
}

/** Exercises and labs: two numbers in a grey-green rounded box (Figma "StatsRow Modulos"). */
@Composable
fun ProfileCounters(exercises: Int, labs: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier.fillMaxWidth().background(SocialCardTint, SectionShape).padding(vertical = 8.dp),
    ) {
        Counter(exercises, stringResource(R.string.social_profile_exercises), Modifier.weight(1f))
        Counter(labs, stringResource(R.string.social_profile_labs), Modifier.weight(1f))
    }
}

@Composable
private fun Counter(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(text = value.toString(), color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(text = label, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
    }
}

/** A section title and, in the editor, its eye. The eye makes the row at least 48 dp tall (the touch area); without it the row is just the text. */
@Composable
private fun ProfileSectionTitle(title: String, section: ProfileSection, eyes: ProfileEyes?, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = if (eyes == null) 14.dp else 0.dp, bottom = if (eyes == null) 6.dp else 0.dp),
    ) {
        Text(
            text = title,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        if (eyes != null) {
            val visible = when (section) {
                ProfileSection.Badges -> eyes.badgesVisible
                ProfileSection.Stats -> eyes.statsVisible
                ProfileSection.History -> eyes.historyVisible
            }
            VisibilityEye(
                visible = visible,
                sectionName = title,
                onToggle = { eyes.onToggle(section) },
            )
        }
    }
}

/**
 * The eye next to a title (16 x 16 dp drawn inside a 48 x 48 dp touch area): open = the section is visible, crossed out = hidden.
 * The eye is drawn here, with lines, so no picture with rights is needed. A screen reader hears "Visibilidad de X", the state and
 * that it is a switch.
 */
@Composable
fun VisibilityEye(visible: Boolean, sectionName: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.social_eye_desc, sectionName)
    val state = stringResource(if (visible) R.string.social_eye_visible else R.string.social_eye_hidden)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .toggleable(value = visible, role = Role.Switch, onValueChange = { onToggle() })
            .semantics {
                contentDescription = description
                stateDescription = state
            },
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val line = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            val outline = Path().apply {
                moveTo(0f, h * 0.5f)
                cubicTo(w * 0.25f, h * 0.05f, w * 0.75f, h * 0.05f, w, h * 0.5f)
                cubicTo(w * 0.75f, h * 0.95f, w * 0.25f, h * 0.95f, 0f, h * 0.5f)
            }
            drawPath(outline, NightWatchOnBackground, style = line)
            drawCircle(NightWatchOnBackground, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.5f))
            if (!visible) drawLine(NightWatchOnBackground, Offset(w * 0.12f, h * 0.92f), Offset(w * 0.88f, h * 0.08f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** The unlocked badges, one card each, in a row that scrolls sideways when they do not fit (Figma "Insignias"). */
@Composable
fun ProfileBadges(badges: List<BadgeState>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) {
        SocialBodyText(stringResource(R.string.social_profile_no_badges), modifier = modifier)
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        badges.forEach { ProfileBadgeCard(it) }
    }
}

@Composable
private fun ProfileBadgeCard(badge: BadgeState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(116.dp)
            .heightIn(min = 104.dp)
            .background(NightWatchSurfaceButton, SectionShape)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        EmojiCircle(badge.definition.emoji, size = 56)
        Text(
            text = badge.definition.name,
            color = SocialOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "Batallas jugadas", "Victorias" and "Winrate" (Figma "StatsRow Batallas"). Without battles the winrate is "-": nothing is divided. */
@Composable
fun ProfileStatsRow(stats: BattleStats, modifier: Modifier = Modifier) {
    val winrate = stats.winratePercent?.let { stringResource(R.string.social_profile_percent, it) } ?: stringResource(R.string.social_profile_winrate_none)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        StatCell(stats.played.toString(), stringResource(R.string.social_profile_stat_played), Modifier.weight(1f))
        StatCell(stats.wins.toString(), stringResource(R.string.social_profile_stat_wins), Modifier.weight(1f))
        StatCell(winrate, stringResource(R.string.social_profile_stat_winrate), Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(SocialCardTint, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(text = value, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * The last battles in a box that scrolls inside itself (Figma "Historial", 160 dp). At most [PROFILE_HISTORY_LIMIT] rows; none says
 * "Aún no hay batallas".
 */
@Composable
fun ProfileHistory(rows: List<ProfileBattleRow>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = HistoryBoxHeight)
            .background(SocialCardTint, SectionShape)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.social_profile_history_none),
                color = SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            rows.take(PROFILE_HISTORY_LIMIT).forEach { ProfileHistoryRow(it) }
        }
    }
}

@Composable
private fun ProfileHistoryRow(row: ProfileBattleRow) {
    val title = when (row.outcome) {
        BattleOutcome.Win -> stringResource(R.string.social_profile_won, row.rival)
        BattleOutcome.Loss -> stringResource(R.string.social_profile_lost, row.rival)
        BattleOutcome.Draw -> stringResource(R.string.social_profile_draw, row.rival)
    }
    val plain = when (row.outcome) {
        BattleOutcome.Win -> stringResource(R.string.social_profile_won_plain, row.rival)
        BattleOutcome.Loss -> stringResource(R.string.social_profile_lost_plain, row.rival)
        BattleOutcome.Draw -> stringResource(R.string.social_profile_draw_plain, row.rival)
    }
    val age = row.daysAgo?.let { days ->
        if (days == 0) stringResource(R.string.social_ago_today) else pluralStringResource(R.plurals.social_ago_days, days, days)
    }
    val spoken = if (age == null) plain else stringResource(R.string.social_profile_row_age, plain, age)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NightWatchSurfaceButton, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(text = title, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (age != null) {
            Text(text = age, color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp, modifier = Modifier.alpha(0.7f))
        }
    }
}

// ---- the picker of the background ----

private val PickerShape = RoundedCornerShape(12.dp)
private val PickerCellShape = RoundedCornerShape(8.dp)

/** The pencil (32 x 32 dp) that opens the picker of the background (Figma "Editar fondo", variant "Predeterminada"). */
@Composable
fun EditBackgroundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.social_bg_change)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xCC333333))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(text = "✎", color = NightWatchOnBackground, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * The pencil and, when [open], the picker under it: six squares (⊘ "sin fondo" and the five pictures), the chosen one outlined
 * (variant "Variante2", 200 x 82 dp). Choosing one calls [onSelect] and closes the picker; tapping outside closes it too.
 */
@Composable
fun ProfileBackgroundPicker(
    selected: Int,
    open: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(32.dp)) {
        EditBackgroundButton(onClick = onOpen)
        if (open) {
            val offset = with(LocalDensity.current) { IntOffset(0, 38.dp.roundToPx()) }
            Popup(alignment = Alignment.TopEnd, offset = offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
                BackgroundOptions(selected = normalizeProfileBg(selected), onSelect = onSelect)
            }
        }
    }
}

/** The six squares of the picker. Stateless: [selected] is 0 to [PROFILE_BG_COUNT]. */
@Composable
fun BackgroundOptions(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.width(200.dp).height(82.dp).background(Color(0xFF8C8C8C), PickerShape).padding(8.dp),
    ) {
        for (rowStart in 0..PROFILE_BG_COUNT step 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (option in rowStart until rowStart + 3) {
                    BackgroundOption(option, option == selected, onClick = { onSelect(option) }, modifier = Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun BackgroundOption(option: Int, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = if (option == 0) stringResource(R.string.social_bg_none) else stringResource(R.string.social_bg_option, option)
    val state = stringResource(if (isSelected) R.string.social_bg_selected else R.string.social_bg_not_selected)
    val resource = profileBackgroundRes(option)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(PickerCellShape)
            .background(NightWatchSurfaceButton)
            .border(if (isSelected) 2.dp else 0.dp, if (isSelected) NightWatchGold else Color.Transparent, PickerCellShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                stateDescription = state
            },
    ) {
        if (resource == null) {
            Text(text = "⊘", color = SocialOnLight, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics {})
        } else {
            Image(painter = painterResource(resource), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---- previews ----

private fun previewContent(withBadges: Boolean = true) = ProfileContent(
    username = "Juan",
    isOwn = false,
    streakDays = 2,
    trainedToday = true,
    fighterLevel = 1,
    exercises = if (withBadges) 47 else null,
    labs = if (withBadges) 5 else null,
    badges = if (withBadges) emptyList() else null,
    stats = BattleStats(played = 15, wins = 10, losses = 4, draws = 1),
    history = listOf(
        ProfileBattleRow(BattleOutcome.Win, "Juan", 2),
        ProfileBattleRow(BattleOutcome.Loss, "Juan", 6),
        ProfileBattleRow(BattleOutcome.Loss, "Juan", 6),
    ),
)

@Composable
private fun PreviewProfile(profileBg: Int, content: ProfileContent, eyes: ProfileEyes? = null) {
    NightWatchProtocolTheme {
        SocialProfileScreen(title = "Perfil", onClose = {}, profileBg = profileBg) {
            ProfileSections(
                content = content,
                avatar = { SocialPhotoAvatar(null, null, "🧑", size = 130.dp, withWizardGear = false, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 20.dp),
                eyes = eyes,
            )
        }
    }
}

@Preview(name = "Sin fondo, sin foto", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileNoBackgroundPreview() = PreviewProfile(0, previewContent())

@Preview(name = "Fondo 1", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileBackground1Preview() = PreviewProfile(1, previewContent())

@Preview(name = "Fondo 2", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileBackground2Preview() = PreviewProfile(2, previewContent())

@Preview(name = "Fondo 3", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileBackground3Preview() = PreviewProfile(3, previewContent())

@Preview(name = "Fondo 4", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileBackground4Preview() = PreviewProfile(4, previewContent())

@Preview(name = "Fondo 5", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileBackground5Preview() = PreviewProfile(5, previewContent())

@Preview(name = "Apartados ocultos (sin insignias)", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileHiddenSectionsPreview() = PreviewProfile(0, previewContent(withBadges = false).copy(stats = null, history = emptyList()))

@Preview(name = "Editor: ojos y selector", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileEditorPreview() = PreviewProfile(
    profileBg = 3,
    content = previewContent().copy(isOwn = true),
    eyes = ProfileEyes(badgesVisible = true, statsVisible = false, historyVisible = true, onToggle = {}),
)

@Preview(name = "Selector de fondo", showBackground = true, backgroundColor = 0xFF1F1F1F, widthDp = 240, heightDp = 120)
@Composable
private fun BackgroundOptionsPreview() {
    NightWatchProtocolTheme { BackgroundOptions(selected = 2, onSelect = {}, modifier = Modifier.padding(16.dp)) }
}
