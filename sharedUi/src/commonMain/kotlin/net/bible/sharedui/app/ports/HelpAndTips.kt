@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

/** The drawer help catalog, matching Android's CommonUtils.showHelp order and links. */
@kotlin.native.HiddenFromObjC
object HelpAndTips {
    @kotlin.native.HiddenFromObjC
    data class Item(val titleKey: String, val textKey: String, val videoLink: String?, val docPage: String?)

    private const val windows = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ"
    val items: List<Item> = listOf(
        Item("help_nav_title", "help_nav_text", null, "navigation"),
        Item("help_contextmenus_title", "help_contextmenus_text", null, null),
        Item("help_window_pinning_title", "help_window_pinning_text", windows, "windows"),
        Item("help_bookmarks_title", "help_bookmarks_text", "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MlzNt0Zpna-QoTBpEpWSden", "bookmarks"),
        Item("studypads", "help_studypads_text", "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m", "study_pads"),
        Item("help_search_title", "help_search_text2", null, "search"),
        Item("help_workspaces_title", "help_workspaces_text", windows, "workspaces"),
        Item("help_hidden_features_title", "help_hidden_features_text", null, null),
    )
}
