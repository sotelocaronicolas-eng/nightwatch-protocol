package com.example.nightwatchprotocol.ui.postauth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground

@Composable
fun PostAuthGateScreen(
    onNavigateToMainMenu: () -> Unit,
    onNavigateToPlacementQuiz: () -> Unit,
    viewModel: PostAuthGateViewModel = viewModel(factory = PostAuthGateViewModel.Factory),
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PostAuthGateEvent.NavigateToMainMenu -> onNavigateToMainMenu()
                PostAuthGateEvent.NavigateToPlacementQuiz -> onNavigateToPlacementQuiz()
            }
        }
    }

    NightWatchScreenBackground {
        CircularProgressIndicator(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
    }
}
