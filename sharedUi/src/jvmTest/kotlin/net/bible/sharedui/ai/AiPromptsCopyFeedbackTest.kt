package net.bible.sharedui.ai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.runtime.Composable
import kotlin.test.assertEquals
import kotlin.test.assertNull
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assertCountEquals
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptListFilter
import net.bible.sharedcore.ai.PromptType
import net.bible.service.common.DisplayColorMode
import androidx.compose.foundation.lazy.LazyListState
import kotlin.test.assertTrue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AiPromptsCopyFeedbackTest {
    private val strings = iosStrings("en")
    private fun prompt(id: String) = PromptVd(
        id, id, "", null, isBuiltIn = false, isReadOnly = false, isFavorite = false, isHidden = false,
    )

    private fun isCopyFeedback() = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, strings.promptCopied)

    @Composable
    private fun TestScreen(
        groups: List<PromptGroupVd>,
        copiedId: String?,
        listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
        onRevealed: () -> Unit = {},
        filter: PromptListFilter = PromptListFilter(),
        searchOpen: Boolean = false,
        disableAnimations: Boolean = true,
    ) {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false, disableAnimations = disableAnimations) {
                Box(Modifier.height(350.dp)) {
                    AiPromptsScreen(
                        configured = true, groups = groups, showHidden = false, hasHiddenPrompts = false,
                        onUp = {}, onOpenPrompt = {}, onNewPrompt = {}, onToggleFavorite = {},
                        onSetPromptHidden = { _, _ -> }, onSetCategoryHidden = { _, _ -> },
                        onDeletePrompt = {}, onDeleteCategory = { _, _ -> }, onMovePrompt = { _, _ -> },
                        onMoveCategory = { _, _ -> }, onCreateCategory = {}, onRenameCategory = { _, _ -> },
                        onSetShowHidden = {}, onOpenConnectionSettings = {}, onImportCsv = {}, onExportCsv = {},
                        helpBody = "", helpReadMoreUrl = "", copiedPromptId = copiedId,
                        onCopyRevealed = onRevealed, listState = listState,
                        initiallyFilter = filter, initiallySearchOpen = searchOpen,
                    )
                }
            }
        }
    }

    @Test fun missingCopyExpiresAndLaterGroupChangesDoNotScrollBack() = runComposeUiTest {
        var prompts by mutableStateOf((0..30).map { prompt("Prompt $it") })
        var copiedId by mutableStateOf<String?>(null)
        val listState = LazyListState()
        setContent {
            TestScreen(listOf(PromptGroupVd(null, false, prompts)), copiedId, listState, { copiedId = null })
        }
        runOnIdle { copiedId = "Late copy" }
        // Coroutine timeouts use wall time, not Compose's animation clock.
        val started = System.nanoTime()
        waitUntil(timeoutMillis = 7000) { System.nanoTime() - started >= 5_500_000_000L }
        // The copy may have disappeared (e.g. another sync) before its list refresh arrived.
        runOnIdle { prompts = prompts + prompt("Late copy") }
        waitForIdle()
        runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex, "An expired copy must not steal the scroll position")
            assertNull(copiedId)
        }
    }

    @Test fun missingCopyInEmptyListAlsoExpires() = runComposeUiTest {
        var copiedId by mutableStateOf<String?>("Missing")
        setContent { TestScreen(emptyList(), copiedId, onRevealed = { copiedId = null }) }
        waitUntil(timeoutMillis = 7000) { copiedId == null }
    }

    @Test fun successfulCopyDoesNotRevealAgainAfterFavoriteChanges() = runComposeUiTest {
        var prompts by mutableStateOf((0..30).map { prompt("Prompt $it") })
        var copiedId by mutableStateOf<String?>(null)
        val listState = LazyListState()
        var reveals = 0
        setContent {
            TestScreen(listOf(PromptGroupVd(null, false, prompts)), copiedId, listState,
                { reveals++; copiedId = null })
        }
        runOnIdle { prompts = prompts + prompt("New copy"); copiedId = "New copy" }
        onNodeWithText("New copy").assertIsDisplayed()
        onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        runOnIdle { prompts = prompts.map { it.copy(isFavorite = true) } }
        waitForIdle()
        runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(1, reveals)
        }
    }

    @Test fun newerCopySupersedesAnUnresolvedCopy() = runComposeUiTest {
        var prompts by mutableStateOf((0..30).map { prompt("Prompt $it") })
        var copiedId by mutableStateOf<String?>(null)
        val listState = LazyListState()
        var reveals = 0
        setContent {
            TestScreen(listOf(PromptGroupVd(null, false, prompts)), copiedId, listState,
                { reveals++; copiedId = null })
        }
        runOnIdle { copiedId = "Never arrives" }
        waitForIdle()
        runOnIdle { copiedId = "Newest copy" }
        waitForIdle()
        runOnIdle { prompts = prompts + prompt("Newest copy") }
        onNodeWithText("Newest copy").assertIsDisplayed()
        runOnIdle { assertNull(copiedId); assertEquals(1, reveals) }
    }

    @Test fun favoriteOccurrenceChangingDuringAnimatedRevealUsesTheCurrentGroup() = runComposeUiTest {
        val copy = prompt("Moving copy").copy(isFavorite = true)
        var groups by mutableStateOf(listOf(
            PromptGroupVd(null, false, (0..30).map { prompt("Prompt $it") }),
        ))
        var copiedId by mutableStateOf<String?>(null)
        val listState = LazyListState()
        setContent {
            TestScreen(groups, copiedId, listState, { copiedId = null }, disableAnimations = false)
        }
        mainClock.autoAdvance = false
        runOnUiThread {
            groups = listOf(PromptGroupVd(null, false, (0..30).map { prompt("Prompt $it") } + copy))
            copiedId = copy.id
        }
        mainClock.advanceTimeUntil(timeoutMillis = 2000) { listState.isScrollInProgress }
        runOnUiThread {
            groups = listOf(PromptGroupVd(null, true, listOf(copy))) + groups
        }
        mainClock.autoAdvance = true
        waitForIdle()
        onNode(isCopyFeedback() and hasAnyDescendant(hasText(copy.name))).assertIsDisplayed()
        runOnIdle {
            assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.index == 1 })
            assertNull(copiedId)
        }
    }

    @Test fun copyKeepsSearchAndFilterWhenItAlreadyMatches() = runComposeUiTest {
        var prompts by mutableStateOf(listOf(prompt("Matching source"), prompt("Unrelated")))
        var copiedId by mutableStateOf<String?>(null)
        setContent {
            TestScreen(
                listOf(PromptGroupVd(null, false, prompts)), copiedId,
                onRevealed = { copiedId = null }, searchOpen = true,
                filter = PromptListFilter(types = setOf(PromptType.USER)),
            )
        }
        onNode(hasSetTextAction()).performTextInput("Matching")
        runOnIdle { prompts = prompts + prompt("Matching copy"); copiedId = "Matching copy" }
        onNodeWithText("Matching copy").assertIsDisplayed()
        onNode(hasSetTextAction()).assert(hasText("Matching"))
        onNodeWithText("Unrelated").assertDoesNotExist()
        // A later non-user entry still stays filtered out; preserving only the query is insufficient.
        runOnIdle { prompts = prompts + prompt("Matching built-in").copy(isBuiltIn = true) }
        onNodeWithText("Matching built-in").assertDoesNotExist()
    }

    @Test fun copyClearsSearchWhenItWouldHideTheCopy() = runComposeUiTest {
        var prompts by mutableStateOf(listOf(prompt("Source")))
        var copiedId by mutableStateOf<String?>(null)
        setContent {
            TestScreen(listOf(PromptGroupVd(null, false, prompts)), copiedId,
                onRevealed = { copiedId = null }, searchOpen = true)
        }
        onNode(hasSetTextAction()).performTextInput("Source")
        runOnIdle { prompts = prompts + prompt("Renamed copy"); copiedId = "Renamed copy" }
        onNodeWithText("Renamed copy").assertIsDisplayed()
        onNode(hasSetTextAction()).assert(hasText(""))
    }

    @Test fun copyRevealsAndMarksTheNewRowEvenWithAnimationsDisabled() = copyRevealsAndMarksTheNewRow(true)
    @Test fun copyRevealsAndMarksTheNewRowWithAnimations() = copyRevealsAndMarksTheNewRow(false)

    @Test fun favoriteCopyClearsTypeFilterAndHighlightsOnlyTheFavoritesOccurrence() = runComposeUiTest {
        val source = prompt("Built-in source").copy(isFavorite = true, isBuiltIn = true, isReadOnly = true)
        var prompts by mutableStateOf(listOf(source))
        var copiedId by mutableStateOf<String?>(null)
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    AiPromptsScreen(
                        configured = true, groups = listOf(
                            PromptGroupVd(null, true, prompts), PromptGroupVd(null, false, prompts),
                        ), showHidden = false, hasHiddenPrompts = false,
                        onUp = {}, onOpenPrompt = {}, onNewPrompt = {}, onToggleFavorite = {},
                        onSetPromptHidden = { _, _ -> }, onSetCategoryHidden = { _, _ -> },
                        onDeletePrompt = {}, onDeleteCategory = { _, _ -> }, onMovePrompt = { _, _ -> },
                        onMoveCategory = { _, _ -> }, onCreateCategory = {}, onRenameCategory = { _, _ -> },
                        onSetShowHidden = {}, onOpenConnectionSettings = {}, onImportCsv = {}, onExportCsv = {},
                        helpBody = "", helpReadMoreUrl = "", copiedPromptId = copiedId,
                        initiallyFilter = PromptListFilter(types = setOf(PromptType.BUILT_IN)),
                        onCopyRevealed = { copiedId = null },
                        onCopyPrompt = {
                            prompts = listOf(source, prompt("Favorite copy").copy(isFavorite = true))
                            copiedId = "Favorite copy"
                        },
                    )
                }
            }
        }
        onAllNodesWithContentDescription(strings.menu).onFirst().performClick()
        onNodeWithText(strings.copyLabel).performClick()
        onAllNodes(hasText("Favorite copy")).assertCountEquals(2)
        onNode(isCopyFeedback() and hasAnyDescendant(hasText("Favorite copy"))).assertIsDisplayed()
        onAllNodes(isCopyFeedback()).assertCountEquals(1)
        onAllNodes(isSelectable()).assertCountEquals(0)
    }

    @Test fun delayedCopyExpandsTheLastCategoryBeforeScrolling() = runComposeUiTest {
        val source = prompt("Source").copy(isFavorite = true)
        val category = PromptCategoryVd("last", "Last category", false, false)
        var groups by mutableStateOf(listOf(
            PromptGroupVd(null, true, listOf(source)),
            PromptGroupVd(null, false, (0..20).map { prompt("Other $it") }),
            PromptGroupVd(category, false, listOf(source)),
        ))
        var copiedId by mutableStateOf<String?>(null)
        val listState = LazyListState()
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = true, colorMode = DisplayColorMode.MONOCHROME, disableAnimations = true) {
                    Box(Modifier.height(350.dp)) {
                        AiPromptsScreen(
                            configured = true, groups = groups, showHidden = false, hasHiddenPrompts = false,
                            onUp = {}, onOpenPrompt = {}, onNewPrompt = {}, onToggleFavorite = {},
                            onSetPromptHidden = { _, _ -> }, onSetCategoryHidden = { _, _ -> },
                            onDeletePrompt = {}, onDeleteCategory = { _, _ -> }, onMovePrompt = { _, _ -> },
                            onMoveCategory = { _, _ -> }, onCreateCategory = {}, onRenameCategory = { _, _ -> },
                            onSetShowHidden = {}, onOpenConnectionSettings = {}, onImportCsv = {}, onExportCsv = {},
                            helpBody = "", helpReadMoreUrl = "", copiedPromptId = copiedId, listState = listState,
                        )
                    }
                }
            }
        }
        // Collapse the last category, then publish the returned ID before its refreshed data.
        onNode(hasScrollToIndexAction()).performScrollToIndex(26)
        onNodeWithText("Last category").performClick()
        runOnIdle { copiedId = "Delayed copy" }
        waitForIdle()
        runOnIdle {
            groups = groups.dropLast(1) + groups.last().copy(prompts = listOf(source, prompt("Delayed copy")))
        }
        onNodeWithText("Delayed copy").assertIsDisplayed()
        onNode(isCopyFeedback() and hasAnyDescendant(hasText("Delayed copy"))).assertIsDisplayed()
        // Favorites header+source, divider+uncategorized header+21 rows, divider+last header+source+copy.
        runOnIdle { assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.index == 28 }) }
    }

    private fun copyRevealsAndMarksTheNewRow(disableAnimations: Boolean) = runComposeUiTest {
        var prompts by mutableStateOf((0..30).map { prompt("Prompt $it") })
        var copiedId by mutableStateOf<String?>(null)
        val listState = LazyListState()
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = disableAnimations) {
                    Box(Modifier.height(350.dp)) {
                        AiPromptsScreen(
                            configured = true, groups = listOf(PromptGroupVd(null, false, prompts)),
                            showHidden = false, hasHiddenPrompts = false,
                            onUp = {}, onOpenPrompt = {}, onNewPrompt = {}, onToggleFavorite = {},
                            onSetPromptHidden = { _, _ -> }, onSetCategoryHidden = { _, _ -> },
                            onDeletePrompt = {}, onDeleteCategory = { _, _ -> }, onMovePrompt = { _, _ -> },
                            onMoveCategory = { _, _ -> }, onCreateCategory = {}, onRenameCategory = { _, _ -> },
                            onSetShowHidden = {}, onOpenConnectionSettings = {}, onImportCsv = {}, onExportCsv = {},
                            helpBody = "", helpReadMoreUrl = "",
                            copiedPromptId = copiedId, listState = listState,
                            onCopyRevealed = { copiedId = null },
                            onCopyPrompt = {
                                prompts = prompts + prompt("New copy")
                                copiedId = "New copy"
                            },
                        )
                    }
                }
            }
        }
        onAllNodesWithContentDescription(strings.menu).onFirst().performClick()
        onNodeWithText(strings.copyLabel).performClick()
        onNodeWithText("New copy").assertIsDisplayed()
        onNode(isCopyFeedback() and hasAnyDescendant(hasText("New copy"))).assertIsDisplayed()
        onAllNodes(isSelectable()).assertCountEquals(0)
        runOnIdle { assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.index == 32 }) }
        mainClock.advanceTimeBy(2600)
        onAllNodes(isCopyFeedback()).assertCountEquals(0)
    }
}
