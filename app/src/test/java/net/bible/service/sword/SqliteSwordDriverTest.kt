package net.bible.service.sword

import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [isWritableFile] must answer what `File.canWrite()` answered for the module database file. */
class SqliteSwordDriverTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writableFileMatchesFileCanWrite() {
        val f = tmp.newFile("w.sqlite3").apply { writeText("x") }
        assertEquals(f.canWrite(), isWritableFile(f.absolutePath.toPath()))
    }

    @Test fun readOnlyFileMatchesFileCanWrite() {
        val f = tmp.newFile("r.sqlite3").apply { writeText("x"); setWritable(false) }
        assertEquals(f.canWrite(), isWritableFile(f.absolutePath.toPath()))
    }

    @Test fun isWritableDoesNotChangeTheFile() {
        val f = tmp.newFile("c.sqlite3").apply { writeText("content") }
        isWritableFile(f.absolutePath.toPath())
        assertEquals("content", f.readText())
    }

    @Test fun missingFileIsNotWritableAndIsNotCreated() {
        val f = java.io.File(tmp.root, "none.sqlite3")
        assertFalse(isWritableFile(f.absolutePath.toPath()))
        assertFalse(f.exists())
    }
}
