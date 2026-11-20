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
package net.bible.android.view.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.common.CommonUtils
import org.koin.mp.KoinPlatform

/** What the widget buttons do; a seam so tests can record the calls without a speech engine. */
interface WidgetSpeakCommands {
    /** Play or pause, resuming where Speak last stopped. */
    fun togglePlayPause()
    fun prevVerse()
    fun nextVerse()
    fun rewind()
    fun forward()
    fun stop()

    /** Switches the sleep timer off, or back on with the last used duration. */
    fun toggleSleepTimer()
    fun speakFromBookmark(id: String)
}

/** The real commands: the app's [SpeakControl], same calls as the earlier widgets' broadcast receivers made. */
internal class ControlWidgetSpeakCommands : WidgetSpeakCommands {
    private val speak: SpeakControl get() = KoinPlatform.getKoin().get()

    override fun togglePlayPause() = speak.toggleSpeak(preferLast = true)
    override fun prevVerse() = speak.rewind(SpeakSettings.RewindAmount.ONE_VERSE, toast = false)
    override fun nextVerse() = speak.forward(SpeakSettings.RewindAmount.ONE_VERSE, toast = false)
    override fun rewind() = speak.rewind()
    override fun forward() = speak.forward()
    override fun stop() = speak.stop()
    override fun toggleSleepTimer() {
        val settings = SpeakSettings.load()
        settings.sleepTimer = if (settings.sleepTimer > 0) 0 else settings.lastSleepTimer
        settings.save()
    }
    override fun speakFromBookmark(id: String) = error("resolved by SpeakFromBookmarkAction")
}

/** The commands the actions use; tests replace [override]. */
object WidgetCommands {
    @Volatile internal var override: WidgetSpeakCommands? = null
    internal val current: WidgetSpeakCommands get() = override ?: ControlWidgetSpeakCommands()
}

/**
 * Runs a widget button's [block] on the main thread (as the legacy broadcast receivers did), but only when the app
 * could be opened: a damaged-data widget (#4112) must not touch the databases, and its buttons are not drawn anyway.
 */
private suspend fun runWhenAppOpens(block: suspend () -> Unit) {
    val ok = withContext(Dispatchers.IO) { CommonUtils.initializeAppForHeadlessEntry() }
    if (!ok) return
    withContext(Dispatchers.Main.immediate) { block() }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runWhenAppOpens { WidgetCommands.current.togglePlayPause() }
}

class PrevVerseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runWhenAppOpens { WidgetCommands.current.prevVerse() }
}

class NextVerseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runWhenAppOpens { WidgetCommands.current.nextVerse() }
}

class RewindAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runWhenAppOpens { WidgetCommands.current.rewind() }
}

class ForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runWhenAppOpens { WidgetCommands.current.forward() }
}

class StopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runWhenAppOpens { WidgetCommands.current.stop() }
}

class ToggleSleepTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runWhenAppOpens { WidgetCommands.current.toggleSleepTimer() }
}

/** Speaks from the speak bookmark whose id is [BOOKMARK_ID]. */
class SpeakFromBookmarkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[BOOKMARK_ID] ?: return
        val ok = withContext(Dispatchers.IO) { CommonUtils.initializeAppForHeadlessEntry() }
        if (!ok) return
        val override = WidgetCommands.override
        if (override != null) { override.speakFromBookmark(id); return }
        val bookmarks: BookmarkControl = KoinPlatform.getKoin().get()
        val dto = bookmarks.bibleBookmarksByIds(listOf(IdType(id))).firstOrNull() ?: bookmarks.genericBookmarkById(IdType(id)) ?: return
        withContext(Dispatchers.Main.immediate) { KoinPlatform.getKoin().get<SpeakControl>().speakFromBookmark(dto) }
    }

    companion object {
        val BOOKMARK_ID = ActionParameters.Key<String>("bookmarkId")
    }
}
