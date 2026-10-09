package net.bible.sharedui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedui.history.HistoryScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** Proves the jvm() test target renders a real :sharedUi screen with the generated strings holder. */
@OptIn(ExperimentalTestApi::class)
class JvmComposeSmokeTest {
    @Test
    fun historyScreenRendersOnDesktopJvm() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false) {
                    HistoryScreen(
                        title = "History",
                        entries = listOf(HistoryEntry(1, "Ephesians 2:8", "Today")),
                        error = null,
                        onSelect = {},
                        onDismissError = {},
                    )
                }
            }
        }
        onNodeWithText("Ephesians 2:8").assertExists()
    }
}
