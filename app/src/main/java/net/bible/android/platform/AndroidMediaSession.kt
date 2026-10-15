package net.bible.android.platform

import android.content.Context
import android.media.MediaPlayer
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import net.bible.android.activity.R
import net.bible.android.view.activity.base.AppPosition
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.speak.MediaCommands
import net.bible.sharedcore.platform.speak.MediaPlaybackState
import net.bible.sharedcore.platform.speak.MediaSession

/** Android media-session ownership, including the silent sound needed for media-button listening. */
class AndroidMediaSession(
    private val context: Context,
    private val isSpeaking: () -> Boolean,
) : MediaSession {
    companion object {
        private const val TAG = "MediaButtons"
        var current: AndroidMediaSession? = null
            private set
    }

    private val subscriptions = Subscriptions()
    private val state = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_FAST_FORWARD
                or PlaybackStateCompat.ACTION_REWIND
        )
        .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f)
        .build()
    private val nothingPlaying = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "")
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
        .build()

    // Created only on activation: initialize's test/preference guard must not allocate a system session.
    lateinit var ms: MediaSessionCompat
        private set

    override fun activate(commands: MediaCommands) {
        ms = MediaSessionCompat(context, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPause() { Log.i(TAG, "onPause"); commands.pause() }
                override fun onPlay() { Log.i(TAG, "onPlay"); commands.play() }
                override fun onStop() { Log.i(TAG, "onStop"); commands.stop() }
                override fun onSkipToNext() { Log.i(TAG, "onSkipToNext"); commands.next() }
                override fun onSkipToPrevious() { Log.i(TAG, "onSkipToPrevious"); commands.previous() }
                override fun onFastForward() { Log.i(TAG, "onFastForward"); commands.fastForward() }
                override fun onRewind() { Log.i(TAG, "onFastForward"); commands.rewind() }
            })
            setPlaybackState(state)
            isActive = true
            setMetadata(nothingPlaying)
        }
        current = this
        // Temporary stops intentionally publish no media state, but still need the Android workaround.
        subscriptions.add(net.bible.android.control.speak.SpeakChanges.changes.subscribeOnMain { change ->
            if (change is net.bible.android.control.speak.SpeakChange.State && change.isTemporarilyStopped) {
                makeTriggerSound()
            }
        })
        subscriptions.add(CurrentActivityHolder.appPositionChanges.subscribe { position ->
            if (position == AppPosition.FOREGROUND && isSpeaking()) {
                makeTriggerSound()
            }
        })
    }

    override fun setPlaybackState(state: MediaPlaybackState) {
        val androidState = when (state) {
            MediaPlaybackState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            MediaPlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            MediaPlaybackState.STOPPED -> PlaybackStateCompat.STATE_STOPPED
        }
        ms.setPlaybackState(PlaybackStateCompat.Builder(this.state).setState(androidState, 0, 1f).build())
        makeTriggerSound()
    }

    override fun setNowPlaying(title: String, album: String) {
        ms.setMetadata(MediaMetadataCompat.Builder(nothingPlaying)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .build())
    }

    private fun makeTriggerSound() {
        // Hack to make media button listening work!
        // https://stackoverflow.com/questions/45960265/android-o-oreo-8-and-higher-media-buttons-issue
        if (!CommonUtils.settings.getBoolean("enable_bluetooth_pref", true)) return
        MediaPlayer.create(context, R.raw.silence)?.run {
            setOnCompletionListener { release() }
            start()
        }
    }

    override fun release() {
        subscriptions.cancelAll()
        ms.run {
            isActive = false
            release()
        }
        if (current === this) current = null
    }
}
