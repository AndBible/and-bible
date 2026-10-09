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
        captureMatrix("ReadingSeparator", "verticalBar", content = verticalBarContent())
    }

    @Test fun horizontalBar() {
        captureGolden("ReadingSeparator", "horizontalBar", EDGE_MODE, content = horizontalBarContent())
    }

    // isActive=true: this separator is adjacent to the active window (primary theme role).
    @Test fun activeBar() {
        captureMatrix("ReadingSeparator", "activeBar", content = activeBarContent())
    }

    // isDragging=true: this separator is currently being dragged (tertiary theme role, takes
    // priority over isActive — see WindowSeparator's kdoc).
    @Test fun dragBar() {
        captureMatrix("ReadingSeparator", "dragBar", content = dragBarContent())
    }

    private fun verticalBarContent(): @androidx.compose.runtime.Composable () -> Unit = {
            Box(Modifier.width(60.dp).height(200.dp)) {
                WindowSeparator(isVertical = false, onDragBy = {}, onDragEnd = {}, modifier = Modifier.fillMaxSize())
            }
        }

    private fun horizontalBarContent(): @androidx.compose.runtime.Composable () -> Unit = {
            Box(Modifier.width(200.dp).height(60.dp)) {
                WindowSeparator(isVertical = true, onDragBy = {}, onDragEnd = {}, modifier = Modifier.fillMaxSize())
            }
        }

    private fun activeBarContent(): @androidx.compose.runtime.Composable () -> Unit = {
            Box(Modifier.width(60.dp).height(200.dp)) {
                WindowSeparator(isVertical = false, onDragBy = {}, onDragEnd = {}, isActive = true, modifier = Modifier.fillMaxSize())
            }
        }

    private fun dragBarContent(): @androidx.compose.runtime.Composable () -> Unit = {
            Box(Modifier.width(60.dp).height(200.dp)) {
                WindowSeparator(isVertical = false, onDragBy = {}, onDragEnd = {}, isDragging = true, modifier = Modifier.fillMaxSize())
            }
        }

    @Test
    fun verticalBar_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingSeparator", "verticalBar", mode, content = verticalBarContent()) }
    }

    @Test
    fun horizontalBar_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingSeparator", "horizontalBar", mode, content = horizontalBarContent()) }
    }

    @Test
    fun activeBar_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingSeparator", "activeBar", mode, content = activeBarContent()) }
    }

    @Test
    fun dragBar_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingSeparator", "dragBar", mode, content = dragBarContent()) }
    }
}
