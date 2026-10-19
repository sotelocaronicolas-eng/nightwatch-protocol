package com.example.nightwatchprotocol.ui.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.LabObjective
import com.example.nightwatchprotocol.data.learning.LabTerminal
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.keepVisibleWithKeyboard
import com.example.nightwatchprotocol.ui.theme.NightWatchBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma color/bg/elevated (#3E6E71): objective cards, output and input (2374:279, 2374:294, 2374:296). */
private val ElevatedColor = Color(0xFF3E6E71)

/** Figma color/text/on-light (#162523). */
private val TextOnLight = Color(0xFF162523)

/** SUPUESTO: Figma uses IBM Plex Mono, which is not in the project; the system monospace font stands in for it. */
private val MonoFamily = FontFamily.Monospace

/**
 * The terminal of a lab, full screen and without the bottom bar (Figma 2374:273 and the other five terminals). [onClose]
 * leaves without saving and without a dialog (SUPUESTO).
 */
@Composable
fun LabScreen(
    onClose: () -> Unit,
    onOpenReport: (LearningTopic, List<Int>) -> Unit,
    viewModel: LabViewModel = viewModel(factory = LabViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LabEvent.Close -> onClose()
                is LabEvent.OpenReport -> onOpenReport(event.topic, event.completedObjectives)
            }
        }
    }

    LabContent(
        uiState = uiState,
        onClose = viewModel::onCloseClicked,
        onObjectiveClick = viewModel::onObjectiveClicked,
        onInputChange = viewModel::onInputChanged,
        onSend = viewModel::onSendClicked,
        onFinish = viewModel::onFinishClicked,
        onRetry = viewModel::load,
    )
}

@Composable
private fun LabContent(
    uiState: LabUiState,
    onClose: () -> Unit,
    onObjectiveClick: (Int) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
) {
    NightWatchScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                val terminal = (uiState.status as? LabStatus.Ready)?.terminal
                LabHeader(title = terminal?.lab?.terminalTitle.orEmpty(), onClose = onClose)
                when (uiState.status) {
                    LabStatus.Loading -> Centered {
                        CircularProgressIndicator(color = NightWatchOnBackground)
                        Text(
                            text = stringResource(R.string.learning_lab_loading),
                            color = NightWatchOnBackground,
                            fontFamily = NightWatchBodyFontFamily,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    LabStatus.Failed -> Centered {
                        Text(
                            text = stringResource(R.string.learning_lab_error),
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
                    is LabStatus.Ready -> LabBody(
                        terminal = uiState.status.terminal,
                        input = uiState.input,
                        onObjectiveClick = onObjectiveClick,
                        onInputChange = onInputChange,
                        onSend = onSend,
                        onFinish = onFinish,
                    )
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

/** Title, "Terminal", the "X" and the white line (2374:276, 2374:277, 2404:212, 2404:211). */
@Composable
private fun LabHeader(title: String, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text(
                text = title,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp).width(300.dp),
            )
            Text(
                text = stringResource(R.string.learning_lab_terminal),
                color = NightWatchGold,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, top = 40.dp),
            )
            val closeDescription = stringResource(R.string.learning_lab_close_description)
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
private fun LabBody(
    terminal: LabTerminal,
    input: String,
    onObjectiveClick: (Int) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onFinish: () -> Unit,
) {
    // The whole body scrolls, so with the keyboard open (or in landscape) the field and the buttons can be brought into view.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        // SUPUESTO: in Figma this label sits behind the first card (2374:278 at y=122, card at y=104); here it is above the cards.
        Text(
            text = stringResource(R.string.learning_lab_objectives),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            terminal.lab.objectives.forEachIndexed { index, objective ->
                ObjectiveCard(
                    objective = objective,
                    done = index in terminal.completedObjectives,
                    onClick = { onObjectiveClick(index) },
                )
            }
        }
        TerminalOutput(terminal, modifier = Modifier.padding(top = 16.dp))
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 24.dp).keepVisibleWithKeyboard()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CommandInput(input = input, onInputChange = onInputChange, onSend = onSend, modifier = Modifier.weight(1f))
                SendButton(onClick = onSend)
            }
            FinishButton(onClick = onFinish, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** An objective (2374:279): title, its command in a chip, and a checkbox that turns gold with a check mark once done (2374:309). */
@Composable
private fun ObjectiveCard(objective: LabObjective, done: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (done) R.string.learning_lab_objective_done else R.string.learning_lab_objective_pending)
    val description = stringResource(R.string.learning_lab_objective_description, objective.title, objective.command, state)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(ElevatedColor, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
                .size(18.dp)
                .then(
                    if (done) {
                        Modifier.background(NightWatchGold, RoundedCornerShape(8.dp))
                    } else {
                        Modifier.border(1.5.dp, NightWatchBorderAccent, RoundedCornerShape(8.dp))
                    },
                ),
        ) {
            if (done) {
                // A vector icon, not the "✓" glyph: the glyph sits low in its line box and cannot be centred by the Box.
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = NightWatchBackground,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 44.dp, top = 14.dp, end = 16.dp, bottom = 12.dp)) {
            Text(
                text = objective.title,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .heightIn(min = 24.dp)
                    .background(NightWatchBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = objective.command,
                    color = NightWatchGold,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * The output box (2374:294): 200 dp, the briefing at first and then "$ command" followed by what the terminal answered
 * (2374:328). It scrolls and stays at the end as lines arrive.
 */
@Composable
private fun TerminalOutput(terminal: LabTerminal, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val scroll = rememberScrollState()
    // `map` is inline, so the string resource can be read for each entry.
    val lines = terminal.transcript.map { entry ->
        stringResource(R.string.learning_lab_transcript_command, entry.input) + "\n" + entry.output
    }
    val text = if (lines.isEmpty()) terminal.lab.briefing else lines.joinToString("\n")
    LaunchedEffect(terminal.transcript.size) { scroll.scrollTo(scroll.maxValue) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(ElevatedColor, shape)
            .border(1.dp, NightWatchBorderAccent, shape)
            .verticalScroll(scroll)
            .padding(15.dp),
    ) {
        Text(text = text, color = NightWatchOnBackground, fontFamily = MonoFamily, fontSize = 12.sp)
    }
}

/** The field (2374:296): "> escribe un comando…", 44 dp. Capital letters and auto-correct are off: it is a command line. */
@Composable
private fun CommandInput(input: String, onInputChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.learning_lab_input_description)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(44.dp)
            .background(ElevatedColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.learning_lab_input_prompt),
            color = NightWatchOnBackground,
            fontFamily = MonoFamily,
            fontSize = 13.sp,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Box(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            if (input.isEmpty()) {
                Text(
                    text = stringResource(R.string.learning_lab_input_placeholder),
                    color = NightWatchOnBackground,
                    fontFamily = MonoFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                textStyle = TextStyle(color = NightWatchOnBackground, fontFamily = MonoFamily, fontSize = 12.sp),
                cursorBrush = SolidColor(NightWatchOnBackground),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            )
        }
    }
}

/** "Enviar" (2374:299): 96 x 44, light, radius 12. */
@Composable
private fun SendButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 96.dp, height = 44.dp)
            .background(NightWatchSurfaceButton, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.learning_lab_send),
            color = TextOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

/**
 * "Finalizar laboratorio" (2374:301): light pill, 48 dp. Always enabled. Figma draws its text white on light grey, which is
 * hard to read; the dark text of the other light buttons is used instead.
 */
@Composable
private fun FinishButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            text = stringResource(R.string.learning_lab_finish),
            color = TextOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}
