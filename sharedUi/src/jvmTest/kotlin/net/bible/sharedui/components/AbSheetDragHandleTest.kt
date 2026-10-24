package net.bible.sharedui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** R3: the sheet drag handle must carry a screen-reader name (iOS audit: "Element has no description"). */
@OptIn(ExperimentalTestApi::class)
class AbSheetDragHandleTest {
    @Test
    fun handleExposesTheGivenDescription() = runComposeUiTest {
        setContent { AbTheme(darkTheme = false) { AbSheetDragHandle(description = "Drag handle", mono = false) } }
        onNodeWithContentDescription("Drag handle").assertExists()
    }

    @Test
    fun monochromeHandleExposesTheGivenDescriptionToo() = runComposeUiTest {
        setContent { AbTheme(darkTheme = true) { AbSheetDragHandle(description = "Drag handle", mono = true) } }
        onNodeWithContentDescription("Drag handle").assertExists()
    }
}
