package net.bible.sharedcore.bookmark

/** Workspace label style override. NONE = no override (null overrideMode); others map to overrideMode 0..3. */
enum class OverrideMode { NONE, HIGHLIGHT, UNDERLINE, MARKER, HIDDEN }

/**
 * The effective look of a bookmark carrying this label, for ONE of the two bookmark kinds
 * (text-selection or whole-verse). The `Label` row stores two enum columns now, but the
 * renderer is a strict if/else chain (`bibleview-js/src/composables/bookmarks.ts:585-602`) under
 * the precedence `hide > marker > underline > highlight`, so per kind exactly one of these four
 * applies. HIGHLIGHT is the chain's `else` branch — the classic UI had no affordance for it at all,
 * which is why this enum exists. Same vocabulary as [OverrideMode]'s four non-NONE values on
 * purpose; `WorkspaceLabelOverride` already collapsed the same six booleans into one enum.
 */
enum class BookmarkDisplayStyle { HIGHLIGHT, UNDERLINE, MARKER, HIDDEN }

/**
 * Plain, portable editable state for one label. [labelId] is the opaque `IdType.toString()`
 * (empty for a brand-new label). [color] is ARGB. No Android/Room/JSword types.
 */
data class LabelEditState(
    val labelId: String,
    val name: String,
    val color: Int,
    val customIcon: String?,             // icon-name key into the host customIconMap, or null
    val selectionStyle: BookmarkDisplayStyle,   // text-selection bookmarks
    val wholeVerseStyle: BookmarkDisplayStyle?, // whole-verse bookmarks; null = inherit selectionStyle
    val favourite: Boolean,
    val isAssigning: Boolean,            // gates the "this bookmark" group
    val thisBookmarkSelected: Boolean,
    val thisBookmarkPrimary: Boolean,
    val hasWorkspaceContext: Boolean,    // gates the override picker + "this workspace" group
    val autoAssign: Boolean,
    val autoAssignPrimary: Boolean,
    val overrideMode: OverrideMode,
    val isSpecialLabel: Boolean,         // disables name + hides favourite/autoAssign
    val isSpeakLabel: Boolean,           // additionally hides customIcon
) {
    val nameEditable: Boolean get() = !isSpecialLabel
    val favouriteVisible: Boolean get() = !isSpecialLabel
    val workspaceGroupVisible: Boolean get() = hasWorkspaceContext && !isSpecialLabel
    val customIconVisible: Boolean get() = !isSpeakLabel
    val thisBookmarkGroupVisible: Boolean get() = isAssigning

    val thisBookmarkPrimaryEnabled: Boolean get() = thisBookmarkSelected
    val autoAssignPrimaryEnabled: Boolean get() = autoAssign

    /**
     * The state as it should be persisted: a whole-verse style equal to the selection style is
     * stored as `null` (inherit), matching what the 12 → 13 migration did to the historical data
     * (`BookmarkMigrations.kt:259`). Applied at SAVE time only, never to live editor state.
     */
    fun normalizedForSave(): LabelEditState =
        if (wholeVerseStyle != null && wholeVerseStyle == selectionStyle) copy(wholeVerseStyle = null) else this
}
