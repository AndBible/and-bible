package net.bible.sharedui.app.workspace

import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.settings.BackgroundImageChooserLabels
import net.bible.sharedui.settings.ColorSettingsLabels
import net.bible.sharedui.settings.TextDisplaySettingsScreenLabels
import net.bible.sharedui.strings.Strings

/**
 * The four label bundles of the text-display-settings destination (controller, screen, colours form and
 * background-image chooser), built from [Strings]. Same wording as the `Context`-taking builders in
 * `TextDisplaySettingsLabelBuilders.kt` (which `ComposeReadingViewHost` still calls), each member mapped to the
 * same `R.string` resource in `AndroidStrings`. ~90 strings between them, so a host builds each one lazily.
 */
fun Strings.buildTextDisplayControllerLabels() = TextDisplaySettingsLabels(
    categoryParent = parentSettingsCategoryTitle,
    categoryFontColors = prefsFontAndColorsTitle,
    categoryTextLayout = prefsTextLayoutTitle,
    categoryStrongsMorphology = prefsStrongsAndMorphologyTitle,
    categoryFootnotesXrefs = prefsFootnotesAndXrefsTitle,
    categoryVersesHeadings = prefsVersesAndHeadingsTitle,
    categoryPageScrolling = prefsPageScrollingTitle,
    categoryBookmarks = prefsTextBookmarksTitle,
    categoryReadingMemorization = prefsReadingAndMemorizationTitle,
    // Contains "%s" -- left un-substituted here; the controller does its own .replace("%s", ...).
    workspaceLinkTitleFormat = workspaceTextOptionsLink,
    workspaceLinkSummary = workspaceTextOptionsLinkSummary,
    globalLinkTitle = globalTextOptionsLink,
    globalLinkSummary = globalTextOptionsLinkSummary,
    badgeWorkspace = textOptionsInheritedWorkspace,
    badgeGlobal = textOptionsInheritedGlobal,
    titles = mapOf(
        TextSettingType.COLORS to prefsTextColorsMenutitle,
        TextSettingType.FONTSIZE to fontSizeTitle,
        TextSettingType.FONTFAMILY to prefFontFamilyLabel,
        TextSettingType.LINE_SPACING to lineSpacingTitle,
        TextSettingType.REDLETTERS to prefsRedLetterTitle,
        TextSettingType.MARGINSIZE to prefsMarginSizeTitle,
        TextSettingType.TOPMARGIN to prefsTopMarginTitle,
        TextSettingType.JUSTIFY to prefsJustifyTitle,
        TextSettingType.HYPHENATION to prefsHyphenationTitle,
        TextSettingType.VERSEPERLINE to prefsVersePerLineTitle,
        TextSettingType.STRONGS to prefsShowStrongsTitle,
        TextSettingType.MORPH to prefsShowMorphologyTitle,
        TextSettingType.NON_STRONGS_WORD_ITALIC to prefsNonStrongsWordItalicTitle,
        TextSettingType.FOOTNOTES to prefsShowFootnotesTitle,
        TextSettingType.FOOTNOTES_INLINE to prefsShowFootnotesInlineTitle,
        TextSettingType.XREFS to prefsShowXrefsTitle,
        TextSettingType.EXPAND_XREFS to prefsExpandFootnotesTitle,
        TextSettingType.VERSENUMBERS to prefsShowVersenoTitle,
        TextSettingType.SECTIONTITLES to prefsSectionTitleTitle,
        TextSettingType.TITLE_SCROLL_BUTTON to prefsTitleScrollButtonTitle,
        TextSettingType.PAGENUMBER to pageNumberTitle,
        TextSettingType.INFINITE_SCROLL to prefsInfiniteScrollTitle,
        TextSettingType.PAGE_SCROLL_AMOUNT to prefsPageScrollAmountTitle,
        TextSettingType.SCROLL_HELPER_LINES to prefsScrollHelperLinesTitle,
        TextSettingType.SCROLL_HELPER_LINE_STYLE to prefsScrollHelperLineStyleTitle,
        TextSettingType.PAGE_BUTTONS to prefsPageButtonsTitle,
        TextSettingType.ORDINALS to prefsShowOrdinalsTitle,
        TextSettingType.SHOW_READING_PROGRESS to prefsShowReadingProgressTitle,
        TextSettingType.BOOKMARKS_SHOW to prefsShowBookmarksTitle,
        TextSettingType.MYNOTES to prefsShowMynotesTitle,
        TextSettingType.AI_DOC_MARKERS to prefsShowAiDocMarkersTitle,
        TextSettingType.BOOKMARKS_HIDELABELS to bookmarkSettingsHideLabelsTitle,
        TextSettingType.MARK_AS_READ_BUTTON to prefsMarkAsReadButtonTitle,
        TextSettingType.MEMORIZATION_INDICATORS to prefsShowMemorizationIndicatorsTitle,
        TextSettingType.AUTO_TRACK_READING to prefsAutoTrackReadingTitle,
    ),
    summaries = mapOf(
        TextSettingType.COLORS to prefsTextColorsSummary,
        TextSettingType.FONTSIZE to prefsFontTextSizeSummary,
        TextSettingType.FONTFAMILY to prefsFontFamilySummary,
        TextSettingType.LINE_SPACING to lineSpacingSummary,
        TextSettingType.REDLETTERS to prefsRedLetterSummary,
        TextSettingType.MARGINSIZE to prefsMarginSizeSummary,
        TextSettingType.TOPMARGIN to prefsTopMarginSummary,
        TextSettingType.JUSTIFY to prefsJustifySummary,
        TextSettingType.HYPHENATION to prefsHyphenationSummary,
        TextSettingType.VERSEPERLINE to prefsVersePerLineSummary,
        TextSettingType.STRONGS to prefsShowStrongsSummary,
        TextSettingType.MORPH to prefsShowMorphologySummary,
        TextSettingType.NON_STRONGS_WORD_ITALIC to prefsNonStrongsWordItalicSummary,
        TextSettingType.FOOTNOTES to prefsShowFootnotesSummary,
        TextSettingType.FOOTNOTES_INLINE to prefsShowFootnotesInlineSummary,
        TextSettingType.XREFS to prefsShowXrefsSummary,
        TextSettingType.EXPAND_XREFS to prefsExpandFootnotesSummary,
        TextSettingType.VERSENUMBERS to prefsShowVersenoSummary,
        TextSettingType.SECTIONTITLES to prefsSectionTitleSummary,
        TextSettingType.TITLE_SCROLL_BUTTON to prefsTitleScrollButtonSummary,
        TextSettingType.PAGENUMBER to pageNumberSummary,
        TextSettingType.INFINITE_SCROLL to prefsInfiniteScrollSummary,
        TextSettingType.PAGE_SCROLL_AMOUNT to prefsPageScrollAmountSummary,
        TextSettingType.SCROLL_HELPER_LINES to prefsScrollHelperLinesSummary,
        TextSettingType.SCROLL_HELPER_LINE_STYLE to prefsScrollHelperLineStyleSummary,
        TextSettingType.PAGE_BUTTONS to prefsPageButtonsSummary,
        TextSettingType.ORDINALS to prefsShowOrdinalsSummary,
        TextSettingType.SHOW_READING_PROGRESS to prefsShowReadingProgressSummary,
        TextSettingType.BOOKMARKS_SHOW to prefsShowBookmarksSummary,
        TextSettingType.MYNOTES to prefsShowMynotesSummary,
        TextSettingType.AI_DOC_MARKERS to prefsShowAiDocMarkersSummary,
        TextSettingType.BOOKMARKS_HIDELABELS to hideLabelsHelpIntro,
        TextSettingType.MARK_AS_READ_BUTTON to prefsMarkAsReadButtonSummary,
        TextSettingType.MEMORIZATION_INDICATORS to prefsShowMemorizationIndicatorsSummary,
        TextSettingType.AUTO_TRACK_READING to prefsAutoTrackReadingSummary,
    ),
)

fun Strings.buildTextDisplayScreenLabels() = TextDisplaySettingsScreenLabels(
    resetContentDescription = resetSettings,
    resetConfirmMessage = resetAreYouSure,
    // No classic string exists for a single-row "revert to inherited?" confirmation (this
    // long-press interaction is new in the Compose screen -- classic's per-row reset lives
    // inside each value-editor dialog, with no separate confirm step). Reusing the generic
    // bulk-reset confirmation is an accepted, if imprecise, wording.
    revertMessage = resetAreYouSure,
    fontSizeDialogTitle = fontSizeTitle,
    topMarginDialogTitle = prefsTopMarginTitle,
    lineSpacingDialogTitle = lineSpacingTitle,
    marginSizeDialogTitle = prefsMarginSizeTitle,
    // Contain "%d" -- left un-substituted; TextDisplaySettingsScreen/MarginContent does its own
    // .replace("%d", ...).
    marginLeftLabelFormat = prefLeftMarginLabelMm,
    marginRightLabelFormat = prefRightMarginLabelMm,
    marginMaxWidthLabelFormat = prefMaximumWidthOfTextLabelMm,
    resetToInheritedLabel = resetGeneric,
    badgeWorkspace = textOptionsInheritedWorkspace,
    badgeGlobal = textOptionsInheritedGlobal,
    okLabel = okay,
    cancelLabel = cancel,
)

fun Strings.buildColorSettingsLabels() = ColorSettingsLabels(
    dayMode = colorsDayModeTitle,
    nightMode = colorsNightModeTitle,
    textColor = colorText,
    backgroundColor = colorBackground,
    noise = prefsNoiseTitle,
    workspaceColor = colorWorkspace,
    backgroundImageDay = backgroundImageDay,
    backgroundImageNight = backgroundImageNight,
    opacityDay = backgroundImageOpacityDay,
    opacityNight = backgroundImageOpacityNight,
    change = backgroundImageChange,
    // No standalone R.string.reset exists (only "reset settings"-flavoured strings) -- reuse
    // the same generic reset wording buildTextDisplayScreenLabels() uses for resetToInheritedLabel.
    reset = resetGeneric,
)

fun Strings.buildBackgroundImageChooserLabels() = BackgroundImageChooserLabels(
    title = backgroundImageTitle,
    none = backgroundImageNone,
    import = backgroundImageImport,
    empty = backgroundImageEmpty,
    importing = backgroundImageImporting,
    deleteTitle = backgroundImageDeleteTitle,
    deleteConfirm = backgroundImageDeleteConfirm,
    delete = deleteLabel,
    cancel = cancel,
)

