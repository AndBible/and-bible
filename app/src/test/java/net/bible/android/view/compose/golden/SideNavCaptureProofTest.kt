package net.bible.android.view.compose.golden

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import net.bible.android.TEST_SDK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class SideNavCaptureProofTest {
    @Test fun lightHookUpdatesRealLayoutInsideActivityAction() = hookUpdatesRealLayout(false)
    @Test fun darkHookUpdatesRealLayoutInsideActivityAction() = hookUpdatesRealLayout(true)

    private fun hookUpdatesRealLayout(dark: Boolean) {
        // Same unmanaged ActivityScenario/onActivity lifecycle as captureRoboImage, no ComposeRule
        // frame clock or waitForIdle secretly doing the traversal for the hook.
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val proof = SideNavCaptureProof()
                activity.setContent {
                    net.bible.sharedui.ProvideAppLocals {
                        net.bible.sharedui.theme.AbTheme(darkTheme = dark,
                            colorMode = net.bible.service.common.DisplayColorMode.MONOCHROME,
                            disableAnimations = true) {
                            ReadingViewScreenGoldenTest().seededSideNavScreen(proof)()
                        }
                    }
                }
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                proof.beforeCapture()
            }
        }
    }
}
