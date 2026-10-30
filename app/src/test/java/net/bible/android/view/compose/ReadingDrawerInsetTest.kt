package net.bible.android.view.compose

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.sharedcore.reading.DrawerGroup
import net.bible.sharedcore.reading.DrawerItem
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedui.reading.ReadingDrawerSheet
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #3872: the legacy drawer's fixed footer padding did not clear the navigation bar.
 * #3892: the status-bar inset was padding outside the drawer's scroll area, so a scrolled menu left
 * an empty band at the top. Mount ReadingHost's own [ReadingDrawerSheet] without opening an animated
 * ModalNavigationDrawer (which can hang Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingDrawerInsetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val versionText = "Version 6.0.1230"

    @Test fun footerClearsThreeButtonNavigationInLightTheme() = checkFooter(dark = false, navBottom = 48)
    @Test fun footerClearsThreeButtonNavigationInDarkTheme() = checkFooter(dark = true, navBottom = 48)
    @Test fun footerClearsGestureNavigation() = checkFooter(dark = false, navBottom = 24)

    private fun checkFooter(dark: Boolean, navBottom: Int) {
        compose.setContent {
            AbTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    ReadingDrawerSheet(
                        state = DrawerMenuState(appName = "AndBible", versionText = versionText, groups = emptyList()),
                        icon = { null },
                        onItemClick = {},
                    )
                }
            }
        }
        dispatch(0)
        val baseline = footerBottom()
        dispatch(navBottom)
        assertEquals("footer must move up by exactly one navigation-bar inset", baseline - navBottom, footerBottom(), 1f)
        val rootBottom = compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("footer must remain above the navigation bar", footerBottom() <= rootBottom - navBottom)

        dispatch(navBottom)
        assertEquals("repeated insets must not accumulate padding", baseline - navBottom, footerBottom(), 1f)
        dispatch(0)
        assertEquals("removing the bar must remove its padding", baseline, footerBottom(), 1f)
    }

    @Test fun scrolledMenuFillsStatusBarBandInLightTheme() = checkStatusBarBand(dark = false)
    @Test fun scrolledMenuFillsStatusBarBandInDarkTheme() = checkStatusBarBand(dark = true)

    /**
     * #3892: at rest the header clears the status bar, but once scrolled the rows must move up into
     * that band instead of being clipped below an empty strip of the inset's height.
     */
    private fun checkStatusBarBand(dark: Boolean) {
        val statusTop = 24
        val labels = (1..20).map { "Item $it" }
        compose.setContent {
            AbTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    ReadingDrawerSheet(
                        state = DrawerMenuState(
                            appName = "AndBible",
                            versionText = versionText,
                            groups = listOf(DrawerGroup(null, labels.map { DrawerItem(it, it, it) })),
                        ),
                        icon = { null },
                        onItemClick = {},
                    )
                }
            }
        }
        dispatchStatus(statusTop)
        val rootTop = compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot.top
        val headerTop = compose.onNodeWithText("AndBible").fetchSemanticsNode().boundsInRoot.top
        assertTrue("header must clear the status bar at rest (top $headerTop)", headerTop >= rootTop + statusTop)

        val scroller = compose.onNode(hasScrollAction())
        assertEquals(
            "the scroll viewport must start at the sheet top, not below a status-bar band",
            rootTop, scroller.fetchSemanticsNode().boundsInRoot.top, 1f,
        )
        scroller.performScrollToNode(hasText(labels.last()))
        compose.waitForIdle()
        val inBand = labels.any { label ->
            val b = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            b.height > 0f && b.top < rootTop + statusTop && b.bottom > rootTop
        }
        assertTrue("a scrolled row must be visible in the status-bar band, not dead space", inBand)
    }

    private fun dispatchStatus(top: Int) {
        val root: ViewGroup = compose.activity.findViewById(android.R.id.content)
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
                    .build(),
            )
        }
        compose.waitForIdle()
    }

    private fun footerBottom() = compose.onNodeWithText(versionText).fetchSemanticsNode().boundsInRoot.bottom

    private fun dispatch(bottom: Int) {
        val root: ViewGroup = compose.activity.findViewById(android.R.id.content)
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                    .build(),
            )
        }
        compose.waitForIdle()
    }
}
