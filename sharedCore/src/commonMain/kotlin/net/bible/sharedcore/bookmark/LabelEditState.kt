package net.bible.sharedcore.bookmark

/** Workspace label style override. NONE = no override (null overrideMode); others map to overrideMode 0..3. */
enum class OverrideMode { NONE, HIGHLIGHT, UNDERLINE, MARKER, HIDDEN }

/**
 * The effective look of a bookmark carrying this label, for ONE of the two bookmark kinds
 * (text-selection or whole-verse). The `Label` row stores six independent booleans, but the
 * renderer is a strict if/else chain (`bibleview-js/src/composables/bookmarks.ts:585-602`) under
 * the precedence `hide > marker > underline > highlight`, so per kind exactly one of these four
 * applies. HIGHLIGHT is the chain's `else` branch — the classic UI had no affordance for it at all,
 * which is why this enum exists. Same vocabulary as [OverrideMode]'s four non-NONE values on
 * purpose; `WorkspaceLabelOverride` already collapsed the same six booleans into one enum.
 */
enum class BookmarkDisplayStyle { HIGHLIGHT, UNDERLINE, MARKER, HIDDEN }

/** The three `Label` style columns for ONE bookmark kind, in the order the enum expands to. */
data class BookmarkStyleFlags(val hide: Boolean, val marker: Boolean, val underline: Boolean)

/**
 * The renderer's precedence, as one function: `hide > marker > underline > highlight`
 * (`bibleview-js/src/composables/bookmarks.ts:585-602`). Lossless with respect to what is drawn —
 * a dominated flag has no effect on screen, so collapsing it away cannot change appearance.
 */
fun bookmarkDisplayStyleOf(hide: Boolean, marker: Boolean, underline: Boolean): BookmarkDisplayStyle = when {
    hide -> BookmarkDisplayStyle.HIDDEN
    marker -> BookmarkDisplayStyle.MARKER
    underline -> BookmarkDisplayStyle.UNDERLINE
    else -> BookmarkDisplayStyle.HIGHLIGHT
}

/**
 * The inverse: exactly one flag true, the other two false. Deliberately canonical — writing back
 * through this is what clears the classic editor's silent hidden state (a dominated underline left
 * ticked under a hide, greyed but never cleared). Mirrors `Label.withStyleOverrides()`.
 */
fun bookmarkStyleFlagsOf(style: BookmarkDisplayStyle): BookmarkStyleFlags = when (style) {
    BookmarkDisplayStyle.HIDDEN -> BookmarkStyleFlags(hide = true, marker = false, underline = false)
    BookmarkDisplayStyle.MARKER -> BookmarkStyleFlags(hide = false, marker = true, underline = false)
    BookmarkDisplayStyle.UNDERLINE -> BookmarkStyleFlags(hide = false, marker = false, underline = true)
    BookmarkDisplayStyle.HIGHLIGHT -> BookmarkStyleFlags(hide = false, marker = false, underline = false)
}

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
    val wholeVerseStyle: BookmarkDisplayStyle,  // whole-verse bookmarks
    val favourite: Boolean,
    val isAssigning: Boolean,            // gates the "this bookmark" group
    val thisBookmarkSelected: Boolean,
    val thisBookmarkPrimary: Boolean,
    val hasWorkspaceContext: Boolean,    // gates the override dropdown + "this workspace" group
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
}
