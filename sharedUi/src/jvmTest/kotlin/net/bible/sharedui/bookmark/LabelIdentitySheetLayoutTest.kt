package net.bible.sharedui.bookmark

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LabelIdentitySheetLayoutTest {
    private val strings = iosStrings("en")

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false, disableAnimations = true, content = content)
        }
    }

    @Test fun closeIsAboveTheNameFieldAndDoesNotClearTheName() = runSkikoComposeUiTest(
        size = Size(320f, 760f), density = Density(1f),
    ) {
        var closes = 0
        val names = mutableListOf<String>()
        setContent {
            theme {
                LabelIdentitySheetContent(
                    name = "Study", nameEditable = true, colorArgb = 0xFF2196F3.toInt(), customIcon = null,
                    iconKeys = emptyList(), iconVisible = false,
                    onName = { names += it }, onColor = {}, onCustomIcon = {}, onClose = { closes++ },
                    iconSlot = { _, _ -> },
                )
            }
        }
        val close = onNodeWithContentDescription(strings.settingsEditorClose)
        val closeBounds = close.fetchSemanticsNode().boundsInRoot
        val fieldBounds = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(closeBounds.bottom <= fieldBounds.top, "Sheet close must be above, not beside, the Name field")
        val titleBounds = onNodeWithText(strings.editLabelTitle).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.center.y in closeBounds.top..closeBounds.bottom, "Close belongs to the title row")
        close.performClick()
        assertEquals(1, closes)
        assertTrue(names.isEmpty(), "Closing must not edit the label name")
        onNodeWithText("Study").assertIsDisplayed()
    }

    private fun cappedSheet(window: Size) = runSkikoComposeUiTest(size = window, density = Density(1f)) {
        setContent {
            theme {
                LabelIdentitySheet(
                    name = "Study", nameEditable = false, colorArgb = 0xFF2196F3.toInt(), customIcon = null,
                    iconKeys = List(48) { "icon-$it" }, iconVisible = true,
                    onName = {}, onColor = {}, onCustomIcon = {}, onDismiss = {},
                    iconSlot = { key, _ -> Text(key.orEmpty()) },
                )
            }
        }
        val close = onNodeWithContentDescription(strings.settingsEditorClose)
        val initialClose = close.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        // AbSheetHeader centres the button in its 48dp row, which starts at the sheet's top.
        val sheetTop = initialClose.center.y - 24f
        assertTrue(sheetTop >= window.height / 2f - 1f, "The whole sheet, including its header, must leave half the window visible")
        onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        onNodeWithText("icon-47").assertIsDisplayed()
        assertEquals(initialClose, close.assertIsDisplayed().fetchSemanticsNode().boundsInRoot, "Close must stay pinned while the form scrolls")
        onNodeWithText(strings.editLabelTitle).assertIsDisplayed()
    }

    @Test fun portraitSheetStaysCompactWithCloseVisibleAfterScrolling() = cappedSheet(Size(360f, 760f))
    @Test fun landscapeSheetStaysCompactWithCloseVisibleAfterScrolling() = cappedSheet(Size(760f, 360f))
}
