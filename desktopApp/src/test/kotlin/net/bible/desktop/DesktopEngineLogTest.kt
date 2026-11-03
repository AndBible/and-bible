package net.bible.desktop

import net.bible.sharedcore.log.DesktopLogFile
import net.bible.sharedcore.log.configureDesktopLogFile
import net.bible.sharedcore.log.recentLogSnapshot
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopEngineLogTest {
    @AfterTest fun closeLog() = DesktopLogFile.close()

    @Test
    fun `engine selection line reaches the recent log snapshot used by bug reports`() {
        val line = "desktop renderer: DIRECT3D; engine: WEBVIEW2 [snapshot-test]"
        desktopEngineLog(line)
        assertTrue(recentLogSnapshot().contains(line), "snapshot lacks the engine line")
    }

    @Test
    fun `engine selection line reaches andbible log`() {
        val dir = Files.createTempDirectory("engine-log-test")
        try {
            configureDesktopLogFile(dir)
            desktopEngineLog("webview2 unavailable: boom; falling back to KCEF")
            val text = Files.readString(dir.resolve(DesktopLogFile.FILE_NAME))
            assertTrue(text.contains("DesktopEngine: webview2 unavailable: boom; falling back to KCEF"), text)
        } finally {
            DesktopLogFile.close()
            dir.toFile().deleteRecursively()
        }
    }
}
