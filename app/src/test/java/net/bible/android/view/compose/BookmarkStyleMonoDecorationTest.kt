package net.bible.android.view.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.BookmarkStyleDecoration
import net.bible.sharedui.bookmark.bookmarkStyleDecoration
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarkStyleMonoDecorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun checkMode(mode: DisplayColorMode, dark: Boolean) {
        val decorations = mutableMapOf<BookmarkDisplayStyle, BookmarkStyleDecoration>()
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                    BookmarkDisplayStyle.entries.forEach {
                        decorations[it] = bookmarkStyleDecoration(it, 0xFFFF0000.toInt())
                    }
                }
            }
        }
        compose.runOnIdle {
            decorations.forEach { (style, decoration) ->
                // #4069: only MONOCHROME turns a highlight into bold text; COLOR_EINK keeps the band.
                assertEquals(mode == DisplayColorMode.MONOCHROME && style == BookmarkDisplayStyle.HIGHLIGHT, decoration.bold)
                assertEquals(style == BookmarkDisplayStyle.MARKER, decoration.showsMarkerIcon)
            }
        }
    }

    /** Paints the HIGHLIGHT decoration over paper; returns the centre pixel and the four edge pixels. */
    private fun highlightPixels(mode: DisplayColorMode, dark: Boolean): Pair<Color, List<Color>> {
        val paper = if (dark) Color.Black else Color.White
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                    Box(Modifier.background(paper)) {
                        Box(Modifier.size(80.dp, 40.dp).testTag("decoration")
                            .then(bookmarkStyleDecoration(BookmarkDisplayStyle.HIGHLIGHT, 0xFFFF0000.toInt()).textModifier))
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("decoration").getUnclippedBoundsInRoot()
        val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { content.draw(Canvas(bitmap)) }
        val density = compose.density.density
        // The 2dp horizontal padding is inside the decorated box; sample a few px in from each edge.
        val left = (bounds.left.value * density).toInt() + 1
        val top = (bounds.top.value * density).toInt() + 1
        val right = (bounds.right.value * density).toInt() - 2
        val bottom = (bounds.bottom.value * density).toInt() - 2
        val x = (left + right) / 2
        val y = (top + bottom) / 2
        val centre = Color(bitmap.getPixel(x, y))
        val edges = listOf(x to top, x to bottom, left to y, right to y).map { (px, py) -> Color(bitmap.getPixel(px, py)) }
        bitmap.recycle()
        return centre to edges
    }

    private fun checkMonoPaintsNothing(dark: Boolean) {
        val paper = if (dark) Color.Black else Color.White
        val (centre, edges) = highlightPixels(DisplayColorMode.MONOCHROME, dark)
        assertEquals(paper, centre, "no fill: the highlight is bold text")
        edges.forEach { assertEquals(paper, it, "no frame edge any more (#4069)") }
    }

    private fun checkColorEinkPaintsLabelBand(dark: Boolean) {
        val (centre, _) = highlightPixels(DisplayColorMode.COLOR_EINK, dark)
        val (normal, _) = highlightPixels(DisplayColorMode.NORMAL, dark)
        assertEquals(normal, centre, "COLOR_EINK keeps the NORMAL label-colour band")
        kotlin.test.assertTrue(centre.red > centre.green + 0.1f, "band is the red label colour, got $centre")
    }

    /** Width of a whole-text HIGHLIGHT sample: bold text is wider than regular text. */
    private fun sampleWidth(mode: DisplayColorMode): Float {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = mode, disableAnimations = true) {
                    Box(Modifier.testTag("sample")) {
                        net.bible.sharedui.bookmark.BookmarkStyleSample(
                            style = BookmarkDisplayStyle.HIGHLIGHT, colorArgb = 0xFFFF0000.toInt(),
                            text = "Highlighted words", textStyle = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
                            color = Color.Black, iconSlot = {},
                        )
                    }
                }
            }
        }
        val b = compose.onNodeWithTag("sample").getUnclippedBoundsInRoot()
        return (b.right - b.left).value
    }

    @Test fun mono_highlight_sample_is_bold_others_are_not() {
        val mono = sampleWidth(DisplayColorMode.MONOCHROME)
        val normal = sampleWidth(DisplayColorMode.NORMAL)
        val eink = sampleWidth(DisplayColorMode.COLOR_EINK)
        kotlin.test.assertTrue(mono > normal, "MONOCHROME highlight renders bold (wider): $mono vs $normal")
        assertEquals(normal, eink, "COLOR_EINK highlight keeps regular weight")
    }

    private fun checkEditorMarker(dark: Boolean, mode: DisplayColorMode = DisplayColorMode.MONOCHROME) {
        val seed = 0xFF43A9D7.toInt()
        val tints = mutableListOf<Color>()
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = false) {
                    net.bible.sharedui.bookmark.LabelEditScreen(
                        state = net.bible.sharedcore.bookmark.LabelEditState(
                            labelId = "L1", name = "Study", color = seed, customIcon = "star",
                            selectionStyle = BookmarkDisplayStyle.MARKER, wholeVerseStyle = BookmarkDisplayStyle.MARKER,
                            favourite = false, isAssigning = false, thisBookmarkSelected = false, thisBookmarkPrimary = false,
                            hasWorkspaceContext = false, autoAssign = false, autoAssignPrimary = false,
                            overrideMode = net.bible.sharedcore.bookmark.OverrideMode.NONE, isSpecialLabel = false, isSpeakLabel = false,
                        ),
                        onName = {}, onColor = {}, onCustomIcon = {}, onSelectionStyle = {}, onWholeVerseStyle = {},
                        onToggleFavourite = {}, onToggleSelected = {}, onTogglePrimary = {}, onToggleAutoAssign = {},
                        onToggleAutoAssignPrimary = {}, onOverrideMode = {}, onUp = {}, iconKeys = listOf("star"),
                        iconSlot = { _, tint -> tints.add(tint); Box(Modifier.size(24.dp).background(tint)) }, actions = {},
                    )
                }
            }
        }
        compose.runOnIdle {
            kotlin.test.assertTrue(tints.size >= 3, "identity and both actual marker previews render")
            tints.forEach { assertEquals(if (dark) Color.White else Color.Black, it, "stored seed must not leak grey into preview markers") }
        }
    }

    private fun checkListMarker(dark: Boolean, mode: DisplayColorMode = DisplayColorMode.MONOCHROME) {
        val tints = mutableListOf<Color>()
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                    net.bible.sharedui.bookmark.ManageLabelsScreen(
                        title = "Labels",
                        rows = listOf(net.bible.sharedcore.bookmark.ManageLabelsRow.Item(
                            net.bible.sharedcore.bookmark.LabelItem(
                                "L1", "Study", 0xFF43A9D7.toInt(), false, false, false, "star",
                                selectionStyle = BookmarkDisplayStyle.UNDERLINE,
                                wholeVerseStyle = BookmarkDisplayStyle.MARKER,
                            ), false, false, false, false,
                        )),
                        mode = net.bible.sharedcore.bookmark.ManageLabelsMode.ASSIGN,
                        styleTagsVisible = true, searchText = "",
                        searchMode = net.bible.sharedcore.bookmark.SearchMode.NAME_START,
                        onSearch = {}, onSetSearchMode = {}, filters = emptySet(), onToggleFilter = {},
                        searchModeActive = false, onCloseSearch = {}, onRowClick = {}, onRowLongClick = {},
                        onToggleChecked = {}, onToggleFavourite = {}, onSetPrimary = {}, onToggleAutoAssign = {},
                        onUp = {}, actions = {}, searchActions = {},
                        iconSlot = { _, tint -> tints.add(tint); Box(Modifier.size(24.dp).background(tint)) },
                    )
                }
            }
        }
        compose.runOnIdle {
            kotlin.test.assertTrue(tints.size >= 2, "identity and distinct whole-verse marker axis render")
            tints.forEach { assertEquals(if (dark) Color.White else Color.Black, it, "list markers must share editor ink") }
        }
    }

    @Test fun actual_list_marker_light_uses_ink() = checkListMarker(false)
    @Test fun actual_list_marker_dark_uses_ink() = checkListMarker(true)

    @Test fun actual_editor_marker_light_uses_ink() = checkEditorMarker(false)
    @Test fun actual_editor_marker_dark_uses_ink() = checkEditorMarker(true)

    // #4069: COLOR_EINK markers follow the MONOCHROME ink; only highlight/underline keep colour.
    @Test fun color_eink_list_marker_uses_ink() = checkListMarker(false, DisplayColorMode.COLOR_EINK)
    @Test fun color_eink_editor_marker_dark_uses_ink() = checkEditorMarker(true, DisplayColorMode.COLOR_EINK)

    @Test fun mono_light_highlight_paints_no_fill_or_frame() = checkMonoPaintsNothing(false)
    @Test fun mono_dark_highlight_paints_no_fill_or_frame() = checkMonoPaintsNothing(true)
    @Test fun color_eink_light_paints_label_band() = checkColorEinkPaintsLabelBand(false)
    @Test fun color_eink_dark_paints_label_band() = checkColorEinkPaintsLabelBand(true)

    @Test fun mono_light_bolds_only_highlights() = checkMode(DisplayColorMode.MONOCHROME, false)
    @Test fun mono_dark_bolds_only_highlights() = checkMode(DisplayColorMode.MONOCHROME, true)
    @Test fun bw_keeps_band_decorations() = checkMode(DisplayColorMode.BW, false)
    @Test fun normal_keeps_band_decorations() = checkMode(DisplayColorMode.NORMAL, false)
    @Test fun color_eink_keeps_band_decorations() = checkMode(DisplayColorMode.COLOR_EINK, false)
    @Test fun color_eink_dark_keeps_band_decorations() = checkMode(DisplayColorMode.COLOR_EINK, true)
}
