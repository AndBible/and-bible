package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbChoiceGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Four options with a preview (the shape the two style sections use) above five options without one
 * (the shape the workspace override uses) — the five-option case is here because an odd count
 * leaves the last row half-empty, and that has to look deliberate rather than broken.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbChoiceGroupGoldenTest {

    private val bothShapes = @Composable {
        Column {
            AbChoiceGroup(
                heading = "Text selection bookmarks",
                options = listOf("Highlight", "Underline", "Marker only", "Hidden"),
                selected = "Underline",
                optionLabel = { it },
                onSelect = {},
                preview = { Text("...for God so loved the world...") },
            )
            AbChoiceGroup(
                heading = "Override style",
                options = listOf("No override", "Highlight", "Underline", "Marker only", "Hidden"),
                selected = "No override",
                optionLabel = { it },
                onSelect = {},
            )
        }
    }

    @Test fun choiceGroup_withAndWithoutPreview() =
        captureGolden("AbChoiceGroup", "shapes", EDGE_MODE, heightDp = 520, content = bothShapes)
}
