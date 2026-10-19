@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.activeLocale

/**
 * Search the dictionary, reuse an entry, or create one by typing a name nobody has used yet.
 *
 * The results are one card with a "Suggested" label, exactly as the preview's editor presents them,
 * and each row carries the weight and rest time the entry already knows. This is the only place the
 * dictionary grows, which is why "Create «name»" only appears when the database says the name is
 * new: two entries for one exercise would mean two histories.
 */
@Composable
fun ExercisePickerScreen(
    routineDayId: Long,
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    viewModel: ExercisePickerViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { onBack() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GgymScreenHeader(
                title = stringResource(R.string.picker_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                placeholder = { Text(stringResource(R.string.picker_search_label)) },
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

            Spacer(Modifier.padding(top = 14.dp))

            val locale = activeLocale()
            if (state.canCreate || state.results.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = GgymCardShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        GgymSectionLabel(
                            text = stringResource(R.string.picker_suggested),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                        if (state.canCreate) {
                            PickerRow(
                                name = stringResource(R.string.picker_create, state.query.trim()),
                                detail = null,
                                icon = Icons.Filled.Add,
                                onClick = viewModel::createAndAdd,
                            )
                        }
                        state.results.forEachIndexed { index, exercise ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                )
                            }
                            PickerRow(
                                name = exercise.name,
                                detail = stringResource(
                                    R.string.picker_detail,
                                    exercise.weightKg?.let { weight ->
                                        stringResource(R.string.weight_kg, KgFormat.formatNumber(weight, locale))
                                    } ?: stringResource(R.string.weight_unknown),
                                    stringResource(R.string.slot_rest, exercise.restSeconds),
                                ),
                                icon = null,
                                onClick = { viewModel.addExisting(exercise) },
                                onDelete = { viewModel.askDelete(exercise) },
                            )
                        }
                    }
                }
            }

            if (!state.isLoading && state.results.isEmpty() && !state.canCreate) {
                Text(
                    text = stringResource(R.string.picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    state.pendingDelete?.let { exercise ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.exercise_delete_title),
            message = stringResource(R.string.exercise_delete_message, exercise.name),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    if (state.blockedDeleteRoutines.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBlockedDelete,
            title = { Text(stringResource(R.string.exercise_delete_blocked_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.exercise_delete_blocked_message,
                        state.blockedDeleteRoutines.joinToString(separator = ", "),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissBlockedDelete) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun PickerRow(
    name: String,
    detail: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, top = 9.dp, end = 4.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.exercise_delete_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
