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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.strings.keyed

import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.InheritedFrom
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedlogic.settings.RecentDisplaySettings
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedlogic.settings.TextDisplayString
import org.crosswire.jsword.book.FeatureType

/**
 * A font choice for a CSS generic family, labelled as Android's font picker labels it (`FontDefinition.name`: dashes
 * become spaces, first letter capitalised), so both platforms show "Sans serif".
 */
@kotlin.native.HiddenFromObjC
fun genericFontChoice(family: String): SettingsItem.Choice =
    SettingsItem.Choice(family, family.replace("-", " ").replaceFirstChar { it.uppercase() })

/**
 * The real [TextDisplayPrefItems] for platforms without Android's `OptionsMenuItems` (iOS): the same value, inheritance,
 * enabled/visible and title rules as the Android items (`Preference` and its subclasses behind `getPrefItem`), over the
 * common window repository, document control and app settings, with strings from [strings] by Android's resource keys.
 *
 * What an item answers, by setting type (as on Android):
 * - **value**: the actual (inherited) value, falling back to [TextDisplaySettings.default]; a write goes to the bundle's
 *   level, or clears the override when it equals what the parent level gives, and records the type as recently used
 *   ([recent]). [PrefItemHandle.setNonSpecific] clears the override (global resets to the default).
 * - **enabled / value-when-disabled**: Strong's needs a window with Strong's numbers (value 0 otherwise); morphology and
 *   non-Strong's italics follow it (false otherwise); inline footnotes need footnotes; expanded cross references need
 *   cross references; red letters need a Bible with words of Christ.
 * - **visible**: my notes are hidden on the notes page, the top margin shows on Bible pages only, the scroll helper
 *   lines and style and the page buttons show in e-ink mode only.
 * - **title**: the static title key ([KeyedSettingLabels.titleKey]); font size, top margin, font family, line spacing and
 *   margin size show their current value like Android's.
 * - **iconKey**: Android's drawable entry names (the platform maps them to its own icons).
 * - **handle**: pushes the change to the windows (all of them at workspace/global level, the one window at window level).
 *
 * [window] lookups are lazy: a bundle without a window id needs neither the window repository nor the active page.
 * The picker's content source, the provided background images and the installed fonts are passed in, as those live
 * below this module.
 */
@kotlin.native.HiddenFromObjC
class SharedTextDisplayPrefItems(
    private val strings: () -> StringTable,
    private val contentSource: () -> ContentSource,
    private val windowControl: () -> WindowControl,
    private val documentControl: () -> DocumentControl,
    private val settings: () -> AppSettings,
    private val backgroundImages: () -> Map<String, String> = { emptyMap() },
    override val fonts: List<SettingsItem.Choice> = listOf(
        // The CSS generic families every WebView resolves; a platform with a font scan passes its own.
        genericFontChoice("sans-serif"),
        genericFontChoice("serif"),
        genericFontChoice("monospace"),
    ),
) : TextDisplayPrefItems {
    /** The recently used types, shared with the menus that offer them as shortcuts. */
    val recent: RecentDisplaySettings by lazy { RecentDisplaySettings(settings()) }

    override fun item(bundle: SettingsBundle, type: Types): PrefItemHandle = Item(bundle, type)

    override fun string(key: TextDisplayString, vararg args: Any): String =
        strings().format(textDisplayStringKey(key), args)

    override fun array(key: TextDisplayArray): List<String> = strings().array(textDisplayArrayName(key))

    override fun displaySettingChanged(type: Types) = recent.changed(type)

    override fun contentSource(): ContentSource = contentSource.invoke()

    override fun backgroundImageNames(): Map<String, String> = backgroundImages()

    private inner class Item(private val bundle: SettingsBundle, private val type: Types) : PrefItemHandle {
        private val pageManagerSettings = bundle.pageManagerSettings
        private val workspaceSettings = bundle.workspaceSettings
        private val globalSettings = bundle.globalSettings

        private val window: Window? get() = bundle.windowId?.let { windowControl().windowRepository.getWindow(it) }
        private val pageManager: CurrentPageManager get() = window?.pageManager ?: windowControl().activeWindowPageManager

        private fun plain(of: Types) = Item(bundle, of)

        private val rawValue: Any
            get() = TextDisplaySettings.actual(pageManagerSettings, workspaceSettings, globalSettings).getValue(type)
                ?: TextDisplaySettings.default.getValue(type)!!

        private val valueInt: Int get() = rawValue as? Int ?: TextDisplaySettings.default.getValue(type) as Int
        private val valueString: String get() = rawValue as? String ?: TextDisplaySettings.default.getValue(type) as String

        override var value: Any
            get() = when (type) {
                Types.STRONGS -> if (enabled) rawValue else 0
                Types.MORPH, Types.NON_STRONGS_WORD_ITALIC, Types.FOOTNOTES_INLINE -> if (enabled) rawValue else false
                else -> rawValue
            }
            set(newValue) {
                recent.changed(type)
                val default = TextDisplaySettings.default
                when (bundle.level) {
                    SettingsLevel.WINDOW -> {
                        val parentValue = workspaceSettings.getValue(type) ?: globalSettings.getValue(type) ?: default.getValue(type)
                        if (parentValue == newValue) pageManagerSettings!!.setNonSpecific(type) else pageManagerSettings!!.setValue(type, newValue)
                    }
                    SettingsLevel.WORKSPACE -> {
                        val parentValue = globalSettings.getValue(type) ?: default.getValue(type)
                        if (parentValue == newValue) workspaceSettings.setNonSpecific(type) else workspaceSettings.setValue(type, newValue)
                    }
                    SettingsLevel.GLOBAL -> globalSettings.setValue(type, newValue)
                }
            }

        override val enabled: Boolean
            get() = when (type) {
                Types.REDLETTERS -> pageManager.isBibleShown &&
                    pageManager.currentPage.currentDocument?.hasFeature(FeatureType.WORDS_OF_CHRIST) == true
                Types.EXPAND_XREFS -> plain(Types.XREFS).rawValue == true
                Types.STRONGS -> hasStrongs
                Types.MORPH, Types.NON_STRONGS_WORD_ITALIC -> window == null || hasStrongs
                Types.FOOTNOTES_INLINE -> plain(Types.FOOTNOTES).rawValue == true
                else -> true
            }

        private val hasStrongs: Boolean get() = window == null || pageManager.hasStrongs

        override val visible: Boolean
            get() = when (type) {
                Types.MYNOTES -> !pageManager.isMyNotesShown
                Types.TOPMARGIN -> pageManager.isBibleShown
                Types.SCROLL_HELPER_LINES, Types.SCROLL_HELPER_LINE_STYLE, Types.PAGE_BUTTONS -> settings().getBoolean("eink_mode", false)
                else -> true
            }

        override val inherited: Boolean
            get() = when (bundle.level) {
                SettingsLevel.WINDOW -> pageManagerSettings?.getValue(type) == null
                SettingsLevel.WORKSPACE -> workspaceSettings.getValue(type) == null
                SettingsLevel.GLOBAL -> false
            }

        /** Where the value comes from relative to this item's level; the bundle's own answer. */
        val inheritedFrom: InheritedFrom get() = bundle.inheritedFrom(type)

        override val isBoolean: Boolean get() = value is Boolean

        override val opensDialog: Boolean get() = !isBoolean

        override fun setNonSpecific() {
            val default = TextDisplaySettings.default
            when (bundle.level) {
                SettingsLevel.WINDOW -> pageManagerSettings?.setNonSpecific(type)
                SettingsLevel.WORKSPACE -> workspaceSettings.setNonSpecific(type)
                SettingsLevel.GLOBAL -> globalSettings.setValue(type, default.getValue(type))
            }
        }

        override fun handle() {
            val control = windowControl()
            when (bundle.level) {
                SettingsLevel.GLOBAL, SettingsLevel.WORKSPACE -> control.windowRepository.updateAllWindowsTextDisplaySettings()
                SettingsLevel.WINDOW -> window?.bibleView?.updateTextDisplaySettings()
            }
        }

        override val title: String
            get() = when (type) {
                Types.FONTSIZE -> strings().fmt("font_size_title_pt", valueInt)
                Types.TOPMARGIN -> strings().fmt("prefs_top_margin_title_mm", valueInt)
                Types.FONTFAMILY -> strings().fmt("pref_font_family_label_name", valueString)
                Types.LINE_SPACING -> strings().fmt("prefs_line_spacing_pt_title", valueInt.toFloat() / 10)
                Types.MARGINSIZE -> {
                    val default = TextDisplaySettings.default.marginSize!!
                    val margin = rawValue as? WorkspaceEntities.MarginSize ?: default
                    strings().fmt(
                        "prefs_margin_size_mm_title",
                        margin.marginLeft ?: default.marginLeft!!,
                        margin.marginRight ?: default.marginRight!!,
                        margin.maxWidth ?: default.maxWidth!!,
                    )
                }
                else -> strings().text(KeyedSettingLabels.titleKey(type))
            }

        override val summary: String?
            get() = if (type == Types.MARGINSIZE) {
                strings().text("prefs_margin_size_summary") + " " + strings().text("prefs_margin_size_summary_2")
            } else {
                null
            }

        override val iconKey: String?
            get() = when (type) {
                Types.STRONGS -> if (documentControl().isNewTestament) "ic_strongs_greek" else "ic_strongs_hebrew"
                Types.BOOKMARKS_SHOW -> "ic_bookmarks_show_24dp"
                Types.BOOKMARKS_HIDELABELS -> "ic_labels_hide_24dp"
                Types.MORPH -> "ic_morphology_24dp"
                Types.FOOTNOTES -> "ic_footnotes_24dp"
                Types.EXPAND_XREFS -> "ic_xrefs_inline_24dp"
                Types.XREFS -> "ic_xrefs_24dp"
                Types.SECTIONTITLES -> "ic_section_titles_24dp"
                Types.VERSENUMBERS -> "ic_chapter_verse_numbers_24dp"
                Types.COLORS -> "ic_color_settings_24dp"
                Types.FONTSIZE -> "ic_font_size_24dp"
                Types.FONTFAMILY -> "ic_font_family_24dp"
                Types.MARGINSIZE -> "ic_margin_size_24dp"
                Types.TOPMARGIN -> "ic_margin_top_24dp"
                Types.LINE_SPACING -> "ic_line_spacing_24dp"
                Types.REDLETTERS -> "ic_red_letter_24dp"
                Types.VERSEPERLINE -> "ic_one_verse_per_line_24dp"
                Types.JUSTIFY -> "ic_justify_text_24dp"
                Types.HYPHENATION -> "ic_hyphenation_24dp"
                Types.MYNOTES -> "ic_note_regular_24dp"
                Types.PAGENUMBER -> "ic_chapter_verse_numbers_24dp"
                Types.INFINITE_SCROLL -> "ic_full_screen_by_scrolling_24dp"
                Types.NON_STRONGS_WORD_ITALIC -> "ic_format_italic_24dp"
                Types.MARK_AS_READ_BUTTON, Types.MEMORIZATION_INDICATORS, Types.AUTO_TRACK_READING -> "ic_baseline_check_circle_24"
                Types.TITLE_SCROLL_BUTTON -> "ic_section_titles_24dp"
                else -> "ic_baseline_star_24"
            }
    }
}
