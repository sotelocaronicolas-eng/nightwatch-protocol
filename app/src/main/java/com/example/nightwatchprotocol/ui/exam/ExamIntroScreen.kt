package com.example.nightwatchprotocol.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.home.PillColor
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma color/bg/elevated (#3E6E71), the ring of the icon (2486:252), and color/text/on-light (#162523). */
private val IconRing = Color(0xFF3E6E71)
private val TextOnLight = Color(0xFF162523)

/**
 * The introduction of the final exam (Figma 2486:249): what it covers, how it works, what the person gets and an
 * important note. No time limit and no limit of attempts (decision P11). [onClose] leaves; [onStart] starts the questions.
 */
@Composable
fun ExamIntroScreen(onClose: () -> Unit, onStart: () -> Unit) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ExamHeader(
                title = stringResource(R.string.learning_exam_intro_title),
                tag = stringResource(R.string.learning_exam_intro_tag),
                onClose = onClose,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                // The icon circle (2486:252): gold with a teal ring, 96 dp, 32 dp under the line (104 dp from the top).
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .size(96.dp)
                        .background(NightWatchGold, CircleShape)
                        .border(4.dp, IconRing, CircleShape),
                ) {
                    Text(text = stringResource(R.string.learning_labs_final_exam_emoji), fontSize = 42.sp, color = Color.Black, modifier = Modifier.clearAndSetSemantics {})
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 22.dp, bottom = 16.dp)) {
                    InfoCard(R.string.learning_exam_intro_what_title, R.string.learning_exam_intro_what_text)
                    InfoCard(R.string.learning_exam_intro_how_title, R.string.learning_exam_intro_how_text)
                    InfoCard(R.string.learning_exam_intro_gain_title, R.string.learning_exam_intro_gain_text)
                    InfoCard(R.string.learning_exam_intro_important_title, R.string.learning_exam_intro_important_text)
                }
            }
            StartButton(onClick = onStart, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp))
        }
    }
}

/** A card of the introduction (2486:254): pill colour, radius 16, 128 dp tall in the design (taller if the text needs it). */
@Composable
private fun InfoCard(titleRes: Int, textRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .background(PillColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(textRes),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "Comenzar Examen →" (2486:263): light pill, 48 dp, full width. */
@Composable
private fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .height(48.dp)
            .shadow(4.dp, shape)
            .background(NightWatchSurfaceButton, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.learning_exam_intro_start),
            color = TextOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}
