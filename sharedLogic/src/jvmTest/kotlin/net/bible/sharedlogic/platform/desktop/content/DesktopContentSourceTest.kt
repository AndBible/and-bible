package net.bible.sharedlogic.platform.desktop.content

import java.nio.file.Files
import kotlin.test.*
import okio.buffer

class DesktopContentSourceTest {
    @Test fun readsExactEncodedFileAndFallsBackToExtension() {
        val dir = Files.createTempDirectory("content-source")
        val file = dir.resolve("a \\ # %.MD")
        try {
            Files.writeString(file, "ä content")
            val source = DesktopContentSource { null }
            assertEquals("a \\ # %.MD", source.displayName(file.toUri().toString()))
            assertEquals("text/markdown", source.mimeType(file.toUri().toString()))
            assertEquals("ä content", source.openSource(file.toUri().toString())!!.buffer().use { it.readUtf8() })
            assertNull(source.openSource("https://example.org/a.md"))
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir) }
    }
    @Test fun probeWinsOverExtensionFallback() {
        assertEquals("application/custom", DesktopContentSource { "application/custom" }.mimeType("/tmp/a.csv"))
    }
}
