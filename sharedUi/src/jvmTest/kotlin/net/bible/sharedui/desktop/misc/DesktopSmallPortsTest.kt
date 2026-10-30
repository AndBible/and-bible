package net.bible.sharedui.desktop.misc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.bible.sharedui.app.ports.DocumentRef
import okio.buffer
import java.nio.file.Files

object DesktopDebugCrashProbe {
    @JvmStatic fun main(args: Array<String>) {
        val root = java.nio.file.Paths.get(args[0])
        val tracker = DesktopCrashTracker(net.bible.sharedcore.platform.desktop.DesktopDeviceSettings(root.resolve("settings")), root.resolve("logs"))
        tracker.install()
        tracker.onLaunch()
        DesktopDebugActions(enabled = { true }).crashAppDelayed()
        Thread.sleep(5000)
    }
}

class DesktopSmallPortsTest {
    @Test fun notificationPermissionAnswers() = runBlocking { DesktopNotificationPermission().request() }
    @Test fun systemScreensAnswer() { DesktopSystemScreens().openAppLinkSettings() }
    @Test fun debugActionsDisabledByDefault() {
        val actions = DesktopDebugActions()
        actions.crashAppDelayed()
        actions.showTestError("test")
    }
    @Test fun debugCrashTerminatesProcessAndRecordsException() {
        val root = Files.createTempDirectory("debug-crash")
        val process = ProcessBuilder(
            java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
            "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
            "net.bible.sharedui.desktop.misc.DesktopDebugCrashProbe", root.toString(),
        ).redirectErrorStream(true).start()
        val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly().waitFor()
        val output = process.inputStream.bufferedReader().readText()
        kotlin.test.assertTrue(finished, "debug crash did not terminate: $output")
        kotlin.test.assertTrue(process.exitValue() != 0, "debug crash exited successfully: $output")
        kotlin.test.assertTrue(Files.readString(root.resolve("logs/last_crash.txt")).contains("Deliberate desktop debug crash"))
    }

    @Test fun csvAcceptsRelativeAndWindowsStylePaths() {
        val root = java.nio.file.Paths.get("").toAbsolutePath()
        val folder = Files.createTempDirectory(root, "csv-relative-")
        try {
            val relative = root.relativize(folder.resolve("relative file.csv"))
            DesktopBookmarkCsvFiles().openSink(DocumentRef(relative.toString()))!!.buffer().use { it.writeUtf8("relative") }
            assertEquals("relative", Files.readString(root.resolve(relative)))
            // On Linux, Windows path spellings are legal file names; Windows resolves them natively.
            if (java.io.File.separatorChar == '/') {
                for (name in listOf("C:\\exports\\bookmarks.csv", "\\\\server\\share\\bookmarks.csv")) {
                    val ref = DocumentRef(name)
                    DesktopBookmarkCsvFiles().openSink(ref)!!.buffer().use { it.writeUtf8("windows") }
                    try { assertEquals("windows", Files.readString(root.resolve(name))) }
                    finally { Files.deleteIfExists(root.resolve(name)) }
                }
            }
        } finally {
            Files.deleteIfExists(folder.resolve("relative file.csv"))
            Files.deleteIfExists(folder)
        }
    }

    @Test fun csvSinkWritesLocalFileWithPunctuation() {
        val path = Files.createTempDirectory("csv").resolve("a b,#.csv")
        val sink = DesktopBookmarkCsvFiles().openSink(DocumentRef(path.toUri().toString()))!!
        sink.buffer().use { it.writeUtf8("a,b\n1,2\n") }
        assertEquals("a,b\n1,2\n", Files.readString(path))
        DesktopBookmarkCsvFiles().openSink(DocumentRef(path.toString()))!!.buffer().use { it.writeUtf8("plain") }
        assertEquals("plain", Files.readString(path))
        assertNull(DesktopBookmarkCsvFiles().openSink(DocumentRef("https://example.org/a.csv")))
        assertNull(DesktopBookmarkCsvFiles().openSink(DocumentRef(path.resolve("missing").toString())))
    }
}
