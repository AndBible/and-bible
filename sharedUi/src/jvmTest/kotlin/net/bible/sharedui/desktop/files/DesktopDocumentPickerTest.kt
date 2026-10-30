package net.bible.sharedui.desktop.files

import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.desktop.files.FileChooserBackend
import net.bible.sharedcore.platform.desktop.files.FileFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopDocumentPickerTest {
    @Test
    fun cancellationReturnsEmptyResults() = runTest {
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
            override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? = null
        }
        val picker = DesktopDocumentPicker(chooser)
        assertEquals(emptyList(), picker.openDocuments(listOf("text/csv"), true))
        assertNull(picker.createDocument("export.csv", "text/csv"))
        assertNull(picker.openDocumentTree())
    }

    @Test
    fun returnsEncodedFileUriForPortalPath() = runTest {
        val path = Path.of("/run/user/1000/doc/id/a b.csv")
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean) = listOf(path)
            override suspend fun save(suggestedName: String, filters: List<FileFilter>) = path
        }
        val picker = DesktopDocumentPicker(chooser)
        assertEquals(path.toUri().toString(), picker.openDocuments(listOf("text/csv"), false).single().uri)
        assertEquals(path.toUri().toString(), picker.createDocument("a b.csv", "text/csv")!!.uri)
    }
}
