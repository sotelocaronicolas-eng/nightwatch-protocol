package com.example.nightwatchprotocol.ui.labreport

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma color/bg/elevated (#3E6E71): the StatBox (2404:190). */
private val ElevatedColor = Color(0xFF3E6E71)

/** Figma color/text/on-light (#162523). */
private val TextOnLight = Color(0xFF162523)

/**
 * The report of a lab, full screen and without the bottom bar (Figma 2273:131, 2404:243, 2374:336, 2400:144 and the other
 * four). [onBackToPath] goes to Home with Ruta; [onRetryLab] opens the same lab again from scratch.
 */
@Composable
fun LabReportScreen(
    onBackToPath: () -> Unit,
    onRetryLab: (LearningTopic) -> Unit,
    viewModel: LabReportViewModel = viewModel(factory = LabReportViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LabReportEvent.BackToPath -> onBackToPath()
                is LabReportEvent.RetryLab -> onRetryLab(event.topic)
            }
        }
    }

    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val status = uiState.status) {
                LabReportStatus.Loading -> Centered {
                    CircularProgressIndicator(color = NightWatchOnBackground)
                    Text(
                        text = stringResource(R.string.learning_report_loading),
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                LabReportStatus.Failed -> Centered {
                    Text(
                        text = stringResource(R.string.learning_report_error),
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
                    ReportButtons(onRetryLab = viewModel::onRetryLabClicked, onBackToPath = viewModel::onBackToPathClicked)
                }
                is LabReportStatus.Ready -> {
                    ReportBody(
                        content = status.content,
                        save = uiState.save,
                        onRetrySave = viewModel::onRetrySaveClicked,
                        modifier = Modifier.weight(1f),
                    )
                    ReportButtons(
                        onRetryLab = viewModel::onRetryLabClicked,
                        onBackToPath = viewModel::onBackToPathClicked,
                        // The buttons wait for the save, so the map that opens next already shows the completed lab.
                        enabled = uiState.save !is LabSaveStatus.Saving,
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

/** Trophy, title, sentence, StatBox and the list of objectives (2273:132 to 2273:140). */
@Composable
private fun ReportBody(
    content: LabReportContent,
    save: LabSaveStatus,
    onRetrySave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(51.dp))
        // 🏆 in a success (2273:132); the broken trophy of Figma in a failure (2404:259, 151 x 100).
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(100.dp)) {
            if (content.success) {
                Text(
                    text = stringResource(R.string.learning_report_trophy),
                    fontSize = 64.sp,
                    color = Color.Black,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_lab_report_failed),
                    contentDescription = stringResource(R.string.learning_report_trophy_broken_description),
                    modifier = Modifier.size(width = 151.dp, height = 100.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        // SUPUESTO: 20 / 13 / 14 sp are the sizes of the passwords report; the mission report uses 22 / 14 / 13.
        Text(
            text = if (content.success) content.successTitle else stringResource(R.string.learning_report_failure_title),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = content.summary,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        StatBox(content, modifier = Modifier.padding(top = 30.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
        ) {
            content.items.forEach { item ->
                Text(
                    text = stringResource(
                        if (item.done) R.string.learning_report_item_done else R.string.learning_report_item_missed,
                        item.label,
                    ),
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 14.sp,
                )
            }
        }
        SaveNotice(save, onRetrySave, modifier = Modifier.padding(bottom = 16.dp))
    }
}

/** SUPUESTO (texts not in Figma): says that the lab is being saved, was saved, or could not be, with "Reintentar guardado". */
@Composable
private fun SaveNotice(save: LabSaveStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (save) {
        LabSaveStatus.NotNeeded -> Unit
        LabSaveStatus.Saving -> Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NightWatchOnBackground)
            SaveText(stringResource(R.string.learning_report_saving), modifier = Modifier.padding(start = 8.dp))
        }
        LabSaveStatus.Saved -> SaveText(stringResource(R.string.learning_report_saved), modifier = modifier.fillMaxWidth())
        is LabSaveStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
            SaveText(
                text = stringResource(
                    if (save.offline) R.string.learning_report_save_failed_offline else R.string.learning_report_save_failed,
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

/** The StatBox (2404:190): the big gold number and its label. */
@Composable
private fun StatBox(content: LabReportContent, modifier: Modifier = Modifier) {
    val value = when (val metric = content.metric) {
        is ReportMetric.Percent -> stringResource(R.string.learning_report_percent, metric.value)
        is ReportMetric.EstimatedTime -> metric.text ?: stringResource(R.string.learning_report_no_time)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(ElevatedColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = value,
            color = NightWatchGold,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            maxLines = 1,
        )
        Text(
            text = content.metricLabel,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "Reintentar" (outline, 2273:141) and "Volver a la ruta" (light, 2273:143), 48 dp pills pinned to the bottom. */
@Composable
private fun ReportButtons(
    onRetryLab: () -> Unit,
    onBackToPath: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(2.dp, NightWatchBorderAccent, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onRetryLab),
        ) {
            Text(
                text = stringResource(R.string.learning_report_retry),
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
        // Figma draws this text white on light grey in some frames; the dark text of the other light buttons is used.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(4.dp, shape)
                .background(NightWatchSurfaceButton, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onBackToPath),
        ) {
            Text(
                text = stringResource(R.string.learning_report_back_to_path),
                color = TextOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}
