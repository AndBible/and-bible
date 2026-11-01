package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3Api::class)
class ReadingSheetDragHandleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun quickSheetHasAccessibleCloseInsteadOfDragHandle() {
        var closed = 0
        var closeDescription = ""
        rule.setContent {
            ProvideAppLocals {
                AbTheme(disableAnimations = true) {
                    CompositionLocalProvider(LocalSheetDragHandleDescription provides "Dismiss sheet handle") {
                        closeDescription = LocalStrings.current.settingsEditorClose
                        AbQuickSheet(open = true, title = "History", onDismiss = { closed++ }) {
                            Box(Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Dismiss sheet handle").assertDoesNotExist()
        rule.onNodeWithContentDescription(closeDescription).assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun sheetWithoutCloseKeepsAccessibleDragHandle() {
        rule.setContent {
            AbTheme(disableAnimations = true) {
                AbModalBottomSheet(onDismissRequest = {}, dragHandleDescription = "Dismiss sheet handle") {
                    Box(Modifier.height(80.dp))
                }
            }
        }
        rule.onNodeWithContentDescription("Dismiss sheet handle").assertExists()
    }
}
