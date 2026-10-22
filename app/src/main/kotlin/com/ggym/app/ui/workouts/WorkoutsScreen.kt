@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.EmptyState
import com.ggym.app.ui.common.GgymFab
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.NameInputDialog
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction
import com.ggym.app.ui.common.durationSummary
import com.ggym.app.ui.theme.GgymHeroColors

/**
 * Workouts: the routine list, the running workout, and the way into the routine tree.
 *
 * The card layout follows `preview/index.html`: an in-progress plate first, then routine cards
 * carrying the shape of what is inside them, with the one lime floating action as the only way to
 * make a new routine.
 */
@Composable
fun WorkoutsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onOpenRoutine: (Long) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: WorkoutsViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = { GgymScreenHeader(title = stringResource(R.string.nav_workouts)) },
        floatingActionButton = {
            GgymFab(
                text = stringResource(R.string.workouts_new_routine),
                onClick = viewModel::startCreateRoutine,
            )
        },
    ) { padding ->
        when {
            // Nothing is shown until the first read lands, so the empty state never flashes.
            state.isLoading -> Unit

            // A workout can outlive its routine (it keeps its own names), so the resume card is
            // reason enough to show the list even when no routine is left. The library card too: a
            // user with exercises but no routines still needs the way into them.
            state.routines.isEmpty() && state.resume == null && state.library.isEmpty -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.workouts_empty_title),
                message = stringResource(R.string.workouts_empty_message),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.resume?.let { resume ->
                    item(key = "resume") {
                        ResumeCard(card = resume, onOpen = { onOpenWorkout(resume.sessionId) })
                    }
                }
                item(key = "library") {
                    LibraryRow(card = state.library, onOpen = onOpenLibrary)
                }
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

/**
 * The running workout, on the dark plate: the one thing the Workouts tab can do that no other
 * screen can is get the user back into the workout they are in the middle of.
 */
@Composable
private fun ResumeCard(
    card: ResumeCard,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GgymHeroColors.InProgress)
            .border(1.dp, GgymHeroColors.InProgressBorder, shape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(
                        R.string.workouts_in_progress,
                        durationSummary(card.elapsedMillis),
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 1.2.sp),
                    color = GgymHeroColors.Muted,
                )
                Text(
                    text = listOfNotNull(card.dayName, card.exerciseName).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = GgymHeroColors.Content,
                )
            }
            GgymPill(text = stringResource(R.string.workouts_resume_action), tone = PillTone.Done)
        }
        LinearProgressIndicator(
            progress = { card.progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = GgymHeroColors.Button,
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
    val sets = pluralStringResource(R.plurals.routine_set_count, routine.setCount, routine.setCount)

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.routine_summary_full, days, exercises, sets),
                    style = MaterialTheme.typography.bodySmall,
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

/**
 * The way into the exercise library, above the routine list.
 *
 * A card rather than a menu entry, because the library is a place the user goes rather than an
 * action on a routine. Its second line is the count and — only when there is one — how many
 * exercises still have no weight, which is a hint rather than a task list.
 */
@Composable
private fun LibraryRow(card: LibraryCard, onOpen: () -> Unit) {
    val count = pluralStringResource(R.plurals.routine_exercise_count, card.exerciseCount, card.exerciseCount)
    val detail = if (card.withoutWeightCount > 0) {
        val attention = pluralStringResource(
            R.plurals.library_without_weight,
            card.withoutWeightCount,
            card.withoutWeightCount,
        )
        stringResource(R.string.library_card_summary, count, attention)
    } else {
        count
    }

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.library_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
