package com.ggym.app.ui.exercises

import com.ggym.app.domain.model.Exercise
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.WeightInput
import com.ggym.app.ui.common.WeightParse
import java.util.Locale

/**
 * Which form is open, and therefore which rows its Save writes.
 *
 * The two modes differ in more than their title. **Create** makes a dictionary entry and plans it
 * in a day: its weight is the exercise's *base* weight, and the slot it creates follows it.
 * **EditSlot** rewrites a slot that already exists: its weight is the *day's* number, which wins
 * over the base until it is blanked. The hint under the weight field says which of the two is in
 * play, because that is the one thing about this form a user cannot guess.
 */
enum class ExerciseFormMode {

    /** An exercise that does not exist yet, being given its first numbers **and planned in a day**. */
    Create,

    /**
     * An exercise that does not exist yet, with **no day behind it**: the library's "Add exercise".
     * It makes a dictionary entry and nothing else, so it has no sets or reps to show — those are
     * the day's plan (see [EditDictionary]).
     */
    CreateDictionary,

    /** One planned exercise, on one training day. */
    EditSlot,

    /**
     * One dictionary entry, on its own: name, base weight and default rest. The sheet lists the
     * days that plan it read-only instead of offering sets and reps, because those belong to the day.
     */
    EditDictionary,
}

/**
 * The form's contents, held by the screen that owns it.
 *
 * The sheet itself is stateless like `BodyWeightSheet`, so the numbers live with the screen that
 * knows what Save means. Every field is **text**: the user can type any of them, and the ± controls
 * step what the field holds. Keeping the field's own characters is what makes clearing one to
 * retype it an ordinary state rather than a special case, and it is how a blank becomes something
 * the validation can name.
 *
 * [isOwnWeight] is display-only: it decides whether the weight field is described as the day's own
 * number or as one inherited from the exercise.
 */
data class ExerciseFormState(
    val mode: ExerciseFormMode,
    /**
     * The day's slot this form is about, in [ExerciseFormMode.EditSlot]. `null` in the create
     * modes, which have nothing planned yet.
     */
    val plannedExerciseId: Long? = null,
    /** The dictionary entry this form is about, in [ExerciseFormMode.EditDictionary]. */
    val exerciseId: Long? = null,
    val name: String = "",
    val sets: String = ExerciseFormDefaults.SETS.toString(),
    val reps: String = ExerciseFormDefaults.REPS,
    val restSeconds: String = Exercise.DEFAULT_REST_SECONDS.toString(),
    /** The weight field's text, as shown: a local number, or blank for "no weight of my own". */
    val weight: String = "",
    val isOwnWeight: Boolean = false,
    /**
     * Whether the user has been in the weight field.
     *
     * A slot that inherits its weight shows the number it inherits, so that what is on screen is what
     * would be saved. Saving a form the user never put a weight into must not *pin* that inherited
     * number to the day: an untouched field keeps the slot following the exercise, which is what
     * makes a logged set carry over. Touching it means the user chose this day's number.
     */
    val weightTouched: Boolean = false,
    /** The first thing wrong with the form, if anything: saving stops there. */
    val error: ExerciseFormError? = null,
)

/** What a brand-new exercise starts from, before the user touches anything. */
object ExerciseFormDefaults {
    const val SETS = 3
    const val REPS = "10"
}

/**
 * One row of the dictionary form's read-only "Planned in" list.
 *
 * Built by the screen that owns the form (it is the one with `stringResource`), so the sheet stays
 * a dumb renderer: the day's name, the numbers as text, and where tapping it goes.
 */
data class PlannedInRow(
    val routineId: Long,
    val routineDayId: Long,
    val dayName: String,
    val detail: String,
)

/** Why the form cannot be saved yet. Mapped to copy by the sheet, so it stays testable. */
enum class ExerciseFormError {
    NameBlank,
    NameTaken,
    SetsTooLow,
    RepsBlank,
    RestInvalid,
    WeightInvalid,
}

/** The form, once read: the values to write, or the first thing wrong with it. */
sealed interface ExerciseFormValidation {

    data class Valid(
        val name: String,
        val sets: Int,
        val reps: String,
        val restSeconds: Int,
        val weightKg: Double?,
    ) : ExerciseFormValidation

    data class Invalid(val error: ExerciseFormError) : ExerciseFormValidation
}

/**
 * Reads the form once.
 *
 * A name another entry already holds is **not** judged here: the unique `NOCASE` index is the
 * authority, and the answer (`NameTaken`) comes back from the write. A blank weight is not an
 * error — it is how a slot goes back to following the exercise, and how a new exercise starts with
 * no number at all.
 *
 * The `SetsTooLow` and `RestInvalid` branches are the states a cleared field leaves behind: the
 * fields accept anything, so "no sets" and "no rest time" are things the user can hand the form,
 * and the save has to say what is wrong rather than guess a number. They also still hold for a form
 * anything else builds, which is what makes the rule a property of the form rather than of a
 * control.
 */
fun ExerciseFormState.validate(): ExerciseFormValidation {
    val trimmedName = name.trim()
    val trimmedReps = reps.trim()
    val parsedSets = sets.trim().toIntOrNull()
    val parsedRest = restSeconds.trim().toIntOrNull()
    val parsedWeight = WeightInput.parse(weight)

    return when {
        trimmedName.isEmpty() -> ExerciseFormValidation.Invalid(ExerciseFormError.NameBlank)
        parsedSets == null || parsedSets < 1 -> ExerciseFormValidation.Invalid(ExerciseFormError.SetsTooLow)
        trimmedReps.isEmpty() -> ExerciseFormValidation.Invalid(ExerciseFormError.RepsBlank)
        parsedRest == null || parsedRest < 0 -> ExerciseFormValidation.Invalid(ExerciseFormError.RestInvalid)
        parsedWeight !is WeightParse.Weight -> ExerciseFormValidation.Invalid(ExerciseFormError.WeightInvalid)
        else -> ExerciseFormValidation.Valid(
            name = trimmedName,
            sets = parsedSets,
            reps = trimmedReps,
            restSeconds = parsedRest,
            weightKg = parsedWeight.weightKg,
        )
    }
}

/** Every keystroke clears the last error: the message was about the value that just changed. */
fun ExerciseFormState.withoutError(): ExerciseFormState = copy(error = null)

/**
 * The form's field edits, as pure functions.
 *
 * Both hosts — the picker's create form and the day's edit form — share these instead of each
 * writing its own stepper arithmetic, so a floor (one set, no negative rest) is stated once and
 * cannot drift between the two.
 */
fun ExerciseFormState.withName(text: String): ExerciseFormState = copy(name = text).withoutError()

/** Sets never fall below one: a planned exercise with no sets is not a plan. */
fun ExerciseFormState.withSetsStep(delta: Int): ExerciseFormState =
    copy(sets = ((sets.toIntOrNull() ?: 0) + delta).coerceAtLeast(1).toString()).withoutError()

fun ExerciseFormState.withSets(text: String): ExerciseFormState =
    copy(sets = text.digitsOnly()).withoutError()

fun ExerciseFormState.withReps(text: String): ExerciseFormState = copy(reps = text).withoutError()

/** Rest never falls below zero: there is no rest before the set. */
fun ExerciseFormState.withRestStep(deltaSeconds: Int): ExerciseFormState =
    copy(restSeconds = ((restSeconds.toIntOrNull() ?: 0) + deltaSeconds).coerceAtLeast(0).toString()).withoutError()

fun ExerciseFormState.withRest(text: String): ExerciseFormState =
    copy(restSeconds = text.digitsOnly()).withoutError()

/**
 * What a whole-number field accepts. The numeric keypad offers digits, so anything else came from
 * a paste; dropping it keeps the field's text parseable without turning an accidental letter into
 * an error the user has to find.
 */
private fun String.digitsOnly(): String = filter { it in '0'..'9' }

fun ExerciseFormState.withWeight(text: String): ExerciseFormState =
    copy(weight = text, weightTouched = true).withoutError()

/**
 * One tap of `−2.5` / `+2.5`. The field is reformatted so the user sees the number the buttons
 * produced; a field that holds no weight to step from is left exactly as it is.
 */
fun ExerciseFormState.withWeightStep(deltaKg: Double, locale: Locale): ExerciseFormState =
    WeightInput.stepped(weight, deltaKg)
        ?.let { withWeight(KgFormat.formatNumber(it, locale)) }
        ?: this
