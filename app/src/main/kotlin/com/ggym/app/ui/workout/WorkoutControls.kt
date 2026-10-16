@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ggym.app.R
import com.ggym.app.ui.common.DurationFormat
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.WeightInput
import com.ggym.app.ui.common.durationSummary
import com.ggym.app.ui.theme.GgymHeroWeight
import com.ggym.app.ui.theme.GgymHeroWeightDisplay
import java.util.Locale

/**
 * The pieces the active-workout screen is built from: the hero weight, the reps stepper, the set
 * chips, the rest banner, the finish sheet, and the one big action.
 *
 * They are kept apart from the screen because each one is a single decision: the hero is the
 * largest interactive element in the app, the chips are the workout's editable history, and the
 * action is the only thing a sweaty finger has to find. Long-press and drag gestures are avoided
 * throughout — a gym is exactly where a screen reader and a large touch target matter most.
 */

/**
 * The weight the user is about to lift, at display size.
 *
 * Editable in place (tap the number), with the `−2.5` / `+2.5` pair on either side: the two ways
 * anyone actually changes a weight. The value is the field's own text, so a half-typed "82." is
 * never rewritten under the user's finger, and the digits use tabular figures so they do not jump
 * sideways as they are typed.
 */
@Composable
fun WeightHero(
    hero: WeightHeroState,
    onWeightChange: (String) -> Unit,
    onStep: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onStep(-WeightInput.STEP_KG) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.weight_decrease))
            }

            BasicTextField(
                value = hero.weightText,
                onValueChange = onWeightChange,
                singleLine = true,
                textStyle = GgymHeroWeightDisplay.copy(
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.widthIn(min = 128.dp),
                decorationBox = { field ->
                    if (hero.weightText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.weight_unknown),
                            style = GgymHeroWeightDisplay,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                        )
                    }
                    field()
                },
            )

            FilledTonalIconButton(
                onClick = { onStep(WeightInput.STEP_KG) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.weight_increase))
            }
        }
        Text(
            text = stringResource(R.string.unit_kg),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The reps of the selected set, one tap at a time. Never below zero. */
@Composable
fun RepsStepper(
    reps: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(onClick = { onStep(-1) }, enabled = reps > 0) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.workout_reps_decrease))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = reps.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = stringResource(R.string.workout_reps_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = { onStep(1) }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.workout_reps_increase))
        }
    }
}

/** The exercise's sets, the selected one included, with `+ Add set` at the end of the strip. */
@Composable
fun SetChipRow(
    sets: List<WorkoutSetRow>,
    selectedSetId: Long?,
    locale: Locale,
    onSelect: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A plain scrolling row rather than a lazy one: a workout has a handful of sets, they are all
    // cheap to compose, and every chip staying in the tree keeps the strip honest to accessibility
    // — and to a test that wants to reach the last one.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sets.forEach { set ->
            SetChip(
                set = set,
                selected = set.id == selectedSetId,
                locale = locale,
                onSelect = { onSelect(set.id) },
                onRemove = { onRemove(set.id) },
            )
        }
        AddSetChip(onClick = onAddSet)
    }
}

/** One set: its position, what it holds, and — while it is still a prefill — the way to drop it. */
@Composable
private fun SetChip(
    set: WorkoutSetRow,
    selected: Boolean,
    locale: Locale,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, end = if (set.isCompleted) 14.dp else 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (set.isCompleted) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(set.position.toString(), style = MaterialTheme.typography.labelLarge)
            Text(setWeightText(set, locale), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.workout_set_reps, set.reps),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!set.isCompleted) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.workout_remove_set, set.position),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSetChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.workout_add_set), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The rest that is running: how long is left, what it is for, and the three taps that matter
 * between sets. The time is recomputed from the stored deadline every tick, never decremented.
 */
@Composable
fun RestBanner(
    banner: RestBanner,
    locale: Locale,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.workout_resting),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = banner.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = DurationFormat.clock(
                    millis = banner.remainingSeconds * 1_000,
                    locale = locale,
                ),
                style = GgymHeroWeight,
            )
            IconButton(onClick = onSubtractTime) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.rest_action_subtract_time))
            }
            IconButton(onClick = onAddTime) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rest_action_add_time))
            }
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.rest_action_skip))
            }
        }
    }
}

/**
 * The one big action of the focused view, and the shape morph as the workout moves on: it says what
 * the next tap does, and it changes when that means something else.
 */
@Composable
fun WorkoutAction(
    action: FocusedAction,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).animateContentSize(),
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                (fadeIn(spring()) + scaleIn(spring(), initialScale = 0.9f)) togetherWith
                    (fadeOut(spring()) + scaleOut(spring(), targetScale = 0.9f))
            },
        ) { current ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = actionLabel(current), style = MaterialTheme.typography.titleLarge)
                if (current == FocusedAction.NextExercise) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun actionLabel(action: FocusedAction): String = when (action) {
    FocusedAction.CompleteSet -> stringResource(R.string.workout_complete_set)
    FocusedAction.NextExercise -> stringResource(R.string.workout_next_exercise)
    FocusedAction.FinishWorkout -> stringResource(R.string.workout_finish)
    FocusedAction.AddSet -> stringResource(R.string.workout_add_set)
}

/**
 * What finishing means, before it happens: the sets that count, the exercises nothing happened for,
 * and how long the workout took. "Keep training" is the way back, and the two endings are both on
 * this one sheet so the question is asked once.
 */
@Composable
fun FinishWorkoutSheet(
    state: ActiveWorkoutUiState,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onKeepTraining: () -> Unit,
) {
    val sets = pluralStringResource(R.plurals.workout_sets, state.totalSets, state.totalSets)
    val skipped = pluralStringResource(
        R.plurals.workout_exercises_skipped,
        state.skippedExercises,
        state.skippedExercises,
    )

    ModalBottomSheet(
        onDismissRequest = onKeepTraining,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.workout_finish_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.workout_progress, state.completedSets, sets),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.workout_finish_detail, skipped, durationSummary(state.elapsedMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Text(stringResource(R.string.workout_finish_confirm), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_discard), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onKeepTraining, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_keep_training))
            }
        }
    }
}

/** A set's weight as the chips show it: an empty prefill is "—", a completed zero is 0 kg. */
@Composable
internal fun setWeightText(set: WorkoutSetRow, locale: Locale): String = if (!set.isCompleted && set.weightKg == 0.0) {
    stringResource(R.string.weight_unknown)
} else {
    stringResource(R.string.weight_kg, KgFormat.formatNumber(set.weightKg, locale))
}
