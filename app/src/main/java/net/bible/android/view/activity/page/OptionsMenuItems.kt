/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.page

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.page.MainBibleActivity.Companion.COLORS_CHANGED
import net.bible.android.view.activity.settings.ColorSettingsActivity
import net.bible.android.view.util.widget.FontFamilyWidget
import net.bible.android.view.util.widget.MarginSizeWidget
import net.bible.android.view.util.widget.FontSizeWidget
import net.bible.android.view.util.widget.LineSpacingWidget
import net.bible.android.view.util.widget.TopMarginWidget
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.crosswire.jsword.book.FeatureType
import javax.inject.Inject

interface OptionsMenuItemInterface {
    var value: Any
    val visible: Boolean
    val enabled: Boolean
    val inherited: Boolean
    val isBoolean: Boolean
    val opensDialog: Boolean
    fun handle()
    fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)? = null, onReset: (() -> Unit)? = null): Boolean = false
    fun setNonSpecific() {}

    val title: String?
    val summary: String?
    val icon: Int?
}

val currentActivity: ActivityBase get() = CurrentActivityHolder.currentActivity!!
val application get() = BibleApplication.application
val windowControl get() = CommonUtils.windowControl
val windowRepository get() = CommonUtils.windowControl.windowRepository


abstract class GeneralPreference(
    protected val onlyBibles: Boolean = false,

    val subMenu: Boolean = false,
    override val enabled: Boolean = true
) : OptionsMenuItemInterface {
    @Inject lateinit var documentControl: DocumentControl
    init {
        CommonUtils.buildActivityComponent().inject(this)
    }

    override val inherited: Boolean = false
    override val visible: Boolean
        get() = if (onlyBibles) documentControl.isBibleBook else true

    override var value: Any = false
    override fun handle() {}
    override val title: String? = null
    override val summary: String? = null
    override val icon: Int? = null
    override val opensDialog get()  = !isBoolean
}

abstract class RealSharedPreferencesPreference(
    private val preferenceName: String,
    private val default: Boolean = false,
    onlyBibles: Boolean = false,
    override val isBoolean: Boolean = true,
    private val isBooleanPreference: Boolean = true,

    // If we are handling non-boolean value
    private val trueValue: String = "true",
    private val falseValue: String = "false",
    private val automaticValue: String = "automatic",
    private val defaultString: String = falseValue,
    subMenu: Boolean = false
) : GeneralPreference(onlyBibles, subMenu) {
    private val preferences = CommonUtils.realSharedPreferences
    override val inherited = false

    override var value: Any
        get() = if (isBooleanPreference) {
            preferences.getBoolean(preferenceName, default)
        } else {
            preferences.getString(preferenceName, defaultString) == trueValue
        }
        set(value) = if (isBooleanPreference) {
            preferences.edit().putBoolean(preferenceName, value == true).apply()
        } else {
            preferences.edit().putString(preferenceName, if (value == true) trueValue else falseValue).apply()
        }

    protected open val automatic: Boolean
        get() = if (isBooleanPreference) {
            false
        } else {
            preferences.getString(preferenceName, defaultString) == automaticValue
        }

    override fun handle() {}
}

open class Preference(val settings: SettingsBundle,
                      var type: TextDisplaySettings.Types,
                      onlyBibles: Boolean = false,
) : GeneralPreference(onlyBibles) {
    protected val valueInt get() = (value as Int)
    protected val valueString get() = (value as String)
    private val actualTextSettings get() = TextDisplaySettings.actual(settings.pageManagerSettings, settings.workspaceSettings)
    private val pageManagerSettings = settings.pageManagerSettings
    private val workspaceSettings = settings.workspaceSettings
    val window = windowRepository.getWindow(settings.windowId)

    protected val default = TextDisplaySettings.default

    override val inherited: Boolean get() = if (window == null) false else pageManagerSettings?.getValue(type) == null
    val pageManager get() = window?.pageManager ?: windowControl.activeWindowPageManager

    override val visible: Boolean
        get() {
            return if (window != null && onlyBibles) pageManager.isBibleShown else true
        }

    override val opensDialog: Boolean = !isBoolean

    override fun setNonSpecific() {
        if(window != null) {
            pageManagerSettings?.setNonSpecific(type)
        } else {
            workspaceSettings.setValue(type, TextDisplaySettings.default.getValue(type))
        }
    }

    override var value
        get() = actualTextSettings.getValue(type)?: TextDisplaySettings.default.getValue(type)!!
        set(value) {
            CommonUtils.displaySettingChanged(type)
            if (window != null) {
                if (workspaceSettings.getValue(type) ?: default.getValue(type) == value)
                    pageManagerSettings!!.setNonSpecific(type)
                else
                    pageManagerSettings!!.setValue(type, value)
            } else {
                workspaceSettings.setValue(type, value)
            }
        }

    override val isBoolean: Boolean get() = value is Boolean

    override fun handle(){
        if(window == null) {
            windowRepository.updateAllWindowsTextDisplaySettings()
        } else {
            window.bibleView?.updateTextDisplaySettings()
        }
    }

    override val title: String?
        get() {
            val id = when(type) {
                TextDisplaySettings.Types.STRONGS -> R.string.prefs_show_strongs_title
                TextDisplaySettings.Types.MORPH -> R.string.prefs_show_morphology_title
                TextDisplaySettings.Types.FOOTNOTES -> R.string.prefs_show_footnotes_title
                TextDisplaySettings.Types.EXPAND_XREFS -> R.string.prefs_expand_footnotes_title
                TextDisplaySettings.Types.XREFS -> R.string.prefs_show_xrefs_title
                TextDisplaySettings.Types.REDLETTERS -> R.string.prefs_red_letter_title
                TextDisplaySettings.Types.SECTIONTITLES -> R.string.prefs_section_title_title
                TextDisplaySettings.Types.VERSENUMBERS -> R.string.prefs_show_verseno_title
                TextDisplaySettings.Types.VERSEPERLINE -> R.string.prefs_verse_per_line_title
                TextDisplaySettings.Types.MYNOTES -> R.string.prefs_show_mynotes_title
                TextDisplaySettings.Types.COLORS -> R.string.prefs_text_colors_menutitle
                TextDisplaySettings.Types.JUSTIFY -> R.string.prefs_justify_title
                TextDisplaySettings.Types.HYPHENATION -> R.string.prefs_hyphenation_title
                TextDisplaySettings.Types.TOPMARGIN -> R.string.prefs_top_margin_title
                TextDisplaySettings.Types.FONTSIZE -> R.string.font_size_title
                TextDisplaySettings.Types.FONTFAMILY -> R.string.pref_font_family_label
                TextDisplaySettings.Types.MARGINSIZE -> R.string.prefs_margin_size_title
                TextDisplaySettings.Types.LINE_SPACING -> R.string.line_spacing_title
                TextDisplaySettings.Types.BOOKMARKS_SHOW -> R.string.prefs_show_bookmarks_title
                TextDisplaySettings.Types.BOOKMARKS_HIDELABELS -> R.string.bookmark_settings_hide_labels_title
            }
            return application.getString(id)
        }

    override val icon: Int?
        get() = when(type) {
            TextDisplaySettings.Types.STRONGS -> if(documentControl.isNewTestament) R.drawable.ic_strongs_greek else R.drawable.ic_strongs_hebrew
            TextDisplaySettings.Types.BOOKMARKS_SHOW -> R.drawable.ic_bookmarks_show_24dp
            TextDisplaySettings.Types.BOOKMARKS_HIDELABELS -> R.drawable.ic_labels_hide_24dp
            TextDisplaySettings.Types.MORPH -> R.drawable.ic_morphology_24dp
            TextDisplaySettings.Types.FOOTNOTES -> R.drawable.ic_footnotes_24dp
            TextDisplaySettings.Types.EXPAND_XREFS -> R.drawable.ic_xrefs_inline_24dp
            TextDisplaySettings.Types.XREFS -> R.drawable.ic_xrefs_24dp
            TextDisplaySettings.Types.SECTIONTITLES -> R.drawable.ic_section_titles_24dp
            TextDisplaySettings.Types.VERSENUMBERS -> R.drawable.ic_chapter_verse_numbers_24dp
            TextDisplaySettings.Types.COLORS -> R.drawable.ic_color_settings_24dp
            TextDisplaySettings.Types.FONTSIZE -> R.drawable.ic_font_size_24dp
            TextDisplaySettings.Types.FONTFAMILY -> R.drawable.ic_font_family_24dp
            TextDisplaySettings.Types.MARGINSIZE -> R.drawable.ic_margin_size_24dp
            TextDisplaySettings.Types.TOPMARGIN -> R.drawable.ic_margin_top_24dp
            TextDisplaySettings.Types.LINE_SPACING -> R.drawable.ic_line_spacing_24dp
            TextDisplaySettings.Types.REDLETTERS -> R.drawable.ic_red_letter_24dp
            TextDisplaySettings.Types.VERSEPERLINE -> R.drawable.ic_one_verse_per_line_24dp
            TextDisplaySettings.Types.JUSTIFY -> R.drawable.ic_justify_text_24dp
            TextDisplaySettings.Types.HYPHENATION -> R.drawable.ic_hyphenation_24dp
            TextDisplaySettings.Types.MYNOTES -> R.drawable.ic_note_regular_24dp
            else -> R.drawable.ic_baseline_star_24
        }
}

class TiltToScrollPreference(val mainBibleActivity: MainBibleActivity):
    GeneralPreference() {
    private val wsBehaviorSettings = windowRepository.workspaceSettings
    override fun handle() { mainBibleActivity.invalidateOptionsMenu() }
    override var value: Any
        get() = wsBehaviorSettings.enableTiltToScroll
        set(value) {
            wsBehaviorSettings.enableTiltToScroll = value == true
        }
    override val visible: Boolean get() = super.visible && PageTiltScrollControl.isTiltSensingPossible
    override val isBoolean = true
}

class CommandPreference(
    private val launch: ((activity: Activity, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?) -> Unit)? = null,
    private val handle: (() -> Unit)? = null,
    override val enabled: Boolean = true,
    override var value: Any = Object(),
    override val visible: Boolean = true,
    override val inherited: Boolean = false,
    override val opensDialog: Boolean = false,
    override val title: String? = null,
    override val summary: String? = null,
) : OptionsMenuItemInterface {
    override fun handle() {
        handle?.invoke()
    }
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        launch?.invoke(activity, onChanged, onReset)
        return true
    }

    override val icon: Int? = null
    override val isBoolean get() = handle != null && value is Boolean
}

open class SubMenuPreference(onlyBibles: Boolean = false, enabled: Boolean = true, override val visible: Boolean = true) :
    GeneralPreference(onlyBibles = onlyBibles, subMenu = true, enabled = enabled)
{
    override val isBoolean: Boolean = false
}

class NightModePreference(val mainBibleActivity: MainBibleActivity) : RealSharedPreferencesPreference("night_mode_pref", false) {
    override fun handle() { mainBibleActivity.refreshIfNightModeChange() }
    override var value: Any
        get() = ScreenSettings.nightMode
        set(value) {
            if(enabled) {
                super.value = value
            }
        }
    override val enabled: Boolean get() = ScreenSettings.manualMode
}

class MyNotesPreference (settings: SettingsBundle) : Preference(settings, TextDisplaySettings.Types.MYNOTES) {
    override val visible: Boolean get() = !pageManager.isMyNotesShown
}

class RedLettersPreference (settings: SettingsBundle) : Preference(settings, TextDisplaySettings.Types.REDLETTERS) {
    override val enabled: Boolean get() = pageManager.isBibleShown && pageManager.currentPage.currentDocument?.hasFeature(FeatureType.WORDS_OF_CHRIST) == true
}

class ExpandXrefsPreference (settings: SettingsBundle) : Preference(settings, TextDisplaySettings.Types.EXPAND_XREFS) {
    override val enabled: Boolean get() = Preference(settings, TextDisplaySettings.Types.XREFS).value == true
}

class StrongsPreference (settings: SettingsBundle) : Preference(settings, TextDisplaySettings.Types.STRONGS) {
    override val enabled: Boolean get() = pageManager.hasStrongs
    override var value get() = if (enabled) super.value else 0
        set(value) {
            super.value = value
        }

    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        val items = activity.resources.getStringArray(R.array.strongsModeEntries)
        var newChoice = value
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.strongs_mode_title)
            .setSingleChoiceItems(items, value as Int) { _, v ->
                newChoice = v
            }
            .setPositiveButton(R.string.okay) { _,_ ->
                value = newChoice
                onChanged?.invoke(newChoice)
            }
            .setNeutralButton(R.string.reset_generic) { _, _ -> setNonSpecific(); onReset?.invoke() }
            .setNegativeButton(R.string.cancel, null)
        dialog.show()
        return true
    }
}

class MorphologyPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.MORPH) {
    override val enabled: Boolean
        get() {
            val itm = StrongsPreference(settings)
            return itm.enabled && itm.value as Int > 0
        }

    override var value: Any
        get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }
}

class FontSizePreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.FONTSIZE) {
    override val title: String get() = application.getString(R.string.font_size_title_pt, valueInt)
    override val visible = true
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        FontSizeWidget.dialog(activity, settings.actualSettings.fontFamily!!, value as Int, {
            setNonSpecific()
            onReset?.invoke()
        }) {
            value = it
            onChanged?.invoke(it)
        }
        return true
    }
}

class TopMarginPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.TOPMARGIN) {
    override val title: String get() = application.getString(R.string.prefs_top_margin_title_mm, valueInt)
    override val visible = pageManager.isBibleShown
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        TopMarginWidget.dialog(activity, value as Int, {
            setNonSpecific()
            onReset?.invoke()
        }) {
            value = it
            onChanged?.invoke(it)
        }
        return true
    }
}

class FontFamilyPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.FONTFAMILY) {
    override val title: String get() = application.getString(R.string.pref_font_family_label_name, valueString)
    override val visible = true
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        FontFamilyWidget.dialog(activity, settings.actualSettings.fontSize!!, value as String, {
            setNonSpecific()
            onReset?.invoke()
        }) {
            value = it
            onChanged?.invoke(it)
        }
        return true
    }
}

class LineSpacingPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.LINE_SPACING) {
    override val title: String get() = application.getString(R.string.prefs_line_spacing_pt_title, valueInt.toFloat() / 10)
    override val visible = true
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        LineSpacingWidget.dialog(activity, valueInt, {
            setNonSpecific()
            onReset?.invoke()
        }) {
            value = it
            onChanged?.invoke(it)
        }
        return true
    }
}

class ColorPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.COLORS) {
    override val visible = true
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        val intent = Intent(activity, ColorSettingsActivity::class.java)
        intent.putExtra("settingsBundle", settings.toJson())
        activity.startActivityForResult(intent, COLORS_CHANGED)
        return true
    }
}

class HideLabelsPreference(settings: SettingsBundle, type: TextDisplaySettings.Types): Preference(settings, type) {
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        val intent = Intent(activity, ManageLabels::class.java)
        val originalValues = value as List<IdType>

        intent.putExtra("data", ManageLabels.ManageLabelsData(
            mode = ManageLabels.Mode.HIDELABELS,
            selectedLabels = originalValues.toMutableSet(),
            isWindow = settings.windowId != null
        ).applyFrom(windowRepository.workspaceSettings).toJSON())
        activity.lifecycleScope.launch (Dispatchers.Main) {
            val result = activity.awaitIntent(intent)
            if(result.resultCode == Activity.RESULT_OK) {
                val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                if(resultData.reset) {
                    setNonSpecific()
                    onReset?.invoke()
                } else {
                    value = resultData.selectedLabels.toList()
                    windowRepository.workspaceSettings.updateFrom(resultData)
                    onChanged?.invoke(value)
                }
            }
        }
        return true
    }
}

class AutoAssignPreference(val workspaceSettings: WorkspaceEntities.WorkspaceSettings): GeneralPreference() {
    override val isBoolean = false
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        val intent = Intent(activity, ManageLabels::class.java)

        intent.putExtra("data",
            ManageLabels.ManageLabelsData(mode = ManageLabels.Mode.WORKSPACE).applyFrom(workspaceSettings).toJSON()
        )

        activity.lifecycleScope.launch (Dispatchers.Main) {
            val result = activity.awaitIntent(intent)
            if(result.resultCode == Activity.RESULT_OK) {
                val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                if (resultData.reset) {
                    workspaceSettings.autoAssignLabels = mutableSetOf()
                    workspaceSettings.autoAssignPrimaryLabel = null
                    onReset?.invoke()
                } else {
                    workspaceSettings.updateFrom(resultData)
                    onChanged?.invoke(1)
                }
            }
        }
        return true
    }
}

class MarginSizePreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.MARGINSIZE) {
    private val leftVal get() = (value as WorkspaceEntities.MarginSize).marginLeft!!
    private val rightVal get() = (value  as WorkspaceEntities.MarginSize).marginRight!!
    // I added this field later (migration 15..16) so to prevent crashes because of null values, need to have this.
    private val maxWidth get() = (value  as WorkspaceEntities.MarginSize).maxWidth ?: defaultVal.maxWidth!!
    private val defaultVal = TextDisplaySettings.default.marginSize!!
    override val title: String get() = application.getString(R.string.prefs_margin_size_mm_title, leftVal, rightVal, maxWidth)
    override val summary: String? get() = application.getString(R.string.prefs_margin_size_summary) + " " + application.getString(R.string.prefs_margin_size_summary_2)
    override val visible = true
    override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        MarginSizeWidget.dialog(activity, value as WorkspaceEntities.MarginSize,
            {
                setNonSpecific()
                onReset?.invoke()
            },
            {
                value = it
                onChanged?.invoke(it)
            })

        return true
    }
}

class SplitModePreference(val mainBibleActivity: MainBibleActivity) :
    GeneralPreference() {
    private val wsBehaviorSettings = windowRepository.workspaceSettings
    override fun handle() {
        windowControl.windowSizesChanged()
        ABEventBus.post(MainBibleActivity.ConfigurationChanged(mainBibleActivity.resources.configuration))
    }

    override var value: Any
        get() = wsBehaviorSettings.enableReverseSplitMode
        set(value) {
            wsBehaviorSettings.enableReverseSplitMode = value == true
        }

    override val isBoolean = true
    override val visible: Boolean get() = super.visible && windowControl.isMultiWindow
}

class WindowPinningPreference :
    GeneralPreference() {
    private val wsBehaviorSettings = windowRepository.workspaceSettings
    override var value: Any
        get() = !wsBehaviorSettings.autoPin
        set(value) {
            wsBehaviorSettings.autoPin = value == false
        }

    override fun handle() {
        windowControl.autoPinChanged()
    }

    override val isBoolean = true
}

