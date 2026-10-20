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
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.AiModelsController.Field
import net.bible.sharedcore.ai.ModelEditState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Holds the controller echo of the add-model form in a queue (the `collectAsState` hop); see [AiProviderFormAsyncEchoTest]. */
@OptIn(ExperimentalTestApi::class)
class AiModelsFormAsyncEchoTest {
    private val strings = iosStrings("en")
    private val custom = ModelEditState(
        id = null, step = ModelEditState.Step.PICK_MODEL, providerChoices = emptyList(), providerId = "p",
        availableModels = emptyList(), loadingModels = false, modelId = AiModelsController.CUSTOM_MODEL_ID,
        customModelId = "", priceInput = "", priceOutput = "", setAsDefault = false, showUnsupported = false,
        isCustom = true, showPriceFields = true, canSave = false,
    )

    private class Harness(initial: ModelEditState, val asyncEcho: Boolean) {
        var shown by mutableStateOf(initial)
        val queue = ArrayDeque<ModelEditState.() -> ModelEditState>()
        val received = mutableListOf<Pair<Field, String>>()
        fun write(change: ModelEditState.() -> ModelEditState) { shown = shown.change() }
        fun release() { while (queue.isNotEmpty()) write(queue.removeFirst()) }
    }

    private fun ModelEditState.with(field: Field, value: String) = when (field) {
        Field.CUSTOM_MODEL_ID -> copy(customModelId = value)
        Field.PRICE_INPUT -> copy(priceInput = value)
        Field.PRICE_OUTPUT -> copy(priceOutput = value)
    }

    private fun ComposeUiTest.show(h: Harness) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                AiModelsScreen(
                    models = emptyList(), providers = emptyList(), editState = h.shown,
                    onUp = {}, onAdd = {}, onPickProvider = {}, onPickModel = {}, onStartEdit = {},
                    onField = { f, v ->
                        h.received += f to v
                        val change: ModelEditState.() -> ModelEditState = { with(f, v) }
                        if (h.asyncEcho) h.queue.addLast(change) else h.write(change)
                    },
                    onSave = {}, onDelete = {}, onSetDefault = {}, onSetAsDefault = {}, onSetShowUnsupported = {},
                    onDismiss = {}, helpBody = "", helpReadMoreUrl = "",
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

    @Test fun customModelIdKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(custom, asyncEcho = true); show(h)
        assertEquals("gp", typeTwo(strings.llmCustomModelHint, "g", "p", h))
        assertEquals(Field.CUSTOM_MODEL_ID to "gp", h.received.last())
    }

    @Test fun inputPriceKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(custom, asyncEcho = true); show(h)
        assertEquals("12", typeTwo(strings.llmCustomInputPriceLabel, "1", "2", h))
        assertEquals(Field.PRICE_INPUT to "12", h.received.last())
    }

    @Test fun outputPriceKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(custom, asyncEcho = true); show(h)
        assertEquals("34", typeTwo(strings.llmCustomOutputPriceLabel, "3", "4", h))
        assertEquals(Field.PRICE_OUTPUT to "34", h.received.last())
    }

    @Test fun immediateEchoControlKeepsBothKeystrokes() = runComposeUiTest {
        val h = Harness(custom, asyncEcho = false); show(h)
        assertEquals("gp", typeTwo(strings.llmCustomModelHint, "g", "p", h))
        assertEquals("12", typeTwo(strings.llmCustomInputPriceLabel, "1", "2", h))
        assertEquals("34", typeTwo(strings.llmCustomOutputPriceLabel, "3", "4", h))
    }

    @Test fun editingAnotherModelReseedsTheFieldsFromTheNewState() = runComposeUiTest {
        val h = Harness(custom.copy(id = "m1", priceInput = "1.5", priceOutput = "2.5"), asyncEcho = true); show(h)
        // A different model replaces the dialog state (startEdit with another id), and an unrelated controller write
        // changes a price that the user never typed.
        runOnIdle { h.write { copy(id = "m2", priceInput = "9", priceOutput = "8") } }
        waitForIdle()
        assertEquals("9", field(strings.llmCustomInputPriceLabel).editable())
        assertEquals("8", field(strings.llmCustomOutputPriceLabel).editable())
        runOnIdle { h.write { copy(priceInput = "7") } }
        waitForIdle()
        assertEquals("7", field(strings.llmCustomInputPriceLabel).editable())
    }
}
