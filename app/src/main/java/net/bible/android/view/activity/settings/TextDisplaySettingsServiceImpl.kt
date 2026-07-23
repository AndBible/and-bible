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

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.defaultWorkspaceColor
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.util.widget.availableFonts
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedcore.settings.TextSettingsSnapshot

/**
 * Android impl of [TextDisplaySettingsService] — the ONLY place classic
 * `WorkspaceEntities`/`SettingsBundle`/`getPrefItem` are read or written from the Compose side
 * (approach B). Drives the EXISTING classic machinery (`getPrefItem`, `OptionsMenuItemInterface`,
 * `WindowRepository`, `CommonUtils.globalTextDisplaySettings`) instead of duplicating the
 * sparse-override/inheritance math, which lives in `Preference.value` / `SettingsBundle.actual()`
 * (`TextDisplaySettings.kt`, `OptionsMenuItems.kt`, `WorkspaceEntities.kt`).
 *
 * Parity target: [TextDisplaySettingsActivity.commitDirtyToInMemoryState] (per-level in-memory
 * commit + propagation) and `MainBibleActivity.workspaceSettingsChanged` (same per-level branches
 * fired from the classic activity-result path).
 */
class TextDisplaySettingsServiceImpl : TextDisplaySettingsService {
    private val app get() = BibleApplication.application
    private val windowControl get() = CommonUtils.windowControl
    private val repo get() = windowControl.windowRepository

    private fun TextSettingType.toClassic() = TextDisplaySettings.Types.valueOf(name)
    private fun InheritedFrom(from: net.bible.android.database.InheritedFrom) = when (from) {
        net.bible.android.database.InheritedFrom.NONE -> InheritedFrom.NONE
        net.bible.android.database.InheritedFrom.WORKSPACE -> InheritedFrom.WORKSPACE
        net.bible.android.database.InheritedFrom.GLOBAL -> InheritedFrom.GLOBAL
    }

    private fun bundleFor(scope: SettingsScope): SettingsBundle = when (scope) {
        is SettingsScope.Global -> SettingsBundle(
            level = SettingsLevel.GLOBAL,
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        is SettingsScope.Workspace -> SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceId = repo.id, workspaceName = repo.name,
            workspaceSettings = repo.textDisplaySettings.apply {  // LIVE object; carry workspaceColor like getItemOptions
                colors?.workspaceColor = repo.workspaceSettings.workspaceColor
            },
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        is SettingsScope.Window -> {
            val window = repo.getWindow(IdType(scope.windowId))!!
            SettingsBundle(
                level = SettingsLevel.WINDOW,
                windowId = window.id,
                pageManagerSettings = window.pageManager.textDisplaySettings,  // LIVE object
                workspaceId = repo.id, workspaceName = repo.name,
                workspaceSettings = repo.textDisplaySettings,
                globalSettings = CommonUtils.globalTextDisplaySettings,
            )
        }
    }

    override fun loadText(scope: SettingsScope): TextSettingsSnapshot {
        val bundle = bundleFor(scope)
        val rows = TextSettingType.entries.associateWith { t ->
            val classic = t.toClassic()
            val item = getPrefItem(bundle, classic)
            TextSettingRow(t, valueFor(t, item), InheritedFrom(bundle.inheritedFrom(classic)), item.enabled, item.visible)
        }
        return TextSettingsSnapshot(
            scope, titleFor(scope), repo.name, rows,
            showParentCategory = scope !is SettingsScope.Global,
            showWorkspaceLink = scope is SettingsScope.Window,
            showGlobalLink = scope !is SettingsScope.Global,
        )
    }

    private fun valueFor(t: TextSettingType, item: OptionsMenuItemInterface): TextSettingRowValue = when (t) {
        TextSettingType.STRONGS -> choice((item.value as Int).toString(), strongsEntries())
        TextSettingType.PAGE_SCROLL_AMOUNT -> choice((item.value as Int).toString(), pageScrollEntries())
        TextSettingType.SCROLL_HELPER_LINE_STYLE -> choice((item.value as Int).toString(), scrollHelperStyleEntries())
        TextSettingType.FONTFAMILY -> choice(item.value as String, fontFamilyEntries())
        TextSettingType.FONTSIZE -> TextSettingRowValue.Numeric(item.value as Int, 1, 60, app.getString(R.string.font_size_pt, item.value as Int))
        TextSettingType.TOPMARGIN -> TextSettingRowValue.Numeric(item.value as Int, 0, 60, app.getString(R.string.value_mm, item.value as Int))
        TextSettingType.LINE_SPACING -> TextSettingRowValue.Numeric(item.value as Int, 10, 30, app.getString(R.string.prefs_line_spacing_pt, (item.value as Int).toFloat() / 10))
        TextSettingType.MARGINSIZE -> {
            val m = item.value as WorkspaceEntities.MarginSize
            val def = TextDisplaySettings.default.marginSize!!
            val l = m.marginLeft ?: def.marginLeft!!; val r = m.marginRight ?: def.marginRight!!; val mw = m.maxWidth ?: def.maxWidth!!
            TextSettingRowValue.Margins(l, r, mw, 30, 30, 500, app.getString(R.string.prefs_margin_size_mm_title, l, r, mw))
        }
        TextSettingType.COLORS -> TextSettingRowValue.ColorsNav(app.getString(R.string.prefs_text_colors_summary))
        TextSettingType.BOOKMARKS_HIDELABELS -> {
            @Suppress("UNCHECKED_CAST") val ids = item.value as? List<IdType> ?: emptyList()
            TextSettingRowValue.HideLabels(app.getString(R.string.bookmark_settings_hide_labels_summary))  // count-free summary; parity with static XML summary
        }
        else -> TextSettingRowValue.Bool(item.value as Boolean)
    }

    private fun choice(sel: String, entries: List<SettingsItem.Choice>) = TextSettingRowValue.Choice(sel, entries)
    private fun strongsEntries() = app.resources.getStringArray(R.array.strongsModeEntries)
        .mapIndexed { i, l -> SettingsItem.Choice(i.toString(), l) }
    private fun pageScrollEntries(): List<SettingsItem.Choice> {
        val labels = app.resources.getStringArray(R.array.pageScrollAmountEntries)
        val values = intArrayOf(25, 33, 50, 66, 75, 100)   // verbatim from PageScrollAmountPreference.scrollValues
        return values.mapIndexed { i, v -> SettingsItem.Choice(v.toString(), labels.getOrElse(i) { v.toString() }) }
    }
    private fun scrollHelperStyleEntries() = app.resources.getStringArray(R.array.scrollHelperLineStyleEntries)
        .mapIndexed { i, l -> SettingsItem.Choice(i.toString(), l) }
    private fun fontFamilyEntries() = availableFonts.map { SettingsItem.Choice(it.realFontFamily, it.name) }

    private fun titleFor(scope: SettingsScope) = when (scope) {
        is SettingsScope.Global -> app.getString(R.string.global_text_display_settings_title)
        is SettingsScope.Workspace -> app.getString(R.string.workspace_text_display_settings_title, repo.name)
        is SettingsScope.Window -> app.getString(R.string.window_text_display_settings_title, windowControl.windowPosition(IdType(scope.windowId)) + 1)
    }

    override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) {
        val bundle = bundleFor(scope)
        val classic = type.toClassic()
        getPrefItem(bundle, classic).value = value.toClassic()   // classic sparse-override + MRU (CommonUtils.displaySettingChanged)
        applyAndPersist(scope, bundle, setOf(classic))
    }

    private fun TextSettingValue.toClassic(): Any = when (this) {
        is TextSettingValue.BoolValue -> value
        is TextSettingValue.IntValue -> value
        is TextSettingValue.StringValue -> value
        is TextSettingValue.MarginsValue -> WorkspaceEntities.MarginSize(left, right, maxWidth)
        is TextSettingValue.LabelIdsValue -> ids.map { IdType(it) }
    }

    override fun revert(scope: SettingsScope, type: TextSettingType) {
        val bundle = bundleFor(scope)
        val classic = type.toClassic()
        getPrefItem(bundle, classic).setNonSpecific()
        applyAndPersist(scope, bundle, setOf(classic))
    }

    override fun reset(scope: SettingsScope) {
        val all = TextDisplaySettings.Types.values().toSet()
        when (scope) {
            is SettingsScope.Global -> {
                CommonUtils.globalTextDisplaySettings = TextDisplaySettings()
                repo.propagateGlobalTextDisplaySettingsChange(all, CommonUtils.globalTextDisplaySettings)
                repo.updateAllWindowsTextDisplaySettings()
            }
            is SettingsScope.Workspace -> {
                repo.textDisplaySettings = TextDisplaySettings()
                repo.workspaceSettings.workspaceColor = defaultWorkspaceColor
                repo.updateWindowTextDisplaySettingsValues(all, repo.textDisplaySettings)
                repo.updateAllWindowsTextDisplaySettings()
                repo.saveIntoDb(false)
            }
            is SettingsScope.Window -> {
                val window = repo.getWindow(IdType(scope.windowId))!!
                window.pageManager.textDisplaySettings = TextDisplaySettings()
                window.bibleView?.updateTextDisplaySettings()
                repo.saveIntoDb(false)
            }
        }
    }

    /** Mirrors TextDisplaySettingsActivity.commitDirtyToInMemoryState + workspaceSettingsChanged, per edit. */
    private fun applyAndPersist(scope: SettingsScope, bundle: SettingsBundle, dirty: Set<TextDisplaySettings.Types>) {
        when (scope) {
            is SettingsScope.Global -> {
                CommonUtils.globalTextDisplaySettings = bundle.globalSettings
                repo.propagateGlobalTextDisplaySettingsChange(dirty, bundle.globalSettings)
                repo.updateAllWindowsTextDisplaySettings()
            }
            is SettingsScope.Workspace -> {
                repo.workspaceSettings.workspaceColor = repo.textDisplaySettings.colors?.workspaceColor ?: defaultWorkspaceColor
                repo.updateWindowTextDisplaySettingsValues(dirty, repo.textDisplaySettings)
                repo.updateAllWindowsTextDisplaySettings()
                repo.saveIntoDb(false)
            }
            is SettingsScope.Window -> {
                val window = repo.getWindow(IdType(scope.windowId))!!
                window.bibleView?.updateTextDisplaySettings()
                repo.saveIntoDb(false)
            }
        }
    }

    // ---- Plan A COLORS bridge (host-only; Plan B replaces with the Compose colors destination) ----
    fun colorsBundleJson(scope: SettingsScope): String = bundleFor(scope).toJson()
    fun applyColorsResult(scope: SettingsScope, colorsJson: String?, reset: Boolean) {
        val bundle = bundleFor(scope)
        val item = getPrefItem(bundle, TextDisplaySettings.Types.COLORS)
        if (reset) item.setNonSpecific() else if (colorsJson != null) item.value = WorkspaceEntities.Colors.fromJson(colorsJson)
        applyAndPersist(scope, bundle, setOf(TextDisplaySettings.Types.COLORS))
    }

    // ---- Plan A BOOKMARKS_HIDELABELS bridge (host-only; mirrors the COLORS bridge above) ----
    // The current-scope [TextSettingRowValue.HideLabels] DTO carries only the display summary (Task
    // 4/5 didn't need the raw ids for rendering), so the host needs a separate read of the classic
    // value to seed `ManageLabels.ManageLabelsData.selectedLabels` when opening the label picker
    // (reproducing classic `HideLabelsPreference.openDialog`'s initial selection).
    fun currentHideLabelsIds(scope: SettingsScope): List<IdType> {
        val bundle = bundleFor(scope)
        @Suppress("UNCHECKED_CAST")
        return getPrefItem(bundle, TextDisplaySettings.Types.BOOKMARKS_HIDELABELS).value as? List<IdType> ?: emptyList()
    }
}
