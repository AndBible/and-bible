package net.bible.sharedui.app.download

import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanReadFileTest {
    @Test
    fun missingFileIsNotReadable() {
        assertFalse(canReadFile(File("/nonexistent-dir/none.json").toOkioPath()))
    }

    @Test
    fun existingReadableFileIsReadable() {
        val f = File.createTempFile("canread", ".json").apply { deleteOnExit() }
        assertTrue(canReadFile(f.toOkioPath()))
    }

    @Test
    fun existingButUnreadableFileIsNotReadable() {
        val f = File.createTempFile("canread", ".json").apply { deleteOnExit() }
        f.setReadable(false)
        if (f.canRead()) return   // running as root: permissions are ignored, nothing to assert
        assertFalse(canReadFile(f.toOkioPath()))
    }
}
