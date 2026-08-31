package net.bible.service.sword

import net.bible.service.download.FakeBookFactory
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * `BookAndKey.getName()` prefixes the document abbreviation, which is right where a key is shown out
 * of context and wrong where the document IS the context (an EPUB search targets one book; a chapter
 * picker lists one book's chapters). For an EPUB the abbreviation is the full title, which is what
 * made every result row read "Book title: chapter title".
 */
class BookAndKeyNameTest {

    @Test
    fun stripsTheDocumentPrefixFromABookAndKey() {
        val book = FakeBookFactory.giveDoesNotExist("MyEpub")
        val key = BookAndKey(DefaultLeafKeyList("Chapter 1", "1"), book)
        assertNotEquals("Chapter 1", key.name)          // the prefix really is there
        assertEquals("Chapter 1", key.nameWithoutDocument)
    }

    @Test
    fun aBookAndKeyWithNoDocumentIsUnchanged() {
        val key = BookAndKey(DefaultLeafKeyList("Chapter 1", "1"), null)
        assertEquals("Chapter 1", key.nameWithoutDocument)
    }

    @Test
    fun aPlainKeyIsUnchanged() {
        val key = DefaultLeafKeyList("Chapter 1", "1")
        assertEquals("Chapter 1", key.nameWithoutDocument)
    }
}
