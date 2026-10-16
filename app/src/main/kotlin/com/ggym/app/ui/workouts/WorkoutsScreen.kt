@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.ggym.app.ui.common.NameInputDialog
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction

/**
 * Workouts: the routine list, and the way into the routine tree.
 *
 * The cards are the first real data in the app, so they carry the app's tonal style: a large,
 * generously rounded container, the name in a bold title, the shape of the routine underneath.
 */
@Composable
fun WorkoutsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onOpenRoutine: (Long) -> Unit,
    viewModel: WorkoutsViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.nav_workouts)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::startCreateRoutine,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.workouts_new_routine)) },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            // Nothing is shown until the first read lands, so the empty state never flashes.
            state.isLoading -> Unit

            state.routines.isEmpty() -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.workouts_empty_title),
                message = stringResource(R.string.workouts_empty_message),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.routines, key = { it.id }) { routine ->
                    RoutineRow(
                        routine = routine,
                        onOpen = { onOpenRoutine(routine.id) },
                        onRename = { viewModel.startRenameRoutine(routine) },
                        onDelete = { viewModel.askDeleteRoutine(routine) },
                    )
                }
            }
        }
    }

    state.nameDialog?.let { dialog ->
        NameInputDialog(
            title = stringResource(
                if (dialog.isCreating) R.string.routine_new_title else R.string.routine_rename_title,
            ),
            label = stringResource(R.string.routine_name_label),
            initialName = dialog.initialName,
            onSave = viewModel::saveName,
            onDismiss = viewModel::dismissNameDialog,
        )
    }

    state.pendingDelete?.let { routine ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.routine_delete_title),
            message = stringResource(R.string.routine_delete_message, routine.name),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun RoutineRow(
    routine: RoutineCard,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val days = pluralStringResource(R.plurals.routine_day_count, routine.dayCount, routine.dayCount)
    val exercises = pluralStringResource(R.plurals.routine_exercise_count, routine.exerciseCount, routine.exerciseCount)

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.routine_summary, days, exercises),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
