package com.example.nightwatchprotocol.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.activeStreakText
import com.example.nightwatchprotocol.ui.components.UserAvatar
import com.example.nightwatchprotocol.ui.theme.NightWatchBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.ui.labs.LabsTab
import com.example.nightwatchprotocol.ui.path.LearningPathTab
import com.example.nightwatchprotocol.ui.social.SocialTab

/** Figma 49:15 "MAIN MENU": the colours of the bar line, the panel behind the pill and the sheet border/handle (113:145, 113:150). */
private val SheetBorderColor = Color(0xFF204143)

/** The opacity of what is not available yet, as the locked labs of Figma 2271:84 (opacity 60%). */
private const val DIMMED_ALPHA = 0.6f

/**
 * Home: the container of Inicio, Ruta and Labs with the bottom bar (module 3). It replaced the old Main Menu.
 * "Perfil" does not show content here: it asks for the user menu, which opens above Home so the tab is still selected
 * when it closes.
 */
@Composable
fun HomeScreen(
    onSignedOut: () -> Unit,
    onOpenUserMenu: () -> Unit,
    onOpenLesson: (LearningTopic) -> Unit,
    onOpenLab: (LearningTopic) -> Unit,
    onOpenExam: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenPairStreaks: () -> Unit,
    onOpenBattles: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoon = stringResource(R.string.learning_coming_soon)
    val noPendingLessons = stringResource(R.string.learning_home_no_pending_lessons)

    // Name and progress are re-read each time Home comes back (after Editar Perfil, or after a lesson in a later stage).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAccount()
        viewModel.refreshProgress()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.SignedOut -> onSignedOut()
                HomeEvent.OpenUserMenu -> onOpenUserMenu()
                HomeEvent.ShowComingSoon -> showNotice(snackbarHostState, comingSoon)
                HomeEvent.ShowNoPendingLessons -> showNotice(snackbarHostState, noPendingLessons)
                is HomeEvent.OpenLesson -> onOpenLesson(event.topic)
            }
        }
    }

    HomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenLesson = onOpenLesson,
        onOpenLab = onOpenLab,
        onOpenExam = onOpenExam,
        onDailyLessonClick = viewModel::onDailyLessonClicked,
        onTabSelected = viewModel::onTabSelected,
        onOpenPairStreaks = onOpenPairStreaks,
        onOpenFriends = onOpenFriends,
        onOpenBattles = onOpenBattles,
        onProfileClick = viewModel::onProfileClicked,
        onComingSoonCardClick = viewModel::onComingSoonCardClicked,
        onSignOut = viewModel::onSignOutClicked,
        onRetryProgress = viewModel::refreshProgress,
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onOpenLesson: (LearningTopic) -> Unit,
    onOpenLab: (LearningTopic) -> Unit,
    onOpenExam: () -> Unit,
    onDailyLessonClick: () -> Unit,
    onTabSelected: (HomeTab) -> Unit,
    onOpenPairStreaks: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenBattles: () -> Unit,
    onProfileClick: () -> Unit,
    onComingSoonCardClick: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProgress: () -> Unit,
) {
    NightWatchScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            // Each tab handles the status bar itself and the bar handles the navigation bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NightWatchBottomBar(
                    selected = uiState.selectedTab,
                    onTabSelected = onTabSelected,
                    onProfileClick = onProfileClick,
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (uiState.selectedTab) {
                    HomeTab.Inicio -> InicioTab(
                        uiState = uiState,
                        onSignOut = onSignOut,
                        onDailyLessonClick = onDailyLessonClick,
                        onComingSoonCardClick = onComingSoonCardClick,
                        onRetryProgress = onRetryProgress,
                    )
                    HomeTab.Ruta -> LearningPathTab(
                        snackbarHostState = snackbarHostState,
                        onOpenLesson = onOpenLesson,
                        onOpenExam = onOpenExam,
                    )
                    HomeTab.Labs -> LabsTab(
                        snackbarHostState = snackbarHostState,
                        onOpenLab = onOpenLab,
                        onOpenExam = onOpenExam,
                    )
                    HomeTab.Social -> SocialTab(
                        onOpenPairStreaks = onOpenPairStreaks,
                        onOpenFriends = onOpenFriends,
                        onOpenBattles = onOpenBattles,
                    )
                }
            }
        }
    }
}

// ---- Inicio (Figma 49:15) ----

@Composable
private fun InicioTab(
    uiState: HomeUiState,
    onSignOut: () -> Unit,
    onDailyLessonClick: () -> Unit,
    onComingSoonCardClick: () -> Unit,
    onRetryProgress: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(title = stringResource(R.string.learning_home_app_name)) {
            // The exit icon (49:15, 72:13).
            Image(
                painter = painterResource(id = R.drawable.img_home_exit),
                contentDescription = stringResource(R.string.learning_home_sign_out),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
                    .size(50.dp)
                    .clickable(role = Role.Button, onClick = onSignOut),
            )
        }
        // The light panel under the pill (113:148) with the dark rounded sheet over it (113:145).
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(NightWatchHeaderPanel),
        ) {
            UserPill(uiState)
            // The sheet is 12 dp wider than the screen in the design, so its side borders are off screen.
            Box(
                modifier = Modifier
                    .padding(top = 140.dp)
                    .offset(x = (-6).dp)
                    .requiredWidth(maxWidth + 12.dp)
                    .fillMaxSize()
                    .background(NightWatchBackground, SheetShape)
                    .border(3.dp, SheetBorderColor, SheetShape),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 25.dp)
                        .width(50.dp)
                        .height(3.dp)
                        .background(SheetBorderColor),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 61.dp, end = 61.dp, top = 72.dp, bottom = 24.dp),
                ) {
                    DailyLessonSection(uiState.dailyLesson, onDailyLessonClick, onRetryProgress)
                    ComingSoonSection(
                        title = stringResource(R.string.learning_home_new_concept),
                        imageRes = R.drawable.img_home_concept,
                        imageSize = 79.dp,
                        onClick = onComingSoonCardClick,
                    )
                    ComingSoonSection(
                        title = stringResource(R.string.learning_home_cibermynd),
                        imageRes = R.drawable.img_home_cibermynd,
                        imageSize = 61.dp,
                        onClick = onComingSoonCardClick,
                    )
                }
            }
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp)
private val CardShape = RoundedCornerShape(16.dp)


/** The badge with the photo or initial, the name, the level and the fixed streak (72:2 to 72:6, pill 108:16). */
@Composable
private fun UserPill(uiState: HomeUiState) {
    val shape = RoundedCornerShape(100.dp)
    // The pill bleeds off the left edge in the design: only its right end is visible.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 30.dp, end = 101.dp)
            .fillMaxWidth()
            .height(75.dp)
            .shadow(4.dp, shape)
            .background(PillColor, shape)
            .padding(start = 23.dp, end = 16.dp),
    ) {
        UserAvatar(display = uiState.avatar, size = 51.dp)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = uiState.displayName,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = activeStreakText(uiState.streak),
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.learning_home_level, uiState.level ?: stringResource(R.string.learning_loading_dots)),
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 15.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** The light 80 dp card with its illustration (72:15 to 72:17); the border is 3 dp (#597F82) and the radius 16. */
@Composable
private fun IllustratedCard(
    imageRes: Int,
    imageSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(4.dp, CardShape)
            .background(NightWatchSurfaceButton, CardShape)
            .border(3.dp, NightWatchBorderAccent, CardShape),
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier.size(imageSize),
        )
    }
}

/**
 * "Lección diaria": the next pending lesson, or "No tienes lecciones pendientes" (decision Q3). Tapping the card opens the
 * pending lesson, or says there is none; while loading or after an error the tap does nothing. Loading and errors only
 * touch the line under the card.
 */
@Composable
private fun DailyLessonSection(state: DailyLessonState, onClick: () -> Unit, onRetry: () -> Unit) {
    SectionTitle(stringResource(R.string.learning_home_daily_lesson))
    IllustratedCard(
        imageRes = R.drawable.img_home_lesson,
        imageSize = 70.dp,
        modifier = if (state is DailyLessonState.Pending || state == DailyLessonState.NoneLeft) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 39.dp).padding(top = 6.dp, bottom = 6.dp),
    ) {
        when (state) {
            DailyLessonState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NightWatchOnBackground,
                )
                CaptionText(stringResource(R.string.learning_home_loading_progress), modifier = Modifier.padding(start = 8.dp))
            }
            is DailyLessonState.Pending ->
                CaptionText(stringResource(R.string.learning_home_next_lesson, state.title))
            DailyLessonState.NoneLeft ->
                CaptionText(stringResource(R.string.learning_home_no_pending_lessons))
            is DailyLessonState.Failed -> {
                CaptionText(
                    text = stringResource(
                        if (state.offline) R.string.learning_home_progress_error_offline else R.string.learning_home_progress_error,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.learning_home_retry),
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 12.sp,
        textAlign = TextAlign.Start,
        modifier = modifier.alpha(0.85f),
    )
}

/**
 * "Concepto nuevo" and "Prepárate con CiberMynd": dimmed, and tapping only shows "Próximamente". They stay focusable so
 * TalkBack reads "…, próximamente" and "No disponible".
 */
@Composable
private fun ComingSoonSection(title: String, imageRes: Int, imageSize: Dp, onClick: () -> Unit) {
    val description = stringResource(R.string.learning_card_coming_soon_description, title)
    val unavailable = stringResource(R.string.learning_state_unavailable)
    SectionTitle(title)
    IllustratedCard(
        imageRes = imageRes,
        imageSize = imageSize,
        modifier = Modifier
            .alpha(DIMMED_ALPHA)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                stateDescription = unavailable
            },
    )
    Box(modifier = Modifier.height(39.dp))
}

