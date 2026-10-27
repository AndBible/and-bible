package net.bible.android.view.compose

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedui.reading.ReadingDrawerContent
import net.bible.sharedui.reading.ReadingDrawerWidth
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
 * ReadingHost now wraps ReadingDrawerContent in ModalDrawerSheet, whose default insets must
 * keep the footer above the bar. Mount that same sheet/content pair without opening an animated
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
                    ModalDrawerSheet(modifier = Modifier.width(ReadingDrawerWidth)) {
                        ReadingDrawerContent(
                            state = DrawerMenuState(appName = "AndBible", versionText = versionText, groups = emptyList()),
                            icon = { null },
                            onItemClick = {},
                        )
                    }
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
