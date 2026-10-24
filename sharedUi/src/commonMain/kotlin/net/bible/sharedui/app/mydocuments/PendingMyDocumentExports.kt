@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.mydocuments

import kotlinx.serialization.Serializable

/**
 * What of the My Documents host survives `recreate()` and process death: the export that is waiting for the
 * folder picker. See [PendingMyDocumentExports] for why the ids are persistent ones.
 */
@Serializable
@kotlin.native.HiddenFromObjC
data class MyDocumentsRecreationState(
    val documentIds: List<String> = emptyList(),
    val pageIds: List<String> = emptyList(),
    /** True when [documentIds] is a multi-select export (one subfolder per document); false for a single document. */
    val documentsBatch: Boolean = false,
    /** True while the documents import's file picker was open. */
    val importPicking: Boolean = false,
)

/**
 * What the My Documents / pages export is waiting for while the system folder picker is open.
 *
 * Holds PERSISTENT document/page ids (their [net.bible.android.database.IdType] string form), never
 * the session-local `Long` tokens of the list controllers: those tokens belong to a session that is
 * rebuilt when the host is recreated (e.g. a day/night switch while the picker is up), so a
 * token-based pending target silently resolved to nothing and the export was dropped.
 * Survives recreation through [snapshot]/[restoreFrom].
 */
@kotlin.native.HiddenFromObjC
class PendingMyDocumentExports {
    var documentIds: List<String> = emptyList()
    var pageIds: List<String> = emptyList()

    /** True when [documentIds] is a multi-select export (one subfolder per document); false for a single document. */
    var documentsBatch: Boolean = false

    /** True when an export is waiting for a folder. */
    val isPending: Boolean get() = documentIds.isNotEmpty() || pageIds.isNotEmpty()

    /** Returns and clears the pending document ids. */
    fun takeDocumentIds(): List<String> = documentIds.also { documentIds = emptyList() }

    /** Returns and clears the pending page ids. */
    fun takePageIds(): List<String> = pageIds.also { pageIds = emptyList() }

    fun snapshot() = MyDocumentsRecreationState(documentIds, pageIds, documentsBatch)

    fun restoreFrom(state: MyDocumentsRecreationState?) {
        documentIds = state?.documentIds.orEmpty()
        pageIds = state?.pageIds.orEmpty()
        documentsBatch = state?.documentsBatch ?: false
    }
}
