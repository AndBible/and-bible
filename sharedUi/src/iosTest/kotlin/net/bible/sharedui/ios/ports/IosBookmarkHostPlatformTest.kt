package net.bible.sharedui.ios.ports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.awaitStartupOnMain
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.iosStringTable
import okio.FileSystem
import okio.buffer
import okio.use
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.VerseRangeFactory
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL
import kotlin.test.*

/** Picker-fake evidence only: stream F's real document UI is intentionally not implemented here. */
class IosBookmarkHostPlatformTest {
    private val files = FileSystem.SYSTEM
    private val path = IosAssemblyTest.sharedRoot / "bookmark CSV # test.csv"
    private val ref get() = DocumentRef(NSURL.fileURLWithPath(path.toString()).absoluteString!!)
    private val notices = mutableListOf<String>()
    private val failures = mutableListOf<Throwable?>()
    private var language = "en"
    private val table get() = iosStringTable(language)
    private lateinit var control: BookmarkControl
    private lateinit var dialogs: AppDialogController
    private lateinit var platform: IosBookmarkHostPlatform
    private var importRef: DocumentRef? = null
    private var exportRef: DocumentRef? = null
    private val picker = object : DocumentPicker {
        override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? {
            assertTrue(Regex("bible_bookmarks_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}\\.csv").matches(suggestedName))
            assertEquals("text/csv", mimeType)
            return exportRef
        }
        override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef> {
            assertEquals(listOf("text/csv", "text/plain", "text/comma-separated-values"), mimeTypes)
            assertFalse(multiple)
            return listOfNotNull(importRef)
        }
        override suspend fun openDocumentTree(): DocumentRef? = null
    }

    @BeforeTest fun setUp() {
        runBlocking { IosAppStartup.resetForTest() }
        files.deleteRecursively(IosAssemblyTest.sharedRoot / IosAppConfig.DATABASES_DIR)
        awaitStartupOnMain { IosAppStartup.ensureStarted(IosAppConfig(root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
            repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore())) }
        awaitStartupOnMain { IosAppStartup.openDatabases() }
        val koin = KoinPlatform.getKoin()
        control = koin.get()
        dialogs = koin.get()
        platform = IosBookmarkHostPlatform({ control }, picker, koin.get(), dialogs, { table },
            object : UserNotifier {
                override fun showInfo(message: String, long: Boolean) { notices += message }
                override fun showError(message: String, cause: Throwable?) { notices += message; failures += cause }
            }, koin.get(), IosBookmarkCsvFiles()::openSink, ::iosBookmarkCsvTimestamp)
    }

    @AfterTest fun tearDown() {
        runBlocking { IosAppStartup.resetForTest() }
        files.deleteRecursively(IosAssemblyTest.sharedRoot / IosAppConfig.DATABASES_DIR)
        files.delete(path, mustExist = false)
    }

    private suspend fun export(bookmark: BibleBookmarkWithNotes) = kotlinx.coroutines.coroutineScope {
        val job = async(Dispatchers.Default) { platform.exportBookmarksCsv(listOf(bookmark)) }
        withTimeout(5000) { while (dialogs.pending.value == null) delay(1) }
        val shown = dialogs.pending.value!!
        val request = (shown.request as AppDialogRequest.Localized).resolve() as AppDialogRequest.MultiChoice
        assertEquals(18, request.options.size)
        dialogs.respond(shown.id, AppDialogResult.SelectedMany(request.options.map { it.value }))
        job.await()
    }

    @Test fun csvExportWritesThroughThePicker() = runBlocking {
        exportRef = ref
        val book = Books.installed().getBook("K4RawText") as AbstractPassageBook
        val label = control.insertOrUpdateLabel(Label(name = "CSV label").apply { new = true })
        val bookmark = control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
            verseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1-Gen.1.2"),
            textRange = null, wholeVerse = true, book = book,
        ).apply { notes = "first; line\n<second>"; customIcon = "star"; new = true }, setOf(label.id))
        export(bookmark)
        val csv = files.read(path) { readUtf8() }
        assertTrue(csv.startsWith("osisRef;bibleRef;document;book;"))
        assertTrue(csv.contains("K4RawText"))
        assertTrue(csv.contains("CSV label"))
        assertEquals(listOf(table.fmt("csv_export_success", 1)), notices)
        assertTrue(failures.isEmpty())

        control.deleteBookmark(bookmark)
        importRef = ref
        platform.importBookmarksCsv()
        val restored = assertNotNull(control.bibleBookmarkById(bookmark.id))
        assertEquals(bookmark.verseRange.getOsisRef(), restored.verseRange.getOsisRef())
        assertEquals("first; line\n<second>", restored.notes)
        assertEquals("star", restored.customIcon)
        assertEquals("K4RawText", restored.book?.initials)
        assertEquals(listOf("CSV label"), control.labelsForBookmark(restored).map { it.name })
        assertEquals(table.fmt("csv_import_success", 1, 0), notices.last())
    }

    @Test fun csvImportReadsThroughThePickerAndUpdatesExistingBookmark() = runBlocking {
        files.write(path) { writeUtf8("osisRef;notes;customIcon\nGen.1.1;imported;heart\n") }
        importRef = ref
        platform.importBookmarksCsv()
        val bookmark = control.allBibleBookmarks().single()
        assertEquals("Gen.1.1", bookmark.verseRange.getOsisRef())
        assertEquals("imported", bookmark.notes)
        assertEquals("heart", bookmark.customIcon)
        files.write(path) { writeUtf8("id;osisRef;notes\n${bookmark.id};Gen.1.1;updated\n") }
        platform.importBookmarksCsv()
        assertEquals("updated", control.allBibleBookmarks().single().notes)
        assertEquals(listOf(table.fmt("csv_import_success", 1, 0), table.fmt("csv_import_success", 0, 1)), notices)
    }

    @Test fun partialImportEscapesErrorsAndOpenSummaryTracksLanguage() = runBlocking {
        val ownedId = net.bible.android.database.IdType("bdf9d884-61d3-4d11-8d3b-32e20d452ae9")
        val beforeIds = control.allBibleBookmarks().map { it.id }.toSet()
        assertFalse(ownedId in beforeIds)
        // Invalid UUIDs intentionally fall back to a fresh ID; only an invalid reference makes this row fail.
        files.write(path) { writeUtf8("osisRef;id;notes\nGen.1.1;$ownedId;valid\n<bad-reference>;;invalid\n") }
        importRef = ref
        platform.importBookmarksCsv()
        assertEquals("valid", assertNotNull(control.bibleBookmarkById(ownedId)).notes)
        assertEquals(beforeIds + ownedId, control.allBibleBookmarks().map { it.id }.toSet())
        val request = dialogs.pending.value!!.request as AppDialogRequest.Localized
        val english = request.resolve() as AppDialogRequest.Message
        assertTrue(english.message.startsWith(net.bible.sharedcore.ui.dialog.plainTextToHtml(
            table.fmt("csv_import_errors", 1, 0, 1))))
        assertTrue(english.message.contains("<br>"))
        assertTrue(english.message.contains("Record 3: Invalid bookmark data"), english.message)
        assertFalse(english.message.contains("<bad>"))
        language = "fi"
        val finnish = request.resolve() as AppDialogRequest.Message
        assertEquals(table.fmt("import_items", "CSV"), finnish.title)
        assertNotEquals(english.message, finnish.message)
        assertTrue(notices.isEmpty())
    }

    @Test fun emptyAndMissingInputAreErrorsButPickerCancelIsSilent() = runBlocking {
        platform.importBookmarksCsv()
        assertTrue(notices.isEmpty())
        importRef = ref
        files.delete(path, mustExist = false)
        platform.importBookmarksCsv()
        assertEquals(1, failures.size)
        files.write(path) { writeUtf8("") }
        platform.importBookmarksCsv()
        assertEquals(2, failures.size)
        assertTrue(control.allBibleBookmarks().isEmpty())
    }

    @Test fun fileUrlAndPlainPathWritesDecodeSpacesAndRejectForeignSchemes() {
        val adapter = IosBookmarkCsvFiles()
        adapter.openSink(ref).buffer().use { it.writeUtf8("URL") }
        assertEquals("URL", files.read(path) { readUtf8() })
        adapter.openSink(DocumentRef(path.toString())).buffer().use { it.writeUtf8("path") }
        assertEquals("path", files.read(path) { readUtf8() })
        assertFailsWith<IllegalArgumentException> { adapter.openSink(DocumentRef("https://example.org/file.csv")) }
    }

    @Test fun labelIconForEveryKey() {
        assertEquals(33, IosLabelIcons().iconKeys.size)
        assertEquals(null, IosLabelIcons().iconKeys.last())
    }
}
