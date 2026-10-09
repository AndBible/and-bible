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
package net.bible.android.control.speak

import android.content.Intent
import net.bible.service.db.blockingDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.BibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.common.AdvancedSpeakSettings
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.sharedcore.speak.SpeakBookmarkRowVd
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.speak.SpeakTransportStateVd
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Classic label formatting from `SpeakTransportWidget.onBookmarkButtonClick` (:190-197). `internal`
 * so the test can call it directly without going through a running [SpeakTransportServiceImpl]
 * instance, mirroring `mapEntry` in `AgentSessionServiceImpl`.
 */
internal fun labelOf(b: BookmarkEntities.BaseBookmarkWithNotes): String = when (b) {
    is BibleBookmarkWithNotes -> "${b.verseRange.start.getName()} (${b.playbackSettings?.bookId ?: "?"})"
    is BookmarkEntities.GenericBookmarkWithNotes -> "${b.book?.abbreviation} - ${b.bookKey?.getName()}"
    else -> throw RuntimeException("Illegal bookmark type")
}

/**
 * Android impl of [SpeakTransportService]. Bridges the events the classic `SpeakTransportWidget`
 * listens to, now [SpeakChanges] and [SpeakSettingsChanges], plus the visibility SSOT set from
 * [NavHostComposeActivity] into a [StateFlow], and reproduces the widget's button dispatch
 * (`onButtonClick` :143-177, `onBookmarkButtonClick` :186-214). Koin single (process-lived); the
 * reading-view classic code is unchanged.
 */
class SpeakTransportServiceImpl : SpeakTransportService, KoinComponent {
    private val speakControl: SpeakControl by inject()
    private val bookmarkControl: BookmarkControl by inject()

    /** Cached id → bookmark for [speakFromBookmark]; refreshed by [speakBookmarks]. */
    private var bookmarkCache: Map<String, BookmarkEntities.BaseBookmarkWithNotes> = emptyMap()

    /**
     * Cached bookmark-button visibility. Classic `SpeakTransportWidget` only recomputes this on
     * attach + [SpeakSettingsChanges] (`resetView`, :216-227) — never on the plain
     * [SpeakChanges] handlers that fire on every verse transition. Mirror that:
     * recompute only here (init) and in the [SpeakSettingsChanges] handler below, not in [build].
     */
    private var bookmarkVisible: Boolean = rawSpeakBookmarks().isNotEmpty()

    private val _state = MutableStateFlow(build(visible = !speakControl.isStopped))
    override val state: StateFlow<SpeakTransportStateVd> = _state.asStateFlow()

    init {
        // Process lifetime (Koin single): never cancelled.
        SpeakChanges.changes.subscribeOnMain { refresh() }
        SpeakSettingsChanges.changes.subscribeOnMain {
            bookmarkVisible = rawSpeakBookmarks().isNotEmpty()
            refresh()
        }
    }

    /** The reading host calls [setTransportVisible] when its `transportBarVisible` changes. */
    fun setTransportVisible(visible: Boolean) {
        _state.value = build(visible = visible)
    }

    private fun refresh() { _state.value = build(visible = _state.value.visible) }

    private fun build(visible: Boolean) = SpeakTransportStateVd(
        visible = visible,
        speaking = speakControl.isSpeaking,
        paused = speakControl.isPaused,
        stopped = speakControl.isStopped,
        statusText = speakControl.getStatusText(FLAG_SHOW_ALL),
        bookmarkButtonVisible = bookmarkVisible,
    )

    // Classic SpeakTransportWidget.speakBookmarks (:179-184)
    private fun rawSpeakBookmarks(): List<BookmarkEntities.BaseBookmarkWithNotes> {
        val label = blockingDb { bookmarkControl.speakLabel() } // L1-pending(speak)
        val bible = blockingDb { bookmarkControl.getBibleBookmarksWithLabel(label) } // L1-pending(speak)
            .sortedWith { a, b -> a.verseRange.start.compareTo(b.verseRange.start) }
        val generic = blockingDb { bookmarkControl.getGenericBookmarksWithLabel(label) } // L1-pending(speak)
        return bible + generic
    }

    override fun speakBookmarks(): List<SpeakBookmarkRowVd> {
        val list = rawSpeakBookmarks()
        bookmarkCache = list.associateBy { it.id.toString() }
        return list.map { SpeakBookmarkRowVd(it.id.toString(), labelOf(it)) }
    }

    override fun speakFromBookmark(id: String) {
        val dto = bookmarkCache[id] ?: return
        speakControl.speakFromBookmark(dto)
        maybeSyncToFront()
    }

    override fun speakAny() {
        speakControl.speakAny()
        maybeSyncToFront()
    }

    override fun pause() = speakControl.pause()
    override fun continueAfterPause() = speakControl.continueAfterPause()

    override fun stop() {
        if (speakControl.isStopped) _state.value = _state.value.copy(visible = false)
        else speakControl.stop()
    }

    override fun rewind() = speakControl.rewind()
    override fun forward() = speakControl.forward()
    override fun prevVerse() = speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
    override fun nextVerse() = speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)

    /** Classic sync side-effect (SpeakTransportWidget :163-167, :206-210). */
    private fun maybeSyncToFront() {
        if (AdvancedSpeakSettings.synchronize) {
            val ctx = BibleApplication.application
            // reading-host re-typing T8b: same three flags, the reading host instead of the
            // classic Activity.
            ctx.startActivity(NavHostComposeActivity.intentFor(ctx, NavRoutes.READING).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
