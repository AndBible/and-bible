package net.bible.sharedui.ai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.ProviderTypeVd
import net.bible.sharedcore.ai.ProviderVd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
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

    @Test fun customProviderFormValidatesAndSavesThroughController() = runComposeUiTest {
        val records = MutableStateFlow<List<ProviderVd>>(emptyList())
        var savedKey = ""
        val custom = ProviderTypeVd("CUSTOM", "Custom provider", "OTHER", null, "", false, true)
        val service = object : LlmProviderService {
            override val ready = MutableStateFlow(true)
            override val providers = records
            override fun providerTypes() = listOf(custom)
            override suspend fun apiKeyFor(providerId: String) = savedKey
            override suspend fun saveProvider(id: String?, typeId: String, displayName: String, apiKey: String, endpoint: String, apiFormatId: String) {
                savedKey = apiKey
                records.value = listOf(ProviderVd("saved", displayName, typeId, true, true, endpoint, apiFormatId))
            }
            override suspend fun deleteProvider(id: String) { records.value = emptyList() }
            override suspend fun testConnection(typeId: String, endpoint: String, apiKey: String) = Result.success(Unit)
            override suspend fun fetchAvailableModels(providerId: String) = emptyList<net.bible.sharedcore.ai.AvailableModelVd>()
            override suspend fun refresh() = Unit
            override fun recommendedSetups() = emptyList<net.bible.sharedcore.ai.RecommendedSetupVd>()
            override suspend fun performEasySetup(setupId: String, apiKey: String) = Unit
            override fun disclaimerAccepted() = true
            override suspend fun acceptDisclaimer() = Unit
        }
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val controller = AiProvidersController(service, scope)
        val strings = iosStrings("en")
        try {
            controller.startAdd()
            controller.pickType("CUSTOM")
            setContent {
                val dialog by controller.dialog.collectAsState()
                val providers by controller.providers.collectAsState()
                CompositionLocalProvider(LocalStrings provides strings) {
                    AbTheme(darkTheme = false, disableAnimations = true) {
                        AiProvidersScreen(providers, controller.providerTypes(), dialog, {}, controller::startAdd,
                            controller::pickType, controller::startEdit, controller::updateField, controller::save,
                            controller::delete, controller::dismissDialog, "", "")
                    }
                }
            }
            onNodeWithText(strings.okay).assertIsNotEnabled()
            onAllNodes(hasSetTextAction())[1].performTextInput("test-key")
            onNodeWithText(strings.okay).assertIsNotEnabled()
            onAllNodes(hasSetTextAction())[0].performTextInput("Private provider")
            onAllNodes(hasSetTextAction())[2].performTextInput("https://example.org/api")
            onNodeWithText(strings.okay).assertIsEnabled().performClick()
            waitForIdle()
            assertEquals("test-key", savedKey)
            assertEquals("Private provider", records.value.single().displayName)
            assertEquals("https://example.org/api", records.value.single().endpoint)
            assertEquals("OPENAI", records.value.single().apiFormatId)
            val reopened = AiProvidersController(service, scope)
            runOnIdle { reopened.startEdit("saved") }
            assertEquals("test-key", reopened.dialog.value?.apiKey)
            assertEquals("https://example.org/api", reopened.dialog.value?.endpoint)
        } finally { scope.coroutineContext[Job]?.cancel() }
    }

    @Test fun immediateEchoControlKeepsBothKeystrokes() {
        assertEquals("no", typeTwoKeys(Field.API_KEY, 1, "n", "o", asyncEcho = false).first)
        assertEquals("ht", typeTwoKeys(Field.ENDPOINT, 2, "h", "t", asyncEcho = false).first)
    }
}
