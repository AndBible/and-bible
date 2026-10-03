package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.navigation.ChooseGeneralBookKeyScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ChooseGeneralBookKeyGoldenTest {
    private val sample = listOf(
        KeyRow("0", "Introduction"),
        KeyRow("1", "Chapter 1 — In the beginning"),
        KeyRow("2", "Chapter 2 — The garden"),
        KeyRow("3", "Chapter 3 — The fall"),
        KeyRow("4", "Appendix"),
    )

    @Test fun general_book_primary() {
        captureMatrix("ChooseGeneralBookKey", "primary") {
            ChooseGeneralBookKeyScreen("Select item", sample, "2", null, {}, {}, {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun general_book_primary_rtl() {
        captureRtl("ChooseGeneralBookKey", "primary") {
            ChooseGeneralBookKeyScreen("Select item", sample, "2", null, {}, {}, {})
        }
    }

    @Test fun general_book_empty() {
        captureGolden("ChooseGeneralBookKey", "empty", EDGE_MODE) {
            ChooseGeneralBookKeyScreen("Select item", emptyList(), null, null, {}, {}, {})
        }
    }
}
