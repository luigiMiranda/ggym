package com.ggym.app.domain.workout

/**
 * Which sound the rest-over alert makes.
 *
 * The sound is *ours to play* rather than the notification channel's, and it has to stay that way:
 * M3 decided the alert ducks the music instead of talking over it, and a channel's sound is played
 * by the system at full volume, where nothing can duck it. So the user's choice is a choice of what
 * **this app** plays, and [SystemDefault] means the system's own alarm sound rather than "let the
 * notification system handle it".
 */
sealed interface AlertSound {

    /** The platform's default alarm sound. Chosen by default: it is the one users leave audible. */
    data object SystemDefault : AlertSound

    /** No sound at all. The notification still arrives and still vibrates — this is quiet, not off. */
    data object None : AlertSound

    /**
     * A sound the user picked with the platform's ringtone picker, as the URI it returned.
     *
     * It is stored as the URI string rather than a resolved `Uri` so that the domain keeps no
     * Android type, and so a chosen ringtone that has since been deleted or unmounted degrades to
     * [SystemDefault] when it is played rather than throwing on the way in.
     */
    data class Ringtone(val uri: String) : AlertSound
}

/**
 * How long the alert rings, once the rest is over.
 *
 * Both modes end, which is the point: "until I stop it" is a promise about *intent*, not about
 * arithmetic, and the arithmetic still needs an answer for a phone left in a gym bag. [Manual]
 * therefore carries a backstop, and the backstop is long enough that no real workout reaches it.
 */
sealed interface RingPolicy {

    /** Rings until the user stops it, or until [BACKSTOP_SECONDS] pass. */
    data object Manual : RingPolicy

    /** Rings for [seconds] and then stops by itself. */
    data class Automatic(val seconds: Int) : RingPolicy

    /**
     * The number of seconds the alert actually rings for.
     *
     * This is the one place the two modes become a duration, so the service has a single number to
     * schedule from and no second opinion about when a ring ends.
     */
    val ringSeconds: Int
        get() = when (this) {
            Manual -> BACKSTOP_SECONDS
            is Automatic -> seconds
        }

    companion object {
        /** Bounds of the stepper. Below [MIN_SECONDS] a ring is a click; above [MAX_SECONDS] it nags. */
        const val MIN_SECONDS = 5
        const val MAX_SECONDS = 120
        const val STEP_SECONDS = 5

        /** The default is automatic: a rest ending during a set should not need a free hand. */
        const val DEFAULT_SECONDS = 20

        /** The generous cap on [Manual]. Ten minutes, chosen so nothing real ever meets it. */
        const val BACKSTOP_SECONDS = 600

        /** What a fresh install rings with. */
        val Default: RingPolicy = Automatic(DEFAULT_SECONDS)

        /** [seconds] clamped into the stepper's range, so no caller can store an unusable value. */
        fun automatic(seconds: Int): Automatic = Automatic(seconds.coerceIn(MIN_SECONDS, MAX_SECONDS))

        /** One tap of the stepper, clamped at both ends. */
        fun step(seconds: Int, steps: Int): Automatic =
            automatic(seconds + steps * STEP_SECONDS)
    }
}
