@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workout

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.DurationFormat
import com.ggym.app.ui.common.EmptyState
import com.ggym.app.ui.common.GgymHeroCard
import com.ggym.app.ui.common.GgymHeroLabel
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymPrimaryButton
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.GgymStepper
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.activeLocale
import com.ggym.app.ui.common.durationSummary
import com.ggym.app.ui.common.notificationPermissionGranted
import com.ggym.app.ui.theme.GgymHeroColors
import com.ggym.app.ui.theme.GgymHeroWeight
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The workout that is running: every exercise as a card, or one exercise at a time with the weight
 * as the largest thing on the screen.
 *
 * One destination with two modes (an internal list/pager switch, not two routes), because swiping
 * between exercises is not navigation and the two modes share everything that matters: which set
 * the hero edits, whether a rest is running, what has been performed.
 *
 * The screen owns no workout state of its own. Everything it shows comes from the draft in the
 * database and the stored rest deadline, so a force-stop, a swipe away from recents or a tap on the
 * notification's `+15 s` all land here as the next state rather than as a lost workout.
 */
@Composable
fun ActiveWorkoutScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onWorkoutEnded: () -> Unit,
    viewModel: ActiveWorkoutViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = activeLocale()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { onWorkoutEnded() }
    }

    // The notification permission is asked once, the first time a workout is on screen. A denial is
    // an ordinary outcome: the countdown, the banner and the tone all work without the drawer, and
    // Settings says what is missing. So the answer is not read here.
    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val promptDue by viewModel.isNotificationPromptDue.collectAsStateWithLifecycle(initialValue = false)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(promptDue) {
        if (promptDue) {
            viewModel.notificationPromptShown()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.notificationPermissionGranted()) {
                notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Back never drops a workout on the floor: focused → overview → the finish sheet. The sheet and
    // the dialog handle their own back while they are up.
    BackHandler(enabled = !state.isFinishSheetVisible && !state.isDiscardDialogVisible && !state.isMissing) {
        if (state.focused != null) viewModel.showOverview() else viewModel.askFinish()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                // The banner is the rest starting or ending — a change of place, so it enters and
                // leaves with spatial motion. The last countdown is kept for the exit: while the
                // banner slides away the state already holds no rest, and a blank strip would make
                // the motion meaningless.
                var lastRest by remember { mutableStateOf<RestBanner?>(null) }
                state.rest?.let { lastRest = it }
                AnimatedVisibility(
                    visible = state.rest != null,
                    enter = slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        initialOffsetY = { it },
                    ) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { it },
                    ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    lastRest?.let { banner ->
                        RestBanner(
                            banner = banner,
                            locale = locale,
                            onAddTime = viewModel::addRestTime,
                            onSubtractTime = viewModel::subtractRestTime,
                            onSkip = viewModel::skipRest,
                        )
                    }
                }

                if (!state.isLoading && !state.isMissing && state.focused != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        val focused = state.focused
                        WorkoutAction(
                            action = focused?.action ?: FocusedAction.CompleteSet,
                            enabled = focused == null ||
                                focused.action != FocusedAction.CompleteSet ||
                                focused.hero.canComplete,
                            onClick = { focused?.let { viewModel.runAction(it.action) } },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            // Nothing is shown until the first read lands, so the empty state never flashes.
            state.isLoading -> Unit

            state.isMissing -> WorkoutGone(onBack = onWorkoutEnded, modifier = Modifier.padding(padding))

            state.focused != null -> WorkoutFocused(
                state = state,
                locale = locale,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )

            else -> WorkoutOverview(
                state = state,
                locale = locale,
                onOpenExercise = viewModel::focusExercise,
                onFinish = viewModel::askFinish,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.isFinishSheetVisible) {
        FinishWorkoutSheet(
            state = state,
            onFinish = viewModel::confirmFinish,
            onDiscard = viewModel::askDiscard,
            onKeepTraining = viewModel::dismissFinishSheet,
        )
    }

    if (state.isDiscardDialogVisible) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.workout_discard_title),
            message = stringResource(R.string.workout_discard_message),
            confirmLabel = stringResource(R.string.workout_discard),
            onConfirm = viewModel::confirmDiscard,
            onDismiss = viewModel::cancelDiscard,
        )
    }
}

/** The workout as a list: what is planned, what has been done, and the weight to load next. */
@Composable
private fun WorkoutOverview(
    state: ActiveWorkoutUiState,
    locale: Locale,
    onOpenExercise: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        GgymScreenHeader(
            title = state.dayName.ifEmpty { stringResource(R.string.workout_title_fallback) },
            subtitle = state.routineName.takeIf { it.isNotEmpty() },
            trailing = {
                GgymPill(
                    text = stringResource(R.string.workout_finish_short),
                    tone = PillTone.Ghost,
                    onClick = onFinish,
                )
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.workout_session_label,
                    DurationFormat.clock(state.elapsedMillis, locale),
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 1.1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.workout_overview_sets,
                    state.completedSets,
                    pluralStringResource(R.plurals.workout_sets, state.totalSets, state.totalSets),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 1.1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = {
                if (state.totalSets == 0) 0f else state.completedSets.toFloat() / state.totalSets
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.exercises, key = { _, card -> card.exerciseLogId }) { index, card ->
                ExerciseCardRow(
                    card = card,
                    locale = locale,
                    onClick = { onOpenExercise(index) },
                )
            }
        }
    }
}

@Composable
private fun ExerciseCardRow(
    card: WorkoutExerciseCard,
    locale: Locale,
    onClick: () -> Unit,
) {
    // A started exercise earns the brighter container: a glance down the list has to say what is
    // left to do. The weight pill is lime for the same reason.
    Surface(
        onClick = onClick,
        shape = com.ggym.app.ui.common.GgymCardShape,
        color = if (card.isStarted) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(card.exerciseName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            R.string.workout_overview_sets,
                            card.completedSets,
                            pluralStringResource(R.plurals.workout_sets, card.totalSets, card.totalSets),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GgymPill(
                    text = card.workingWeightKg?.let { weight ->
                        stringResource(R.string.weight_kg, KgFormat.formatNumber(weight, locale))
                    } ?: stringResource(R.string.weight_unknown),
                    tone = if (card.isStarted) PillTone.Lime else PillTone.Ghost,
                )
            }
            LinearProgressIndicator(
                progress = { card.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
        }
    }
}

/** One page per exercise; the page under the user's finger is the one the hero edits. */
@Composable
private fun WorkoutFocused(
    state: ActiveWorkoutUiState,
    locale: Locale,
    viewModel: ActiveWorkoutViewModel,
    modifier: Modifier = Modifier,
) {
    val focused = state.focused ?: return
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = focused.index,
        pageCount = { state.exercises.size },
    )

    // A swipe is a selection: the pager's page and the ViewModel's focus are kept the same thing,
    // so the rows, the hero and the action always belong to the exercise on screen.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> viewModel.focusExercise(page) }
    }

    // The bottom action can say "Next exercise": the ViewModel moves first, and the pager follows
    // it (rather than the other way round, which is what a swipe does).
    LaunchedEffect(focused.index) {
        if (pagerState.currentPage != focused.index) pagerState.animateScrollToPage(focused.index)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // The previous and next exercise are the page's own navigation, as in the preview: at the
        // first one, "Overview" is what sits to the left, so the way back is never a hidden gesture.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val previous = state.exercises.getOrNull(focused.index - 1)
            val next = state.exercises.getOrNull(focused.index + 1)

            Text(
                text = "‹ " + (previous?.exerciseName ?: stringResource(R.string.workout_mode_overview)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) {
                        if (previous == null) viewModel.showOverview()
                        else scope.launch { pagerState.animateScrollToPage(focused.index - 1) }
                    },
            )
            if (next != null) {
                Text(
                    text = next.exerciseName + " ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) {
                            scope.launch { pagerState.animateScrollToPage(focused.index + 1) }
                        },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { page -> state.exercises[page].exerciseLogId },
        ) { page ->
            val card = state.exercises[page]
            // A page that the focused state has not caught up with yet (mid-swipe) shows its own
            // first unperformed set rather than another exercise's number, and cannot be acted on.
            val pageView = if (focused.index == page) focused else card.asPage(page, locale)

            ExercisePage(
                focused = pageView,
                exerciseCount = state.exercises.size,
                locale = locale,
                onWeightChange = viewModel::editWeight,
                onStepWeight = viewModel::stepWeight,
                onStepReps = viewModel::stepReps,
                onSelectSet = viewModel::selectSet,
                onRemoveSet = viewModel::removeSet,
                onUncompleteSet = viewModel::uncompleteSet,
                onAddSet = viewModel::addSet,
                onStartRest = viewModel::startRest,
            )
        }

        PageDots(count = state.exercises.size, current = focused.index)
    }

}

@Composable
private fun ExercisePage(
    focused: FocusedExercise,
    exerciseCount: Int,
    locale: Locale,
    onWeightChange: (String) -> Unit,
    onStepWeight: (Double) -> Unit,
    onStepReps: (Int) -> Unit,
    onSelectSet: (Long) -> Unit,
    onRemoveSet: (Long) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    onStartRest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val selectedPosition = focused.sets.firstOrNull { it.id == focused.hero.selectedSetId }?.position
        val sets = pluralStringResource(R.plurals.workout_sets, focused.sets.size, focused.sets.size)

        GgymHeroCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GgymHeroLabel(stringResource(R.string.workout_exercise_of, focused.index + 1, exerciseCount))
                Spacer(Modifier.weight(1f))
                if (selectedPosition != null) {
                    GgymPill(
                        text = stringResource(R.string.workout_set_of, selectedPosition, focused.sets.size),
                        tone = PillTone.Lime,
                    )
                }
            }
            Text(
                text = focused.exerciseName,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = GgymHeroColors.Content,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = heroTargetLine(focused, sets),
                style = MaterialTheme.typography.bodySmall,
                color = GgymHeroColors.Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            WeightHero(
                hero = focused.hero,
                onWeightChange = onWeightChange,
                onStep = onStepWeight,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GgymStepper(
                label = stringResource(R.string.slot_field_reps),
                labelAccent = if (focused.targetReps.isBlank()) {
                    ""
                } else {
                    stringResource(R.string.workout_reps_target, focused.targetReps)
                },
                value = focused.hero.reps.toString(),
                onDecrease = { onStepReps(-1) },
                onIncrease = { onStepReps(1) },
                decreaseLabel = stringResource(R.string.workout_reps_decrease),
                increaseLabel = stringResource(R.string.workout_reps_increase),
                modifier = Modifier.weight(1f),
            )
            GgymStepper(
                label = stringResource(R.string.slot_field_rest),
                value = stringResource(R.string.rest_seconds_short, focused.restSeconds),
                modifier = Modifier.weight(1f),
            )
        }

        SetRowList(
            sets = focused.sets,
            selectedSetId = focused.hero.selectedSetId,
            locale = locale,
            onSelect = onSelectSet,
            onRemove = onRemoveSet,
            onUncomplete = onUncompleteSet,
            onAddSet = onAddSet,
            onStartRest = onStartRest,
        )
    }
}

/** Page dots: the shape of the workout, and where in it the user is. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 20.dp else 8.dp,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "page-dot",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}

/** The workout ended elsewhere: say so, and offer the way out rather than an empty screen. */
@Composable
private fun WorkoutGone(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Outlined.FitnessCenter,
            title = stringResource(R.string.workout_missing_title),
            message = stringResource(R.string.workout_missing_message),
            modifier = Modifier.weight(1f),
        )
        GgymPrimaryButton(
            text = stringResource(R.string.workout_back_to_workouts),
            onClick = onBack,
            modifier = Modifier.padding(bottom = 32.dp),
        )
    }
}

/**
 * A page's own view of itself, for the moment before the focused state follows the pager. It shows
 * the exercise's first unperformed set and cannot be acted on.
 */
private fun WorkoutExerciseCard.asPage(index: Int, locale: Locale): FocusedExercise {
    val set = sets.firstOrNull { !it.isCompleted } ?: sets.lastOrNull()
    val weightText = when {
        set == null -> ""
        set.weightKg == 0.0 && !set.isCompleted -> ""
        else -> KgFormat.formatNumber(set.weightKg, locale)
    }

    return FocusedExercise(
        index = index,
        exerciseLogId = exerciseLogId,
        exerciseName = exerciseName,
        restSeconds = restSeconds,
        targetReps = targetReps,
        sets = sets,
        hero = WeightHeroState(
            selectedSetId = set?.id,
            weightText = weightText,
            reps = set?.reps ?: 0,
            canComplete = false,
            isSelectedSetCompleted = false,
        ),
        action = if (sets.isEmpty()) FocusedAction.AddSet else FocusedAction.CompleteSet,
    )
}

/**
 * The hero's subtitle: the plan's target when the workout carries one — `Target 4 × 8-10 · rest
 * 90s`, the M0 preview's own line — and the shape it always showed when it does not. A blank
 * target is old history, or a slot planned without one; printing an empty target after `×` would
 * be worse than the copy the screen used before.
 */
@Composable
private fun heroTargetLine(focused: FocusedExercise, sets: String): String {
    val rest = stringResource(R.string.slot_rest, focused.restSeconds)
    return if (focused.targetReps.isBlank()) {
        stringResource(R.string.day_start_shape, sets, rest)
    } else {
        stringResource(
            R.string.workout_target_line,
            stringResource(R.string.slot_target, focused.sets.size, focused.targetReps),
            rest,
        )
    }
}

/** What the bottom action does, shared by the bar above it and the screen's own callbacks. */
private fun ActiveWorkoutViewModel.runAction(action: FocusedAction) {
    when (action) {
        FocusedAction.CompleteSet -> completeSelectedSet()
        FocusedAction.AddSet -> addSet()
        FocusedAction.FinishWorkout -> askFinish()
        FocusedAction.NextExercise -> focusNextExercise()
    }
}
