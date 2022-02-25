/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.device.speak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.session.MediaButtonReceiver
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent

class AndBibleMediaButtonReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        CommonUtils.initializeApp()
        Log.i("MediaButtons", "MediaButtonReceiver onReceive")
        val handler = MediaButtonHandler.handler
        if(handler != null) {
            MediaButtonReceiver.handleIntent(handler.ms, intent)
        }
    }
}


class MediaButtonHandler(val speakControl: SpeakControl) {
    companion object {
        const val TAG = "MediaButtons"
        var handler: MediaButtonHandler? = null
        fun initialize(speakControl: SpeakControl) {
            if(!application.isRunningTests && CommonUtils.booleanSettings.get("enable_bluetooth_pref", true)) {
                handler = MediaButtonHandler(speakControl)
            }
        }
        fun release() {
            handler?.release()
            handler = null
        }
    }

    private val state: PlaybackStateCompat = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_FAST_FORWARD
                or PlaybackStateCompat.ACTION_REWIND
        )
        .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f)
        .build()

    private val nothingPlaying: MediaMetadataCompat = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "")
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
        .build()

    val cb = object : MediaSessionCompat.Callback() {
        override fun onPause() {
            Log.i(TAG, "onPause")
            speakControl.pause()
        }

        override fun onPlay() {
            Log.i(TAG, "onPlay")
            speakControl.toggleSpeak()
        }

        override fun onStop() {
            Log.i(TAG, "onStop")
            speakControl.stop()
        }

        override fun onSkipToNext() {
            Log.i(TAG, "onSkipToNext")
            speakControl.forward(SpeakSettings.RewindAmount.SMART)
        }

        override fun onSkipToPrevious() {
            Log.i(TAG, "onSkipToPrevious")
            speakControl.rewind(SpeakSettings.RewindAmount.SMART)
        }

        override fun onFastForward() {
            Log.i(TAG, "onFastForward")
            speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
        }

        override fun onRewind() {
            Log.i(TAG, "onFastForward")
            speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
        }
    }
    var ms = MediaSessionCompat(application, TAG).apply {
        setCallback(cb)
        setPlaybackState(state)
        isActive = true
        setMetadata(nothingPlaying)
    }

    init {
        ABEventBus.getDefault().register(this)
    }

    private fun makeTriggerSound() {
        // Hack to make media button listening work!
        // https://stackoverflow.com/questions/45960265/android-o-oreo-8-and-higher-media-buttons-issue
        MediaPlayer.create(application, R.raw.silence)?.run {
            setOnCompletionListener { release() }
            start()
        }
    }

    fun setState(s: Int) {
        return ms.setPlaybackState(PlaybackStateCompat.Builder(state).setState(s, 0, 1f).build())
    }

    fun onEventMainThread(event: SpeakEvent) {
        Log.i(TAG, "playback state ${event.speakState}")
        when {
            event.isPaused -> setState(PlaybackStateCompat.STATE_PAUSED)
            event.isStopped -> setState(PlaybackStateCompat.STATE_STOPPED)
            event.isSpeaking -> setState(PlaybackStateCompat.STATE_PLAYING)
        }
        makeTriggerSound()
    }

    fun onEvent(event: AppToBackgroundEvent) {
        if(event.newPosition == AppToBackgroundEvent.Position.FOREGROUND && speakControl.isSpeaking) {
            makeTriggerSound()
        }
    }

    fun onEventMainThread(ev: SpeakProgressEvent) {
        ms.setMetadata(
            MediaMetadataCompat.Builder(nothingPlaying)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, ev.book.name)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, ev.key.name)
                .build()
        )
    }

    fun release() {
        ABEventBus.getDefault().unregister(this)
        ms.run {
            isActive = false
            release()
        }
    }
}
