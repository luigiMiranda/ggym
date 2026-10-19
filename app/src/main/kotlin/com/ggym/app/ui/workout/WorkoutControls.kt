@file:OptIn(ExperimentalMaterial3Api::class)

package com.ggym.app.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggym.app.R
import com.ggym.app.ui.common.DurationFormat
import com.ggym.app.ui.common.GgymHeroLabel
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymPrimaryButton
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.GgymStepper
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.WeightInput
import com.ggym.app.ui.common.durationSummary
import com.ggym.app.ui.theme.GgymHeroColors
import com.ggym.app.ui.theme.GgymHeroWeight
import com.ggym.app.ui.theme.GgymHeroWeightDisplay
import java.util.Locale

/**
 * The pieces the active-workout screen is built from: the hero plate, the reps and rest steppers,
 * the set rows, the rest card and the one big action.
 *
 * They are kept apart from the screen because each one is a single decision: the hero is the
 * largest interactive element in the app, the set rows are the workout's editable history, and the
 * action is the only thing a sweaty finger has to find. Long-press and drag gestures are avoided
 * throughout — a gym is exactly where a screen reader and a large touch target matter most.
 */

/**
 * The weight the user is about to lift, at display size, on the dark plate.
 *
 * Editable in place (tap the number), with the `−2.5` / `+2.5` pair on the right: the two ways
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GgymHeroLabel(stringResource(R.string.slot_field_weight))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.width(IntrinsicSize.Min)) {
                    if (hero.weightText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.weight_unknown),
                            style = GgymHeroWeightDisplay,
                            color = GgymHeroColors.Muted,
                        )
                    }
                    BasicTextField(
                        value = hero.weightText,
                        onValueChange = onWeightChange,
                        singleLine = true,
                        textStyle = GgymHeroWeightDisplay.copy(
                            color = GgymHeroColors.Accent,
                            textAlign = TextAlign.Start,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        cursorBrush = SolidColor(GgymHeroColors.Accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = stringResource(R.string.unit_kg),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = GgymHeroColors.Accent,
                    modifier = Modifier.padding(start = 6.dp, bottom = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStepButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.weight_decrease),
                    accent = false,
                    onClick = { onStep(-WeightInput.STEP_KG) },
                )
                HeroStepButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.weight_increase),
                    accent = true,
                    onClick = { onStep(WeightInput.STEP_KG) },
                )
            }
        }

        Text(
            text = stringResource(R.string.workout_weight_hint),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = GgymHeroColors.Muted,
        )
    }
}

/** The plate's two square buttons: neutral for less, lime for more. */
@Composable
private fun HeroStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (accent) GgymHeroColors.AccentButton else GgymHeroColors.Button)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (accent) GgymHeroColors.OnAccentButton else GgymHeroColors.OnButton,
        )
    }
}

/**
 * The sets of one exercise as the preview lists them: a vertical row each, a performed row tinted
 * and checked, the selected row carrying the edit. The two pills underneath are the only actions
 * that are not a set: add one, or start the rest without performing anything.
 */
@Composable
fun SetRowList(
    sets: List<WorkoutSetRow>,
    selectedSetId: Long?,
    locale: Locale,
    onSelect: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onAddSet: () -> Unit,
    onStartRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        sets.forEach { set ->
            SetRow(
                set = set,
                selected = set.id == selectedSetId,
                locale = locale,
                onSelect = { onSelect(set.id) },
                onRemove = { onRemove(set.id) },
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GgymPill(
                text = stringResource(R.string.workout_add_set),
                tone = PillTone.Ghost,
                onClick = onAddSet,
            )
            GgymPill(
                text = stringResource(R.string.workout_start_rest),
                tone = PillTone.Ghost,
                onClick = onStartRest,
            )
        }
    }
}

@Composable
private fun SetRow(
    set: WorkoutSetRow,
    selected: Boolean,
    locale: Locale,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            set.isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = effects,
        label = "set-row-container",
    )
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val corner by animateDpAsState(
        targetValue = if (set.isCompleted) GgymRadiiForSets.completed else GgymRadiiForSets.planned,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "set-row-corner",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(start = 12.dp, top = 9.dp, end = if (set.isCompleted) 12.dp else 2.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (set.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (set.isCompleted) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = set.position.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.workout_set_label, set.position),
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(
            text = setRowNumbers(set, locale),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = content,
        )
        if (!set.isCompleted) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.workout_remove_set, set.position),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * A row's numbers as the preview writes them: "82.5 × 10" for a performed set and "82.5 × —" for
 * one that is still a plan. The unit is on the hero, not on every row.
 */
@Composable
private fun setRowNumbers(set: WorkoutSetRow, locale: Locale): String {
    val weight = if (!set.isCompleted && set.weightKg == 0.0) {
        stringResource(R.string.weight_unknown)
    } else {
        KgFormat.formatNumber(set.weightKg, locale)
    }
    val reps = if (set.isCompleted) {
        stringResource(R.string.workout_set_reps, set.reps)
    } else {
        stringResource(R.string.workout_set_reps_unknown)
    }
    return "$weight $reps"
}

/** The two row radii, as an object so the numbers are not buried in the component. */
private object GgymRadiiForSets {
    val planned = 14.dp
    val completed = 18.dp
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
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                GgymSectionLabel(stringResource(R.string.workout_resting))
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
                color = MaterialTheme.colorScheme.primary,
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
    // Read once here: the transition lambda is not a composable scope.
    val scheme = MaterialTheme.motionScheme
    val effectsIn = scheme.defaultEffectsSpec<Float>()
    val effectsOut = scheme.fastEffectsSpec<Float>()
    val spatialIn = scheme.defaultSpatialSpec<Float>()
    val spatialOut = scheme.fastSpatialSpec<Float>()

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                // The label is an effect (it must be read at once) while its arrival is spatial: the
                // new label grows into place with the scheme's slight overshoot.
                (fadeIn(effectsIn) + scaleIn(spatialIn, initialScale = 0.92f)) togetherWith
                    (fadeOut(effectsOut) + scaleOut(spatialOut, targetScale = 0.92f))
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
            // The summary's headline number earns the brand hero style: this sheet is the last
            // thing the user reads before deciding to save.
            Text(
                text = stringResource(R.string.workout_progress, state.completedSets, sets),
                style = GgymHeroWeight,
            )
            Text(
                text = stringResource(R.string.workout_finish_detail, skipped, durationSummary(state.elapsedMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GgymPrimaryButton(
                text = stringResource(R.string.workout_finish_confirm),
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_discard), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onKeepTraining, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_keep_training))
            }
        }
    }
}
