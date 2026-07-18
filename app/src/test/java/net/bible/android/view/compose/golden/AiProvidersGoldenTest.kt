package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.ProviderEditState
import net.bible.sharedcore.ai.ProviderTypeVd
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedui.ai.AiProvidersScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiProvidersGoldenTest {

    /** Two builtin providers (API key set) + one custom provider (endpoint, no key yet). */
    private val providers = listOf(
        ProviderVd(
            id = "p1", displayName = "OpenAI", providerTypeId = "OPENAI", apiKeySet = true,
            isCustom = false, endpoint = "", apiFormatId = "",
        ),
        ProviderVd(
            id = "p2", displayName = "Anthropic", providerTypeId = "ANTHROPIC", apiKeySet = true,
            isCustom = false, endpoint = "", apiFormatId = "",
        ),
        ProviderVd(
            id = "p3", displayName = "My Custom LLM", providerTypeId = "CUSTOM", apiKeySet = false,
            isCustom = true, endpoint = "https://api.example.com/v1", apiFormatId = "OPENAI",
        ),
    )

    private val providerTypes = listOf(
        ProviderTypeVd(
            id = "OPENAI", displayName = "OpenAI", tier = "RECOMMENDED",
            apiKeyUrl = "https://platform.openai.com/api-keys", defaultEndpoint = "https://api.openai.com/v1",
            supportsDynamicModels = true, isCustom = false,
        ),
        ProviderTypeVd(
            id = "ANTHROPIC", displayName = "Anthropic", tier = "RECOMMENDED",
            apiKeyUrl = "https://console.anthropic.com/settings/keys", defaultEndpoint = "https://api.anthropic.com/v1",
            supportsDynamicModels = false, isCustom = false,
        ),
        ProviderTypeVd(
            id = "CUSTOM", displayName = "Custom (OpenAI-compatible)", tier = "UNCATEGORIZED",
            apiKeyUrl = null, defaultEndpoint = "", supportsDynamicModels = false, isCustom = true,
        ),
    )

    /** FORM step, editing the custom provider `p3`: name/endpoint/api-format fields editable,
     *  a Delete action (id != null), and the api-key instructions link (apiKeyUrl on the type). */
    private val customEditState = ProviderEditState(
        id = "p3",
        step = ProviderEditState.Step.FORM,
        typeId = "CUSTOM",
        displayName = "My Custom LLM",
        apiKey = "sk-abc123",
        endpoint = "https://api.example.com/v1",
        apiFormatId = "OPENAI",
        isCustom = true,
        apiKeyUrl = "https://example.com/keys",
        canSave = true,
    )

    /** FORM step, editing the builtin provider `p1`: name field disabled (preset displayName),
     *  NO endpoint/api-format fields — materially different (shorter) dialog than the custom form. */
    private val builtinEditState = ProviderEditState(
        id = "p1",
        step = ProviderEditState.Step.FORM,
        typeId = "OPENAI",
        displayName = "OpenAI",
        apiKey = "sk-abc123",
        endpoint = "",
        apiFormatId = "",
        isCustom = false,
        apiKeyUrl = "https://platform.openai.com/api-keys",
        canSave = true,
    )

    private fun screen(
        editState: ProviderEditState?,
        items: List<ProviderVd> = providers,
        initiallyHelpDialogOpen: Boolean = false,
        showAcceptDisclaimerDialog: Boolean = false,
    ) = @androidx.compose.runtime.Composable {
        AiProvidersScreen(
            providers = items,
            providerTypes = providerTypes,
            editState = editState,
            onUp = {},
            onAdd = {},
            onPickType = {},
            onStartEdit = {},
            onField = { _, _ -> },
            onSave = {},
            onDelete = {},
            onDismiss = {},
            helpBody = "AI Providers is where you connect the app to an AI service such as OpenAI or Anthropic.",
            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#choosing-a-provider",
            initiallyHelpDialogOpen = initiallyHelpDialogOpen,
            showAcceptDisclaimerDialog = showAcceptDisclaimerDialog,
            onAcceptDisclaimer = {},
            onDismissAcceptDisclaimer = {},
        )
    }

    @Test fun populated_matrix() =
        captureMatrix("AiProviders", "populated", content = screen(null))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun populated_rtl() =
        captureRtl("AiProviders", "populated", content = screen(null))

    // heightDp=900: the custom-provider FORM has name/api-key/link/endpoint/description/dropdown —
    // taller than the default viewport allows without clipping the api-format dropdown.
    @Test fun edit_custom_matrix() =
        captureMatrix("AiProviders", "edit_custom", heightDp = 900, content = screen(customEditState))

    // Builtin-provider FORM is shorter (no endpoint/api-format fields) — default heightDp is fine.
    @Test fun edit_builtin_matrix() =
        captureMatrix("AiProviders", "edit_builtin", content = screen(builtinEditState))

    @Test fun empty() =
        captureGolden("AiProviders", "empty", EDGE_MODE, content = screen(null, items = emptyList()))

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog.
    @Test fun help_matrix() =
        captureMatrix("AiProviders", "help", content = screen(null, initiallyHelpDialogOpen = true))

    // F31: Quick-setup / Add-provider disclaimer gate now opens an AbConfirmDialog (replacing
    // classic's AlertDialog.Builder accept flow). AbConfirmDialog's message body is height-bounded
    // + scrollable (like AbInfoDialog), so the full multi-paragraph disclaimer no longer needs an
    // artificially tall viewport to keep the Accept/Cancel buttons reachable — heightDp=800 is a
    // realistic phone-portrait height (e.g. Pixel 6 ~412x915dp) and the buttons are visible.
    @Test fun accept_disclaimer_matrix() =
        captureMatrix(
            "AiProviders", "accept_disclaimer",
            heightDp = 800,
            content = screen(null, showAcceptDisclaimerDialog = true),
        )
}
