package com.ggym.app.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToneGenerator

/**
 * The alert's audio behaviour, which is the one thing about the rest timer that cannot be checked
 * by reading state: while a rest ends, the app must **duck** whatever is playing rather than stop
 * or talk over it.
 *
 * The platform's tone generator and audio manager are shadows here, so the test can see the tone
 * that was played and the focus that was asked for — on the phone, the answer to this is the user's
 * music dipping for a moment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AndroidRestAlertPlayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)

    @Before
    fun setUp() {
        ShadowToneGenerator.reset()
    }

    @Test
    fun `the alert asks for a transient duck and then gives the music back`() = runTest {
        val player = AndroidRestAlertPlayer(
            context,
            // Unconfined, so the alert reaches the audio system the moment it is asked for; the
            // tone that follows is the one that has a duration to wait out.
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
        )

        player.play()
        advanceUntilIdle()

        val tones = ShadowToneGenerator.getPlayedTones()
        assertEquals(1, tones.size)
        assertEquals(ToneGenerator.TONE_PROP_BEEP2, tones[0].type())
        assertEquals(Duration.ofMillis(700), tones[0].duration())

        val focus = shadowOf(audioManager).lastAudioFocusRequest
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            focus.audioFocusRequest.focusGain,
        )
        assertNotNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }
}
