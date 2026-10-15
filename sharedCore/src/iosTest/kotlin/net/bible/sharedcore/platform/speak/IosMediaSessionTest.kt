package net.bible.sharedcore.platform.speak

import kotlinx.cinterop.ExperimentalForeignApi
import platform.MediaPlayer.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosMediaSessionTest {
    private class Recorder : MediaCommands {
        val events = mutableListOf<String>()
        override fun play() { events += "play" }
        override fun pause() { events += "pause" }
        override fun stop() { events += "stop" }
        override fun next() { events += "next" }
        override fun previous() { events += "previous" }
        override fun fastForward() { events += "fastForward" }
        override fun rewind() { events += "rewind" }
    }

    @Test fun activationMetadataStatesAndRelease() {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        val info = MPNowPlayingInfoCenter.defaultCenter()
        val session = IosMediaSession(commands, info)
        val all = listOf(commands.playCommand, commands.pauseCommand, commands.stopCommand,
            commands.nextTrackCommand, commands.previousTrackCommand, commands.skipForwardCommand,
            commands.skipBackwardCommand, commands.togglePlayPauseCommand)
        try {
            session.activate(Recorder())
            assertTrue(all.all { it.enabled })
            session.setPlaybackState(MediaPlaybackState.PLAYING)
            session.setNowPlaying("John 3:16", "John")
            assertEquals("John 3:16", info.nowPlayingInfo?.get(MPMediaItemPropertyTitle))
            assertEquals("John", info.nowPlayingInfo?.get(MPMediaItemPropertyAlbumTitle))
            assertEquals(1.0, info.nowPlayingInfo?.get(MPNowPlayingInfoPropertyPlaybackRate))
            session.setPlaybackState(MediaPlaybackState.PAUSED)
            assertEquals(0.0, info.nowPlayingInfo?.get(MPNowPlayingInfoPropertyPlaybackRate))
            assertEquals("John 3:16", info.nowPlayingInfo?.get(MPMediaItemPropertyTitle))
            session.setPlaybackState(MediaPlaybackState.STOPPED)
            assertNull(info.nowPlayingInfo)
            session.setNowPlaying("John 3:17", "John")
            assertEquals(0.0, info.nowPlayingInfo?.get(MPNowPlayingInfoPropertyPlaybackRate))
            session.activate(Recorder())
            assertTrue(all.all { it.enabled })
            session.release()
            assertFalse(all.any { it.enabled })
            assertNull(info.nowPlayingInfo)
            session.release()
            assertFalse(all.any { it.enabled })
        } finally { session.release() }
    }
}
