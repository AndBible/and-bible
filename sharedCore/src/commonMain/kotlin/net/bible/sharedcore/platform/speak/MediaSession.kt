package net.bible.sharedcore.platform.speak

enum class MediaPlaybackState { PLAYING, PAUSED, STOPPED }

/** Commands from headset buttons, lock screen and car controls. */
interface MediaCommands {
    fun play()
    fun pause()
    fun stop()
    fun next()
    fun previous()
    fun fastForward()
    fun rewind()
}

/** Routes system media commands in and publishes playback state and now-playing metadata. */
interface MediaSession {
    fun activate(commands: MediaCommands)
    fun setPlaybackState(state: MediaPlaybackState)
    fun setNowPlaying(title: String, album: String)
    fun release()
}
