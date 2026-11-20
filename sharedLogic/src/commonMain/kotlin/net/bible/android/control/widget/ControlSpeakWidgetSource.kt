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
package net.bible.android.control.widget

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettingsChanges
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.device.speak.TextCommand
import net.bible.service.format.SaxTextPolicy
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.util.currentTimeMillis
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

/** Whether a bookmark change can alter the listening list. */
internal fun redrawsListeningList(change: BookmarkChange): Boolean = when (change) {
    is BookmarkChange.BookmarksUpserted,
    is BookmarkChange.BookmarksDeleted,
    is BookmarkChange.LabelUpserted,
    is BookmarkChange.LabelsDeleted,
    is BookmarkChange.NoteModified -> true
    is BookmarkChange.BookmarkToLabelUpserted,
    is BookmarkChange.StudyPadOrder,
    is BookmarkChange.StudyPadTextEntryDeleted -> false
}

/**
 * [SpeakWidgetSource] over [SpeakControl], the speak settings and the speak-label bookmarks. Subscribes to the
 * speak, settings and bookmark event streams for its whole life (a Koin single); [close] cancels them.
 */
class ControlSpeakWidgetSource(
    private val isSpeaking: () -> Boolean,
    private val isPaused: () -> Boolean,
    private val sleepTimerActivationTime: () -> Long?,
    private val bookmarkControl: BookmarkControl,
    private val advancedSpeakSettings: AdvancedSpeakSettings,
    private val bibleTraverser: BibleTraverser,
    private val settings: AppSettings,
    private val coreStrings: CoreStrings,
    private val saxTextPolicy: SaxTextPolicy,
) : SpeakWidgetSource {
    /** Reads the speak state from [speakControl] (a final class, hence the function parameters above). */
    constructor(
        speakControl: SpeakControl,
        bookmarkControl: BookmarkControl,
        advancedSpeakSettings: AdvancedSpeakSettings,
        bibleTraverser: BibleTraverser,
        settings: AppSettings,
        coreStrings: CoreStrings,
        saxTextPolicy: SaxTextPolicy,
    ) : this(
        { speakControl.isSpeaking }, { speakControl.isPaused }, { speakControl.sleepTimerActivationTime },
        bookmarkControl, advancedSpeakSettings, bibleTraverser, settings, coreStrings, saxTextPolicy,
    )

    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    override val changes: Flow<Unit> = events.asSharedFlow()

    private val subscriptions = Subscriptions()
    /** Written by the event handlers (any thread), read by [facts]; replaced whole so a read is consistent. */
    private val tracked = atomic(Tracked())

    init {
        subscriptions.add(SpeakChanges.changes.subscribe { change ->
            tracked.update { t ->
                when (change) {
                    is SpeakChange.Progress -> {
                        val command = change.speakCommand
                        var next = t.copy(book = change.book, key = change.key)
                        if (command is TextCommand) {
                            next = if (command.type == TextCommand.TextType.TITLE) next.copy(title = command.text.ifEmpty { null })
                            else next.copy(verseText = command.text)
                        }
                        next
                    }
                    is SpeakChange.State -> when {
                        change.isSpeaking -> t.copy(title = null)
                        !change.isPaused -> t.copy(title = null, verseText = "")
                        else -> t
                    }
                    SpeakChange.Status -> t
                }
            }
            events.tryEmit(Unit)
        })
        subscriptions.add(SpeakSettingsChanges.changes.subscribe { events.tryEmit(Unit) })
        subscriptions.add(bookmarkControl.changes.subscribe { if (redrawsListeningList(it)) events.tryEmit(Unit) })
    }

    fun close() = subscriptions.cancelAll()

    /** Forgets the title, verse text and position of an earlier run; a producer start must not show them. */
    override fun reset() = tracked.update { Tracked() }

    override fun sleepActivationTimeMs(): Long? = sleepTimerActivationTime()
    override fun nowMs(): Long = currentTimeMillis()

    private fun versesOf(key: Key?): Verse? = when (key) {
        is Verse -> key
        is VerseRange -> key.start
        // A book's getKey(osisRef) parses to a passage, which is what the saved resume position comes back as.
        is Passage -> if (key.countVerses() == 0) null else key.getRangeAt(0, RestrictionType.NONE).start
        else -> null
    }

    override suspend fun facts(): SpeakFacts {
        val phase = when {
            isSpeaking() -> SpeakPhase.SPEAKING
            isPaused() -> SpeakPhase.PAUSED
            else -> SpeakPhase.STOPPED
        }
        val (titleNow, textNow, book, key) = tracked.value
        var shownBook: Book? = null
        var shownVerse: Verse? = null
        if (phase != SpeakPhase.STOPPED) {
            shownBook = book
            shownVerse = versesOf(key)
        } else {
            // What toggleSpeak(preferLast = true) resumes: the position SpeakControl saved on stop.
            val bookRef = settings.getString("lastSpeakBook")
            val osisRef = settings.getString("lastSpeakRef")
            if (bookRef != null && osisRef != null) {
                shownBook = Books.installed().getBook(bookRef)
                shownVerse = try {
                    versesOf((shownBook as? SwordBook)?.getKey(osisRef))
                } catch (e: Exception) {
                    null
                }
            }
        }
        val percent = shownVerse?.let { runCatching { bibleTraverser.getPercentOfBook(it) }.getOrNull() }
        val next = shownVerse?.let { v ->
            if (phase == SpeakPhase.STOPPED || shownBook == null) null
            else runCatching {
                v.versification.next(v)?.let {
                    SwordContentFacade.getCanonicalText(shownBook, it, true, strings = coreStrings, saxTextPolicy = saxTextPolicy).trim().ifEmpty { null }
                }
            }.getOrNull()
        }

        val speakBookmarks = bookmarkControl.speakLabel().let { label ->
            bookmarkControl.getBibleBookmarksWithLabel(label)
                .sortedWith { a, b -> a.verseRange.start.compareTo(b.verseRange.start) } +
                bookmarkControl.getGenericBookmarksWithLabel(label)
        }
        var currentId: String? = null
        val rows = speakBookmarks.map { b ->
            when (b) {
                is BookmarkEntities.BibleBookmarkWithNotes -> {
                    val initials = b.playbackSettings?.bookId
                    if (currentId == null && phase != SpeakPhase.STOPPED && shownVerse != null && initials == shownBook?.initials &&
                        b.verseRange.start.ordinal <= shownVerse.ordinal && shownVerse.ordinal <= b.verseRange.end.ordinal
                    ) currentId = b.id.toString()
                    ListeningRow(
                        bookmarkId = b.id.toString(),
                        reference = b.verseRange.start.getName(),
                        translation = initials ?: "?",
                        repeats = b.playbackSettings?.verseRange != null,
                        percent = runCatching { bibleTraverser.getPercentOfBook(b.verseRange.start) }.getOrNull(),
                        current = false,
                    )
                }
                is BookmarkEntities.GenericBookmarkWithNotes -> ListeningRow(
                    bookmarkId = b.id.toString(),
                    reference = b.bookKey?.getName() ?: "",
                    translation = b.book?.abbreviation ?: "",
                    repeats = b.playbackSettings?.verseRange != null,
                    percent = null,
                    current = false,
                )
                else -> throw RuntimeException("Illegal bookmark type")
            }
        }
        return SpeakFacts(
            phase = phase,
            title = titleNow,
            // A non-Bible book being read has no verse: show its key (chapter, entry) rather than "Continue listening".
            reference = shownVerse?.getName() ?: key?.takeIf { phase != SpeakPhase.STOPPED && shownBook != null }?.getName(),
            translation = shownBook?.initials,
            percent = percent,
            verseText = textNow,
            nextVerseText = next,
            autoBookmarkOn = advancedSpeakSettings.autoBookmark,
            rows = rows,
            currentBookmarkId = currentId,
        )
    }

    private data class Tracked(val title: String? = null, val verseText: String = "", val book: Book? = null, val key: Key? = null)
}
