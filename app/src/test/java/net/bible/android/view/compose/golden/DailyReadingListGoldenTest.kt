package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.readingplan.DayEntry
import net.bible.sharedui.readingplan.DailyReadingListScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DailyReadingListGoldenTest {
    private val sample = listOf(
        DayEntry(1, "Day 1", "Genesis 1-2; Matthew 1"),
        DayEntry(2, "Day 2", "Genesis 3-4; Matthew 2"),
        DayEntry(3, "Day 3", "Genesis 5-7; Matthew 3"),
    )

    @Test fun daylist_primary() {
        captureMatrix("DailyReadingList", "primary") {
            DailyReadingListScreen("Reading Plan", sample, null, {}, {}, {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun daylist_primary_rtl() {
        captureRtl("DailyReadingList", "primary") {
            DailyReadingListScreen("Reading Plan", sample, null, {}, {}, {})
        }
    }
}
