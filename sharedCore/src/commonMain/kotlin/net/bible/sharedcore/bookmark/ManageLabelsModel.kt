package net.bible.sharedcore.bookmark

/** The four contexts ManageLabels is shown in, each enabling a different subset of controls. */
enum class ManageLabelsMode {
    STUDYPAD, WORKSPACE, ASSIGN, HIDELABELS;

    val showUnassigned: Boolean get() = this == HIDELABELS || this == WORKSPACE
    val showCheckboxes: Boolean get() = this == HIDELABELS || this == ASSIGN
    val hasResetButton: Boolean get() = this == WORKSPACE || this == HIDELABELS
    val hasReOrderButton: Boolean get() = this == HIDELABELS || this == ASSIGN || this == WORKSPACE
    val workspaceEdits: Boolean get() = this == WORKSPACE || this == ASSIGN
    val primaryShown: Boolean get() = this == WORKSPACE || this == ASSIGN
    val showActiveCategory: Boolean get() = this == WORKSPACE || this == ASSIGN || this == HIDELABELS
    val hideCategories: Boolean get() = this == STUDYPAD
}

/** Grouping of labels in the list: currently active, recently used, or everything else. */
enum class LabelCategory { ACTIVE, RECENT, OTHER }

/**
 * How the search text filters the label list (StudyPad content-search, 7b-2). [NAME_START]
 * (default) and [NAME_CONTAINS] filter on the label's display name; [CONTENT] searches inside
 * StudyPad entry text (Task 2) — its name-filter fallback (used e.g. on a search error) behaves
 * like [NAME_CONTAINS].
 */
enum class SearchMode { NAME_START, NAME_CONTAINS, CONTENT }

/** One plain, portable label as shown in the list. [id] = IdType.toString(); [color] = ARGB. */
data class LabelItem(
    val id: String,
    val name: String,            // already display-name resolved
    val color: Int,
    val favourite: Boolean,
    val isUnlabeled: Boolean,
    val isSpecial: Boolean,
    val customIcon: String?,
    val hasOverride: Boolean,
)

sealed interface ManageLabelsRow {
    data class Header(val category: LabelCategory) : ManageLabelsRow
    /** A label row with its mode-computed control states resolved by the controller. */
    data class Item(
        val label: LabelItem,
        val checked: Boolean,        // in contextSelectedItems
        val isAutoAssign: Boolean,   // in autoAssignLabels (workspaceEdits)
        val isPrimary: Boolean,      // == contextPrimaryLabel
        val highlighted: Boolean,    // == highlightLabelId (StudyPad current)
    ) : ManageLabelsRow
    /** A StudyPad content-search hit (SearchMode.CONTENT, Task 2): one label with matching entries. */
    data class SearchResult(
        val labelId: String,
        val name: String,
        val color: Int,
        val matchCount: Int,
        val snippet: String,
        val matchStart: Int,
        val matchEnd: Int,
        val firstMatchEntryId: String?,
    ) : ManageLabelsRow
}
