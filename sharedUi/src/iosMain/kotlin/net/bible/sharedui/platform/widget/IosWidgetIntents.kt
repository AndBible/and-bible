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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.host.app.HostReadingReady
import net.bible.sharedui.platform.widget.IosWidgetUrls.run
import org.koin.mp.KoinPlatform

/**
 * Widget buttons that run without opening the app (iOS 17+ App Intents, `AudioPlaybackIntent`): the system starts the
 * app process in the background and the Swift intent calls [run] with the same `andbible-widget://speak/...` or
 * `bookmark/...` URL a link would carry. Also starts the snapshot writer once reading is ready ([startWriter]).
 *
 * The headless bring-up is the one the spike proved (`2026-11-20-ios-widget-intent-spike.md`): Koin, databases and the
 * window repository, off the UI. It needs an installed Bible; without one it logs and returns false (the widget shows
 * "Open AndBible" in that case, so this is only a race).
 */
object IosWidgetIntents {
    private const val TAG = "IosWidgetIntents"

    /** Runs the speak action [url] names, bringing the app up headless first when needed. True when it ran. */
    suspend fun run(url: String): Boolean {
        val action = WidgetUrl.parse(url)
        if (action !is WidgetUrlAction.Speak && action !is WidgetUrlAction.SpeakBookmark && action !is WidgetUrlAction.SpeakPassage) {
            Log.w(TAG, "not a speak url: $url")
            return false
        }
        try {
            bringUp()
            val transport = KoinPlatform.getKoin().get<SpeakTransportService>()
            // The speak engine is main-thread bound.
            withContext(Dispatchers.Main) {
                when (action) {
                    is WidgetUrlAction.Speak -> transport.run(action.command)
                    is WidgetUrlAction.SpeakBookmark -> transport.speakFromBookmark(action.id)
                    is WidgetUrlAction.SpeakPassage -> IosWidgetUrls.speakPassage(action.passage)
                    else -> Unit
                }
            }
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "widget intent failed: $url", e)
            return false
        }
    }

    private suspend fun bringUp() {
        if (HostReadingReady.isReady) return
        IosAppStartup.ensureStarted()
        IosAppStartup.openDatabases()
        HostReadingReady.ensure(HostStartup.stringTable)
    }

    /**
     * Starts the snapshot writer once reading is ready (databases open, window repository loaded, so a default Bible
     * exists). Suspends until then; with no Bible installed that is until one is. Call from a coroutine off Main.
     */
    suspend fun startWriter() {
        HostReadingReady.readyFlow.first { it }
        val writer = KoinPlatform.getKoinOrNull()?.getOrNull<IosWidgetSnapshotWriter>() ?: return
        writer.start()
        writer.writeNow()
    }
}
