/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedui.platform.widget

import net.bible.android.control.link.BibleLink
import net.bible.android.control.link.LinkControl
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.speak.SpeakTransportService
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Executes the `andbible-widget://` URLs a widget tap sends to the app (Swift's `onOpenURL`). A tap on a cold launch
 * arrives before Koin and the databases exist, so the action waits in [pending] until [flush] runs once startup is
 * Ready; an action that arrives later runs at once. Main thread only (the entry hops to it).
 */
object IosWidgetUrls {
    private const val TAG = "IosWidgetUrls"
    private val pending = ArrayDeque<WidgetUrlAction>()

    /** Swift's entry. Invalid URLs are logged and dropped. */
    fun open(url: String) {
        val run = {
            val action = WidgetUrl.parse(url)
            if (action == WidgetUrlAction.Invalid) Log.w(TAG, "ignored widget url: $url")
            else if (!tryRun(action)) pending.addLast(action)
        }
        if (NSThread.isMainThread) run() else dispatch_async(dispatch_get_main_queue()) { run() }
    }

    /** Runs the actions that arrived before startup finished. Call on Main when startup is Ready. */
    fun flush() {
        while (pending.isNotEmpty()) {
            if (!tryRun(pending.first())) return
            pending.removeFirst()
        }
    }

    /** False when Koin is not up yet (the action must wait); true when it ran or failed for good. */
    private fun tryRun(action: WidgetUrlAction): Boolean {
        val koin = KoinPlatform.getKoinOrNull() ?: return false
        runCatching {
            when (action) {
                is WidgetUrlAction.Speak -> koin.get<SpeakTransportService>().run(action.command)
                is WidgetUrlAction.SpeakBookmark -> koin.get<SpeakTransportService>().speakFromBookmark(action.id)
                is WidgetUrlAction.OpenPassage -> {
                    val book = action.document?.let { Books.installed().getBook(it) }
                    koin.get<LinkControl>().loadApplicationUrl(
                        BibleLink("content", action.key, action.v11n, forceDoc = book != null), book,
                    )
                }
                WidgetUrlAction.Invalid -> Unit
            }
        }.onFailure { Log.e(TAG, "widget url failed: $action", it) }
        return true
    }

    private fun SpeakTransportService.run(command: SpeakCommand) {
        val s = state.value
        when (command) {
            SpeakCommand.TOGGLE -> if (s.speaking) pause() else if (s.paused) continueAfterPause() else speakAny()
            SpeakCommand.PLAY -> if (s.paused) continueAfterPause() else if (!s.speaking) speakAny()
            SpeakCommand.PAUSE -> if (s.speaking) pause()
            SpeakCommand.STOP -> stop()
            SpeakCommand.PREV_VERSE -> prevVerse()
            SpeakCommand.NEXT_VERSE -> nextVerse()
            SpeakCommand.REWIND -> rewind()
            SpeakCommand.FORWARD -> forward()
        }
    }
}
