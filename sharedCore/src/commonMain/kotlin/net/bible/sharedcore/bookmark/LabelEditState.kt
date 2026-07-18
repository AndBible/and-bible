package net.bible.sharedcore.bookmark

/** Workspace label style override. NONE = no override (null overrideMode); others map to overrideMode 0..3. */
enum class OverrideMode { NONE, HIGHLIGHT, UNDERLINE, MARKER, HIDDEN }

/**
 * Plain, portable editable state for one label. [labelId] is the opaque `IdType.toString()`
 * (empty for a brand-new label). [color] is ARGB. No Android/Room/JSword types.
 */
data class LabelEditState(
    val labelId: String,
    val name: String,
    val color: Int,
    val customIcon: String?,             // icon-name key into the host customIconMap, or null
    val underline: Boolean,
    val underlineWholeVerse: Boolean,
    val marker: Boolean,
    val markerWholeVerse: Boolean,
    val hide: Boolean,
    val hideWholeVerse: Boolean,
    val favourite: Boolean,
    val isAssigning: Boolean,            // gates the "this bookmark" group
    val thisBookmarkSelected: Boolean,
    val thisBookmarkPrimary: Boolean,
    val hasWorkspaceContext: Boolean,    // gates the override dropdown + "this workspace" group
    val autoAssign: Boolean,
    val autoAssignPrimary: Boolean,
    val overrideMode: OverrideMode,
    val isSpecialLabel: Boolean,         // disables name + hides favourite/autoAssign/customIcon
    val isSpeakLabel: Boolean,           // additionally hides customIcon
) {
    val nameEditable: Boolean get() = !isSpecialLabel
    val favouriteVisible: Boolean get() = !isSpecialLabel
    val workspaceGroupVisible: Boolean get() = hasWorkspaceContext && !isSpecialLabel
    val customIconVisible: Boolean get() = !isSpeakLabel
    val thisBookmarkGroupVisible: Boolean get() = isAssigning

    val underlineEnabled: Boolean get() = !hide && !marker
    val underlineWholeVerseEnabled: Boolean get() = !hideWholeVerse && !markerWholeVerse
    val markerEnabled: Boolean get() = !hide
    val markerWholeVerseEnabled: Boolean get() = !hideWholeVerse
    val thisBookmarkPrimaryEnabled: Boolean get() = thisBookmarkSelected
    val autoAssignPrimaryEnabled: Boolean get() = autoAssign
}
