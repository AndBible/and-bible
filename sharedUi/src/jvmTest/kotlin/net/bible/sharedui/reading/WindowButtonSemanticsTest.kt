package net.bible.sharedui.reading

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.theme.AbTheme

/**
 * The window buttons are gesture-only boxes; without semantics VoiceOver and TalkBack cannot find or press them
 * (an XCUITest saw the rail tabs and the pane ☰ only as unlabelled "Other" elements). Each button must be one
 * accessible button with its label and both of its actions.
 */
@OptIn(ExperimentalTestApi::class)
class WindowButtonSemanticsTest {
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun railTabIsOneButtonLabelledWithItsTextsAndBothActions() = runComposeUiTest {
        var clicks = 0
        var longPresses = 0
        setContent {
            AbTheme {
                WindowButton(
                    label = "KJV", isActive = true, isMinimised = false, isPinned = false, isLinks = false, syncGroup = 0,
                    mode = WindowButtonMode.Rail, onClick = { clicks++ }, onLongPress = { longPresses++ }, topLabel = "Jo 3:16",
                )
            }
        }
        val tab = onNodeWithText("KJV", useUnmergedTree = false)
        tab.assert(isButton)
        val texts = tab.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.map { it.text }
        assertEquals(listOf("Jo 3:16", "KJV"), texts, "the merged button carries both rail texts")
        tab.performSemanticsAction(SemanticsActions.OnClick)
        tab.performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1, clicks)
        assertEquals(1, longPresses)
    }

    @Test
    fun paneButtonIsAButtonNamedByItsDescriptionNotTheGlyph() = runComposeUiTest {
        var clicks = 0
        var longPresses = 0
        setContent {
            AbTheme {
                WindowButton(
                    label = "☰", isActive = true, isMinimised = false, isPinned = false, isLinks = false, syncGroup = 1,
                    mode = WindowButtonMode.Pane, onClick = { clicks++ }, onLongPress = { longPresses++ },
                    contentDescription = "Window menu",
                )
            }
        }
        val pane = onNodeWithContentDescription("Window menu")
        pane.assert(isButton)
        assertEquals(null, pane.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text), "the ☰ glyph is not read out")
        pane.performSemanticsAction(SemanticsActions.OnClick)
        pane.performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1, clicks)
        assertEquals(1, longPresses)
    }
}
