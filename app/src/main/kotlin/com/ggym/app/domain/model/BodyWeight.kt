package com.ggym.app.domain.model

import java.time.LocalDate

/** A body-weight measurement on a given day. At most one is expected per day. */
data class BodyWeightLog(
    val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
)

/**
 * The only weight range the app accepts for a *human body*, per the product specification: a
 * value has to be strictly inside it, so `25.0` and `300.0` are both rejected.
 *
 * This is a domain rule, not a storage rule: it is enforced where a body weight enters the app
 * (the import validator today, the body-weight screen later) so that the message the user reads
 * and the value the database accepts can never disagree. Working weights are deliberately *not*
 * constrained by it — a leg press is heavier than a person.
 */
object BodyWeightLimits {

    const val MIN_KG = 25.0
    const val MAX_KG = 300.0

    fun isValid(weightKg: Double): Boolean = weightKg.isFinite() && weightKg > MIN_KG && weightKg < MAX_KG
}
