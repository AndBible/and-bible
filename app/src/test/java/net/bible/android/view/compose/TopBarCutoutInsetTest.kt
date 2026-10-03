package net.bible.android.view.compose

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.android.activity.R
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.components.AbTopAppBar
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Final review, Important 1: the app is edge-to-edge, and once the status bar is hidden
 * (`hide_status_bar`, reading fullscreen, the F77 policy on every destination) `statusBars` is 0, so a
 * top display cutout (camera hole) is padded by nothing. Every top bar must pad
 * `max(statusBars, displayCutout.top)` -- the UNION, never the sum.
 *
 * Each test mounts once, measures the content top with no insets, dispatches, and asserts the shift.
 * `sdk` is explicit: the inset dispatch path is the API 35 one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TopBarCutoutInsetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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

    private fun dispatch(statusTop: Int, cutoutTop: Int) {
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

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    private fun mountReading() = compose.setContent {
        ProvideAppLocals {
            Box(Modifier.fillMaxSize()) {
                ReadingViewScreen(
                    layout = layout(), toolbar = ToolbarState.EMPTY, toolbarIcons = icons(),
                    toolbarCallbacks = callbacks(), fullScreen = false,
                    onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                    pane = { Box(Modifier.fillMaxSize().testTag("pane")) },
                )
            }
        }
    }

    /** Shift of [tag]'s top after dispatching the given insets, relative to zero insets. */
    private fun shift(tag: String, statusTop: Int, cutoutTop: Int): Float {
        dispatch(0, 0)
        val before = top(tag)
        dispatch(statusTop, cutoutTop)
        return top(tag) - before
    }

    @Test fun readingToolbarPadsACutoutWhenTheStatusBarIsHidden() {
        mountReading()
        assertEquals(60f, shift("pane", statusTop = 0, cutoutTop = 60), 1f)
    }

    @Test fun readingToolbarPadsTheLargerOfStatusBarAndCutoutNotTheSum() {
        mountReading()
        assertEquals(60f, shift("pane", statusTop = 24, cutoutTop = 60), 1f)
        assertEquals(24f, shift("pane", statusTop = 24, cutoutTop = 10), 1f)
    }

    @Test fun abTopAppBarPadsACutoutWhenTheStatusBarIsHidden() {
        compose.setContent {
            ProvideAppLocals { AbTopAppBar(title = { Text("T", Modifier.testTag("title")) }) }
        }
        assertEquals(60f, shift("title", statusTop = 0, cutoutTop = 60), 1f)
    }

    @Test fun abTopAppBarPadsTheLargerOfStatusBarAndCutoutNotTheSum() {
        compose.setContent {
            ProvideAppLocals { AbTopAppBar(title = { Text("T", Modifier.testTag("title")) }) }
        }
        assertEquals(60f, shift("title", statusTop = 24, cutoutTop = 60), 1f)
    }

    @Test fun selectionBarPadsACutoutWhenTheStatusBarIsHidden() {
        compose.setContent {
            ProvideAppLocals {
                AbSelectionScaffold(
                    title = "x", selectionMode = true, selectedCount = 3,
                    onNavigateUp = {}, onExitSelection = {},
                    selectionActions = {},
                ) { pv -> Box(Modifier.fillMaxSize().padding(pv).testTag("content")) }
            }
        }
        // The content slot sits directly under the bar, so its top follows the bar's padding.
        assertEquals(60f, shift("content", statusTop = 0, cutoutTop = 60), 1f)
    }
}
