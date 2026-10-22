@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.EmptyState
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymFab
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.ReorderableColumn
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction
import com.ggym.app.ui.common.activeLocale
import com.ggym.app.ui.exercises.ExerciseFormSheet
import com.ggym.app.ui.theme.GgymHeroColors

/**
 * The exercises planned for one training day.
 *
 * The start action is a dark plate above the list: it is the one tap this screen exists to offer,
 * and the plate keeps it louder than the plan it would start. Slots are cards with the weight as a
 * lime pill, which is the preview's way of making the number findable without making it a headline.
 */
@Composable
fun DayDetailScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    viewModel: DayDetailViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DayDetailEffect.OpenWorkout -> onOpenWorkout(effect.sessionId)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GgymScreenHeader(
                title = state.dayName.ifEmpty { stringResource(R.string.day_title_fallback) },
                subtitle = state.routineName.takeIf { it.isNotEmpty() },
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (!state.isMissing && !state.isLoading) {
                GgymFab(
                    text = stringResource(R.string.day_add_exercise),
                    onClick = onAddExercise,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Unit

            state.isMissing -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.day_missing_title),
                message = stringResource(R.string.day_missing_message),
                modifier = Modifier.padding(padding),
            )

            // The start plate sits above the slots in every case, so an empty day shows it
            // disabled rather than hiding the one thing this screen is for.
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                StartWorkoutPlate(
                    exerciseCount = state.slots.size,
                    setCount = state.plannedSetCount,
                    onStart = viewModel::startWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 4.dp),
                )

                if (state.slots.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.FitnessCenter,
                        title = stringResource(R.string.day_empty_title),
                        message = stringResource(R.string.day_empty_message),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ReorderableColumn(
                        items = state.slots,
                        keyOf = { slot -> slot.id },
                        onReorder = { slots -> viewModel.reorderSlots(slots.map { it.id }) },
                        handleDescription = stringResource(R.string.action_reorder),
                        moveUpLabel = stringResource(R.string.action_move_up),
                        moveDownLabel = stringResource(R.string.action_move_down),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        spacing = 10.dp,
                    ) { slot, handle, isDragging ->
                        SlotRow(
                            slot = slot,
                            modifier = handle,
                            isDragging = isDragging,
                            onEdit = { viewModel.openForm(slot) },
                            onDelete = { viewModel.askDeleteSlot(slot) },
                        )
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

    // One action for the name and the numbers: they are two rows, but one thought. A collision is
    // the database's answer, so the refusal comes back into the open form with the typed text.
    state.form?.let { form ->
        ExerciseFormSheet(
            form = form,
            onNameChange = viewModel::setFormName,
            onSetsChange = viewModel::setFormSets,
            onStepSets = viewModel::stepFormSets,
            onRepsChange = viewModel::setFormReps,
            onRestChange = viewModel::setFormRest,
            onStepRest = viewModel::stepFormRest,
            onWeightChange = viewModel::setFormWeight,
            onStepWeight = viewModel::stepFormWeight,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::dismissForm,
            onRemove = viewModel::removeFormSlot,
        )
    }
}

/**
 * "Start workout" on the dark plate: the day's shape and one tap. Disabled while nothing is
 * planned, because a workout with no exercises is not a workout.
 */
@Composable
private fun StartWorkoutPlate(
    exerciseCount: Int,
    setCount: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = exerciseCount > 0
    val exercises = pluralStringResource(R.plurals.day_start_exercises, exerciseCount, exerciseCount)
    val sets = pluralStringResource(R.plurals.day_start_sets, setCount, setCount)
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(GgymHeroColors.InProgress)
            .border(1.dp, GgymHeroColors.InProgressBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onStart)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.day_start_label),
                style = MaterialTheme.typography.titleLarge,
                color = GgymHeroColors.Content,
            )
            Text(
                text = stringResource(R.string.day_start_shape, exercises, sets),
                style = MaterialTheme.typography.bodySmall,
                color = GgymHeroColors.Muted,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primary else GgymHeroColors.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else GgymHeroColors.Muted,
            )
        }
    }
}

@Composable
private fun SlotRow(
    slot: SlotRow,
    modifier: Modifier,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = activeLocale()

    Surface(
        shape = GgymCardShape,
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandle(modifier = modifier)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(slot.exerciseName, style = MaterialTheme.typography.titleMedium)
                    Row {
                        Text(
                            text = stringResource(R.string.slot_target, slot.targetSets, slot.targetReps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.slot_rest, slot.restSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    slot.weightKg?.let { weight ->
                        GgymPill(
                            text = stringResource(R.string.weight_kg, KgFormat.formatNumber(weight, locale)),
                            tone = PillTone.Lime,
                        )
                        if (!slot.hasOwnWeight) {
                            Text(
                                text = stringResource(R.string.slot_weight_inherited),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } ?: GgymPill(text = stringResource(R.string.weight_unknown), tone = PillTone.Ghost)
                }
            }
            RowMenu(
                actions = listOf(
                    RowMenuAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit),
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
