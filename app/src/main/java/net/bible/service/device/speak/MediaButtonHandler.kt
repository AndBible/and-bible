/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.device.speak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media.session.MediaButtonReceiver
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.platform.AndroidMediaSession
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.speak.MediaCommands
import net.bible.sharedcore.platform.speak.MediaPlaybackState
import net.bible.sharedcore.platform.speak.MediaSession

class AndBibleMediaButtonReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        CommonUtils.initializeApp()
        Log.i("MediaButtons", "MediaButtonReceiver onReceive")
        AndroidMediaSession.current?.let { MediaButtonReceiver.handleIntent(it.ms, intent) }
    }
}

/** Today's media-button to speech mapping, separated from the platform media session. */
internal class SpeakMediaCommands(
    private val pause: () -> Unit,
    private val continueLastPosition: () -> Unit,
    private val stop: () -> Unit,
    private val forward: (SpeakSettings.RewindAmount) -> Unit,
    private val rewind: (SpeakSettings.RewindAmount) -> Unit,
    private val buttonsEnabled: () -> Boolean,
) : MediaCommands {
    override fun play() { if (buttonsEnabled()) continueLastPosition() }
    override fun pause() = pause.invoke()
    override fun stop() = stop.invoke()
    override fun next() = forward(SpeakSettings.RewindAmount.SMART)
    override fun previous() = rewind(SpeakSettings.RewindAmount.SMART)
    override fun fastForward() = forward(SpeakSettings.RewindAmount.ONE_VERSE)
    override fun rewind() = rewind(SpeakSettings.RewindAmount.ONE_VERSE)
}

/** Null means this change must leave the media session's current state intact. */
internal fun mediaStateFor(change: SpeakChange.State): MediaPlaybackState? = when {
    change.isPaused -> MediaPlaybackState.PAUSED
    change.isStopped -> MediaPlaybackState.STOPPED
    change.isSpeaking -> MediaPlaybackState.PLAYING
    else -> null
}

class MediaButtonHandler(speakControl: SpeakControl, private val session: MediaSession) {
    companion object {
        const val TAG = "MediaButtons"
        var handler: MediaButtonHandler? = null
        fun initialize(speakControl: SpeakControl, session: MediaSession) {
            if (!application.isRunningTests && CommonUtils.settings.getBoolean("enable_bluetooth_pref", true)) {
                handler = MediaButtonHandler(speakControl, session)
            }
        }
        fun release() {
            handler?.release()
            handler = null
        }
    }

    private val subscriptions = Subscriptions()

    init {
        session.activate(SpeakMediaCommands(
            speakControl::pause, speakControl::continueLastPosition, speakControl::stop,
            speakControl::forward, speakControl::rewind,
        ) { CommonUtils.settings.getBoolean("enable_bluetooth_pref", true) })
        subscriptions.add(SpeakChanges.changes.subscribeOnMain { change ->
            when (change) {
                SpeakChange.Status -> Unit
                is SpeakChange.State -> {
                    Log.i(TAG, "playback state ${change.speakState}")
                    mediaStateFor(change)?.let(session::setPlaybackState)
                }
                is SpeakChange.Progress -> session.setNowPlaying(change.key.getName(), change.book.name)
            }
        })
    }

    fun release() {
        subscriptions.cancelAll()
        session.release()
    }
}
