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

    private val picker = object : DocumentPicker {
        override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean) = emptyList<DocumentRef>()
        override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? = null
        override suspend fun openDocumentTree(): DocumentRef? { treePicks++; return null }
    }

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
    )

    @Test
    fun lostExportIsReported() {
        // The picker of the previous host instance is gone: its result can never arrive, so the export is lost.
        val host = host(MyDocumentsRecreationState(documentIds = listOf("11", "12"), documentsBatch = true))
        assertEquals(listOf(strings.errorOccurred), infos)
        // Cleared once reported: a second recreation does not report it again.
        assertEquals(MyDocumentsRecreationState(), host.recreationSnapshot())
        host.restore(host.recreationSnapshot())
        assertEquals(1, infos.size)
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
