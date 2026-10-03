package net.bible.android.view.compose

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.SearchSheetContent
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F91 (fix batch 3 §2.1.2): the count belongs to a finished search. Drawn next to the spinner it
 * read "0 verses" during a cold first search, which the emulator pass recorded as a wrong result.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchSheetContentCountTest {
    @get:Rule val compose = createComposeRule()

    private val label = "5001 verses in 1 translations"

    private fun show(loading: Boolean) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                SearchSheetContent(
                    countLabel = label,
                    loading = loading,
                    error = null,
                    empty = false,
                    listState = rememberLazyListState(),
                    onDismissError = {},
                ) {}
            }
        }
    }

    @Test fun theCountIsHiddenWhileLoading() {
        show(loading = true)
        compose.onNodeWithText(label).assertDoesNotExist()
    }

    @Test fun theCountShowsOnceLoaded() {
        show(loading = false)
        compose.onNodeWithText(label).assertExists()
    }
}
