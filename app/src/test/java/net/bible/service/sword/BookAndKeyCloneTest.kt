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
