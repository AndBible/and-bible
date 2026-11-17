package net.bible.sharedui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import net.bible.sharedcore.ui.dialog.plainTextToHtml
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/** The common action-sheet body must honour the same HTML contract as the other dialog bodies. */
@OptIn(ExperimentalTestApi::class)
class AbActionSheetHtmlTest {
    @Test fun escapedDatabaseMessageKeepsParagraphsAndLiteralFilename() = runComposeUiTest {
        val plain = "Damaged: bookmarks<&>.db\n\nOpen Backup & Restore."
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    AbActionSheetContent("Damaged data", plainTextToHtml(plain), onClose = {}) {
                        Text("Reset damaged data")
                    }
                }
            }
        }
        onNodeWithText("Damaged: bookmarks<&>.db\n\nOpen Backup & Restore.").assertExists()
    }
}
