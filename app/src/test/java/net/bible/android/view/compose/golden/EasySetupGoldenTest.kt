package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.RecommendedSetupVd
import net.bible.sharedui.ai.EasySetupState
import net.bible.sharedui.ai.EasySetupStep
import net.bible.sharedui.ai.EasySetupWizard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class EasySetupGoldenTest {

    private val setups = listOf(
        RecommendedSetupVd(
            id = "openai-gpt4o", label = "OpenAI (GPT-4o)", providerTypeId = "OPENAI",
            modelId = "gpt-4o", apiKeyUrl = "https://platform.openai.com/api-keys",
        ),
        RecommendedSetupVd(
            id = "anthropic-sonnet", label = "Anthropic (Claude 3.5 Sonnet)", providerTypeId = "ANTHROPIC",
            modelId = "claude-3-5-sonnet", apiKeyUrl = "https://console.anthropic.com/settings/keys",
        ),
    )

    /** Step 2 (ENTER_KEY) with a chosen setup: api-key field prefilled, plus the api-key
     *  instructions link and the "Test connection" action (idle — not testing, no result yet). */
    private val enterKeyState = EasySetupState(
        step = EasySetupStep.ENTER_KEY,
        setups = setups,
        selectedSetupId = "openai-gpt4o",
        apiKey = "sk-test-1234567890",
        testing = false,
        testResult = null,
    )

    private fun screen(state: EasySetupState) = @androidx.compose.runtime.Composable {
        EasySetupWizard(
            state = state,
            onPick = {},
            onKeyChange = {},
            onTest = {},
            onConfirm = {},
            onDismiss = {},
        )
    }

    @Test fun enterkey_matrix() =
        captureMatrix("EasySetup", "enterkey", content = screen(enterKeyState))
}
