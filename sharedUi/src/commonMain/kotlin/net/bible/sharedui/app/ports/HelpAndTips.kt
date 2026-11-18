@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedui.help.HelpTipUi
import net.bible.sharedui.strings.Strings

/** The Help & tips catalog, shown on the Tips tab of the Help & tips screen on every platform. */
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
        "navigation" -> DocsLink.NAVIGATION.url
        "windows" -> DocsLink.WINDOWS.url
        "bookmarks" -> DocsLink.BOOKMARKS.url
        "study_pads" -> DocsLink.STUDY_PADS.url
        "search" -> DocsLink.SEARCH.url
        "workspaces" -> DocsLink.WORKSPACES.url
        "documents" -> DocsLink.DOCUMENTS.url
        "look_and_feel" -> DocsLink.LOOK_AND_FEEL.url
        else -> error("Unknown help page: $page")
    }

    /** The video link of [item] for an app in [appLanguage]: with captions in that language when the episode has them. */
    fun videoUrl(item: Item, appLanguage: String): String? = item.episode?.let { TutorialVideos.url(it, appLanguage) }

    /** True when [item]'s video label needs no "(English)" qualifier in [appLanguage]. */
    fun plainVideoLabel(item: Item, appLanguage: String): Boolean =
        item.episode?.let { TutorialVideos.hasPlainLabel(it, appLanguage) } ?: false

    /** Builds the screen's tip rows; [text] resolves a string resource key (the keyed string table of the host). */
    fun toUi(strings: Strings, text: (String) -> String, items: List<Item> = HelpAndTips.items, appLanguage: String = "en"): List<HelpTipUi> =
        items.map { item ->
            HelpTipUi(
                title = text(item.titleKey),
                text = text(item.textKey),
                videoUrl = videoUrl(item, appLanguage),
                videoLabel = if (plainVideoLabel(item, appLanguage)) strings.watchTutorialVideoPlain else strings.watchTutorialVideo,
                docUrl = docUrl(item.docPage),
            )
        }
}
