@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymFab
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction
import com.ggym.app.ui.common.activeLocale

/**
 * The exercise library: every dictionary entry, what it loads, and where it is used.
 *
 * This is the dictionary's own screen — the one place an exercise can be renamed, re-weighted,
 * given a rest default or deleted without opening a workout day. It edits what is global; sets and
 * reps stay on the day, so the form's "Planned in" list is read-only and taps through to the day.
 *
 * A drill-down of the Workouts tab (no fifth tab, no Settings row): the bottom bar stays and
 * Workouts stays lit.
 */
@Composable
fun ExerciseLibraryScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onOpenRoutine: (Long) -> Unit,
    onOpenDay: (routineId: Long, dayId: Long) -> Unit,
    onOpenProgression: (exerciseId: Long) -> Unit,
    viewModel: ExerciseLibraryViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = activeLocale()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = { GgymScreenHeader(title = stringResource(R.string.library_title), onBack = onBack) },
        floatingActionButton = {
            GgymFab(text = stringResource(R.string.library_add), onClick = viewModel::addExercise)
        },
    ) { padding ->
        if (!state.isLoading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::updateQuery,
                        placeholder = { Text(stringResource(R.string.library_search_label)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.exercises.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.library_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                } else {
                    item(key = "list") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = GgymCardShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                                GgymSectionLabel(
                                    text = stringResource(R.string.library_section),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                )
                                state.exercises.forEachIndexed { index, exercise ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                        )
                                    }
                                    LibraryRow(
                                        exercise = exercise,
                                        detail = rowDetail(exercise),
                                        onOpen = { viewModel.openEditForm(exercise) },
                                        onEdit = { viewModel.openEditForm(exercise) },
                                        onProgression = { onOpenProgression(exercise.id) },
                                        onDelete = { viewModel.askDelete(exercise) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
            onRemove = { viewModel.askDeleteFromForm() },
            plannedIn = state.formPlans.map { plan ->
                PlannedInRow(
                    routineId = plan.routineId,
                    routineDayId = plan.routineDayId,
                    dayName = plan.dayName,
                    detail = stringResource(R.string.library_plan_detail, plan.targetSets, plan.targetReps),
                )
            },
            onOpenPlan = { plan ->
                viewModel.dismissForm()
                onOpenDay(plan.routineId, plan.routineDayId)
            },
        )
    }

    state.pendingDelete?.let { exercise ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.exercise_delete_title),
            message = stringResource(R.string.exercise_delete_message, exercise.name),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    if (state.blockedDelete.isNotEmpty()) {
        val routines = state.blockedDelete
        AlertDialog(
            onDismissRequest = viewModel::dismissBlockedDelete,
            title = { Text(stringResource(R.string.exercise_delete_blocked_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.exercise_delete_blocked_message,
                        routines.joinToString(separator = ", ") { it.name },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissBlockedDelete()
                        onOpenRoutine(routines.first().id)
                    },
                ) {
                    Text(stringResource(R.string.exercise_delete_blocked_open, routines.first().name))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBlockedDelete) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

/** "82.5 kg · rest 90 s · in Push A, Push B", or the two honest alternatives. */
@Composable
private fun rowDetail(exercise: LibraryExercise): String {
    val locale = activeLocale()
    val weight = exercise.weightKg
        ?.let { stringResource(R.string.weight_kg, KgFormat.formatNumber(it, locale)) }
        ?: stringResource(R.string.library_no_weight)
    val rest = stringResource(R.string.library_rest, exercise.restSeconds)
    val used = if (exercise.isPlanned) {
        stringResource(R.string.library_used_in, exercise.dayNames.joinToString(", "))
    } else {
        stringResource(R.string.library_unused)
    }
    return stringResource(R.string.library_row_detail, weight, rest, used)
}

@Composable
private fun LibraryRow(
    exercise: LibraryExercise,
    detail: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onProgression: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 14.dp, top = 9.dp, end = 4.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowMenu(
            actions = listOf(
                RowMenuAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit),
                RowMenuAction(stringResource(R.string.library_progression), Icons.Filled.Insights, onClick = onProgression),
                RowMenuAction(stringResource(R.string.action_delete), Icons.Filled.Delete, onClick = onDelete),
            ),
        )
    }
}
