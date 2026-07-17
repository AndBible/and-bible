package net.bible.sharedcore.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.settings.SettingsItem
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiConnectionSettingsControllerTest {
    private fun snap(providers: Int = 1, models: Int = 1) = AiSettingsSnapshot(
        providerCount = providers, modelCount = models, defaultModelLabel = "M",
        agentPermissionMode = AgentPermissionModeIds.ordered.first(), aiLanguage = "",
        aiLanguageLabel = "Default", askModelBeforeRun = false,
        commentaryMaxResponseTokens = 1000, maxIterations = 10,
        customAgentSystemPromptSet = false, customTextTransformationSystemPromptSet = false,
        rawLogRetentionDays = 30, autoHideAgentLogOnCompletion = true,
        allowedToolCount = 3, deniedToolCount = 1, usageSummary = "no usage", disclaimerAccepted = true,
    )
    private class Fake(initial: AiSettingsSnapshot) : AiSettingsService {
        val flow = MutableStateFlow(initial)
        override val snapshot: StateFlow<AiSettingsSnapshot> = flow
        var lastMode: String? = null; var lastAsk: Boolean? = null; var lastIters: Int? = null
        override fun setAgentPermissionMode(value: String) { lastMode = value }
        override fun setAskModelBeforeRun(value: Boolean) { lastAsk = value }
        override fun setMaxIterations(value: Int) { lastIters = value }
        override fun setAiLanguage(value: String) {}
        override fun setCommentaryMaxResponseTokens(value: Int) {}
        override fun setCustomAgentSystemPrompt(value: String?) {}
        override fun setCustomTextTransformationSystemPrompt(value: String?) {}
        override fun setRawLogRetention(days: Int) {}
        override fun setAutoHideAgentLogOnCompletion(value: Boolean) {}
        override fun acceptDisclaimer() {}
        override fun customAgentSystemPromptText() = ""
        override fun customTextTransformationSystemPromptText() = ""
        override fun builtInAgentSystemPromptText() = ""
        override fun builtInTextTransformationSystemPromptText() = ""
        override fun refresh() {}
    }
    private fun controller(fake: Fake, nav: (String) -> Unit = {}) =
        AiConnectionSettingsController(fake, kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()), AiConnectionLabels.forTest(), nav)

    @Test fun noProviders_showsGettingStarted_hidesAdvancedCategories() = runTest {
        val c = controller(Fake(snap(providers = 0)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertTrue(AiConnectionNav.EASY_SETUP in keys)
        assertFalse(AiConnectionNav.MODELS in keys)   // models shortcut hidden until providers exist
    }
    @Test fun noProviders_stillShowsProvidersShortcutAndCategory() = runTest {
        // Classic keeps "Providers & models" category + the Providers shortcut always visible,
        // even with zero providers configured, so the user can always reach the providers screen.
        val c = controller(Fake(snap(providers = 0)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertTrue("ai_providers_models_category" in keys)
        assertTrue(AiConnectionNav.PROVIDERS in keys)
    }
    @Test fun hasProviders_hidesGettingStarted_showsShortcuts() = runTest {
        val c = controller(Fake(snap(providers = 2)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertFalse(AiConnectionNav.EASY_SETUP in keys)
        assertTrue(AiConnectionNav.PROVIDERS in keys)
        assertTrue(AiConnectionNav.MODELS in keys)
    }
    @Test fun hasProviders_showsResetUsageRow() = runTest {
        val c = controller(Fake(snap(providers = 1)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertTrue(AiConnectionNav.RESET_USAGE in keys)
    }
    @Test fun disclaimerWarning_alwaysVisible_regardlessOfAcceptance() = runTest {
        val accepted = controller(Fake(snap().copy(disclaimerAccepted = true)))
        val notAccepted = controller(Fake(snap().copy(disclaimerAccepted = false)))
        assertTrue("ai_disclaimer_warning" in accepted.state.value.visibleItems.map { it.key })
        assertTrue("ai_disclaimer_warning" in notAccepted.state.value.visibleItems.map { it.key })
    }
    @Test fun onListChoice_routesToService() = runTest {
        val f = Fake(snap()); val c = controller(f)
        val target = AgentPermissionModeIds.ordered.last()
        c.onListChoice("agent_permission_mode", target)
        assertEquals(target, f.lastMode)
    }
    @Test fun onSwitch_routesAskModel() = runTest {
        val f = Fake(snap()); val c = controller(f)
        c.onSwitch("ask_model_before_run", true)
        assertEquals(true, f.lastAsk)
    }
    @Test fun onNavigate_forwardsKey() = runTest {
        var got: String? = null
        val c = controller(Fake(snap(providers = 2))) { got = it }
        c.onNavigate(AiConnectionNav.PROVIDERS)
        assertEquals(AiConnectionNav.PROVIDERS, got)
    }
    @Test fun snapshotChange_rebuildsState() = runTest {
        val f = Fake(snap(providers = 0)); val c = controller(f)
        assertTrue(AiConnectionNav.EASY_SETUP in c.state.value.visibleItems.map { it.key })
        f.flow.value = snap(providers = 1)
        assertFalse(AiConnectionNav.EASY_SETUP in c.state.value.visibleItems.map { it.key })
    }
}
