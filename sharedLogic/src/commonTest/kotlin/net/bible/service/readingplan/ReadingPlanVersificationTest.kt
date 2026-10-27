package net.bible.service.readingplan

import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.VersificationConverter
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the plan parser and conversion used by ReadingPlanControl.read and speak. */
class ReadingPlanVersificationTest {
    private val versifications = Versifications.instance()
    private val kjv = versifications.getVersification("KJV")!!
    private val german = versifications.getVersification("German")!!
    private val reader = PassageReader(kjv)
    private val converter = VersificationConverter()

    @Test
    fun finalKjvJoelChapterCoversTheFourthChapterInGermanNumbering() {
        val reading = converter.convert(reader.getKey("Joel.3"), german)

        val verses = reading.map { it as Verse }
        assertEquals("Joel.4.1", verses.first().getOsisID())
        assertEquals("Joel.4.21", verses.last().getOsisID())
        assertEquals(21, verses.size)
    }

    @Test
    fun separateDailyJoelReadingsCoverEveryVerseInFourChapterNumbering() {
        val readings = (1..3).map { chapter ->
            KeyUtil.getPassage(converter.convert(reader.getKey("Joel.$chapter"), german))!!
        }

        for ((chapter, lastVerse) in listOf(20, 27, 5, 21).withIndex()) {
            for (verse in 1..lastVerse) {
                val expected = Verse(german, BibleBook.JOEL, chapter + 1, verse)
                assertTrue(readings.any { it.contains(expected) }, "Missing ${expected.getOsisID()}")
            }
        }
    }

    @Test
    fun wholeBookAndChapterRangesIncludeTheExtraChapter() {
        for (reference in listOf("Joel", "Joel.1-Joel.3")) {
            val reading = converter.convert(reader.getKey(reference), german)

            val verses = reading.map { it as Verse }.filter { it.verse > 0 }
            assertEquals("Joel.1.1", verses.first().getOsisID())
            assertEquals("Joel.4.21", verses.last().getOsisID())
            assertEquals(73, verses.size)
        }
    }
}
