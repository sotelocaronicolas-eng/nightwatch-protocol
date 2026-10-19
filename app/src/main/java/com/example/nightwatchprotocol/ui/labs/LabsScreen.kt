package com.example.nightwatchprotocol.ui.labs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LabDifficulty
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.ui.home.HomeTopBar
import com.example.nightwatchprotocol.ui.home.showNotice
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma color/bg/elevated (#3E6E71): the icon circle and the difficulty chip (2271:92, 2271:95). */
private val ElevatedColor = Color(0xFF3E6E71)

/** Figma color/text/on-light (#162523). */
private val TextOnLight = Color(0xFF162523)

/** The opacity of a locked lab card (2271:113, opacity 60%). */
private const val LOCKED_ALPHA = 0.6f

/**
 * The "Labs" tab (Figma 2271:84). [snackbarHostState] is the one of Home, so the notices appear above the bottom bar.
 */
@Composable
fun LabsTab(
    snackbarHostState: SnackbarHostState,
    onOpenLab: (LearningTopic) -> Unit,
    onOpenExam: () -> Unit,
    viewModel: LabsViewModel = viewModel(factory = LabsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lockedLab = stringResource(R.string.learning_labs_locked_lab)
    val lockedExam = stringResource(R.string.learning_labs_locked_exam)

    // Read again each time the tab shows or Home comes back (after a lab), so the cards follow the saved progress.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LabsEvent.OpenLab -> onOpenLab(event.topic)
                is LabsEvent.ShowMessage -> showNotice(
                    snackbarHostState,
                    when (event.message) {
                        LabsMessage.CompletePreviousIsland -> lockedLab
                        LabsMessage.CompleteAllIslands -> lockedExam
                    },
                )
                LabsEvent.OpenExam -> onOpenExam()
            }
        }
    }

    LabsContent(
        uiState = uiState,
        onLabClick = viewModel::onLabClicked,
        onFinalTestClick = viewModel::onFinalTestClicked,
        onRetry = viewModel::refresh,
    )
}

@Composable
private fun LabsContent(
    uiState: LabsUiState,
    onLabClick: (LearningTopic) -> Unit,
    onFinalTestClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(title = stringResource(R.string.learning_labs_title), titleTop = 25.dp)
        Text(
            text = stringResource(R.string.learning_labs_subtitle),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).alpha(0.85f),
        )
        (uiState.status as? LabsStatus.Failed)?.let { failed ->
            FailureNotice(offline = failed.offline, onRetry = onRetry, modifier = Modifier.padding(horizontal = 20.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.labs.isEmpty() && uiState.status is LabsStatus.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = NightWatchOnBackground)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                ) {
                    uiState.labs.forEach { lab ->
                        LabCard(
                            emoji = lab.emoji,
                            title = lab.title,
                            difficulty = difficultyLabel(lab.difficulty),
                            state = lab.state,
                            onClick = { onLabClick(lab.topic) },
                        )
                    }
                    // The "Examen Final" card (2486:241): it exists even if the labs could not be read.
                    LabCard(
                        emoji = stringResource(R.string.learning_labs_final_exam_emoji),
                        title = stringResource(R.string.learning_labs_final_exam),
                        difficulty = stringResource(R.string.learning_labs_difficulty_exam),
                        state = uiState.finalTest,
                        onClick = onFinalTestClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: LabDifficulty): String = stringResource(
    when (difficulty) {
        LabDifficulty.FACIL -> R.string.learning_labs_difficulty_easy
        LabDifficulty.MEDIO -> R.string.learning_labs_difficulty_medium
        LabDifficulty.DIFICIL -> R.string.learning_labs_difficulty_hard
    },
)

/** This says why the list shows only lab 1 open and offers "Reintentar". */
@Composable
private fun FailureNotice(offline: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = stringResource(if (offline) R.string.learning_labs_error_offline else R.string.learning_labs_error),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f).alpha(0.85f),
        )
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.learning_retry),
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * A lab card (2271:91): light, 118 dp tall in the design (taller if the text needs it), 2 dp border, radius 16. The whole
 * card is the tap target. A locked card is dimmed to 60 % and keeps its "Comenzar →" as Figma draws it (2271:113); tapping
 * it only says why.
 */
@Composable
private fun LabCard(
    emoji: String,
    title: String,
    difficulty: String,
    state: IslandState,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val stateText = stringResource(
        when (state) {
            IslandState.Completed -> R.string.learning_island_state_completed
            IslandState.Available -> R.string.learning_island_state_available
            IslandState.Locked -> R.string.learning_island_state_locked
        },
    )
    val description = stringResource(R.string.learning_labs_card_description, title, difficulty, stateText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .alpha(if (state == IslandState.Locked) LOCKED_ALPHA else 1f)
            .shadow(4.dp, shape)
            .background(NightWatchSurfaceButton, shape)
            .border(2.dp, NightWatchBorderAccent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = stateText
            }
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).background(ElevatedColor, CircleShape),
        ) {
            Text(text = emoji, fontSize = 22.sp, color = Color.Black, modifier = Modifier.clearAndSetSemantics {})
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = title,
                color = TextOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .widthIn(min = 60.dp)
                    .height(20.dp)
                    .background(ElevatedColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = difficulty,
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (state == IslandState.Completed) {
                Text(
                    text = stringResource(R.string.learning_labs_completed),
                    color = TextOnLight,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                StartButton(modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

/** "Comenzar →" (2271:111): 36 dp tall pill, 110 dp wide in the design. Drawn only; the card takes the tap. */
@Composable
private fun StartButton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 110.dp)
            .height(36.dp)
            .shadow(4.dp, shape)
            .background(NightWatchSurfaceButton, shape)
            .padding(horizontal = 7.dp),
    ) {
        Text(
            text = stringResource(R.string.learning_labs_start),
            color = TextOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
        )
    }
}
