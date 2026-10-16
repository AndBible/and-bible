package net.bible.android.view.compose

import net.bible.sharedui.ProvideAppLocals
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.android.TEST_SDK
import net.bible.android.view.compose.golden.goldenToolbarCallbacks
import net.bible.android.view.compose.golden.goldenToolbarIcons
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.monoPaper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
class ReadingMonochromeSideNavTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightSeededSideNavIsPaper() = seededSideNav(false)
    @Test fun darkSeededSideNavIsPaper() = seededSideNav(true)

    private fun seededSideNav(dark: Boolean) {
        var rightInset = -1
        val window = WindowSnapshot("A", WindowStateValue.VISIBLE, 1f, true, true, false, 0, false)
        compose.setContent {
            rightInset = WindowInsets.navigationBars.getRight(LocalDensity.current, androidx.compose.ui.platform.LocalLayoutDirection.current)
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
                    Box(Modifier.fillMaxSize().testTag("root")) {
                        ReadingViewScreen(
                            layout = WindowLayoutState(listOf(window), "A", null, false, true),
                            toolbar = ToolbarState.EMPTY.copy(workspaceColorArgb = 0xFFFF00FF.toInt(), deriveToolbarFromTheme = false),
                            toolbarIcons = goldenToolbarIcons(), toolbarCallbacks = goldenToolbarCallbacks(),
                            fullScreen = false, onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                            pane = { Box(Modifier.fillMaxSize().testTag("pane")) },
                            paneBackground = { Color.Magenta }, edgeBackground = Color.Cyan,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val root = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 24, 0)).build())
        }
        compose.waitForIdle()
        assertEquals("Dispatch must actually reach Compose", 24, rightInset)
        val rootBounds = compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
        val paneBounds = compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
        assertEquals(rootBounds.right - 24, paneBounds.right, 1f)
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        assertEquals("Seeded edge cyan must not leak into the nav band", monoPaper(dark), Color(bitmap.getPixel(root.width - 12, root.height / 2)))
        assertEquals("Seeded pane magenta must not leak into the reader", monoPaper(dark), Color(bitmap.getPixel(paneBounds.center.x.toInt(), paneBounds.center.y.toInt())))
    }
}
