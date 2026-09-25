package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

@Composable
fun PlacementResultScreen(
    onContinue: () -> Unit,
    viewModel: PlacementResultViewModel = viewModel(factory = PlacementResultViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PlacementResultEvent.NavigateToMainMenu -> onContinue()
            }
        }
    }

    PlacementResultScreenContent(
        uiState = uiState,
        onContinueClicked = viewModel::onContinueClicked,
    )
}

@Composable
private fun PlacementResultScreenContent(
    uiState: PlacementResultUiState,
    onContinueClicked: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Tu nivel: ${uiState.level}", color = NightWatchOnBackground)
            Text(
                "Tu plan de estudio recomendado:",
                color = NightWatchOnBackground,
                modifier = Modifier.padding(top = 16.dp),
            )
            uiState.studyPlan.forEach { topic ->
                Text("• $topic", color = NightWatchOnBackground, modifier = Modifier.padding(top = 8.dp))
            }
            if (uiState.saveFailed) {
                Text(
                    "No pudimos guardar tu resultado; es posible que veas este quiz de nuevo la próxima vez.",
                    color = NightWatchOnBackground,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            NightWatchButton(
                text = "Continuar",
                onClick = onContinueClicked,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
