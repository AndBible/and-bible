package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.speak.PickedVerse
import net.bible.sharedcore.speak.SleepTimerSelection
import net.bible.sharedui.speak.SleepTimerContent
import net.bible.sharedui.speak.SpeakRangeContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SpeakSheetPagesGoldenTest {
    private fun range(start: PickedVerse?, end: PickedVerse?, err: Boolean, ok: Boolean) =
        @Composable {
            Column {
                SpeakRangeContent(
                    start = start, end = end, showOrderError = err, canCommit = ok,
                    onPickStart = {}, onPickEnd = {}, onClear = {}, onConfirm = {}, onCancel = {},
                )
            }
        }
    private val ps1 = PickedVerse("Ps.23.1", "Ps 23:1", 14240)
    private val ps6 = PickedVerse("Ps.23.6", "Ps 23:6", 14245)

    @Test fun range_empty() = captureGolden("SpeakRange", "empty", EDGE_MODE, heightDp = 400,
        content = range(null, null, err = false, ok = false))
    @Test fun range_set() = captureMatrix("SpeakRange", "set", heightDp = 400,
        content = range(ps1, ps6, err = false, ok = true))
    @Test fun range_order_error() = captureGolden("SpeakRange", "order_error", EDGE_MODE, heightDp = 400,
        content = range(ps6, ps1, err = true, ok = false))

    private fun timer(sel: SleepTimerSelection, custom: Int) = @Composable {
        Column {
            SleepTimerContent(selection = sel, customMinutes = custom, onPick = {})
        }
    }
    @Test fun timer_off() = captureGolden("SleepTimer", "off", EDGE_MODE, heightDp = 400,
        content = timer(SleepTimerSelection.Off, 10))
    @Test fun timer_preset() = captureMatrix("SleepTimer", "preset", heightDp = 400,
        content = timer(SleepTimerSelection.Preset(30), 30))
    @Test fun timer_custom() = captureGolden("SleepTimer", "custom", EDGE_MODE, heightDp = 400,
        content = timer(SleepTimerSelection.Custom(37), 37))
}
