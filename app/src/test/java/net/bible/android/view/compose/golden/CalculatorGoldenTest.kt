package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.calculator.CalcError
import net.bible.sharedui.calculator.CalculatorScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the pilot disguise [CalculatorScreen] (F8: tonal keypad + accent operators + grouped
 * display panel). The screen is stateless — these render fixed [display]/[error] props, no math runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CalculatorGoldenTest {
    @Test fun calculator_primary() {
        captureMatrix("Calculator", "primary") {
            CalculatorScreen(display = "2+2", error = null, onKey = {})
        }
    }

    @Test fun calculator_error() {
        captureGolden("Calculator", "error", EDGE_MODE) {
            CalculatorScreen(display = "2++", error = CalcError.WRONG_FORMAT, onKey = {})
        }
    }
}
