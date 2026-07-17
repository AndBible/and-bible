package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsItemTest {
    @Test fun visibleItems_filtersHiddenAndPreservesOrder() {
        val state = SettingsScreenState(
            title = "T",
            items = listOf(
                SettingsItem.Category("cat", "General"),
                SettingsItem.SwitchRow("a", "A", checked = true),
                SettingsItem.SwitchRow("b", "B", checked = false, visible = false),
                SettingsItem.NavigationRow("nav", "Go"),
            ),
        )
        assertEquals(listOf("cat", "a", "nav"), state.visibleItems.map { it.key })
    }
}
