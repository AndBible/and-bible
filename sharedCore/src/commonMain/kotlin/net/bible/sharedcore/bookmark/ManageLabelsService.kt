package net.bible.sharedcore.bookmark

/** Host-provided data + recent/override lookups for the ManageLabels list. Pure values only, plus
 *  the one persisted display preference below. */
interface ManageLabelsService {
    fun assignableLabels(): List<LabelItem>          // excludes the Unlabeled special (classic: filter !isUnlabeledLabel)
    fun unlabeledLabel(): LabelItem                  // bookmarkControl.labelUnlabelled as a LabelItem
    fun recentLabelIds(): List<String>               // workspaceSettings.recentLabels ids, in order
    /** The style each workspace-overridden label is forced to, keyed by label id. Empty when the
     *  workspace overrides nothing. */
    fun overriddenLabelStyles(): Map<String, BookmarkDisplayStyle>
    fun randomColorArgb(): Int                       // for a new label (host: Color.argb(255,rnd,rnd,rnd))

    /**
     * StudyPad full-text content search (classic `ManageLabels.kt:804-842`), STUDYPAD +
     * [SearchMode.CONTENT] only. The host does the JSword/IO work and returns already-ordered
     * results as plain [ManageLabelsRow.SearchResult]s; the controller debounces the call and
     * applies the results (or falls back to the categorized list on error/empty/short text).
     */
    suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult>

    /** Whether the list rows show their style-example line. A persisted user preference, not a
     *  derived value — the interface's "pure values only" rule has this one deliberate exception,
     *  because the ⋮ toggle that drives it must survive leaving the screen. */
    fun styleTagsVisible(): Boolean
    fun setStyleTagsVisible(visible: Boolean)
}
