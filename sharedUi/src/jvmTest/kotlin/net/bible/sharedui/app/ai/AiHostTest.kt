package net.bible.sharedui.app.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonObject
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class AiHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected ${m.name}")
        } as T

    private val strings = iosStrings("en")
    private val shared = mutableListOf<Pair<String, String>>()

    private fun tool(tool: AgentTool, permission: Boolean) = object : Tool {
        override val agentTool = tool
        override val description = "d-${tool.name}"
        override val parametersSchema = JsonObject(emptyMap())
        override val requiresPermission = permission
        override val category = ToolCategory.BOOKMARKS
        override suspend fun execute(arguments: JsonObject, context: AgentContext): ToolResult = error("unused")
    }

    private fun host(tools: List<Tool> = emptyList()) = AiHost(
        shell = shell,
        strings = strings,
        textShare = object : TextShare {
            override fun shareText(text: String, chooserTitle: String) { shared += text to chooserTitle }
        },
        clipboard = unused(),
        helpDialogs = unused(),
        documentPicker = unused(),
        promptFiles = unused(),
        bugReport = unused(),
        platform = unused(),
        userNotifierProvider = { error("unused") },
        appScopeProvider = { error("unused") },
        aiSettingsProvider = { error("unused") },
        aiSettingsServiceProvider = { error("unused") },
        llmModelServiceProvider = { error("unused") },
        llmProviderServiceProvider = { error("unused") },
        promptServiceProvider = { error("unused") },
        rawLogServiceProvider = { error("unused") },
        documentFilterServiceProvider = { error("unused") },
        toolPermissionServiceProvider = { error("unused") },
        allToolsProvider = { tools },
        toolViewData = { ToolVd(it.agentTool.name, "name-${it.agentTool.name}", it.description, it.requiresPermission, it.category.name) },
    )

    @Test
    fun rawLogShareUsesTextShare() {
        host().shareRawLlmLog(recordId = "r1", workspaceId = null, recordText = "LOG TEXT")
        assertEquals(listOf("LOG TEXT" to strings.shareLabel), shared)
    }

    @Test
    fun toolsSplitByPermission() {
        val read = tool(AgentTool.GET_VERSE_CONTENT, permission = false)
        val write = tool(AgentTool.CREATE_BOOKMARK, permission = true)
        val host = host(listOf(read, write))
        assertEquals(listOf(read.agentTool.name), host.readTools.map { it.id })
        assertEquals(listOf(write.agentTool.name), host.writeTools.map { it.id })
    }
}
