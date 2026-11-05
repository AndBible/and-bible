package net.bible.sharedui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsFocused
import net.bible.sharedui.components.AbMenuItem
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedui.ai.AiPromptsScreen
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbDropdownMenu
import net.bible.sharedui.reading.ReadingOverflowMenu
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/** #3839: actual Material popups without Android's recomposer motion-scale override. */
@OptIn(ExperimentalTestApi::class)
class DisablePopupAnimationsTest {
    private enum class Menu { PANE, OVERFLOW, FIELD, AI_PROMPT }
    private val strings = iosStrings("en")

    private fun ComposeUiTest.opacity(label: String): List<Float> {
        val pixels = onNodeWithText(label).captureToImage().toPixelMap()
        // Focus/hover indications can change RGB without a popup transition; measure the actual fade alpha.
        return List(pixels.width * pixels.height) { pixels[it % pixels.width, it / pixels.width].alpha }
    }

    /** Removing the theme's no-motion policy must restore both entry fades and retained exit frames. */
    private fun menuMotion(
        disabled: Boolean, menu: Menu, handledByHost: Boolean = false,
        dark: Boolean = false, colorMode: DisplayColorMode = DisplayColorMode.NORMAL,
    ) = runComposeUiTest {
        var open by mutableStateOf(false)
        var selections = 0
        val label = when (menu) {
            Menu.PANE -> "Close window"
            Menu.OVERFLOW -> "Reading action"
            Menu.FIELD -> "Commentary"
            Menu.AI_PROMPT -> strings.copyLabel
        }
        setContent {
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalMotionDurationScaleHandledByHost provides handledByHost,
            ) {
                AbTheme(darkTheme = dark, colorMode = colorMode, disableAnimations = disabled) {
                    Box(Modifier.fillMaxSize()) {
                        when (menu) {
                            Menu.PANE -> WindowPaneMenu(
                                items = if (open) listOf(WindowPaneMenuItem("close", label)) else emptyList(),
                                expanded = open,
                                onItemClick = { selections++; open = false }, onDismiss = { open = false },
                            )
                            Menu.OVERFLOW -> ReadingOverflowMenu(
                                items = listOf(OptionsMenuItem("action", label, false, false, true, false)),
                                expanded = open,
                                onItemClick = { selections++; open = false }, onDismiss = { open = false },
                            )
                            Menu.FIELD -> AbDropdownField(
                                "Document type", "Bible", listOf("Bible", label), { it }, { selections++ },
                            )
                            Menu.AI_PROMPT -> AiPromptsScreen(
                                configured = true,
                                groups = listOf(PromptGroupVd(null, false, listOf(
                                    PromptVd("prompt", "Test prompt", "", null, false, false, false, false),
                                ))),
                                showHidden = false, hasHiddenPrompts = false,
                                onUp = {}, onOpenPrompt = {}, onNewPrompt = {}, onToggleFavorite = {},
                                onSetPromptHidden = { _, _ -> }, onSetCategoryHidden = { _, _ -> },
                                onDeletePrompt = {}, onDeleteCategory = { _, _ -> },
                                onMovePrompt = { _, _ -> }, onMoveCategory = { _, _ -> },
                                onCreateCategory = {}, onRenameCategory = { _, _ -> }, onSetShowHidden = {},
                                onOpenConnectionSettings = {}, onImportCsv = {}, onExportCsv = {},
                                helpBody = "", helpReadMoreUrl = "", onCopyPrompt = { selections++ },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        when (menu) {
            Menu.FIELD -> onNodeWithText("Bible").performClick()
            // The prompt row is below the top-bar overflow; semantics traversal order is not visual order.
            Menu.AI_PROMPT -> onAllNodesWithContentDescription(strings.menu).let { nodes ->
                val rowIndex = nodes.fetchSemanticsNodes().withIndex().maxBy { it.value.boundsInRoot.top }.index
                nodes[rowIndex].performClick()
            }
            else -> runOnIdle { open = true }
        }
        mainClock.advanceTimeByFrame()
        val firstFrame = opacity(label)
        mainClock.advanceTimeBy(5_000)
        val settled = opacity(label)
        val entrySettled = settled == firstFrame

        onNodeWithText(label).performClick()
        repeat(2) { mainClock.advanceTimeByFrame() }
        assertEquals(1, selections, "$menu selection is dispatched once")
        val exitGone = onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
        assertEquals(
            listOf(disabled && !handledByHost, disabled && !handledByHost), listOf(entrySettled, exitGone),
            "$menu: first frame fully drawn / no retained exit frames",
        )
        mainClock.advanceTimeBy(5_000)
        onNodeWithText(label).assertDoesNotExist()
    }

    @Test fun paneMenuHasNoAnimationFramesWhenDisabled() = menuMotion(true, Menu.PANE)
    @Test fun paneMenuStillAnimatesWhenEnabled() = menuMotion(false, Menu.PANE)
    @Test fun overflowMenuHasNoAnimationFramesWhenDisabled() = menuMotion(true, Menu.OVERFLOW)
    @Test fun overflowMenuStillAnimatesWhenEnabled() = menuMotion(false, Menu.OVERFLOW)
    @Test fun exposedDropdownHasNoAnimationFramesWhenDisabled() = menuMotion(true, Menu.FIELD)
    @Test fun exposedDropdownStillAnimatesWhenEnabled() = menuMotion(false, Menu.FIELD)
    @Test fun aiPromptRowMenuHasNoAnimationFramesWhenDisabled() = menuMotion(true, Menu.AI_PROMPT)
    @Test fun aiPromptRowMenuStillAnimatesWhenEnabled() = menuMotion(false, Menu.AI_PROMPT)

    @Test fun darkPaneMenuHasNoAnimationFrames() = menuMotion(true, Menu.PANE, dark = true)
    @Test fun monochromePaneMenuHasNoAnimationFrames() =
        menuMotion(true, Menu.PANE, colorMode = DisplayColorMode.MONOCHROME)
    @Test fun darkMonochromePaneMenuHasNoAnimationFrames() =
        menuMotion(true, Menu.PANE, dark = true, colorMode = DisplayColorMode.MONOCHROME)

    // No zero-scale recomposer in this test: retained frames prove the original Material path was kept.
    @Test fun hostHandledPaneMenuKeepsMaterial() = menuMotion(true, Menu.PANE, handledByHost = true)
    @Test fun hostHandledExposedDropdownKeepsMaterial() = menuMotion(true, Menu.FIELD, handledByHost = true)

    @Test fun instantMenuFocusStartsOnFirstItemAndArrowKeysSelectTheNext() = runComposeUiTest {
        lateinit var input: InputModeManager
        var open by mutableStateOf(false)
        var selected = ""
        setContent {
            AbTheme(disableAnimations = true) {
                input = LocalInputModeManager.current
                AbDropdownMenu(open, { open = false }) {
                    AbMenuItem(text = "First", onClick = { selected = "first"; open = false })
                    AbMenuItem(text = "Second", onClick = { selected = "second"; open = false })
                }
            }
        }
        waitForIdle()
        runOnIdle { input.requestInputMode(InputMode.Keyboard); open = true }
        waitForIdle()
        onNodeWithText("First").assertIsFocused()
        onNodeWithText("First").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Second").assertIsFocused()
        onNodeWithText("Second").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("second", selected)
        onNodeWithText("Second").assertDoesNotExist()
    }

    @Test fun sharedMenuIsFullyOpaqueOnItsFirstRenderedFrame() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                AbDropdownMenu(open, { open = false }) {
                    Box(Modifier.size(80.dp).background(Color.Red).testTag("red"))
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        runOnIdle { open = true }
        mainClock.advanceTimeByFrame()
        val pixels = onNodeWithTag("red").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2], "no fade-in frame")
    }
}
