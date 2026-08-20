package net.bible.sharedcore.bookmark

/** Host-provided data + recent/override lookups for the ManageLabels list. Pure values only. */
interface ManageLabelsService {
    fun assignableLabels(): List<LabelItem>          // excludes the Unlabeled special (classic: filter !isUnlabeledLabel)
    fun unlabeledLabel(): LabelItem                  // bookmarkControl.labelUnlabelled as a LabelItem
    fun recentLabelIds(): List<String>               // workspaceSettings.recentLabels ids, in order
    fun overriddenLabelIds(): Set<String>            // workspace overrides with hasOverride
    fun randomColorArgb(): Int                       // for a new label (host: Color.argb(255,rnd,rnd,rnd))

    /**
     * StudyPad full-text content search (classic `ManageLabels.kt:804-842`), STUDYPAD +
     * [SearchMode.CONTENT] only. The host does the JSword/IO work and returns already-ordered
     * results as plain [ManageLabelsRow.SearchResult]s; the controller debounces the call and
     * applies the results (or falls back to the categorized list on error/empty/short text).
     */
    suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult>

    /** Whether the label list draws one-line rows. A global view preference, not per-workspace:
     *  persisted host-side, seeded into the controller at construction and written through on every
     *  toggle (there is no Save step for a view preference). */
    fun compactLabelRows(): Boolean
    fun setCompactLabelRows(value: Boolean)
}
