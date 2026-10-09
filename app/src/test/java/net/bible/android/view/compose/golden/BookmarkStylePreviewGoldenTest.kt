package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.bookmark.BookmarkStylePreview
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoInk
import net.bible.sharedui.theme.LocalIsDarkTheme
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
                    // bookmarkIcon (LabelEditGoldenTest.kt) takes a nullable icon-name key plus a
                    // tint; this preview's iconSlot takes neither, so adapt at this call site --
                    // Color(colorArgb) mirrors what LabelEditScreen's real preview call passes for
                    // a non-null custom icon (the label's own colour, since this background is the
                    // neutral card, not a same-coloured disc).
                    iconSlot = { bookmarkIcon(null, if (isPureMonochrome()) monoInk(LocalIsDarkTheme.current) else Color(AbColor.palette.first())) },
                )
            }
        }
    }

    @Test fun bookmarkStylePreview_all() =
        captureMatrix("BookmarkStylePreview", "all", heightDp = 400, content = allFour)

    private val allFourPartial = @Composable {
        Column {
            BookmarkDisplayStyle.entries.forEach { style ->
                BookmarkStylePreview(
                    style = style,
                    colorArgb = AbColor.palette.first(),
                    sampleText = "For God so loved the world",
                    decoratePartially = true,
                    iconSlot = { bookmarkIcon(null, if (isPureMonochrome()) monoInk(LocalIsDarkTheme.current) else Color(AbColor.palette.first())) },
                )
            }
        }
    }

    /** The selection axis: only the first half of the sentence is decorated. The full-decoration
     *  counterpart is [bookmarkStylePreview_all]; reading the two together is what shows the
     *  convention. MARKER's glyph is a superscript here, at 60% of the text size in the label's
     *  colour, matching the reader. */
    @Test fun bookmarkStylePreview_partial() =
        captureMatrix("BookmarkStylePreview", "partial", heightDp = 400, content = allFourPartial)

    @Test fun bookmarkStylePreview_all_mono() {
        MONO_MODES.forEach { captureGolden("BookmarkStylePreview", "all", it, heightDp = 400, content = allFour) }
    }

    @Test fun bookmarkStylePreview_partial_mono() {
        MONO_MODES.forEach { captureGolden("BookmarkStylePreview", "partial", it, heightDp = 400, content = allFourPartial) }
    }
}
