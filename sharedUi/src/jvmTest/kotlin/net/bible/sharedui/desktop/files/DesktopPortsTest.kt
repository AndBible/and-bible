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
        val links = DesktopExternalLinks({ opened += "portal"; true }, { opened += "desktop"; true }, { opened += "xdg"; true }, this)
        listOf("andbible://foo", "file:///etc/passwd", "javascript:alert(1)").forEach(links::open)
        assertTrue(opened.isEmpty())
    }
    @Test fun httpsOpensViaPortalThenDesktopThenXdgOpen() = runTest {
        val opened = mutableListOf<String>()
        val links = DesktopExternalLinks({ opened += "portal:$it"; false }, { opened += "desktop:$it"; false }, { opened += "xdg:$it"; true }, this)
        links.open("https://example.org/path?q=1")
        runCurrent()
        assertEquals(listOf("portal:https://example.org/path?q=1", "desktop:https://example.org/path?q=1", "xdg:https://example.org/path?q=1"), opened)
    }
    @Test fun successfulPortalPreventsFallback() = runTest {
        val calls = mutableListOf<String>()
        val links = DesktopExternalLinks({ calls += "portal:$it"; true }, { calls += "desktop"; true }, { calls += "xdg"; true }, this)
        links.open("https://example.org/")
        runCurrent()
        assertEquals(listOf("portal:https://example.org/"), calls)
    }

    @Test fun mailtoOpensMailClient() = runTest {
        val opened = mutableListOf<String>()
        val links = DesktopExternalLinks({ false }, { opened += it.toString(); true }, { error("unexpected fallback") }, this)
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
}
