package com.example.nightwatchprotocol.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.Lesson
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.home.PillColor
import com.example.nightwatchprotocol.ui.home.showNotice
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma color/bg/elevated (#3E6E71), the ring of the lesson icon (2294:117), and color/text/on-light (#162523). */
private val IconRing = Color(0xFF3E6E71)
private val TextOnLight = Color(0xFF162523)

/**
 * A lesson, full screen and without the bottom bar (Figma 2294:112 to 2294:202). [onClose] goes back to where the person
 * was; [onOpenLab] opens the lab of the lesson's topic.
 */
@Composable
fun LessonScreen(
    onClose: () -> Unit,
    onOpenLab: (LearningTopic) -> Unit,
    viewModel: LessonViewModel = viewModel(factory = LessonViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedOffline = stringResource(R.string.learning_lesson_save_failed_offline)
    val saveFailed = stringResource(R.string.learning_lesson_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LessonEvent.Close -> onClose()
                is LessonEvent.OpenLab -> onOpenLab(event.topic)
                is LessonEvent.SaveFailed -> showNotice(snackbarHostState, if (event.offline) saveFailedOffline else saveFailed)
            }
        }
    }

    LessonContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onClose = viewModel::onCloseClicked,
        onGoToLab = viewModel::onGoToLabClicked,
        onRetry = viewModel::load,
    )
}

@Composable
private fun LessonContent(
    uiState: LessonUiState,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onGoToLab: () -> Unit,
    onRetry: () -> Unit,
) {
    NightWatchScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                LessonHeader(
                    title = (uiState.status as? LessonStatus.Ready)?.lesson?.title.orEmpty(),
                    moduleNumber = uiState.moduleNumber,
                    moduleTotal = uiState.moduleTotal,
                    onClose = onClose,
                )
                when (val status = uiState.status) {
                    LessonStatus.Loading -> Centered {
                        CircularProgressIndicator(color = NightWatchOnBackground)
                        Text(
                            text = stringResource(R.string.learning_lesson_loading),
                            color = NightWatchOnBackground,
                            fontFamily = NightWatchBodyFontFamily,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    LessonStatus.Failed -> Centered {
                        Text(
                            text = stringResource(R.string.learning_lesson_error),
                            color = NightWatchOnBackground,
                            fontFamily = NightWatchBodyFontFamily,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRetry) {
                            Text(
                                text = stringResource(R.string.learning_retry),
                                color = NightWatchOnBackground,
                                fontFamily = NightWatchBodyFontFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    is LessonStatus.Ready -> LessonBody(status.lesson, onGoToLab)
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
    ) { content() }
}

/** Title, "MÓDULO N de 6", the "X" and the white line (2294:114, 2294:115, 2435:156, 2435:155). */
@Composable
private fun LessonHeader(title: String, moduleNumber: Int, moduleTotal: Int, onClose: () -> Unit) {
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
            Text(
                text = stringResource(R.string.learning_lesson_module, moduleNumber, moduleTotal),
                color = NightWatchGold,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 20.dp, top = 40.dp),
            )
            val closeDescription = stringResource(R.string.learning_lesson_close_description)
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

@Composable
private fun LessonBody(lesson: Lesson, onGoToLab: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        // The icon circle (2294:117): gold with a teal ring, 96 dp, 68 dp below the line.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 68.dp)
                .size(96.dp)
                .background(NightWatchGold, CircleShape)
                .border(4.dp, IconRing, CircleShape),
        ) {
            Text(text = lesson.emoji, fontSize = 42.sp, color = Color.Black, modifier = Modifier.clearAndSetSemantics {})
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 22.dp).fillMaxWidth(),
        ) {
            lesson.cards.forEach { card ->
                LessonCardView(heading = "${card.emoji} ${card.title}", text = card.text)
            }
        }
        GoToLabButton(onClick = onGoToLab, modifier = Modifier.padding(top = 20.dp, bottom = 24.dp))
    }
}

/** A card of the lesson (2294:119): 372 wide, 128 tall in the design (taller if the text needs it), radius 16. */
@Composable
private fun LessonCardView(heading: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .background(PillColor, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Text(
            text = heading,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 16.sp,
        )
        Text(
            text = text,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "🧪 Ir al laboratorio →" (2294:128): light pill, 48 dp tall, full width. */
@Composable
private fun GoToLabButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(4.dp, shape)
            .background(NightWatchSurfaceButton, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.learning_lesson_go_to_lab),
            color = TextOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}
