package net.bible.sharedui.app.bookmark

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.KJVA
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.reading.host.slots.MapSearchSettings
import net.bible.sharedui.strings.keyed.StringTable
import org.crosswire.jsword.passage.VerseRangeFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

private fun iosStringTable(language: String) = StringTable(language, { key -> "$language:$key" },
    { key, args -> "$language:$key:${args.joinToString()}" }, { n, _ -> n }, { emptyList() })

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BookmarkCsvHostFlowTest {
    private val settings = MapSearchSettings()
    private val dialogs = AppDialogController()
    private var table: StringTable = iosStringTable("en")
    private val messages = mutableListOf<String>()
    private val errors = mutableListOf<Throwable?>()
    private var creates = 0
    private var selectedName: String? = null
    private var selectedTypes: List<String>? = null
    private var create: suspend () -> DocumentRef? = { null }
    private var input: DocumentRef? = null
    private var control: () -> net.bible.android.control.bookmark.BookmarkControl = { error("No codec or database access expected") }
    private var source: (DocumentRef) -> okio.Source? = { error("Unexpected read") }
    private var sink: (DocumentRef) -> okio.Sink? = { throw IllegalStateException("<disk full>") }
    private val picker = object : DocumentPicker {
        override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? {
            assertEquals("text/csv", mimeType)
            creates++
            selectedName = suggestedName
            return create()
        }
        override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef> {
            assertFalse(multiple)
            selectedTypes = mimeTypes
            return listOfNotNull(input)
        }
        override suspend fun openDocumentTree(): DocumentRef? = null
    }
    private fun flow() = BookmarkCsvHostFlow(
        bookmarkControl = { control() },
        picker = picker, settings = settings, dialogs = dialogs, table = { table },
        notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) { messages += message }
            override fun showError(message: String, cause: Throwable?) { messages += message; errors += cause }
        },
        openSource = { source(it) }, openSink = { sink(it) },
        timestamp = { "2026-10-19_14-05" },
    )
    private fun bookmarks() = listOf(BibleBookmarkWithNotes(
        verseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1"), textRange = null, wholeVerse = true, book = null,
    ))
    private fun choice(): AppDialogRequest.MultiChoice =
        (dialogs.pending.value!!.request as AppDialogRequest.Localized).resolve() as AppDialogRequest.MultiChoice

    @Test fun emptyExportShowsNoticeWithoutPicker() = runTest {
        flow().exportBookmarksCsv(emptyList())
        assertEquals(listOf(table.text("no_bookmarks_to_export")), messages)
        assertEquals(0, creates)
        assertNull(dialogs.pending.value)
    }

    @Test fun chooserUsesLiveLanguageAndPersistsInverseSelectionBeforePickerCancellation() = runTest {
        settings.setStringSet("csv_export_unchecked_columns", setOf("notes"))
        val export = async { flow().exportBookmarksCsv(bookmarks()) }
        runCurrent()
        val english = choice()
        assertEquals(18, english.options.size)
        assertFalse("notes" in english.selectedIds)
        assertTrue("osisRef" in english.selectedIds)
        table = iosStringTable("fi")
        val finnish = choice()
        assertEquals(table.text("csv_column_selection_title"), finnish.title)
        assertNotEquals(english.title, finnish.title)
        assertEquals(english.options.map { it.value }, finnish.options.map { it.value })
        assertEquals(english.selectedIds, finnish.selectedIds)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("osisRef", "notes")))
        export.await()
        assertEquals("bible_bookmarks_2026-10-19_14-05.csv", selectedName)
        assertEquals(16, settings.getStringSet("csv_export_unchecked_columns").size)
        assertFalse("notes" in settings.getStringSet("csv_export_unchecked_columns"))
        assertTrue(messages.isEmpty())
    }

    @Test fun chooserCancelPersistsUncheckedColumnsWithoutOpeningPicker() = runTest {
        val export = async { flow().exportBookmarksCsv(bookmarks()) }
        runCurrent()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        export.await()
        assertEquals(18, settings.getStringSet("csv_export_unchecked_columns").size)
        assertEquals(0, creates)
    }

    @Test fun sinkFailureShowsLocalizedErrorAndOriginalCause() = runTest {
        create = { DocumentRef("file:///not-a-real-file") }
        val export = async { flow().exportBookmarksCsv(bookmarks()) }
        runCurrent()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("osisRef")))
        export.await()
        assertEquals(listOf(table.fmt("csv_export_failed", "<disk full>")), messages)
        assertEquals("<disk full>", errors.single()?.message)
    }

    @Test fun pickerCancellationPropagatesWithoutErrorNotice() = runTest {
        create = { throw CancellationException("host disposed") }
        val export = async { flow().exportBookmarksCsv(bookmarks()) }
        runCurrent()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("osisRef")))
        assertFailsWith<CancellationException> { export.await() }
        assertTrue(messages.isEmpty())
    }

    @Test fun cancellationAfterOpeningOutputClosesSinkExactlyOnceWithoutErrorNotice() = runTest {
        create = { DocumentRef("fake:output") }
        var closes = 0
        sink = { object : okio.ForwardingSink(okio.Buffer()) {
            override fun close() { closes++; super.close() }
        } }
        control = { throw CancellationException("disposed after opening") }
        val export = async { flow().exportBookmarksCsv(bookmarks()) }
        runCurrent()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("osisRef")))
        assertFailsWith<CancellationException> { export.await() }
        assertEquals(1, closes)
        assertTrue(messages.isEmpty())
    }

    @Test fun failureAfterOpeningInputClosesSourceExactlyOnceAndReportsCause() = runTest {
        input = DocumentRef("fake:input")
        var closes = 0
        source = { object : okio.ForwardingSource(okio.Buffer().writeUtf8("osisRef\\nGen.1.1\\n")) {
            override fun close() { closes++; super.close() }
        } }
        val cause = IllegalStateException("control unavailable")
        control = { throw cause }
        flow().importBookmarksCsv()
        assertEquals(1, closes)
        assertTrue(generateSequence(errors.single()) { it.cause }.any { it === cause },
            "coroutine stack recovery may wrap the exception but must retain the original cause")
    }

    @Test fun importCancellationAcceptsAllThreeAndroidMimeTypesAndDoesNotRead() = runTest {
        flow().importBookmarksCsv()
        assertEquals(listOf("text/csv", "text/plain", "text/comma-separated-values"), selectedTypes)
        assertTrue(messages.isEmpty())
    }
}
