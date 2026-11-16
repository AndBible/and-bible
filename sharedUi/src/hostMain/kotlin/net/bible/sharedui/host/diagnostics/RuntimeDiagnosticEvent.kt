package net.bible.sharedui.host.diagnostics

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class DiagnosticSource { APPLICATION, SCENE, HOST, WINDOW, PANE, WEB_VIEW }
internal enum class DiagnosticState { ACTIVE, INACTIVE, BACKGROUND, FOREGROUND, INITIALIZED, CREATED, STARTED, RESUMED, DESTROYED, STOPPED, SUCCEEDED, FAILED, UNKNOWN }
internal enum class DiagnosticReason { BUSY, NOT_READY, TIMEOUT, CANCELLED, UNAVAILABLE, NO_HANDLER, NO_URL, NO_WINDOW, STALE_IDENTITY, OTHER }
internal enum class ProbeTrigger { STARTUP, LIFECYCLE, RECOVERY, MANUAL }
internal enum class ProbeOutcome { ALIVE, LOADING, DEAD, UNRESPONSIVE }
internal enum class RecoveryAction { REQUESTED, GIVE_UP, FAILED, COMPLETED }
internal enum class RecoveryFailure { NONE, NO_HANDLER, NO_URL, NO_WINDOW, STALE_IDENTITY, TIMEOUT, OTHER }

internal sealed interface RuntimeDiagnosticEvent {
    data class RunStarted(val version: String, val build: String, val commit: String) : RuntimeDiagnosticEvent
    data class LifecycleObserved(val source: DiagnosticSource, val state: DiagnosticState, val id: String) : RuntimeDiagnosticEvent
    data class ProbeStarted(val id: String) : RuntimeDiagnosticEvent
    data class ProbeSkipped(val id: String, val reason: DiagnosticReason) : RuntimeDiagnosticEvent
    data class ProbeSettled(val id: String, val state: DiagnosticState, val durationMillis: Long) : RuntimeDiagnosticEvent
    data class RecoveryObserved(val source: DiagnosticSource, val state: DiagnosticState, val id: String) : RuntimeDiagnosticEvent
    data class OwnershipObserved(val hostId: String, val windowId: String, val sceneId: String, val applicationState: DiagnosticState, val sceneState: DiagnosticState, val composeState: DiagnosticState, val hidden: Boolean, val key: Boolean, val attached: Boolean) : RuntimeDiagnosticEvent
    data class ProbeContext(val trigger: ProbeTrigger, val probeId: String, val paneId: String, val webViewId: String, val candidateCount: Int, val eligibleCount: Int) : RuntimeDiagnosticEvent
    data class ProbeResult(val probeId: String, val paneId: String, val webViewId: String, val outcome: ProbeOutcome, val staleIdentity: Boolean, val durationMillis: Long) : RuntimeDiagnosticEvent
    data class RecoveryResult(val paneId: String, val action: RecoveryAction, val failure: RecoveryFailure) : RuntimeDiagnosticEvent
    data class PaneCandidates(val trigger: ProbeTrigger, val candidateCount: Int, val eligibleCount: Int) : RuntimeDiagnosticEvent
    data class PaneSkipped(val trigger: ProbeTrigger, val paneId: String, val webViewId: String, val reason: DiagnosticReason) : RuntimeDiagnosticEvent
    data class ClientReady(val paneId: String) : RuntimeDiagnosticEvent
    data class ContentTerminated(val id: String) : RuntimeDiagnosticEvent
    data class EventsDropped(val count: Long) : RuntimeDiagnosticEvent
}

internal data class DiagnosticRecord(val runId: String, val sequence: Long, val epochMillis: Long, val event: RuntimeDiagnosticEvent) {
    fun encode(): String = buildJsonObject {
        put("runId", runId)
        put("sequence", sequence)
        put("epochMillis", epochMillis)
        put("event", event.json())
    }.toString()
}

private fun RuntimeDiagnosticEvent.json(): JsonObject = buildJsonObject {
    when (val event = this@json) {
        is RuntimeDiagnosticEvent.RunStarted -> { put("type", "RunStarted"); put("version", event.version); put("build", event.build); put("commit", event.commit) }
        is RuntimeDiagnosticEvent.LifecycleObserved -> { put("type", "LifecycleObserved"); put("source", event.source.name); put("state", event.state.name); put("id", event.id) }
        is RuntimeDiagnosticEvent.ProbeStarted -> { put("type", "ProbeStarted"); put("id", event.id) }
        is RuntimeDiagnosticEvent.ProbeSkipped -> { put("type", "ProbeSkipped"); put("id", event.id); put("reason", event.reason.name) }
        is RuntimeDiagnosticEvent.ProbeSettled -> { put("type", "ProbeSettled"); put("id", event.id); put("state", event.state.name); put("durationMillis", event.durationMillis) }
        is RuntimeDiagnosticEvent.RecoveryObserved -> { put("type", "RecoveryObserved"); put("source", event.source.name); put("state", event.state.name); put("id", event.id) }
        is RuntimeDiagnosticEvent.OwnershipObserved -> { put("type", "OwnershipObserved"); put("hostId", event.hostId); put("windowId", event.windowId); put("sceneId", event.sceneId); put("applicationState", event.applicationState.name); put("sceneState", event.sceneState.name); put("composeState", event.composeState.name); put("hidden", event.hidden); put("key", event.key); put("attached", event.attached) }
        is RuntimeDiagnosticEvent.ProbeContext -> { put("type", "ProbeContext"); put("trigger", event.trigger.name); put("probeId", event.probeId); put("paneId", event.paneId); put("webViewId", event.webViewId); put("candidateCount", event.candidateCount); put("eligibleCount", event.eligibleCount) }
        is RuntimeDiagnosticEvent.ProbeResult -> { put("type", "ProbeResult"); put("probeId", event.probeId); put("paneId", event.paneId); put("webViewId", event.webViewId); put("outcome", event.outcome.name); put("staleIdentity", event.staleIdentity); put("durationMillis", event.durationMillis) }
        is RuntimeDiagnosticEvent.RecoveryResult -> { put("type", "RecoveryResult"); put("paneId", event.paneId); put("action", event.action.name); put("failure", event.failure.name) }
        is RuntimeDiagnosticEvent.PaneCandidates -> { put("type", "PaneCandidates"); put("trigger", event.trigger.name); put("candidateCount", event.candidateCount); put("eligibleCount", event.eligibleCount) }
        is RuntimeDiagnosticEvent.PaneSkipped -> { put("type", "PaneSkipped"); put("trigger", event.trigger.name); put("paneId", event.paneId); put("webViewId", event.webViewId); put("reason", event.reason.name) }
        is RuntimeDiagnosticEvent.ClientReady -> { put("type", "ClientReady"); put("paneId", event.paneId) }
        is RuntimeDiagnosticEvent.ContentTerminated -> { put("type", "ContentTerminated"); put("id", event.id) }
        is RuntimeDiagnosticEvent.EventsDropped -> { put("type", "EventsDropped"); put("count", event.count) }
    }
}
