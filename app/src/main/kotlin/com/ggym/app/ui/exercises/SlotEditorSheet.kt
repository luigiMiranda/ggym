@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.WeightField

/**
 * The ViewModel key for one slot's editor.
 *
 * `viewModel()` caches by *owner and class*, so a sheet opened for a different slot on the same
 * screen would otherwise be handed the ViewModel of the slot opened first: it would show that
 * slot's exercise and values, and saving would write *that* slot. One key per slot is what makes
 * every opening a genuine opening.
 */
internal fun slotEditorViewModelKey(plannedExerciseId: Long): String = "slot-editor-$plannedExerciseId"

/**
 * The slot editor: how much of one exercise, on one training day.
 *
 * A sheet rather than a destination, because the day stays visible behind it — the user is
 * arranging a list, and the form should not make them lose their place. The weight field is the
 * last one, and it carries the steppers: it is the number this whole feature exists for.
 *
 * The sheet creates its own ViewModel from [plannedExerciseId], so the form and the slot it
 * belongs to are always created together (see [slotEditorViewModelKey]).
 */
@Composable
fun SlotEditorSheet(
    plannedExerciseId: Long,
    viewModelFactory: (Long) -> ViewModelProvider.Factory,
    onDismiss: () -> Unit,
) {
    // The sheet owns its ViewModel, so the key and the id it belongs to cannot drift apart at a
    // call site: creating one without the other is not expressible here.
    val viewModel: SlotEditorViewModel = viewModel(
        key = slotEditorViewModelKey(plannedExerciseId),
        factory = viewModelFactory(plannedExerciseId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { onDismiss() }
    }

    // The slot was removed from another screen while the sheet was open: closing is the honest
    // response, because there is nothing left to edit.
    LaunchedEffect(state.isMissing) {
        if (state.isMissing) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Hidden start plus Expanded-only values is the "no half-opened sheet" shape: this form is
        // five fields, and a partially expanded sheet would show three of them.
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
            Text(state.exerciseName, style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.sets,
                    onValueChange = viewModel::setSets,
                    label = { Text(stringResource(R.string.slot_field_sets)) },
                    singleLine = true,
                    isError = state.error == SlotEditorError.SetsTooLow,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.reps,
                    onValueChange = viewModel::setReps,
                    label = { Text(stringResource(R.string.slot_field_reps)) },
                    singleLine = true,
                    isError = state.error == SlotEditorError.RepsBlank,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.restSeconds,
                onValueChange = viewModel::setRestSeconds,
                label = { Text(stringResource(R.string.slot_field_rest)) },
                suffix = { Text(stringResource(R.string.unit_seconds)) },
                singleLine = true,
                isError = state.error == SlotEditorError.RestInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            WeightField(
                value = state.weight,
                onValueChange = viewModel::setWeight,
                onStep = viewModel::stepWeight,
                label = stringResource(R.string.slot_field_weight),
                isError = state.error == SlotEditorError.WeightInvalid,
                supportingText = errorMessage(state.error)
                    ?: stringResource(R.string.slot_weight_hint).takeIf { state.weight.isBlank() },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = viewModel::remove, enabled = !state.isLoading) {
                    Text(
                        text = stringResource(R.string.slot_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = viewModel::save, enabled = !state.isLoading) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun errorMessage(error: SlotEditorError?): String? = when (error) {
    null -> null
    SlotEditorError.SetsTooLow -> stringResource(R.string.slot_error_sets)
    SlotEditorError.RepsBlank -> stringResource(R.string.slot_error_reps)
    SlotEditorError.RestInvalid -> stringResource(R.string.slot_error_rest)
    SlotEditorError.WeightInvalid -> stringResource(R.string.slot_error_weight)
}
