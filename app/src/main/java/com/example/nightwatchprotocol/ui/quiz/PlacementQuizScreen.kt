package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchFieldShape
import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSectionTitleStyle
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

@Composable
fun PlacementQuizScreen(
    onFinished: (level: PlacementLevel, weakCategories: Set<QuizCategory>, saveFailed: Boolean) -> Unit,
    viewModel: PlacementQuizViewModel = viewModel(factory = PlacementQuizViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlacementQuizEvent.NavigateToResult ->
                    onFinished(event.level, event.weakCategories, event.saveFailed)
            }
        }
    }

    PlacementQuizScreenContent(
        uiState = uiState,
        onOptionSelected = viewModel::onOptionSelected,
        onNextClicked = viewModel::onNextClicked,
    )
}

/*
 * Figma "PREGUNTA 1" (2017:30): progress bar, "Pregunta X de 6" and the category pinned at the top (below the status
 * bar); the question and its four options centred in the space that is left; the button near the bottom edge. If the
 * question does not fit (small screens, large fonts) only the middle part scrolls, the button stays reachable.
 */
@Composable
private fun PlacementQuizScreenContent(
    uiState: PlacementQuizUiState,
    onOptionSelected: (Int) -> Unit,
    onNextClicked: () -> Unit,
) {
    val question = uiState.currentQuestion

    NightWatchScreenBackground {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
            return@NightWatchScreenBackground
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QuizHeader(uiState)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val minHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (question != null) {
                        Text(
                            text = question.text,
                            color = NightWatchOnBackground,
                            style = NightWatchSectionTitleStyle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        )
                        question.options.forEachIndexed { index, option ->
                            val isSelected = uiState.selectedOptionIndex == index
                            val borderColor = if (isSelected) NightWatchLinkAccent else NightWatchBorderAccent
                            Text(
                                text = "${'a' + index})  $option",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyLarge,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(NightWatchFieldShape)
                                    .background(NightWatchSurfaceButtonMuted)
                                    .border(2.dp, borderColor, NightWatchFieldShape)
                                    .selectable(selected = isSelected, onClick = { onOptionSelected(index) })
                                    .testTag("quiz_option_$index")
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            }
            NightWatchButton(
                text = when {
                    uiState.isSubmitting -> "Guardando…"
                    uiState.questions.isNotEmpty() && uiState.currentIndex == uiState.questions.lastIndex -> "Finalizar"
                    else -> "Siguiente"
                },
                onClick = onNextClicked,
                enabled = uiState.selectedOptionIndex != null && !uiState.isSubmitting,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun QuizHeader(uiState: PlacementQuizUiState) {
    val total = uiState.questions.size
    val fraction = if (total == 0) 0f else (uiState.currentIndex + 1).toFloat() / total
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(NightWatchPillShape)
                .background(NightWatchSurfaceButtonMuted),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(NightWatchPillShape)
                    .background(NightWatchLinkAccent),
            )
        }
        Text(
            text = uiState.progressLabel,
            color = NightWatchOnBackground,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
        )
        uiState.currentQuestion?.let { question ->
            Text(
                text = categoryLabel(question.category),
                color = NightWatchOnBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

private fun categoryLabel(category: QuizCategory): String = when (category) {
    QuizCategory.CONCEPTOS_BASICOS -> "🛡️ Conceptos Básicos"
    QuizCategory.CONTRASENAS -> "🔑 Contraseñas y Autenticación"
    QuizCategory.INGENIERIA_SOCIAL -> "🎣 Phishing / Ingeniería Social"
    QuizCategory.REDES_MALWARE -> "🌐 Redes y Malware"
}
