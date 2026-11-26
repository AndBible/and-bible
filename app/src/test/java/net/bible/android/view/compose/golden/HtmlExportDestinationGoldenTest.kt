package net.bible.android.view.compose.golden

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbActionSheetContent
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.strings.AndroidStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Destination sheet body only; BibleViewSurfaceHtmlExportTest verifies the request from the Android surface. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class HtmlExportDestinationGoldenTest {
    @Test fun exportDestination() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val strings = AndroidStrings(context)
        captureMatrix("HtmlExport", "destination", heightDp = 340) {
            SheetSurface {
                AbActionSheetContent(
                    title = strings.exportDestinationTitle,
                    message = strings.exportDestinationMessage,
                    onClose = {},
                ) {
                    AbActionSheetRow(label = context.getString(net.bible.android.activity.R.string.share), onClick = {})
                    AbActionSheetRow(label = strings.backupPhoneStorage, onClick = {})
                }
            }
        }
    }
}
