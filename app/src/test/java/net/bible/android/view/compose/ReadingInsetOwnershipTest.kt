package net.bible.android.view.compose

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.android.activity.R
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.readingRailInsetPadding
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F66/F67 and correction C2. The reading tree is edge-to-edge (API 35, and API 30+ after Task 2), so
 * each inset must be consumed exactly once:
 *  - the nav-bar BOTTOM inset by the bottom-most surface: the strip when no bar is up, otherwise the
 *    bar (a strip that also pads it floats one nav bar too high -- F66 with the keyboard, F67 over
 *    the collapsed agent panel, and the same gap over the Speak bar);
 *  - a SIDE nav bar / cutout by the split (nothing padded it: panes ran under a landscape 3-button
 *    nav bar).
 * Insets are dispatched to the content root because Robolectric does not synthesise them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReadingInsetOwnershipTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val navPx = 48

    private fun layout() = WindowLayoutState(
        windows = listOf(
            WindowSnapshot(
                id = "A", state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
                isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
            ),
        ),
        activeWindowId = "A", maximizedWindowId = null, reverseSplitMode = false, restoreButtonsVisible = true,
    )

    @Composable
    private fun icons() = ReadingToolbarIcons(
        home = painterResource(R.drawable.ic_menu), search = painterResource(R.drawable.ic_search_24dp),
        speak = painterResource(R.drawable.ic_baseline_headphones_24), strongs = painterResource(R.drawable.ic_strongs_hebrew),
        bible = painterResource(R.drawable.ic_bible_24dp), commentary = painterResource(R.drawable.ic_commentary),
        workspace = painterResource(R.drawable.ic_workspace_solid_24dp), overflow = painterResource(R.drawable.ic_more_vert_black_24dp),
    )

    private fun callbacks() = ReadingToolbarCallbacks(
        onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
        onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
        onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
        onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
    )

    /** Records what Compose itself sees, so a dispatch that never reaches Compose fails loudly. */
    private val seenNavBottom = mutableIntStateOf(-1)

    private fun mount(speakBarVisible: Boolean) {
        compose.setContent {
            val d = LocalDensity.current
            seenNavBottom.intValue = WindowInsets.navigationBars.getBottom(d)
            ProvideAppLocals {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    ReadingViewScreen(
                        layout = layout(), toolbar = ToolbarState.EMPTY, toolbarIcons = icons(),
                        toolbarCallbacks = callbacks(), fullScreen = false,
                        onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                        pane = { Box(Modifier.fillMaxSize().testTag("pane")) },
                        tabBar = { apply -> Box(Modifier.readingRailInsetPadding(apply).size(20.dp).testTag("strip")) },
                        // Like the real bar, renders nothing while it is not visible.
                        speakBar = { apply ->
                            if (speakBarVisible) Box(
                                Modifier.fillMaxWidth()
                                    .then(if (apply) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
                                    .height(40.dp).testTag("speak")
                            )
                        },
                        speakBarVisible = speakBarVisible,
                    )
                }
            }
        }
    }

    private fun dispatch(left: Int, right: Int, bottom: Int) {
        val root: ViewGroup = compose.activity.findViewById(android.R.id.content)
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(left, 0, right, bottom))
                    .build(),
            )
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test fun withNoBarTheStripSitsExactlyOneNavBarAboveTheScreenBottom() {
        mount(speakBarVisible = false)
        dispatch(0, 0, navPx)
        assertEquals("the dispatch must reach Compose", navPx, seenNavBottom.intValue)
        assertEquals(bounds("root").bottom - navPx, bounds("strip").bottom, 1f)
    }

    // F67's twin on the Speak bar: the bar owns the nav-bar inset, so the strip must sit ON it.
    @Test fun withTheSpeakBarUpTheStripSitsOnTheBar() {
        mount(speakBarVisible = true)
        dispatch(0, 0, navPx)
        assertEquals("the dispatch must reach Compose", navPx, seenNavBottom.intValue)
        assertEquals(bounds("speak").top, bounds("strip").bottom, 1f)
    }

    // Correction C2 / Review Focus 1: a side nav bar (landscape 3-button) must not cover the pane.
    @Test fun aSideNavigationBarIsPaddedByTheSplit() {
        mount(speakBarVisible = false)
        dispatch(0, navPx, 0)
        assertEquals(bounds("root").right - navPx, bounds("pane").right, 1f)
    }
}
