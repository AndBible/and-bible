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

package net.bible.service.common

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.GenBookBackend
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.book.BookMetaData
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key

/**
 * Previous/next over general books (#3875, #3561, #3184): a key-list backend (EPUB, MyDocument) and a tree backend
 * (RawGenBook) step the same way, wrap at both ends and treat an unknown key alike.
 */
class AdjacentGeneralKeyTest {
    private val cleanups = mutableListOf<() -> Unit>()

    @AfterTest
    fun cleanUp() = cleanups.reversed().forEach { it() }

    private class ListState(val metadata: SwordBookMetaData) : OpenFileState {
        override fun close() = Unit
        override fun getBookMetaData(): BookMetaData = metadata
        override fun releaseResources() = Unit
        override fun getLastAccess(): Long = 0L
        override fun setLastAccess(lastAccess: Long) = Unit
    }

    /** Like the EPUB and MyDocument backends: an indexed page list that throws IndexOutOfBoundsException. */
    private class ListBackend(private val metadata: SwordBookMetaData, val pages: List<String>) :
        AbstractKeyBackend<ListState>(metadata) {
        override fun initState() = ListState(metadata)
        override fun getCardinality() = pages.size
        override fun get(index: Int): Key = DefaultLeafKeyList(pages[index], pages[index])
        override fun indexOf(that: Key?): Int = pages.indexOf(that!!.getOsisRef())
        override fun readRawContent(state: ListState, key: Key?) = ""
    }

    private fun listBook(vararg pages: String): SwordGenBook {
        val metadata = SwordBookMetaData("[ListBook]\nModDrv=RawGenBook\nDescription=List\n".toByteArray(), "list.conf")
        return SwordGenBook(metadata, ListBackend(metadata, pages.toList()))
    }

    private class Node(val name: String, val parent: Int, val sibling: Int, val child: Int, val text: String?)

    /** A RawGenBook on disk: root -> (A -> (A1, A2)), B. Pointers are byte offsets into the .idx file. */
    private fun treeBook(): SwordGenBook {
        val dir = Files.createTempDirectory("adjacent-genbook").toFile()
        cleanups += { dir.deleteRecursively() }
        val nodes = listOf(
            Node("", -1, -1, 4, null),
            Node("A", 0, 16, 8, "a"),
            Node("A1", 4, 12, -1, "a1"),
            Node("A2", 4, -1, -1, "a2"),
            Node("B", 0, -1, -1, "b"),
        )
        val bdt = StringBuilder()
        val dat = java.io.ByteArrayOutputStream()
        val idx = ByteBuffer.allocate(4 * nodes.size).order(ByteOrder.LITTLE_ENDIAN)
        for (node in nodes) {
            idx.putInt(dat.size())
            val name = node.name.toByteArray()
            val userData = node.text?.let { 8 } ?: 0
            val bytes = ByteBuffer.allocate(12 + name.size + 1 + 2 + userData).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(node.parent).putInt(node.sibling).putInt(node.child).put(name).put(0.toByte())
                .putShort(userData.toShort())
            node.text?.let { bytes.putInt(bdt.length).putInt(it.length); bdt.append(it) }
            dat.write(bytes.array())
        }
        val path = File(dir, "tree").path
        File("$path.idx").writeBytes(idx.array())
        File("$path.dat").writeBytes(dat.toByteArray())
        File("$path.bdt").writeBytes(bdt.toString().toByteArray())
        val metadata = SwordBookMetaData(
            "[TreeBook]\nModDrv=RawGenBook\nDescription=Tree\nDataPath=$path\nEncoding=UTF-8\n".toByteArray(), "tree.conf",
        ).apply { library = File("/").toURI() }
        return SwordGenBook(metadata, GenBookBackend(metadata)).also { book -> cleanups += { Activator.deactivate(book) } }
    }

    private fun Pair<Key, Boolean>.name() = first.getOsisRef() to second

    @Test
    fun keyListBackendStepsAndWrapsAtBothEnds() {
        val book = listBook("p1", "p2", "p3")
        fun key(name: String) = DefaultLeafKeyList(name, name)
        assertEquals("p2" to false, book.adjacentGeneralKey(key("p1"), 1).name())
        assertEquals("p1" to false, book.adjacentGeneralKey(key("p2"), -1).name())
        assertEquals("p1" to true, book.adjacentGeneralKey(key("p3"), 1).name(), "forward wraps to the first page")
        assertEquals("p3" to true, book.adjacentGeneralKey(key("p1"), -1).name(), "backward wraps to the last page")
        assertEquals("p1" to false, book.adjacentGeneralKey(key("gone"), 1).name(), "unknown key: forward starts at the first")
        assertEquals("p3", book.adjacentGeneralKey(key("gone"), -1).first.getOsisRef(), "unknown key: backward goes to the last")
    }

    @Test
    fun emptyKeyListBackendStaysOnTheKey() {
        val key = DefaultLeafKeyList("gone", "gone")
        assertSame(key, listBook().adjacentGeneralKey(key, 1).first)
        assertSame(key, listBook().adjacentGeneralKey(key, -1).first)
    }

    @Test
    fun treeBackendWalksDescendantsInOrderAndWrapsAtBothEnds() {
        val book = treeBook()
        val keys = book.getGlobalKeyList().toList()
        val names = keys.map { it.getOsisRef() }
        // The index starts with the (unnamed) root, as it always has.
        assertEquals(listOf("", "A", "A/A1", "A/A2", "B"), names)
        assertEquals(names, (book.backend as GenBookBackend).readIndex().toList().map { it.getOsisRef() },
            "The global list is the index the old code re-read on every step")

        for (i in 0 until keys.size - 1) {
            assertEquals(names[i + 1] to false, book.adjacentGeneralKey(keys[i], 1).name())
            assertEquals(names[i] to false, book.adjacentGeneralKey(keys[i + 1], -1).name())
        }
        assertEquals("" to true, book.adjacentGeneralKey(keys.last(), 1).name(), "forward wraps to the first key")
        assertEquals("B" to true, book.adjacentGeneralKey(keys.first(), -1).name(), "backward wraps to the last key")
        assertEquals("A/A2" to false, book.adjacentGeneralKey(book.getKey("A/A1"), 1).name(), "a looked-up key is found")
    }

    @Test
    fun treeBackendUnknownKeyGoesToAnEndWithoutWrapping() {
        val book = treeBook()
        val unknown = DefaultLeafKeyList("Nope", "Nope")
        assertEquals("" to false, book.adjacentGeneralKey(unknown, 1).name())
        assertEquals("B" to false, book.adjacentGeneralKey(unknown, -1).name())
    }

    @Test
    fun onlyUnitStepsAreSupported() {
        assertFailsWith<IllegalArgumentException> { listBook("p1").adjacentGeneralKey(DefaultLeafKeyList("p1", "p1"), 2) }
    }
}
