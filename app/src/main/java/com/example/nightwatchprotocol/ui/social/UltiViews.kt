package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.UltiAttemptResult
import com.example.nightwatchprotocol.data.social.UltiRole
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.lab.CommandInput
import com.example.nightwatchprotocol.ui.lab.SendButton
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/** The red of a failure (the same as the arena's rival side and the "Incorrecto" of Figma). */
private val FailRed = Color(0xFFC0503E)

/** "¡Ulti disponible!" (Figma 2449:290): put it aside ("Guardar para después" sends nothing) or use it now. */
@Composable
internal fun UltiChoice(enabled: Boolean, onSave: () -> Unit, onUse: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 36.dp)) {
        BigIcon(stringResource(R.string.social_ulti_choice_icon), NightWatchGold)
        Text(
            text = stringResource(R.string.social_ulti_choice_title),
            color = NightWatchGold,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        SocialBodyText(
            stringResource(R.string.social_ulti_choice_body),
            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
        SocialPill(stringResource(R.string.social_ulti_use), onUse, style = PillStyle.Gold, enabled = enabled, modifier = Modifier.padding(top = 28.dp))
        SocialPill(stringResource(R.string.social_ulti_save), onSave, style = PillStyle.Outlined, enabled = enabled, modifier = Modifier.padding(top = 10.dp))
    }
}

/**
 * The terminal challenge of the ulti, for the attacker ("Desafío de tu Ulti", Figma 2450:193) and for the defender (M4: the same
 * screen with another text). The command is NOT shown: it is typed from memory (decision O10). The attempts and the 2 minutes are
 * the server's; the time shows in the turn pill above.
 */
@Composable
internal fun UltiChallenge(
    battle: BattleState,
    command: String,
    wrongCommand: Boolean,
    submitting: Boolean,
    onCommandChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    val ulti = battle.ulti ?: return
    val attacker = ulti.role == UltiRole.Attacker
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        SocialBodyText(
            text = if (attacker) {
                stringResource(R.string.social_ulti_attack_intro, battle.rivalUsername)
            } else {
                stringResource(R.string.social_ulti_defend_intro, battle.rivalUsername)
            },
            alpha = 1f,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().background(SocialElevated, shape).padding(16.dp),
        ) {
            Box(modifier = Modifier.size(20.dp).background(if (ulti.myResult == UltiAttemptResult.Solved) NightWatchGold else Color(0xFFA1A1A1), CircleShape))
            Text(
                text = ulti.objective,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(SocialElevated, shape)
                .border(1.dp, SocialCardTint, shape)
                .padding(16.dp),
        ) {
            Text(
                text = if (attacker) {
                    stringResource(R.string.social_ulti_attack_box)
                } else {
                    stringResource(R.string.social_ulti_defend_box, battle.rivalUsername)
                },
                color = NightWatchOnBackground,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
        when (ulti.myResult) {
            UltiAttemptResult.Pending -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    CommandInput(input = command, onInputChange = onCommandChanged, onSend = onSend, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.padding(start = 8.dp).alpha(if (submitting) 0.5f else 1f)) { SendButton(onClick = onSend) }
                }
                SocialBodyText(
                    stringResource(R.string.social_ulti_attempts, ulti.attemptsLeft.coerceIn(0, 3)),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (wrongCommand) {
                    Text(
                        text = stringResource(R.string.social_ulti_wrong),
                        color = NightWatchError,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            UltiAttemptResult.Solved -> SocialBodyText(
                stringResource(R.string.social_ulti_solved, battle.rivalUsername),
                alpha = 1f,
                modifier = Modifier.padding(top = 16.dp),
            )
            UltiAttemptResult.Failed -> SocialBodyText(
                stringResource(R.string.social_ulti_failed, battle.rivalUsername),
                alpha = 1f,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * How the ulti ended (Figma 2450:208 and 2450:223, and the defender's side of M4). The damage is the server's number: 45, or 23 with
 * the shield. The defender sees "Escudo activo" when they solved their challenge first.
 */
@Composable
internal fun UltiResultPanel(result: UltiResultView, rival: String) {
    val hit = result.damage > 0
    val title: String
    val body: String
    val detail: String
    val icon: String
    if (result.byMe) {
        icon = stringResource(if (hit) R.string.social_ulti_done_icon else R.string.social_ulti_fail_icon)
        title = stringResource(if (hit) R.string.social_ulti_ok_title else R.string.social_ulti_fail_title)
        body = when {
            !hit -> stringResource(R.string.social_ulti_fail_body)
            result.shield -> stringResource(R.string.social_ulti_ok_shield_body, rival)
            else -> stringResource(R.string.social_ulti_ok_body)
        }
        detail = if (hit) stringResource(R.string.social_ulti_ok_damage, result.damage, rival) else stringResource(R.string.social_ulti_fail_damage)
    } else {
        icon = stringResource(if (!hit) R.string.social_ulti_fail_icon else if (result.shield) R.string.social_ulti_shield_icon else R.string.social_ulti_done_icon)
        title = when {
            !hit -> stringResource(R.string.social_ulti_rival_failed_title, rival)
            result.shield -> stringResource(R.string.social_ulti_shield_title)
            else -> stringResource(R.string.social_ulti_hit_title)
        }
        body = when {
            !hit -> stringResource(R.string.social_ulti_rival_failed_body)
            result.shield -> stringResource(R.string.social_ulti_shield_body, rival)
            else -> stringResource(R.string.social_ulti_hit_body, rival)
        }
        detail = if (hit) stringResource(R.string.social_ulti_received_damage, result.damage) else stringResource(R.string.social_ulti_fail_damage)
    }
    val good = if (result.byMe) hit else !hit || result.shield
    val accent = if (good) NightWatchGold else FailRed
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 36.dp)) {
        BigIcon(icon, if (good) NightWatchGold else Color(0xFFA1A1A1))
        Text(
            text = title,
            color = if (good) NightWatchGold else NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        SocialBodyText(body, modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .background(SocialElevated, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.5.dp, accent), RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp),
        ) {
            Text(text = detail, color = accent, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BigIcon(emoji: String, background: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp).background(background, CircleShape)) {
        Text(text = emoji, fontSize = 44.sp)
    }
}
