package net.bible.service.common

import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempFilesTest {
    private val fs = FileSystem.SYSTEM

    private fun withDir(block: (okio.Path) -> Unit) {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "tempfilestest-${System.nanoTime()}"
        fs.createDirectories(dir)
        try { block(dir) } finally { fs.deleteRecursively(dir) }
    }

    @Test fun createsEmptyUniqueFilesWithPrefixAndSuffix() = withDir { dir ->
        val files = (1..100).map { createTempFile(dir, "andbible-", ".tmp") }
        assertEquals(100, files.toSet().size)
        files.forEach {
            assertEquals(0L, fs.metadata(it).size)
            assertTrue(it.name, Regex("^andbible-\\d+\\.tmp$").matches(it.name))
            assertEquals(dir, it.parent)
        }
    }

    @Test fun nullSuffixMeansDotTmp() = withDir { dir ->
        val f = createTempFile(dir, "webdav-secret")
        assertTrue(f.name, Regex("^webdav-secret\\d+\\.tmp$").matches(f.name))
    }
}
