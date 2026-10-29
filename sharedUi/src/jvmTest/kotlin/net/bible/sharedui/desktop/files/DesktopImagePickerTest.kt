package net.bible.sharedui.desktop.files

import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.desktop.files.FileChooserBackend
import net.bible.sharedcore.platform.desktop.files.FileFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopImagePickerTest {
    @Test
    fun cancelledImagePickReturnsNull() = runTest {
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
            override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? = null
        }
        assertNull(DesktopImagePicker(chooser).pickImage())
    }

    @Test
    fun imagePickReturnsFileUri() = runTest {
        val path = Path.of("/run/user/1000/doc/id/image.png")
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean) = listOf(path)
            override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? = null
        }
        assertEquals(path.toUri().toString(), DesktopImagePicker(chooser).pickImage())
    }
}
