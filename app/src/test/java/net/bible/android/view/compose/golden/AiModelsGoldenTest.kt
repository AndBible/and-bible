package net.bible.android.view.compose.golden

import net.bible.sharedcore.docs.DocsLinks
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.AvailableModelVd
import net.bible.sharedcore.ai.ModelEditState
import net.bible.sharedcore.ai.ModelVd
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedui.ai.AiModelsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiModelsGoldenTest {

    private val providers = listOf(
        ProviderVd(id = "p1", displayName = "OpenAI", providerTypeId = "OPENAI", apiKeySet = true, isCustom = false, endpoint = "", apiFormatId = ""),
        ProviderVd(id = "p2", displayName = "Anthropic", providerTypeId = "ANTHROPIC", apiKeySet = true, isCustom = false, endpoint = "", apiFormatId = ""),
        ProviderVd(
            id = "p3", displayName = "My Custom LLM", providerTypeId = "CUSTOM", apiKeySet = false,
            isCustom = true, endpoint = "https://api.example.com/v1", apiFormatId = "OPENAI",
        ),
    )

    /** Default (★) + supported (✓) + a third with unknown/custom pricing, no badges. */
    private val models = listOf(
        ModelVd(
            id = "m1", modelId = "gpt-4o", displayName = "GPT-4o", providerId = "p1",
            isDefault = true, supported = true, pricingSummary = "\$5.00 / \$15.00 per 1M tokens",
        ),
        ModelVd(
            id = "m2", modelId = "claude-3-5-sonnet", displayName = "Claude 3.5 Sonnet", providerId = "p2",
            isDefault = false, supported = true, pricingSummary = "\$3.00 / \$15.00 per 1M tokens",
        ),
        ModelVd(
            id = "m3", modelId = "custom-model", displayName = "Custom Model", providerId = "p3",
            isDefault = false, supported = false, pricingSummary = "\$1.00 / \$2.00 per 1M tokens",
            priceInput = "1.00", priceOutput = "2.00",
        ),
    )

    /** Add flow, PICK_MODEL step, provider p1, a mix of supported/unsupported + categorized
     *  (OpenRouter-style "category/model") available models — reveals the category dropdown and
     *  the show-unsupported switch — with CUSTOM_MODEL_ID picked, revealing the free-text custom
     *  model id field + editable price fields. */
    private val addCustomEditState = ModelEditState(
        id = null,
        step = ModelEditState.Step.PICK_MODEL,
        providerChoices = providers,
        providerId = "p1",
        availableModels = listOf(
            AvailableModelVd(modelId = "openai/gpt-4-turbo", label = "GPT-4 Turbo", supported = true, knownPricing = true),
            AvailableModelVd(modelId = "anthropic/claude-3-opus", label = "Claude 3 Opus", supported = false, knownPricing = true),
            AvailableModelVd(modelId = "gpt-3.5-turbo", label = "GPT-3.5 Turbo", supported = true, knownPricing = true),
        ),
        loadingModels = false,
        modelId = AiModelsController.CUSTOM_MODEL_ID,
        customModelId = "my-custom-model-id",
        priceInput = "0.50",
        priceOutput = "1.50",
        setAsDefault = false,
        showUnsupported = false,
        isCustom = true,
        showPriceFields = true,
        canSave = true,
    )

    private fun screen(editState: ModelEditState?, initiallyHelpDialogOpen: Boolean = false) = @androidx.compose.runtime.Composable {
        AiModelsScreen(
            models = models,
            providers = providers,
            editState = editState,
            onUp = {},
            onAdd = {},
            onPickProvider = {},
            onPickModel = {},
            onStartEdit = {},
            onField = { _, _ -> },
            onSave = {},
            onDelete = {},
            onSetDefault = {},
            onSetAsDefault = {},
            onSetShowUnsupported = {},
            onDismiss = {},
            helpBody = "AI Models lets you add and configure the specific models available from your providers.",
            helpReadMoreUrl = DocsLinks.page("ai", "available-models"),
            initiallyHelpDialogOpen = initiallyHelpDialogOpen,
        )
    }

    @Test fun populated_matrix() =
        captureMatrix("AiModels", "populated", content = screen(null))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun populated_rtl() =
        captureRtl("AiModels", "populated", content = screen(null))

    // heightDp=1000: the add-custom PICK_MODEL form stacks provider/category/show-unsupported
    // switch/picker/custom-id field/price fields/set-default switch — taller than the default
    // viewport allows without clipping the trailing set-default switch.
    @Test fun add_custom_matrix() =
        captureMatrix("AiModels", "add_custom", heightDp = 1000, content = screen(addCustomEditState))

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog.
    @Test fun help_matrix() =
        captureMatrix("AiModels", "help", content = screen(null, initiallyHelpDialogOpen = true))
}
