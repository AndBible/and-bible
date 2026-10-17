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

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.platform.AppCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.sharedcore.speak.SpeakBookmarkRowVd
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.speak.SpeakTransportStateVd
import kotlin.concurrent.Volatile

/**
 * Classic label formatting from `SpeakTransportWidget.onBookmarkButtonClick` (:190-197). `internal`
 * so the test can call it directly without going through a running [SpeakTransportServiceImpl]
 * instance, mirroring `mapEntry` in `AgentSessionServiceImpl`.
 */
@VisibleForTesting
fun labelOf(b: BookmarkEntities.BaseBookmarkWithNotes): String = when (b) {
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
class SpeakTransportServiceImpl(
    private val scope: AppCoroutineScope,
    private val platform: SpeakPlatform,
    private val advancedSpeakSettings: AdvancedSpeakSettings,
    private val speakControl: SpeakControl,
    private val bookmarkControl: BookmarkControl,
) : SpeakTransportService {

    /** Cached id → bookmark for [speakFromBookmark]; refreshed by [speakBookmarks]. */
    private var bookmarkCache: Map<String, BookmarkEntities.BaseBookmarkWithNotes> = emptyMap()

    /**
     * Cached bookmark-button visibility. Classic `SpeakTransportWidget` only recomputes this on
     * attach + [SpeakSettingsChanges] (`resetView`, :216-227) — never on the plain
     * [SpeakChanges] handlers that fire on every verse transition. Mirror that:
     * recompute only here (init) and in the [SpeakSettingsChanges] handler below, not in [build].
     */
    private var bookmarkVisible: Boolean = false

    private val _state = MutableStateFlow(build(visible = !speakControl.isStopped))
    override val state: StateFlow<SpeakTransportStateVd> = _state.asStateFlow()

    init {
        instance = this
        refreshBookmarkVisibility()
        // Process lifetime (Koin single): never cancelled.
        SpeakChanges.changes.subscribeOnMain { refresh() }
        SpeakSettingsChanges.changes.subscribeOnMain {
            refreshBookmarkVisibility()
        }
    }

    private fun refreshBookmarkVisibility() {
        scope.launch {
            bookmarkVisible = rawSpeakBookmarks().isNotEmpty()
            refresh()
        }
    }

    /** The reading host calls [setTransportVisible] when its `transportBarVisible` changes. */
    fun setTransportVisible(visible: Boolean) {
        _state.value = build(visible = visible)
    }

    /** Rebuilds the cached state (status text is locale-dependent) after an in-app language change. */
    fun refreshForLocale() { refresh() }

    private fun refresh() { _state.update { build(visible = it.visible) } }

    private fun build(visible: Boolean) = SpeakTransportStateVd(
        visible = visible,
        speaking = speakControl.isSpeaking,
        paused = speakControl.isPaused,
        stopped = speakControl.isStopped,
        statusText = speakControl.getStatusText(FLAG_SHOW_ALL),
        bookmarkButtonVisible = bookmarkVisible,
    )

    // Classic SpeakTransportWidget.speakBookmarks (:179-184)
    private suspend fun rawSpeakBookmarks(): List<BookmarkEntities.BaseBookmarkWithNotes> {
        val label = bookmarkControl.speakLabel()
        val bible = bookmarkControl.getBibleBookmarksWithLabel(label)
            .sortedWith { a, b -> a.verseRange.start.compareTo(b.verseRange.start) }
        val generic = bookmarkControl.getGenericBookmarksWithLabel(label)
        return bible + generic
    }

    override suspend fun speakBookmarks(): List<SpeakBookmarkRowVd> {
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
        val wasStopped = speakControl.isStopped
        speakControl.stop()
        if (wasStopped) _state.value = _state.value.copy(visible = false)
    }

    override fun rewind() = speakControl.rewind()
    override fun forward() = speakControl.forward()
    override fun prevVerse() = speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
    override fun nextVerse() = speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)

    /** Classic sync side-effect (SpeakTransportWidget :163-167, :206-210). */
    private fun maybeSyncToFront() {
        if (advancedSpeakSettings.synchronize) {
            platform.bringReadingToFront()
        }
    }

    companion object {
        /** The live process-wide service, set on construction (never instantiated from outside Koin); null before first use. */
        @Volatile var instance: SpeakTransportServiceImpl? = null
    }
}
