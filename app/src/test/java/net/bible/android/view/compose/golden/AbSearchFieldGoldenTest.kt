package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbSearchField
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbSearchFieldGoldenTest {
    @Test fun searchfield_filled() {
        captureMatrix("AbSearchField", "filled") {
            Column { AbSearchField(value = "faith", onValueChange = {}, placeholder = "Search") }
        }
    }

    @Test fun searchfield_empty() {
        captureGolden("AbSearchField", "empty", EDGE_MODE) {
            Column { AbSearchField(value = "", onValueChange = {}, placeholder = "Search") }
        }
    }
}
