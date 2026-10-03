package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingPlanError
import net.bible.sharedui.readingplan.ReadingPlanSelectorScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingPlanSelectorGoldenTest {
    private val sample = listOf(
        PlanEntry("y1ntpspr", "Bible in a year: NT, Psalms, Proverbs", "One-year plan through the NT with Psalms and Proverbs"),
        PlanEntry("y1ot1nt1_chronological", "Bible in a year: Chronological", "Read the whole Bible in chronological order"),
        PlanEntry("mcheyne", "M'Cheyne one-year", "Robert Murray M'Cheyne's classic plan"),
    )

    @Test fun selector_primary() {
        captureMatrix("ReadingPlanSelector", "primary") {
            ReadingPlanSelectorScreen("Choose a Reading Plan", sample, false, null, {}, {}, {}, {}, {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun selector_primary_rtl() {
        captureRtl("ReadingPlanSelector", "primary") {
            ReadingPlanSelectorScreen("Choose a Reading Plan", sample, false, null, {}, {}, {}, {}, {})
        }
    }

    @Test fun selector_empty() {
        captureGolden("ReadingPlanSelector", "empty", EDGE_MODE) {
            ReadingPlanSelectorScreen("Choose a Reading Plan", emptyList(), false, null, {}, {}, {}, {}, {})
        }
    }

    @Test fun selector_duplicate() {
        captureGolden("ReadingPlanSelector", "duplicate", EDGE_MODE) {
            ReadingPlanSelectorScreen("Choose a Reading Plan", sample, true, null, {}, {}, {}, {}, {})
        }
    }
}
