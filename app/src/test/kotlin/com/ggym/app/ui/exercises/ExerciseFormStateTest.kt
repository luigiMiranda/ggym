package com.ggym.app.ui.exercises

import com.ggym.app.domain.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The form as a value: what it accepts, what it refuses, and what one tap of a stepper does.
 *
 * No database and no composition here on purpose — this is the arithmetic and the rules, and the
 * two screens that show the form (the picker and a training day) are tested separately, where the
 * question is where the write goes rather than what it contains.
 */
class ExerciseFormStateTest {

    private val locale = Locale.ENGLISH

    private fun form(
        name: String = "Bench Press",
        sets: String = "4",
        reps: String = "8-10",
        restSeconds: String = "90",
        weight: String = "80",
        isOwnWeight: Boolean = true,
        error: ExerciseFormError? = null,
    ) = ExerciseFormState(
        mode = ExerciseFormMode.EditSlot,
        plannedExerciseId = 7,
        name = name,
        sets = sets,
        reps = reps,
        restSeconds = restSeconds,
        weight = weight,
        isOwnWeight = isOwnWeight,
        error = error,
    )

    private fun valid(state: ExerciseFormState): ExerciseFormValidation.Valid =
        state.validate() as ExerciseFormValidation.Valid

    @Test
    fun `a complete form reads back trimmed and parsed`() {
        val read = valid(form(name = "  Bench Press  ", reps = " 8-10 "))

        assertEquals("Bench Press", read.name)
        assertEquals("8-10", read.reps)
        assertEquals(4, read.sets)
        assertEquals(90, read.restSeconds)
        assertEquals(80.0, read.weightKg)
    }

    @Test
    fun `a target that is a word is a target`() {
        assertEquals("AMRAP", valid(form(reps = "AMRAP")).reps)
    }

    @Test
    fun `a blank name is refused`() {
        assertEquals(
            ExerciseFormError.NameBlank,
            (form(name = "   ").validate() as ExerciseFormValidation.Invalid).error,
        )
    }

    @Test
    fun `a blank target is refused`() {
        assertEquals(
            ExerciseFormError.RepsBlank,
            (form(reps = " ").validate() as ExerciseFormValidation.Invalid).error,
        )
    }

    @Test
    fun `letters in the weight are refused`() {
        assertEquals(
            ExerciseFormError.WeightInvalid,
            (form(weight = "eighty").validate() as ExerciseFormValidation.Invalid).error,
        )
    }

    /**
     * The blank-weight rule the whole weight feature rests on: a slot with no number of its own is
     * how it follows the exercise, and a new exercise with no number is how it starts.
     */
    @Test
    fun `a blank weight is legal and asks for no number`() {
        assertEquals(null, valid(form(weight = "", isOwnWeight = false)).weightKg)
    }

    @Test
    fun `a comma decimal is read on any keyboard`() {
        assertEquals(82.5, valid(form(weight = "82,5")).weightKg)
    }

    @Test
    fun `zero sets, a blank count and a blank rest are all refused`() {
        assertEquals(
            ExerciseFormError.SetsTooLow,
            (form(sets = "0").validate() as ExerciseFormValidation.Invalid).error,
        )
        assertEquals(
            ExerciseFormError.SetsTooLow,
            (form(sets = "").validate() as ExerciseFormValidation.Invalid).error,
        )
        assertEquals(
            ExerciseFormError.RestInvalid,
            (form(restSeconds = "").validate() as ExerciseFormValidation.Invalid).error,
        )
        assertEquals(
            ExerciseFormError.RestInvalid,
            (form(restSeconds = "-15").validate() as ExerciseFormValidation.Invalid).error,
        )
    }

    /**
     * The user's request, as a rule: the ± buttons are a convenience, not the only way in. A rest of
     * 75 s is typed exactly rather than stepped to from 60 or 90, and the same is true of sets.
     */
    @Test
    fun `a typed count is exact, not the nearest step`() {
        assertEquals(75, valid(form(restSeconds = "75")).restSeconds)
        assertEquals(5, valid(form(sets = "5")).sets)
        assertEquals("75", form(restSeconds = "90").withRest("75").restSeconds)
        assertEquals("5", form(sets = "4").withSets("5").sets)
    }

    @Test
    fun `a pasted letter is dropped rather than kept as an error`() {
        assertEquals("12", form(restSeconds = "90").withRest("1a2").restSeconds)
        assertEquals("3", form(sets = "3").withSets("3x").sets)
    }

    @Test
    fun `the set stepper never falls below one`() {
        assertEquals("1", form(sets = "2").withSetsStep(-1).sets)
        assertEquals("1", form(sets = "1").withSetsStep(-1).sets)
        assertEquals("2", form(sets = "1").withSetsStep(1).sets)
        assertEquals("1", form(sets = "").withSetsStep(1).sets)
    }

    @Test
    fun `the rest stepper never falls below zero`() {
        assertEquals("0", form(restSeconds = "15").withRestStep(-15).restSeconds)
        assertEquals("0", form(restSeconds = "0").withRestStep(-15).restSeconds)
        assertEquals("105", form(restSeconds = "90").withRestStep(15).restSeconds)
    }

    @Test
    fun `the weight stepper starts from zero and is formatted in the locale`() {
        assertEquals("2.5", form(weight = "").withWeightStep(2.5, locale).weight)
        assertEquals("82.5", form(weight = "80").withWeightStep(2.5, locale).weight)
        assertEquals("77.5", form(weight = "80").withWeightStep(-2.5, locale).weight)
        assertEquals("82,5", form(weight = "80").withWeightStep(2.5, Locale.ITALIAN).weight)
    }

    @Test
    fun `the weight stepper leaves an unreadable field alone`() {
        val state = form(weight = "eighty")
        assertEquals(state, state.withWeightStep(2.5, locale))
    }

    @Test
    fun `the weight stepper cannot go below the empty bar`() {
        assertEquals("0", form(weight = "0").withWeightStep(-2.5, locale).weight)
    }

    @Test
    fun `any edit clears the last error`() {
        assertEquals(null, form(error = ExerciseFormError.WeightInvalid).withWeight("81").error)
        assertEquals(null, form(error = ExerciseFormError.NameTaken).withName("Front Squat").error)
        assertEquals(null, form(error = ExerciseFormError.SetsTooLow).withSetsStep(1).error)
        assertEquals(null, form(error = ExerciseFormError.SetsTooLow).withSets("3").error)
        assertEquals(null, form(error = ExerciseFormError.RepsBlank).withReps("10").error)
        assertEquals(null, form(error = ExerciseFormError.RestInvalid).withRestStep(15).error)
        assertEquals(null, form(error = ExerciseFormError.RestInvalid).withRest("90").error)
    }

    /**
     * The weight is the one field whose *absence of an edit* carries meaning (see
     * [ExerciseFormState.weightTouched]), so the flag has to move with the text.
     */
    @Test
    fun `touching the weight marks the field as the user's own`() {
        assertTrue(form(weight = "80", isOwnWeight = false).withWeight("80").weightTouched)
        assertTrue(form(weight = "").withWeightStep(2.5, locale).weightTouched)
    }

    @Test
    fun `a create form starts from the app's defaults`() {
        val fresh = ExerciseFormState(mode = ExerciseFormMode.Create, name = "Cable Fly")

        assertEquals(ExerciseFormDefaults.SETS.toString(), fresh.sets)
        assertEquals(ExerciseFormDefaults.REPS, fresh.reps)
        assertEquals(Exercise.DEFAULT_REST_SECONDS.toString(), fresh.restSeconds)
        assertEquals("", fresh.weight)
        assertEquals(3, valid(fresh).sets)
    }
}
