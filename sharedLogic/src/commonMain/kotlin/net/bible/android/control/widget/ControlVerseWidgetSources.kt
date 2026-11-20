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

import net.bible.android.common.toV11n
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.database.IdType
import net.bible.service.format.SaxTextPolicy
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.platform.LocalCalendar
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

/** [VerseWidgetSources] over the app's controls: bookmarks, reading plan, the active window and SWORD text. */
class ControlVerseWidgetSources(
    private val bookmarkControl: BookmarkControl,
    private val windowControl: WindowControl,
    private val readingPlanControl: ReadingPlanControl,
    private val coreStrings: CoreStrings,
    private val saxTextPolicy: SaxTextPolicy,
    private val calendar: LocalCalendar,
    private val widgetStrings: WidgetStrings,
) : VerseWidgetSources {

    private fun SwordBook.asVerseBible() = VerseBible(initials, abbreviation, versification.name)

    private fun swordBook(initials: String): SwordBook? =
        SwordDocumentFacade.bibles.firstOrNull { it.initials == initials } as? SwordBook

    private fun v11n(name: String): Versification? = Versifications.instance().getVersification(name)

    override suspend fun defaultBible(): VerseBible? = try {
        windowControl.defaultBibleDoc(false).asVerseBible()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun bible(initials: String): VerseBible? = swordBook(initials)?.asVerseBible()

    override suspend fun bibles(): List<VerseBible> =
        SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().map { it.asVerseBible() }

    override suspend fun labels(): List<Pair<IdType, String>> =
        bookmarkControl.assignableLabels().filter { !it.isSpecialLabel }.map { it.id to it.name }

    override suspend fun label(labelId: IdType): LabelVerses? {
        val label = bookmarkControl.labelById(labelId)?.takeIf { !it.isSpecialLabel } ?: return null
        val refs = bookmarkControl.getBibleBookmarksWithLabel(label)
            .sortedWith { a, b -> a.verseRange.start.compareTo(b.verseRange.start) }
            .map {
                VerseRef(it.playbackSettings?.bookId, it.verseRange.versification.name, it.verseRange.start.ordinal, it.verseRange.end.ordinal)
            }
        return LabelVerses(label.name, refs)
    }

    override suspend fun partOrdinals(part: VerseSource.RandomPart, bible: VerseBible): IntRange {
        val v11n = v11n(bible.v11nName) ?: return IntRange.EMPTY
        fun first(book: BibleBook) = v11n.getOrdinal(Verse(v11n, book, 1, 1))
        fun last(book: BibleBook): Int {
            val chapter = v11n.getLastChapter(book)
            return v11n.getOrdinal(Verse(v11n, book, chapter, v11n.getLastVerse(book, chapter)))
        }
        return try {
            when (part) {
                VerseSource.RandomPart.WHOLE_BIBLE -> first(BibleBook.GEN)..last(BibleBook.REV)
                VerseSource.RandomPart.NEW_TESTAMENT -> first(BibleBook.MATT)..last(BibleBook.REV)
                VerseSource.RandomPart.PSALMS_PROVERBS -> first(BibleBook.PS)..last(BibleBook.PROV)
            }
        } catch (e: Exception) {
            IntRange.EMPTY
        }
    }

    override suspend fun readingPlanPassage(bible: VerseBible): VerseRef? {
        if (!readingPlanControl.isReadingPlanSelected || !readingPlanControl.currentPlanExists()) return null
        val v11n = v11n(bible.v11nName) ?: return null
        val day = readingPlanControl.currentPlanDay()
        val key = readingPlanControl.getDaysReading(day).forVersification(v11n).getReadingKeys.firstOrNull() ?: return null
        val passage = KeyUtil.getPassage(key) ?: return null
        if (passage.countVerses() == 0) return null
        val range = passage.getRangeAt(0, RestrictionType.NONE)
        return VerseRef(bible.initials, range.versification.name, range.start.ordinal, range.end.ordinal)
    }

    private fun range(ref: VerseRef, toV11n: Versification? = null): VerseRange? {
        val v11n = v11n(ref.v11nName) ?: return null
        val r = VerseRange(v11n, Verse(v11n, ref.startOrdinal), Verse(v11n, ref.endOrdinal))
        return if (toV11n != null && toV11n != v11n) r.toV11n(toV11n) else r
    }

    override suspend fun text(ref: VerseRef, bible: VerseBible): String {
        val book = swordBook(bible.initials) ?: return ""
        val range = range(ref, book.versification) ?: return ""
        return try {
            range.map { SwordContentFacade.getCanonicalText(book, it, true, strings = coreStrings, saxTextPolicy = saxTextPolicy) }
                .joinToString(" ") { it.trim() }.trim()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    override fun referenceName(ref: VerseRef, bible: VerseBible): String {
        val book = swordBook(bible.initials)
        return range(ref, book?.versification)?.getName() ?: ""
    }

    override fun osisRef(ref: VerseRef, bible: VerseBible): String {
        val book = swordBook(bible.initials)
        return range(ref, book?.versification)?.getOsisRef() ?: ""
    }

    override fun sourceName(source: VerseSource): String = when (source) {
        is VerseSource.Random -> widgetStrings.randomSourceName(source.part)
        VerseSource.ReadingPlan -> widgetStrings.readingPlanSourceName
        is VerseSource.Label -> ""
    }

    override fun formatDate(epochMs: Long): String = calendar.formatDefaultDate(epochMs)
}
