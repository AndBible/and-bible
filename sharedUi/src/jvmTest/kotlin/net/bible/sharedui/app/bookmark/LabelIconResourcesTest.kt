package net.bible.sharedui.app.bookmark

import net.bible.sharedui.icons.AbIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.theme.AbTheme
import kotlin.test.*

class LabelIconResourcesTest {
    @Test fun labelIconForEveryKeyAndUnknownFallback() {
        val icons = ResourceLabelIcons()
        val expected = listOf("book", "book-bible", "cross", "church", "star-of-david", "person-praying",
            "info", "question", "exclamation", "lightbulb", "bell", "flag", "star", "tag", "envelope", "comment",
            "share-nodes", "link", "handshake", "clock", "map-marker", "globe", "landmark", "calendar", "user",
            "music", "microphone", "key", "crown", "heart", "heart-crack", "robot")
        assertEquals(expected + null, icons.iconKeys)
        for (key in expected) {
            assertEquals(AbIcons.labelIconResources.getValue(key), icons.labelResource(key))
            assertEquals(AbIcons.labelIconResources.getValue(key), icons.manageResource(key, false))
            assertEquals(AbIcons.labelIconResources.getValue(key), icons.manageResource(key, true))
        }
        for (key in listOf(null, "unknown-key")) {
            assertEquals(AbIcons.bookmarkIconResources.getValue("ic_baseline_bookmark_24"), icons.labelResource(key))
            assertEquals(AbIcons.bookmarkIconResources.getValue("ic_label_24dp"), icons.manageResource(key, false))
            assertEquals(AbIcons.bookmarkIconResources.getValue("ic_baseline_studypads_24"), icons.manageResource(key, true))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun actualIconSlotsRenderInLightDarkAndMonochromeWithoutAnimations() = runComposeUiTest {
        val icons = ResourceLabelIcons()
        for ((dark, mode) in listOf(false to DisplayColorMode.NORMAL, true to DisplayColorMode.NORMAL,
            false to DisplayColorMode.MONOCHROME, true to DisplayColorMode.MONOCHROME)) {
            setContent {
                AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                    Column(Modifier.testTag("slots")) {
                        icons.LabelIcon("star", Color.Gray)
                        icons.LabelIcon(null, Color.Gray)
                        icons.ManageLabelIcon(null, Color.Gray, true)
                        icons.ManageLabelIcon("not-known", Color.Gray, false)
                        icons.ShareIcon()
                        icons.SaveIcon()
                        BookmarkActionIcon.entries.forEach { icons.ActionIcon(it, it.name, Modifier) }
                    }
                }
            }
            val pixels = onNodeWithTag("slots").captureToImage().toPixelMap()
            assertTrue(pixels.buffer.any { it != 0 }, "actual icon slots must not be empty")
        }
    }

    @Test fun everyActionRetainsTheAndroidGlyph() {
        val icons = ResourceLabelIcons()
        val keys = listOf("ic_search_24dp", "ic_add_circle_outline_white_24dp", "ic_help_white_24dp",
            "ic_baseline_refresh_24", "ic_text_format_white_24dp", "ic_baseline_undo_24", "file_export",
            "ic_file_download_24dp", "ic_check_24dp", "ic_delete_24dp")
        assertEquals(keys.size, BookmarkActionIcon.entries.size)
        BookmarkActionIcon.entries.zip(keys).forEach { (action, key) ->
            assertEquals(AbIcons.bookmarkActionIconResources.getValue(key), icons.actionResource(action))
        }
    }
}
