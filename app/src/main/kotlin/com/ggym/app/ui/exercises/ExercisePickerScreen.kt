@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.ui.common.ConfirmDeleteDialog

/**
 * Search the dictionary, reuse an entry, or create one by typing a name nobody has used yet.
 *
 * This is the only place the dictionary grows, which is why "Create «name»" only appears when the
 * database says the name is new: two entries for one exercise would mean two histories.
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
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text(stringResource(R.string.picker_search_label)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.canCreate) {
                    item(key = "create") {
                        PickerRow(
                            name = stringResource(R.string.picker_create, state.query.trim()),
                            icon = Icons.Filled.Add,
                            onClick = viewModel::createAndAdd,
                        )
                    }
                }

                items(state.results, key = { it.id }) { exercise ->
                    PickerRow(
                        name = exercise.name,
                        icon = null,
                        onClick = { viewModel.addExisting(exercise) },
                        onDelete = { viewModel.askDelete(exercise) },
                    )
                }

                if (!state.isLoading && state.results.isEmpty() && !state.canCreate) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.exercise_delete_title),
                    )
                }
            }
        }
    }
}
