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

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.WorkspaceColorChanged
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.defaultWorkspaceColor
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.util.widget.availableFonts
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.sword.backgroundimage.BackgroundImageImporter
import net.bible.service.sword.backgroundimage.backgroundImageFile
import net.bible.service.sword.backgroundimage.isBackgroundImageModule
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedcore.settings.TextSettingsSnapshot
import org.crosswire.jsword.book.Books

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
            TextSettingRow(
                t, valueFor(t, item), InheritedFrom(bundle.inheritedFrom(classic)), item.enabled, item.visible,
                iconKey = iconKeyFor(item),
            )
        }
        return TextSettingsSnapshot(
            scope, titleFor(scope), repo.name, rows,
            showParentCategory = scope !is SettingsScope.Global,
            showWorkspaceLink = scope is SettingsScope.Window,
            showGlobalLink = scope !is SettingsScope.Global,
        )
    }

    /**
     * Classic's per-setting icon (`ItemPreference.icon`, `OptionsMenuItems.kt:259-289`) as a
     * drawable entry name, which `LocalSettingsIcon` resolves back to a `Painter`. Reuses the
     * [item] already read via `getPrefItem(bundle, classic)` for this row rather than reading a
     * second one. The classic inheritance overlay (`CommonUtils.iconWithInheritance`) is
     * deliberately NOT reproduced — the Compose screen already shows inheritance as a separate
     * text badge (A/B batch 3, F4).
     */
    private fun iconKeyFor(item: OptionsMenuItemInterface): String? =
        item.icon?.let { app.resources.getResourceEntryName(it) }

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
        TextSettingType.BOOKMARKS_HIDELABELS ->
            TextSettingRowValue.HideLabels(app.getString(R.string.bookmark_settings_hide_labels_summary))  // count-free summary; parity with static XML summary
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
                ABEventBus.post(WorkspaceColorChanged())
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
                ABEventBus.post(WorkspaceColorChanged())
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

    // ---- Colours (Batch 12d-B T5) — reproduces MainBibleActivity.onActivityResult COLORS_CHANGED ----

    private fun colorTitleFor(scope: SettingsScope) = when (scope) {
        is SettingsScope.Window -> app.getString(R.string.window_color_settings_title)
        is SettingsScope.Workspace, is SettingsScope.Global -> app.getString(R.string.workspace_color_settings_title)
    }

    private fun bgName(initials: String?): String =
        initials?.let { AndBibleAddons.providedBackgroundImages[it]?.name ?: it }
            ?: app.getString(R.string.background_image_none)

    override fun loadColors(scope: SettingsScope): ColorsSnapshot {
        val bundle = bundleFor(scope)
        val c = bundle.actualSettings.colors ?: TextDisplaySettings.default.colors!!
        val wsColor = windowControl.windowRepository.workspaceSettings.workspaceColor ?: defaultWorkspaceColor
        return ColorsSnapshot(
            title = colorTitleFor(scope),
            dayTextColor = c.dayTextColor ?: TextDisplaySettings.black,
            dayBackground = c.dayBackground ?: TextDisplaySettings.white,
            dayNoise = c.dayNoise ?: 0,
            nightTextColor = c.nightTextColor ?: TextDisplaySettings.white,
            nightBackground = c.nightBackground ?: TextDisplaySettings.black,
            nightNoise = c.nightNoise ?: 0,
            workspaceColor = wsColor,
            workspaceColorVisible = scope !is SettingsScope.Window,
            dayBackgroundImageInitials = c.dayBackgroundImage,
            dayBackgroundImageName = bgName(c.dayBackgroundImage),
            dayBackgroundImageOpacity = c.dayBackgroundImageOpacity ?: 100,
            nightBackgroundImageInitials = c.nightBackgroundImage,
            nightBackgroundImageName = bgName(c.nightBackgroundImage),
            nightBackgroundImageOpacity = c.nightBackgroundImageOpacity ?: 100,
            inheritedFrom = InheritedFrom(bundle.inheritedFrom(TextDisplaySettings.Types.COLORS)),
        )
    }

    /** Current merged colours for [scope], with `workspaceColor` carried like [loadColors]. */
    private fun currentColors(scope: SettingsScope): WorkspaceEntities.Colors {
        val bundle = bundleFor(scope)
        val wsColor = windowControl.windowRepository.workspaceSettings.workspaceColor ?: defaultWorkspaceColor
        val c = (bundle.actualSettings.colors ?: TextDisplaySettings.default.colors!!).copy()
        c.workspaceColor = wsColor
        return c
    }

    private fun editColors(scope: SettingsScope, mutate: (WorkspaceEntities.Colors) -> Unit) {
        val c = currentColors(scope)
        mutate(c)
        applyColors(scope, c)
    }

    /** Reproduces `MainBibleActivity.onActivityResult`'s COLORS_CHANGED write-back, per scope. */
    private fun applyColors(scope: SettingsScope, colors: WorkspaceEntities.Colors) {
        when (scope) {
            is SettingsScope.Window -> {
                val window = repo.getWindow(IdType(scope.windowId))!!
                window.pageManager.textDisplaySettings.colors = colors
                window.bibleView?.updateTextDisplaySettings()
                repo.saveIntoDb(false)
            }
            is SettingsScope.Workspace -> {
                repo.textDisplaySettings.colors = colors
                repo.updateWindowTextDisplaySettingsValues(setOf(TextDisplaySettings.Types.COLORS), repo.textDisplaySettings)
                repo.workspaceSettings.workspaceColor = colors.workspaceColor
                // A/B batch 4a F1 fix round 1: this is the live Compose colour picker's per-edit
                // commit (ColorSettingsController.onWorkspaceColorChange -> setWorkspaceColor ->
                // editColors -> here) — the site most likely to have caused the original bug report.
                ABEventBus.post(WorkspaceColorChanged())
                repo.updateAllWindowsTextDisplaySettings()
                repo.saveIntoDb(false)
            }
            is SettingsScope.Global -> {
                val g = CommonUtils.globalTextDisplaySettings
                g.colors = colors
                CommonUtils.globalTextDisplaySettings = g
                repo.propagateGlobalTextDisplaySettingsChange(setOf(TextDisplaySettings.Types.COLORS), g)
                repo.updateAllWindowsTextDisplaySettings()
            }
        }
    }

    override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) = editColors(scope) {
        when (field) {
            ColorField.DAY_TEXT -> it.dayTextColor = argb
            ColorField.DAY_BACKGROUND -> it.dayBackground = argb
            ColorField.NIGHT_TEXT -> it.nightTextColor = argb
            ColorField.NIGHT_BACKGROUND -> it.nightBackground = argb
        }
    }

    override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) = editColors(scope) {
        if (night) it.nightNoise = value else it.dayNoise = value
    }

    override fun setWorkspaceColor(scope: SettingsScope, argb: Int) = editColors(scope) { it.workspaceColor = argb }

    override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) = editColors(scope) {
        if (night) it.nightBackgroundImage = initials else it.dayBackgroundImage = initials
    }

    override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) = editColors(scope) {
        if (night) it.nightBackgroundImageOpacity = opacity else it.dayBackgroundImageOpacity = opacity
    }

    // Whole-Colors reset — reproduces the classic ColorSettingsActivity "Reset" (MainBibleActivity
    // COLORS_CHANGED reset branch): WINDOW -> colours null (inherit); WORKSPACE/GLOBAL -> default.colors.
    override fun resetColors(scope: SettingsScope) {
        when (scope) {
            is SettingsScope.Window -> {
                val window = repo.getWindow(IdType(scope.windowId))!!
                window.pageManager.textDisplaySettings.colors = null
                window.bibleView?.updateTextDisplaySettings()
                repo.saveIntoDb(false)
            }
            is SettingsScope.Workspace -> {
                repo.textDisplaySettings.colors = TextDisplaySettings.default.colors
                repo.workspaceSettings.workspaceColor = defaultWorkspaceColor
                // A/B batch 4a F1 fix round 1: the live Compose colour picker's "Reset" action
                // (ColorSettingsController.onReset -> resetColors -> here).
                ABEventBus.post(WorkspaceColorChanged())
                repo.updateWindowTextDisplaySettingsValues(setOf(TextDisplaySettings.Types.COLORS), repo.textDisplaySettings)
                repo.updateAllWindowsTextDisplaySettings()
                repo.saveIntoDb(false)
            }
            is SettingsScope.Global -> {
                val g = CommonUtils.globalTextDisplaySettings
                g.colors = TextDisplaySettings.default.colors
                CommonUtils.globalTextDisplaySettings = g
                repo.propagateGlobalTextDisplaySettingsChange(setOf(TextDisplaySettings.Types.COLORS), g)
                repo.updateAllWindowsTextDisplaySettings()
            }
        }
    }

    // ---- Background image (Batch 12d-B T6) — register/import/delete ---------------------------

    /** Host-set seam: launches the platform photo picker, returns the picked image's content-URI
     *  string (or null on cancel). Set by [TextDisplaySettingsComposeActivity] via a registered
     *  `PickVisualMedia` launcher bridged to a suspend fun; a Koin singleton can't own an
     *  `ActivityResultLauncher` itself. */
    var imagePicker: (suspend () -> String?)? = null

    override fun loadBackgroundOptions(): List<BackgroundImageOption> =
        AndBibleAddons.providedBackgroundImages.map { (initials, p) ->
            BackgroundImageOption(initials = initials, name = p.name, thumbnailToken = initials)
        }.sortedBy { it.name.lowercase() }

    override suspend fun importBackgroundImage(): BackgroundImageOption? {
        val uriStr = imagePicker?.invoke() ?: return null
        val file = withContext(Dispatchers.IO) { BackgroundImageImporter.copyAndRegister(app, Uri.parse(uriStr)) } ?: return null
        AndBibleAddons.clearCaches()
        // find the freshly-registered module whose file == the written file
        val initials = Books.installed().books.firstOrNull { it.isBackgroundImageModule && it.backgroundImageFile == file }?.initials ?: return null
        val p = AndBibleAddons.providedBackgroundImages[initials] ?: return null
        return BackgroundImageOption(initials, p.name, initials)
    }

    override fun deleteBackgroundImage(initials: String) {
        val book = Books.installed().getBook(initials) ?: return
        book.driver.delete(book)          // BackgroundImageSwordDriver: deletes file + removeBook
        AndBibleAddons.clearCaches()
    }

    // ---- Plan A BOOKMARKS_HIDELABELS bridge (host-only) ----
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
