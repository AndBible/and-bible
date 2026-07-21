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

    @Test fun newIconAndClickFields_defaultToNull() {
        val nav = SettingsItem.NavigationRow("nav", "Go")
        val switch = SettingsItem.SwitchRow("sw", "Switch", checked = true)
        val info = SettingsItem.InfoRow("info", "Info")
        val listChoice = SettingsItem.ListChoiceRow("lc", "Choice", entries = emptyList(), selectedValue = "a")
        val textInput = SettingsItem.TextInputRow("ti", "Input", value = "x")
        assertEquals(null, nav.iconKey)
        assertEquals(null, switch.iconKey)
        assertEquals(null, info.iconKey)
        assertEquals(null, info.onClickKey)
        assertEquals(null, listChoice.iconKey)
        assertEquals(null, textInput.iconKey)
    }

    @Test fun newIconAndClickFields_holdWhenSet() {
        val nav = SettingsItem.NavigationRow("nav", "Go", iconKey = "ic_nav")
        val switch = SettingsItem.SwitchRow("sw", "Switch", checked = true, iconKey = "ic_switch")
        val info = SettingsItem.InfoRow(
            "info",
            "Info",
            iconKey = "ic_info",
            onClickKey = "info_dialog",
        )
        val listChoice = SettingsItem.ListChoiceRow(
            "lc", "Choice", entries = emptyList(), selectedValue = "a", iconKey = "ic_lc",
        )
        val textInput = SettingsItem.TextInputRow("ti", "Input", value = "x", iconKey = "ic_ti")
        assertEquals("ic_nav", nav.iconKey)
        assertEquals("ic_switch", switch.iconKey)
        assertEquals("ic_info", info.iconKey)
        assertEquals("info_dialog", info.onClickKey)
        assertEquals("ic_lc", listChoice.iconKey)
        assertEquals("ic_ti", textInput.iconKey)
    }

    @Test fun sliderAndMultiSelect_areVisibleItemsAndCarryValues() {
        val state = SettingsScreenState(
            title = "T",
            items = listOf(
                SettingsItem.SliderRow("font", "Font", value = 150, min = 10, max = 500, valueLabel = "150 %"),
                SettingsItem.MultiSelectRow(
                    "dicts", "Dictionaries",
                    options = listOf(SettingsItem.Choice("a", "A"), SettingsItem.Choice("b", "B")),
                    selectedValues = setOf("a"),
                ),
                SettingsItem.MultiSelectRow("hidden", "H", options = emptyList(), selectedValues = emptySet(), visible = false),
            ),
        )
        assertEquals(listOf("font", "dicts"), state.visibleItems.map { it.key })
        assertEquals(150, (state.visibleItems[0] as SettingsItem.SliderRow).value)
        assertEquals(setOf("a"), (state.visibleItems[1] as SettingsItem.MultiSelectRow).selectedValues)
    }
}
