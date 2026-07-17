package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.agentPermissionModeChoices
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.ai.AiConnectionSettingsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiConnectionSettingsGoldenTest {

    /** Mirrors `AiConnectionSettingsController.build()` with providers configured (hasProviders=true):
     *  every gated item visible, populated with representative values. */
    private fun configuredState() = SettingsScreenState(
        title = "AI connection settings",
        items = listOf(
            SettingsItem.InfoRow(key = "ai_disclaimer_warning", title = "Disclaimer", visible = true),
            SettingsItem.NavigationRow(
                key = "easy_setup", title = "Getting started", summary = "Set up your first AI provider",
                visible = false,
            ),
            SettingsItem.Category(key = "ai_providers_models_category", title = "Providers & models", visible = true),
            SettingsItem.NavigationRow(key = "providers", title = "Providers", summary = "2", visible = true),
            SettingsItem.NavigationRow(key = "models", title = "Models", summary = "GPT-4o", visible = true),
            SettingsItem.Category(key = "ai_behavior_category", title = "Behavior", visible = true),
            SettingsItem.ListChoiceRow(
                key = "ai_language", title = "AI language", summary = "English",
                entries = emptyList(), selectedValue = "en", visible = true,
            ),
            SettingsItem.ListChoiceRow(
                key = "agent_permission_mode", title = "Tool permission mode", summary = "Ask every time",
                entries = agentPermissionModeChoices(
                    mapOf(
                        "always_ask" to "Ask every time",
                        "auto_safe" to "Auto-approve safe tools",
                        "auto_all" to "Auto-approve all tools",
                    ),
                ),
                selectedValue = "always_ask", visible = true,
            ),
            SettingsItem.NavigationRow(
                key = "tool_permissions", title = "Tool permissions", summary = "Manage tool permissions (3)",
                visible = true,
            ),
            SettingsItem.NavigationRow(
                key = "documents", title = "Documents", summary = "Manage documents available to AI", visible = true,
            ),
            SettingsItem.TextInputRow(
                key = "commentary_max_response", title = "Commentary max response", summary = "2000 tokens",
                value = "2000", numeric = true, visible = true,
            ),
            SettingsItem.TextInputRow(
                key = "agent_max_iterations", title = "Max iterations", summary = "Max agent iterations (10)",
                value = "10", numeric = true, visible = true,
            ),
            SettingsItem.SwitchRow(
                key = "ask_model_before_run", title = "Ask before running",
                summary = "Show a model selection dialog before executing a prompt", checked = true, visible = true,
            ),
            SettingsItem.SwitchRow(
                key = "auto_hide_agent_log", title = "Auto-hide agent log",
                summary = "Automatically hide the AI panel when a task finishes", checked = false, visible = true,
            ),
            SettingsItem.Category(key = "ai_advanced_category", title = "Advanced", visible = true),
            SettingsItem.TextInputRow(
                key = "custom_agent_prompt", title = "Custom agent system prompt", summary = "Custom",
                value = "", visible = true,
            ),
            SettingsItem.TextInputRow(
                key = "custom_text_transform_prompt", title = "Custom text transformation system prompt",
                summary = "Default", value = "", visible = true,
            ),
            SettingsItem.Category(key = "ai_usage_category", title = "Usage", visible = true),
            SettingsItem.InfoRow(
                key = "usage_summary", title = "Usage summary", summary = "12,345 tokens · \$0.34", visible = true,
            ),
            SettingsItem.NavigationRow(
                key = "reset_usage", title = "Reset usage data", summary = "Clear cumulative token and cost tracking",
                visible = true,
            ),
            SettingsItem.NavigationRow(
                key = "raw_log_history", title = "Raw log history",
                summary = "View and manage saved AI conversation logs", visible = true,
            ),
            SettingsItem.TextInputRow(
                key = "raw_log_retention", title = "Raw log retention", summary = "30 days", value = "30",
                numeric = true, visible = true,
            ),
        ),
    )

    /** Mirrors `AiConnectionSettingsController.build()` with no providers (hasProviders=false):
     *  only the getting-started row + the always-visible disclaimer/providers-category/PROVIDERS
     *  shortcut are visible; everything gated on `hasProviders` is not. */
    private fun noProvidersState() = SettingsScreenState(
        title = "AI connection settings",
        items = listOf(
            SettingsItem.InfoRow(key = "ai_disclaimer_warning", title = "Disclaimer", visible = true),
            SettingsItem.NavigationRow(
                key = "easy_setup", title = "Getting started", summary = "Set up your first AI provider",
                visible = true,
            ),
            SettingsItem.Category(key = "ai_providers_models_category", title = "Providers & models", visible = true),
            SettingsItem.NavigationRow(key = "providers", title = "Providers", summary = "No providers configured", visible = true),
            SettingsItem.NavigationRow(key = "models", title = "Models", summary = "No models configured", visible = false),
            SettingsItem.Category(key = "ai_behavior_category", title = "Behavior", visible = false),
            SettingsItem.ListChoiceRow(
                key = "ai_language", title = "AI language", summary = "English",
                entries = emptyList(), selectedValue = "en", visible = false,
            ),
            SettingsItem.ListChoiceRow(
                key = "agent_permission_mode", title = "Tool permission mode", summary = "Ask every time",
                entries = emptyList(), selectedValue = "always_ask", visible = false,
            ),
            SettingsItem.NavigationRow(key = "tool_permissions", title = "Tool permissions", summary = "Manage tool permissions", visible = false),
            SettingsItem.NavigationRow(key = "documents", title = "Documents", summary = "Manage documents available to AI", visible = false),
            SettingsItem.TextInputRow(
                key = "commentary_max_response", title = "Commentary max response", summary = "No limit",
                value = "0", numeric = true, visible = false,
            ),
            SettingsItem.TextInputRow(
                key = "agent_max_iterations", title = "Max iterations", summary = "Max agent iterations (unlimited)",
                value = "0", numeric = true, visible = false,
            ),
            SettingsItem.SwitchRow(
                key = "ask_model_before_run", title = "Ask before running",
                summary = "Show a model selection dialog before executing a prompt", checked = true, visible = false,
            ),
            SettingsItem.SwitchRow(
                key = "auto_hide_agent_log", title = "Auto-hide agent log",
                summary = "Automatically hide the AI panel when a task finishes", checked = false, visible = false,
            ),
            SettingsItem.Category(key = "ai_advanced_category", title = "Advanced", visible = false),
            SettingsItem.TextInputRow(
                key = "custom_agent_prompt", title = "Custom agent system prompt", summary = "Default", value = "",
                visible = false,
            ),
            SettingsItem.TextInputRow(
                key = "custom_text_transform_prompt", title = "Custom text transformation system prompt",
                summary = "Default", value = "", visible = false,
            ),
            SettingsItem.Category(key = "ai_usage_category", title = "Usage", visible = false),
            SettingsItem.InfoRow(key = "usage_summary", title = "Usage summary", summary = "0 tokens · \$0.00", visible = false),
            SettingsItem.NavigationRow(key = "reset_usage", title = "Reset usage data", summary = "Clear cumulative token and cost tracking", visible = false),
            SettingsItem.NavigationRow(key = "raw_log_history", title = "Raw log history", summary = "View and manage saved AI conversation logs", visible = false),
            SettingsItem.TextInputRow(
                key = "raw_log_retention", title = "Raw log retention", summary = "30 days", value = "30",
                numeric = true, visible = false,
            ),
        ),
    )

    private fun screen(state: SettingsScreenState) = @androidx.compose.runtime.Composable {
        AiConnectionSettingsScreen(
            state = state,
            onUp = {},
            onSwitch = { _, _ -> },
            onListChoice = { _, _ -> },
            onTextInputInt = { _, _ -> },
            onCustomPromptSave = { _, _ -> },
            customPromptTextFor = { "" },
            onEditLanguage = {},
            onNavigate = {},
        )
    }

    // heightDp=1400: the configured state is a long list (categories + rows); the default viewport
    // clips well before the raw-log-retention row at the bottom.
    @Test fun configured_matrix() =
        captureMatrix("AiConnectionSettings", "configured", heightDp = 1400, content = screen(configuredState()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun configured_rtl() =
        captureRtl("AiConnectionSettings", "configured", heightDp = 1400, content = screen(configuredState()))

    // heightDp=600: only the disclaimer + getting-started row + providers category/shortcut render.
    @Test fun noproviders_matrix() =
        captureMatrix("AiConnectionSettings", "noproviders", heightDp = 600, content = screen(noProvidersState()))
}
