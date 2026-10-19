@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.ggym.app.ui.common.ReorderableColumn
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction

/**
 * One routine and its training days, presented as the preview's list: a small uppercase section
 * label, then a card per day with the shape of what is inside it.
 *
 * Days are dragged by their handle; the order is written once, on drop. The row menu keeps rename
 * and delete only — "Move up" / "Move down" moved into the handle's accessibility actions, so a
 * screen reader can still reorder without a gesture.
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

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                GgymSectionLabel(
                    text = stringResource(R.string.routine_days_section),
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
                )
                ReorderableColumn(
                    items = state.days,
                    keyOf = { day -> day.id },
                    onReorder = { days -> viewModel.reorderDays(days.map { it.id }) },
                    handleDescription = stringResource(R.string.action_reorder),
                    moveUpLabel = stringResource(R.string.action_move_up),
                    moveDownLabel = stringResource(R.string.action_move_down),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
                    spacing = 10.dp,
                ) { day, handle, isDragging ->
                    DayRow(
                        day = day,
                        modifier = handle,
                        isDragging = isDragging,
                        onOpen = { onOpenDay(day.id) },
                        onRename = { viewModel.startRenameDay(day.name, day) },
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
    modifier: Modifier,
    isDragging: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
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
            // The card's body is the tap target; the handle and the menu are deliberately outside
            // it, so grabbing the row or opening its menu never also opens the day.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(start = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
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
            }
            RowMenu(
                actions = listOf(
                    RowMenuAction(stringResource(R.string.action_rename), Icons.Filled.Edit, onClick = onRename),
                    RowMenuAction(stringResource(R.string.action_delete), Icons.Filled.Delete, onClick = onDelete),
                ),
            )
        }
    }
}

/** The 40dp square the finger grabs. Its semantics (description + move actions) come from [handle]. */
@Composable
internal fun DragHandle(modifier: Modifier) {
    Box(
        modifier = Modifier.size(40.dp).then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
    Spacer(Modifier.width(4.dp))
}
