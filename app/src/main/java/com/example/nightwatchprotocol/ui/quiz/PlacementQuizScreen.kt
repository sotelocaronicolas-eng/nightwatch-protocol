package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchFieldShape
import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

@Composable
fun PlacementQuizScreen(
    onFinished: (level: PlacementLevel, weakCategories: Set<QuizCategory>, saveFailed: Boolean) -> Unit,
    viewModel: PlacementQuizViewModel = viewModel(factory = PlacementQuizViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = uiState.progressLabel,
                color = NightWatchOnBackground,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (question != null) {
                Text(
                    text = question.text,
                    color = NightWatchOnBackground,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                )
                question.options.forEachIndexed { index, option ->
                    val isSelected = uiState.selectedOptionIndex == index
                    val borderColor = if (isSelected) NightWatchLinkAccent else NightWatchBorderAccent
                    Text(
                        text = "${'a' + index})  $option",
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyLarge,
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
            NightWatchButton(
                text = if (uiState.isSubmitting) "Guardando…" else "Siguiente",
                onClick = onNextClicked,
                enabled = uiState.selectedOptionIndex != null && !uiState.isSubmitting,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
