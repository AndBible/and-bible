package net.bible.sharedcore.platform.desktop.files

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopFileFiltersTest {
    @Test
    fun backupCsvAndImageFormatsAreSelectable() {
        val backup = fileFiltersFor(listOf("application/zip"))
        assertTrue(backup.any { "*.abk" in it.globs && "application/zip" in it.mimeTypes })
        val csv = fileFiltersFor(listOf("text/csv"))
        assertTrue(csv.any { "*.csv" in it.globs && "text/csv" in it.mimeTypes })
        val image = fileFiltersFor(listOf("image/*"))
        assertTrue(image.any { "*.png" in it.globs && "image/*" in it.mimeTypes })
    }
}
