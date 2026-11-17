@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedui.help.HelpTipUi
import net.bible.sharedui.strings.Strings

/** The Help & tips catalog, matching Android's CommonUtils.showHelp order and links. */
@kotlin.native.HiddenFromObjC
object HelpAndTips {
    /** One tip: string resource keys, the tutorial [episode] that explains it and the user-guide page [docPage] (a stem). */
    @kotlin.native.HiddenFromObjC
    data class Item(val titleKey: String, val textKey: String, val episode: TutorialVideos.Episode?, val docPage: String?)

    val items: List<Item> = listOf(
        Item("help_nav_title", "help_nav_text", TutorialVideos.Episode.TUTORIAL_02_FINDING_YOUR_WAY, "navigation"),
        Item("help_contextmenus_title", "help_contextmenus_text", TutorialVideos.Episode.TUTORIAL_03_VERSE_MENU, null),
        Item("help_study_tools_title", "help_study_tools_text", TutorialVideos.Episode.TUTORIAL_04_STRONGS_DICTIONARIES_CROSSREFS, "documents"),
        Item("help_window_pinning_title", "help_window_pinning_text", TutorialVideos.Episode.TUTORIAL_06_WINDOWS_2_PIN_SYNC_LINKS, "windows"),
        Item("help_bookmarks_title", "help_bookmarks_text", TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS, "bookmarks"),
        Item("studypads", "help_studypads_text", TutorialVideos.Episode.TUTORIAL_11_STUDY_PADS, "study_pads"),
        Item("help_search_title", "help_search_text2", TutorialVideos.Episode.TUTORIAL_12_SEARCH_1_BASICS, "search"),
        Item("help_workspaces_title", "help_workspaces_text", TutorialVideos.Episode.TUTORIAL_07_WORKSPACES, "workspaces"),
        Item("help_look_and_feel_title", "help_look_and_feel_text", TutorialVideos.Episode.TUTORIAL_19_APPEARANCE_1_NIGHT_MODE_FONTS_COLOURS, "look_and_feel"),
        Item("help_hidden_features_title", "help_hidden_features_text", null, null),
    )

    /** The user-guide URL of a [Item.docPage] stem; null for null. Literal calls keep the app-to-documentation link scanner authoritative. */
    fun docUrl(page: String?): String? = when (page) {
        null -> null
        "navigation" -> DocsLinks.page("navigation")
        "windows" -> DocsLinks.page("windows")
        "bookmarks" -> DocsLinks.page("bookmarks")
        "study_pads" -> DocsLinks.page("study_pads")
        "search" -> DocsLinks.page("search")
        "workspaces" -> DocsLinks.page("workspaces")
        "documents" -> DocsLinks.page("documents")
        "look_and_feel" -> DocsLinks.page("look_and_feel")
        else -> error("Unknown help page: $page")
    }

    /** The video link of [item] (until the language-aware links of the subtitle work, the episode's own or the placeholder URL). */
    fun videoUrl(item: Item): String? = item.episode?.let(TutorialVideos::url)

    /** Builds the screen's tip rows; [text] resolves a string resource key (the keyed string table of the host). */
    fun toUi(strings: Strings, text: (String) -> String, items: List<Item> = HelpAndTips.items): List<HelpTipUi> =
        items.map { item ->
            HelpTipUi(
                title = text(item.titleKey),
                text = text(item.textKey),
                videoUrl = videoUrl(item),
                videoLabel = strings.watchTutorialVideo,
                docUrl = docUrl(item.docPage),
            )
        }
}
