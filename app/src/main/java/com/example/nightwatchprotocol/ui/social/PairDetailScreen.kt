package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.social.PairMetrics
import com.example.nightwatchprotocol.data.social.PairState
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/** The detail of one pair (Figma 2447:186 with ONE person in "hoy"). */
@Composable
fun PairDetailScreen(
    onClose: () -> Unit,
    onOpenLesson: (LearningTopic) -> Unit,
    onOpenLab: (LearningTopic) -> Unit,
    viewModel: PairDetailViewModel = viewModel(factory = PairDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PairDetailEvent.Gone -> onClose()
            }
        }
    }

    SocialScreen(
        title = if (uiState.username.isEmpty()) stringResource(R.string.social_pairs_title) else stringResource(R.string.social_pair_title, uiState.username),
        onClose = onClose,
    ) {
        PairDetailContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onExercise = {
                when (val exercise = uiState.exercise) {
                    is TodayExercise.OpenLesson -> onOpenLesson(exercise.topic)
                    is TodayExercise.OpenLab -> onOpenLab(exercise.topic)
                    TodayExercise.AllDone, TodayExercise.Unknown -> Unit
                }
            },
            onEnd = viewModel::onEndClicked,
        )
    }
    if (uiState.confirmingEnd) {
        AlertDialog(
            onDismissRequest = viewModel::onEndDismissed,
            title = { Text(stringResource(R.string.social_pair_end_title, uiState.username)) },
            text = { Text(stringResource(R.string.social_pair_end_body)) },
            confirmButton = { TextButton(onClick = viewModel::onEndConfirmed) { Text(stringResource(R.string.social_pair_end_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::onEndDismissed) { Text(stringResource(R.string.social_cancel)) } },
        )
    }
}

@Composable
internal fun PairDetailContent(
    uiState: PairDetailUiState,
    onRetry: () -> Unit,
    onExercise: () -> Unit,
    onEnd: () -> Unit,
) {
    val metrics = uiState.metrics
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        uiState.loadError?.let { ErrorNotice(it, onRetry) }
        if (metrics == null) {
            if (uiState.loading) SocialLoading()
            return@Column
        }
        Text(text = stringResource(R.string.social_icon_pair), fontSize = 48.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = metrics.currentDays.toString(),
            color = NightWatchGold,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
        )
        SocialBodyText(stringResource(R.string.social_pair_days_label), alpha = 1f)
        SocialBodyText(
            stringResource(R.string.social_pairs_best, pluralStringResource(R.plurals.social_days, metrics.bestDays, metrics.bestDays)),
            modifier = Modifier.padding(top = 2.dp),
        )
        WeekStrip(uiState.week)
        SocialBodyText(stringResource(R.string.social_pair_week_label), modifier = Modifier.padding(top = 6.dp))
        Banner(metrics, uiState.username)
        SocialSectionTitle(stringResource(R.string.social_pair_today_title), modifier = Modifier.align(Alignment.Start))
        Column(verticalArrangement = SocialListSpacing) {
            TodayRow(stringResource(R.string.social_pair_me), metrics.meDoneToday)
            TodayRow(uiState.username, metrics.partnerDoneToday)
        }
        uiState.actionError?.let { InlineError(it, Modifier.padding(top = 10.dp)) }
        ExerciseButton(uiState.exercise, metrics, onExercise)
        Text(
            text = stringResource(R.string.social_pair_end),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .heightIn(min = 48.dp)
                .clickable(enabled = !uiState.ending, role = Role.Button, onClick = onEnd)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun WeekStrip(week: List<WeekDay>) {
    val initials = stringArrayResource(R.array.social_week_initials)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 20.dp)) {
        week.forEachIndexed { index, day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (day.shared) NightWatchGold else SocialElevated, CircleShape)
                        .let { if (day.today) it.border(BorderStroke(2.dp, NightWatchOnBackground), CircleShape) else it },
                ) {
                    if (day.shared) Text(text = "✓", color = SocialOnLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = initials.getOrElse(index) { "" },
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** The one-line explanation of what is missing today: who has not trained, or why the streak stops. */
@Composable
private fun Banner(metrics: PairMetrics, partner: String) {
    val text = when {
        metrics.state == PairState.RouteComplete -> stringResource(R.string.social_pair_banner_route_complete)
        metrics.meDoneToday && metrics.partnerDoneToday -> stringResource(R.string.social_pair_banner_done)
        !metrics.meDoneToday -> stringResource(
            if (metrics.currentDays > 0) R.string.social_pair_banner_me_missing else R.string.social_pair_banner_none,
        )
        else -> stringResource(R.string.social_pair_banner_partner_missing, partner)
    }
    Box(
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .background(SocialElevated, RoundedCornerShape(16.dp))
            .border(1.5.dp, SocialOnLight, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = NightWatchOnBackground, fontFamily = NightWatchBodyFontFamily, fontSize = 13.sp)
    }
}

/** Only "did their exercise today: yes or no" (rule R14): never which island. */
@Composable
private fun TodayRow(name: String, done: Boolean) {
    PersonRow(
        name = name,
        subtitle = null,
        trailing = {
            Text(
                text = stringResource(if (done) R.string.social_pair_done else R.string.social_pair_pending),
                fontSize = 18.sp,
            )
        },
    )
}

@Composable
private fun ExerciseButton(exercise: TodayExercise, metrics: PairMetrics, onClick: () -> Unit) {
    val (text, enabled) = when {
        exercise == TodayExercise.AllDone -> stringResource(R.string.social_pair_exercise_all_done) to false
        metrics.meDoneToday -> stringResource(R.string.social_pair_exercise_done) to false
        exercise == TodayExercise.Unknown -> stringResource(R.string.social_pair_exercise_lesson) to false
        else -> stringResource(R.string.social_pair_exercise_lesson) to true
    }
    NightWatchButton(text = text, onClick = onClick, enabled = enabled, modifier = Modifier.padding(top = 20.dp))
}
