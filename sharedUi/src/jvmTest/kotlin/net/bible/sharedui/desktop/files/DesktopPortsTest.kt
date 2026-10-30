@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.desktop.files

import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.nio.file.Files
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import net.bible.sharedui.app.ports.DocumentRef
import org.junit.Assume.assumeFalse
import kotlin.test.*
import org.crosswire.jsword.book.Books
import net.bible.sharedcore.platform.desktop.dbus.PortalOpenUri
import net.bible.sharedcore.platform.desktop.dbus.PortalOpenResult
import java.util.UUID

class DesktopPortsTest {
    @Test fun clipboardRoundTripWithLocalClipboard() {
        val local = Clipboard("local")
        val port = DesktopClipboardText { local }
        port.write("label", "ä text")
        assertEquals("ä text", port.read())
    }
    @Test fun systemClipboardRoundTrip() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val previous = clipboard.getContents(null)
        try {
            val port = DesktopClipboardText()
            port.write("label", "clipboard test ä")
            assertEquals("clipboard test ä", port.read())
        } finally { if (previous != null) clipboard.setContents(previous, null) }
    }
    @Test fun internalLinksNeverLeaveApp() = runTest {
        val opened = mutableListOf<String>()
        val links = DesktopExternalLinks({ opened += "portal"; PortalOpenResult.OPENED }, { opened += "desktop"; true }, { opened += "xdg"; true }, this)
        listOf("andbible://foo", "file:///etc/passwd", "javascript:alert(1)").forEach(links::open)
        assertTrue(opened.isEmpty())
    }
    @Test fun httpsOpensViaPortalThenDesktopThenXdgOpen() = runTest {
        val opened = mutableListOf<String>()
        val links = DesktopExternalLinks({ opened += "portal:$it"; PortalOpenResult.UNAVAILABLE }, { opened += "desktop:$it"; false }, { opened += "xdg:$it"; true }, this)
        links.open("https://example.org/path?q=1")
        runCurrent()
        assertEquals(listOf("portal:https://example.org/path?q=1", "desktop:https://example.org/path?q=1", "xdg:https://example.org/path?q=1"), opened)
    }
    @Test fun portalResponseCodesRespectCancellation() = runTest {
        for ((code, expected) in listOf(0 to emptyList(), 1 to emptyList(), 2 to listOf("desktop", "xdg"))) {
            val calls = mutableListOf<String>()
            val portal = PortalOpenUri(testResponse = { code })
            val links = DesktopExternalLinks({ portal.open(it) }, { calls += "desktop"; false }, { calls += "xdg"; true }, this)
            links.open("https://example.org/")
            runCurrent()
            assertEquals(expected, calls, "response $code")
        }
    }

    @Test fun successfulPortalPreventsFallback() = runTest {
        val calls = mutableListOf<String>()
        val links = DesktopExternalLinks({ calls += "portal:$it"; PortalOpenResult.OPENED }, { calls += "desktop"; true }, { calls += "xdg"; true }, this)
        links.open("https://example.org/")
        runCurrent()
        assertEquals(listOf("portal:https://example.org/"), calls)
    }

    @Test fun mailtoOpensMailClient() = runTest {
        val opened = mutableListOf<String>()
        val links = DesktopExternalLinks({ PortalOpenResult.UNAVAILABLE }, { opened += it.toString(); true }, { error("unexpected fallback") }, this)
        links.open("mailto:user@example.org?subject=Hi")
        runCurrent()
        assertEquals(listOf("mailto:user@example.org?subject=Hi"), opened)
    }
    @Test fun promptAddonSelfImportPreservesContentAndRegisters() = runTest {
        val root = Files.createTempDirectory("prompt-addon")
        val prompts = Files.createDirectories(root.resolve("prompts"))
        val name = "test_${UUID.randomUUID().toString().replace("-", "") }"
        val source = prompts.resolve("$name.csv")
        val registry = Books.installed()
        var cleared = 0
        try {
            Files.writeString(source, "name,prompt\nTest,ä\n")
            DesktopPromptFiles(modulesDirectory = { root }, clearCaches = { cleared++ }).installAsAddon(DocumentRef(source.toUri().toString()))
            assertEquals("name,prompt\nTest,ä\n", Files.readString(source))
            assertNotNull(registry.getBook("Prompts_$name"))
            assertEquals(1, cleared)
        } finally {
            registry.getBook("Prompts_$name")?.let { registry.removeBook(it) }
            root.toFile().deleteRecursively()
        }
    }

    @Test fun promptAddonRejectsSymlinkDirectoryAndDestination() = runTest {
        val root = Files.createTempDirectory("prompt-addon")
        try {
            val outside = Files.createDirectory(root.resolve("outside"))
            val modules = Files.createDirectory(root.resolve("modules"))
            val source = Files.writeString(root.resolve("pack.csv"), "data")
            val port = DesktopPromptFiles(modulesDirectory = { modules }, clearCaches = { error("must not clear") })
            val ref = DocumentRef(source.toUri().toString())
            val prompts = modules.resolve("prompts")
            Files.createSymbolicLink(prompts, outside)
            assertFailsWith<IllegalArgumentException> { port.installAsAddon(ref) }
            Files.delete(prompts)
            Files.createDirectory(prompts)
            Files.createSymbolicLink(prompts.resolve("pack.csv"), outside.resolve("target"))
            assertFailsWith<IllegalArgumentException> { port.installAsAddon(ref) }
            assertFalse(Files.exists(outside.resolve("target")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun promptRoundTripPreservesExactFileUri() = runTest {
        val dir = Files.createTempDirectory("andbible-prompt")
        val file = dir.resolve("prompts \\ # %.csv")
        try {
            val port = DesktopPromptFiles(now = { LocalDateTime.of(2025, 1, 2, 3, 4) })
            val ref = DocumentRef(file.toUri().toString())
            port.write(ref, "ä,Ω\n")
            assertEquals("ä,Ω\n", port.read(ref))
            assertEquals("ai_prompts_2025-01-02_03-04.csv", port.suggestedExportName())
            assertFailsWith<IllegalArgumentException> { port.read(DocumentRef("https://example.org/a.csv")) }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir) }
    }

    @Test fun externalLinksUseDesktopBrowseOnWindows() = runTest {
        val calls = mutableListOf<String>()
        val links = DesktopExternalLinks({ calls += "portal"; PortalOpenResult.OPENED }, { calls += "desktop:$it"; true }, { calls += "xdg"; true }, this, "Windows 11")
        links.open("https://example.org/a b".replace(" ", "%20"))
        links.open("mailto:user@example.org")
        runCurrent()
        assertEquals(listOf("desktop:https://example.org/a%20b", "desktop:mailto:user@example.org"), calls)

        // Even when Desktop.browse fails there is no xdg-open on Windows.
        calls.clear()
        DesktopExternalLinks({ calls += "portal"; PortalOpenResult.OPENED }, { calls += "desktop"; false }, { calls += "xdg"; true }, this, "Windows 11")
            .open("https://example.org/")
        runCurrent()
        assertEquals(listOf("desktop"), calls)

        // PortalOpenUri itself never touches D-Bus on Windows.
        val dbus = net.bible.sharedcore.platform.desktop.dbus.DesktopDbus("Windows 11")
        assertFalse(dbus.supportedOs)
        assertFalse(dbus.portalAvailable())
        assertEquals(PortalOpenResult.UNAVAILABLE, PortalOpenUri(dbus).open(java.net.URI("https://example.org/")))
    }
}
