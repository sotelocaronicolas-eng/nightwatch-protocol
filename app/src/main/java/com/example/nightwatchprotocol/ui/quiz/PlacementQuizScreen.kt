package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

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
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(uiState.progressLabel, color = NightWatchOnBackground)
            if (question != null) {
                Text(
                    text = question.text,
                    color = NightWatchOnBackground,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                )
                question.options.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.selectedOptionIndex == index,
                                onClick = { onOptionSelected(index) },
                            )
                            .testTag("quiz_option_$index")
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = uiState.selectedOptionIndex == index,
                            onClick = { onOptionSelected(index) },
                        )
                        Text(option, color = NightWatchOnBackground)
                    }
                }
            }
            NightWatchButton(
                text = "Siguiente",
                onClick = onNextClicked,
                enabled = uiState.selectedOptionIndex != null,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
