package net.bible.service.sword.mybible

import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #2747: versification detection on the chapter bounds of real MyBible modules (resources
 * `mybible/<name>-chapter-bounds.txt`, numbers only). Detection minimises lost protocanonical verses first.
 */
class MyBibleVersificationDetectionTest {
    private fun v11n(name: String) = Versifications.instance().getVersification(name)!!
    private fun lost(proto: Long, deutero: Long) = MyBibleLostVerses(proto, deutero)

    @Test
    fun nrsvceShapedModuleKeepsEveryProtocanonicalVerseInNrsva() {
        val chapters = chapterBounds("nrsvce")
        assertEquals("NRSVA", detectMyBibleVersification(chapters).name)
        val fit = fitMyBibleVersification(chapters, v11n("NRSVA"))
        // Esther 10:4-13 and Daniel 3:31-97, 13, 14: Greek additions NRSVA keeps as separate books.
        assertEquals(mapOf(BibleBook.ESTH to lost(0, 10), BibleBook.DAN to lost(0, 173)), fit.lostVerses)
        // Catholic2 keeps Daniel 13-14 but drops fewer verses only by dropping protocanonical ones.
        val catholic = fitMyBibleVersification(chapters, v11n("Catholic2"))
        assertEquals(133, catholic.totalLost)
        assertEquals(lost(21, 0), catholic.lostVerses[BibleBook.JOEL])
        assertEquals(lost(6, 0), catholic.lostVerses[BibleBook.MAL])
    }

    @Test
    fun nabreShapedModuleLosesNoProtocanonicalVerse() {
        val chapters = chapterBounds("nabre")
        assertEquals("Catholic2", detectMyBibleVersification(chapters).name)
        val fit = fitMyBibleVersification(chapters, v11n("Catholic2"))
        assertEquals(0, fit.protocanonicalLost)
        assertEquals(
            mapOf(BibleBook.ESTH to lost(0, 62), BibleBook.JOB to lost(0, 1), BibleBook.DAN to lost(0, 1), BibleBook.JOHN to lost(0, 1)),
            fit.lostVerses,
        )
    }

    @Test
    fun separateBookAndVulgateLayoutsKeepTheirVersification() {
        assertEquals("NRSVA", detectMyBibleVersification(chapterBounds("nrsv")).name)
        assertEquals("LXX", detectMyBibleVersification(chapterBounds("lxxe")).name)
        assertEquals("Vulg", detectMyBibleVersification(chapterBounds("cpdv")).name)
        assertEquals(0, fitMyBibleVersification(chapterBounds("cpdv"), v11n("Vulg")).totalLost)
    }

    @Test
    fun lostVersesSplitAtKjvChapterEnd() {
        // Joel 2 has 32 verses in KJV, 27 in Catholic2: 28-32 are protocanonical; Joel 4 is not a KJV chapter.
        val fit = fitMyBibleVersification(
            listOf(MyBibleChapterBound(360, 2, 32), MyBibleChapterBound(360, 4, 21), MyBibleChapterBound(345, 1, 42)),
            v11n("Catholic2"),
        )
        assertEquals(lost(5, 0), fit.lostVerses[BibleBook.JOEL])
        assertEquals(lost(0, 42), fit.lostVerses[BibleBook.BEL])
    }

    @Test
    fun emptyModuleKeepsKjva() {
        assertEquals("KJVA", detectMyBibleVersification(emptyList()).name)
    }

    @Test
    fun book345IsAdditionsToDanielOnlyWhereThereIsNoBel() {
        assertEquals(BibleBook.ADD_DAN, myBibleBook(345, v11n("Luther")))
        assertEquals(BibleBook.BEL, myBibleBook(345, v11n("NRSVA")))
        assertEquals(BibleBook.BEL, myBibleBook(345, null))
        assertEquals(345, bibleBookToMyBibleInt[BibleBook.ADD_DAN])
        assertEquals(345, bibleBookToMyBibleInt[BibleBook.BEL])
    }
}

/** The chapter bounds of fixture `mybible/<name>-chapter-bounds.txt`; a 0 marks a chapter the module lacks. */
internal fun chapterBounds(name: String): List<MyBibleChapterBound> =
    MyBibleVersificationDetectionTest::class.java.getResourceAsStream("/mybible/$name-chapter-bounds.txt")!!
        .bufferedReader().use { it.readLines() }
        .filterNot { it.startsWith("#") || it.isBlank() }
        .flatMap { line ->
            val (number, counts) = line.split(":")
            counts.split(",").mapIndexedNotNull { index, last ->
                last.toInt().takeIf { it > 0 }?.let { MyBibleChapterBound(number.toInt(), index + 1, it) }
            }
        }
