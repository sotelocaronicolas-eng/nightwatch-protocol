package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchFieldShape
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchOnButton
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "¡Resultados listos!",
                color = NightWatchOnBackground,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Tu nivel: ${uiState.level}",
                color = NightWatchOnButton,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .clip(NightWatchPillShape)
                    .background(NightWatchSurfaceButton)
                    .border(2.dp, NightWatchBorderAccent, NightWatchPillShape)
                    .padding(horizontal = 32.dp, vertical = 14.dp),
            )
            Text(
                text = "Tu plan de estudio recomendado:",
                color = NightWatchOnBackground,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            uiState.studyPlan.forEach { topic ->
                Text(
                    text = topic,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(NightWatchFieldShape)
                        .background(NightWatchSurfaceButtonMuted)
                        .border(1.5.dp, NightWatchBorderAccent, NightWatchFieldShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            if (uiState.saveFailed) {
                Text(
                    text = "No pudimos guardar tu resultado; es posible que veas este quiz de nuevo la próxima vez.",
                    color = NightWatchOnBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
            NightWatchButton(
                text = "Continuar",
                onClick = onContinueClicked,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
