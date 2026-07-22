package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedui.reading.WindowSeparator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingSeparatorGoldenTest {
    @Test fun verticalBar() {
        captureMatrix("ReadingSeparator", "verticalBar") {
            Box(Modifier.width(60.dp).height(200.dp)) {
                WindowSeparator(isVertical = false, onDragBy = {}, onDragEnd = {}, modifier = Modifier.fillMaxSize())
            }
        }
    }

    @Test fun horizontalBar() {
        captureGolden("ReadingSeparator", "horizontalBar", EDGE_MODE) {
            Box(Modifier.width(200.dp).height(60.dp)) {
                WindowSeparator(isVertical = true, onDragBy = {}, onDragEnd = {}, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
