@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

@kotlin.native.HiddenFromObjC
interface DocumentInstallStarter {
    /** Hands [uris] to the install service; [action] is the Intent action the files arrived with, null for an in-app pick. */
    fun start(uris: List<DocumentRef>, action: String?)
    fun hostBound()
    fun hostUnbound()
}

/** Optional session capability: returns after this submission's actual aggregate terminal outcome, not just enqueue. */
@kotlin.native.HiddenFromObjC
interface CompletingDocumentInstallStarter : DocumentInstallStarter {
    /** Cancelling the waiter must not cancel the process-owned install workers. */
    suspend fun startAndAwait(uris: List<DocumentRef>, action: String?)
}
