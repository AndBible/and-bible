package net.bible.service.sword

import net.bible.service.download.NullKeyBackend
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Characterizes the JVM-only subclasses that must survive K3's explicit Key copies. */
class BookAndKeyCloneTest {
    @Test
    fun bookAndKeyCopiesNestedKeyButKeepsDocumentAndDropsOptionalLocation() {
        val kjv = org.crosswire.jsword.versification.system.Versifications.instance().getVersification("KJV")!!
        val a = org.crosswire.jsword.passage.Verse(kjv, org.crosswire.jsword.versification.BibleBook.GEN, 1, 1)
        val b = org.crosswire.jsword.passage.Verse(kjv, org.crosswire.jsword.versification.BibleBook.GEN, 1, 2)
        val c = org.crosswire.jsword.passage.Verse(kjv, org.crosswire.jsword.versification.BibleBook.GEN, 1, 3)
        val nested = org.crosswire.jsword.passage.DistinctPassage(kjv).apply { addAll(a) }
        val book = net.bible.service.download.FakeBookFactory(com.nhaarman.mockitokotlin2.mock()).giveDoesNotExist("CloneBook")
        val ordinal = net.bible.android.control.page.OrdinalRange(1, 3)
        val original = BookAndKey(nested, book, ordinal = ordinal, htmlId = "anchor")
        assertSame(ordinal, original.ordinal)
        val copy = original.clone() as BookAndKey
        assertSame(original.javaClass, copy.javaClass)
        assertNotSame(original, copy)
        assertNotSame(nested, copy.key)
        assertEquals(original.getOsisRef(), copy.getOsisRef())
        assertSame(original.document, copy.document)
        org.junit.Assert.assertNull(copy.htmlId)
        org.junit.Assert.assertNull(copy.ordinal)
        copy.key.addAll(b)
        copy.key.removeAll(a)
        assertTrue(nested.contains(a))
        assertFalse(nested.contains(b))
        nested.addAll(c)
        nested.removeAll(a)
        assertTrue(copy.key.contains(b))
        assertFalse(copy.key.contains(c))
        assertFalse(copy.key.contains(a))
    }

    @Test
    fun studyPadCloneSharesLabelAndHistoricallyDropsEntryId() {
        val label = net.bible.android.database.bookmarks.BookmarkEntities.Label(name = "original")
        val entryId = net.bible.android.database.IdType()
        val original = StudyPadKey(label, entryId)
        val copy = original.clone() as StudyPadKey
        assertSame(original.javaClass, copy.javaClass)
        assertNotSame(original, copy)
        assertSame(label, copy.label)
        assertEquals(original.getOsisRef(), copy.getOsisRef())
        assertEquals(entryId, original.entryId)
        org.junit.Assert.assertNull(copy.entryId)
        label.name = "changed"
        assertEquals(original.getName(), copy.getName())
        copy.label.name = "copy mutation"
        assertEquals("copy mutation", original.getName())
    }

    @Test
    fun bookAndKeyListKeepsRuntimeClassAndSharesItsCollection() {
        val a = DefaultLeafKeyList("a", "A")
        val b = DefaultLeafKeyList("b", "B")
        val list = BookAndKeyList().apply { addAll(a) }
        val c = list.clone()
        assertSame(BookAndKeyList::class.java, c.javaClass)
        assertNotSame(list, c)
        assertEquals("a", c.getName())
        assertEquals("A", c.getOsisRef())
        assertSame(a, c.get(0))
        c.addAll(b)
        assertTrue(list.contains(b))
        assertEquals("A||B", list.getOsisRef())
        list.removeAll(a)
        assertFalse(c.contains(a))
        c.setName("copy")
        assertEquals("b", list.getName())
        assertEquals("copy", c.getName())
    }

    @Test
    fun nullKeyBackendKeepsRuntimeClassMetadataAndSubclassState() {
        val metadata = SwordBookMetaData(
            "[CloneTest]\nDescription=Clone fixture\nModDrv=RawLD\n".toByteArray(), "CloneTest"
        )
        val a = DefaultLeafKeyList("a")
        val b = DefaultLeafKeyList("b")
        val backend = NullKeyBackend(metadata).apply { indexOf(a) }
        val c = backend.clone() as NullKeyBackend
        assertSame(NullKeyBackend::class.java, c.javaClass)
        assertNotSame(backend, c)
        assertEquals(backend.getName(), c.getName())
        assertSame(a, c.get(0))
        assertSame(metadata, c.initState().metadata)
        c.indexOf(b)
        assertSame(a, backend.get(0))
        assertSame(b, c.get(0))
        backend.indexOf(null)
        assertSame(backend, backend.get(0))
        assertSame(b, c.get(0))
    }
}
