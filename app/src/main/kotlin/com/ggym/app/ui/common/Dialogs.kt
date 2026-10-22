package com.ggym.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.ggym.app.R

/**
 * The one confirmation dialog for destructive actions, so "delete" always looks and reads the same.
 *
 * The message says what disappears *with* the thing being deleted: this app stores real training
 * work, and "are you sure?" is not an explanation. [confirmLabel] exists for the one destructive
 * action that is not a deletion (discarding a workout) and still deserves the same dialog; `null`
 * means the ordinary "Delete".
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel ?: stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * A one-field naming dialog, for routines, training days and exercises alike.
 *
 * Saving is only reachable with a non-blank name, so a screen never has to explain a blank one.
 * [error] is for the refusal a name can earn from the database — an exercise name another entry
 * already holds — and it leaves the dialog open with the typed text intact.
 */
@Composable
fun NameInputDialog(
    title: String,
    label: String,
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The working-weight field: a decimal keyboard, a `kg` suffix, and the two steps that matter in a
 * gym (−2.5 / +2.5, the smallest pair of plates). The steps are buttons rather than gestures so
 * they are reachable with a screen reader, and their labels say the direction.
 *
 * A blank field is legal and means "no weight of my own": the caller decides whether that reads as
 * inheriting the exercise's default or as an error.
 */
@Composable
fun WeightField(
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    GgymField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        suffix = stringResource(R.string.unit_kg),
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        onDecrease = { onStep(-WeightInput.STEP_KG) },
        onIncrease = { onStep(WeightInput.STEP_KG) },
        decreaseLabel = stringResource(R.string.weight_decrease),
        increaseLabel = stringResource(R.string.weight_increase),
        modifier = modifier.fillMaxWidth(),
    )
}
