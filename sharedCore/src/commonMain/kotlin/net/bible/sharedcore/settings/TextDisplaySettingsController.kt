package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Host-resolved strings for the text-display-settings screen: category headers, drill-up
 * parent-link rows, and per-[TextSettingType] titles/summaries. Kept out of the controller so
 * tests can supply stubs and translated strings stay on the Android side (`strings.xml`). Mirrors
 * `ReadingProgressSettingsLabels`/`AppSettingsLabels`.
 */
data class TextDisplaySettingsLabels(
    /** The `parent_settings_category` header shown above the two drill-up link rows (classic
     * `text_display_settings.xml` `parent_settings_category_title`). */
    val categoryParent: String,
    val categoryFontColors: String,
    val categoryTextLayout: String,
    val categoryStrongsMorphology: String,
    val categoryFootnotesXrefs: String,
    val categoryVersesHeadings: String,
    val categoryPageScrolling: String,
    val categoryBookmarks: String,
    val categoryReadingMemorization: String,
    /** Contains "%s", replaced with the workspace name. */
    val workspaceLinkTitleFormat: String,
    val workspaceLinkSummary: String,
    val globalLinkTitle: String,
    val globalLinkSummary: String,
    val badgeWorkspace: String,
    val badgeGlobal: String,
    val titles: Map<TextSettingType, String>,
    val summaries: Map<TextSettingType, String>,
) {
    companion object {
        fun forTest() = TextDisplaySettingsLabels(
            categoryParent = "Parent settings",
            categoryFontColors = "Font & colours",
            categoryTextLayout = "Text layout",
            categoryStrongsMorphology = "Strong's & morphology",
            categoryFootnotesXrefs = "Footnotes & cross-references",
            categoryVersesHeadings = "Verses & headings",
            categoryPageScrolling = "Page & scrolling",
            categoryBookmarks = "Bookmarks",
            categoryReadingMemorization = "Reading & memorization",
            workspaceLinkTitleFormat = "%s text options",
            workspaceLinkSummary = "Edit the settings shared by this workspace",
            globalLinkTitle = "Global text options",
            globalLinkSummary = "Edit the app-wide default settings",
            badgeWorkspace = "Workspace",
            badgeGlobal = "Global",
            titles = TextSettingType.entries.associateWith { it.name },
            summaries = TextSettingType.entries.associateWith { it.name + " summary" },
        )
    }
}

/** View-data the text-display-settings composable renders. */
data class TextDisplaySettingsScreenState(
    val title: String,
    val items: List<SettingsItem>,
    /** Raw values for the screen's numeric/margin dialogs + badge (inherited-from) lookup. */
    val rows: Map<TextSettingType, TextSettingRow>,
)

/** Drill-up navigation key: opens the enclosing workspace's text-display-settings screen. */
const val KEY_OPEN_WORKSPACE_SETTINGS = "open_workspace_settings"

/** Drill-up navigation key: opens the global (app-wide default) text-display-settings screen. */
const val KEY_OPEN_GLOBAL_SETTINGS = "open_global_settings"

/**
 * Builds the declarative [TextDisplaySettingsScreenState] for the text-display-settings screen
 * (font size, margins, Strong's/morphology, footnotes, bookmarks display, etc.) from a
 * [TextDisplaySettingsService] snapshot, at a given [settingsScope] (window, workspace, or
 * global). Synchronous and NOT a service-flow collector: drill-up means multiple scopes/
 * controllers coexist (a window screen, its workspace screen, the global screen), each owning its
 * own snapshot — [refresh] re-pulls `service.loadText(settingsScope)` after every write.
 *
 * Category order + the 9-category / 35-type membership mirrors the classic XML preference screen
 * (`docs/adding-text-display-setting.md`). Row kind (switch/list-choice/navigation) is fixed per
 * [TextSettingType] — see the `when` in [buildRow].
 */
class TextDisplaySettingsController(
    private val service: TextDisplaySettingsService,
    private val settingsScope: SettingsScope,
    private val labels: TextDisplaySettingsLabels,
    private val onNavigateCallback: (String) -> Unit,
) {
    private val _state = MutableStateFlow(build(service.loadText(settingsScope)))
    val state: StateFlow<TextDisplaySettingsScreenState> = _state.asStateFlow()

    /** Re-pull the snapshot from the service and rebuild the screen state. */
    fun refresh() { _state.value = build(service.loadText(settingsScope)) }

    private fun build(snapshot: TextSettingsSnapshot): TextDisplaySettingsScreenState {
        fun rowsFor(types: List<TextSettingType>): List<SettingsItem> =
            types.map { buildRow(snapshot.rows.getValue(it)) }

        val items = listOf(
            SettingsItem.Category(
                key = "cat_parent",
                title = labels.categoryParent,
                visible = snapshot.showParentCategory,
            ),
            *listOfNotNull(
                // The drill-up links are omitted (not merely hidden) when not applicable to this
                // scope: e.g. a workspace screen has no enclosing workspace to link to.
                if (snapshot.showWorkspaceLink) {
                    SettingsItem.NavigationRow(
                        key = KEY_OPEN_WORKSPACE_SETTINGS,
                        title = labels.workspaceLinkTitleFormat.replace("%s", snapshot.workspaceName),
                        summary = labels.workspaceLinkSummary,
                    )
                } else null,
                if (snapshot.showGlobalLink) {
                    SettingsItem.NavigationRow(
                        key = KEY_OPEN_GLOBAL_SETTINGS,
                        title = labels.globalLinkTitle,
                        summary = labels.globalLinkSummary,
                    )
                } else null,
            ).toTypedArray(),

            SettingsItem.Category(key = "cat_font_colors", title = labels.categoryFontColors),
            *rowsFor(listOf(
                TextSettingType.COLORS, TextSettingType.FONTSIZE, TextSettingType.FONTFAMILY,
                TextSettingType.LINE_SPACING, TextSettingType.REDLETTERS,
            )).toTypedArray(),

            SettingsItem.Category(key = "cat_text_layout", title = labels.categoryTextLayout),
            *rowsFor(listOf(
                TextSettingType.MARGINSIZE, TextSettingType.TOPMARGIN, TextSettingType.JUSTIFY,
                TextSettingType.HYPHENATION, TextSettingType.VERSEPERLINE,
            )).toTypedArray(),

            SettingsItem.Category(key = "cat_strongs_morphology", title = labels.categoryStrongsMorphology),
            *rowsFor(listOf(
                TextSettingType.STRONGS, TextSettingType.MORPH, TextSettingType.NON_STRONGS_WORD_ITALIC,
            )).toTypedArray(),

            SettingsItem.Category(key = "cat_footnotes_xrefs", title = labels.categoryFootnotesXrefs),
            *rowsFor(listOf(
                TextSettingType.FOOTNOTES, TextSettingType.FOOTNOTES_INLINE, TextSettingType.XREFS,
                TextSettingType.EXPAND_XREFS,
            )).toTypedArray(),

            SettingsItem.Category(key = "cat_verses_headings", title = labels.categoryVersesHeadings),
            *rowsFor(listOf(
                TextSettingType.VERSENUMBERS, TextSettingType.SECTIONTITLES,
                TextSettingType.TITLE_SCROLL_BUTTON, TextSettingType.PAGENUMBER,
            )).toTypedArray(),

            SettingsItem.Category(key = "cat_page_scrolling", title = labels.categoryPageScrolling),
            *rowsFor(listOf(
                TextSettingType.INFINITE_SCROLL, TextSettingType.PAGE_SCROLL_AMOUNT,
                TextSettingType.SCROLL_HELPER_LINES, TextSettingType.SCROLL_HELPER_LINE_STYLE,
                TextSettingType.PAGE_BUTTONS, TextSettingType.ORDINALS, TextSettingType.SHOW_READING_PROGRESS,
            )).toTypedArray(),

            SettingsItem.Category(key = "cat_bookmarks", title = labels.categoryBookmarks),
            *rowsFor(listOf(
                TextSettingType.BOOKMARKS_SHOW, TextSettingType.MYNOTES, TextSettingType.AI_DOC_MARKERS,
                TextSettingType.BOOKMARKS_HIDELABELS,
            )).toTypedArray(),

            SettingsItem.Category(key = "cat_reading_memorization", title = labels.categoryReadingMemorization),
            *rowsFor(listOf(
                TextSettingType.MARK_AS_READ_BUTTON, TextSettingType.MEMORIZATION_INDICATORS,
                TextSettingType.AUTO_TRACK_READING,
            )).toTypedArray(),
        )
        return TextDisplaySettingsScreenState(title = snapshot.screenTitle, items = items, rows = snapshot.rows)
    }

    private fun buildRow(row: TextSettingRow): SettingsItem {
        val type = row.type
        val key = type.name
        val title = labels.titles.getValue(type)
        return when (type) {
            TextSettingType.JUSTIFY, TextSettingType.HYPHENATION, TextSettingType.MORPH,
            TextSettingType.FOOTNOTES, TextSettingType.FOOTNOTES_INLINE, TextSettingType.EXPAND_XREFS,
            TextSettingType.XREFS, TextSettingType.REDLETTERS, TextSettingType.SECTIONTITLES,
            TextSettingType.VERSENUMBERS, TextSettingType.VERSEPERLINE, TextSettingType.BOOKMARKS_SHOW,
            TextSettingType.MYNOTES, TextSettingType.PAGENUMBER, TextSettingType.INFINITE_SCROLL,
            TextSettingType.NON_STRONGS_WORD_ITALIC, TextSettingType.MARK_AS_READ_BUTTON,
            TextSettingType.TITLE_SCROLL_BUTTON, TextSettingType.MEMORIZATION_INDICATORS,
            TextSettingType.AUTO_TRACK_READING, TextSettingType.AI_DOC_MARKERS,
            TextSettingType.SCROLL_HELPER_LINES, TextSettingType.PAGE_BUTTONS, TextSettingType.ORDINALS,
            TextSettingType.SHOW_READING_PROGRESS -> {
                val value = row.value as TextSettingRowValue.Bool
                SettingsItem.SwitchRow(
                    key = key,
                    title = title,
                    summary = labels.summaries[type],
                    checked = value.checked,
                    enabled = row.enabled,
                    visible = row.visible,
                    iconKey = row.iconKey,
                )
            }

            TextSettingType.STRONGS, TextSettingType.PAGE_SCROLL_AMOUNT,
            TextSettingType.SCROLL_HELPER_LINE_STYLE, TextSettingType.FONTFAMILY -> {
                val value = row.value as TextSettingRowValue.Choice
                SettingsItem.ListChoiceRow(
                    key = key,
                    title = title,
                    summary = labels.summaries[type],
                    entries = value.entries,
                    selectedValue = value.selectedValue,
                    enabled = row.enabled,
                    visible = row.visible,
                    iconKey = row.iconKey,
                )
            }

            TextSettingType.FONTSIZE, TextSettingType.TOPMARGIN, TextSettingType.LINE_SPACING -> {
                val value = row.value as TextSettingRowValue.Numeric
                SettingsItem.NavigationRow(
                    key = key,
                    title = title,
                    summary = value.displayText,
                    enabled = row.enabled,
                    visible = row.visible,
                    iconKey = row.iconKey,
                )
            }

            TextSettingType.MARGINSIZE -> {
                val value = row.value as TextSettingRowValue.Margins
                SettingsItem.NavigationRow(
                    key = key,
                    title = title,
                    summary = value.displayText,
                    enabled = row.enabled,
                    visible = row.visible,
                    iconKey = row.iconKey,
                )
            }

            TextSettingType.COLORS -> {
                val value = row.value as TextSettingRowValue.ColorsNav
                SettingsItem.NavigationRow(
                    key = key,
                    title = title,
                    summary = value.summary,
                    enabled = row.enabled,
                    visible = row.visible,
                    iconKey = row.iconKey,
                )
            }

            TextSettingType.BOOKMARKS_HIDELABELS -> {
                val value = row.value as TextSettingRowValue.HideLabels
                SettingsItem.NavigationRow(
                    key = key,
                    title = title,
                    summary = value.summary,
                    enabled = row.enabled,
                    visible = row.visible,
                    iconKey = row.iconKey,
                )
            }
        }
    }

    fun onSwitch(key: String, checked: Boolean) {
        service.setValue(settingsScope, TextSettingType.valueOf(key), TextSettingValue.BoolValue(checked))
        refresh()
    }

    fun onListChoice(key: String, value: String) {
        val type = TextSettingType.valueOf(key)
        val setting = when (type) {
            TextSettingType.STRONGS, TextSettingType.PAGE_SCROLL_AMOUNT,
            TextSettingType.SCROLL_HELPER_LINE_STYLE -> TextSettingValue.IntValue(value.toInt())
            else -> TextSettingValue.StringValue(value)
        }
        service.setValue(settingsScope, type, setting)
        refresh()
    }

    fun onNumericChange(key: String, value: Int) {
        service.setValue(settingsScope, TextSettingType.valueOf(key), TextSettingValue.IntValue(value))
        refresh()
    }

    fun onMarginsChange(key: String, left: Int, right: Int, maxWidth: Int) {
        service.setValue(settingsScope, TextSettingType.valueOf(key), TextSettingValue.MarginsValue(left, right, maxWidth))
        refresh()
    }

    fun onHideLabelsChange(ids: List<String>) {
        service.setValue(settingsScope, TextSettingType.BOOKMARKS_HIDELABELS, TextSettingValue.LabelIdsValue(ids))
        refresh()
    }

    fun onRevert(key: String) {
        service.revert(settingsScope, TextSettingType.valueOf(key))
        refresh()
    }

    fun onReset() {
        service.reset(settingsScope)
        refresh()
    }

    fun onNavigate(key: String) = onNavigateCallback(key)
}
