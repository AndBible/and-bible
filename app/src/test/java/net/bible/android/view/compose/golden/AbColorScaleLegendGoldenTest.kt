package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedui.progress.AbColorScaleLegend
import net.bible.sharedui.progress.countHeatColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbColorScaleLegendGoldenTest {

    private val steps = ReadingProgressScale.countScaleSteps(12)

    private val content = @androidx.compose.runtime.Composable {
        AbColorScaleLegend(
            label = "Read count:",
            steps = steps,
            stepColor = { countHeatColors(it, 12).background },
            stepLabel = { "$it" },
        )
    }

    @Test fun count_matrix() =
        captureMatrix("AbColorScaleLegend", "count", heightDp = 80, content = content)

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun count_rtl() =
        captureRtl("AbColorScaleLegend", "count", heightDp = 80, content = content)
}
