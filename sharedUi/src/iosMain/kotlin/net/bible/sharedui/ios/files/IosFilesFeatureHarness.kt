@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.ApiFormat
import net.bible.service.llm.LlmProviderConfig
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.ios.ports.IosDocumentPicker
import net.bible.sharedui.strings.Strings
import org.koin.mp.KoinPlatform
import platform.Foundation.*
import kotlin.native.Platform

/** Debug-only feature setup and independent observations; all export/import actions remain production UI. */
internal object IosFilesFeatureHarness {
    private var mode: String? = null
    private var token = ""

    fun prepareLaunch() {
        if (!Platform.isDebugBinary) return
        val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
        mode = args.indexOf("-filesFeatureTest").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
            ?.takeIf { it == "mydocs" || it == "prompts" }
        if (mode == null) return
        check("-filesPortTest" !in args && "-incomingRuntimeTest" !in args)
        token = args.indexOf("-filesFeatureToken").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }.orEmpty()
        check(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(token))
    }

    private val pageTitle get() = "F-page-$token"
    private val filename get() = if (mode == "mydocs") "$pageTitle.md" else "F-prompts-$token.csv"
    private val pagePayload get() = "# F page $token\n\nPayload; \"quoted\", end.\n"

    private suspend fun seed(): Pair<String, AgentPrompt?> {
        val database = DatabaseContainer.instance
        if (mode == "mydocs") {
            val dao = database.myDocumentDb.myDocumentDao()
            val document = MyDocument(name = "F Documents $token", initials = "F_${token.replace("-", "")}")
            dao.insert(document)
            dao.insertPageWithContent(MyDocumentPage(documentId = document.id, title = pageTitle,
                pageKey = "f-page", orderNumber = 0), pagePayload)
            return NavRoutes.myDocumentPages(document.id.toString(), document.initials, document.name) to null
        }
        val db = database.aiSettingsDb
        val prompt = AgentPrompt(id = IdType(token), name = "F-prompt-$token",
            description = "Description; \"$token\"", promptTemplate = "First $token; \"quoted\"\nSecond $token",
            createdAt = 1_700_000_000_000L)
        check(db.agentPromptDao().promptById(prompt.id) == null)
        db.agentPromptDao().insert(prompt)
        db.llmProviderConfigDao().insert(LlmProviderConfig(providerType = "CUSTOM", displayName = "F provider $token",
            endpoint = "https://files-fixture.invalid", apiFormat = ApiFormat.OPENAI))
        val service = KoinPlatform.getKoin().get<PromptService>()
        service.refresh()
        check(service.configured.value) { "Real provider-count screen gate did not refresh" }
        return NavRoutes.AI_PROMPTS to prompt
    }

    private inline fun <T> scoped(folder: DocumentRef, block: (NSURL) -> T): T {
        val url = checkNotNull(NSURL.URLWithString(folder.uri))
        check(url.isFileURL())
        val acquired = url.startAccessingSecurityScopedResource()
        return try { block(url) } finally { if (acquired) url.stopAccessingSecurityScopedResource() }
    }

    private fun target(folder: NSURL): NSURL = checkNotNull(folder.URLByAppendingPathComponent(filename))

    private fun readSaved(folder: DocumentRef): String? = scoped(folder) {
        NSString.stringWithContentsOfURL(target(it), NSUTF8StringEncoding, null)
    }

    /** Independent CSV record reader: never calls the exporter or importer being exercised. */
    private fun records(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> { row.add(field.toString()); field.clear() }
                c == '\n' && !quoted -> { row.add(field.toString()); field.clear(); rows.add(row.toList()); row.clear() }
                else -> field.append(c)
            }
            i++
        }
        check(!quoted) { "Unterminated external CSV record" }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row.toList()) }
        return rows
    }

    private fun csvMatches(text: String, seed: AgentPrompt): Boolean {
        val rows = records(text)
        val header = listOf("name", "description", "promptTemplate", "showIn", "orderNumber", "strictContextMatching",
            "permissionMode", "allowedTools", "deniedTools", "configuredModelId", "id", "createdAt", "bibleOnly", "category")
        if (rows.firstOrNull() != header) return false
        val matching = rows.drop(1).filter { it.getOrNull(10) == seed.id.toString() }
        return matching == listOf(listOf(seed.name, seed.description.orEmpty(), seed.promptTemplate, "", "0", "true",
            "", "", "", "", seed.id.toString(), "2023-11-14T22:13:20Z", "false", ""))
    }

    private fun importedMatches(actual: AgentPrompt, seed: AgentPrompt): Boolean =
        actual.id == seed.id && actual.name == seed.name && actual.description == seed.description &&
            actual.promptTemplate == seed.promptTemplate && actual.showIn == seed.showIn &&
            actual.orderNumber == seed.orderNumber && actual.strictContextMatching == seed.strictContextMatching &&
            actual.permissionMode == seed.permissionMode && actual.allowedTools == seed.allowedTools &&
            actual.deniedTools == seed.deniedTools && actual.configuredModelId == seed.configuredModelId &&
            actual.createdAt == seed.createdAt && actual.bibleOnly == seed.bibleOnly && actual.categoryId == seed.categoryId

    @Composable
    fun Overlay(strings: Strings, route: String?, awaitGraph: suspend () -> Unit, navigate: (String) -> Unit) {
        if (!Platform.isDebugBinary || mode == null) return
        val scope = rememberCoroutineScope()
        var seeded by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var entered by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<DocumentRef?>(null) }
        var busy by remember { mutableStateOf(false) }
        var destination by remember { mutableStateOf<String?>(null) }
        var prompt by remember { mutableStateOf<AgentPrompt?>(null) }
        var saved by remember { mutableStateOf(false) }
        var absent by remember { mutableStateOf(false) }
        var imported by remember { mutableStateOf(false) }
        fun fail(error: Exception) { nsLog("Files feature diagnostic failed: ${error.stackTraceToString()}"); failed = true }
        LaunchedEffect(Unit) {
            try {
                awaitGraph()
                val result = withContext(Dispatchers.Default) { seed() }
                destination = result.first
                prompt = result.second
                seeded = true
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
        }
        LaunchedEffect(selected, entered) {
            val folder = selected ?: return@LaunchedEffect
            if (!entered) return@LaunchedEffect
            while (true) {
                try {
                    val observation = withContext(Dispatchers.Default) {
                        val text = readSaved(folder)
                        val expected = prompt
                        val matches = text != null && if (expected == null) text == pagePayload else csvMatches(text, expected)
                        val actual = expected?.let { DatabaseContainer.instance.aiSettingsDb.agentPromptDao().promptById(it.id) }
                        matches to actual
                    }
                    if (observation.first) saved = true
                    val expected = prompt
                    if (expected != null && saved) {
                        if (observation.second == null) absent = true
                        else if (absent && importedMatches(observation.second!!, expected)) imported = true
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { fail(error); return@LaunchedEffect }
                delay(200)
            }
        }
        // Once entered, only small status witnesses remain; no diagnostic control intercepts real menus.
        Column(Modifier.padding(4.dp)) {
            if (failed) Text(strings.errorOccurred, Modifier.testTag("files-feature-error"))
            if (seeded) Text(strings.okay, Modifier.testTag("files-feature-seeded"))
            if (selected != null) {
                Text(strings.okay, Modifier.testTag("files-feature-folder-ready"))
                if (!entered) Text(strings.okay, Modifier.testTag("files-feature-destination-absent"))
            }
            if (entered && route?.substringBefore('?') == destination?.substringBefore('?'))
                Text(strings.okay, Modifier.testTag("files-feature-route-ready"))
            if (saved) Text(strings.okay, Modifier.testTag("files-feature-saved-verified"))
            if (absent && !imported) Text(strings.okay, Modifier.testTag("files-feature-prompt-absent"))
            if (imported) Text(strings.okay, Modifier.testTag("files-feature-imported-verified"))
            if (!entered) {
                Button(enabled = seeded && !busy && !failed, modifier = Modifier.testTag("files-feature-folder-select"), onClick = {
                    scope.launch {
                        busy = true
                        selected = null
                        try {
                            val folder = IosDocumentPicker().openDocumentTree()
                            if (folder != null) {
                                withContext(Dispatchers.Default) {
                                    scoped(folder) { check(!NSFileManager.defaultManager.fileExistsAtPath(target(it).path!!)) }
                                }
                                selected = folder
                            }
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) { fail(error) }
                        finally { busy = false }
                    }
                }) { Text(strings.okay) }
                Button(enabled = selected != null && !busy && !failed, modifier = Modifier.testTag("files-feature-enter"),
                    onClick = { entered = true; navigate(checkNotNull(destination)) }) { Text(strings.okay) }
            }
        }
    }
}
