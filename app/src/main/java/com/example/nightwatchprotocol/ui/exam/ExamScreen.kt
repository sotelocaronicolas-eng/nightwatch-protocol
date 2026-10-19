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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma color/bg/elevated (#3E6E71): the option cards (2492:254). */
private val ElevatedColor = Color(0xFF3E6E71)

/**
 * One question of the exam, full screen and without the bottom bar (Figma 2491:243 and the other five). [onClose] leaves
 * without saving; [onFinished] receives the option index chosen for each question when the sixth is answered.
 */
@Composable
fun ExamScreen(
    onClose: () -> Unit,
    onFinished: (List<Int>) -> Unit,
    viewModel: ExamViewModel = viewModel(factory = ExamViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ExamEvent.Close -> onClose()
                is ExamEvent.Finished -> onFinished(event.answers)
            }
        }
    }

    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val status = uiState.status) {
                is ExamStatus.Question -> {
                    val question = status.question
                    ExamHeader(
                        title = stringResource(R.string.learning_exam_question_number, question.number, question.total),
                        tag = stringResource(R.string.learning_exam_question_topic, question.emoji, question.topicLabel),
                        onClose = viewModel::onCloseClicked,
                    )
                    QuestionBody(question, onOptionClick = viewModel::onOptionSelected)
                }
                else -> {
                    ExamHeader(title = "", tag = null, onClose = viewModel::onCloseClicked)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    ) {
                        if (status == ExamStatus.Failed) {
                            Text(
                                text = stringResource(R.string.learning_exam_error),
                                color = NightWatchOnBackground,
                                fontFamily = NightWatchBodyFontFamily,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = viewModel::load) {
                                Text(
                                    text = stringResource(R.string.learning_retry),
                                    color = NightWatchOnBackground,
                                    fontFamily = NightWatchBodyFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            CircularProgressIndicator(color = NightWatchOnBackground)
                            if (status == ExamStatus.Loading) {
                                Text(
                                    text = stringResource(R.string.learning_exam_loading),
                                    color = NightWatchOnBackground,
                                    fontFamily = NightWatchBodyFontFamily,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Title, the gold line under it, the "X" and the white line (2491:245, 2491:244, 2491:259, 2491:258). Shared with the
 * introduction (2486:251, 2486:250), which has the same header.
 */
@Composable
internal fun ExamHeader(title: String, tag: String?, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text(
                text = title,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp).width(300.dp),
            )
            if (tag != null) {
                Text(
                    text = tag,
                    color = NightWatchGold,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 20.dp, top = 40.dp).width(300.dp),
                )
            }
            val closeDescription = stringResource(R.string.learning_exam_close_description)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 79.dp, height = 72.dp)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = closeDescription },
            ) {
                Text(
                    text = stringResource(R.string.learning_lesson_close),
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White),
        )
    }
}

/**
 * The question and its four options (2492:253 to 2492:264). The text starts 196 dp under the line, as in the design
 * (272 dp from the top). The grey arc at the bottom of Figma (2506:252) is drawn at the end.
 */
@Composable
private fun QuestionBody(question: ExamQuestionUi, onOptionClick: (questionIndex: Int, optionIndex: Int) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = question.text,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp, top = 196.dp, bottom = 10.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 90.dp)) {
                question.options.forEachIndexed { optionIndex, label ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(ElevatedColor, RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { onOptionClick(question.index, optionIndex) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = label,
                            color = NightWatchOnBackground,
                            fontFamily = NightWatchBodyFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        BottomArc(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * SUPUESTO: the grey ellipse of Figma (2506:252, 445 x 170 at the bottom edge, 3 dp border) as a decoration only; it is
 * drawn in Compose and not as the exported asset. It carries no information (it is not a progress bar).
 */
@Composable
private fun BottomArc(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(y = 65.dp)
            .requiredSize(width = 445.dp, height = 170.dp)
            .background(NightWatchSurfaceButton, RoundedCornerShape(percent = 50))
            .border(3.dp, NightWatchBorderAccent, RoundedCornerShape(percent = 50)),
    )
}
