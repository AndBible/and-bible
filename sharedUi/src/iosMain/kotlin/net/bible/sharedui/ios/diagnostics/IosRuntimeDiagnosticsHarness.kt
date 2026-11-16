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
        fun sequence(type: String, source: String? = null, state: String? = null): String =
            currentRecords.firstOrNull {
                val event = it["event"]?.jsonObject
                event?.get("type")?.jsonPrimitive?.content == type &&
                    (source == null || event["source"]?.jsonPrimitive?.content == source) &&
                    (state == null || event["state"]?.jsonPrimitive?.content == state)
            }?.get("sequence")?.jsonPrimitive?.content ?: "missing"
        val identities = records.mapNotNull { record ->
            val run = record["runId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val seq = record["sequence"]?.jsonPrimitive?.content ?: return@mapNotNull null
            "$run:$seq"
        }.joinToString(",")
        return "attachment=true runs=${if (runs.size >= 2) "2+" else runs.size} " +
            "current=${current in runs} run=$current records=$identities " +
            "host=${sequence("OwnershipObserved")} pane=${sequence("ClientReady")} " +
            "probe=${sequence("ProbeResult")} background=${sequence("LifecycleObserved", "APPLICATION", "BACKGROUND")}"
    }
}
