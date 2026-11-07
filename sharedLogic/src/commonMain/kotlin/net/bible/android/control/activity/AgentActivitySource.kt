package net.bible.android.control.activity

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.bible.android.database.IdType
import net.bible.service.llm.agent.AgentSessionChange
import net.bible.service.llm.agent.AgentStopReason
import net.bible.service.llm.agent.LogEntryType
import net.bible.sharedcore.event.Events

data class AgentActivityStrings(
    val agent: String,
    val agentIn: (String) -> String,
    val waiting: (String?) -> String,
    val failed: String,
)

fun agentActivityId(workspaceId: IdType): String = "agent:$workspaceId"

fun workspaceIdOfAgentItem(item: ActivityItem): IdType? {
    if (item.kind != ActivityKind.AI_AGENT || !item.id.startsWith("agent:")) return null
    return IdType(item.id.removePrefix("agent:")).takeUnless { it.isEmpty }
}

class AgentActivitySource(
    private val center: ActivityCenter,
    changes: Events<AgentSessionChange>,
    private val stop: (IdType) -> Unit,
    private val workspaceName: (IdType) -> String?,
    private val strings: () -> AgentActivityStrings,
) {
    private class Run(var lastMessage: String? = null, var lastError: String? = null, var waiting: Boolean = false, var decisionId: IdType? = null)
    // Serialize per-run bookkeeping and center mutations, including asynchronous name enrichment.
    private val lock = SynchronizedObject()
    private val runs = mutableMapOf<IdType, Run>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var closed = false
    private var lookup: (suspend (IdType) -> String?)? = null

    private val subscription = changes.subscribe { event ->
        val change = if (event is AgentSessionChange.LogUpdated) {
            event.copy(entry = event.entry.copy())
        } else event
        synchronized(lock) {
            if (!closed) handle(change)
        }
    }

    internal fun resolveWorkspaceNames(find: suspend (IdType) -> String?) {
        synchronized(lock) { lookup = find }
    }

    private fun handle(change: AgentSessionChange) {
        val ws = change.workspaceId
        val id = agentActivityId(ws)
        when (change) {
            is AgentSessionChange.StatusChanged -> if (change.isRunning) {
                val run = Run()
                runs[ws] = run
                val text = strings()
                val name = workspaceName(ws)?.takeIf { it.isNotBlank() }
                center.start(id, ActivityKind.AI_AGENT, name?.let(text.agentIn) ?: text.agent) { stop(ws) }
                val finder = lookup
                // A run token prevents a slow lookup from renaming a replacement run.
                if (name == null && finder != null) scope.launch {
                    val found = try {
                        finder(ws)?.takeIf { it.isNotBlank() }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    synchronized(lock) {
                        if (!closed && runs[ws] === run && found != null) center.setTitle(id, strings().agentIn(found))
                    }
                }
            } else {
                if (change.stopReason == null) return
                val run = runs.remove(ws) ?: return
                when (change.stopReason) {
                    AgentStopReason.COMPLETED -> center.succeed(id)
                    AgentStopReason.ERROR -> center.fail(id, run.lastError ?: strings().failed)
                    AgentStopReason.CANCELLED -> center.cancelled(id)
                    null -> Unit
                }
            }
            is AgentSessionChange.LogUpdated -> {
                val run = runs[ws] ?: return
                run.lastMessage = change.entry.message
                if (change.entry.type == LogEntryType.ERROR) run.lastError = change.entry.message
                if (!run.waiting) center.setMessage(id, run.lastMessage)
            }
            is AgentSessionChange.PermissionWaiting -> Unit
            is AgentSessionChange.SessionCleared -> {
                runs.remove(ws)
                center.dismiss(id)
            }
            is AgentSessionChange.UserDecisionPending -> {
                val run = runs[ws] ?: return
                if (!change.waiting && run.decisionId != change.decisionId) return
                run.waiting = change.waiting
                run.decisionId = if (change.waiting) change.decisionId else null
                center.setMessage(id, if (change.waiting) strings().waiting(change.toolName) else run.lastMessage)
                center.setAttention(id, change.waiting)
            }
        }
    }

    fun close() {
        subscription.cancel()
        synchronized(lock) { closed = true; runs.clear() }
        scope.cancel()
    }
}
