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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsNotSelected
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
        onNode(isSelected() and hasAnyDescendant(hasText("Favorite copy"))).assertIsDisplayed()
        onAllNodes(isSelected()).assertCountEquals(1)
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
        onNode(isSelectable() and hasAnyDescendant(hasText("Delayed copy"))).assertIsSelected()
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
        onNode(isSelectable() and hasAnyDescendant(hasText("New copy"))).assertIsSelected()
        runOnIdle { assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.index == 32 }) }
        mainClock.advanceTimeBy(2600)
        onNode(isSelectable() and hasAnyDescendant(hasText("New copy"))).assertIsNotSelected()
    }
}
