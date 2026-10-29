package net.bible.sharedui.host.ports

import kotlin.test.Test
import kotlin.test.assertEquals

class PathNamesTest {
    @Test
    fun lastComponentOfCommonPaths() {
        assertEquals("b.zip", lastPathComponent("/a/b.zip"))
        assertEquals("b", lastPathComponent("/a/b/"))
        assertEquals("b", lastPathComponent("/a/b///"))
        assertEquals("b", lastPathComponent("a//b"))
        assertEquals("name", lastPathComponent("name"))
    }

    @Test
    fun rootAndEmpty() {
        assertEquals("/", lastPathComponent("/"))
        assertEquals("/", lastPathComponent("///"))
        assertEquals("", lastPathComponent(""))
    }

    @Test
    fun decodedAndUnicodeNamesAreKeptLiterally() {
        assertEquals("Raamattu 1938 (fi).zip", lastPathComponent("/tmp/Raamattu 1938 (fi).zip"))
        assertEquals("päivä%20x\\y.epub", lastPathComponent("/Users/Ääkkönen/päivä%20x\\y.epub"))
        assertEquals("..", lastPathComponent("/a/.."))
    }
}
