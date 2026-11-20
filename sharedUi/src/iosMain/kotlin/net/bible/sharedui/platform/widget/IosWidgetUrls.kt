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

import net.bible.android.control.page.window.WindowControl
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.speak.SpeakTransportService
import org.koin.mp.KoinPlatform
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Executes the `andbible-widget://` URLs a widget tap sends to the app (Swift's `onOpenURL`). A tap on a cold launch
 * arrives before Koin and the databases exist, so the action waits in [pending] until [flush] runs once startup is
 * Ready; an action that arrives later runs at once. "Ready" is the [ready] flag, not "Koin exists": a headless intent
 * leaves the process with Koin up and no UI, and the databases and window repository are not loaded then. Main thread
 * only (the entry hops to it).
 */
object IosWidgetUrls {
    private const val TAG = "IosWidgetUrls"
    private val queue = WidgetUrlQueue(::execute)

    /** Swift's entry. Invalid URLs are logged and dropped. */
    fun open(url: String) {
        val run = {
            val action = WidgetUrl.parse(url)
            if (action == WidgetUrlAction.Invalid) Log.w(TAG, "ignored widget url: $url")
            else queue.submit(action)
        }
        if (NSThread.isMainThread) run() else dispatch_async(dispatch_get_main_queue()) { run() }
    }

    /** Runs the actions that arrived before startup finished, and all later ones at once. Call on Main when startup is Ready. */
    fun flush() = queue.markReady()

    private fun execute(action: WidgetUrlAction) {
        val koin = KoinPlatform.getKoinOrNull() ?: return
        runCatching {
            when (action) {
                is WidgetUrlAction.Speak -> koin.get<SpeakTransportService>().run(action.command)
                is WidgetUrlAction.SpeakBookmark -> koin.get<SpeakTransportService>().speakFromBookmark(action.id)
                is WidgetUrlAction.OpenPassage -> {
                    // The active window, like Android's openDeepLink; LinkControl would use the links window.
                    val opened = WidgetPassage.open(action) { doc, key ->
                        koin.get<WindowControl>().activeWindowPageManager.setCurrentDocumentAndKey(doc, key)
                    }
                    if (!opened) Log.w(TAG, "widget passage not found: $action")
                }
                WidgetUrlAction.Invalid -> Unit
            }
        }.onFailure { Log.e(TAG, "widget url failed: $action", it) }
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
