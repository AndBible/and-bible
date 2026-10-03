package net.bible.sharedcore.bookmark

/** The four contexts ManageLabels is shown in, each enabling a different subset of controls. */
enum class ManageLabelsMode {
    STUDYPAD, WORKSPACE, ASSIGN, HIDELABELS;

    val showUnassigned: Boolean get() = this == HIDELABELS || this == WORKSPACE
    val showCheckboxes: Boolean get() = this == HIDELABELS || this == ASSIGN
    val hasResetButton: Boolean get() = this == WORKSPACE || this == HIDELABELS
    /** Re-order regroups rows into the ACTIVE bucket and does nothing else (RECENT is constant for
     *  the session), so it is offered exactly where [showActiveCategory] is true. Keep the two
     *  in step — `ManageLabelsModeTest` asserts they are equal for every mode. */
    val hasReOrderButton: Boolean get() = this == ASSIGN || this == HIDELABELS
    val workspaceEdits: Boolean get() = this == WORKSPACE || this == ASSIGN
    val primaryShown: Boolean get() = this == WORKSPACE || this == ASSIGN
    /** Round 17b: ASSIGN and HIDELABELS only. WORKSPACE draws no leading selection control, so a
     *  "Selected labels" heading named a selection the user could not see themselves making; its
     *  auto-assign membership is visible per row in the ⚡ column and reachable as a search filter
     *  ([LabelFilter.AUTO_ADD]) instead. Must stay equal to [hasReOrderButton]. */
    val showActiveCategory: Boolean get() = this == ASSIGN || this == HIDELABELS
    val hideCategories: Boolean get() = this == STUDYPAD
    /** Whether a row shows its style example line: only ASSIGN and WORKSPACE, because those are
     *  the two modes where what the label will look like on the page is the thing the user is
     *  choosing. See ManageLabelsModeTest for the fuller reasoning. */
    val styleTagsShown: Boolean get() = this == WORKSPACE || this == ASSIGN
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

/**
 * The row filters the search-options sheet offers (round 17b). Multiple active filters are ANDed.
 * They are offered only where the matching row control is drawn (`ManageLabelsMode.workspaceEdits`),
 * because a filter for a state the list does not display would have no visible referent — and they
 * are cleared when the search bar closes, so the list is never silently filtered.
 */
enum class LabelFilter { AUTO_ADD, FAVOURITE }

/** One plain, portable label as shown in the list. [id] = IdType.toString(); [color] = ARGB. */
data class LabelItem(
    val id: String,
    val name: String,            // already display-name resolved
    val color: Int,
    val favourite: Boolean,
    val isUnlabeled: Boolean,
    val isSpecial: Boolean,
    val customIcon: String?,
    // Appended, not inserted: ManageLabelsControllerTest constructs LabelItem POSITIONALLY, so an
    // inserted parameter would rebind its neighbour's argument. Same append-only discipline the
    // positional AppMessage keys taught this repo.
    /** The label's own style for text-selection bookmarks. */
    val selectionStyle: BookmarkDisplayStyle = BookmarkDisplayStyle.HIGHLIGHT,
    /** The whole-verse axis; `null` means "inherit [selectionStyle]", which is a storage concept —
     *  the list shows the tag for this axis only when it is non-null, i.e. only when it is really
     *  set to something of its own. */
    val wholeVerseStyle: BookmarkDisplayStyle? = null,
    /** The style this workspace's override imposes, or `null` when there is no override. Relinked
     *  by the controller on every rebuild from [ManageLabelsService.overriddenLabelStyles] — the
     *  mapper cannot know it, since it is workspace state, not label state. An override takes BOTH
     *  axes (`BookmarkEntities.Label.withStyleOverrides`), so this single value is what the reader
     *  draws for this label here, whatever [selectionStyle] and [wholeVerseStyle] say. */
    val overrideStyle: BookmarkDisplayStyle? = null,
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
