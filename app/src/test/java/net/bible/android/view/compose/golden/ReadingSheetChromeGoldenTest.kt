package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.components.AbSheetDragHandle
import net.bible.sharedui.search.EpubSearchSettings
import net.bible.sharedui.search.SearchSettingsSheetContent
import net.bible.sharedui.theme.isPureMonochrome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capture sheet content, never an open modal window (which hangs Roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingSheetChromeGoldenTest {
    @Test fun compactHandle() = captureMatrix("ReadingSheetChrome", "compactHandle", heightDp = 100) {
        SheetSurface {
            Column {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AbSheetDragHandle(description = "Dismiss sheet", mono = isPureMonochrome())
                }
                Text("Sheet content", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp))
                Box(Modifier.height(16.dp))
            }
        }
    }

    @Test fun searchSettings() = captureMatrix("ReadingSheetChrome", "searchSettings", heightDp = 240) {
        SheetSurface {
            SearchSettingsSheetContent(onClose = {}) {
                EpubSearchSettings(mode = EpubSearchMode.PHRASE, onMode = {})
            }
        }
    }
}
