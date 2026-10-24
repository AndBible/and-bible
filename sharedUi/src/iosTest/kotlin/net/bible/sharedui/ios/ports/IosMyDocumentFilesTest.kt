@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.sharedui.app.ports.DocumentFolderExport
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.assembly.iosStringTable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.*
import kotlin.test.*

class IosMyDocumentFilesTest {
    @Test fun importTitleDropsTheExtension() = withTempFolder { dir ->
        val ref = write(dir / "Notes.part.md", "A UTF-8 page: ä Ω")
        val scopes = Scopes()
        val port = scopes.port()
        val page = assertNotNull(port.importPageFile(ref))
        assertEquals("Notes.part", page.title)
        assertEquals(MyDocumentContentType.MARKDOWN, page.contentType)
        assertEquals("A UTF-8 page: ä Ω", page.content)
        assertEquals("Notes.part.md", port.displayName(ref))
        scopes.assertBalanced(1)
    }

    @Test fun importPageReadsExactPickedBackslashFileNotNormalizedDecoy() = withExactUrlFixture { root ->
        val selected = NSURL.fileURLWithPath("$root/picked\\page.md")
        writeExact(selected, "picked page")
        val decoy = "$root/picked".toPath()
        FileSystem.SYSTEM.createDirectory(decoy)
        write(decoy / "page.md", "wrong normalized page")
        val scopes = Scopes()
        val imported = assertNotNull(scopes.port().importPageFile(DocumentRef(selected.absoluteString!!)))
        scopes.assertBalanced(1)
        assertEquals(listOf(selected.absoluteString), scopes.starts)
        assertEquals("picked\\page", imported.title)
        assertEquals("picked page", imported.content)
    }

    @Test fun readDocumentsReadsExactPickedBackslashFileNotNormalizedDecoy() = withExactUrlFixture { root ->
        val selected = NSURL.fileURLWithPath("$root/picked\\page.md")
        writeExact(selected, "picked document")
        val decoy = "$root/picked".toPath()
        FileSystem.SYSTEM.createDirectory(decoy)
        write(decoy / "page.md", "wrong normalized document")
        val scopes = Scopes()
        val imported = scopes.port().readDocumentFiles(listOf(DocumentRef(selected.absoluteString!!)))
        scopes.assertBalanced(1)
        assertEquals(listOf(selected.absoluteString), scopes.starts)
        assertEquals(listOf("picked\\page.md"), imported.map { it.fileName })
        assertEquals(listOf("picked document"), imported.map { it.content })
    }

    @Test fun pagesExportStaysInExactPickedBackslashDirectory() = withExactUrlFixture { root ->
        val selected = NSURL.fileURLWithPath("$root/picked\\folder", isDirectory = true)
        assertTrue(NSFileManager.defaultManager.createDirectoryAtURL(selected, true, null, null))
        val decoy = "$root/picked/folder".toPath()
        FileSystem.SYSTEM.createDirectories(decoy)
        val scopes = Scopes()
        assertTrue(scopes.port().exportPagesToFolder(listOf(page("Page", content = "picked export")),
            DocumentRef(selected.absoluteString!!)))
        scopes.assertBalanced(1)
        assertEquals(listOf(selected.absoluteString), scopes.starts)
        assertEquals(emptyList(), FileSystem.SYSTEM.list(decoy), "Normalized decoy must remain untouched")
        assertEquals("picked export", readExact(NSURL.fileURLWithPath("${selected.path}/01-Page.md")))
    }

    @Test fun documentsExportStaysInExactPickedBackslashDirectory() = withExactUrlFixture { root ->
        val selected = NSURL.fileURLWithPath("$root/picked\\folder", isDirectory = true)
        assertTrue(NSFileManager.defaultManager.createDirectoryAtURL(selected, true, null, null))
        val decoy = "$root/picked/folder".toPath()
        FileSystem.SYSTEM.createDirectories(decoy)
        val scopes = Scopes()
        assertTrue(scopes.port().exportDocumentsToFolder(listOf(
            DocumentFolderExport("Document", listOf(page("Page", content = "picked document export"))),
        ), DocumentRef(selected.absoluteString!!)))
        scopes.assertBalanced(1)
        assertEquals(listOf(selected.absoluteString), scopes.starts)
        assertEquals(emptyList(), FileSystem.SYSTEM.list(decoy), "Normalized decoy must remain untouched")
        assertEquals("picked document export", readExact(NSURL.fileURLWithPath("${selected.path}/Document/01-Page.md")))
    }

    @Test fun htmlExtensionsImportAsHtml() = withTempFolder { dir ->
        val scopes = Scopes()
        val port = scopes.port()
        for ((name, title) in listOf("PageHtml.html" to "PageHtml", "PageHtm.HTM" to "PageHtm", "PageUpper.HTML" to "PageUpper")) {
            val page = assertNotNull(port.importPageFile(write(dir / name, "<p>Text</p>")))
            assertEquals(title, page.title)
            assertEquals(MyDocumentContentType.HTML, page.contentType)
            assertEquals("<p>Text</p>", page.content)
        }
        val markdown = assertNotNull(port.importPageFile(write(dir / "Page.txt", "plain")))
        assertEquals(MyDocumentContentType.MARKDOWN, markdown.contentType)
        scopes.assertBalanced(4)
    }

    @Test fun exportNameIsSanitisedAndCapped() = withTempFolder { dir ->
        val scopes = Scopes()
        val port = scopes.port()
        assertTrue(port.exportPagesToFolder(listOf(
            page("A<>:/?*\"|\\._- B"),
            page("123456789012345678901234567890123456789012345678901234567890"),
        ), ref(dir)))
        assertEquals(listOf(
            "01-A._- B.md",
            "02-12345678901234567890123456789012345678901234567890.md",
        ), FileSystem.SYSTEM.list(dir).map { it.name }.sorted())
        scopes.assertBalanced(1)
    }

    @Test fun emptySanitisedTitleUsesLocalisedFallback() = withTempFolder { dir ->
        val scopes = Scopes()
        assertTrue(scopes.port().exportPagesToFolder(listOf(page("<>:/?*")), ref(dir)))
        assertEquals(listOf("01-page.md"), FileSystem.SYSTEM.list(dir).map { it.name })
        assertEquals("body", read(dir / "01-page.md"))
        scopes.assertBalanced(1)
    }

    @Test fun folderExportNumbersFiles() = withTempFolder { dir ->
        val scopes = Scopes()
        assertTrue(scopes.port().exportPagesToFolder(listOf(
            page("First", content = "ä first"),
            page("Second", MyDocumentContentType.HTML, "<p>second</p>"),
            page("Empty", content = null),
        ), ref(dir)))
        assertEquals(listOf("01-First.md", "02-Second.html", "03-Empty.md"),
            FileSystem.SYSTEM.list(dir).map { it.name }.sorted())
        assertEquals("ä first", read(dir / "01-First.md"))
        assertEquals("<p>second</p>", read(dir / "02-Second.html"))
        assertEquals("", read(dir / "03-Empty.md"))
        scopes.assertBalanced(1)
    }

    @Test fun unreadableFileIsSkipped() = withTempFolder { dir ->
        val scopes = Scopes()
        val directory = dir / "cannot-read-as-text.md"
        FileSystem.SYSTEM.createDirectory(directory)
        val files = scopes.port().readDocumentFiles(listOf(
            write(dir / "z.md", "last"),
            ref(dir / "missing.md"),
            ref(directory),
            write(dir / "a.md", "ä first"),
        ))
        assertEquals(listOf("a.md", "z.md"), files.map { it.fileName })
        assertEquals(listOf("ä first", "last"), files.map { it.content })
        scopes.assertBalanced(4)
    }

    @Test fun missingPageReturnsNullAndReleasesScope() = withTempFolder { dir ->
        val scopes = Scopes()
        assertNull(scopes.port().importPageFile(ref(dir / "missing.md")))
        scopes.assertBalanced(1)
    }

    @Test fun folderOpenFailureReturnsFalseAndReleasesScope() = withTempFolder { dir ->
        val scopes = Scopes()
        val notFolder = dir / "file.md"
        write(notFolder, "unchanged")
        val port = scopes.port()
        assertFalse(port.exportPagesToFolder(listOf(page("Not written")), ref(notFolder)))
        assertFalse(port.exportDocumentsToFolder(listOf(DocumentFolderExport("Doc", listOf(page("Not written")))), ref(notFolder)))
        assertFalse(port.exportPagesToFolder(listOf(page("Not written")), ref(dir / "missing")))
        assertEquals("unchanged", read(notFolder))
        assertEquals(listOf("file.md"), FileSystem.SYSTEM.list(dir).map { it.name })
        scopes.assertBalanced(3)
    }

    @Test fun existingPageFilesAndOccupiedSuffixSurviveRepeatedExports() = withTempFolder { dir ->
        write(dir / "01-Page.md", "unrelated sentinel")
        write(dir / "01-Page (2).md", "occupied suffix sentinel")
        val scopes = Scopes()
        val port = scopes.port()
        assertTrue(port.exportPagesToFolder(listOf(page("Page", content = "first export")), ref(dir)))
        assertEquals("unrelated sentinel", read(dir / "01-Page.md"))
        assertEquals("occupied suffix sentinel", read(dir / "01-Page (2).md"))
        assertTrue(port.exportPagesToFolder(listOf(page("Page", content = "second export")), ref(dir)))
        assertEquals("unrelated sentinel", read(dir / "01-Page.md"))
        assertEquals("occupied suffix sentinel", read(dir / "01-Page (2).md"))
        val outputs = FileSystem.SYSTEM.list(dir)
        assertEquals(4, outputs.size, "Each export must retain a separate file without clobbering existing bytes")
        assertEquals(listOf("first export", "occupied suffix sentinel", "second export", "unrelated sentinel"),
            outputs.map { read(it) }.sorted())
        scopes.assertBalanced(2)
    }

    @Test fun failedPageWriteDoesNotPreventLaterPages() = withTempFolder { dir ->
        // An existing directory at the target filename forces a real output-open failure.
        val blocked = dir / "01-Blocked.md"
        FileSystem.SYSTEM.createDirectory(blocked)
        val scopes = Scopes()
        assertTrue(scopes.port().exportPagesToFolder(listOf(page("Blocked"), page("Later", content = "kept")), ref(dir)))
        assertTrue(FileSystem.SYSTEM.metadata(blocked).isDirectory)
        assertEquals("kept", read(dir / "02-Later.md"))
        scopes.assertBalanced(1)
    }

    @Test fun documentsUseSubfoldersAndSkipFailedDirectoryCreation() = withTempFolder { dir ->
        write(dir / "Blocked", "not a directory")
        val scopes = Scopes()
        assertTrue(scopes.port().exportDocumentsToFolder(listOf(
            DocumentFolderExport("Blocked", listOf(page("Lost"))),
            DocumentFolderExport("First document", listOf(page("Page", content = "first"))),
            DocumentFolderExport("Second document", listOf(page("Page", content = "second"))),
        ), ref(dir)))
        assertEquals("not a directory", read(dir / "Blocked"))
        assertEquals("first", read(dir / "First document" / "01-Page.md"))
        assertEquals("second", read(dir / "Second document" / "01-Page.md"))
        scopes.assertBalanced(1)
    }

    @Test fun documentsWithCollidingFolderNamesKeepBothPageContents() = withTempFolder { dir ->
        val scopes = Scopes()
        // The screen sanitises both "Notes?" and "Notes!" to this same folder name.
        assertTrue(scopes.port().exportDocumentsToFolder(listOf(
            DocumentFolderExport("Notes", listOf(page("Page", content = "first document"))),
            DocumentFolderExport("Notes", listOf(page("Page", content = "second document"))),
        ), ref(dir)))
        val folders = FileSystem.SYSTEM.list(dir)
        assertEquals(2, folders.size)
        assertTrue(folders.all { FileSystem.SYSTEM.metadata(it).isDirectory })
        assertEquals(listOf("first document", "second document"), folders.map { read(it / "01-Page.md") }.sorted())
        scopes.assertBalanced(1)
    }

    @Test fun specialFolderSegmentsNeverWriteOutsideADocumentSubfolder() = withTempFolder { dir ->
        val root = dir / "picked"
        FileSystem.SYSTEM.createDirectory(root)
        val scopes = Scopes()
        assertTrue(scopes.port().exportDocumentsToFolder(listOf(
            DocumentFolderExport(".", listOf(page("Dot", content = "dot document"))),
            DocumentFolderExport("..", listOf(page("Parent", content = "parent document"))),
            DocumentFolderExport("Good", listOf(page("Page", content = "normal document"))),
        ), ref(root)))
        assertEquals(listOf(root), FileSystem.SYSTEM.list(dir), "Nothing may escape the picked folder")
        val entries = FileSystem.SYSTEM.list(root)
        assertTrue(entries.all { FileSystem.SYSTEM.metadata(it).isDirectory }, "Each exported page belongs to a document subfolder")
        assertEquals("normal document", read(root / "Good" / "01-Page.md"))
        scopes.assertBalanced(1)
    }

    @Test fun localFileAccessStillWorksWhenSecurityScopeIsNotGranted() = withTempFolder { dir ->
        val scopes = Scopes(granted = false)
        val file = write(dir / "local.md", "local content")
        assertEquals("local content", assertNotNull(scopes.port().importPageFile(file)).content)
        assertEquals(1, scopes.starts.size)
        assertEquals(emptyList(), scopes.stops)
    }

    @Test fun rejectsNonFileUrlsWithoutStartingSecurityScope() = withTempFolder { dir ->
        val scopes = Scopes()
        val port = scopes.port()
        val remote = DocumentRef("https://example.invalid/page.md")
        assertNull(port.importPageFile(remote))
        assertEquals(emptyList(), port.readDocumentFiles(listOf(remote)))
        assertFalse(port.exportPagesToFolder(listOf(page("Page")), remote))
        assertEquals(emptyList(), FileSystem.SYSTEM.list(dir))
        scopes.assertBalanced(0)
    }

    @Test fun cancellationIsNotConvertedToUnreadableFile() = withTempFolder { dir ->
        val port = IosMyDocumentFiles(startAccess = { throw CancellationException("cancelled") })
        val file = write(dir / "page.md", "text")
        assertFailsWith<CancellationException> { port.importPageFile(file) }
        assertFailsWith<CancellationException> { port.readDocumentFiles(listOf(file)) }
        assertFailsWith<CancellationException> { port.exportPagesToFolder(listOf(page("Page")), ref(dir)) }
    }

    private class Scopes(private val granted: Boolean = true) {
        val starts = mutableListOf<String>()
        val stops = mutableListOf<String>()
        private val active = mutableSetOf<String>()

        fun port() = IosMyDocumentFiles(
            strings = { iosStringTable("en") },
            startAccess = { url ->
                val uri = assertNotNull(url.absoluteString)
                assertTrue(url.isFileURL())
                starts += uri
                if (granted) assertTrue(active.add(uri), "Scope already active: $uri")
                granted
            },
            stopAccess = { url ->
                val uri = assertNotNull(url.absoluteString)
                assertTrue(active.remove(uri), "No matching scope: $uri")
                stops += uri
            },
        )

        fun assertBalanced(expected: Int) {
            assertEquals(expected, starts.size)
            assertEquals(starts, stops)
            assertTrue(active.isEmpty())
        }
    }

    private fun page(title: String, type: MyDocumentContentType = MyDocumentContentType.MARKDOWN, content: String? = "body") =
        MyDocumentPageWithContent(IdType(), IdType(), title, "key", type, 0, 0, 0, null, null, content)

    private fun ref(path: Path) = DocumentRef(assertNotNull(NSURL.fileURLWithPath(path.toString()).absoluteString))

    private fun write(path: Path, content: String): DocumentRef {
        FileSystem.SYSTEM.write(path) { writeUtf8(content) }
        return ref(path)
    }

    private fun read(path: Path) = FileSystem.SYSTEM.read(path) { readUtf8() }

    private fun writeExact(url: NSURL, content: String) {
        assertTrue(NSString.create(string = content).writeToURL(url, true, NSUTF8StringEncoding, null))
    }

    private fun readExact(url: NSURL) = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)

    private fun withExactUrlFixture(block: suspend (String) -> Unit) = runBlocking {
        val root = "${NSTemporaryDirectory()}/my-documents-exact-url-${NSUUID().UUIDString}"
        val url = NSURL.fileURLWithPath(root, isDirectory = true)
        assertTrue(NSFileManager.defaultManager.createDirectoryAtURL(url, true, null, null))
        var primary: Throwable? = null
        try { block(root) } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            // Foundation must remove literal backslash children; Okio would normalize their names.
            val removed = NSFileManager.defaultManager.removeItemAtURL(url, null)
            if (!removed) {
                val cleanup = AssertionError("Failed to remove exact URL fixture: $root")
                primary?.addSuppressed(cleanup) ?: throw cleanup
            }
        }
    }

    private fun withTempFolder(block: suspend (Path) -> Unit) = runBlocking {
        val dir = NSTemporaryDirectory().toPath() / "andbible-my-documents-${NSUUID().UUIDString}"
        FileSystem.SYSTEM.createDirectory(dir)
        try { block(dir) } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }
}
