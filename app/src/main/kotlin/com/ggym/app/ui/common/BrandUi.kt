package com.ggym.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggym.app.R
import com.ggym.app.ui.theme.GgymHeroColors
import com.ggym.app.ui.theme.LocalGgymDarkTheme

/**
 * The pieces the screen preview in `preview/index.html` is built from.
 *
 * They are deliberately small and boring: a screen title, an uppercase section label, a pill, a
 * stepper, the dark hero plate. Keeping them here is what makes the preview a *system* rather than
 * nine one-off layouts — every screen gets the same corner radii, the same label casing, the same
 * lime accent, and a change to one of them moves every screen at once.
 *
 * The plate is the one component that does not follow the active colour scheme: it is near-black in
 * light mode too, because chartreuse on an off-white page is unreadable. See `GgymHeroColors`.
 */

/** The screen title: heavy, tight, 27sp, the same weight as the preview's app bar. */
val GgymScreenTitle = androidx.compose.ui.text.TextStyle(
    fontWeight = FontWeight.Black,
    fontSize = 27.sp,
    letterSpacing = (-0.6).sp,
)

/** The card radius from the preview: 20dp, a step squarer than the 28dp hero plate. */
val GgymCardShape = RoundedCornerShape(20.dp)

/** A top-of-screen title, with an optional back button and trailing pills. */
@Composable
fun GgymScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = if (onBack != null) 4.dp else 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = GgymScreenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/** The small uppercase label above a group, as in the preview (`APPEARANCE`, `WORKING WEIGHT`). */
@Composable
fun GgymSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 11.5.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The label inside the dark plate: muted by the plate's own palette, not the page's. */
@Composable
fun GgymHeroLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = com.ggym.app.ui.theme.GgymHeroColors.Muted,
        modifier = modifier,
    )
}

enum class PillTone {
    /** The loud accent: what the user should read first. */
    Lime,

    /** An outline-only pill: a quiet action or a secondary fact. */
    Ghost,

    /** A performed thing. */
    Done,

    /** Destruction. */
    Error,
}

/** A pill, exactly as the preview draws them: 12sp bold, fully round, 4 × 11 padding. */
@Composable
fun GgymPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Ghost,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (tone) {
        PillTone.Lime -> colors.primaryContainer
        PillTone.Ghost -> Color.Transparent
        PillTone.Done -> colors.primary
        PillTone.Error -> colors.errorContainer
    }
    val content = when (tone) {
        PillTone.Lime -> colors.onPrimaryContainer
        PillTone.Ghost -> colors.onSurfaceVariant
        PillTone.Done -> colors.onPrimary
        PillTone.Error -> colors.onErrorContainer
    }
    val shape = CircleShape
    val base = modifier
        .clip(shape)
        .background(container)
        .then(if (tone == PillTone.Ghost) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)

    Box(modifier = base) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = content,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
        )
    }
}

/**
 * The dark hero plate: a card that stays dark in both schemes.
 *
 * It is a `Column`, not a `Surface`, so a screen can lay out its own rows without fighting a
 * content slot. Corner radius and border come from the preview (28dp, one hairline).
 */
@Composable
fun GgymHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = LocalGgymDarkTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (dark) GgymHeroColors.SurfaceDark else GgymHeroColors.SurfaceLight)
            .border(
                width = 1.dp,
                color = if (dark) GgymHeroColors.BorderDark else GgymHeroColors.BorderLight,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

/**
 * The app's one data-entry field: a tonal container, the label above the value, and the step
 * buttons when the number has a step.
 *
 * The approved previews draw every field this way — a small uppercase label above, the value at
 * display weight below — whether it is typed (a name, a target) or stepped (sets, rest, weight).
 * This is that one shape, so the exercise form cannot show three field styles in one column, and the
 * working weight looks the same here as it does in the body-weight and history editors.
 *
 * Focus and error are a border on the same container rather than a different container, so nothing
 * moves when the field changes state. The value is a plain [BasicTextField] under the container:
 * Material's outlined field puts its label *inside* the border, which is exactly the mismatch this
 * replaces.
 */
@Composable
fun GgymField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onDecrease: (() -> Unit)? = null,
    onIncrease: (() -> Unit)? = null,
    decreaseLabel: String = "",
    increaseLabel: String = "",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.medium
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val valueStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(width = 2.dp, color = borderColor, shape = shape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = valueStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = interactionSource,
                modifier = Modifier.weight(1f),
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (onDecrease != null || onIncrease != null) {
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StepSquare(
                        icon = Icons.Filled.Remove,
                        contentDescription = decreaseLabel,
                        enabled = onDecrease != null,
                        onClick = { onDecrease?.invoke() },
                    )
                    StepSquare(
                        icon = Icons.Filled.Add,
                        contentDescription = increaseLabel,
                        enabled = onIncrease != null,
                        onClick = { onIncrease?.invoke() },
                    )
                }
            }
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * A label/value card with two square buttons, the preview's one form control.
 *
 * Pass `null` callbacks for a value that is read-only: the buttons disappear and the fact keeps the
 * same silhouette, which is what lets the rest time sit next to the reps without looking disabled.
 */
@Composable
fun GgymStepper(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onDecrease: (() -> Unit)? = null,
    onIncrease: (() -> Unit)? = null,
    decreaseLabel: String = "",
    increaseLabel: String = "",
    valueColor: Color = Color.Unspecified,
    /**
     * A second half of the label, printed after a space in the brand accent: the workout's reps
     * stepper uses it to say `REPS · TARGET 8-10` without moving the actual off its own control.
     * Blank (the default) leaves the label to the caller.
     */
    labelAccent: String = "",
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append(label.uppercase())
                if (labelAccent.isNotBlank()) {
                    append(' ')
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(labelAccent.uppercase())
                    }
                }
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
                modifier = Modifier.weight(1f),
            )
            if (onDecrease != null || onIncrease != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepSquare(
                        icon = Icons.Filled.Remove,
                        contentDescription = decreaseLabel,
                        enabled = onDecrease != null,
                        onClick = { onDecrease?.invoke() },
                    )
                    StepSquare(
                        icon = Icons.Filled.Add,
                        contentDescription = increaseLabel,
                        enabled = onIncrease != null,
                        onClick = { onIncrease?.invoke() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepSquare(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.onSurface else colors.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The one loud button: a full-round pill, as the focused view's "Complete set" is in the preview. */
@Composable
fun GgymPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The extended FAB, in the preview's lime pill: the one floating action on a tab. */
@Composable
fun GgymFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text, style = MaterialTheme.typography.titleSmall) },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = modifier,
    )
}
