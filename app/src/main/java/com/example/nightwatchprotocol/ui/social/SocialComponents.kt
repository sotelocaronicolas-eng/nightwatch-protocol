package com.example.nightwatchprotocol.ui.social

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.ServerCountdown
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SystemMonotonicClock
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import kotlinx.coroutines.delay

/*
 * The pieces every Social screen shares. They reuse the look that already exists in the app (the light cards and pills of Labs, the
 * header of the settings screens): no new style (decision D17). The measures come from the Figma frames of Social
 * (2440:163 to 2451:209) and are adjusted after trying them on a phone.
 */

/** Figma color/bg/elevated (#3E6E71): the icon circle and the chips (as in Labs, 2271:92). */
internal val SocialElevated = Color(0xFF3E6E71)

/** The grey-green card of "Tu código de amigo" and of the stats (Figma 2446:212, 2446:265). */
internal val SocialCardTint = Color(0xFF94A6A7)

/** Figma color/text/on-light (#162523). */
internal val SocialOnLight = Color(0xFF162523)

/** The red of a failure and of the rival's side in a battle (Figma "Incorrecto" and the arena's bar). */
internal val SocialFail = Color(0xFFC0503E)

private val CardShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(100.dp)

/**
 * The gap between the white line under the title and the first element, the same on every screen of Social and of the battle
 * (also under the header of the Social tab). Screens do not add a top padding of their own to the first element.
 */
val SocialContentTopSpacing = 16.dp

/**
 * A full screen of Social (without the bottom bar): the header with the title and the "X", and the content below it, which gets
 * [SocialContentTopSpacing] above and stays clear of the navigation bar. The keyboard is handled by [NightWatchScreenBackground].
 */
@Composable
fun SocialScreen(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NightWatchScreenBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = title, onClose = onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = SocialContentTopSpacing),
                content = content,
            )
        }
    }
}

/**
 * Makes a piece of text that appears or changes during a battle ("Mateo acertó", "Incorrecto"...) be read out by a screen reader
 * without the person having to look for it. Only for fixed text: a text that redraws every second (a countdown) must not use it.
 */
fun Modifier.politeAnnouncement(): Modifier =
    semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }

/**
 * Runs [onLeft] when the screen leaves the composition for good. It does NOT run while the Activity is only being recreated
 * (rotation, dark mode, language, font size): the view model survives that, and the person did not leave, so a challenge waiting
 * for an answer or a place in the queue must not be given up.
 */
@Composable
fun LeaveScreenEffect(key: Any, onLeft: () -> Unit) {
    val activity = LocalActivity.current
    val currentOnLeft by rememberUpdatedState(onLeft)
    DisposableEffect(key) {
        onDispose { if (activity?.isChangingConfigurations != true) currentOnLeft() }
    }
}

@Composable
fun SocialBodyText(text: String, modifier: Modifier = Modifier, alpha: Float = 0.85f) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 13.sp,
        modifier = modifier.alpha(alpha),
    )
}

@Composable
fun SocialSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** The icon circle with an emoji (Figma 2440:237, 2446:187). The emoji is decoration: the text next to it says everything. */
@Composable
fun EmojiCircle(emoji: String, modifier: Modifier = Modifier, size: Int = 48) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size.dp).background(SocialElevated, CircleShape),
    ) {
        Text(text = emoji, fontSize = (size * 0.46f).sp, color = Color.Black, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** A tile of Social - Inicio (2440:236): light card, 2 dp border, icon circle, title, chip and a "… →" button drawn only. */
@Composable
fun SocialCard(
    emoji: String,
    title: String,
    chip: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .shadow(4.dp, CardShape)
            .background(NightWatchSurfaceButton, CardShape)
            .border(2.dp, NightWatchBorderAccent, CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(14.dp),
    ) {
        EmojiCircle(emoji)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = title,
                color = SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Chip(chip, modifier = Modifier.padding(top = 10.dp))
            PillLabel(buttonText, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 60.dp)
            .height(20.dp)
            .background(SocialElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = text,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/** The "Ver amigos →" pill, only drawn: the card takes the tap. */
@Composable
private fun PillLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 110.dp)
            .height(36.dp)
            .shadow(4.dp, PillShape)
            .background(NightWatchSurfaceButton, PillShape)
            .padding(horizontal = 7.dp),
    ) {
        Text(
            text = text,
            color = SocialOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
        )
    }
}

/** A row of a list of people (Figma 2446:186): avatar circle, name, a second line and something on the right (a button or a state). */
@Composable
fun PersonRow(
    name: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    emoji: String = stringResource(R.string.social_icon_person),
    showAvatar: Boolean = true,
    avatarKey: String? = null,
    avatarVersion: Int? = null,
    onClick: (() -> Unit)? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(2.dp, CardShape)
            .background(NightWatchSurfaceButton, CardShape)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (showAvatar) {
            // The name is right next to it: the avatar is decoration for a screen reader.
            SocialPhotoAvatar(avatarKey, avatarVersion, emoji, size = 40.dp, withWizardGear = false, contentDescription = null)
        }
        Column(modifier = Modifier.padding(start = if (showAvatar) 12.dp else 0.dp).weight(1f)) {
            Text(
                text = name,
                color = SocialOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = SocialOnLight,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.alpha(0.8f),
                )
            }
            details?.invoke(this)
        }
        trailing?.invoke()
    }
}

enum class PillStyle { Light, Gold, Outlined }

/** The small pills of the lists: "Aceptar", "Retar", "Cancelar solicitud" (Figma 2446:252, 2448:207). */
@Composable
fun SocialPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Light,
    enabled: Boolean = true,
) {
    val container = when (style) {
        PillStyle.Light -> NightWatchSurfaceButton
        PillStyle.Gold -> NightWatchGold
        PillStyle.Outlined -> Color.Transparent
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(container, PillShape)
            .let { if (style == PillStyle.Outlined) it.border(1.5.dp, SocialOnLight, PillShape) else it.shadow(2.dp, PillShape) }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = SocialOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Aceptar" (gold) and "Rechazar" (outlined) side by side: the answer to a request, an invitation or a challenge. */
@Composable
fun AcceptDeclinePills(
    acceptText: String,
    declineText: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.padding(top = 6.dp)) {
        SocialPill(acceptText, onAccept, style = PillStyle.Gold, enabled = enabled)
        SocialPill(declineText, onDecline, style = PillStyle.Outlined, enabled = enabled)
    }
}

/** The error of a call: its own message (never the code) and "Reintentar". */
@Composable
fun ErrorNotice(error: SocialError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = stringResource(error.messageRes()),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.social_retry),
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** The error under a field or a button: red, like the other forms of the app. */
@Composable
fun InlineError(error: SocialError, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(error.messageRes()),
        color = NightWatchError,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 12.sp,
        modifier = modifier.padding(top = 6.dp),
    )
}

@Composable
fun SocialLoading(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().padding(32.dp)) {
        CircularProgressIndicator(color = NightWatchOnBackground)
    }
}

/** A list with nothing in it: a short sentence, not an error. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).alpha(0.85f),
    )
}

/** The spacing of a list of rows. */
val SocialListSpacing = Arrangement.spacedBy(10.dp)

/**
 * The seconds left of a [ServerCountdown], redrawn four times a second from the monotonic clock. The phone's date and time are
 * never read, and reaching 0 decides nothing: the next answer of the server says what happened.
 */
@Composable
fun rememberSecondsLeft(countdown: ServerCountdown): State<Int> = produceState(
    initialValue = countdown.remainingSeconds(SystemMonotonicClock.nanoTime()),
    key1 = countdown,
) {
    while (true) {
        value = countdown.remainingSeconds(SystemMonotonicClock.nanoTime())
        delay(250)
    }
}

/** "1:45" for a countdown. */
@Composable
fun CountdownText(
    countdown: ServerCountdown,
    modifier: Modifier = Modifier,
    color: Color = SocialOnLight,
    fontSize: TextUnit = 12.sp,
) {
    val seconds by rememberSecondsLeft(countdown)
    Text(
        text = stringResource(R.string.social_countdown, seconds / 60, seconds % 60),
        color = color,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
        modifier = modifier,
    )
}
