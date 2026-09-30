package net.bible.android.view.compose

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.sharedui.search.readingSheetInsetPadding
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Final review, Important 2: the reading search sheet and its snackbar sit in a
 * `BottomSheetScaffold` that applies no window insets, on an edge-to-edge window. The shared
 * `readingSheetInsetPadding()` must clear the nav bar (bottom and, in landscape, the sides) and the
 * display cutout, as a union rather than a sum. `SearchSheetStructureGuardTest` guards that the host
 * applies it to both the sheet content and the snackbar host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = android.app.Application::class)
class ReadingSheetInsetPaddingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun mount() = compose.setContent {
        Box(Modifier.fillMaxSize().testTag("root")) {
            Box(Modifier.fillMaxSize().readingSheetInsetPadding().testTag("content"))
        }
    }

    private fun dispatch(nav: Insets, cutout: Insets = Insets.NONE) {
        val root: ViewGroup = compose.activity.findViewById(android.R.id.content)
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), nav)
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
                    .build(),
            )
        }
        compose.waitForIdle()
    }

    private fun b(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test fun clearsTheBottomNavigationBar() {
        mount(); dispatch(Insets.of(0, 0, 0, 48))
        assertEquals(b("root").bottom - 48, b("content").bottom, 1f)
    }

    @Test fun clearsASideNavigationBarInLandscape() {
        mount(); dispatch(Insets.of(0, 0, 40, 0))
        assertEquals(b("root").right - 40, b("content").right, 1f)
    }

    @Test fun clearsASideCutoutAndUsesTheLargerNotTheSum() {
        mount(); dispatch(nav = Insets.of(0, 0, 0, 48), cutout = Insets.of(30, 0, 0, 10))
        assertEquals(b("root").left + 30, b("content").left, 1f)
        assertEquals("bottom is max(nav 48, cutout 10), not the sum", b("root").bottom - 48, b("content").bottom, 1f)
    }
}
