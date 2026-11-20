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

import kotlinx.coroutines.test.runTest
import net.bible.android.database.IdType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fake data: a toy Bible of 66 "books" of 100 verses each; book 40 (ordinal 3900..3999) is Matthew. */
internal class FakeVerseSources : VerseWidgetSources {
    val web = VerseBible("WEB", "WEB", "KJV")
    var default: VerseBible? = web
    var installed = mutableMapOf("WEB" to web)
    val labelMap = mutableMapOf<IdType, LabelVerses>()
    var plan: VerseRef? = null
    /** Verse text by start ordinal; absent = "verse N". */
    val texts = mutableMapOf<Int, String>()
    var emptyOrdinals = mutableSetOf<Int>()

    override suspend fun defaultBible() = default
    override suspend fun bible(initials: String) = installed[initials]
    override suspend fun bibles() = installed.values.toList()
    override suspend fun labels() = labelMap.map { it.key to it.value.name }
    override suspend fun label(labelId: IdType) = labelMap[labelId]
    /** When set, every random part uses this pool instead of the toy ranges. */
    var poolOverride: VerseOrdinals? = null
    override suspend fun partOrdinals(part: VerseSource.RandomPart, bible: VerseBible): VerseOrdinals = poolOverride ?: RangeVerseOrdinals(when (part) {
        VerseSource.RandomPart.WHOLE_BIBLE -> 0 until 6600
        VerseSource.RandomPart.NEW_TESTAMENT -> NT_START until 6600
        VerseSource.RandomPart.PSALMS_PROVERBS -> 1800 until 2000
    })
    override suspend fun readingPlanPassage(bible: VerseBible) = plan
    override suspend fun text(ref: VerseRef, bible: VerseBible): String {
        if (ref.startOrdinal in emptyOrdinals) return ""
        return (ref.startOrdinal..ref.endOrdinal).joinToString(" ") { texts[it] ?: "verse $it" }
    }
    override fun referenceName(ref: VerseRef, bible: VerseBible) =
        if (ref.startOrdinal == ref.endOrdinal) "Ref ${ref.startOrdinal}" else "Ref ${ref.startOrdinal}-${ref.endOrdinal}"
    override fun osisRef(ref: VerseRef, bible: VerseBible) = "O.${ref.startOrdinal}"
    override fun sourceName(source: VerseSource) = when (source) {
        is VerseSource.Random -> "Random ${source.part}"
        VerseSource.ReadingPlan -> "Today's reading"
        is VerseSource.Label -> error("label names come from the label")
    }
    override fun formatDate(epochMs: Long) = "date $epochMs"

    companion object { const val NT_START = 3900 }
}

class VerseCandidatesTest {
    private val sources = FakeVerseSources()
    private val candidates = VerseCandidates(sources)
    private val utc = LocalZone { 0L }
    private val now = WidgetTestTime.utc(2026, 11, 20, 10)
    private fun ref(o: Int, end: Int = o) = VerseRef("WEB", "KJV", o, end)
    private fun config(source: VerseSource, seed: Long = 1L, frequency: VerseChangeFrequency = VerseChangeFrequency.DAILY) =
        VerseWidgetConfig(source = source, frequency = frequency, seed = seed)

    @Test fun labelSourceUsesOnlyBibleBookmarksInVerseOrder() = runTest {
        val id = IdType()
        // the port's contract: Bible bookmarks only (generic ones never reach it), ordered by ordinal
        sources.labelMap[id] = LabelVerses("Memory", listOf(ref(10), ref(20), ref(30)))
        assertEquals(listOf(10, 20, 30), candidates.candidates(VerseSource.Label(id), sources.web).map { it.startOrdinal })
        val content = candidates.content(config(VerseSource.Label(id)), now, utc)
        assertIs<VerseWidgetContent.Verse>(content)
        assertEquals("Memory", content.sourceName)
        assertTrue(content.ref.startOrdinal in listOf(10, 20, 30))
    }

    @Test fun deletedLabelGivesLabelMissing() = runTest {
        val content = candidates.content(config(VerseSource.Label(IdType())), now, utc)
        assertEquals(VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.LABEL_MISSING), content)
    }

    @Test fun emptyLabelGivesLabelEmpty() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Empty", emptyList())
        assertEquals(VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.LABEL_EMPTY), candidates.content(config(VerseSource.Label(id)), now, utc))
    }

    @Test fun noBibleInstalledGivesNoBible() = runTest {
        sources.default = null
        sources.installed.clear()
        assertEquals(VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.NO_BIBLE),
            candidates.content(config(VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE)), now, utc))
    }

    @Test fun missingChosenBibleFallsBackToDefault() = runTest {
        val c = config(VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE)).copy(bibleInitials = "GONE")
        assertIs<VerseWidgetContent.Verse>(candidates.content(c, now, utc))
    }

    @Test fun noActivePlanGivesNoReadingPlan() = runTest {
        assertEquals(VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.NO_READING_PLAN),
            candidates.content(config(VerseSource.ReadingPlan), now, utc))
    }

    @Test fun readingPlanClampedToFirstThreeVerses() = runTest {
        sources.plan = ref(100, 140)
        val c = candidates.content(config(VerseSource.ReadingPlan), now, utc)
        assertIs<VerseWidgetContent.Verse>(c)
        assertEquals(ref(100, 102), c.ref)
        assertEquals("Today's reading", c.sourceName)
    }

    @Test fun randomNewTestamentStaysInNt() = runTest {
        val src = VerseSource.Random(VerseSource.RandomPart.NEW_TESTAMENT)
        repeat(200) { p ->
            val c = candidates.content(config(src, seed = 5L), WidgetTestTime.utc(2026, 1, 1) + p * 86_400_000L, utc)
            assertIs<VerseWidgetContent.Verse>(c)
            assertTrue(c.ref.startOrdinal >= FakeVerseSources.NT_START, "ordinal ${c.ref.startOrdinal}")
        }
    }

    @Test fun psalmsProverbsPart() = runTest {
        val src = VerseSource.Random(VerseSource.RandomPart.PSALMS_PROVERBS)
        repeat(100) { p ->
            val c = candidates.content(config(src, seed = 9L), WidgetTestTime.utc(2026, 1, 1) + p * 86_400_000L, utc) as VerseWidgetContent.Verse
            assertTrue(c.ref.startOrdinal in 1800 until 2000)
        }
    }

    @Test fun verseRangeBookmarkShowsWholeRange() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Ranges", listOf(ref(50, 51)))
        sources.texts[50] = "In the beginning"
        sources.texts[51] = "was the Word"
        val c = candidates.content(config(VerseSource.Label(id)), now, utc) as VerseWidgetContent.Verse
        assertTrue(c.text.contains("In the beginning") && c.text.contains("was the Word"), c.text)
        assertEquals("Ref 50-51", c.reference)
    }

    @Test fun emptyVerseIsSkipped() = runTest {
        val id = IdType()
        val refs = (0 until 5).map { ref(it) }
        sources.labelMap[id] = LabelVerses("Five", refs)
        val first = (candidates.content(config(VerseSource.Label(id)), now, utc) as VerseWidgetContent.Verse).ref.startOrdinal
        sources.emptyOrdinals += first
        val second = (candidates.content(config(VerseSource.Label(id)), now, utc) as VerseWidgetContent.Verse).ref.startOrdinal
        assertEquals((first + 1) % 5, second)
    }

    @Test fun allVersesEmptyGivesUnavailable() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Blank", listOf(ref(1), ref(2)))
        sources.emptyOrdinals += setOf(1, 2)
        assertEquals(VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.LABEL_EMPTY),
            candidates.content(config(VerseSource.Label(id)), now, utc))
    }

    @Test fun linkIsAndBibleUrl() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("L", listOf(ref(7)))
        val c = candidates.content(config(VerseSource.Label(id)), now, utc) as VerseWidgetContent.Verse
        assertTrue(c.link.startsWith("https://read.andbible.org/"), c.link)
        assertTrue(c.link.contains("document=WEB"), c.link)
        assertTrue(c.link.contains("ordinal=7"), c.link)
    }

    @Test fun sameWidgetShowsSameVerseAllDay() = runTest {
        val src = VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE)
        val morning = candidates.content(config(src), WidgetTestTime.utc(2026, 11, 20, 6), utc)
        val night = candidates.content(config(src), WidgetTestTime.utc(2026, 11, 20, 23), utc)
        assertEquals((morning as VerseWidgetContent.Verse).ref, (night as VerseWidgetContent.Verse).ref)
    }

    /** Ordinals 10, 20, 30...: the gaps stand for chapter and verse 0 introductions that must never be picked. */
    private class GappedOrdinals(override val size: Int) : VerseOrdinals {
        override fun ordinalAt(index: Int) = (index + 1) * 10
    }

    @Test fun randomPickNeverLandsInAGapAndIsUniform() = runTest {
        sources.poolOverride = GappedOrdinals(4)
        val src = VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE)
        val counts = HashMap<Int, Int>()
        repeat(2000) { p ->
            val c = candidates.content(config(src, seed = 3L), WidgetTestTime.utc(2026, 1, 1) + p * 86_400_000L, utc) as VerseWidgetContent.Verse
            counts[c.ref.startOrdinal] = (counts[c.ref.startOrdinal] ?: 0) + 1
        }
        assertEquals(setOf(10, 20, 30, 40), counts.keys)
        // uniform: each of 4 verses about 500 of 2000, never the doubled weight of a skipped neighbour
        assertTrue(counts.values.all { it in 400..600 }, counts.toString())
    }

    @Test fun randomCandidatesListOnlyPoolOrdinals() = runTest {
        sources.poolOverride = GappedOrdinals(3)
        val list = candidates.candidates(VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE), sources.web)
        assertEquals(listOf(10, 20, 30), list.map { it.startOrdinal })
    }

    @Test fun randomSampleRotatesWithPeriod() = runTest {
        val src = VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE)
        val a = candidates.sample(src, sources.web, 30, rotation = 100L).map { it.ref.startOrdinal }
        val b = candidates.sample(src, sources.web, 30, rotation = 101L).map { it.ref.startOrdinal }
        assertEquals(30, a.size)
        assertEquals(a, candidates.sample(src, sources.web, 30, rotation = 100L).map { it.ref.startOrdinal })
        assertTrue(a != b)
    }

    @Test fun bigLabelSampleRotatesWithPeriod() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Big", (0 until 100).map { ref(it) })
        val a = candidates.sample(VerseSource.Label(id), sources.web, 30, rotation = 5L).map { it.ref.startOrdinal }
        val b = candidates.sample(VerseSource.Label(id), sources.web, 30, rotation = 6L).map { it.ref.startOrdinal }
        assertEquals(30, a.size)
        assertEquals(30, a.toSet().size)
        assertTrue(a != b)
    }

    @Test fun labelSampleStillReachesLimitWhenSomeVersesAreEmpty() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Big", (0 until 100).map { ref(it) })
        sources.emptyOrdinals += (0 until 100 step 4)
        val sample = candidates.sample(VerseSource.Label(id), sources.web, 30, rotation = 1L)
        assertEquals(30, sample.size)
        assertTrue(sample.none { it.ref.startOrdinal % 4 == 0 })
    }

    @Test fun rangeOrdinalsRejectIndexOutsideThePool() {
        val pool = RangeVerseOrdinals(10..12)
        assertEquals(12, pool.ordinalAt(2))
        assertFailsWith<IllegalArgumentException> { pool.ordinalAt(3) }
        assertFailsWith<IllegalArgumentException> { pool.ordinalAt(-1) }
    }
}
