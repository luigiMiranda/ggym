@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.DurationFormat
import com.ggym.app.ui.common.EmptyState
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction
import com.ggym.app.ui.common.activeLocale
import com.ggym.app.ui.common.durationSummary
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

    // Back never drops a workout on the floor: focused → overview → the finish sheet. The sheet and
    // the dialog handle their own back while they are up.
    BackHandler(enabled = !state.isFinishSheetVisible && !state.isDiscardDialogVisible && !state.isMissing) {
        if (state.focused != null) viewModel.showOverview() else viewModel.askFinish()
    }

    Scaffold(
        topBar = {
            WorkoutTopBar(
                state = state,
                locale = locale,
                onFinish = viewModel::askFinish,
                onDiscard = viewModel::askDiscard,
            )
        },
        bottomBar = {
            Column {
                state.rest?.let { banner ->
                    RestBanner(
                        banner = banner,
                        locale = locale,
                        onAddTime = viewModel::addRestTime,
                        onSubtractTime = viewModel::subtractRestTime,
                        onSkip = viewModel::skipRest,
                    )
                }
                if (!state.isLoading && !state.isMissing && state.focused == null) {
                    WorkoutFooter(state = state, onFinish = viewModel::askFinish)
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.isLoading && !state.isMissing) {
                ModePicker(
                    isFocused = state.focused != null,
                    onOverview = viewModel::showOverview,
                    onFocused = {
                        viewModel.focusExercise(state.focused?.index ?: state.firstOpenExercise())
                    },
                )
            }

            when {
                // Nothing is shown until the first read lands, so the empty state never flashes.
                state.isLoading -> Unit

                state.isMissing -> WorkoutGone(onBack = onWorkoutEnded)

                state.focused != null -> WorkoutFocused(
                    state = state,
                    locale = locale,
                    viewModel = viewModel,
                )

                else -> WorkoutOverview(
                    state = state,
                    locale = locale,
                    onOpenExercise = viewModel::focusExercise,
                )
            }
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

/** The day, how long it has been running, and the two ways out. */
@Composable
private fun WorkoutTopBar(
    state: ActiveWorkoutUiState,
    locale: Locale,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = state.dayName.ifEmpty { stringResource(R.string.workout_title_fallback) },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                if (state.routineName.isNotEmpty()) {
                    Text(
                        text = state.routineName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            Text(
                text = DurationFormat.clock(state.elapsedMillis, locale),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            RowMenu(
                actions = listOf(
                    RowMenuAction(stringResource(R.string.workout_finish), Icons.Filled.Check, onClick = onFinish),
                    RowMenuAction(stringResource(R.string.workout_discard), Icons.Filled.Delete, onClick = onDiscard),
                ),
            )
        },
    )
}

/** The two modes of the one destination: the whole workout, or one exercise at a time. */
@Composable
private fun ModePicker(
    isFocused: Boolean,
    onOverview: () -> Unit,
    onFocused: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = !isFocused,
            onClick = onOverview,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.workout_mode_overview)) },
        )
        SegmentedButton(
            selected = isFocused,
            onClick = onFocused,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.workout_mode_focused)) },
        )
    }
}

/** The workout as a list: what is planned, what has been done, and the weight to load next. */
@Composable
private fun WorkoutOverview(
    state: ActiveWorkoutUiState,
    locale: Locale,
    onOpenExercise: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun ExerciseCardRow(
    card: WorkoutExerciseCard,
    locale: Locale,
    onClick: () -> Unit,
) {
    // "Started" earns a tonal step up: a glance down the list has to say what is left to do.
    val container = if (card.isStarted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.exerciseName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(
                            R.string.workout_overview_sets,
                            card.completedSets,
                            pluralStringResource(R.plurals.workout_sets, card.totalSets, card.totalSets),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = card.workingWeightKg?.let { weight ->
                        stringResource(R.string.weight_kg, KgFormat.formatNumber(weight, locale))
                    } ?: stringResource(R.string.weight_unknown),
                    style = GgymHeroWeight,
                    color = if (card.isStarted) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (card.totalSets > 0) {
                LinearProgressIndicator(
                    progress = { card.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

/** What the workout has done so far, and the way to end it. */
@Composable
private fun WorkoutFooter(
    state: ActiveWorkoutUiState,
    onFinish: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.workout_progress,
                        state.completedSets,
                        pluralStringResource(R.plurals.workout_sets, state.totalSets, state.totalSets),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = durationSummary(state.elapsedMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onFinish) {
                Text(stringResource(R.string.workout_finish))
            }
        }
    }
}

/** One page per exercise; the page under the user's finger is the one the hero edits. */
@Composable
private fun WorkoutFocused(
    state: ActiveWorkoutUiState,
    locale: Locale,
    viewModel: ActiveWorkoutViewModel,
) {
    val focused = state.focused ?: return
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = focused.index,
        pageCount = { state.exercises.size },
    )

    // A swipe is a selection: the pager's page and the ViewModel's focus are kept the same thing,
    // so the chips, the hero and the action always belong to the exercise on screen.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> viewModel.focusExercise(page) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.workout_exercise_of, focused.index + 1, state.exercises.size),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            PageDots(count = state.exercises.size, current = focused.index)
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
                locale = locale,
                onWeightChange = viewModel::editWeight,
                onStepWeight = viewModel::stepWeight,
                onStepReps = viewModel::stepReps,
                onSelectSet = viewModel::selectSet,
                onRemoveSet = viewModel::removeSet,
                onAddSet = viewModel::addSet,
                onAction = { action ->
                    when (action) {
                        FocusedAction.CompleteSet -> viewModel.completeSelectedSet()
                        FocusedAction.AddSet -> viewModel.addSet()
                        FocusedAction.FinishWorkout -> viewModel.askFinish()
                        FocusedAction.NextExercise -> scope.launch { pagerState.animateScrollToPage(page + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun ExercisePage(
    focused: FocusedExercise,
    locale: Locale,
    onWeightChange: (String) -> Unit,
    onStepWeight: (Double) -> Unit,
    onStepReps: (Int) -> Unit,
    onSelectSet: (Long) -> Unit,
    onRemoveSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    onAction: (FocusedAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WeightHero(
                hero = focused.hero,
                onWeightChange = onWeightChange,
                onStep = onStepWeight,
            )
            RepsStepper(reps = focused.hero.reps, onStep = onStepReps)
            Text(
                text = stringResource(R.string.slot_rest, focused.restSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SetChipRow(
                sets = focused.sets,
                selectedSetId = focused.hero.selectedSetId,
                locale = locale,
                onSelect = onSelectSet,
                onRemove = onRemoveSet,
                onAddSet = onAddSet,
            )
        }
        WorkoutAction(
            action = focused.action,
            enabled = focused.action != FocusedAction.CompleteSet || focused.hero.canComplete,
            onClick = { onAction(focused.action) },
        )
    }
}

/** Page dots: the shape of the workout, and where in it the user is. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(targetValue = if (active) 20.dp else 8.dp, label = "page-dot")
            Box(
                modifier = Modifier
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
private fun WorkoutGone(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Outlined.FitnessCenter,
            title = stringResource(R.string.workout_missing_title),
            message = stringResource(R.string.workout_missing_message),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onBack,
            modifier = Modifier.padding(bottom = 32.dp),
        ) {
            Text(stringResource(R.string.workout_back_to_workouts))
        }
    }
}

/**
 * The exercise the "Exercise" mode opens on: the first one that still has work in it, else the
 * first. A workout whose every set is done opens on the last exercise rather than nowhere.
 */
private fun ActiveWorkoutUiState.firstOpenExercise(): Int =
    exercises.indexOfFirst { !it.isFinished }.takeIf { it >= 0 } ?: exercises.lastIndex.coerceAtLeast(0)

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
