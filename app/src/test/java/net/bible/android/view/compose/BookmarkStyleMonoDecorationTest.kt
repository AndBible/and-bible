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
                assertEquals(mode == DisplayColorMode.MONOCHROME && style == BookmarkDisplayStyle.HIGHLIGHT, decoration.frame)
                assertEquals(style == BookmarkDisplayStyle.MARKER, decoration.showsMarkerIcon)
            }
        }
    }

    private fun checkPixels(dark: Boolean) {
        val ink = if (dark) Color.White else Color.Black
        val paper = if (dark) Color.Black else Color.White
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME, disableAnimations = true) {
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
        val left = (bounds.left.value * density).toInt()
        val top = (bounds.top.value * density).toInt()
        val right = (bounds.right.value * density).toInt() - 1
        val bottom = (bounds.bottom.value * density).toInt() - 1
        val x = (left + right) / 2
        val y = (top + bottom) / 2
        assertEquals(paper, Color(bitmap.getPixel(x, y)), "fill stays transparent over paper")
        listOf(x to top, x to bottom, left to y, right to y).forEach { (px, py) ->
            assertEquals(ink, Color(bitmap.getPixel(px, py)), "all four rectangle edges use ink")
        }
        bitmap.recycle()
    }

    private fun checkEditorMarker(dark: Boolean) {
        val seed = 0xFF43A9D7.toInt()
        val tints = mutableListOf<Color>()
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME, disableAnimations = false) {
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

    @Test fun actual_editor_marker_light_uses_ink() = checkEditorMarker(false)
    @Test fun actual_editor_marker_dark_uses_ink() = checkEditorMarker(true)

    @Test fun mono_light_paints_transparent_fill_and_ink_edges() = checkPixels(false)
    @Test fun mono_dark_paints_transparent_fill_and_ink_edges() = checkPixels(true)

    @Test fun mono_light_frames_only_highlights() = checkMode(DisplayColorMode.MONOCHROME, false)
    @Test fun mono_dark_frames_only_highlights() = checkMode(DisplayColorMode.MONOCHROME, true)
    @Test fun bw_keeps_band_decorations() = checkMode(DisplayColorMode.BW, false)
    @Test fun normal_keeps_band_decorations() = checkMode(DisplayColorMode.NORMAL, false)
    @Test fun color_eink_keeps_band_decorations() = checkMode(DisplayColorMode.COLOR_EINK, false)
}
