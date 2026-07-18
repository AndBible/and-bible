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

    private fun screen(
        configured: Boolean,
        groups: List<PromptGroupVd> = emptyList(),
        showHidden: Boolean = false,
        hasHiddenPrompts: Boolean = false,
        initiallyHelpDialogOpen: Boolean = false,
        initiallyOverflowMenuOpen: Boolean = false,
    ) =
        @androidx.compose.runtime.Composable {
            AiPromptsScreen(
                configured = configured,
                groups = groups,
                showHidden = showHidden,
                hasHiddenPrompts = hasHiddenPrompts,
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
                helpBody = "AI Settings is where you manage your prompts and categories.",
                helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
                initiallyHelpDialogOpen = initiallyHelpDialogOpen,
                initiallyOverflowMenuOpen = initiallyOverflowMenuOpen,
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

    /** Same shape as [configuredGroups] but with no hidden built-in prompt — for the F38
     *  no-hidden-prompts overflow case (toggle omitted). */
    private fun configuredGroupsNoHidden(): List<PromptGroupVd> = configuredGroups().map { group ->
        group.copy(prompts = group.prompts.map { it.copy(isHidden = false) })
    }

    /** Mirrors classic `manage_prompts.xml` child 0: not yet configured -> centered CTA only. */
    @Test fun notConfigured() =
        captureGolden("AiPrompts", "notconfigured", EDGE_MODE, content = screen(configured = false))

    // heightDp=900: 3 groups (Favorites/Summarization/uncategorized) x up to 2 rows each, plus
    // headers/dividers; the default viewport would clip the uncategorized bucket at the bottom.
    // hasHiddenPrompts=true: configuredGroups() has one hidden built-in prompt (p-summarize).
    @Test fun configured_matrix() =
        captureMatrix(
            "AiPrompts", "configured",
            heightDp = 900,
            content = screen(configured = true, groups = configuredGroups(), showHidden = true, hasHiddenPrompts = true),
        )

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun configured_rtl() =
        captureRtl(
            "AiPrompts", "configured",
            heightDp = 900,
            content = screen(configured = true, groups = configuredGroups(), showHidden = true, hasHiddenPrompts = true),
        )

    /** configured=true but no prompts at all in any group -> the screen's own empty-summary text,
     *  distinct from the not-configured CTA above. */
    @Test fun configured_empty() =
        captureGolden("AiPrompts", "configured_empty", EDGE_MODE, content = screen(configured = true, groups = emptyList()))

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog. heightDp=900 (same as "configured") so the
    // dialog renders over the full list.
    @Test fun configured_help_matrix() =
        captureMatrix(
            "AiPrompts", "help",
            heightDp = 900,
            content = screen(
                configured = true, groups = configuredGroups(), showHidden = true,
                hasHiddenPrompts = true, initiallyHelpDialogOpen = true,
            ),
        )

    // F38: the overflow's show/hide-hidden toggle is present (aligned with the plain items) only
    // when a hidden built-in prompt actually exists.
    @Test fun configured_overflowOpen_withHiddenPrompts_matrix() =
        captureMatrix(
            "AiPrompts", "overflow_open_hidden",
            heightDp = 900,
            content = screen(
                configured = true, groups = configuredGroups(), showHidden = true,
                hasHiddenPrompts = true, initiallyOverflowMenuOpen = true,
            ),
        )

    // F38: with no hidden built-in prompts, the toggle is omitted entirely (classic parity) — only
    // the plain items remain, all still sharing the same aligned leading-icon-slot inset.
    @Test fun configured_overflowOpen_noHiddenPrompts() =
        captureGolden(
            "AiPrompts", "overflow_open_nohidden", EDGE_MODE,
            heightDp = 900,
            content = screen(
                configured = true, groups = configuredGroupsNoHidden(), showHidden = false,
                hasHiddenPrompts = false, initiallyOverflowMenuOpen = true,
            ),
        )
}
