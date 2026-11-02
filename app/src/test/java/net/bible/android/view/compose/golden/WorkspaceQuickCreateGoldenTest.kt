package net.bible.android.view.compose.golden

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.workspaces.WorkspaceCreateAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures the real name dialog after tapping the quick selector's Add action. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceQuickCreateGoldenTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(dark: Boolean, mode: DisplayColorMode, tag: String) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                    AbQuickSheetContent(
                        title = "Switch to workspace", onClose = {},
                        actions = { WorkspaceCreateAction(3, {}) },
                    ) {}
                }
            }
        }
        compose.onNodeWithContentDescription("New").performClick()
        compose.onNode(isDialog()).captureRoboImage("src/test/roborazzi/WorkspaceQuick_create_$tag.png")
    }

    @Test fun light() = capture(false, DisplayColorMode.NORMAL, "light")
    @Test fun dark() = capture(true, DisplayColorMode.NORMAL, "dark")
    @Test fun monochrome() = capture(false, DisplayColorMode.MONOCHROME, "bw")
}
