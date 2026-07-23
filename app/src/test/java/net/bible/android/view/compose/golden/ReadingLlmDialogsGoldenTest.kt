package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.reading.*
import net.bible.sharedui.ai.reading.ReadingLlmDialogs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingLlmDialogsGoldenTest {

    private val groups = listOf(
        ReadingPromptGroupVd("Favorites", ReadingLlmService.FAVORITES_CATEGORY_ID, isFavorites = true, collapsed = false,
            prompts = listOf(ReadingPromptVd("f1", "Explain verse", "Explain the selected verse", isFavorite = true, specifyBeforeRun = false))),
        ReadingPromptGroupVd("Study", "c1", isFavorites = false, collapsed = false,
            prompts = listOf(
                ReadingPromptVd("p1", "Cross references", "Find cross references", isFavorite = false, specifyBeforeRun = false),
                ReadingPromptVd("p2", "Custom question", "Ask your own question", isFavorite = false, specifyBeforeRun = true))),
        ReadingPromptGroupVd("Collapsed group", "c2", isFavorites = false, collapsed = true,
            prompts = listOf(ReadingPromptVd("p3", "Hidden", "", isFavorite = false, specifyBeforeRun = false))),
    )
    private val models = listOf(
        ReadingModelVd("m1", "gpt-4o", "OpenAI", isDefault = true, supported = true),
        ReadingModelVd("m2", "claude-3", "Anthropic", isDefault = false, supported = false),
    )
    private fun dialogs(d: ReadingLlmDialog) = @Composable {
        ReadingLlmDialogs(d, onPromptChosen = {}, onToggleFavorite = {}, onCategoryExpandedChanged = { _, _ -> },
            onSpecifySubmitted = {}, onModelChosen = { _, _ -> }, onRegenerateConfirmed = { _, _, _ -> }, onDismiss = {})
    }

    @Test fun promptSelector_matrix() = captureMatrix("ReadingLlmDialogs", "promptSelector") { dialogs(ReadingLlmDialog.PromptSelector(groups))() }
    @Test @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun promptSelector_rtl() = captureRtl("ReadingLlmDialogs", "promptSelector") { dialogs(ReadingLlmDialog.PromptSelector(groups))() }
    @Test fun specify_matrix() = captureMatrix("ReadingLlmDialogs", "specify") { dialogs(ReadingLlmDialog.SpecifyBeforeRun("p2", "Custom question"))() }
    @Test fun modelSelection_matrix() = captureMatrix("ReadingLlmDialogs", "modelSelection") { dialogs(ReadingLlmDialog.ModelSelection(models, allowSetDefault = true))() }
    @Test fun regenerate_matrix() = captureMatrix("ReadingLlmDialogs", "regenerate") { dialogs(ReadingLlmDialog.Regenerate("page1"))() }
    @Test fun promptSelector_empty() = captureGolden("ReadingLlmDialogs", "promptSelectorEmpty", EDGE_MODE) {
        dialogs(ReadingLlmDialog.PromptSelector(listOf(ReadingPromptGroupVd("Uncategorized", null, false, false, emptyList()))))()
    }
}
