/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.runBlocking
import net.bible.sharedlogic.addons.SharedAddonCatalog
import net.bible.sharedlogic.addons.SharedAddonScanner
import net.bible.sharedlogic.addons.SharedPromptPackSource
import net.bible.sharedui.app.ports.DocumentRef
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Books
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSTimeZone
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosPromptFilesTest {
    @Test fun suggestedExportNameFormat() {
        val port = IosPromptFiles(
            now = { NSDate.dateWithTimeIntervalSince1970(1735787040.0) },
            timeZone = { NSTimeZone.timeZoneForSecondsFromGMT(0) },
        )
        assertEquals("ai_prompts_2025-01-02_03-04.csv", port.suggestedExportName())
    }

    @Test fun defaultConstructorReadsDecodedFileUrlAsUtf8() = withTempFolder { dir ->
        val source = dir / "prompts with spaces # percent %.csv"
        write(source, CSV)
        assertEquals(CSV, IosPromptFiles().read(ref(source)))
    }

    @Test fun readPreservesLiteralBackslashInSourceUrl() = withLiteralFile(CSV) { url ->
        val source = DocumentRef(url.absoluteString!!)
        val scopes = Scopes(source)
        assertEquals(CSV, scopes.port().read(source))
        scopes.assertBalanced()
    }

    @Test fun writePreservesLiteralBackslashInDestinationUrl() = withLiteralFile("old content") { url ->
        val destination = DocumentRef(url.absoluteString!!)
        val scopes = Scopes(destination)
        scopes.port().write(destination, CSV)
        assertEquals(CSV, NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null))
        scopes.assertBalanced()
    }

    @Test fun writeReplacesUtf8ContentInsideSecurityScope() = withTempFolder { dir ->
        val target = dir / "export # %.csv"
        write(target, "old content that must not survive")
        val scopes = Scopes(ref(target))
        scopes.onStop = { assertEquals(CSV, read(target)) }
        val port = scopes.port()
        port.write(ref(target), CSV)
        assertEquals(CSV, read(target))
        scopes.assertBalanced()
    }

    @Test fun unreadableRefThrowsIllegalArgument() = withTempFolder { dir ->
        val missing = ref(dir / "missing.csv")
        val scopes = Scopes(missing)
        assertFailsWith<IllegalArgumentException> { scopes.port().read(missing) }
        scopes.assertBalanced()
    }

    @Test fun readingDirectoryThrowsIllegalArgumentAndReleasesScope() = withTempFolder { dir ->
        val scopes = Scopes(ref(dir))
        assertFailsWith<IllegalArgumentException> { scopes.port().read(ref(dir)) }
        scopes.assertBalanced()
    }

    @Test fun writeFailureThrowsIllegalArgumentAndReleasesScope() = withTempFolder { dir ->
        val missingParent = ref(dir / "absent" / "export.csv")
        val scopes = Scopes(missingParent)
        assertFailsWith<IllegalArgumentException> { scopes.port().write(missingParent, CSV) }
        scopes.assertBalanced()
        assertFalse(FileSystem.SYSTEM.exists(dir / "absent"))
    }

    @Test fun nonFileRefsAreRejectedBeforeSecurityScope() = runBlocking {
        val refs = listOf(DocumentRef("https://example.invalid/prompts.csv"), DocumentRef("not a file URL"))
        for (ref in refs) {
            val port = IosPromptFiles(
                startAccess = { error("Invalid reference must not acquire security scope") },
                stopAccess = { error("Invalid reference must not release security scope") },
            )
            assertFailsWith<IllegalArgumentException> { port.read(ref) }
            assertFailsWith<IllegalArgumentException> { port.write(ref, CSV) }
            assertFailsWith<IllegalArgumentException> { port.installAsAddon(ref) }
        }
    }

    @Test fun sandboxFileRemainsReadableWhenNoSecurityScopeIsGranted() = withTempFolder { dir ->
        val source = dir / "local.csv"
        write(source, CSV)
        val port = IosPromptFiles(
            startAccess = { false },
            stopAccess = { error("Must not release an unacquired scope") },
        )
        assertEquals(CSV, port.read(ref(source)))
        port.write(ref(source), "replacement")
        assertEquals("replacement", read(source))
    }

    @Test fun installAsAddonCopiesAndRegisters() = withTempFolder { dir ->
        val packName = "task4_${NSUUID().UUIDString}"
        val source = dir / "external # %" / "$packName.csv"
        write(source, CSV)
        val modules = dir / "modules"
        val installed = modules / "prompts" / "$packName.csv"
        val initials = "Prompts_$packName"
        val registry = Books.installed()
        assertEquals(null, registry.getBook(initials))
        // Use the same real scanner/catalog relationship as the iOS bindings, with a private cache.
        val scanner = SharedAddonScanner(books = { registry.getBooks() })
        val packs = SharedPromptPackSource(scanner)
        val catalog = SharedAddonCatalog(scanner, packs)
        assertFalse(packs.packs.any { it.moduleName == initials }) // prime the stale book cache
        var reloads = 0
        packs.reloaded.subscribe { reloads++ }
        val scopes = Scopes(ref(source))
        scopes.onStop = { assertEquals(CSV, read(installed)) }
        val port = scopes.port(modulesDirectory = { modules }, addonCatalog = { catalog })
        try {
            port.installAsAddon(ref(source))
            assertEquals(CSV, read(installed))
            assertEquals(CSV, read(source), "Import must leave the external source intact")
            val book = assertNotNull(registry.getBook(initials), "The real CSV prompt book must be registered")
            assertEquals("$packName.csv", book.bookMetaData.getProperty("AndBibleProvidesPrompts"))
            val pack = packs.packs.single { it.moduleName == initials }
            assertEquals(installed.toString(), pack.path)
            FileSystem.SYSTEM.delete(source)
            assertEquals(CSV, read(pack.path.toPath()), "The registered pack must use the sandbox copy")
            assertEquals(1, reloads, "Import must notify the real prompt source, not only clear its scanner")
            scopes.assertBalanced()
        } finally {
            // Only this test's unique book is ours; never replace global factories or clear other books.
            registry.getBook(initials)?.let { registry.removeBook(it) }
            catalog.clearCaches()
        }
    }

    @Test fun failedInstallDoesNotPublishPackOrNotifyReload() = withTempFolder { dir ->
        val scanner = SharedAddonScanner(books = { Books.installed().getBooks() })
        val packs = SharedPromptPackSource(scanner)
        val catalog = SharedAddonCatalog(scanner, packs)
        val before = packs.packs
        var reloads = 0
        packs.reloaded.subscribe { reloads++ }
        val missing = ref(dir / "missing.csv")
        val scopes = Scopes(missing)
        assertFailsWith<IllegalArgumentException> {
            scopes.port(modulesDirectory = { dir / "modules" }, addonCatalog = { catalog }).installAsAddon(missing)
        }
        assertEquals(before, packs.packs)
        assertEquals(0, reloads)
        scopes.assertBalanced()
    }

    @Test fun selfImportPreservesTheWholeCsv() = withTempFolder { dir ->
        val name = "task4_self_${NSUUID().UUIDString}"
        val modules = dir / "modules"
        val source = modules / "prompts" / "$name.csv"
        write(source, CSV)
        withCatalog { catalog ->
            try {
                IosPromptFiles(modulesDirectory = { modules }, addonCatalog = { catalog }).installAsAddon(ref(source))
                assertEquals(CSV, read(source))
                assertNotNull(Books.installed().getBook("Prompts_$name"))
            } finally {
                Books.installed().getBook("Prompts_$name")?.let { Books.installed().removeBook(it) }
            }
        }
    }

    @Test fun encodedSeparatorInFileNameCannotEscapePromptsDirectory() = withTempFolder { dir ->
        // A literal backslash is a valid POSIX filename but must never become a destination separator.
        val name = "task4_safe_${NSUUID().UUIDString}"
        val external = dir / "external"
        FileSystem.SYSTEM.createDirectories(external)
        val sourceUrl = NSURL.fileURLWithPath("$external/..\\$name.csv")
        val source = DocumentRef(sourceUrl.absoluteString!!)
        val modules = dir / "modules"
        // Do not let Okio normalize a literal POSIX backslash into a path separator in the fixture.
        assertTrue(NSString.create(string = CSV).writeToURL(sourceUrl, true, NSUTF8StringEncoding, null))
        var primary: Throwable? = null
        try {
            withCatalog { catalog ->
                try {
                    IosPromptFiles(modulesDirectory = { modules }, addonCatalog = { catalog }).installAsAddon(source)
                    val installed = FileSystem.SYSTEM.list(modules / "prompts").single()
                    assertEquals(".._$name.csv", installed.name)
                    assertEquals(CSV, read(installed))
                    assertFalse(FileSystem.SYSTEM.exists(modules / "$name.csv"))
                } finally {
                    Books.installed().getBook("Prompts_.._$name")?.let { Books.installed().removeBook(it) }
                }
            }
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                assertTrue(NSFileManager.defaultManager.removeItemAtURL(sourceUrl, null), "Remove the literal source URL")
            } catch (cleanup: Throwable) {
                val failure = primary
                if (failure == null) throw cleanup
                failure.addSuppressed(cleanup)
                // Okio cannot enumerate this literal backslash name; retain the real body failure if cleanup failed.
                NSFileManager.defaultManager.removeItemAtURL(NSURL.fileURLWithPath(external.toString()), null)
            }
        }
    }

    @Test fun symlinkedPromptsDirectoryIsRejectedWithoutOutsideWrites() = withTempFolder { dir ->
        val source = dir / "source.csv"
        val outside = dir / "outside"
        val modules = dir / "modules"
        write(source, CSV)
        FileSystem.SYSTEM.createDirectories(outside)
        FileSystem.SYSTEM.createDirectories(modules)
        FileSystem.SYSTEM.createSymlink(modules / "prompts", outside)
        val scopes = Scopes(ref(source))
        assertFailsWith<IllegalArgumentException> {
            scopes.port(modulesDirectory = { modules }).installAsAddon(ref(source))
        }
        assertTrue(FileSystem.SYSTEM.list(outside).isEmpty())
        scopes.assertBalanced()
    }

    @Test fun symlinkedDestinationIsRejectedWithoutClobberingOutsideFile() = withTempFolder { dir ->
        val source = dir / "source.csv"
        val outside = dir / "outside.csv"
        val modules = dir / "modules"
        write(source, CSV)
        write(outside, "keep outside")
        FileSystem.SYSTEM.createDirectories(modules / "prompts")
        FileSystem.SYSTEM.createSymlink(modules / "prompts" / "source.csv", outside)
        val scopes = Scopes(ref(source))
        assertFailsWith<IllegalArgumentException> {
            scopes.port(modulesDirectory = { modules }).installAsAddon(ref(source))
        }
        assertEquals("keep outside", read(outside))
        scopes.assertBalanced()
    }

    private suspend fun withCatalog(block: suspend (SharedAddonCatalog) -> Unit) {
        val scanner = SharedAddonScanner(books = { Books.installed().getBooks() })
        val catalog = SharedAddonCatalog(scanner, SharedPromptPackSource(scanner))
        try {
            block(catalog)
        } finally {
            catalog.clearCaches()
        }
    }

    private class Scopes(private val expected: DocumentRef) {
        private var starts = 0
        private var stops = 0
        var onStop: () -> Unit = {}

        fun port(
            modulesDirectory: () -> Path = { error("This operation must not need the module directory") },
            addonCatalog: () -> SharedAddonCatalog = { error("This operation must not need the addon catalog") },
        ) = IosPromptFiles(
            modulesDirectory = modulesDirectory,
            addonCatalog = addonCatalog,
            startAccess = {
                assertEquals(expected.uri, it.absoluteString)
                starts++
                true
            },
            stopAccess = {
                assertEquals(expected.uri, it.absoluteString)
                stops++
                onStop()
            },
        )

        fun assertBalanced() {
            assertEquals(1, starts)
            assertEquals(1, stops)
        }
    }

    private fun withLiteralFile(text: String, block: suspend (NSURL) -> Unit) = withTempFolder { dir ->
        val url = NSURL.fileURLWithPath("$dir/literal\\name.csv")
        assertTrue(NSString.create(string = text).writeToURL(url, true, NSUTF8StringEncoding, null))
        var primary: Throwable? = null
        try {
            block(url)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                assertTrue(NSFileManager.defaultManager.removeItemAtURL(url, null))
            } catch (cleanup: Throwable) {
                val failure = primary
                if (failure == null) throw cleanup
                failure.addSuppressed(cleanup)
            }
        }
    }

    private fun withTempFolder(block: suspend (Path) -> Unit) = runBlocking {
        val dir = NSTemporaryDirectory().toPath() / "IosPromptFilesTest-${NSUUID().UUIDString}"
        FileSystem.SYSTEM.createDirectories(dir)
        var primary: Throwable? = null
        try {
            block(dir)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                FileSystem.SYSTEM.deleteRecursively(dir)
            } catch (cleanup: Throwable) {
                val failure = primary
                if (failure == null) throw cleanup
                failure.addSuppressed(cleanup)
            }
        }
    }

    private fun ref(path: Path) = DocumentRef(NSURL.fileURLWithPath(path.toString()).absoluteString!!)

    private fun write(path: Path, text: String) {
        FileSystem.SYSTEM.createDirectories(path.parent!!)
        FileSystem.SYSTEM.write(path) { writeUtf8(text) }
    }

    private fun read(path: Path) = FileSystem.SYSTEM.read(path) { readUtf8() }

    private companion object {
        const val CSV = "name,prompt\nStudy,\"Explain café and Ω\"\n"
    }
}
