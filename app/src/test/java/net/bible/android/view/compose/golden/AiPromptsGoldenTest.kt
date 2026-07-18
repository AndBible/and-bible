package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedui.ai.AiPromptsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiPromptsGoldenTest {

    private fun screen(configured: Boolean, groups: List<PromptGroupVd> = emptyList(), showHidden: Boolean = false) =
        @androidx.compose.runtime.Composable {
            AiPromptsScreen(
                configured = configured,
                groups = groups,
                showHidden = showHidden,
                onUp = {},
                onOpenPrompt = {},
                onNewPrompt = {},
                onToggleFavorite = {},
                onSetPromptHidden = { _, _ -> },
                onSetCategoryHidden = { _, _ -> },
                onDeletePrompt = {},
                onDeleteCategory = { _, _ -> },
                onMovePrompt = { _, _ -> },
                onMoveCategory = { _, _ -> },
                onCreateCategory = {},
                onRenameCategory = { _, _ -> },
                onSetShowHidden = {},
                onOpenConnectionSettings = {},
                onImportCsv = {},
                onExportCsv = {},
                onHelp = {},
            )
        }

    private val summaryCat = PromptCategoryVd(id = "cat-summary", name = "Summarization", isBuiltIn = false, isHidden = false)

    /** Favorites (virtual group, non-empty) + one real category (with a hidden built-in prompt,
     *  shown because showHidden=true) + the uncategorized bucket. A favorited prompt legitimately
     *  appears in both the Favorites group and its own category group (independent listings). */
    private fun configuredGroups(): List<PromptGroupVd> = listOf(
        PromptGroupVd(
            category = null,
            isFavorites = true,
            prompts = listOf(
                PromptVd(
                    id = "p-explain", name = "Explain passage",
                    description = "Explains the selected passage in plain language",
                    categoryId = null, isBuiltIn = true, isReadOnly = true, isFavorite = true, isHidden = false,
                ),
                PromptVd(
                    id = "p-crossref", name = "Cross references", description = "Finds related cross references",
                    categoryId = summaryCat.id, isBuiltIn = false, isReadOnly = false, isFavorite = true, isHidden = false,
                ),
            ),
        ),
        PromptGroupVd(
            category = summaryCat,
            isFavorites = false,
            prompts = listOf(
                PromptVd(
                    id = "p-summarize", name = "Summarize chapter", description = "Summarizes the current chapter",
                    categoryId = summaryCat.id, isBuiltIn = true, isReadOnly = true, isFavorite = false, isHidden = true,
                ),
                PromptVd(
                    id = "p-crossref", name = "Cross references", description = "Finds related cross references",
                    categoryId = summaryCat.id, isBuiltIn = false, isReadOnly = false, isFavorite = true, isHidden = false,
                ),
            ),
        ),
        PromptGroupVd(
            category = null,
            isFavorites = false,
            prompts = listOf(
                PromptVd(
                    id = "p-mynotes", name = "My custom prompt", description = "A user-authored uncategorized prompt",
                    categoryId = null, isBuiltIn = false, isReadOnly = false, isFavorite = false, isHidden = false,
                ),
            ),
        ),
    )

    /** Mirrors classic `manage_prompts.xml` child 0: not yet configured -> centered CTA only. */
    @Test fun notConfigured() =
        captureGolden("AiPrompts", "notconfigured", EDGE_MODE, content = screen(configured = false))

    // heightDp=900: 3 groups (Favorites/Summarization/uncategorized) x up to 2 rows each, plus
    // headers/dividers; the default viewport would clip the uncategorized bucket at the bottom.
    @Test fun configured_matrix() =
        captureMatrix("AiPrompts", "configured", heightDp = 900, content = screen(configured = true, groups = configuredGroups(), showHidden = true))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun configured_rtl() =
        captureRtl("AiPrompts", "configured", heightDp = 900, content = screen(configured = true, groups = configuredGroups(), showHidden = true))

    /** configured=true but no prompts at all in any group -> the screen's own empty-summary text,
     *  distinct from the not-configured CTA above. */
    @Test fun configured_empty() =
        captureGolden("AiPrompts", "configured_empty", EDGE_MODE, content = screen(configured = true, groups = emptyList()))
}
