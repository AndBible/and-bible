@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import net.bible.sharedcore.docs.TutorialVideos

/** The drawer help catalog, matching Android's CommonUtils.showHelp order and links. */
@kotlin.native.HiddenFromObjC
object HelpAndTips {
    @kotlin.native.HiddenFromObjC
    data class Item(val titleKey: String, val textKey: String, val videoLink: String?, val docPage: String?)

    private fun video(episode: TutorialVideos.Episode) = TutorialVideos.url(episode)

    val items: List<Item> = listOf(
        Item("help_nav_title", "help_nav_text", video(TutorialVideos.Episode.TUTORIAL_02_FINDING_YOUR_WAY), "navigation"),
        Item("help_contextmenus_title", "help_contextmenus_text", video(TutorialVideos.Episode.TUTORIAL_03_VERSE_MENU), null),
        Item("help_study_tools_title", "help_study_tools_text", video(TutorialVideos.Episode.TUTORIAL_04_STRONGS_DICTIONARIES_CROSSREFS), "documents"),
        Item("help_window_pinning_title", "help_window_pinning_text", video(TutorialVideos.Episode.TUTORIAL_06_WINDOWS_2_PIN_SYNC_LINKS), "windows"),
        Item("help_bookmarks_title", "help_bookmarks_text", video(TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS), "bookmarks"),
        Item("studypads", "help_studypads_text", video(TutorialVideos.Episode.TUTORIAL_11_STUDY_PADS), "study_pads"),
        Item("help_search_title", "help_search_text2", video(TutorialVideos.Episode.TUTORIAL_12_SEARCH_1_BASICS), "search"),
        Item("help_workspaces_title", "help_workspaces_text", video(TutorialVideos.Episode.TUTORIAL_07_WORKSPACES), "workspaces"),
        Item("help_look_and_feel_title", "help_look_and_feel_text", video(TutorialVideos.Episode.TUTORIAL_19_APPEARANCE_1_NIGHT_MODE_FONTS_COLOURS), "look_and_feel"),
        Item("help_hidden_features_title", "help_hidden_features_text", null, null),
    )
}
