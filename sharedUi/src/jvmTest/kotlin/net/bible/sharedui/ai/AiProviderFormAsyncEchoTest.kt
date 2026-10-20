package net.bible.sharedui.ai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.ai.AiProvidersController.Field
import net.bible.sharedcore.ai.ProviderEditState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The controller publishes fields through a StateFlow that the screen reads via `collectAsState`, so its echo reaches the
 * form a dispatch after the keystroke. These tests hold the echo in a queue to model that hop on the real screen.
 */
@OptIn(ExperimentalTestApi::class)
class AiProviderFormAsyncEchoTest {
    private val empty = ProviderEditState(
        id = null, step = ProviderEditState.Step.FORM, typeId = "custom", displayName = "", apiKey = "", endpoint = "",
        apiFormatId = "openai", isCustom = true, apiKeyUrl = null, canSave = false,
    )

    private fun ProviderEditState.with(field: Field, value: String) = when (field) {
        Field.NAME -> copy(displayName = value)
        Field.API_KEY -> copy(apiKey = value)
        Field.ENDPOINT -> copy(endpoint = value)
        else -> this
    }

    private fun typeTwoKeys(field: Field, index: Int, first: String, second: String, asyncEcho: Boolean): Pair<String, List<Pair<Field, String>>> {
        var result = ""
        val received = mutableListOf<Pair<Field, String>>()
        runComposeUiTest {
            var shown by mutableStateOf(empty)
            val queue = ArrayDeque<Pair<Field, String>>()
            setContent {
                CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                    AbTheme(darkTheme = false) {
                        AiProvidersScreen(
                            providers = emptyList(), providerTypes = emptyList(), editState = shown,
                            onUp = {}, onAdd = {}, onPickType = {}, onStartEdit = {},
                            onField = { f, v ->
                                received += f to v
                                if (asyncEcho) queue.addLast(f to v) else shown = shown.with(f, v)
                            },
                            onSave = {}, onDelete = {}, onDismiss = {}, helpBody = "", helpReadMoreUrl = "",
                        )
                    }
                }
            }
            val node = onAllNodes(hasSetTextAction())[index]
            node.performClick()
            node.performTextInput(first)
            waitForIdle() // a recomposition while the controller echo is still in flight
            node.performTextInput(second)
            runOnIdle { while (queue.isNotEmpty()) queue.removeFirst().let { (f, v) -> shown = shown.with(f, v) } }
            waitForIdle()
            result = node.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        }
        return result to received
    }

    @Test fun apiKeyKeepsBothKeystrokesWhileTheControllerEchoIsDelayed() {
        val (text, received) = typeTwoKeys(Field.API_KEY, 1, "n", "o", asyncEcho = true)
        assertEquals("no", text)
        assertEquals("no", received.last().second)
    }

    @Test fun endpointKeepsBothKeystrokesWhileTheControllerEchoIsDelayed() {
        val (text, received) = typeTwoKeys(Field.ENDPOINT, 2, "h", "t", asyncEcho = true)
        assertEquals("ht", text)
        assertEquals("ht", received.last().second)
    }

    @Test fun immediateEchoControlKeepsBothKeystrokes() {
        assertEquals("no", typeTwoKeys(Field.API_KEY, 1, "n", "o", asyncEcho = false).first)
        assertEquals("ht", typeTwoKeys(Field.ENDPOINT, 2, "h", "t", asyncEcho = false).first)
    }
}
