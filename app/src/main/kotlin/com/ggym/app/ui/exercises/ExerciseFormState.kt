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

    /** An exercise that does not exist yet, being given its first numbers. */
    Create,

    /** One planned exercise, on one training day. */
    EditSlot,
}

/**
 * The form's contents, held by the screen that owns it.
 *
 * The sheet itself is stateless like `BodyWeightSheet`, so the numbers live with the screen that
 * knows what Save means. Sets and rest are [Int]s rather than text because both are stepped and
 * neither has a text affordance left; the weight and the reps stay text because both are typed.
 *
 * [isOwnWeight] is display-only: it decides whether the weight field is described as the day's own
 * number or as one inherited from the exercise.
 */
data class ExerciseFormState(
    val mode: ExerciseFormMode,
    /**
     * The day's slot this form is about, in [ExerciseFormMode.EditSlot]. `null` in
     * [ExerciseFormMode.Create], which has nothing planned yet.
     */
    val plannedExerciseId: Long? = null,
    val name: String = "",
    val sets: Int = ExerciseFormDefaults.SETS,
    val reps: String = ExerciseFormDefaults.REPS,
    val restSeconds: Int = Exercise.DEFAULT_REST_SECONDS,
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
 * The `SetsTooLow` and `RestInvalid` branches are guards, not UI states: the −/+ controls cannot
 * produce them. They are kept because the form is a value that anything can build, and "a form with
 * zero sets is not saved" is a rule about the form rather than about one control.
 */
fun ExerciseFormState.validate(): ExerciseFormValidation {
    val trimmedName = name.trim()
    val trimmedReps = reps.trim()
    val parsedWeight = WeightInput.parse(weight)

    return when {
        trimmedName.isEmpty() -> ExerciseFormValidation.Invalid(ExerciseFormError.NameBlank)
        sets < 1 -> ExerciseFormValidation.Invalid(ExerciseFormError.SetsTooLow)
        trimmedReps.isEmpty() -> ExerciseFormValidation.Invalid(ExerciseFormError.RepsBlank)
        restSeconds < 0 -> ExerciseFormValidation.Invalid(ExerciseFormError.RestInvalid)
        parsedWeight !is WeightParse.Weight -> ExerciseFormValidation.Invalid(ExerciseFormError.WeightInvalid)
        else -> ExerciseFormValidation.Valid(
            name = trimmedName,
            sets = sets,
            reps = trimmedReps,
            restSeconds = restSeconds,
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
    copy(sets = (sets + delta).coerceAtLeast(1)).withoutError()

fun ExerciseFormState.withReps(text: String): ExerciseFormState = copy(reps = text).withoutError()

/** Rest never falls below zero: there is no rest before the set. */
fun ExerciseFormState.withRestStep(deltaSeconds: Int): ExerciseFormState =
    copy(restSeconds = (restSeconds + deltaSeconds).coerceAtLeast(0)).withoutError()

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
