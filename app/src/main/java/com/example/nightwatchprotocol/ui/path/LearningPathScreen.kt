package com.example.nightwatchprotocol.ui.path

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.home.HomeTopBar
import com.example.nightwatchprotocol.ui.home.PillColor
import com.example.nightwatchprotocol.ui.home.showNotice
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

/** Figma color/bg/elevated (#3E6E71): the ring of a completed island (2266:98). */
private val CompletedRing = Color(0xFF3E6E71)

/** The design is drawn on a 412 dp wide map (MapContent 2266:82) and scaled to the width of the phone. */
private const val DESIGN_WIDTH = 412f
private const val DESIGN_MAP_HEIGHT = 1440f

/** Island positions (left, top) and sizes in the 412 x 1440 map, in the order of the topics (Figma 2266:97 to 2266:122). */
private val IslandPositions = listOf(
    28.dp to 13.dp,
    254.dp to 214.dp,
    34.dp to 394.dp,
    254.dp to 597.dp,
    25.dp to 816.dp,
    244.dp to 995.dp,
)
private val IslandSize = 92.dp
private val FinalTestPosition = 207.dp to 1310.dp
private val FinalTestSize = 108.dp

/**
 * SUPUESTO: the labels are two lines in Figma ("Isla de las / Contraseñas", "Islas de Redes / Seguras"). They are not
 * stored with a break, so they wrap at this width, which gives the same lines for the six names.
 */
private val LabelWidth = 98.dp

/**
 * The "Ruta" tab (Figma 2266:72). [snackbarHostState] is the one of Home, so the notices appear above the bottom bar.
 */
@Composable
fun LearningPathTab(
    snackbarHostState: SnackbarHostState,
    onOpenLesson: (LearningTopic) -> Unit,
    viewModel: LearningPathViewModel = viewModel(factory = LearningPathViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lockedIsland = stringResource(R.string.learning_path_locked_island)
    val lockedFinalTest = stringResource(R.string.learning_path_locked_final_test)
    val comingSoon = stringResource(R.string.learning_coming_soon)

    // Read again each time the tab shows or Home comes back (after a lesson), so the map follows the saved progress.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LearningPathEvent.OpenLesson -> onOpenLesson(event.topic)
                is LearningPathEvent.ShowMessage -> showNotice(
                    snackbarHostState,
                    when (event.message) {
                        PathMessage.CompletePreviousIsland -> lockedIsland
                        PathMessage.CompleteAllIslands -> lockedFinalTest
                        PathMessage.ComingSoon -> comingSoon
                    },
                )
            }
        }
    }

    LearningPathContent(
        uiState = uiState,
        onIslandClick = viewModel::onIslandClicked,
        onFinalTestClick = viewModel::onFinalTestClicked,
        onRetry = viewModel::refresh,
    )
}

@Composable
private fun LearningPathContent(
    uiState: LearningPathUiState,
    onIslandClick: (LearningTopic) -> Unit,
    onFinalTestClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(title = stringResource(R.string.learning_path_title))
        Spacer(Modifier.height(12.dp))
        ProgressCard(uiState, modifier = Modifier.padding(horizontal = 20.dp))
        (uiState.status as? PathStatus.Failed)?.let { failed ->
            FailureNotice(offline = failed.offline, onRetry = onRetry, modifier = Modifier.padding(horizontal = 20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.islands.isEmpty() && uiState.status is PathStatus.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = NightWatchOnBackground)
            } else {
                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    RouteMap(uiState, onIslandClick, onFinalTestClick)
                }
            }
        }
    }
}

/** "🧭 Tu nivel: …", "N de 7 islas completadas" and the bar (2266:76 to 2266:80). */
@Composable
private fun ProgressCard(uiState: LearningPathUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(PillColor, shape)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.learning_path_level, uiState.level ?: "…"),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.learning_path_progress, uiState.completedCount, uiState.totalCount),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp).alpha(0.85f),
        )
        // The bar: a muted track and a gold fill of completed / total (146 of 340 for 3 of 7).
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(8.dp)
                .background(NightWatchSurfaceButtonMuted, RoundedCornerShape(4.dp)),
        ) {
            val fraction = if (uiState.totalCount == 0) 0f else uiState.completedCount.toFloat() / uiState.totalCount
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(NightWatchGold, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** The map still shows island 1 open; this says why the rest is locked and offers "Reintentar". */
@Composable
private fun FailureNotice(offline: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = stringResource(if (offline) R.string.learning_path_error_offline else R.string.learning_path_error),
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

/** The 412 x 1440 map of the design, scaled to the width of the screen so taps and text scale with it. */
@Composable
private fun RouteMap(
    uiState: LearningPathUiState,
    onIslandClick: (LearningTopic) -> Unit,
    onFinalTestClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val designWidth = DESIGN_WIDTH.dp.roundToPx()
                val designHeight = DESIGN_MAP_HEIGHT.dp.roundToPx()
                val scale = constraints.maxWidth.toFloat() / designWidth
                val placeable = measurable.measure(Constraints.fixed(designWidth, designHeight))
                layout(constraints.maxWidth, (designHeight * scale).toInt()) {
                    placeable.placeWithLayer(0, 0) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
    ) {
        // The dotted gold route (2266:83): 374.6 x 1450 placed at (-10.2, 16) of the map.
        Image(
            painter = painterResource(id = R.drawable.img_route_path),
            contentDescription = null,
            modifier = Modifier.offset(x = (-10.2).dp, y = 16.dp).requiredWidth(374.628.dp).height(1450.dp),
        )
        uiState.islands.forEachIndexed { index, island ->
            val (left, top) = IslandPositions[index]
            IslandNode(
                name = island.name,
                emoji = if (island.state == IslandState.Locked) "🔒" else island.emoji,
                state = island.state,
                size = IslandSize,
                emojiSize = 28.sp,
                onClick = { onIslandClick(island.topic) },
                modifier = Modifier.offset(x = left, y = top),
            )
        }
        val (finalLeft, finalTop) = FinalTestPosition
        IslandNode(
            name = stringResource(R.string.learning_path_final_test),
            // Figma keeps 📝 on the Prueba Final even when it is locked (2266:124).
            emoji = "📝",
            state = uiState.finalTest,
            size = FinalTestSize,
            emojiSize = 34.sp,
            onClick = onFinalTestClick,
            modifier = Modifier.offset(x = finalLeft, y = finalTop),
        )
    }
}

/**
 * One island: completed = gold with a teal ring; unlocked = light with a gold ring and the dashed halo (2266:109, "Cueva del
 * Engaño Social"); locked = muted grey at 75 % (2266:114). SUPUESTO: the Prueba Final uses the same three looks; Figma
 * only draws it locked.
 */
@Composable
private fun IslandNode(
    name: String,
    emoji: String,
    state: IslandState,
    size: Dp,
    emojiSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText = stringResource(
        when (state) {
            IslandState.Completed -> R.string.learning_island_state_completed
            IslandState.Available -> R.string.learning_island_state_available
            IslandState.Locked -> R.string.learning_island_state_locked
        },
    )
    Box(modifier = modifier.size(size)) {
        if (state == IslandState.Available) {
            Image(
                painter = painterResource(id = R.drawable.img_island_halo),
                contentDescription = null,
                modifier = Modifier.offset(x = (-8).dp, y = (-8).dp).requiredWidth(size + 16.dp).height(size + 16.dp).alpha(0.5f),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .shadow(4.dp, CircleShape)
                .islandLook(state)
                // The whole node is one button for TalkBack, locked ones included (they answer with a notice).
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = name
                    stateDescription = stateText
                },
        ) {
            Text(
                text = emoji,
                fontSize = emojiSize,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            text = name,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = size + 6.dp)
                .requiredWidth(LabelWidth)
                .clearAndSetSemantics {},
        )
    }
}

private fun Modifier.islandLook(state: IslandState): Modifier = when (state) {
    IslandState.Completed -> this
        .background(NightWatchGold, CircleShape)
        .border(3.dp, CompletedRing, CircleShape)
    IslandState.Available -> this
        .background(NightWatchSurfaceButton, CircleShape)
        .border(4.dp, NightWatchGold, CircleShape)
    IslandState.Locked -> this
        .alpha(0.75f)
        .background(NightWatchSurfaceButtonMuted, CircleShape)
        .border(3.dp, NightWatchBorderAccent, CircleShape)
}
