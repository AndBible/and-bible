package net.bible.android.view.compose.golden

import android.app.Application
import com.github.takahirom.roborazzi.captureRoboImage
import net.bible.android.TEST_SDK
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.history.HistoryScreen
import net.bible.sharedui.theme.AbTheme
import net.bible.service.common.DisplayColorMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain empty Application keeps the golden render stateless — the manifest's DebugApp
// boots Koin/DB (crashes headlessly) and the plan requires goldens depend on neither.
@Config(sdk = [TEST_SDK], application = Application::class)
class SpikeGoldenTest {
    @Test
    fun spike_history_light() {
        captureRoboImage("src/test/roborazzi/Spike_history_light.png") {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    HistoryScreen(
                        title = "History",
                        entries = listOf(
                            HistoryEntry(0, "Genesis 1:1", "9:15 am, Tue 8 Jul"),
                            HistoryEntry(1, "John 3:16", "9:14 am, Tue 8 Jul"),
                        ),
                        error = null,
                        onSelect = {},
                        onDismissError = {},
                    )
                }
            }
        }
    }
}
