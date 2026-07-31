package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeService(var snap: TextSettingsSnapshot) : FakeColoursOnlyService() {
    val setCalls = mutableListOf<Triple<SettingsScope, TextSettingType, TextSettingValue>>()
    val revertCalls = mutableListOf<Pair<SettingsScope, TextSettingType>>()
    val resetCalls = mutableListOf<SettingsScope>()
    override fun loadText(scope: SettingsScope) = snap
    override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) { setCalls += Triple(scope, type, value) }
    override fun revert(scope: SettingsScope, type: TextSettingType) { revertCalls += scope to type }
    override fun reset(scope: SettingsScope) { resetCalls += scope }
}

class TextDisplaySettingsControllerTest {
    private fun bool(v: Boolean) = TextSettingRowValue.Bool(v)
    private fun row(t: TextSettingType, v: TextSettingRowValue, inh: InheritedFrom = InheritedFrom.NONE,
                    enabled: Boolean = true, visible: Boolean = true, iconKey: String? = null) =
        TextSettingRow(t, v, inh, enabled, visible, iconKey)

    /** A full 35-type snapshot with representative row-values for the non-bool types. */
    private fun snapshot(
        scope: SettingsScope = SettingsScope.Workspace("ws"),
        overrides: Map<TextSettingType, TextSettingRow> = emptyMap(),
    ): TextSettingsSnapshot {
        val choice = TextSettingRowValue.Choice("0", listOf(SettingsItem.Choice("0", "Off")))
        val numeric = TextSettingRowValue.Numeric(16, 1, 60, "16 pt")
        val margins = TextSettingRowValue.Margins(3, 3, 170, 30, 30, 500, "3/3/170 mm")
        val rows = TextSettingType.entries.associateWith { t ->
            overrides[t] ?: when (t) {
                TextSettingType.STRONGS, TextSettingType.PAGE_SCROLL_AMOUNT,
                TextSettingType.SCROLL_HELPER_LINE_STYLE -> row(t, choice)
                TextSettingType.FONTFAMILY -> row(t, TextSettingRowValue.Choice("sans-serif", listOf(SettingsItem.Choice("sans-serif", "Sans serif"))))
                TextSettingType.FONTSIZE, TextSettingType.TOPMARGIN, TextSettingType.LINE_SPACING -> row(t, numeric)
                TextSettingType.MARGINSIZE -> row(t, margins)
                TextSettingType.COLORS -> row(t, TextSettingRowValue.ColorsNav("Colours"))
                TextSettingType.BOOKMARKS_HIDELABELS -> row(t, TextSettingRowValue.HideLabels("2 labels hidden"))
                else -> row(t, bool(true))
            }
        }
        return TextSettingsSnapshot(scope, "Title", "My WS", rows,
            showParentCategory = scope !is SettingsScope.Global,
            showWorkspaceLink = scope is SettingsScope.Window,
            showGlobalLink = scope !is SettingsScope.Global)
    }

    private fun controller(svc: TextDisplaySettingsService, scope: SettingsScope = SettingsScope.Workspace("ws"),
                           onNav: (String) -> Unit = {}) =
        TextDisplaySettingsController(svc, scope, TextDisplaySettingsLabels.forTest(), onNav)

    @Test fun buildsNineCategoriesInXmlOrder() {
        val items = controller(FakeService(snapshot())).state.value.items
        val cats = items.filterIsInstance<SettingsItem.Category>().map { it.key }
        // Category 1 (parent) omitted at WORKSPACE via showWorkspaceLink=false but the header shows
        // for the global link -> parent category IS present at WORKSPACE. Assert the 8 content cats appear in order.
        assertEquals(
            listOf("cat_parent", "cat_font_colors", "cat_text_layout", "cat_strongs_morphology",
                   "cat_footnotes_xrefs", "cat_verses_headings", "cat_page_scrolling",
                   "cat_bookmarks", "cat_reading_memorization"),
            cats,
        )
    }

    @Test fun allThirtyFiveTypeRowsPresentWithCorrectKind() {
        val items = controller(FakeService(snapshot())).state.value.items
        val byKey = items.associateBy { it.key }
        assertTrue(byKey["JUSTIFY"] is SettingsItem.SwitchRow)
        assertTrue(byKey["STRONGS"] is SettingsItem.ListChoiceRow)
        assertTrue(byKey["FONTFAMILY"] is SettingsItem.ListChoiceRow)
        assertTrue(byKey["FONTSIZE"] is SettingsItem.NavigationRow)
        assertTrue(byKey["MARGINSIZE"] is SettingsItem.NavigationRow)
        assertTrue(byKey["COLORS"] is SettingsItem.NavigationRow)
        assertTrue(byKey["BOOKMARKS_HIDELABELS"] is SettingsItem.NavigationRow)
        // every TextSettingType has a row
        assertEquals(35, TextSettingType.entries.count { byKey[it.name] != null })
    }

    @Test fun disabledAndHiddenPassThroughFromSnapshot() {
        val over = mapOf(
            TextSettingType.MORPH to row(TextSettingType.MORPH, bool(false), enabled = false),
            TextSettingType.PAGE_BUTTONS to row(TextSettingType.PAGE_BUTTONS, bool(false), visible = false),
        )
        val items = controller(FakeService(snapshot(overrides = over))).state.value.items.associateBy { it.key }
        assertEquals(false, (items["MORPH"] as SettingsItem.SwitchRow).enabled)
        assertEquals(false, (items["PAGE_BUTTONS"] as SettingsItem.SwitchRow).visible)
    }

    @Test fun windowScopeShowsBothParentLinks_workspaceShowsOnlyGlobal_globalShowsNone() {
        fun linkKeys(scope: SettingsScope) = controller(FakeService(snapshot(scope)), scope).state.value.items
            .filterIsInstance<SettingsItem.NavigationRow>().map { it.key }
            .filter { it == KEY_OPEN_WORKSPACE_SETTINGS || it == KEY_OPEN_GLOBAL_SETTINGS }
        assertEquals(listOf(KEY_OPEN_WORKSPACE_SETTINGS, KEY_OPEN_GLOBAL_SETTINGS), linkKeys(SettingsScope.Window("w", "ws")))
        assertEquals(listOf(KEY_OPEN_GLOBAL_SETTINGS), linkKeys(SettingsScope.Workspace("ws")))
        assertEquals(emptyList(), linkKeys(SettingsScope.Global))
    }

    @Test fun switchWriteRoutesToServiceWithBoolValueAndRebuilds() {
        val svc = FakeService(snapshot())
        val ctl = controller(svc)
        ctl.onSwitch("JUSTIFY", false)
        assertEquals(Triple(SettingsScope.Workspace("ws") as SettingsScope, TextSettingType.JUSTIFY, TextSettingValue.BoolValue(false)), svc.setCalls.single())
    }

    @Test fun listChoiceWriteParsesIntForStrongsAndStringForFontFamily() {
        val svc = FakeService(snapshot())
        val ctl = controller(svc)
        ctl.onListChoice("STRONGS", "2")
        ctl.onListChoice("FONTFAMILY", "serif")
        assertEquals(TextSettingValue.IntValue(2), svc.setCalls[0].third)
        assertEquals(TextSettingValue.StringValue("serif"), svc.setCalls[1].third)
    }

    @Test fun numericAndMarginsWritesRouteCorrectly() {
        val svc = FakeService(snapshot())
        val ctl = controller(svc)
        ctl.onNumericChange("FONTSIZE", 22)
        ctl.onMarginsChange("MARGINSIZE", 5, 6, 200)
        assertEquals(TextSettingValue.IntValue(22), svc.setCalls[0].third)
        assertEquals(TextSettingValue.MarginsValue(5, 6, 200), svc.setCalls[1].third)
    }

    @Test fun hideLabelsWriteRoutesLabelIds() {
        val svc = FakeService(snapshot())
        controller(svc).onHideLabelsChange(listOf("a", "b"))
        assertEquals(Triple(SettingsScope.Workspace("ws") as SettingsScope, TextSettingType.BOOKMARKS_HIDELABELS, TextSettingValue.LabelIdsValue(listOf("a", "b"))), svc.setCalls.single())
    }

    @Test fun revertAndResetRouteToService() {
        val svc = FakeService(snapshot())
        val ctl = controller(svc)
        ctl.onRevert("REDLETTERS"); ctl.onReset()
        assertEquals(SettingsScope.Workspace("ws") to TextSettingType.REDLETTERS, svc.revertCalls.single())
        assertEquals(SettingsScope.Workspace("ws"), svc.resetCalls.single())
    }

    @Test fun parentLinkNavigationForwardsToLambda() {
        val nav = mutableListOf<String>()
        controller(FakeService(snapshot(SettingsScope.Window("w", "ws"))), SettingsScope.Window("w", "ws")) { nav += it }
            .onNavigate(KEY_OPEN_GLOBAL_SETTINGS)
        assertEquals(listOf(KEY_OPEN_GLOBAL_SETTINGS), nav)
    }

    @Test fun iconKeyReachesTheBuiltSettingsItem() {
        val over = mapOf(
            TextSettingType.JUSTIFY to row(TextSettingType.JUSTIFY, bool(true), iconKey = "ic_justify_text_24dp"),
        )
        val items = controller(FakeService(snapshot(overrides = over))).state.value.items
        val switch = items.filterIsInstance<SettingsItem.SwitchRow>().first { it.key == "JUSTIFY" }
        assertEquals("ic_justify_text_24dp", switch.iconKey)
    }

    @Test fun aRowWithoutAnIconKeyStaysIconless() {
        val items = controller(FakeService(snapshot())).state.value.items
        val switch = items.filterIsInstance<SettingsItem.SwitchRow>().first { it.key == "JUSTIFY" }
        assertEquals(null, switch.iconKey)
    }

    /**
     * Task 8's two iconKey tests above both use JUSTIFY (a SwitchRow), so a future deletion of
     * `iconKey = row.iconKey` from the ListChoiceRow branch (STRONGS/PAGE_SCROLL_AMOUNT/
     * SCROLL_HELPER_LINE_STYLE/FONTFAMILY) would pass silently. Covers that branch via FONTFAMILY.
     */
    @Test fun iconKeyReachesAListChoiceRow() {
        val over = mapOf(
            TextSettingType.FONTFAMILY to row(
                TextSettingType.FONTFAMILY,
                TextSettingRowValue.Choice("sans-serif", listOf(SettingsItem.Choice("sans-serif", "Sans serif"))),
                iconKey = "ic_font_family_24dp",
            ),
        )
        val items = controller(FakeService(snapshot(overrides = over))).state.value.items
        val choice = items.filterIsInstance<SettingsItem.ListChoiceRow>().first { it.key == "FONTFAMILY" }
        assertEquals("ic_font_family_24dp", choice.iconKey)
    }

    /**
     * Covers a NavigationRow branch other than FONTSIZE/TOPMARGIN/LINE_SPACING, of which there are
     * three (MARGINSIZE, COLORS, BOOKMARKS_HIDELABELS) -- each builds its own `SettingsItem.NavigationRow(...)`
     * call, so a future edit could drop `iconKey` from just one of them. Covers COLORS here.
     */
    @Test fun iconKeyReachesANavigationRow() {
        val over = mapOf(
            TextSettingType.COLORS to row(TextSettingType.COLORS, TextSettingRowValue.ColorsNav("Colours"), iconKey = "ic_color_settings_24dp"),
        )
        val items = controller(FakeService(snapshot(overrides = over))).state.value.items
        val nav = items.filterIsInstance<SettingsItem.NavigationRow>().first { it.key == "COLORS" }
        assertEquals("ic_color_settings_24dp", nav.iconKey)
    }
}
