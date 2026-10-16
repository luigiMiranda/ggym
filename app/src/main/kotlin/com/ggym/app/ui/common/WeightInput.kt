package com.ggym.app.ui.common

/** What the weight field holds after being read. */
sealed interface WeightParse {

    /** [weightKg] is `null` when the field is blank: the slot inherits the exercise's default. */
    data class Weight(val weightKg: Double?) : WeightParse

    /** Not a weight at all: letters, more than two decimals, a negative number, a lone separator. */
    data object Invalid : WeightParse
}

/**
 * Reading and stepping the working-weight field.
 *
 * The field is numeric and local: both "82.5" and "82,5" are accepted on any device, because a
 * user who switches keyboards should not have to relearn where the decimal separator is. A blank
 * field is not an error — it is how a slot goes back to inheriting the exercise's default — and
 * two decimals is the limit, matching the smallest plate pair the app is willing to believe in.
 */
object WeightInput {

    private val WEIGHT_PATTERN = Regex("""^\d{1,3}([.,]\d{1,2})?$""")

    /** Step of the −/+ buttons: the smallest pair of plates in a gym. */
    const val STEP_KG: Double = 2.5

    fun parse(text: String): WeightParse {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return WeightParse.Weight(null)
        if (!WEIGHT_PATTERN.matches(trimmed)) return WeightParse.Invalid

        val weightKg = trimmed.replace(',', '.').toDouble()
        return if (weightKg.isFinite() && weightKg >= 0) WeightParse.Weight(weightKg) else WeightParse.Invalid
    }

    /**
     * The value after one step, or `null` when the field does not hold a weight to step from.
     *
     * Blank counts as zero, so the first tap on "+" lands on 2.5 kg rather than refusing to move,
     * and the result never goes below zero: there is no such thing as −2.5 kg on the bar.
     */
    fun stepped(text: String, deltaKg: Double = STEP_KG): Double? = when (val parsed = parse(text)) {
        is WeightParse.Weight -> ((parsed.weightKg ?: 0.0) + deltaKg).coerceAtLeast(0.0)
        WeightParse.Invalid -> null
    }
}
