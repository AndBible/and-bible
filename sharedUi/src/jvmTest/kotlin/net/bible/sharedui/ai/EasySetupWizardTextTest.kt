package net.bible.sharedui.ai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.ai.RecommendedSetupVd
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wizard's API-key field is host-owned: the production host (`AiNavGraph`) keeps [EasySetupState] in local state and
 * updates it synchronously in `onKeyChange`; the controller is only called on test/confirm. There is no async echo to
 * hold, so this test mirrors the production host (immediate update) and is expected to be GREEN before any change:
 * "not reproducible", production left unchanged. Fixture key strings only.
 */
@OptIn(ExperimentalTestApi::class)
class EasySetupWizardTextTest {
    private val strings = iosStrings("en")
    private val setup = RecommendedSetupVd("s1", "Setup one", "openai", "model-1", null)

    @Test fun apiKeyKeepsBothKeystrokesWithTheHostOwnedState() = runComposeUiTest {
        var state by mutableStateOf(EasySetupState(EasySetupStep.ENTER_KEY, listOf(setup), selectedSetupId = "s1"))
        val received = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    EasySetupWizard(
                        state = state, onPick = {},
                        onKeyChange = { k -> received += k; state = state.copy(apiKey = k, testResult = null) },
                        onTest = {}, onConfirm = {}, onDismiss = {},
                    )
                }
            }
        }
        val node = onNode(hasSetTextAction() and hasText(strings.providerApiKeyLabel))
        node.performClick()
        node.performTextInput("k")
        waitForIdle()
        node.performTextInput("1")
        waitForIdle()
        assertEquals("k1", state.apiKey)
        assertEquals(listOf("k", "k1"), received)
    }
}
