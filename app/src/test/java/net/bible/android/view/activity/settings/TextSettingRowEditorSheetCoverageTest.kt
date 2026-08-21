/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.settings

import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedcore.settings.TextSettingsSnapshot
import net.bible.sharedcore.settings.textSettingEditorPageFor
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * T6: `textSettingEditorPageFor` (`:sharedCore`) decides which [TextSettingType]s resolve to a
 * [SettingsEditorPage.Row]; `TextDisplaySettingsScreen.kt`'s private `SHEET_EDITED_TEXT_SETTING_KEYS`
 * (`:sharedUi`) decides which of THOSE the screen actually opens a sheet page for (the rest —
 * FONTFAMILY/STRONGS/PAGE_SCROLL_AMOUNT/SCROLL_HELPER_LINE_STYLE — render as native
 * `SettingsItem.ListChoiceRow`s and never reach `handleNavigate` at all). Nothing in the type system
 * ties the two tables together, so a type added to one and not the other would silently open NOTHING
 * when tapped: no crash, no failing test, just a dead row — exactly the class of bug this guards.
 *
 * `:sharedCore` cannot see `:sharedUi`'s constant (dependency direction), and the constant is
 * `private` even within `:sharedUi`, so it can't be referenced directly even from an `:app` test —
 * this is a SOURCE guard (same idiom as `SettingsBadgeLayoutDriftTest`/`SearchSheetStructureGuardTest`),
 * parsing the literal `TextSettingType.X.name` entries out of the real source file, rather than a
 * render or reflection test.
 *
 * The "expected" side is computed from the REAL [TextDisplaySettingsController] (via a fake
 * [TextDisplaySettingsService]) rather than hand-listing FONTSIZE/TOPMARGIN/LINE_SPACING/MARGINSIZE
 * again here: that would just be a second copy of the same duplication risk this test exists to
 * catch. Deriving it from the controller's actual [SettingsItem.NavigationRow] output means a future
 * type that becomes both a `Row` page AND a `NavigationRow` is picked up automatically, with no
 * edit to this test required.
 */
class TextSettingRowEditorSheetCoverageTest {

    private class FakeService(private val snap: TextSettingsSnapshot) : TextDisplaySettingsService {
        override fun loadText(scope: SettingsScope) = snap
        override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) {}
        override fun revert(scope: SettingsScope, type: TextSettingType) {}
        override fun reset(scope: SettingsScope) {}
        override fun loadColors(scope: SettingsScope) = ColorsSnapshot(
            title = "", dayTextColor = 0, dayBackground = 0, dayNoise = 0,
            nightTextColor = 0, nightBackground = 0, nightNoise = 0,
            workspaceColor = 0, workspaceColorVisible = false,
            dayBackgroundImageInitials = null, dayBackgroundImageName = "", dayBackgroundImageOpacity = 0,
            nightBackgroundImageInitials = null, nightBackgroundImageName = "", nightBackgroundImageOpacity = 0,
            inheritedFrom = InheritedFrom.NONE,
        )
        override fun loadBackgroundOptions(): List<BackgroundImageOption> = emptyList()
        override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) {}
        override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) {}
        override fun setWorkspaceColor(scope: SettingsScope, argb: Int) {}
        override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) {}
        override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) {}
        override fun resetColors(scope: SettingsScope) {}
        override suspend fun importBackgroundImage(): BackgroundImageOption? = null
        override fun deleteBackgroundImage(initials: String) {}
    }

    private fun row(t: TextSettingType, v: TextSettingRowValue) =
        TextSettingRow(t, v, InheritedFrom.NONE, enabled = true, visible = true)

    /** Minimal 35-type snapshot — just enough of a fixture per type for `buildRow`'s unsafe casts
     *  not to throw; the actual values are irrelevant to this test, only the resulting ROW KIND
     *  ([SettingsItem.NavigationRow] vs [SettingsItem.ListChoiceRow] vs others) is. */
    private fun snapshot(): TextSettingsSnapshot {
        val choice = TextSettingRowValue.Choice("0", listOf(SettingsItem.Choice("0", "Off")))
        val numeric = TextSettingRowValue.Numeric(16, 1, 60, "16 pt")
        val margins = TextSettingRowValue.Margins(3, 3, 170, 30, 30, 500, "3/3/170 mm")
        val rows = TextSettingType.entries.associateWith { t ->
            when (t) {
                TextSettingType.STRONGS, TextSettingType.PAGE_SCROLL_AMOUNT,
                TextSettingType.SCROLL_HELPER_LINE_STYLE, TextSettingType.FONTFAMILY -> row(t, choice)
                TextSettingType.FONTSIZE, TextSettingType.TOPMARGIN, TextSettingType.LINE_SPACING -> row(t, numeric)
                TextSettingType.MARGINSIZE -> row(t, margins)
                TextSettingType.COLORS -> row(t, TextSettingRowValue.ColorsNav(""))
                TextSettingType.BOOKMARKS_HIDELABELS -> row(t, TextSettingRowValue.HideLabels(""))
                else -> row(t, TextSettingRowValue.Bool(true))
            }
        }
        return TextSettingsSnapshot(
            SettingsScope.Workspace("ws"), "Text options", "My workspace", rows,
            showParentCategory = true, showWorkspaceLink = false, showGlobalLink = true,
        )
    }

    private fun items(): List<SettingsItem> = TextDisplaySettingsController(
        service = FakeService(snapshot()),
        settingsScope = SettingsScope.Workspace("ws"),
        labels = TextDisplaySettingsLabels.forTest(),
        onNavigateCallback = {},
    ).state.value.items

    /** Every [TextSettingType] that (a) renders as a [SettingsItem.NavigationRow] — so it reaches
     *  `handleNavigate`/`onNavigate` at all — AND (b) resolves via [textSettingEditorPageFor] to a
     *  [SettingsEditorPage.Row]. This is exactly the set `SHEET_EDITED_TEXT_SETTING_KEYS` must cover,
     *  or the row opens nothing when tapped. */
    private fun expectedSheetEditedKeys(): Set<String> =
        items()
            .filterIsInstance<SettingsItem.NavigationRow>()
            .map { it.key }
            .filter { textSettingEditorPageFor(it) is SettingsEditorPage.Row }
            .toSet()

    private val screenSource = java.io.File(
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/TextDisplaySettingsScreen.kt"
    ).readText()

    private fun actualSheetEditedKeys(): Set<String> {
        val block = Regex(
            "SHEET_EDITED_TEXT_SETTING_KEYS = setOf\\(([^)]*)\\)",
            RegexOption.DOT_MATCHES_ALL,
        ).find(screenSource)?.groupValues?.get(1)
            ?: error("SHEET_EDITED_TEXT_SETTING_KEYS = setOf(...) not found in TextDisplaySettingsScreen.kt")
        return Regex("TextSettingType\\.([A-Z_]+)\\.name").findAll(block).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun sheetEditedKeysExactlyCoverTheNavigationRowTypesThatResolveToARowPage() {
        assertThat(
            "SHEET_EDITED_TEXT_SETTING_KEYS (TextDisplaySettingsScreen.kt) must exactly match the " +
                "NavigationRow types textSettingEditorPageFor resolves to a Row page, or a row opens " +
                "nothing when tapped",
            actualSheetEditedKeys(),
            equalTo(expectedSheetEditedKeys()),
        )
    }

    @Test
    fun sanityTheParsedSetIsNonEmpty() {
        // Guards the guard: if the regex above ever stops matching (e.g. the constant is renamed or
        // reformatted), actualSheetEditedKeys() would silently return an empty set and the main test
        // would report a confusing "expected 4, got 0" rather than the real problem.
        assertThat(actualSheetEditedKeys().isNotEmpty(), equalTo(true))
    }
}
