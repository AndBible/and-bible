package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.navigation.ChooseMapKeyScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ChooseMapKeyGoldenTest {
    private val sample = listOf(
        KeyRow("0", "The Exodus from Egypt"),
        KeyRow("1", "The Twelve Tribes"),
        KeyRow("2", "Paul's First Missionary Journey"),
        KeyRow("3", "Paul's Second Missionary Journey"),
    )

    @Test fun map_primary() {
        captureMatrix("ChooseMapKey", "primary") {
            ChooseMapKeyScreen("Select item", sample, "0", null, {}, {}, {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun map_primary_rtl() {
        captureRtl("ChooseMapKey", "primary") {
            ChooseMapKeyScreen("Select item", sample, "0", null, {}, {}, {})
        }
    }
}
