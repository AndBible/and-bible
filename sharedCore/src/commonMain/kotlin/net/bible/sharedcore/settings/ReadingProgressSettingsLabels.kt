package net.bible.sharedcore.settings

/**
 * Host-resolved strings for the reading-progress/memorization settings screen (category-less;
 * screen title + row titles/summaries), kept out of the controller so tests can supply stubs and
 * translated strings stay on the Android side (`strings.xml`). Mirrors `AppSettingsLabels`
 * (`net.bible.sharedcore.settings.AppSettingsService`).
 */
data class ReadingProgressSettingsLabels(
    val screenTitle: String,

    val autoMarkMemorizedTitle: String,
    val autoMarkMemorizedSummary: String,
    val memorizeTypeFullWordsTitle: String,
    val memorizeTypeFullWordsSummary: String,
    val memorizeWordVisibilityTitle: String,
    val memorizeWordVisibilitySummary: String,
    val memorizeErrorHeatmapTitle: String,
    val memorizeErrorHeatmapSummary: String,
    val memorizeScrambleHideUsedTitle: String,
    val memorizeScrambleHideUsedSummary: String,
    val memorizeIncludeReferenceTitle: String,
    val memorizeIncludeReferenceSummary: String,
) {
    companion object {
        fun forTest() = ReadingProgressSettingsLabels(
            screenTitle = "Reading progress settings",

            autoMarkMemorizedTitle = "Automatically mark as memorized",
            autoMarkMemorizedSummary = "Mark a verse as memorized once you have typed it correctly",
            memorizeTypeFullWordsTitle = "Type full words",
            memorizeTypeFullWordsSummary = "Require typing whole words instead of first letters",
            memorizeWordVisibilityTitle = "Word visibility",
            memorizeWordVisibilitySummary = "Choose how much of each word is shown while memorizing",
            memorizeErrorHeatmapTitle = "Error heatmap",
            memorizeErrorHeatmapSummary = "Highlight words that are often typed incorrectly",
            memorizeScrambleHideUsedTitle = "Hide used words when scrambled",
            memorizeScrambleHideUsedSummary = "Hide words already placed correctly in scramble mode",
            memorizeIncludeReferenceTitle = "Include reference",
            memorizeIncludeReferenceSummary = "Include the verse reference when memorizing",
        )
    }
}
