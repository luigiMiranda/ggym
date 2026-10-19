package com.ggym.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.domain.history.CalendarCell
import com.ggym.app.ui.common.ConfirmDeleteDialog
import com.ggym.app.ui.common.DateLabels
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymScreenTitle
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.activeLocale
import com.ggym.app.ui.common.durationSummary
import java.time.LocalDate

/**
 * The Calendar tab: a month with a dot under every trained day, and the selected day's workouts
 * underneath it.
 *
 * The grid and the day cards are one destination on purpose (the preview's hierarchy): the user
 * picks a day while the month that explains it stays on screen. The card is the cheap act —
 * reading and deleting — while "Edit log" opens the history screen where several sets can be
 * corrected with room to see them.
 */
@Composable
fun CalendarScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onOpenHistory: (Long) -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = activeLocale()
    val monthTitle = DateLabels.monthTitle(state.month, locale)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CalendarHeader(
                title = monthTitle,
                onTitleClick = viewModel::showToday,
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
                onToday = viewModel::showToday,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "calendar") {
                MonthGrid(
                    state = state,
                    onSelect = viewModel::selectDate,
                )
            }

            if (!state.isLoading) {
                if (!state.hasWorkoutsThisMonth) {
                    item(key = "month-empty") {
                        EmptyLine(text = stringResource(R.string.calendar_month_empty, monthTitle))
                    }
                } else {
                    item(key = "day-header") {
                        val count = state.selectedSessions.size
                        GgymSectionLabel(
                            text = stringResource(
                                R.string.calendar_day_header,
                                DateLabels.dayLong(state.selectedDate, locale),
                                pluralStringResource(R.plurals.calendar_day_workouts, count, count),
                            ),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    if (state.selectedSessions.isEmpty()) {
                        item(key = "day-empty") {
                            EmptyLine(
                                text = stringResource(
                                    R.string.calendar_day_empty,
                                    DateLabels.dayMonth(state.selectedDate, locale),
                                ),
                            )
                        }
                    }

                    items(state.selectedSessions, key = { it.sessionId }) { card ->
                        SessionCard(
                            card = card,
                            onEditLog = { onOpenHistory(card.sessionId) },
                            onDelete = { viewModel.askDelete(card) },
                        )
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { card ->
        ConfirmDeleteDialog(
            title = stringResource(
                R.string.history_delete_title,
                card.dayName,
                DateLabels.dayMonth(card.date, locale),
            ),
            message = stringResource(R.string.history_delete_message),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/**
 * The month header: the month and year, the two arrows, and the one jump back.
 *
 * The title is its own tap target returning to today, as well as the "Today" pill — the two most
 * likely places a thumb tries. Compact arrows keep the full month name from being ellipsized on a
 * narrow phone.
 */
@Composable
private fun CalendarHeader(
    title: String,
    onTitleClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthArrow(
            icon = Icons.Filled.ChevronLeft,
            contentDescription = stringResource(R.string.calendar_previous_month),
            onClick = onPrevious,
        )
        Text(
            text = title,
            style = GgymScreenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onTitleClick)
                .padding(horizontal = 4.dp),
        )
        MonthArrow(
            icon = Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.calendar_next_month),
            onClick = onNext,
        )
        GgymPill(text = stringResource(R.string.calendar_today), onClick = onToday)
    }
}

@Composable
private fun MonthArrow(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The weekday initials and the six weeks, as one block. */
@Composable
private fun MonthGrid(
    state: CalendarUiState,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = activeLocale()

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            DateLabels.weekdayInitials(locale).forEach { initial ->
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                )
            }
        }
        state.grid.weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        isToday = cell.date == state.today,
                        isSelected = cell.date == state.selectedDate,
                        workoutCount = state.workoutCount(cell.date),
                        onClick = { onSelect(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One day.
 *
 * The accessible label carries the number of workouts, so the dot is not the only way to know a
 * day was trained — and it names the date in full, because "14" alone says nothing out of context.
 */
@Composable
private fun DayCell(
    cell: CalendarCell,
    isToday: Boolean,
    isSelected: Boolean,
    workoutCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = activeLocale()
    val workouts = pluralStringResource(R.plurals.calendar_day_workouts, workoutCount, workoutCount)
    val description = stringResource(
        R.string.calendar_day_a11y,
        DateLabels.dayMonth(cell.date, locale),
        workouts,
    )
    val colors = MaterialTheme.colorScheme

    val numberColor = when {
        isSelected -> colors.onPrimary
        !cell.inMonth -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        else -> colors.onSurface
    }

    Column(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(colors.primary) else Modifier)
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.5.dp, colors.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = numberColor,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        workoutCount == 0 -> Color.Transparent
                        isSelected -> colors.onPrimary
                        else -> colors.primary
                    },
                ),
        )
    }
}

/** One finished workout on the selected day. */
@Composable
private fun SessionCard(
    card: CalendarSessionCard,
    onEditLog: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = activeLocale()

    Surface(shape = GgymCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.dayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = durationSummary(card.durationSeconds * 1_000L),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            card.exercises.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().alpha(if (line.skipped) 0.5f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (line.skipped) {
                            stringResource(R.string.history_exercise_skipped, line.name)
                        } else {
                            line.name
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = line.maxWeightKg
                            ?.takeUnless { line.skipped }
                            ?.let { weight ->
                                stringResource(
                                    R.string.history_exercise_value,
                                    line.setCount,
                                    stringResource(R.string.weight_kg, KgFormat.formatNumber(weight, locale)),
                                )
                            }
                            ?: stringResource(R.string.weight_unknown),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GgymPill(text = stringResource(R.string.history_edit_log), onClick = onEditLog)
                GgymPill(
                    text = stringResource(R.string.history_delete_workout),
                    tone = PillTone.Error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}
