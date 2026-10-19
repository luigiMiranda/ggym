@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymFab
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.NameInputDialog
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction

/**
 * One routine and its training days, presented as the preview's list: a small uppercase section
 * label, then a card per day with the shape of what is inside it.
 *
 * Days are a list inside a routine, so reordering is done from each row's menu ("Move up" /
 * "Move down") and the ends of the list simply disable the entry that cannot go further.
 */
@Composable
fun RoutineDetailScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    viewModel: RoutineDetailViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GgymScreenHeader(
                title = state.routineName.ifEmpty { stringResource(R.string.routine_title_fallback) },
                onBack = onBack,
                trailing = {
                    if (!state.isMissing && !state.isLoading) {
                        IconButton(onClick = { viewModel.startRenameRoutine(state.routineName) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.routine_rename_title),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isMissing && !state.isLoading) {
                GgymFab(
                    text = stringResource(R.string.routine_add_day),
                    onClick = viewModel::startAddDay,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Unit

            state.isMissing -> EmptyState(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.routine_missing_title),
                message = stringResource(R.string.routine_missing_message),
                modifier = Modifier.padding(padding),
            )

            state.days.isEmpty() -> EmptyState(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.days_empty_title),
                message = stringResource(R.string.days_empty_message),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "label") {
                    GgymSectionLabel(
                        text = stringResource(R.string.routine_days_section),
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(state.days, key = { _, day -> day.id }) { index, day ->
                    DayRow(
                        day = day,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.days.lastIndex,
                        onOpen = { onOpenDay(day.id) },
                        onRename = { viewModel.startRenameDay(day.name, day) },
                        onMoveUp = { viewModel.moveDay(day.id, up = true) },
                        onMoveDown = { viewModel.moveDay(day.id, up = false) },
                        onDelete = { viewModel.askDeleteDay(day) },
                    )
                }
            }
        }
    }

    state.nameDialog?.let { dialog ->
        NameInputDialog(
            title = stringResource(R.string.routine_rename_title),
            label = stringResource(R.string.routine_name_label),
            initialName = dialog.initialName,
            onSave = viewModel::saveRoutineName,
            onDismiss = viewModel::dismissRoutineNameDialog,
        )
    }

    state.dayDialog?.let { dialog ->
        NameInputDialog(
            title = stringResource(if (dialog.isCreating) R.string.day_new_title else R.string.day_rename_title),
            label = stringResource(R.string.day_name_label),
            initialName = dialog.initialName,
            onSave = viewModel::saveDayName,
            onDismiss = viewModel::dismissDayDialog,
        )
    }

    state.pendingDelete?.let { day ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.day_delete_title),
            message = stringResource(R.string.day_delete_message, day.name),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun DayRow(
    day: DayRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = GgymCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(day.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pluralStringResource(
                        R.plurals.routine_exercise_count,
                        day.exerciseCount,
                        day.exerciseCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowMenu(
                actions = listOf(
                    RowMenuAction(stringResource(R.string.action_rename), Icons.Filled.Edit, onClick = onRename),
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
                    RowMenuAction(stringResource(R.string.action_delete), Icons.Filled.Delete, onClick = onDelete),
                ),
            )
        }
    }
}
