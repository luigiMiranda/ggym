package com.ggym.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sound a rest ends with.
 *
 * It is the app's own tone rather than a notification channel's sound, because a channel's sound is
 * played by the system at full volume and cannot duck anything. A rest ending in a gym has to be
 * heard *over* whatever the user is listening to without stopping it, so the alert asks for a
 * transient audio focus that makes the music dip instead.
 *
 * It is behind an interface because the alert is the one part of the timer a JVM test cannot hear:
 * a test asserts "the alert was played", and the phone answers "the music dipped".
 */
interface RestAlertPlayer {

    /** Plays the alert. Returns as soon as the tone is handed to the audio system. */
    fun play()
}

/**
 * The real alert: a short tone, played while the app holds `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
 *
 * Three details matter. The focus request is what ducks the music instead of talking over it, and
 * it is abandoned when the tone is over, so the dip lasts as long as the beep. The tone is a
 * `ToneGenerator` — a handful of samples the platform already has, no audio asset to ship or to
 * keep in sync with a licence. And it runs on the player's own scope, so neither the service nor
 * the broadcast receiver that plays it has to wait a second for a beep.
 */
class AndroidRestAlertPlayer(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : RestAlertPlayer {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    override fun play() {
        scope.launch {
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            val holding = audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

            val tone = ToneGenerator(STREAM, VOLUME_PERCENT)
            try {
                tone.startTone(TONE, TONE_MILLIS)
                delay(TONE_MILLIS.toLong())
                tone.stopTone()
            } finally {
                tone.release()
                // Focus is a request, not a right: if it was refused, the tone still plays — what
                // is lost is the dip, not the alert.
                if (holding) audioManager.abandonAudioFocusRequest(focus)
            }
        }
    }

    private companion object {
        /**
         * The alarm stream, deliberately: the sound has to get through with the phone in a bag and
         * music playing, and the alarm volume is the one users leave audible.
         */
        const val STREAM = AudioManager.STREAM_ALARM
        const val VOLUME_PERCENT = 90
        const val TONE = ToneGenerator.TONE_PROP_BEEP2
        const val TONE_MILLIS = 700
    }
}
