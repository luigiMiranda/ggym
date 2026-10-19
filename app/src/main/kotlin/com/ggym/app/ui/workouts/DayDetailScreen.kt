@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.EmptyState
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction
import com.ggym.app.ui.common.activeLocale
import com.ggym.app.ui.exercises.SlotEditorSheet
import com.ggym.app.ui.theme.GgymHeroWeight

/**
 * The exercises planned for one training day — the screen where the app's central promise becomes
 * visible: each slot shows the weight to load, and opens an editor that changes *that* slot.
 */
@Composable
fun DayDetailScreen(
    viewModelFactory: ViewModelProvider.Factory,
    slotEditorViewModelFactory: (Long) -> ViewModelProvider.Factory,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    viewModel: DayDetailViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editedSlotId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DayDetailEffect.OpenWorkout -> onOpenWorkout(effect.sessionId)
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(state.dayName.ifEmpty { stringResource(R.string.day_title_fallback) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isMissing && !state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onAddExercise,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.day_add_exercise)) },
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            state.isLoading -> Unit

            state.isMissing -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.day_missing_title),
                message = stringResource(R.string.day_missing_message),
                modifier = Modifier.padding(padding),
            )

            // The start container sits above the slots in every case, so an empty day shows it
            // disabled rather than hiding the one thing this screen is for.
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                StartWorkoutCard(
                    exerciseCount = state.slots.size,
                    setCount = state.plannedSetCount,
                    onStart = viewModel::startWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                )

                if (state.slots.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.FitnessCenter,
                        title = stringResource(R.string.day_empty_title),
                        message = stringResource(R.string.day_empty_message),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(state.slots, key = { _, slot -> slot.id }) { index, slot ->
                            SlotRow(
                                slot = slot,
                                canMoveUp = index > 0,
                                canMoveDown = index < state.slots.lastIndex,
                                onEdit = { editedSlotId = slot.id },
                                onMoveUp = { viewModel.moveSlot(slot.id, up = true) },
                                onMoveDown = { viewModel.moveSlot(slot.id, up = false) },
                                onDelete = { viewModel.askDeleteSlot(slot) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.startConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::dismissStartConflict,
            title = { Text(stringResource(R.string.day_conflict_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.day_conflict_message,
                        conflict.runningDayName,
                        conflict.runningRoutineName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::resumeRunningWorkout) {
                    Text(stringResource(R.string.day_conflict_resume))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::discardRunningAndStart) {
                        Text(
                            text = stringResource(R.string.day_conflict_discard),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = viewModel::dismissStartConflict) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }

    state.pendingDelete?.let { slot ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.slot_delete_title),
            message = stringResource(R.string.slot_delete_message, slot.exerciseName),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    editedSlotId?.let { slotId ->
        SlotEditorSheet(
            plannedExerciseId = slotId,
            viewModelFactory = slotEditorViewModelFactory,
            onDismiss = { editedSlotId = null },
        )
    }
}

/**
 * "Start workout": the day's shape above the list, so the decision to train is one tap away from
 * the plan. Disabled while nothing is planned, because a workout with no exercises is not a workout.
 */
@Composable
private fun StartWorkoutCard(
    exerciseCount: Int,
    setCount: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercises = pluralStringResource(R.plurals.day_start_exercises, exerciseCount, exerciseCount)
    val sets = pluralStringResource(R.plurals.day_start_sets, setCount, setCount)

    Card(
        onClick = onStart,
        enabled = exerciseCount > 0,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.day_start_label),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.day_start_shape, exercises, sets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SlotRow(
    slot: SlotRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = activeLocale()

    Card(
        onClick = onEdit,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(slot.exerciseName, style = MaterialTheme.typography.titleMedium)
                Row {
                    Text(
                        text = stringResource(R.string.slot_target, slot.targetSets, slot.targetReps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.slot_rest, slot.restSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                slot.weightKg?.let { weight ->
                    Text(
                        text = stringResource(R.string.weight_kg, KgFormat.formatNumber(weight, locale)),
                        style = GgymHeroWeight,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!slot.hasOwnWeight) {
                        Text(
                            text = stringResource(R.string.slot_weight_inherited),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } ?: Text(
                    text = stringResource(R.string.weight_unknown),
                    style = GgymHeroWeight,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            RowMenu(
                actions = listOf(
                    RowMenuAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit),
                    RowMenuAction(
                        label = stringResource(R.string.action_move_up),
                        icon = Icons.Filled.KeyboardArrowUp,
                        enabled = canMoveUp,
                        onClick = onMoveUp,
                    ),
                    RowMenuAction(
                        label = stringResource(R.string.action_move_down),
                        icon = Icons.Filled.KeyboardArrowDown,
                        enabled = canMoveDown,
                        onClick = onMoveDown,
                    ),
                    RowMenuAction(
                        label = stringResource(R.string.slot_remove),
                        icon = Icons.Filled.Delete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }
}
