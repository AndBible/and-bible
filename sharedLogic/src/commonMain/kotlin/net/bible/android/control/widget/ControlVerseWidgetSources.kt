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

    override suspend fun partOrdinals(part: VerseSource.RandomPart, bible: VerseBible): VerseOrdinals {
        val v11n = v11n(bible.v11nName) ?: return ChapterBlocks(emptyList())
        val all = (0 until v11n.bookCount).map { v11n.getBook(it) }
        // Same span as before: from the first to the last book of the part in this versification's own order.
        fun between(from: BibleBook, to: BibleBook): List<BibleBook> {
            val i = all.indexOf(from)
            val j = all.indexOf(to)
            return if (i < 0 || j < i) emptyList() else all.subList(i, j + 1)
        }
        val books = when (part) {
            VerseSource.RandomPart.WHOLE_BIBLE -> between(BibleBook.GEN, BibleBook.REV)
            VerseSource.RandomPart.NEW_TESTAMENT -> between(BibleBook.MATT, BibleBook.REV)
            VerseSource.RandomPart.PSALMS_PROVERBS -> between(BibleBook.PS, BibleBook.PROV)
        }
        return try {
            // One block per chapter, verses 1..last only: the ordinals in between are introductions (chapter 0, verse 0).
            ChapterBlocks(books.flatMap { book ->
                (1..v11n.getLastChapter(book)).mapNotNull { chapter ->
                    val last = v11n.getLastVerse(book, chapter)
                    if (last < 1) null
                    else v11n.getOrdinal(Verse(v11n, book, chapter, 1))..v11n.getOrdinal(Verse(v11n, book, chapter, last))
                }
            })
        } catch (e: Exception) {
            ChapterBlocks(emptyList())
        }
    }

    /** The real verses as consecutive-ordinal blocks (one per chapter), found by binary search over cumulative sizes. */
    internal class ChapterBlocks(private val blocks: List<IntRange>) : VerseOrdinals {
        private val starts = IntArray(blocks.size).also { var n = 0; blocks.forEachIndexed { i, r -> it[i] = n; n += r.last - r.first + 1 } }
        override val size: Int = blocks.lastOrNull()?.let { starts.last() + it.last - it.first + 1 } ?: 0

        override fun ordinalAt(index: Int): Int {
            require(index in 0 until size) { "index $index of $size" }
            var lo = 0
            var hi = blocks.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (starts[mid] <= index) lo = mid else hi = mid - 1
            }
            return blocks[lo].first + (index - starts[lo])
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
