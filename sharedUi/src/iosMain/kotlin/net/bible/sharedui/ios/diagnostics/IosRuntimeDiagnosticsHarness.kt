@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package net.bible.sharedui.ios.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.ios.report.IosBugReport
import okio.Buffer
import okio.GzipSource
import okio.buffer
import okio.use
import platform.Foundation.NSProcessInfo
import kotlin.native.Platform

/** Explicitly enabled only in debug test launches; never presents or sends a report. */
internal object IosRuntimeDiagnosticsHarness {
    @Composable
    fun Overlay() {
        if (!Platform.isDebugBinary || !NSProcessInfo.processInfo.arguments.contains("-runtimeDiagnosticsTest")) return
        val scope = rememberCoroutineScope()
        var result by remember { mutableStateOf<String?>(null) }
        Column {
            Button(onClick = {
                scope.launch {
                    result = try { inspectReport() } catch (_: Exception) { "error" }
                }
            }, modifier = Modifier.testTag("runtime-diagnostics-probe")) { Text("Inspect diagnostics") }
            result?.let { Text(it, Modifier.testTag("runtime-diagnostics-result")) }
        }
    }

    private suspend fun inspectReport(): String {
        var history: String? = null
        IosBugReport(hostStringTable("en"), confirm = { true }, failure = { throw it },
            send = { _, _, _, attachments ->
                val attachment = attachments.single { it.name == "ios_runtime_diagnostics.txt.gz" }
                history = GzipSource(Buffer().write(attachment.bytes)).buffer().use { it.readUtf8() }
            }).report()
        val records = requireNotNull(history).lineSequence().filter { it.isNotBlank() }.map {
            Json.parseToJsonElement(it).jsonObject
        }.toList()
        val runs = records.filter { it["event"]?.jsonObject?.get("type")?.jsonPrimitive?.content == "RunStarted" }
            .map { it.getValue("runId").jsonPrimitive.content }.distinct()
        val current = IosRuntimeDiagnostics.testRunId
        val currentRecords = records.filter { it["runId"]?.jsonPrimitive?.content == current }
        fun field(record: kotlinx.serialization.json.JsonObject, name: String) =
            record["event"]?.jsonObject?.get(name)?.jsonPrimitive?.content.orEmpty()
        fun seq(record: kotlinx.serialization.json.JsonObject?) = record?.get("sequence")?.jsonPrimitive?.content ?: "missing"
        fun number(record: kotlinx.serialization.json.JsonObject?) = seq(record).toLongOrNull() ?: Long.MAX_VALUE
        val background = currentRecords.firstOrNull { field(it, "type") == "LifecycleObserved" &&
            field(it, "source") == "APPLICATION" && field(it, "state") == "BACKGROUND" }
        val foreground = currentRecords.firstOrNull { field(it, "type") == "LifecycleObserved" &&
            field(it, "source") == "APPLICATION" && field(it, "state") == "FOREGROUND" && number(it) > number(background) }
        val active = currentRecords.firstOrNull { field(it, "type") == "LifecycleObserved" &&
            field(it, "source") == "APPLICATION" && field(it, "state") == "ACTIVE" && number(it) > number(foreground) }
        fun attached(record: kotlinx.serialization.json.JsonObject) = field(record, "type") == "OwnershipObserved" &&
            field(record, "attached") == "true" && listOf("hostId", "windowId", "sceneId").all {
                field(record, it).isNotEmpty() && field(record, it) != "none"
            }
        val host = currentRecords.firstOrNull(::attached)
        val hostActive = currentRecords.firstOrNull { attached(it) && field(it, "hostId") == host?.let { h -> field(h, "hostId") } &&
            field(it, "windowId") == host?.let { h -> field(h, "windowId") } &&
            field(it, "sceneId") == host?.let { h -> field(h, "sceneId") } &&
            field(it, "applicationState") == "ACTIVE" && number(it) > number(active) }
        fun paneOwner(probe: kotlinx.serialization.json.JsonObject) = currentRecords.lastOrNull {
            field(it, "type") == "PaneOwnershipObserved" && number(it) < number(probe) &&
                field(it, "paneId") == field(probe, "paneId") && field(it, "webViewId") == field(probe, "webViewId")
        }
        fun ready(probe: kotlinx.serialization.json.JsonObject) = currentRecords.firstOrNull {
            field(it, "type") == "ClientReady" && field(it, "paneId") == field(probe, "paneId") && number(it) < number(probe)
        }
        val probe = currentRecords.firstOrNull { record ->
            field(record, "type") == "ProbeResult" && number(record) > number(active) && ready(record) != null &&
                paneOwner(record)?.let { owner ->
                    field(owner, "attached") == "true" && host != null &&
                        field(owner, "windowId") == field(host, "windowId") && field(owner, "sceneId") == field(host, "sceneId")
                } == true
        }
        val pane = probe?.let(::ready)
        val paneOwnership = probe?.let(::paneOwner)
        val identities = records.mapNotNull { record ->
            val run = record["runId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val seq = record["sequence"]?.jsonPrimitive?.content ?: return@mapNotNull null
            "$run:$seq"
        }.joinToString(",")
        return "attachment=true runs=${if (runs.size >= 2) "2+" else runs.size} " +
            "current=${current in runs} run=$current records=$identities " +
            "host=${seq(host)} pane=${seq(pane)} paneowner=${seq(paneOwnership)} probe=${seq(probe)} background=${seq(background)} " +
            "foreground=${seq(foreground)} active=${seq(active)} hostactive=${seq(hostActive)}"
    }
}
