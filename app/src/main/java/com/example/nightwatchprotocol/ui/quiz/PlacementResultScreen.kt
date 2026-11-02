package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.common.asString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchFieldShape
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchOnButton
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSectionTitleStyle
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

@Composable
fun PlacementResultScreen(
    onContinue: () -> Unit,
    viewModel: PlacementResultViewModel = viewModel(factory = PlacementResultViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            // Figma 2017:126 puts the title 95 px from the top of a frame that includes the status bar, so the
            // top margin is added below the system bar instead of drawing the title under it.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.quiz_result_title),
                color = NightWatchOnBackground,
                style = NightWatchSectionTitleStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.quiz_result_level, uiState.level.asString()),
                color = NightWatchOnButton,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .clip(NightWatchPillShape)
                    .background(NightWatchSurfaceButton)
                    .border(2.dp, NightWatchBorderAccent, NightWatchPillShape)
                    .padding(horizontal = 32.dp, vertical = 14.dp),
            )
            Text(
                text = stringResource(R.string.quiz_result_plan_title),
                color = NightWatchOnBackground,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            uiState.studyPlan.forEach { topic ->
                Text(
                    text = topic.asString(),
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 14.sp,
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
                    text = stringResource(R.string.quiz_result_save_failed),
                    color = NightWatchOnBackground,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
            NightWatchButton(
                text = stringResource(R.string.quiz_result_continue),
                onClick = onContinueClicked,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
