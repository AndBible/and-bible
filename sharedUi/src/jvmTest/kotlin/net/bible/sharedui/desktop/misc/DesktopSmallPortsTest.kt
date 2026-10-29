package net.bible.sharedui.desktop.misc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.bible.sharedui.app.ports.DocumentRef
import okio.buffer
import java.nio.file.Files

class DesktopSmallPortsTest {
    @Test fun notificationPermissionAnswers() = runBlocking { DesktopNotificationPermission().request() }
    @Test fun systemScreensAnswer() { DesktopSystemScreens().openAppLinkSettings() }
    @Test fun debugActionsDisabledByDefault() {
        val actions = DesktopDebugActions()
        actions.crashAppDelayed()
        actions.showTestError("test")
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
