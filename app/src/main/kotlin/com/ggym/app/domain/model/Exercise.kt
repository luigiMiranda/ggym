package com.ggym.app.domain.model

/**
 * One entry of the global exercise dictionary.
 *
 * The dictionary exists so that "bench press" and "Bench Press" can never become two exercises
 * with two separate histories: [name] is unique, case-insensitively, both in the database and in
 * the autocomplete that fills it. See docs/ARCHITECTURE.md, "Data conventions".
 *
 * [currentWorkingWeight] is the weight the user should load next time — the value the workout
 * screen leads with. It stays `null` until a workout (or the routine editor) writes one.
 */
data class Exercise(
    val id: Long = 0,
    val name: String,
    val currentWorkingWeight: Double? = null,
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
) {
    companion object {
        /** Used when an exercise is added to a routine day without a more specific rest time. */
        const val DEFAULT_REST_SECONDS = 90
    }
}
