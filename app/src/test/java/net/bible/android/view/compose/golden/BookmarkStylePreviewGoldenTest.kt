package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.bookmark.BookmarkStylePreview
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * All four styles in one capture, so a change to any of them is one golden to look at. Captured in
 * the 4-mode matrix because the label colour must degrade to grey in BW and stay coloured in
 * COLOR_EINK — the preview is the screen's only large block of label colour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarkStylePreviewGoldenTest {

    private val allFour = @Composable {
        Column {
            BookmarkDisplayStyle.entries.forEach { style ->
                BookmarkStylePreview(
                    style = style,
                    colorArgb = AbColor.palette.first(),
                    sampleText = "For God so loved the world",
                    // bookmarkIcon (LabelEditGoldenTest.kt) takes a nullable icon-name key; this
                    // preview's iconSlot takes none, so adapt at this call site.
                    iconSlot = { bookmarkIcon(null) },
                )
            }
        }
    }

    @Test fun bookmarkStylePreview_all() =
        captureMatrix("BookmarkStylePreview", "all", heightDp = 400, content = allFour)
}
