package net.bible.service.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeZipPathsTest {
    @Test fun rejectsParentTraversal() {
        assertNull(safeZipEntryPath("../x"))
        assertNull(safeZipEntryPath("..\\x"))
        assertNull(safeZipEntryPath("a/../../x"))
        assertNull(safeZipEntryPath("a/b/.."))
        assertNull(safeZipEntryPath("a/../b"))
        assertNull(safeZipEntryPath(".."))
        assertNull(safeZipEntryPath("a/.."))
    }

    @Test fun rejectsAbsoluteNames() {
        assertNull(safeZipEntryPath("/etc/x"))
        assertNull(safeZipEntryPath("\\etc\\x"))
        assertNull(safeZipEntryPath("C:/x"))
        assertNull(safeZipEntryPath("c:\\x"))
    }

    @Test fun acceptsAndNormalizesSafeNames() {
        assertEquals("mods.d/kjv.conf", safeZipEntryPath("mods.d/kjv.conf"))
        assertEquals("a/b.txt", safeZipEntryPath("a\\b.txt"))
        assertEquals("a", safeZipEntryPath("./a"))
        assertEquals("a/b", safeZipEntryPath("a//b"))
        assertEquals("a/..b/c..", safeZipEntryPath("a/..b/c.."))
        assertEquals("..hidden", safeZipEntryPath("..hidden"))
        assertEquals("a..b", safeZipEntryPath("a..b"))
        assertEquals("foo..", safeZipEntryPath("foo.."))
    }

    @Test fun emptyAndDotNamesNormalizeToEmptyNotNull() {
        assertEquals("", safeZipEntryPath(""))
        assertEquals("", safeZipEntryPath("."))
        assertEquals("", safeZipEntryPath("./"))
    }
}
