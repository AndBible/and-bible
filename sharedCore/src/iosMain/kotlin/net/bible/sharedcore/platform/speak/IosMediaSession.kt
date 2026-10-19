package net.bible.sharedcore.platform.speak

import kotlinx.cinterop.ExperimentalForeignApi
import platform.MediaPlayer.*

/** Owns only its command targets; the consumer calls this adapter on Main and releases it. */
@OptIn(ExperimentalForeignApi::class)
class IosMediaSession(
    private val commandCenter: MPRemoteCommandCenter = MPRemoteCommandCenter.sharedCommandCenter(),
    private val infoCenter: MPNowPlayingInfoCenter = MPNowPlayingInfoCenter.defaultCenter(),
    isDiscrete: () -> Boolean = { false },
    neutralTitle: () -> String = { "" },
) : MediaSession {
    private val metadata = IosSystemMediaMetadata(isDiscrete, neutralTitle)
    private var state = MediaPlaybackState.STOPPED
    private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()

    override fun activate(commands: MediaCommands) {
        removeTargets()
        bind(commandCenter.playCommand, commands::play)
        bind(commandCenter.pauseCommand, commands::pause)
        bind(commandCenter.stopCommand, commands::stop)
        bind(commandCenter.nextTrackCommand, commands::next)
        bind(commandCenter.previousTrackCommand, commands::previous)
        bind(commandCenter.skipForwardCommand, commands::fastForward)
        bind(commandCenter.skipBackwardCommand, commands::rewind)
        bind(commandCenter.togglePlayPauseCommand) {
            if (state == MediaPlaybackState.PLAYING) commands.pause() else commands.play()
        }
    }

    private fun bind(command: MPRemoteCommand, action: () -> Unit) {
        val target = command.addTargetWithHandler {
            action()
            MPRemoteCommandHandlerStatusSuccess
        }
        targets += command to target
        command.enabled = true
    }

    private val rate: Double get() = if (state == MediaPlaybackState.PLAYING) 1.0 else 0.0

    override fun setPlaybackState(state: MediaPlaybackState) {
        this.state = state
        if (state == MediaPlaybackState.STOPPED) {
            infoCenter.nowPlayingInfo = null
        } else {
            infoCenter.nowPlayingInfo = (infoCenter.nowPlayingInfo ?: emptyMap<Any?, Any>()) +
                (MPNowPlayingInfoPropertyPlaybackRate to rate)
        }
    }

    /** U7 hides content, not the installed app name (ADR 0007); reevaluate mode on every publication. */
    override fun setNowPlaying(title: String, album: String) {
        val visible = metadata.forContent(title, album)
        // Replace the whole payload: no artwork or other content from an earlier publication survives.
        infoCenter.nowPlayingInfo = mapOf(
            MPMediaItemPropertyTitle to visible.title,
            MPMediaItemPropertyAlbumTitle to visible.album,
            MPNowPlayingInfoPropertyPlaybackRate to rate,
        )
    }

    private fun removeTargets() {
        targets.forEach { (command, target) ->
            command.removeTarget(target)
            command.enabled = false
        }
        targets.clear()
    }

    override fun release() {
        removeTargets()
        state = MediaPlaybackState.STOPPED
        infoCenter.nowPlayingInfo = null
    }
}
