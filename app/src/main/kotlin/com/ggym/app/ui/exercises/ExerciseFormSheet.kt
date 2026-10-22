@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ggym.app.R
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.ui.common.GgymField
import com.ggym.app.ui.common.GgymPrimaryButton
import com.ggym.app.ui.common.WeightField
import kotlinx.coroutines.launch

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
    /** The dictionary form's read-only "Planned in" list; ignored by the other modes. */
    plannedIn: List<PlannedInRow> = emptyList(),
    onOpenPlan: ((PlannedInRow) -> Unit)? = null,
) {
    // Hidden start plus Expanded-only values is the "no half-opened sheet" shape: this form is
    // several fields, and a partially expanded sheet would show only some of them.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    // Which fields a mode has. The two create modes and the slot differ in whether a training day's
    // plan is part of the form: sets and reps are the day's, so the dictionary form does not show
    // them at all (a base sets/reps would be a second inheritance rule).
    val hasSlotFields = form.mode == ExerciseFormMode.Create || form.mode == ExerciseFormMode.EditSlot

    val scope = rememberCoroutineScope()
    // Saving from a create mode slides the sheet down first, then writes: there is no write-time
    // refusal on that path (a name the dictionary already holds is not an error — the entry wins),
    // so the day behind is never seen updating under an open form and the form never rides out the
    // pop. An edit can still be refused (a name collision), so it writes first and closes only when
    // the write accepts; the refusal then lands in the still-open form, where the user can read it.
    val save: () -> Unit = {
        val mayCloseFirst =
            (form.mode == ExerciseFormMode.Create || form.mode == ExerciseFormMode.CreateDictionary) &&
                form.validate() is ExerciseFormValidation.Valid
        if (mayCloseFirst) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) onSave()
            }
        } else {
            onSave()
        }
    }

    // Each error belongs to the field that caused it: a taken name must not be explained under the
    // weight field, where it would read as a complaint about the number.
    val nameError = form.error?.takeIf {
        it == ExerciseFormError.NameBlank || it == ExerciseFormError.NameTaken
    }
    val setsError = form.error?.takeIf { it == ExerciseFormError.SetsTooLow }.takeIf { hasSlotFields }
    val repsError = form.error?.takeIf { it == ExerciseFormError.RepsBlank }.takeIf { hasSlotFields }
    val restError = form.error?.takeIf { it == ExerciseFormError.RestInvalid }
    val weightError = form.error?.takeIf { it == ExerciseFormError.WeightInvalid }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            // The keyboard is part of the sheet's height: `imePadding` lifts the content above it,
            // and the scroll lets the weight field (the last one) be brought into view. Without
            // one of the two the keyboard covers the form and the number being typed is invisible.
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = when (form.mode) {
                    ExerciseFormMode.Create, ExerciseFormMode.CreateDictionary -> stringResource(R.string.form_title_create)
                    ExerciseFormMode.EditSlot, ExerciseFormMode.EditDictionary -> stringResource(R.string.form_title_edit)
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

            if (hasSlotFields) {
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
                        inputFilter = { it.digitsOnly() },
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
                inputFilter = { it.digitsOnly() },
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

            if (form.mode == ExerciseFormMode.EditDictionary) {
                PlannedInList(plannedIn = plannedIn, onOpenPlan = onOpenPlan)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text(
                            text = when (form.mode) {
                                ExerciseFormMode.EditDictionary -> stringResource(R.string.library_delete_action)
                                else -> stringResource(R.string.slot_remove)
                            },
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
                        ExerciseFormMode.CreateDictionary -> stringResource(R.string.library_add)
                        ExerciseFormMode.EditSlot, ExerciseFormMode.EditDictionary -> stringResource(R.string.action_save)
                    },
                    onClick = save,
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
    ExerciseFormMode.Create, ExerciseFormMode.CreateDictionary, ExerciseFormMode.EditDictionary ->
        stringResource(R.string.form_weight_base_hint)
    ExerciseFormMode.EditSlot -> if (form.weight.isBlank()) {
        stringResource(R.string.slot_weight_hint)
    } else {
        stringResource(R.string.form_weight_slot_hint)
    }
}

/**
 * The dictionary form's read-only "Planned in" list: where the exercise is used, and a way into
 * the day. Tapping a row opens that training day — the edit itself lives there, because sets and
 * reps are the day's plan rather than the exercise's.
 */
@Composable
private fun PlannedInList(plannedIn: List<PlannedInRow>, onOpenPlan: ((PlannedInRow) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.library_planned_in).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (plannedIn.isEmpty()) {
            Text(
                text = stringResource(R.string.library_planned_in_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            plannedIn.forEach { plan ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onOpenPlan != null) {
                                Modifier.clickable(role = Role.Button) { onOpenPlan(plan) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = plan.dayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = plan.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
