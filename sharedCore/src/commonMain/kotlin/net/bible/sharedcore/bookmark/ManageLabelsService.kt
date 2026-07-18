package net.bible.sharedcore.bookmark

/** Host-provided data + recent/override lookups for the ManageLabels list. Pure values only. */
interface ManageLabelsService {
    fun assignableLabels(): List<LabelItem>          // excludes the Unlabeled special (classic: filter !isUnlabeledLabel)
    fun unlabeledLabel(): LabelItem                  // bookmarkControl.labelUnlabelled as a LabelItem
    fun recentLabelIds(): List<String>               // workspaceSettings.recentLabels ids, in order
    fun overriddenLabelIds(): Set<String>            // workspace overrides with hasOverride
    fun randomColorArgb(): Int                       // for a new label (host: Color.argb(255,rnd,rnd,rnd))
}
