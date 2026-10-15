package net.bible.android.platform

import android.support.v4.media.session.PlaybackStateCompat
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.speak.MediaCommands
import net.bible.sharedcore.platform.speak.MediaPlaybackState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MediaButtonAndroidSessionTest {
    private lateinit var session: AndroidMediaSession
    private var originalButtonsEnabled = true
    private val commands = object : MediaCommands {
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun fastForward() = Unit
        override fun rewind() = Unit
    }

    @Before fun setUp() {
        originalButtonsEnabled = CommonUtils.settings.getBoolean("enable_bluetooth_pref", true)
        CommonUtils.settings.setBoolean("enable_bluetooth_pref", false)
        session = AndroidMediaSession(BibleApplication.application)
        session.activate(commands)
    }

    @After fun tearDown() {
        try {
            session.release()
        } finally {
            CommonUtils.settings.setBoolean("enable_bluetooth_pref", originalButtonsEnabled)
        }
    }

    @Test fun playbackStatesPreserveActionsPositionAndSpeed() {
        val controller = session.ms.controller
        val actions = controller.playbackState!!.actions
        assertEquals(PlaybackStateCompat.STATE_STOPPED, controller.playbackState!!.state)
        for ((state, androidState) in listOf(
            MediaPlaybackState.PLAYING to PlaybackStateCompat.STATE_PLAYING,
            MediaPlaybackState.PAUSED to PlaybackStateCompat.STATE_PAUSED,
            MediaPlaybackState.STOPPED to PlaybackStateCompat.STATE_STOPPED,
        )) {
            session.setPlaybackState(state)
            val actual = controller.playbackState!!
            assertEquals(androidState, actual.state)
            assertEquals(actions, actual.actions)
            assertEquals(0L, actual.position)
            assertEquals(1f, actual.playbackSpeed)
        }
    }

    @Test fun releasingAnOlderSessionDoesNotClearTheReceiverOwner() {
        val newer = AndroidMediaSession(BibleApplication.application)
        newer.activate(commands)
        try {
            session.release()
            assertSame(newer, AndroidMediaSession.current)
        } finally {
            newer.release()
        }
        assertNull(AndroidMediaSession.current)
    }
}
