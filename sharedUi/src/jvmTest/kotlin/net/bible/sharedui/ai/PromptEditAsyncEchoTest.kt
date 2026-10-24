package net.bible.sharedui.ai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.ai.PromptEditData
import net.bible.sharedcore.ai.PromptEditTab
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Holds the prompt controller's echo in a queue (the `collectAsState` hop) on the real prompt editor screen. */
@OptIn(ExperimentalTestApi::class)
class PromptEditAsyncEchoTest {
    private val strings = iosStrings("en")
    private val prompt = PromptEditData(
        id = "p-1", name = "", description = "", template = "", categoryId = null, contexts = emptySet(),
        isTextTransformation = false, permissionMode = null, allowedTools = emptySet(), deniedTools = emptySet(),
        modelOverrideId = null, maxIterations = null, strictContextMatching = false, specifyBeforeRun = false,
        noDocumentCreation = false, autoIncludeDocuments = false, autoIncludeCommentaries = false,
        isReadOnly = false, isBuiltIn = false,
    )

    private class Harness(initial: PromptEditData, val asyncEcho: Boolean) {
        var shown by mutableStateOf(initial)
        val queue = ArrayDeque<PromptEditData.() -> PromptEditData>()
        val received = mutableListOf<String>()
        fun write(change: PromptEditData.() -> PromptEditData) { shown = shown.change() }
        fun echo(sent: String, change: PromptEditData.() -> PromptEditData) {
            received += sent
            if (asyncEcho) queue.addLast(change) else write(change)
        }
        fun release() { while (queue.isNotEmpty()) write(queue.removeFirst()) }
    }

    private fun ComposeUiTest.show(h: Harness) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                PromptEditScreen(
                    state = h.shown, tab = PromptEditTab.PROMPT, availableTabs = listOf(PromptEditTab.PROMPT),
                    disabledContexts = emptySet(), hiddenAdvancedKeys = emptySet(), isDirty = false, canSave = true,
                    isReadOnly = false, isBuiltIn = false, isNew = false, categories = emptyList(),
                    toolsByCategory = emptyList(), modelChoices = emptyList(),
                    globalToolPermission = { ToolPermission.DEFAULT }, globalMaxIterationsLabel = "20",
                    onSelectTab = {},
                    onSetName = { v -> h.echo(v) { copy(name = v) } },
                    onSetDescription = { v -> h.echo(v) { copy(description = v) } },
                    onSetTemplate = { v -> h.echo(v) { copy(template = v) } },
                    onSetCategory = {}, onToggleContext = {}, onSetBibleOnly = {}, onSetTextTransformation = {},
                    onSetPermissionMode = {}, onSetToolPermission = { _, _ -> }, onSetCategoryRead = { _, _ -> },
                    onSetCategoryWrite = { _, _ -> }, onResetToolPermissions = {}, onSetModelOverride = {},
                    onSetMaxIterations = {}, onSetSwitch = { _, _ -> }, onSave = {}, onDelete = {},
                    onCopyToCustomize = {}, onViewTools = {}, onBack = {}, helpBody = "", helpReadMoreUrl = "",
                )
            }
        }
    }

    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
    private fun SemanticsNodeInteraction.editable() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.typeTwo(label: String, first: String, second: String, h: Harness): String {
        val node = field(label)
        node.performClick()
        node.performTextInput(first)
        waitForIdle()
        node.performTextInput(second)
        runOnIdle { h.release() }
        waitForIdle()
        return node.editable()
    }

    @Test fun nameKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(prompt, asyncEcho = true); show(h)
        assertEquals("ab", typeTwo(strings.promptNameLabel, "a", "b", h))
        assertEquals("ab", h.received.last())
    }

    @Test fun descriptionKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(prompt, asyncEcho = true); show(h)
        assertEquals("cd", typeTwo(strings.promptDescriptionLabel, "c", "d", h))
        assertEquals("cd", h.received.last())
    }

    @Test fun templateKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(prompt, asyncEcho = true); show(h)
        assertEquals("ef", typeTwo(strings.promptTemplateLabel, "e", "f", h))
        assertEquals("ef", h.received.last())
    }

    @Test fun immediateEchoControlKeepsBothKeystrokes() = runComposeUiTest {
        val h = Harness(prompt, asyncEcho = false); show(h)
        assertEquals("ab", typeTwo(strings.promptNameLabel, "a", "b", h))
        assertEquals("cd", typeTwo(strings.promptDescriptionLabel, "c", "d", h))
        assertEquals("ef", typeTwo(strings.promptTemplateLabel, "e", "f", h))
    }

    @Test fun controllerWritesStillReachTheFields() = runComposeUiTest {
        val h = Harness(prompt, asyncEcho = true); show(h)
        typeTwo(strings.promptNameLabel, "a", "b", h)
        // Synthetic: no controller writes these fields under the same prompt id today, but the helper must adopt such a write.
        runOnIdle { h.write { copy(name = "Copy of ab", description = "reloaded", template = "T") } }
        waitForIdle()
        assertEquals("Copy of ab", field(strings.promptNameLabel).editable())
        assertEquals("reloaded", field(strings.promptDescriptionLabel).editable())
        assertEquals("T", field(strings.promptTemplateLabel).editable())
    }
}
