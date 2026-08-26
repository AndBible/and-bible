package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.reading.*
import net.bible.sharedui.ai.reading.ModelSelectionSheetContent
import net.bible.sharedui.ai.reading.PromptSelectorSheetContent
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

    // The two DIALOG arms still capture through the dispatcher — that is legitimate and unchanged.
    @Test fun specify_matrix() = captureMatrix("ReadingLlmDialogs", "specify") { dialogs(ReadingLlmDialog.SpecifyBeforeRun("p2", "Custom question"))() }
    @Test fun regenerate_matrix() = captureMatrix("ReadingLlmDialogs", "regenerate") { dialogs(ReadingLlmDialog.Regenerate("page1"))() }

    // The two SHEET arms (round 14a) capture their BODIES instead, in a Surface painted the colour a
    // ModalBottomSheet paints — `Modifier.abBottomFade` ramps from that colour to itself, so on the
    // harness's bare background the fade would prove nothing. Going through `ReadingLlmDialogs` for
    // these would open a real sheet and hang the whole `:app` suite; `SettingsEditorSheetGuardTest`
    // forbids it, by wrapper name AND by arm construction inside a capturing file.
    @Test fun promptSelectorSheet_matrix() = captureMatrix("ReadingLlmDialogs", "promptSelectorSheet", heightDp = 620) {
        SheetSurface { promptSelectorBody(groups) }
    }

    @Test @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun promptSelectorSheet_rtl() = captureRtl("ReadingLlmDialogs", "promptSelectorSheet", heightDp = 620) {
        SheetSurface { promptSelectorBody(groups) }
    }

    @Test fun promptSelectorSheet_empty() = captureGolden("ReadingLlmDialogs", "promptSelectorSheetEmpty", EDGE_MODE) {
        SheetSurface {
            promptSelectorBody(listOf(ReadingPromptGroupVd("Uncategorized", null, false, false, emptyList())))
        }
    }

    @Test fun modelSelectionSheet_matrix() = captureMatrix("ReadingLlmDialogs", "modelSelectionSheet", heightDp = 420) {
        SheetSurface {
            ModelSelectionSheetContent(
                models = models,
                allowSetDefault = true,
                onModelChosen = { _, _ -> },
                onClose = {},
            )
        }
    }

    @Composable
    private fun promptSelectorBody(groups: List<ReadingPromptGroupVd>) =
        PromptSelectorSheetContent(
            groups = groups,
            onPromptChosen = {},
            onToggleFavorite = {},
            onCategoryExpandedChanged = { _, _ -> },
            onClose = {},
        )
}
