@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    viewModel: DayDetailViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editedSlotId by rememberSaveable { mutableStateOf<Long?>(null) }

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

            state.slots.isEmpty() -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.day_empty_title),
                message = stringResource(R.string.day_empty_message),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
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
