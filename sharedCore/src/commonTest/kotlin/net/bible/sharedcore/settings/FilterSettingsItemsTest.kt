package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterSettingsItemsTest {

    private fun cat(key: String, title: String) = SettingsItem.Category(key = key, title = title)
    private fun sw(key: String, title: String, summary: String? = null) =
        SettingsItem.SwitchRow(key = key, title = title, summary = summary, checked = false)

    // A representative two-category list.
    private val items = listOf(
        cat("display_cat", "Display"),
        sw("night_mode", "Night mode", summary = "Switch between light and dark"),
        sw("keep_screen_on", "Keep screen on"),
        cat("sync_cat", "Synchronization"),
        sw("sync_bookmarks", "Bookmarks", summary = "Sync your bookmarks to the cloud"),
    )

    @Test fun emptyQueryReturnsListUnchanged() {
        assertEquals(items, filterSettingsItems(items, ""))
    }

    @Test fun blankQueryReturnsListUnchanged() {
        assertEquals(items, filterSettingsItems(items, "   "))
    }

    @Test fun matchInTitleKeepsRowAndItsCategory() {
        val result = filterSettingsItems(items, "night")
        assertEquals(listOf("display_cat", "night_mode"), result.map { it.key })
    }

    @Test fun matchInSummaryKeepsRowAndItsCategory() {
        // "cloud" appears only in the Bookmarks summary, under the Synchronization category.
        val result = filterSettingsItems(items, "cloud")
        assertEquals(listOf("sync_cat", "sync_bookmarks"), result.map { it.key })
    }

    @Test fun categoryWithNoMatchingChildrenIsHidden() {
        // "screen" matches only "Keep screen on" (Display); the Synchronization category is dropped.
        val result = filterSettingsItems(items, "screen")
        assertEquals(listOf("display_cat", "keep_screen_on"), result.map { it.key })
    }

    @Test fun matchIsCaseInsensitive() {
        val result = filterSettingsItems(items, "NIGHT")
        assertEquals(listOf("display_cat", "night_mode"), result.map { it.key })
    }

    @Test fun noMatchesReturnsEmpty() {
        assertEquals(emptyList(), filterSettingsItems(items, "zzz-nothing"))
    }

    @Test fun topLevelRowWithoutCategoryMatchesOnItsOwn() {
        val flat = listOf(
            sw("standalone", "Standalone toggle", summary = "no category header"),
            cat("c", "Cat"),
            sw("child", "Child row"),
        )
        assertEquals(listOf("standalone"), filterSettingsItems(flat, "standalone").map { it.key })
    }
}
