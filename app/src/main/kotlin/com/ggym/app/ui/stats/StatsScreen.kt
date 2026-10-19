@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.domain.model.BodyWeightLimits
import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.stats.BodyWeightRange
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.DateLabels
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymPrimaryButton
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.RowMenu
import com.ggym.app.ui.common.RowMenuAction
import com.ggym.app.ui.common.WeightField
import com.ggym.app.ui.common.WeightInput
import com.ggym.app.ui.common.WeightParse
import com.ggym.app.ui.common.activeLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * The Stats tab: attendance, body weight, one exercise's progression, and the log.
 *
 * Every number on this screen is a row the user wrote — a finished workout, a completed set, a
 * measurement — and every chart has a text summary beside it with the same numbers, so the picture
 * is a second reading of the data rather than the only one. The body-weight card is the app's one
 * place to enter a body weight.
 */
@Composable
fun StatsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: StatsViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = activeLocale()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = { GgymScreenHeader(title = stringResource(R.string.nav_stats)) },
    ) { padding ->
        if (!state.isLoading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "attendance") {
                    AttendanceCard(state = state, locale = locale)
                }
                item(key = "body-weight") {
                    BodyWeightCard(
                        state = state,
                        locale = locale,
                        onSelectRange = viewModel::selectRange,
                        onLog = viewModel::openLogSheet,
                    )
                }
                item(key = "progression") {
                    ProgressionCard(
                        state = state,
                        locale = locale,
                        onOpenPicker = viewModel::openExercisePicker,
                    )
                }
                item(key = "log") {
                    BodyWeightLogCard(
                        state = state,
                        locale = locale,
                        onLog = viewModel::openLogSheet,
                        onEdit = viewModel::openEditSheet,
                        onDelete = viewModel::askDeleteEntry,
                    )
                }
            }
        }
    }

    state.sheet?.let { sheet ->
        BodyWeightSheet(
            sheet = sheet,
            today = state.today,
            onDateChange = viewModel::setSheetDate,
            onSave = viewModel::saveSheet,
            onDelete = viewModel::askDeleteSheetEntry,
            onDismiss = viewModel::dismissSheet,
        )
    }

    state.pendingDelete?.let { entry ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.stats_delete_title),
            message = stringResource(
                R.string.stats_delete_message,
                DateLabels.dayMonth(entry.date, locale),
            ),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    if (state.exercisePickerOpen) {
        ExerciseSelectorSheet(
            exercises = state.exercises,
            onSelect = viewModel::selectExercise,
            onDismiss = viewModel::dismissExercisePicker,
        )
    }
}

/** "How many days did I train each month?" — twelve bars and one sentence. */
@Composable
private fun AttendanceCard(state: StatsUiState, locale: Locale) {
    StatCard {
        GgymSectionLabel(stringResource(R.string.stats_attendance_label))

        if (!state.hasWorkouts) {
            EmptyNote(stringResource(R.string.stats_attendance_empty))
        } else {
            AttendanceChart(
                counts = state.attendance,
                modifier = Modifier.padding(top = 8.dp),
            )

            val best = state.attendance.maxByOrNull { it.workoutDays }
            if (best != null) {
                Text(
                    text = stringResource(
                        R.string.stats_attendance_summary,
                        pluralStringResource(
                            R.plurals.stats_workout_days,
                            state.totalWorkoutDays,
                            state.totalWorkoutDays,
                        ),
                        pluralStringResource(R.plurals.stats_months, state.attendance.size, state.attendance.size),
                        DateLabels.monthName(best.month, locale),
                        best.workoutDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** The body-weight trend with its range filters and the window's min/average/max. */
@Composable
private fun BodyWeightCard(
    state: StatsUiState,
    locale: Locale,
    onSelectRange: (BodyWeightRange) -> Unit,
    onLog: () -> Unit,
) {
    StatCard {
        GgymSectionLabel(stringResource(R.string.stats_body_weight_label))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BodyWeightRange.entries.forEach { range ->
                GgymPill(
                    text = rangeLabel(range),
                    tone = if (range == state.bodyRange) PillTone.Lime else PillTone.Ghost,
                    onClick = { onSelectRange(range) },
                )
            }
        }

        if (state.bodyStats.count == 0) {
            EmptyNote(
                text = stringResource(R.string.stats_body_empty),
                action = stringResource(R.string.stats_body_empty_action),
                onAction = onLog,
            )
        } else {
            val points = state.bodyStats.points.map { ChartPoint(it.date.toEpochDay(), it.weightKg) }
            LineChart(points = points, modifier = Modifier.padding(top = 10.dp))

            val min = state.bodyStats.minKg
            val average = state.bodyStats.averageKg
            val max = state.bodyStats.maxKg
            if (min != null && average != null && max != null) {
                Text(
                    text = stringResource(
                        R.string.stats_body_summary,
                        KgFormat.formatOneDecimal(min, locale),
                        KgFormat.formatOneDecimal(average, locale),
                        KgFormat.formatOneDecimal(max, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (state.bodyStats.count == 1) {
                Text(
                    text = stringResource(R.string.stats_body_one),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One exercise's maximum working weight over time, with a least-squares trend. */
@Composable
private fun ProgressionCard(
    state: StatsUiState,
    locale: Locale,
    onOpenPicker: () -> Unit,
) {
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GgymSectionLabel(
                text = stringResource(R.string.stats_progression_label),
                modifier = Modifier.weight(1f),
            )
            if (state.exercises.isNotEmpty()) {
                GgymPill(
                    text = state.selectedExerciseName
                        .takeIf { it.isNotEmpty() }
                        ?.let { stringResource(R.string.stats_exercise_selector, it) }
                        ?: stringResource(R.string.stats_progression_pick),
                    onClick = onOpenPicker,
                )
            }
        }

        when {
            state.exercises.isEmpty() -> EmptyNote(stringResource(R.string.stats_progression_no_exercises))

            state.progression.isEmpty -> EmptyNote(
                stringResource(R.string.stats_progression_empty, state.selectedExerciseName),
            )

            else -> {
                val points = state.progression.points
                    .map { ChartPoint(it.date.toEpochDay(), it.maxWeightKg) }
                val trend = state.progression.trend?.let { line ->
                    ChartTrend(
                        from = ChartPoint(line.from.date.toEpochDay(), line.from.maxWeightKg),
                        to = ChartPoint(line.to.date.toEpochDay(), line.to.maxWeightKg),
                    )
                }

                LineChart(points = points, trend = trend, modifier = Modifier.padding(top = 10.dp))

                // The chart's numbers, in text: a picture is not something a screen reader can read.
                Text(
                    text = stringResource(
                        R.string.stats_progression_summary,
                        pluralStringResource(R.plurals.stats_progression_sessions, points.size, points.size),
                        stringResource(
                            R.string.weight_kg,
                            KgFormat.formatNumber(points.last().y, locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LegendDot(color = MaterialTheme.colorScheme.primary, label = stringResource(R.string.stats_progression_legend_max))
                    if (trend != null) {
                        LegendDot(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            label = stringResource(R.string.stats_progression_legend_trend),
                            hollow = true,
                        )
                    }
                }

                if (points.size == 1) {
                    Text(
                        text = stringResource(R.string.stats_progression_one),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** The measurements, newest first, each editable and deletable through its own menu. */
@Composable
private fun BodyWeightLogCard(
    state: StatsUiState,
    locale: Locale,
    onLog: () -> Unit,
    onEdit: (BodyWeightLog) -> Unit,
    onDelete: (BodyWeightLog) -> Unit,
) {
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GgymSectionLabel(
                text = stringResource(R.string.stats_log_label),
                modifier = Modifier.weight(1f),
            )
            GgymPill(
                text = stringResource(R.string.stats_log_add),
                tone = PillTone.Lime,
                onClick = onLog,
            )
        }

        if (state.bodyLog.isEmpty()) {
            EmptyNote(stringResource(R.string.stats_log_empty))
        } else {
            state.bodyLog.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (entry.date == state.today) {
                            stringResource(R.string.stats_log_today)
                        } else {
                            DateLabels.dayShort(entry.date, locale)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.weight_kg,
                            KgFormat.formatNumber(entry.weightKg, locale),
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    RowMenu(
                        actions = listOf(
                            RowMenuAction(
                                label = stringResource(R.string.action_edit),
                                icon = Icons.Filled.Edit,
                                onClick = { onEdit(entry) },
                            ),
                            RowMenuAction(
                                label = stringResource(R.string.action_delete),
                                icon = Icons.Filled.Delete,
                                onClick = { onDelete(entry) },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The one form a body weight enters the app through.
 *
 * It reuses the working-weight field, so the separators, the two decimals and the −2.5/+2.5 steps
 * are the same as everywhere else. The date row opens the platform picker; the value's range is
 * the same `BodyWeightLimits` the repository enforces, checked here so the Save button can be
 * disabled before a round trip.
 */
@Composable
private fun BodyWeightSheet(
    sheet: BodyWeightSheetState,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onSave: (Double?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = activeLocale()
    var weight by rememberSaveable(sheet.entryId) {
        mutableStateOf(sheet.initialWeightKg?.let { KgFormat.formatNumber(it, locale) } ?: "")
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val parsed = WeightInput.parse(weight)
    val parsedWeight = (parsed as? WeightParse.Weight)?.weightKg
    val invalid = parsed is WeightParse.Invalid ||
        parsedWeight == null ||
        !BodyWeightLimits.isValid(parsedWeight) ||
        sheet.invalid

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
                text = stringResource(
                    if (sheet.isEditing) R.string.stats_sheet_edit_title else R.string.stats_sheet_new_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )

            WeightField(
                value = weight,
                onValueChange = { weight = it },
                onStep = onStep,
                label = stringResource(R.string.stats_sheet_weight),
                isError = invalid,
                supportingText = stringResource(R.string.stats_error_weight).takeIf { invalid },
            )

            DateRow(
                label = stringResource(R.string.stats_sheet_date),
                value = if (sheet.date == today) {
                    stringResource(R.string.stats_log_today)
                } else {
                    DateLabels.dayMonth(sheet.date, locale)
                },
                onClick = { showDatePicker = true },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (sheet.isEditing) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.stats_sheet_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(Modifier)
                }

                GgymPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { onSave(parsedWeight) },
                    enabled = !invalid,
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = sheet.date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** The searchable exercise list: the dictionary, not "only exercises with history". */
@Composable
private fun ExerciseSelectorSheet(
    exercises: List<ExerciseOption>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(exercises, query) {
        val needle = query.trim()
        if (needle.isEmpty()) exercises else exercises.filter { it.name.contains(needle, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_select_exercise),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.stats_exercise_search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_exercise_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(filtered, key = { it.id }) { option ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable(role = Role.Button, onClick = { onSelect(option.id) })
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A chart's empty state: what is missing and, when there is an action, the one tap that fixes it. */
@Composable
private fun EmptyNote(
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (action != null && onAction != null) {
                GgymPill(text = action, tone = PillTone.Lime, onClick = onAction)
            }
        }
    }
}

/** A legend entry: a dot in the line's colour and the word beside it. */
@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String, hollow: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            shape = CircleShape,
            color = if (hollow) MaterialTheme.colorScheme.surface else color,
            border = if (hollow) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
            modifier = Modifier.padding(0.dp),
        ) {
            Spacer(Modifier.padding(3.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The card shape every section uses: the preview's 20dp container on the tonal layer. */
@Composable
private fun StatCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(shape = GgymCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun rangeLabel(range: BodyWeightRange): String = stringResource(
    when (range) {
        BodyWeightRange.ONE_MONTH -> R.string.stats_range_1m
        BodyWeightRange.THREE_MONTHS -> R.string.stats_range_3m
        BodyWeightRange.SIX_MONTHS -> R.string.stats_range_6m
        BodyWeightRange.ONE_YEAR -> R.string.stats_range_1y
        BodyWeightRange.ALL -> R.string.stats_range_all
    },
)
