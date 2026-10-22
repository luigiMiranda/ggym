@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.DateLabels
import com.ggym.app.ui.common.EmptyState
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymField
import com.ggym.app.ui.common.GgymPrimaryButton
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction
import com.ggym.app.ui.common.WeightField
import com.ggym.app.ui.common.WeightInput
import com.ggym.app.ui.common.WeightParse
import com.ggym.app.ui.common.activeLocale
import com.ggym.app.ui.common.durationSummary
import java.util.Locale

/**
 * One past workout, for correcting it.
 *
 * There is no way to add anything here — no new set, no new exercise, no different date. A workout
 * is a record of what happened, and the only two verbs are "correct this number" and "remove what
 * should not be there".
 */
@Composable
fun HistoryDetailScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onWorkoutDeleted: () -> Unit,
    viewModel: HistoryDetailViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = activeLocale()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { onWorkoutDeleted() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GgymScreenHeader(
                title = state.dayName.ifEmpty { stringResource(R.string.history_title_fallback) },
                subtitle = state.date?.let { date ->
                    stringResource(
                        R.string.history_detail_subtitle,
                        DateLabels.dayLong(date, locale),
                        durationSummary(state.durationSeconds * 1_000L),
                    )
                },
                onBack = onBack,
                trailing = {
                    if (!state.isMissing && !state.isLoading) {
                        RowMenu(
                            actions = listOf(
                                RowMenuAction(
                                    label = stringResource(R.string.history_delete_workout),
                                    icon = Icons.Filled.Delete,
                                    onClick = viewModel::askDeleteWorkout,
                                ),
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Unit

            state.isMissing -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.history_missing_title),
                message = stringResource(R.string.history_missing_message),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.exercises, key = { block -> block.id }) { block ->
                    ExerciseBlock(
                        block = block,
                        onEditSet = viewModel::editSet,
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        SetEditorSheet(
            state = editor,
            onSave = viewModel::saveSet,
            onRemove = { viewModel.askDeleteSet(editor.set) },
            onDismiss = viewModel::dismissEditor,
        )
    }

    when (val target = state.pendingDelete) {
        null -> Unit

        is HistoryDeleteTarget.Set -> ConfirmDeleteDialog(
            title = stringResource(R.string.history_remove_set_title),
            message = stringResource(R.string.history_remove_set_message),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )

        HistoryDeleteTarget.Workout -> ConfirmDeleteDialog(
            title = stringResource(
                R.string.history_delete_title,
                state.dayName,
                state.date?.let { DateLabels.dayMonth(it, locale) }.orEmpty(),
            ),
            message = stringResource(R.string.history_delete_message),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/** One exercise's card: its name, its set count, and every set as an editable row. */
@Composable
private fun ExerciseBlock(
    block: HistoryExerciseBlock,
    onEditSet: (HistorySetRow) -> Unit,
) {
    val locale = activeLocale()

    Surface(shape = GgymCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(R.plurals.workout_sets, block.sets.size, block.sets.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            block.sets.forEach { set ->
                SetRow(set = set, locale = locale, onClick = { onEditSet(set) })
            }
        }
    }
}

@Composable
private fun SetRow(
    set: HistorySetRow,
    locale: Locale,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.workout_set_label, set.setNumber),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                R.string.history_set_values,
                stringResource(R.string.weight_kg, KgFormat.formatNumber(set.weightKg, locale)),
                set.reps,
            ),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.history_edit_set, set.setNumber),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The set editor: weight and reps, nothing else.
 *
 * The weight field is the shared one (both separators, two decimals, −2.5/+2.5), so a correction
 * here reads exactly like entering a working weight anywhere else. "Remove set" is the second verb
 * and it asks first — this is history, where a mis-tap cannot be undone by retyping a number.
 */
@Composable
private fun SetEditorSheet(
    state: SetEditorState,
    onSave: (Double?, Int?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = activeLocale()
    var weight by rememberSaveable(state.set.id) {
        mutableStateOf(KgFormat.formatNumber(state.set.weightKg, locale))
    }
    var reps by rememberSaveable(state.set.id) { mutableStateOf(state.set.reps.toString()) }

    val parsedWeight = WeightInput.parse(weight)
    val weightValue = (parsedWeight as? WeightParse.Weight)?.weightKg
    val weightInvalid = parsedWeight is WeightParse.Invalid ||
        weightValue == null ||
        state.error == SetEditError.WeightInvalid
    val repsValue = reps.trim().toIntOrNull()?.takeIf { it >= 1 }
    val repsInvalid = repsValue == null || state.error == SetEditError.RepsInvalid

    val onStep: (Double) -> Unit = { delta ->
        WeightInput.stepped(weight, delta)?.let { weight = KgFormat.formatNumber(it, locale) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.history_editor_title, state.set.setNumber),
                style = MaterialTheme.typography.headlineSmall,
            )

            WeightField(
                value = weight,
                onValueChange = { weight = it },
                onStep = onStep,
                label = stringResource(R.string.history_field_weight),
                isError = weightInvalid,
                supportingText = stringResource(R.string.history_error_weight)
                    .takeIf { weightInvalid },
            )

            GgymField(
                value = reps,
                onValueChange = { reps = it },
                label = stringResource(R.string.history_field_reps),
                isError = repsInvalid,
                supportingText = stringResource(R.string.history_error_reps).takeIf { repsInvalid },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.history_remove_set),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                GgymPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { onSave(weightValue, repsValue) },
                    enabled = !weightInvalid && !repsInvalid,
                )
            }
        }
    }
}
