package net.bible.android.view.compose

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
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
import net.bible.sharedui.components.HostSystemBars
import net.bible.sharedui.components.LocalHostSystemBars
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
 * #3892: with `hide_status_bar` a top display cutout left an empty band at the top of the menu; with the
 * status bar shown, rows must stay clipped below it. Mount ReadingHost's own [ReadingDrawerSheet] without opening an animated
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

    @Test fun visibleStatusBarClipsTheMenuBelowItInLightTheme() = checkVisibleStatusBar(dark = false)
    @Test fun visibleStatusBarClipsTheMenuBelowItInDarkTheme() = checkVisibleStatusBar(dark = true)

    /**
     * #3892 follow-up: with the status bar shown, the menu scrolls under the bar's bottom edge. Rows must never
     * draw up under the clock and status icons (the first fix put the inset inside the scroll and let them).
     */
    private fun checkVisibleStatusBar(dark: Boolean) {
        val statusTop = 24
        mountBand(dark, HostSystemBars(statusVisible = true, navVisible = true))
        dispatchTop(statusTop = statusTop, cutoutTop = 0)
        val rootTop = rootTop()
        val headerTop = compose.onNodeWithText("AndBible").fetchSemanticsNode().boundsInRoot.top
        assertTrue("header must clear the status bar at rest (top $headerTop)", headerTop >= rootTop + statusTop)
        val scroller = compose.onNode(hasScrollAction())
        assertEquals(
            "the scroll viewport must start below a visible status bar",
            rootTop + statusTop, scroller.fetchSemanticsNode().boundsInRoot.top, 1f,
        )
        scroller.performScrollToNode(hasText(bandLabels.last()))
        compose.waitForIdle()
        val underBar = bandLabels.filter { label ->
            val b = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            b.height > 0f && b.top < rootTop + statusTop - 1f
        }
        assertTrue("no scrolled row may draw under the visible status bar: $underBar", underBar.isEmpty())
    }

    @Test fun hiddenStatusBarLeavesNoBandInLightTheme() = checkHiddenStatusBar(dark = false)
    @Test fun hiddenStatusBarLeavesNoBandInDarkTheme() = checkHiddenStatusBar(dark = true)

    /**
     * #3892: with `hide_status_bar` the status-bar inset is 0, but Material's default drawer insets also
     * union the display cutout, so a camera hole kept an empty band of the status bar's height at the top.
     * Like the top bars (`topBarCutoutTop`), the drawer ignores the top cutout while the host hides the bar.
     */
    private fun checkHiddenStatusBar(dark: Boolean) {
        mountBand(dark, HostSystemBars(statusVisible = false, navVisible = true))
        dispatchTop(statusTop = 0, cutoutTop = 0)
        val headerBefore = compose.onNodeWithText("AndBible").fetchSemanticsNode().boundsInRoot.top
        dispatchTop(statusTop = 0, cutoutTop = 30)
        val rootTop = rootTop()
        assertEquals(
            "a top cutout must not push the header down while the status bar is hidden",
            headerBefore, compose.onNodeWithText("AndBible").fetchSemanticsNode().boundsInRoot.top, 1f,
        )
        assertEquals(
            "the scroll viewport must start at the sheet top while the status bar is hidden",
            rootTop, compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot.top, 1f,
        )
    }

    /** No host bar state (iOS, desktop, previews): Material's default, the larger of status bar and cutout. */
    @Test fun withoutHostStateTheMenuClearsTheLargerOfStatusBarAndCutout() {
        mountBand(dark = false, bars = null)
        dispatchTop(statusTop = 24, cutoutTop = 30)
        assertEquals(
            "the scroll viewport must start below max(status bar, cutout)",
            rootTop() + 30f, compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot.top, 1f,
        )
    }

    private val bandLabels = (1..20).map { "Item $it" }

    private fun mountBand(dark: Boolean, bars: HostSystemBars?) {
        compose.setContent {
            CompositionLocalProvider(LocalHostSystemBars provides bars) {
                AbTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().testTag("root")) {
                        ReadingDrawerSheet(
                            state = DrawerMenuState(
                                appName = "AndBible",
                                versionText = versionText,
                                groups = listOf(DrawerGroup(null, bandLabels.map { DrawerItem(it, it, it) })),
                            ),
                            icon = { null },
                            onItemClick = {},
                        )
                    }
                }
            }
        }
    }

    private fun rootTop() = compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot.top

    private fun dispatchTop(statusTop: Int, cutoutTop: Int) {
        val root: ViewGroup = compose.activity.findViewById(android.R.id.content)
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusTop, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, cutoutTop, 0, 0))
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
