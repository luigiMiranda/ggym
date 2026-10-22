@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ggym.app.R
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.ui.common.GgymField
import com.ggym.app.ui.common.GgymPrimaryButton
import com.ggym.app.ui.common.WeightField

/**
 * The exercise form: a name and the numbers that go with it, in one place.
 *
 * Stateless on purpose, like `BodyWeightSheet`: this is the form, and the screen that owns the
 * [form] knows what Save means — the picker creates a dictionary entry and plans it, a training day
 * rewrites a slot. Two screens, one form, one copy of the validation ([validate]).
 *
 * A sheet rather than a dialog because the day stays visible behind it (the user is arranging a
 * list and must not lose their place), and because a text field inside a `Dialog` window never
 * reports idle under Robolectric — this form's name-collision path is testable where the old rename
 * dialog's was not.
 *
 * The controls say what the numbers mean, and all five are the same [GgymField]: a tonal container
 * with the label above the value. Sets, rest and weight also carry the ± buttons that step them,
 * while the name and the target reps are typed only. Every number can be typed as well as stepped,
 * so an exact rest of 75 s is one keystroke rather than five taps.
 */
@Composable
fun ExerciseFormSheet(
    form: ExerciseFormState,
    onNameChange: (String) -> Unit,
    onSetsChange: (String) -> Unit,
    onStepSets: (Int) -> Unit,
    onRepsChange: (String) -> Unit,
    onRestChange: (String) -> Unit,
    onStepRest: (Int) -> Unit,
    onWeightChange: (String) -> Unit,
    onStepWeight: (Double) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    // Each error belongs to the field that caused it: a taken name must not be explained under the
    // weight field, where it would read as a complaint about the number.
    val nameError = form.error?.takeIf {
        it == ExerciseFormError.NameBlank || it == ExerciseFormError.NameTaken
    }
    val setsError = form.error?.takeIf { it == ExerciseFormError.SetsTooLow }
    val repsError = form.error?.takeIf { it == ExerciseFormError.RepsBlank }
    val restError = form.error?.takeIf { it == ExerciseFormError.RestInvalid }
    val weightError = form.error?.takeIf { it == ExerciseFormError.WeightInvalid }

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
            Text(
                text = when (form.mode) {
                    ExerciseFormMode.Create -> stringResource(R.string.form_title_create)
                    ExerciseFormMode.EditSlot -> stringResource(R.string.form_title_edit)
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            GgymField(
                value = form.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.exercise_rename_label),
                isError = nameError != null,
                supportingText = nameError?.let { errorMessage(it) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GgymField(
                    value = form.sets,
                    onValueChange = onSetsChange,
                    label = stringResource(R.string.slot_field_sets),
                    isError = setsError != null,
                    supportingText = setsError?.let { errorMessage(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    onDecrease = { onStepSets(-1) },
                    onIncrease = { onStepSets(1) },
                    decreaseLabel = stringResource(R.string.form_sets_decrease),
                    increaseLabel = stringResource(R.string.form_sets_increase),
                    modifier = Modifier.weight(1f),
                )
                GgymField(
                    value = form.reps,
                    onValueChange = onRepsChange,
                    label = stringResource(R.string.slot_field_reps),
                    isError = repsError != null,
                    supportingText = repsError?.let { errorMessage(it) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }

            GgymField(
                value = form.restSeconds,
                onValueChange = onRestChange,
                label = stringResource(R.string.slot_field_rest),
                suffix = stringResource(R.string.unit_seconds),
                isError = restError != null,
                supportingText = restError?.let { errorMessage(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                onDecrease = { onStepRest(-RestTimerUseCases.STEP_SECONDS) },
                onIncrease = { onStepRest(RestTimerUseCases.STEP_SECONDS) },
                decreaseLabel = stringResource(R.string.form_rest_decrease),
                increaseLabel = stringResource(R.string.form_rest_increase),
                modifier = Modifier.fillMaxWidth(),
            )

            WeightField(
                value = form.weight,
                onValueChange = onWeightChange,
                onStep = onStepWeight,
                label = stringResource(R.string.slot_field_weight),
                isError = weightError != null,
                supportingText = weightError?.let { errorMessage(it) } ?: weightHint(form),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text(
                            text = stringResource(R.string.slot_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                GgymPrimaryButton(
                    text = when (form.mode) {
                        ExerciseFormMode.Create -> stringResource(R.string.form_add_to_day)
                        ExerciseFormMode.EditSlot -> stringResource(R.string.action_save)
                    },
                    onClick = onSave,
                )
            }
        }
    }
}

/**
 * What the weight field means here, in one line.
 *
 * This is the only place the two modes differ in a way the layout cannot show: a Create form's
 * number becomes the exercise's base weight, a slot's number belongs to that day alone. Getting this
 * wrong is exactly the bug this milestone fixes, so it is said out loud rather than implied.
 */
@Composable
private fun weightHint(form: ExerciseFormState): String = when (form.mode) {
    ExerciseFormMode.Create -> stringResource(R.string.form_weight_base_hint)
    ExerciseFormMode.EditSlot -> if (form.weight.isBlank()) {
        stringResource(R.string.slot_weight_hint)
    } else {
        stringResource(R.string.form_weight_slot_hint)
    }
}

/** The form's one error line. Anything without a message (a save in flight) shows nothing. */
@Composable
internal fun errorMessage(error: ExerciseFormError?): String? = when (error) {
    null -> null
    ExerciseFormError.NameBlank -> stringResource(R.string.form_error_name)
    ExerciseFormError.NameTaken -> stringResource(R.string.exercise_rename_taken)
    ExerciseFormError.SetsTooLow -> stringResource(R.string.slot_error_sets)
    ExerciseFormError.RepsBlank -> stringResource(R.string.slot_error_reps)
    ExerciseFormError.RestInvalid -> stringResource(R.string.slot_error_rest)
    ExerciseFormError.WeightInvalid -> stringResource(R.string.slot_error_weight)
}
