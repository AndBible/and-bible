package net.bible.android.view.compose

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbScaffold
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix batch 2 §2.1.2. From API 35 the host window is edge-to-edge, where `adjustResize` no longer
 * shrinks anything: the app must consume `WindowInsets.ime`, and before this fix no non-reading
 * screen did (Material's Scaffold default is `systemBarsForVisualComponents`, no IME). A text field
 * in the lower half of e.g. PromptEditScreen stayed under the keyboard. On 30–34 non-reading
 * destinations keep decor-fits (Task 2), Compose sees ime = 0 there, and this is a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ScaffoldImeInsetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun abScaffoldContentStopsAtTheKeyboard() {
        compose.setContent {
            ProvideAppLocals {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    AbScaffold(title = "t") { padding ->
                        Box(Modifier.padding(padding).fillMaxSize().testTag("content"))
                    }
                }
            }
        }
        val root: ViewGroup = compose.activity.findViewById(android.R.id.content)
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 39))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
                    .build(),
            )
        }
        compose.waitForIdle()
        val rootB = compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertEquals("content must end at the keyboard's top, not the nav bar's", rootB.bottom - 300f, content.bottom, 1f)
    }
}
