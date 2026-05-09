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

package net.bible.android.control.progress

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.progress.ChapterReadHistory
import net.bible.android.database.progress.DailyReadingCount
import net.bible.android.database.progress.MemorizedVerse
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ProgressControlTest {

    private val dao get() = DatabaseContainer.instance.progressDb.progressDao()

    @Before
    fun setUp() {
        // Ensure clean state
    }

    @After
    fun tearDown() {
        resetDatabase()
    }

    // --- Memorized verses ---

    @Test
    fun `markVerseMemorized marks single verse`() {
        val verse = Verse(KJVA, BibleBook.GEN, 1, 1)
        val range = VerseRange(KJVA, verse)

        ProgressControl.markVerseMemorized(range)

        assertTrue(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 1))
    }

    @Test
    fun `markVerseMemorized marks verse range`() {
        val start = Verse(KJVA, BibleBook.GEN, 1, 1)
        val end = Verse(KJVA, BibleBook.GEN, 1, 3)
        val range = VerseRange(KJVA, start, end)

        ProgressControl.markVerseMemorized(range)

        assertTrue(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 1))
        assertTrue(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 2))
        assertTrue(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 3))
        assertFalse(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 4))
    }

    @Test
    fun `markVerseMemorized is idempotent`() {
        val verse = Verse(KJVA, BibleBook.GEN, 1, 1)
        val range = VerseRange(KJVA, verse)

        ProgressControl.markVerseMemorized(range)
        ProgressControl.markVerseMemorized(range)

        assertEquals(1, ProgressControl.getTotalMemorizedVerses())
    }

    @Test
    fun `unmarkVerseMemorized removes verses`() {
        val start = Verse(KJVA, BibleBook.GEN, 1, 1)
        val end = Verse(KJVA, BibleBook.GEN, 1, 5)
        val range = VerseRange(KJVA, start, end)

        ProgressControl.markVerseMemorized(range)
        assertEquals(5, ProgressControl.getTotalMemorizedVerses())

        val removeRange = VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 2), Verse(KJVA, BibleBook.GEN, 1, 4))
        ProgressControl.unmarkVerseMemorized(removeRange)

        assertTrue(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 1))
        assertFalse(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 2))
        assertFalse(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 3))
        assertFalse(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 4))
        assertTrue(ProgressControl.isVerseMemorized(KJVA, BibleBook.GEN, 1, 5))
    }

    // --- Memorization progress ---

    @Test
    fun `getMemorizationProgress for chapter returns correct fraction`() {
        val lastVerse = KJVA.getLastVerse(BibleBook.GEN, 1)
        // Memorize first 5 verses of Gen 1
        val range = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 5)
        )
        ProgressControl.markVerseMemorized(range)

        val progress = ProgressControl.getMemorizationProgress(KJVA, BibleBook.GEN, 1)
        assertEquals(5f / lastVerse, progress, 0.001f)
    }

    @Test
    fun `getMemorizationProgress for book returns correct fraction`() {
        // Memorize Gen 1:1-3
        val range = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 3)
        )
        ProgressControl.markVerseMemorized(range)

        val progress = ProgressControl.getMemorizationProgress(KJVA, BibleBook.GEN)
        assertTrue(progress > 0f)
        assertTrue(progress < 1f)
    }

    @Test
    fun `getMemorizationProgress returns zero when nothing memorized`() {
        assertEquals(0f, ProgressControl.getMemorizationProgress(KJVA, BibleBook.GEN, 1), 0.001f)
    }

    // --- Chapter reading ---

    @Test
    fun `recordChapterRead and isChapterRead`() {
        assertFalse(ProgressControl.isChapterRead(KJVA, BibleBook.GEN, 1))

        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)

        assertTrue(ProgressControl.isChapterRead(KJVA, BibleBook.GEN, 1))
        assertFalse(ProgressControl.isChapterRead(KJVA, BibleBook.GEN, 2))
    }

    @Test
    fun `getReadingProgress returns correct fraction`() {
        val totalChapters = KJVA.getLastChapter(BibleBook.GEN)

        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2)

        val progress = ProgressControl.getReadingProgress(KJVA, BibleBook.GEN)
        assertEquals(2f / totalChapters, progress, 0.001f)
    }

    @Test
    fun `getReadingProgress returns zero when nothing read`() {
        assertEquals(0f, ProgressControl.getReadingProgress(KJVA, BibleBook.GEN), 0.001f)
    }

    @Test
    fun `getReadChaptersForBook returns read chapters`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 3)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 7)

        val chapters = ProgressControl.getReadChaptersForBook(BibleBook.GEN)
        assertEquals(listOf(3, 7), chapters)
    }

    // --- Cycles ---

    @Test
    fun `getCurrentCycle returns 1 by default`() {
        assertEquals(1, ProgressControl.getCurrentCycle())
    }

    @Test
    fun `startNewCycle increments cycle`() {
        val newCycle = ProgressControl.startNewCycle()
        assertEquals(2, newCycle)
    }

    @Test
    fun `reading records are cycle-specific`() {
        // Read in cycle 1
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        assertTrue(ProgressControl.isChapterRead(KJVA, BibleBook.GEN, 1))

        // Start new cycle - the cycle itself is determined by the max cycle in DB.
        // Since we haven't written cycle 2 records yet, getCurrentCycle still returns 1.
        // We need to manually write a cycle 2 record to advance.
        val cycle2 = ProgressControl.startNewCycle()
        dao.insertChapterReadHistory(
            net.bible.android.database.progress.ChapterReadHistory(
                kjvBookOrdinal = BibleBook.GEN.ordinal,
                chapter = 5,
                cycle = cycle2,
            )
        )

        // Now getCurrentCycle should return 2
        assertEquals(2, ProgressControl.getCurrentCycle())

        // Gen 1 was read in cycle 1, not in cycle 2
        assertFalse(ProgressControl.isChapterRead(KJVA, BibleBook.GEN, 1))
        // Gen 5 was read in cycle 2
        assertTrue(ProgressControl.isChapterRead(KJVA, BibleBook.GEN, 5))
    }

    // --- Statistics ---

    @Test
    fun `getTotalReadChapters counts correctly`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2)
        ProgressControl.recordChapterRead(KJVA, BibleBook.EXOD, 1)

        assertEquals(3, ProgressControl.getTotalReadChapters())
    }

    @Test
    fun `getBookReadingProgress returns progress for read books only`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.EXOD, 1)

        val progress = ProgressControl.getBookReadingProgress()
        assertTrue(progress.containsKey(BibleBook.GEN))
        assertTrue(progress.containsKey(BibleBook.EXOD))
        assertFalse(progress.containsKey(BibleBook.LEV))
    }

    // --- Chapter read history ---

    @Test
    fun `recordChapterRead increases count for that chapter`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)

        assertEquals(3, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 1))
    }

    @Test
    fun `getChapterReadCount returns zero for unread chapter`() {
        assertEquals(0, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 1))
    }

    @Test
    fun `getChapterReadCount is independent per chapter`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2)

        assertEquals(2, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 1))
        assertEquals(1, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 2))
        assertEquals(0, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 3))
    }

    @Test
    fun `getChapterReadCountsForBook returns map of chapter to count`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 3)

        val counts = ProgressControl.getChapterReadCountsForBook(BibleBook.GEN)
        assertEquals(2, counts[1])
        assertEquals(1, counts[3])
        assertFalse(counts.containsKey(2))
    }

    @Test
    fun `getChapterReadCountsForBook returns empty map when nothing read`() {
        assertTrue(ProgressControl.getChapterReadCountsForBook(BibleBook.GEN).isEmpty())
    }

    @Test
    fun `getDistinctReadChaptersCountForBook counts unique chapters only`() {
        // Chapter 1 read 3 times, chapter 2 once — distinct count is 2
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2)

        assertEquals(2, ProgressControl.getDistinctReadChaptersCountForBook(BibleBook.GEN))
    }

    @Test
    fun `getBookCountProgress readPercent equals totalReads divided by totalChapters`() {
        val totalChapters = KJVA.getLastChapter(BibleBook.GEN)
        // Read chapter 1 twice and chapter 2 once → 3 total reads
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2)

        val progress = ProgressControl.getBookCountProgress()
        val genProgress = progress[BibleBook.GEN]
        assertFalse("GEN should be present", genProgress == null)
        assertEquals(3f / totalChapters, genProgress!!.readPercent, 0.001f)
    }

    @Test
    fun `getBookCountProgress readPercent exceeds 1 when chapters read multiple times`() {
        // 3 John has 1 chapter; reading it 6 times should give readPercent = 6.0
        val john3 = BibleBook.JOHN3
        val totalChapters = KJVA.getLastChapter(john3)
        assertEquals("3 John should have 1 chapter", 1, totalChapters)
        repeat(6) { ProgressControl.recordChapterRead(KJVA, john3, 1) }

        val progress = ProgressControl.getBookCountProgress()
        assertEquals(6.0f, progress[john3]!!.readPercent, 0.001f)
    }

    @Test
    fun `getBookCountProgress excludes books with no reads`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)

        val progress = ProgressControl.getBookCountProgress()
        assertTrue(progress.containsKey(BibleBook.GEN))
        assertFalse(progress.containsKey(BibleBook.EXOD))
    }

    @Test
    fun `deleteReadHistoryEntry removes one read instance only`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)

        val latestEntry = ProgressControl.getReadHistoryForChapter(BibleBook.GEN, 1).first()
        ProgressControl.deleteReadHistoryEntry(latestEntry)

        assertEquals(1, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 1))
        assertEquals(1, ProgressControl.getReadHistoryForChapter(BibleBook.GEN, 1).size)
    }

    @Test
    fun `deleteReadHistoryEntries removes only selected instances`() {
        repeat(3) { ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1) }
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2)

        val selectedEntries = ProgressControl.getReadHistoryForBook(BibleBook.GEN)
            .filter { it.chapter == 1 }
            .take(2)
        ProgressControl.deleteReadHistoryEntries(selectedEntries)

        assertEquals(1, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 1))
        assertEquals(1, ProgressControl.getChapterReadCount(KJVA, BibleBook.GEN, 2))
        assertEquals(2, ProgressControl.getReadHistoryForBook(BibleBook.GEN).size)
    }

    @Test
    fun `getReadHistoryForDay returns entries for tapped calendar day`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2)

        val entries = ProgressControl.getReadHistoryForDay(localMidnightToday())

        // Both reads land in the same millisecond, so order is unspecified — assert as a set.
        assertEquals(setOf(1, 2), entries.map { it.chapter }.toSet())
    }

    @Test
    fun `getReadHistoryForDay returns recordChapterRead entries across books`() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 3)
        ProgressControl.recordChapterRead(KJVA, BibleBook.EXOD, 1)

        val entries = ProgressControl.getReadHistoryForDay(localMidnightToday())

        assertEquals(2, entries.size)
        assertTrue(entries.any { it.kjvBookOrdinal == BibleBook.GEN.ordinal && it.chapter == 3 })
        assertTrue(entries.any { it.kjvBookOrdinal == BibleBook.EXOD.ordinal && it.chapter == 1 })
    }

    // --- Local-day bucketing across timezones (regression: AndBible/and-bible#3800) ---
    //
    // SQLite cannot resolve the device timezone, so the heatmap previously bucketed reads
    // by UTC days while the view rendered cells by local days — the two never agreed for
    // users not on UTC. These tests pin the JVM timezone to UTC+10 and assert that bucketing,
    // distinct-day counting, and per-day history all align with the *local* calendar day.

    @Test
    fun `getReadingCalendar buckets reads by local day in non-UTC timezone`() = withTimeZone("Etc/GMT-10") {
        // Three reads spanning a UTC midnight that sits at 14:00 local (UTC+10):
        //   2025-11-08T13:00:00Z = 2025-11-08 23:00 local → local day Nov 8
        //   2025-11-08T15:00:00Z = 2025-11-09 01:00 local → local day Nov 9
        //   2025-11-08T23:30:00Z = 2025-11-09 09:30 local → local day Nov 9
        insertRead(BibleBook.GEN, 1, parseUtc("2025-11-08T13:00:00Z"))
        insertRead(BibleBook.GEN, 2, parseUtc("2025-11-08T15:00:00Z"))
        insertRead(BibleBook.GEN, 3, parseUtc("2025-11-08T23:30:00Z"))

        val records = ProgressControl.getReadingCalendar(
            parseUtc("2025-11-01T00:00:00Z"),
            parseUtc("2025-11-30T23:59:00Z"),
        )

        // Local midnights expressed in UTC ms (UTC+10):
        //   2025-11-08 00:00+10:00 = 2025-11-07T14:00:00Z
        //   2025-11-09 00:00+10:00 = 2025-11-08T14:00:00Z
        val nov8Local = parseUtc("2025-11-07T14:00:00Z")
        val nov9Local = parseUtc("2025-11-08T14:00:00Z")
        assertEquals(
            listOf(DailyReadingCount(nov8Local, 1), DailyReadingCount(nov9Local, 2)),
            records,
        )
    }

    @Test
    fun `getDistinctReadDays counts distinct local days not UTC days`() = withTimeZone("Etc/GMT-10") {
        // Two reads on the same local day (Nov 9 +10) that span UTC midnight,
        // and one read on the next local day. Must report 2 local days, not 3.
        insertRead(BibleBook.GEN, 1, parseUtc("2025-11-08T15:00:00Z"))  // Nov 9 local
        insertRead(BibleBook.GEN, 2, parseUtc("2025-11-08T23:30:00Z"))  // Nov 9 local
        insertRead(BibleBook.GEN, 3, parseUtc("2025-11-09T15:00:00Z"))  // Nov 10 local

        assertEquals(2, ProgressControl.getDistinctReadDays())
    }

    @Test
    fun `getReadHistoryForDay returns reads from the local calendar day`() = withTimeZone("Etc/GMT-10") {
        // r1 belongs to local Nov 9 (06:00 local), r2 to local Nov 8 (23:00 local).
        // Querying the Nov 9 local-midnight key must return r1 only.
        insertRead(BibleBook.GEN, 1, parseUtc("2025-11-08T20:00:00Z"))
        insertRead(BibleBook.GEN, 2, parseUtc("2025-11-08T13:00:00Z"))

        val nov9LocalMidnight = parseUtc("2025-11-08T14:00:00Z")
        val entries = ProgressControl.getReadHistoryForDay(nov9LocalMidnight)

        assertEquals(listOf(1), entries.map { it.chapter })
    }

    @Test
    fun `getMemorizationCalendar buckets memorizations by local day`() = withTimeZone("Etc/GMT-10") {
        // Two memorizations on the same local day split by UTC midnight.
        dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = 1, memorizedAt = parseUtc("2025-11-08T15:00:00Z")))
        dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = 2, memorizedAt = parseUtc("2025-11-08T23:30:00Z")))

        val records = ProgressControl.getMemorizationCalendar(
            parseUtc("2025-11-01T00:00:00Z"),
            parseUtc("2025-11-30T23:59:00Z"),
        )

        assertEquals(
            listOf(DailyReadingCount(parseUtc("2025-11-08T14:00:00Z"), 2)),
            records,
        )
    }

    private fun localMidnightToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parseUtc(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun insertRead(book: BibleBook, chapter: Int, readAt: Long) {
        dao.insertChapterReadHistory(
            ChapterReadHistory(kjvBookOrdinal = book.ordinal, chapter = chapter, readAt = readAt)
        )
    }

    /** Runs [block] with the JVM default timezone temporarily set to [zoneId]. */
    private inline fun withTimeZone(zoneId: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    // --- Memorization targets ---

    @Test
    fun `addMemorizationTarget and getAllMemorizationTargets`() {
        val range = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 5)
        )

        val target = ProgressControl.addMemorizationTarget(range)
        val targets = ProgressControl.getAllMemorizationTargets()

        assertEquals(1, targets.size)
        assertEquals(target.id, targets[0].id)
    }

    @Test
    fun `removeMemorizationTarget removes by id`() {
        val range = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 5)
        )
        val target = ProgressControl.addMemorizationTarget(range)

        ProgressControl.removeMemorizationTarget(target.id)

        assertTrue(ProgressControl.getAllMemorizationTargets().isEmpty())
    }

    @Test
    fun `removeMemorizationTargetByRange splits partially overlapping target`() {
        // Target: Gen 1:1-10
        val targetRange = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 10)
        )
        ProgressControl.addMemorizationTarget(targetRange)

        // Remove: Gen 1:4-7
        val removeRange = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 4),
            Verse(KJVA, BibleBook.GEN, 1, 7)
        )
        ProgressControl.removeMemorizationTargetByRange(removeRange)

        val targets = ProgressControl.getAllMemorizationTargets()
        assertEquals(2, targets.size)

        val ordinals = targets.flatMap { it.kjvOrdinalStart..it.kjvOrdinalEnd }.sorted()
        val v1 = Verse(KJVA, BibleBook.GEN, 1, 1).ordinal
        val v3 = Verse(KJVA, BibleBook.GEN, 1, 3).ordinal
        val v8 = Verse(KJVA, BibleBook.GEN, 1, 8).ordinal
        val v10 = Verse(KJVA, BibleBook.GEN, 1, 10).ordinal
        assertEquals((v1..v3).toList() + (v8..v10).toList(), ordinals)
    }

    @Test
    fun `removeMemorizationTargetByRange fully covering target removes it`() {
        val targetRange = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 3),
            Verse(KJVA, BibleBook.GEN, 1, 5)
        )
        ProgressControl.addMemorizationTarget(targetRange)

        // Remove wider range
        val removeRange = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 10)
        )
        ProgressControl.removeMemorizationTargetByRange(removeRange)

        assertTrue(ProgressControl.getAllMemorizationTargets().isEmpty())
    }

    @Test
    fun `removeMemorizationTargetByRange with no overlap does nothing`() {
        val targetRange = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 5)
        )
        ProgressControl.addMemorizationTarget(targetRange)

        // Remove non-overlapping range
        val removeRange = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 10),
            Verse(KJVA, BibleBook.GEN, 1, 15)
        )
        ProgressControl.removeMemorizationTargetByRange(removeRange)

        assertEquals(1, ProgressControl.getAllMemorizationTargets().size)
    }

    @Test
    fun `getTargetTotalVerses sums across targets`() {
        ProgressControl.addMemorizationTarget(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1), Verse(KJVA, BibleBook.GEN, 1, 5))
        )
        ProgressControl.addMemorizationTarget(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 2, 1), Verse(KJVA, BibleBook.GEN, 2, 3))
        )

        assertEquals(8, ProgressControl.getTargetTotalVerses())
    }

    @Test
    fun `getMemorizationTargetProgress tracks memorized within targets`() {
        val range = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 10)
        )
        ProgressControl.addMemorizationTarget(range)

        // Memorize half the target
        val memorizeRange = VerseRange(
            KJVA,
            Verse(KJVA, BibleBook.GEN, 1, 1),
            Verse(KJVA, BibleBook.GEN, 1, 5)
        )
        ProgressControl.markVerseMemorized(memorizeRange)

        val (memorized, total) = ProgressControl.getMemorizationTargetProgress()
        assertEquals(5, memorized)
        assertEquals(10, total)
    }

    @Test
    fun `getMemorizationTargetProgress returns zero when no targets`() {
        val (memorized, total) = ProgressControl.getMemorizationTargetProgress()
        assertEquals(0, memorized)
        assertEquals(0, total)
    }

    // --- getMemorizedVerseRanges ---

    @Test
    fun `getMemorizedVerseRanges groups consecutive verses`() {
        // Memorize Gen 1:1-3 and Gen 1:5-6 (gap at v4)
        ProgressControl.markVerseMemorized(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1), Verse(KJVA, BibleBook.GEN, 1, 3))
        )
        ProgressControl.markVerseMemorized(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 5), Verse(KJVA, BibleBook.GEN, 1, 6))
        )

        val ranges = ProgressControl.getMemorizedVerseRanges()
            .sortedBy { it.start.ordinal }
        assertEquals(2, ranges.size)
        assertEquals(3, ranges[0].cardinality)  // v1-v3
        assertEquals(2, ranges[1].cardinality)  // v5-v6
    }

    @Test
    fun `getMemorizedVerseRanges returns empty for no memorized verses`() {
        assertTrue(ProgressControl.getMemorizedVerseRanges().isEmpty())
    }

    @Test
    fun `getMemorizedVerseRanges merges adjacent ranges into one`() {
        // Memorize Gen 1:1-5 as one block
        ProgressControl.markVerseMemorized(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1), Verse(KJVA, BibleBook.GEN, 1, 5))
        )

        val ranges = ProgressControl.getMemorizedVerseRanges()
        assertEquals(1, ranges.size)
        assertEquals(5, ranges[0].cardinality)
    }

    // --- getTargetOrdinalsInRange ---

    @Test
    fun `getTargetOrdinalsInRange returns ordinals within query range`() {
        // Target: Gen 1:5-10
        val v5 = Verse(KJVA, BibleBook.GEN, 1, 5).ordinal
        val v10 = Verse(KJVA, BibleBook.GEN, 1, 10).ordinal
        ProgressControl.addMemorizationTarget(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 5), Verse(KJVA, BibleBook.GEN, 1, 10))
        )

        // Query: Gen 1:1-7 — should only include 5-7
        val v1 = Verse(KJVA, BibleBook.GEN, 1, 1).ordinal
        val v7 = Verse(KJVA, BibleBook.GEN, 1, 7).ordinal
        val result = ProgressControl.getTargetOrdinalsInRange(v1, v7)

        assertEquals((v5..v7).toList(), result)
    }

    @Test
    fun `getTargetOrdinalsInRange returns empty when no overlap`() {
        ProgressControl.addMemorizationTarget(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 5), Verse(KJVA, BibleBook.GEN, 1, 10))
        )

        val v20 = Verse(KJVA, BibleBook.GEN, 1, 20).ordinal
        val v25 = Verse(KJVA, BibleBook.GEN, 1, 25).ordinal
        val result = ProgressControl.getTargetOrdinalsInRange(v20, v25)

        assertTrue(result.isEmpty())
    }

    // --- getMemorizedOrdinalsInRange ---

    @Test
    fun `getMemorizedOrdinalsInRange returns memorized ordinals in range`() {
        ProgressControl.markVerseMemorized(
            VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1), Verse(KJVA, BibleBook.GEN, 1, 10))
        )

        val v3 = Verse(KJVA, BibleBook.GEN, 1, 3).ordinal
        val v6 = Verse(KJVA, BibleBook.GEN, 1, 6).ordinal
        val result = ProgressControl.getMemorizedOrdinalsInRange(v3, v6)

        assertEquals((v3..v6).toList(), result)
    }
}
