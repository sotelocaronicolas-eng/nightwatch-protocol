package com.example.nightwatchprotocol.ui.examresult

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** Figma color/bg/elevated (#3E6E71): the StatBox (2491:360). */
private val ElevatedColor = Color(0xFF3E6E71)

/** Figma color/text/on-light (#162523). */
private val TextOnLight = Color(0xFF162523)

/**
 * The result of the exam, full screen and without the bottom bar (Figma 2491:357, 2498:512 and 2491:345). [onGo] leaves for
 * the destination of the tapped button or row.
 */
@Composable
fun ExamResultScreen(
    onGo: (ExamDestination) -> Unit,
    viewModel: ExamResultViewModel = viewModel(factory = ExamResultViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExamResultEvent.Go -> onGo(event.destination)
            }
        }
    }

    // The buttons wait for the save, so the screens that open next already read the attempt.
    val enabled = uiState.save !is ExamSaveStatus.Saving

    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val status = uiState.status) {
                ExamResultStatus.Loading -> Centered {
                    CircularProgressIndicator(color = NightWatchOnBackground)
                    Text(
                        text = stringResource(R.string.learning_exam_result_loading),
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                ExamResultStatus.Failed -> Centered {
                    Text(
                        text = stringResource(R.string.learning_exam_result_error),
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
                }
                is ExamResultStatus.Ready -> {
                    ResultBody(
                        outcome = status.outcome,
                        save = uiState.save,
                        enabled = enabled,
                        onRetrySave = viewModel::onRetrySaveClicked,
                        onGo = viewModel::onDestinationClicked,
                        modifier = Modifier.weight(1f),
                    )
                    ResultButtons(
                        passed = status.outcome != ExamOutcome.NotPassed,
                        enabled = enabled,
                        onGo = viewModel::onDestinationClicked,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp),
    ) { content() }
}

/** Icon, title, sentence, StatBox and the three lines (2491:368, 2491:358, 2491:359, 2491:360, 2491:363 to 2491:365). */
@Composable
private fun ResultBody(
    outcome: ExamOutcome,
    save: ExamSaveStatus,
    enabled: Boolean,
    onRetrySave: () -> Unit,
    onGo: (ExamDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val passed = outcome != ExamOutcome.NotPassed
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // 🏆 when passed (2491:368), 📖 when not (2494:246, 44 dp lower in the design).
        Spacer(Modifier.height(if (passed) 51.dp else 95.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(100.dp)) {
            Text(
                text = stringResource(if (passed) R.string.learning_report_trophy else R.string.learning_exam_result_fail_emoji),
                fontSize = 64.sp,
                color = Color.Black,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(
                when (outcome) {
                    ExamOutcome.Perfect -> R.string.learning_exam_result_pass_title
                    is ExamOutcome.OneError -> R.string.learning_exam_result_one_error_title
                    ExamOutcome.NotPassed -> R.string.learning_exam_result_fail_title
                },
            ),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                when (outcome) {
                    ExamOutcome.Perfect -> R.string.learning_exam_result_pass_summary
                    is ExamOutcome.OneError -> R.string.learning_exam_result_one_error_summary
                    ExamOutcome.NotPassed -> R.string.learning_exam_result_fail_summary
                },
            ),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        StatBox(passed, modifier = Modifier.padding(top = 30.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
        ) {
            when (outcome) {
                ExamOutcome.Perfect -> {
                    Line(stringResource(R.string.learning_exam_result_pass_row_1))
                    Line(stringResource(R.string.learning_exam_result_pass_row_2))
                    Line(stringResource(R.string.learning_exam_result_pass_row_3))
                }
                is ExamOutcome.OneError -> {
                    Line(stringResource(R.string.learning_exam_result_one_error_row_1))
                    Line(stringResource(R.string.learning_exam_result_one_error_topic, outcome.topicLabel))
                    Line(stringResource(R.string.learning_exam_result_one_error_correct, outcome.correctOption))
                }
                // SUPUESTO: Figma draws these three as plain lines; here each one is also a tap target for what it says.
                ExamOutcome.NotPassed -> {
                    Line(stringResource(R.string.learning_exam_result_fail_row_path), enabled) { onGo(ExamDestination.Path) }
                    Line(stringResource(R.string.learning_exam_result_fail_row_labs), enabled) { onGo(ExamDestination.Labs) }
                    Line(stringResource(R.string.learning_exam_result_fail_row_retry), enabled) { onGo(ExamDestination.NewAttempt) }
                }
            }
        }
        SaveNotice(save, onRetrySave, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun Line(text: String, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    // A line that is a tap target is at least 48 dp tall (touch size); a plain line keeps the 24 dp of the design.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = if (onClick != null) 48.dp else 24.dp),
    ) {
        Text(
            text = text,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 14.sp,
        )
    }
}

/**
 * The StatBox (2491:360). SUPUESTO: the congratulation is longer than Figma's "¡Subiste de nivel!" (P5 forbids that
 * promise), so it is drawn at 22 sp and may take two lines; "Sigue practicando" keeps the 32 sp of the design.
 */
@Composable
private fun StatBox(passed: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .background(ElevatedColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(if (passed) R.string.learning_exam_result_pass_stat else R.string.learning_exam_result_fail_stat),
            color = NightWatchGold,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (passed) 22.sp else 32.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (passed) R.string.learning_exam_result_pass_stat_label else R.string.learning_exam_result_fail_stat_label,
            ),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** SUPUESTO (texts not in Figma): says that the attempt is being saved, was saved, or could not be, with "Reintentar guardado". */
@Composable
private fun SaveNotice(save: ExamSaveStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (save) {
        ExamSaveStatus.NotStarted -> Unit
        ExamSaveStatus.Saving -> Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NightWatchOnBackground)
            SaveText(stringResource(R.string.learning_report_saving), modifier = Modifier.padding(start = 8.dp))
        }
        ExamSaveStatus.Saved -> SaveText(stringResource(R.string.learning_report_saved), modifier = modifier.fillMaxWidth())
        is ExamSaveStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
            SaveText(
                text = stringResource(
                    if (save.offline) R.string.learning_exam_result_save_failed_offline else R.string.learning_exam_result_save_failed,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.learning_report_retry_save),
                    color = NightWatchGold,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SaveText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 13.sp,
        modifier = modifier,
    )
}

/**
 * Passed: "ACEPTAR" (2491:367), light, to the map. Not passed: "Reintentar" (outline, 2491:354) for a new attempt and
 * "Volver al inicio" (light, 2491:355). SUPUESTO: Figma does not say where they lead; the dark text of the other light
 * buttons is used (Figma draws it white on light grey).
 */
@Composable
private fun ResultButtons(
    passed: Boolean,
    enabled: Boolean,
    onGo: (ExamDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(100.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        if (!passed) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(2.dp, NightWatchBorderAccent, shape)
                    .clickable(enabled = enabled, role = Role.Button) { onGo(ExamDestination.NewAttempt) },
            ) {
                Text(
                    text = stringResource(R.string.learning_exam_result_retry),
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(4.dp, shape)
                .background(NightWatchSurfaceButton, shape)
                .clickable(enabled = enabled, role = Role.Button) {
                    onGo(if (passed) ExamDestination.Path else ExamDestination.Home)
                },
        ) {
            Text(
                text = stringResource(if (passed) R.string.learning_exam_result_accept else R.string.learning_exam_result_home),
                color = TextOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}
