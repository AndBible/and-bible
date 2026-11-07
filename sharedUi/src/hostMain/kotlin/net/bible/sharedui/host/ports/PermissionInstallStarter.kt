package net.bible.sharedui.host.ports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import net.bible.sharedui.app.ports.CompletingDocumentInstallStarter
import net.bible.sharedui.app.ports.DocumentRef

internal class PermissionInstallStarter(
    private val delegate: CompletingDocumentInstallStarter,
    private val scope: CoroutineScope,
    private val ensureAsked: suspend () -> Unit,
) : CompletingDocumentInstallStarter {
    override fun start(uris: List<DocumentRef>, action: String?) {
        askForSubmission(uris)
        delegate.start(uris, action)
    }

    override suspend fun startAndAwait(uris: List<DocumentRef>, action: String?) {
        askForSubmission(uris)
        delegate.startAndAwait(uris, action)
    }

    private fun askForSubmission(uris: List<DocumentRef>) {
        if (uris.isEmpty()) return
        scope.launch {
            try { ensureAsked() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("PermissionInstallStarter", "Notification permission failed", e) }
        }
    }

    override fun hostBound() = delegate.hostBound()
    override fun hostUnbound() = delegate.hostUnbound()
}
