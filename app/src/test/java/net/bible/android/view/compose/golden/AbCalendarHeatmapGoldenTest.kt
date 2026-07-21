package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.progress.*
import net.bible.sharedui.progress.AbCalendarHeatmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbCalendarHeatmapGoldenTest {
    private fun fixture(): @Composable () -> Unit {
        val skeleton = CalendarSkeleton(
            slots = (0 until 8).flatMap { w -> (0 until 7).map { d -> DaySlot(w, d, (w*7L+d)*86_400_000L) } },
            monthLabels = listOf(MonthLabel(0, "Jan"), MonthLabel(4, "Feb")),
            weeks = 8, dayOfWeekLabels = listOf("","M","","W","","F",""),
        )
        val counts = skeleton.slots.associate { it.dayTimestamp to (it.weekIndex + it.dayIndex) % 5 }
        val heat = CalendarHeatmapLayout.assemble(skeleton, counts)
        return { AbCalendarHeatmap(heat) }
    }
    @Test fun heatmap_primary() = captureMatrix("AbCalendarHeatmap", "primary", heightDp = 200, content = fixture())
    @Test @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun heatmap_primary_rtl() = captureRtl("AbCalendarHeatmap", "primary", heightDp = 200, content = fixture())
}
