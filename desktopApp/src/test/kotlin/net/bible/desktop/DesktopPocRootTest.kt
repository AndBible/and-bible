package net.bible.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.poc.PocScenario
import net.bible.sharedui.webview.PlaceholderBibleWebView
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DesktopPocRootTest {
    @Test
    fun splitShowsTwoPlaceholderPanes() = runComposeUiTest {
        setContent {
            DesktopPocRoot("{}", { c, m -> PlaceholderBibleWebView(c, m) }, PocScenario.SPLIT2)
        }
        onNodeWithTag("pane-w1").assertExists()
        onNodeWithTag("pane-w2").assertExists()
        onNodeWithTag("pane-w3").assertDoesNotExist()
    }
}
