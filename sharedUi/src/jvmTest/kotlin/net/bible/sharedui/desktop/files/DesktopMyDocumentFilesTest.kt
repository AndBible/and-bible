package net.bible.sharedui.desktop.files

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.android.database.IdType
import net.bible.sharedui.app.ports.DocumentFolderExport
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import net.bible.sharedui.app.ports.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopMyDocumentFilesTest {
    private fun page(title: String, content: String) = MyDocumentPageWithContent(IdType(), IdType(), title, "key", MyDocumentContentType.MARKDOWN, 0, 0, 0, null, null, content)

    @Test fun repeatedExportsAndCollidingDocumentsPreserveContents() = runTest {
        val root = Files.createTempDirectory("documents")
        try {
            val port = DesktopMyDocumentFiles { "page" }
            val ref = DocumentRef(root.toUri().toString())
            Files.writeString(root.resolve("01-Page.md"), "sentinel")
            assertTrue(port.exportPagesToFolder(listOf(page("Page", "first")), ref))
            assertTrue(port.exportPagesToFolder(listOf(page("Page", "second")), ref))
            assertEquals("sentinel", Files.readString(root.resolve("01-Page.md")))
            assertEquals("first", Files.readString(root.resolve("01-Page (2).md")))
            assertEquals("second", Files.readString(root.resolve("01-Page (3).md")))
            assertTrue(port.exportDocumentsToFolder(listOf(DocumentFolderExport("Notes", listOf(page("Page", "one"))), DocumentFolderExport("Notes", listOf(page("Page", "two")))), ref))
            assertEquals("one", Files.readString(root.resolve("Notes/01-Page.md")))
            assertEquals("two", Files.readString(root.resolve("Notes (2)/01-Page.md")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun importSkipsUnreadableAndSortsExactNames() = runTest {
        val root = Files.createTempDirectory("documents")
        try {
            val a = Files.writeString(root.resolve("a \\ # %.md"), "first")
            val z = Files.writeString(root.resolve("z.md"), "last")
            val port = DesktopMyDocumentFiles { "page" }
            val refs = listOf(z, root.resolve("missing.md"), root, a).map { DocumentRef(it.toUri().toString()) }
            val files = port.readDocumentFiles(refs)
            assertEquals(listOf("a \\ # %.md", "z.md"), files.map { it.fileName })
            assertEquals(listOf("first", "last"), files.map { it.content })
            assertEquals(null, port.importPageFile(DocumentRef("https://example.org/a.md")))
            assertFalse(port.exportPagesToFolder(listOf(page("Page", "bad")), DocumentRef("https://example.org/")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun sanitisesAndLimitsPageTitles() = runTest {
        val root = Files.createTempDirectory("documents")
        try {
            val port = DesktopMyDocumentFiles { "page" }
            assertTrue(port.exportPagesToFolder(listOf(page("<>:/?*", "fallback"), page("A<>:/?*\"|\\\\._- B", "safe"), page("x".repeat(60), "long")), DocumentRef(root.toUri().toString())))
            assertEquals("fallback", Files.readString(root.resolve("01-page.md")))
            assertEquals("safe", Files.readString(root.resolve("02-A._- B.md")))
            assertEquals("long", Files.readString(root.resolve("03-${"x".repeat(50)}.md")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun blockedPageReportsFailureButWritesLaterPage() = runTest {
        val root = Files.createTempDirectory("documents")
        try {
            Files.createDirectory(root.resolve("01-Blocked.md"))
            val port = DesktopMyDocumentFiles { "page" }
            assertFalse(port.exportPagesToFolder(listOf(page("Blocked", "lost"), page("Later", "kept")), DocumentRef(root.toUri().toString())))
            assertEquals("kept", Files.readString(root.resolve("02-Later.md")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun symlinkPageCollisionReportsFailureAndContinues() = runTest {
        val root = Files.createTempDirectory("documents")
        try {
            val outside = Files.writeString(root.resolve("outside"), "sentinel")
            Files.createSymbolicLink(root.resolve("01-Blocked.md"), outside)
            val port = DesktopMyDocumentFiles { "page" }
            assertFalse(port.exportPagesToFolder(listOf(page("Blocked", "lost"), page("Later", "kept")), DocumentRef(root.toUri().toString())))
            assertEquals("sentinel", Files.readString(outside))
            assertEquals("kept", Files.readString(root.resolve("02-Later.md")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun symlinkFolderIsNotFollowed() = runTest {
        val root = Files.createTempDirectory("documents")
        try {
            val outside = Files.createDirectory(root.resolve("outside"))
            val picked = Files.createDirectory(root.resolve("picked"))
            Files.createSymbolicLink(picked.resolve("Notes"), outside)
            val port = DesktopMyDocumentFiles { "page" }
            assertFalse(port.exportDocumentsToFolder(listOf(DocumentFolderExport("Notes", listOf(page("Page", "bad"))), DocumentFolderExport("Later", listOf(page("Page", "kept")))), DocumentRef(picked.toUri().toString())))
            assertFalse(Files.exists(outside.resolve("01-Page.md")))
            assertEquals("kept", Files.readString(picked.resolve("Later/01-Page.md")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun importsUtf8HtmlWithTitleWithoutExtension() = runTest {
        val file = Files.createTempFile("andbible-notes.", ".HTML")
        try {
            Files.writeString(file, "<p>ä Ω</p>")
            val page = assertNotNull(DesktopMyDocumentFiles().importPageFile(DocumentRef(file.toUri().toString())))
            assertEquals(file.fileName.toString().removeSuffix(".HTML"), page.title)
            assertEquals(MyDocumentContentType.HTML, page.contentType)
            assertEquals("<p>ä Ω</p>", page.content)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
