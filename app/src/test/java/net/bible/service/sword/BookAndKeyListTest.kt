package net.bible.service.sword

import com.nhaarman.mockitokotlin2.mock
import net.bible.service.download.FakeBookFactory
import org.crosswire.jsword.passage.DistinctPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BookAndKeyListTest {
    private val kjv = Versifications.instance().getVersification("KJV")!!
    private val book = FakeBookFactory(mock()).giveDoesNotExist("ListBook")

    private fun key(verse: Int) =
        BookAndKey(DistinctPassage(kjv).apply { addAll(Verse(kjv, BibleBook.GEN, 1, verse)) }, book)

    private fun list() = BookAndKeyList().apply {
        addAll(key(1))
        addAll(key(2))
    }

    @Test
    fun cloneHasTheSameKeysAndOsisRef() {
        val original = list()
        val copy = original.clone()
        assertTrue((copy as Any) is BookAndKeyList)
        assertNotSame(original, copy)
        assertEquals(original.getOsisRef(), copy.getOsisRef())
        assertEquals(original.getCardinality(), copy.getCardinality())
        assertEquals(2, copy.getCardinality())
    }

    @Test
    fun cloneDoesNotShareTheList() {
        val original = list()
        val copy = original.clone()
        copy.addAll(key(3))
        assertEquals(2, original.getCardinality())
        assertEquals(3, copy.getCardinality())
    }
}
