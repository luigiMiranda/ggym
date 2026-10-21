package com.ggym.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.domain.workout.AlertSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * The alert's audio behaviour, which is the one thing about the rest timer that cannot be checked by
 * reading state: while a rest ends, the app must **duck** whatever is playing rather than stop or talk
 * over it, and it must keep doing so for as long as it rings.
 *
 * The audio manager is a shadow here, so the test can see the focus that was asked for and given
 * back — on the phone, the answer to this is the user's music dipping and then coming back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AndroidRestAlertPlayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)

    /**
     * Robolectric's `MediaPlayer` refuses a data source it knows nothing about, so the ringtone the
     * alert plays has to be described to it first: one second long, prepared instantly. Without this
     * the player would fail to prepare, fall back to the default — which it also does not know — and
     * the focus assertions below would pass vacuously by never running.
     */
    @Before
    fun describeTheRingtone() {
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, Uri.parse(SOME_RINGTONE)),
            ShadowMediaPlayer.MediaInfo(1_000, 0),
        )
    }

    @After
    fun forgetTheRingtone() {
        ShadowMediaPlayer.resetStaticState()
    }

    /**
     * The focus the alert holds. Pinned by constant as well as by call, because this is the decision
     * M6 had to carry over from M3 intact: anything but `MAY_DUCK` stops the music instead of
     * dipping it, and the difference is invisible in every other test.
     */
    @Test
    fun `the alert's focus is a transient duck`() {
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            alertDuckFocusRequest().focusGain,
        )
    }

    /** The alarm stream is the one users leave audible, and the one a bag cannot muffle. */
    @Test
    fun `the alert plays on the alarm stream`() {
        val attributes = alertAudioAttributes()
        assertEquals(AudioAttributes.USAGE_ALARM, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.contentType)
    }

    @Test
    fun `None resolves to no sound at all`() {
        assertNull(alertSoundUri(AlertSound.None))
    }

    @Test
    fun `a blank ringtone uri is not a sound`() {
        assertNull(alertSoundUri(AlertSound.Ringtone("   ")))
    }

    @Test
    fun `a picked ringtone plays from its own uri`() {
        assertEquals(
            Uri.parse(SOME_RINGTONE),
            alertSoundUri(AlertSound.Ringtone(SOME_RINGTONE)),
        )
    }

    /** The default is the platform's own alarm sound, not an asset the app has to ship. */
    @Test
    fun `the system default resolves to the platform's alarm sound`() {
        assertEquals(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            alertSoundUri(AlertSound.SystemDefault),
        )
    }

    /** With nothing to play there is nothing to duck for, so the music is left completely alone. */
    @Test
    fun `silence never touches the audio system`() = runTest {
        val player = AndroidRestAlertPlayer(context, playerScope())

        player.start(AlertSound.None)
        advanceUntilIdle()

        assertNull(shadowOf(audioManager).lastAudioFocusRequest)
    }

    /** A `stop` with nothing ringing must not abandon a focus the app does not hold. */
    @Test
    fun `stopping an alert that never started gives nothing back`() = runTest {
        val player = AndroidRestAlertPlayer(context, playerScope())

        player.stop()
        advanceUntilIdle()

        assertNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    /**
     * The pair that matters: a ringing alert holds the ducking focus, and only `stop` releases it.
     *
     * A ring is not a beep, so the focus has to outlive the call that started it — which is exactly
     * what changed in M6, and what this asserts.
     */
    @Test
    fun `a ringing alert holds the ducking focus until it is stopped`() = runTest {
        val player = AndroidRestAlertPlayer(context, playerScope())

        player.start(AlertSound.Ringtone(SOME_RINGTONE))
        advanceUntilIdle()

        val requested = shadowOf(audioManager).lastAudioFocusRequest
        assertNotNull("a sound to play is a reason to ask for focus", requested)
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            requested.audioFocusRequest.focusGain,
        )
        assertNull("the ring has not ended yet", shadowOf(audioManager).lastAbandonedAudioFocusRequest)

        player.stop()
        advanceUntilIdle()

        assertNotNull(
            "stopping the ring is what gives the music back",
            shadowOf(audioManager).lastAbandonedAudioFocusRequest,
        )
    }

    /** The alert dispatches its work, so its ordering is the scope's and not the caller's. */
    private fun TestScope.playerScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private companion object {
        const val SOME_RINGTONE = "content://media/external/audio/media/42"
    }
}
