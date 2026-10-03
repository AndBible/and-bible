package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbReorderableColumn
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbReorderableColumnGoldenTest {
    @Composable
    private fun sample() = AbReorderableColumn(
        items = listOf("Alpha", "Beta", "Gamma"),
        key = { it },
        onMove = { _, _ -> },
    ) { item, handle ->
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.DragHandle, contentDescription = null, modifier = handle)
            Text(item, modifier = Modifier.padding(start = 16.dp))
        }
    }

    @Test fun reorderable_resting() { captureMatrix("AbReorderableColumn", "resting") { sample() } }
}
