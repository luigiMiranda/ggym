package com.ggym.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.ggym.app.domain.workout.AlertSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * How the alert sounds: the alarm stream, and a sonification content type.
 *
 * `USAGE_ALARM` is deliberate — the sound has to get through with the phone in a bag and music
 * playing, and the alarm volume is the one users leave audible. It also keeps the alert out of the
 * notification stream, whose volume is a different setting.
 */
internal fun alertAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * The focus the alert holds while it rings: transient, and **may duck** — the music keeps playing
 * underneath, quieter, for as long as the ring lasts.
 *
 * Exposed rather than inlined so a test can pin the one constant that must never quietly change:
 * M3 chose it so a rest ending would dip the music instead of stopping it, and it is the property
 * most easily lost.
 */
internal fun alertDuckFocusRequest(): AudioFocusRequest = AudioFocusRequest.Builder(
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
)
    .setAudioAttributes(alertAudioAttributes())
    .build()

/**
 * The URI the alert plays, or `null` for [AlertSound.None] and for a blank URI.
 *
 * A picked ringtone that has since been deleted or unmounted is not filtered here: it is only known
 * to be unopenable when it fails to open, which is [AndroidRestAlertPlayer]'s fallback to the
 * platform default.
 */
internal fun alertSoundUri(sound: AlertSound): Uri? = when (sound) {
    AlertSound.SystemDefault -> defaultAlarmUri()
    AlertSound.None -> null
    is AlertSound.Ringtone -> sound.uri.takeIf(String::isNotBlank)?.let(Uri::parse)
}

/**
 * The platform's default alarm sound, falling back to its default notification sound: a device can
 * have no alarm tone configured, and a rest ending is still worth hearing.
 */
internal fun defaultAlarmUri(): Uri? =
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

/**
 * The sound a rest ends with, and the only thing that can stop it.
 *
 * It is the app's own sound rather than a notification channel's, because a channel's sound is
 * played by the system at full volume and cannot duck anything. A rest ending in a gym has to be
 * heard *over* whatever the user is listening to without stopping it, so the alert holds a
 * transient audio focus that makes the music dip instead — for as long as it rings, which is why
 * this is a start/stop pair rather than the one-shot `play()` M3 had.
 *
 * It is behind an interface because the alert is the one part of the timer a JVM test cannot hear:
 * a test asserts "the alert was started with this sound and stopped", and the phone answers "the
 * music dipped".
 */
interface RestAlertPlayer {

    /** Starts [sound] ringing, replacing anything already ringing. [AlertSound.None] is a no-op. */
    fun start(sound: AlertSound)

    /** Silences the alert and gives the music back. Safe to call when nothing is ringing. */
    fun stop()
}

/**
 * The real alert: a `MediaPlayer` looping the chosen sound, under `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
 *
 * Three details matter.
 *
 * **The sound is resolved, not assumed.** [AlertSound.SystemDefault] is the platform's default
 * *alarm* sound rather than a bundled asset — M3's "no audio asset to ship or to keep in sync with a
 * licence" survives, and the alarm stream is the one users leave audible. A ringtone the user picked
 * can be gone by the time it is played — deleted, on an unmounted card, or a stale URI from a
 * restored settings file — so a URI that will not open falls back to the default rather than leaving
 * a silent alert.
 *
 * **Focus is held until stopped, not for the length of a beep.** That is what makes the music stay
 * dipped for the whole ring and come back afterwards, and it is the property M3 was protecting.
 *
 * **The work is dispatched, not done inline.** `prepare()` touches the filesystem, and the caller is
 * the service's main thread. It runs on a single-threaded scope, which also means a `start` and a
 * `stop` are executed in the order they were called — with a free-threaded scope, a `stop` that
 * overtook a `start` would leave a looping player ringing with no way to reach it.
 */
class AndroidRestAlertPlayer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(1),
    ),
) : RestAlertPlayer {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Touched only from [scope], which has one thread, so no lock is needed. */
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var holdingFocus = false

    override fun start(sound: AlertSound) {
        scope.launch { play(sound) }
    }

    override fun stop() {
        scope.launch { release() }
    }

    private fun play(sound: AlertSound) {
        release()

        val wanted = alertSoundUri(sound) ?: return
        // Prepared but not started: focus is asked for first, so the sound never plays unducked.
        val prepared = preparedPlayer(wanted)
            ?: defaultAlarmUri()?.takeIf { it != wanted }?.let(::preparedPlayer)
            ?: return

        requestFocus()
        prepared.start()
        player = prepared
    }

    /**
     * A looping player parked at the start of [uri], or `null` if it will not open.
     *
     * The instance is released on every failure path: a `MediaPlayer` that threw during `prepare()`
     * would otherwise hold a native decoder until the collector got to it.
     */
    private fun preparedPlayer(uri: Uri): MediaPlayer? {
        val created = MediaPlayer()
        return runCatching {
            created.setAudioAttributes(alertAudioAttributes())
            created.setDataSource(context, uri)
            created.isLooping = true
            created.prepare()
            created
        }.getOrElse {
            created.runCatching { release() }
            null
        }
    }

    private fun release() {
        player?.runCatching { if (isPlaying) stop() }
        player?.runCatching { release() }
        player = null
        releaseFocus()
    }

    private fun requestFocus() {
        val request = alertDuckFocusRequest()
        focusRequest = request
        holdingFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        // Focus is a request, not a right: if it was refused, the sound still plays — what is lost
        // is the dip, not the alert.
        if (holdingFocus) audioManager.abandonAudioFocusRequest(request)
        holdingFocus = false
    }
}
