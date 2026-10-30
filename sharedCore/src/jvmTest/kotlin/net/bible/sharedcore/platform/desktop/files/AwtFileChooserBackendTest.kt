package net.bible.sharedcore.platform.desktop.files

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AwtFileChooserBackendTest {
    /**
     * Found live on Windows (W1): without a directory, AWT's dialog opens in the process's working directory, which is
     * C:\Windows\System32 for an app started by the shell or a scheduled task. The first dialog opens in the user's
     * Documents (or home), later ones where the user last chose a file.
     */
    @Test fun initialDirectoryIsDocumentsThenTheLastUsedOne() {
        val home = Files.createTempDirectory("awt home ä")
        try {
            assertEquals(home, AwtFileChooserBackend.initialDirectory(null, home))
            val documents = Files.createDirectory(home.resolve("Documents"))
            assertEquals(documents, AwtFileChooserBackend.initialDirectory(null, home))
            val last = Files.createDirectory(home.resolve("backups"))
            assertEquals(last, AwtFileChooserBackend.initialDirectory(last, home))
            // A remembered directory that has gone away falls back to Documents again.
            Files.delete(last)
            assertEquals(documents, AwtFileChooserBackend.initialDirectory(last, home))
        } finally {
            home.toFile().deleteRecursively()
        }
    }
}
