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
        categories: List<PromptCategoryVd> = emptyList(),
        initiallyHelpDialogOpen: Boolean = false,
        initiallyOverflowMenuOpen: Boolean = false,
        initiallyMoveToCategoryPromptId: String? = null,
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
                onCopyPrompt = {},
                onMovePromptToCategory = { _, _ -> },
                categoriesProvider = { categories },
                helpBody = "AI Settings is where you manage your prompts and categories.",
                helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
                initiallyHelpDialogOpen = initiallyHelpDialogOpen,
                initiallyOverflowMenuOpen = initiallyOverflowMenuOpen,
                initiallyMoveToCategoryPromptId = initiallyMoveToCategoryPromptId,
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

    // F38: the overflow's show/hide-hidden toggle (present + aligned only when a hidden built-in
    // prompt exists) was previously golden-covered by force-opening the top-bar AbOverflowMenu
    // (initiallyOverflowMenuOpen = true). Those two goldens (overflow_open_hidden /
    // overflow_open_nohidden) are REMOVED: force-opening a Compose DropdownMenu (a Popup rendered
    // in its own window) intermittently hangs Roborazzi's captureScreenIfMultipleWindows in this
    // Robolectric/Roborazzi version — the "Main Thread" spins in ShadowPausedLooper.idle() near
    // 100% CPU and never converges (same signature as the dropped F40 per-row-overflow golden;
    // confirmed via jstack). The toggle's presence/absence gating is verified by
    // AiPromptsControllerTest (hasHiddenPrompts), and its 48dp leading-slot alignment by code
    // review; the AbOverflowMenu itself is proven elsewhere. Re-introduce a popup-open golden only
    // after a Roborazzi/Robolectric upgrade (or a per-test JVM fork, forkEvery=1) resolves the
    // multi-window-capture hang. Dialog-open goldens (AbInfoDialog/AbConfirmDialog/AbListChoiceDialog)
    // are NOT affected and remain covered below.

    // F40: NOTE — a golden that force-opens a PER-ROW PromptRowOverflow (the DropdownMenu nested
    // inside a LazyColumn item, as opposed to the top-bar AbOverflowMenu) was attempted here and
    // dropped: it reproducibly hung the Robolectric/Roborazzi Compose capture (confirmed via TWO
    // separate jstack captures — the "SDK NN Main Thread" spins indefinitely in
    // ShadowPausedLooper.idle() under RoborazziKt.captureScreenIfMultipleWindows, pinned near 100%
    // CPU, never converging). This reproduced on two DIFFERENT target rows across two runs (once
    // with a duplicated prompt id forcing two simultaneous popups open, once with a demonstrably
    // unique id) — i.e. a Popup anchored to a LazyColumn item is unstable to force-open for golden
    // capture in this harness version, independent of the specific row. The underlying
    // PromptRowOverflow code itself (Copy + Move to category… items, gating) is unchanged in
    // structure from the already-proven CategoryRowOverflow/AbOverflowMenu pattern and is verified
    // by the AiPromptsControllerTest unit tests (onCopyPrompt/onMovePromptToCategory) plus code
    // review; the "Move to category…" DIALOG itself (a plain top-level AlertDialog, NOT nested in a
    // LazyColumn) is verified below and captures reliably. Re-attempt a per-row-popup golden only
    // after a Roborazzi/Robolectric upgrade, and consider rendering the row in isolation rather than
    // inside the full scrollable list.

    // F40: the "Move to category…" picker (AbListChoiceDialog) opened for a user prompt already in
    // "Summarization" — single-choice list of "(uncategorized)" + all categories, pre-selecting the
    // prompt's current category.
    @Test fun configured_moveToCategoryDialog_matrix() =
        captureMatrix(
            "AiPrompts", "move_to_category_dialog",
            heightDp = 900,
            content = screen(
                configured = true, groups = configuredGroups(), showHidden = true,
                hasHiddenPrompts = true, categories = listOf(summaryCat),
                initiallyMoveToCategoryPromptId = "p-crossref",
            ),
        )
}
