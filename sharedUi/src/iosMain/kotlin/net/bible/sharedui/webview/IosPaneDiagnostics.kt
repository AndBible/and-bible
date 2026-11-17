@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.webview

import net.bible.sharedui.ios.diagnostics.NativeDiagnosticIdentities
import platform.WebKit.WKWebView
import net.bible.sharedui.host.diagnostics.*
import platform.Foundation.NSUUID

internal data class PaneDiagnosticCandidate(
    val paneId: String, val webViewId: String,
    val hasHandler: Boolean, val hasUrl: Boolean, val hasWindow: Boolean,
)

internal fun observePaneCandidates(
    candidates: List<PaneDiagnosticCandidate>, trigger: ProbeTrigger, emit: (RuntimeDiagnosticEvent) -> Unit,
) {
    val eligible = candidates.count { it.hasHandler && it.hasUrl && it.hasWindow }
    emit(RuntimeDiagnosticEvent.PaneCandidates(trigger, candidates.size, eligible))
    candidates.forEach { candidate ->
        val reason = when {
            !candidate.hasHandler -> DiagnosticReason.NO_HANDLER
            !candidate.hasUrl -> DiagnosticReason.NO_URL
            !candidate.hasWindow -> DiagnosticReason.NO_WINDOW
            else -> null
        }
        if (reason != null) emit(RuntimeDiagnosticEvent.PaneSkipped(trigger, candidate.paneId, candidate.webViewId, reason))
    }
}

internal class PaneDiagnosticIdentity {
    fun newEntry() = PaneDiagnosticEntry(NSUUID().UUIDString, NSUUID().UUIDString)
}

internal data class PaneDiagnosticEntry(val paneId: String, val webViewId: String) {
    fun observeOwnership(webView: WKWebView, emit: (RuntimeDiagnosticEvent) -> Unit) {
        try {
            val window = webView.window
            emit(RuntimeDiagnosticEvent.PaneOwnershipObserved(paneId, webViewId,
                NativeDiagnosticIdentities.identity(window), NativeDiagnosticIdentities.identity(window?.windowScene), window != null))
        } catch (_: Throwable) { }
    }
    fun clientReady(emit: (RuntimeDiagnosticEvent) -> Unit) { try { emit(RuntimeDiagnosticEvent.ClientReady(paneId)) } catch (_: Throwable) { } }
    fun terminated(emit: (RuntimeDiagnosticEvent) -> Unit) { try { emit(RuntimeDiagnosticEvent.ContentTerminated(webViewId)) } catch (_: Throwable) { } }
}
