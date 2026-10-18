package net.bible.sharedui.app.mydocuments

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentFolderExport
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ImportedFile
import net.bible.sharedui.app.ports.ImportedPage
import net.bible.sharedui.app.ports.MyDocumentFiles
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyDocumentsHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job() + Dispatchers.Unconfined)
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private val strings = iosStrings("en")
    private val infos = mutableListOf<String>()
    private var treePicks = 0

    /** What the redelivered pick of the previous host instance answers; null = it was cancelled. */
    private var resumedTree: DocumentRef? = null
    private var resumedDocuments: List<DocumentRef> = emptyList()
    private var resumeTreeCalls = 0
    private var suspendResume = false

    private val picker = object : DocumentPicker {
        override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean) = emptyList<DocumentRef>()
        override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? = null
        override suspend fun openDocumentTree(): DocumentRef? { treePicks++; return null }
        override suspend fun resumeDocumentTree(): DocumentRef? {
            resumeTreeCalls++
            if (suspendResume) kotlinx.coroutines.awaitCancellation()
            return resumedTree
        }
        override suspend fun resumeOpenDocuments(): List<DocumentRef> = resumedDocuments
    }

    private class Export(val ids: List<String>, val pages: Boolean, val batch: Boolean, val tree: DocumentRef)
    private val exports = mutableListOf<Export>()

    private val files = object : MyDocumentFiles {
        override suspend fun importPageFile(ref: DocumentRef): ImportedPage? = null
        override suspend fun readDocumentFiles(refs: List<DocumentRef>) = emptyList<ImportedFile>()
        override suspend fun exportPage(page: net.bible.android.database.mydocument.MyDocumentPageWithContent) = Unit
        override suspend fun exportPagesToFolder(pages: List<net.bible.android.database.mydocument.MyDocumentPageWithContent>, tree: DocumentRef) = true
        override suspend fun exportDocumentsToFolder(documents: List<DocumentFolderExport>, tree: DocumentRef) = true
        override fun displayName(ref: DocumentRef): String? = null
    }

    private fun host(restored: MyDocumentsRecreationState? = null) = MyDocumentsHost(
        shell = shell,
        strings = strings,
        documentPicker = picker,
        files = files,
        userNotifierProvider = {
            object : UserNotifier {
                override fun showInfo(message: String, long: Boolean) { infos += message }
                override fun showError(message: String, cause: Throwable?) { infos += message }
            }
        },
        restoredDocumentsSearch = { null },
        restoredPages = { null },
        consumeRestoredPages = {},
        restored = restored,
        exportSeam = { ids, pages, batch, tree -> exports += Export(ids, pages, batch, tree) },
    )

    @Test
    fun restoredPendingExportCompletesWhenTheFolderArrives() {
        // The folder pick that was open at recreation is redelivered: the export runs, nothing is reported.
        resumedTree = DocumentRef("content://tree")
        val host = host(MyDocumentsRecreationState(documentIds = listOf("11", "12"), documentsBatch = true))
        assertEquals(1, exports.size)
        assertEquals(listOf("11", "12"), exports.single().ids)
        assertTrue(exports.single().batch)
        assertEquals(DocumentRef("content://tree"), exports.single().tree)
        assertTrue(infos.isEmpty())
        // Taken: a second recreation does not export it again.
        assertEquals(MyDocumentsRecreationState(), host.recreationSnapshot())
    }

    @Test
    fun restoredPendingExportCancelledClearsSilently() {
        resumedTree = null
        val host = host(MyDocumentsRecreationState(pageIds = listOf("p1")))
        assertTrue(exports.isEmpty())
        assertTrue(infos.isEmpty())
        assertEquals(MyDocumentsRecreationState(), host.recreationSnapshot())
    }

    @Test
    fun folderWithNoSurvivingIdsIsReportedOnce() {
        val host = host()
        host.completeExportPick(ticket = 0, tree = DocumentRef("content://tree"))
        assertEquals(listOf(strings.errorOccurred), infos)
        assertTrue(exports.isEmpty())
        // A cancelled folder with nothing pending stays silent.
        host.completeExportPick(ticket = 0, tree = null)
        assertEquals(1, infos.size)
    }

    @Test
    fun restoredImportPickShowsTheNamePrompt() {
        resumedDocuments = listOf(DocumentRef("content://a.md"))
        val host = host(MyDocumentsRecreationState(importPicking = true))
        assertEquals(strings.newDocumentName(1), host.myDocumentImportNamePrompt.value)
        assertEquals(MyDocumentsRecreationState(), host.recreationSnapshot())
    }

    @Test
    fun cancelledRestoredImportPickShowsNothing() {
        val host = host(MyDocumentsRecreationState(importPicking = true))
        assertEquals(null, host.myDocumentImportNamePrompt.value)
        assertEquals(MyDocumentsRecreationState(), host.recreationSnapshot())
    }

    @Test
    fun restoreWhileAPickIsLiveIsANoOp() {
        resumeTreeCalls = 0
        suspendResume = true   // the redelivered pick has not arrived: the host stays pending
        val host = host(MyDocumentsRecreationState(pageIds = listOf("p1")))
        assertEquals(1, resumeTreeCalls)
        host.restore(MyDocumentsRecreationState(pageIds = listOf("p1")))   // a live pick is open: must not resume it again
        assertEquals(1, resumeTreeCalls)
        assertEquals(listOf("p1"), host.recreationSnapshot().pageIds)
    }

    @Test
    fun nothingPendingReportsNothing() {
        host()
        host(MyDocumentsRecreationState())
        assertTrue(infos.isEmpty())
    }

    @Test
    fun exportWithoutResolvableTargetIsReportedAndNoPickerOpens() {
        val host = host()
        // No session is loaded, so no token resolves to a persistent id.
        host.launchMyDocumentsExport(listOf(5L), batch = true)
        host.launchMyDocumentPagesExport(listOf(1L))
        assertEquals(listOf(strings.errorOccurred, strings.errorOccurred), infos)
        assertEquals(0, treePicks)
        assertEquals(MyDocumentsRecreationState(), host.recreationSnapshot())
    }
}
