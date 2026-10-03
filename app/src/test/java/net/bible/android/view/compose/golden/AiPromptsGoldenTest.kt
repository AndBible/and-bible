package net.bible.android.view.compose.golden

import net.bible.sharedcore.docs.DocsLinks
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptListFilter
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedui.ai.AiPromptsScreen
import net.bible.sharedui.ai.PromptFilterSheetContent
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
        initiallySearchOpen: Boolean = false,
        initiallyFilter: PromptListFilter = PromptListFilter(),
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
                helpReadMoreUrl = DocsLinks.page("ai"),
                initiallyHelpDialogOpen = initiallyHelpDialogOpen,
                initiallyOverflowMenuOpen = initiallyOverflowMenuOpen,
                initiallySearchOpen = initiallySearchOpen,
                initiallyFilter = initiallyFilter,
            )
        }

    private val summaryCat = PromptCategoryVd(id = "cat-summary", name = "Summarization", isBuiltIn = false, isHidden = false)

    /** Favorites (virtual group, non-empty) + one real category (with a hidden built-in prompt,
     *  shown because showHidden=true) + the uncategorized bucket. A favorited prompt legitimately
     *  appears in both the Favorites group and its own category group (independent listings).
     *
     *  17f/B4: three prompts carry the row's new third meta line, one marking each of the
     *  ways it can render — a wrong meta-line implementation would visibly change this capture:
     *  - `p-explain`: `isBuiltIn=true`, no `sourceModule` -> renders the "Built-in" type badge alone.
     *  - `p-summarize`: `sourceModule` set (`sourceModule` wins over `isBuiltIn` in `promptTypeOf`)
     *    -> renders "Add-on: <module>" instead of "Built-in", alongside its existing hidden dimming.
     *  - `p-crossref`: a plain user prompt (no type marking) with `contexts` set -> renders only the
     *    target list ("Verse selection, Note editor"), proving the targets render independently of
     *    any type badge. */
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
                    contexts = setOf("VERSE_SELECTION", "NOTE_EDITOR"),
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
                    sourceModule = "Commentary Pack",
                ),
                PromptVd(
                    id = "p-crossref", name = "Cross references", description = "Finds related cross references",
                    categoryId = summaryCat.id, isBuiltIn = false, isReadOnly = false, isFavorite = true, isHidden = false,
                    contexts = setOf("VERSE_SELECTION", "NOTE_EDITOR"),
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
    // multi-window-capture hang. Dialog-open goldens (AbInfoDialog/AbConfirmDialog)
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
    // review; the "Move to category…" picker itself is now a `ModalBottomSheet` (round 14a, G2.3),
    // so it can no longer be captured through this screen at all — same D6 prohibition, same hang.
    // Its body is covered instead by AbSheetWrappersGoldenTest.choice_matrix. Re-attempt a per-row-popup golden only
    // after a Roborazzi/Robolectric upgrade, and consider rendering the row in isolation rather than
    // inside the full scrollable list.

    // 17f/B5: the search bar's inline search mode (icon -> text field + filter action), captured via
    // the initiallySearchOpen test seam rather than by driving the icon click -- same pattern as every
    // other initiallyXxx seam in this file. Never captures the filter sheet itself (an open
    // ModalBottomSheet hangs Roborazzi, see the F38/F40 notes above); that is PromptFilterSheetContent
    // below.
    //
    // Final-review fix M2: also seeds initiallyFilter = favoritesOnly, so this one golden exercises
    // BOTH the filter-active indicator (FilterAlt, not FilterAltOff -- see FilterAction) and an
    // actually-narrowed list (configuredGroups() has two favorited prompts out of four total; a wrong
    // filter wiring, e.g. the icon flipping without the list actually narrowing, would visibly fail
    // this capture where an unfiltered "search open" golden could not).
    @Test fun configured_searchOpen_matrix() =
        captureMatrix(
            "AiPrompts", "search_open",
            heightDp = 900,
            content = screen(
                configured = true, groups = configuredGroups(), showHidden = true,
                hasHiddenPrompts = true, initiallySearchOpen = true,
                initiallyFilter = PromptListFilter(favoritesOnly = true),
            ),
        )

    /** [PromptFilterSheetContent] with favoritesOnly AND one "Show in" context pre-selected: both
     *  land in rows that render ABOVE `AbSheetScrollBound`'s 400dp fold at the default (top) scroll
     *  position, unlike the `types` dimension (fix round 1) which sits below the fold in this
     *  fixture's content and is therefore invisible in the recorded PNG no matter how tall the
     *  capture's own canvas is made. `contexts = {VERSE_SELECTION}` selects "Verse selection" among
     *  four unselected siblings ("Text selection"/"Window menu"/"Workspace menu"/"Note editor"),
     *  proving a selection-highlight bug (e.g. the wrong dimension wired to the wrong chip's
     *  `selected`) would visibly fail this capture -- an all-unselected sheet could not. Wrapped in
     *  [SheetSurface] (defined in AbSheetWrappersGoldenTest.kt), never in an open ModalBottomSheet.
     *
     *  **Widened to a matrix (final-review fix I3b):** was `captureGolden(..., EDGE_MODE, ...)`, light
     *  theme only -- the chip check-icon added by I3a is exactly the kind of selection cue that needs
     *  checking in monochrome/e-ink too (CLAUDE.md's "Theme and Display Modes"), so this now captures
     *  bw/dark/eink/light like every other `_matrix` test in this file. */
    @Test fun filterSheetContent_matrix() = captureMatrix("AiPrompts", "filter_sheet", heightDp = 520) {
        SheetSurface {
            PromptFilterSheetContent(
                filter = PromptListFilter(favoritesOnly = true, contexts = setOf("VERSE_SELECTION")),
                categories = listOf(summaryCat),
                onApply = {},
                onClose = {},
            )
        }
    }
}
