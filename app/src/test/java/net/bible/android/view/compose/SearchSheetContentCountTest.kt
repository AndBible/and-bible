package net.bible.android.view.compose

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
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
    private var closed = 0

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
                    onClose = { closed++ },
                ) {}
            }
        }
    }

    @Test fun resultsHeaderHasAccessibleClose() {
        show(loading = false)
        compose.onNodeWithContentDescription("Close").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun longCountDoesNotTurnTheHeaderIntoATallColumn() {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(disableAnimations = true) {
                    Box(Modifier.width(320.dp)) {
                        SearchSheetContent(
                            countLabel = label,
                            loading = false,
                            error = null,
                            empty = false,
                            listState = rememberLazyListState(),
                            onDismissError = {},
                            onClose = {},
                            // The result actions plus Close leave only a narrow strip at 320dp.
                            actions = { Box(Modifier.width(216.dp).height(48.dp)) },
                        ) {}
                    }
                }
            }
        }
        compose.onNodeWithText(label).assertHeightIsEqualTo(40.dp)
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
