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
package net.bible.android.control.page

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentaryBlockResolverTest {
    private val v11n = Versifications.instance().getVersification("KJV")

    /** Build consecutive John 1 verses 1..count. */
    private fun johnVerses(count: Int): List<Verse> =
        (1..count).map { Verse(v11n, BibleBook.JOHN, 1, it) }

    /** Fake walker: ordered verse list + content map; index-based traversal. */
    private class FakeWalker(
        private val verses: List<Verse>,
        private val content: Map<Verse, String?>,
    ) : CommentaryWalker {
        override fun next(verse: Verse): Verse? {
            val i = verses.indexOf(verse)
            return if (i in 0 until verses.lastIndex) verses[i + 1] else null
        }
        override fun prev(verse: Verse): Verse? {
            val i = verses.indexOf(verse)
            return if (i > 0) verses[i - 1] else null
        }
        override fun render(verse: Verse): String? = content[verse]
    }

    private fun resolver(verses: List<Verse>, content: Map<Verse, String?>) =
        CommentaryBlockResolver(FakeWalker(verses, content))

    @Test
    fun `resolveBlock expands from the middle to start and end`() {
        val vs = johnVerses(7)
        val content = mapOf(
            vs[0] to "A", vs[1] to "A", vs[2] to "A", vs[3] to "A", vs[4] to "A",
            vs[5] to null, vs[6] to "B",
        )
        val block = resolver(vs, content).resolveBlock(vs[2])
        assertEquals(vs[0], block.start)
        assertEquals(vs[4], block.end)
        assertEquals("A", block.content)
    }

    @Test
    fun `resolveBlock on an empty verse returns that verse with null content`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to null, vs[2] to "B")
        val block = resolver(vs, content).resolveBlock(vs[1])
        assertEquals(vs[1], block.start)
        assertEquals(vs[1], block.end)
        assertNull(block.content)
    }

    @Test
    fun `nextBlockStart skips empty verses to the next non-empty block`() {
        val vs = johnVerses(7)
        val content = mapOf(
            vs[0] to "A", vs[1] to "A", vs[2] to "A", vs[3] to "A", vs[4] to "A",
            vs[5] to null, vs[6] to "B",
        )
        val r = resolver(vs, content)
        assertEquals(vs[6], r.nextBlockStart(vs[4]))
    }

    @Test
    fun `prevBlockStart returns the start of the previous block`() {
        val vs = johnVerses(7)
        val content = mapOf(
            vs[0] to "A", vs[1] to "A", vs[2] to "A", vs[3] to "A", vs[4] to "A",
            vs[5] to null, vs[6] to "B",
        )
        val r = resolver(vs, content)
        assertEquals(vs[0], r.prevBlockStart(vs[6]))
    }

    @Test
    fun `single-verse blocks resolve to themselves`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to "B", vs[2] to "C")
        val r = resolver(vs, content)
        val block = r.resolveBlock(vs[1])
        assertEquals(vs[1], block.start)
        assertEquals(vs[1], block.end)
        assertEquals(vs[2], r.nextBlockStart(vs[1]))
        assertEquals(vs[0], r.prevBlockStart(vs[1]))
    }

    @Test
    fun `nextBlockStart returns null at the end`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to "B", vs[2] to "C")
        assertNull(resolver(vs, content).nextBlockStart(vs[2]))
    }

    @Test
    fun `prevBlockStart returns null at the start`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to "B", vs[2] to "C")
        assertNull(resolver(vs, content).prevBlockStart(vs[0]))
    }

    @Test
    fun `all-empty input yields no navigation targets`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to null, vs[1] to null, vs[2] to null)
        val r = resolver(vs, content)
        assertNull(r.nextBlockStart(vs[0]))
        assertNull(r.prevBlockStart(vs[2]))
    }

    @Test
    fun `identical content across a chapter boundary collapses into one block`() {
        val v1 = Verse(v11n, BibleBook.JOHN, 1, 7)
        val v2 = Verse(v11n, BibleBook.JOHN, 2, 1)
        val v3 = Verse(v11n, BibleBook.JOHN, 2, 2)
        val vs = listOf(v1, v2, v3)
        val content = mapOf(v1 to "X", v2 to "X", v3 to "Y")
        val block = resolver(vs, content).resolveBlock(v2)
        assertEquals(v1, block.start)
        assertEquals(v2, block.end)
        assertEquals("X", block.content)
    }

    /** Walker whose render throws for designated verses (e.g. a "verse 0" the module cannot render). */
    private class ThrowingWalker(
        private val verses: List<Verse>,
        private val content: Map<Verse, String?>,
        private val throwOn: Set<Verse>,
    ) : CommentaryWalker {
        override fun next(verse: Verse): Verse? {
            val i = verses.indexOf(verse)
            return if (i in 0 until verses.lastIndex) verses[i + 1] else null
        }
        override fun prev(verse: Verse): Verse? {
            val i = verses.indexOf(verse)
            return if (i > 0) verses[i - 1] else null
        }
        override fun render(verse: Verse): String? {
            if (verse in throwOn) throw RuntimeException("$verse not found in document")
            return content[verse]
        }
    }

    @Test
    fun `a verse that throws while rendering is treated as a separator, not a crash`() {
        // Regression: walking onto an unrenderable verse (e.g. a chapter-intro "verse 0") used to
        // propagate DocumentNotFound and crash navigation. It must be treated as an empty separator.
        val vs = johnVerses(4)
        val content = mapOf(vs[0] to "A", vs[2] to "B", vs[3] to "B")
        val r = CommentaryBlockResolver(ThrowingWalker(vs, content, throwOn = setOf(vs[1])))

        // resolveBlock from the throwing verse yields an empty block (no crash, no snapping).
        val thrown = r.resolveBlock(vs[1])
        assertEquals(vs[1], thrown.start)
        assertEquals(vs[1], thrown.end)
        assertNull(thrown.content)

        // The throwing verse separates the "A" block from the "B" block.
        val blockA = r.resolveBlock(vs[0])
        assertEquals(vs[0], blockA.start)
        assertEquals(vs[0], blockA.end)

        // Navigation skips the throwing verse to the next renderable block.
        assertEquals(vs[2], r.nextBlockStart(vs[0]))
        assertEquals(vs[0], r.prevBlockStart(vs[2]))
    }
}
