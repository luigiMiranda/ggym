package com.ggym.app.domain.workout

import com.ggym.app.domain.repository.RestTimerCommands
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.repository.RestTimerStore
import com.ggym.app.domain.repository.WorkoutPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The rest countdown's rules: when a completed set starts one, what `+15 s` / `-15 s` do to a
 * deadline, and when a rest is over.
 *
 * The timer is **persisted state, not a screen's state**: the deadline and the exercise it belongs
 * to live in [RestTimerStore], and the foreground service that shows the notification is their only
 * writer. This class is the two halves of that arrangement:
 *
 * - **what the app asks for** — [startForSet], [extend], [skip], [stop] and [setEnabled] send a
 *   [RestTimerCommands] and never write the store themselves, so the notification and the app
 *   cannot disagree about what is running;
 * - **what a command means** — [stateForSet], [moveDeadline], [remainingMillis] and [hasElapsed] are
 *   the transitions and the arithmetic, applied by that service when a command arrives, when its
 *   own countdown elapses, or when a restarted process finds a deadline that is already in the
 *   past.
 *
 * [now] is injected, so a test can pin every deadline instead of sleeping through it.
 */
class RestTimerUseCases(
    private val store: RestTimerStore,
    private val commands: RestTimerCommands,
    private val preferences: WorkoutPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** The countdown to show, or `null` when there is none. */
    val state: Flow<RestTimerState?> = store.state

    /** The master switch's position, for the Settings screen. */
    val isEnabled: Flow<Boolean> = preferences.restTimerEnabled

    /**
     * Starts a rest for a set the user just completed, and returns the countdown it started — or
     * `null` when nothing should run, which is also when nothing is written and no command is sent:
     *
     * - the master switch is off: the set is logged and the timer stays out of the way;
     * - the set has no rest time of its own ([restSeconds] is zero, which the slot editor allows).
     *
     * A rest that is already running is replaced: completing the next set starts the next
     * countdown, which is what doing the set asked for.
     */
    suspend fun startForSet(
        workoutExerciseLogId: Long,
        exerciseName: String,
        restSeconds: Int,
    ): RestTimerState? {
        if (restSeconds <= 0 || !preferences.restTimerEnabled.first()) return null

        val started = stateForSet(workoutExerciseLogId, exerciseName, restSeconds)
        commands.start(started)
        return started
    }

    /** Asks for [deltaSeconds] more (or less) rest. Sends nothing when no countdown is running. */
    suspend fun extend(deltaSeconds: Int) {
        if (store.state.first() == null) return
        commands.extend(deltaSeconds)
    }

    /** Ends the rest early, silently: the user is moving on to the next set. */
    suspend fun skip() {
        if (store.state.first() == null) return
        commands.skip()
    }

    /** Tears the countdown down: the workout ended, or the master switch went off. */
    suspend fun stop() {
        if (store.state.first() == null) return
        commands.stop()
    }

    /**
     * Writes the master switch. Turning it off also ends a rest that is running — a switch reading
     * "off" above a live countdown would be a lie.
     */
    suspend fun setEnabled(enabled: Boolean) {
        preferences.setRestTimerEnabled(enabled)
        if (!enabled) stop()
    }

    /**
     * The countdown "rest [restSeconds] for this set" means: [restSeconds] from now, carrying the
     * exercise so the banner and the notification can say what the rest is for.
     */
    fun stateForSet(
        workoutExerciseLogId: Long,
        exerciseName: String,
        restSeconds: Int,
    ): RestTimerState = RestTimerState(
        endsAtMillis = now() + restSeconds * MILLIS_PER_SECOND,
        totalSeconds = restSeconds,
        exerciseLogId = workoutExerciseLogId,
        exerciseName = exerciseName,
    )

    /**
     * What `+15 s` / `-15 s` does to a running countdown: the same rest, [deltaSeconds] later.
     *
     * Returns `null` when the moved deadline is at or past now — the rest is *finished*, and the
     * caller runs the completion path rather than showing a countdown that is already over. Cutting
     * a rest short is not the same act as [skip]: a rest that reaches zero ends the way it would
     * have ended on its own.
     */
    fun moveDeadline(state: RestTimerState, deltaSeconds: Int): RestTimerState? {
        val moved = state.copy(endsAtMillis = state.endsAtMillis + deltaSeconds * MILLIS_PER_SECOND)
        return if (hasElapsed(moved)) null else moved
    }

    /** What is left of the rest at [at], in millis, never negative. */
    fun remainingMillis(state: RestTimerState, at: Long = now()): Long =
        (state.endsAtMillis - at).coerceAtLeast(0L)

    /**
     * Whether the deadline has passed. A rest that is over is not a countdown to watch: the service
     * posts the alert and clears the timer instead.
     */
    fun hasElapsed(state: RestTimerState, at: Long = now()): Boolean =
        remainingMillis(state, at) == 0L

    companion object {
        /** One tap of the `+15 s` / `-15 s` buttons, in the app and in the notification. */
        const val STEP_SECONDS = 15

        private const val MILLIS_PER_SECOND = 1_000L
    }
}
