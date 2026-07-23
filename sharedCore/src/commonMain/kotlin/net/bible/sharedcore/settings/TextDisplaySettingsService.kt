package net.bible.sharedcore.settings

/**
 * Every entry's [Enum.name] must equal the corresponding
 * `net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types` name exactly — the
 * `:app` implementation maps between the two 1:1 via `Types.valueOf(name)`. Only ever append new
 * entries at the end; renaming or reordering breaks that mapping.
 */
enum class TextSettingType {
    FONTSIZE, FONTFAMILY, COLORS, MARGINSIZE, JUSTIFY, HYPHENATION, TOPMARGIN, LINE_SPACING,
    STRONGS, MORPH, FOOTNOTES, FOOTNOTES_INLINE, EXPAND_XREFS, XREFS, REDLETTERS, SECTIONTITLES,
    VERSENUMBERS, VERSEPERLINE, BOOKMARKS_SHOW, BOOKMARKS_HIDELABELS, MYNOTES, PAGENUMBER,
    INFINITE_SCROLL, NON_STRONGS_WORD_ITALIC, MARK_AS_READ_BUTTON, TITLE_SCROLL_BUTTON,
    MEMORIZATION_INDICATORS, AUTO_TRACK_READING, AI_DOC_MARKERS, PAGE_SCROLL_AMOUNT,
    SCROLL_HELPER_LINES, SCROLL_HELPER_LINE_STYLE, PAGE_BUTTONS, ORDINALS, SHOW_READING_PROGRESS,
}

/** Where a [TextSettingRow]'s effective value came from, for the "inherited from" indicator. */
enum class InheritedFrom { NONE, WORKSPACE, GLOBAL }

/** Identifies which level of the settings hierarchy a [TextSettingsSnapshot] is being viewed/edited at. */
sealed interface SettingsScope {
    data class Window(val windowId: String, val workspaceId: String) : SettingsScope
    data class Workspace(val workspaceId: String) : SettingsScope
    data object Global : SettingsScope
}

/** The rendered value + widget shape for one [TextSettingType] row. */
sealed interface TextSettingRowValue {
    data class Bool(val checked: Boolean) : TextSettingRowValue
    data class Choice(val selectedValue: String, val entries: List<SettingsItem.Choice>) : TextSettingRowValue
    data class Numeric(val value: Int, val min: Int, val max: Int, val displayText: String) : TextSettingRowValue
    data class Margins(val left: Int, val right: Int, val maxWidth: Int,
                       val leftMax: Int, val rightMax: Int, val maxWidthMax: Int,
                       val displayText: String) : TextSettingRowValue
    data class ColorsNav(val summary: String) : TextSettingRowValue
    data class HideLabels(val summary: String) : TextSettingRowValue
}

/** One row of a [TextSettingsSnapshot]. */
data class TextSettingRow(
    val type: TextSettingType,
    val value: TextSettingRowValue,
    val inheritedFrom: InheritedFrom,
    val enabled: Boolean,
    val visible: Boolean,
)

/** The full text-display-settings screen state for a given [scope]. */
data class TextSettingsSnapshot(
    val scope: SettingsScope,
    val screenTitle: String,
    val workspaceName: String,
    val rows: Map<TextSettingType, TextSettingRow>,
    val showParentCategory: Boolean,   // WINDOW or WORKSPACE
    val showWorkspaceLink: Boolean,    // WINDOW only
    val showGlobalLink: Boolean,       // WINDOW or WORKSPACE
)

/** The new value submitted by the UI for a [TextSettingType]; shape depends on the setting's row type. */
sealed interface TextSettingValue {
    data class BoolValue(val value: Boolean) : TextSettingValue
    data class IntValue(val value: Int) : TextSettingValue
    data class StringValue(val value: String) : TextSettingValue
    data class MarginsValue(val left: Int, val right: Int, val maxWidth: Int) : TextSettingValue
    data class LabelIdsValue(val ids: List<String>) : TextSettingValue
}

/**
 * Host seam for the text-display-settings screen (font size, margins, Strong's/morphology,
 * footnotes, bookmarks display, etc.), scoped to a window, workspace, or the global default.
 *
 * Plan B extends this interface with colours + background-image methods, consuming the same
 * [SettingsScope]/DTO vocabulary defined here. Those signatures (kept in sync with Plan B Task 1):
 *   fun loadColors(scope: SettingsScope): ColorsSnapshot
 *   fun loadBackgroundOptions(): List<BackgroundImageOption>
 *   fun setColor(scope: SettingsScope, field: ColorField, argb: Int)
 *   fun setNoise(scope: SettingsScope, night: Boolean, value: Int)
 *   fun setWorkspaceColor(scope: SettingsScope, argb: Int)
 *   fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?)
 *   fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int)
 *   fun resetColors(scope: SettingsScope)
 *   suspend fun importBackgroundImage(): BackgroundImageOption?
 *   fun deleteBackgroundImage(initials: String)
 */
interface TextDisplaySettingsService {
    fun loadText(scope: SettingsScope): TextSettingsSnapshot
    fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue)
    fun revert(scope: SettingsScope, type: TextSettingType)
    fun reset(scope: SettingsScope)
    // Plan B adds (colors + background image), consuming the SAME scope/DTO vocabulary.
    // These are the EXACT signatures Plan B declares (day/night carried as `night: Boolean`,
    // colours as non-null ARGB `Int`); keep them in sync with Plan B Task 1:
    //   fun loadColors(scope: SettingsScope): ColorsSnapshot
    //   fun loadBackgroundOptions(): List<BackgroundImageOption>
    //   fun setColor(scope: SettingsScope, field: ColorField, argb: Int)
    //   fun setNoise(scope: SettingsScope, night: Boolean, value: Int)
    //   fun setWorkspaceColor(scope: SettingsScope, argb: Int)
    //   fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?)
    //   fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int)
    //   fun resetColors(scope: SettingsScope)
    //   suspend fun importBackgroundImage(): BackgroundImageOption?
    //   fun deleteBackgroundImage(initials: String)
}
