package net.bible.sharedcore.docs

/**
 * All documentation targets opened by the app. Add targets here, not at call sites.
 *
 * Keep entries literal: the website deep-link gate checks each target against the built docs. Entries
 * without anchors are the user guide pages, and their stems resolve in the generated table of contents.
 */
enum class DocsLink(val page: String, val anchor: String? = null) {
    AI("ai"),
    AI_AI_TOOLS("ai", "ai-tools"),
    AI_AVAILABLE_DATA_AND_DOCUMENTS("ai", "available-data-and-documents"),
    AI_AVAILABLE_MODELS("ai", "available-models"),
    AI_CHOOSING_A_PROVIDER("ai", "choosing-a-provider"),
    AI_CUSTOM_PROMPTS("ai", "custom-prompts"),
    AI_GETTING_STARTED("ai", "getting-started"),
    AI_SETTING_PERMISSIONS("ai", "setting-permissions"),
    BACKUP_RESTORE("backup_restore"),
    BIBLE_LINKS("bible_links"),
    BOOKMARKS("bookmarks"),
    CLOUD_SYNC("cloud_sync"),
    CUSTOMISATION("customisation"),
    CUSTOMISATION_CUSTOM_CSS("customisation/custom_css"),
    CUSTOMISATION_MODULE_CREATION("customisation/module_creation"),
    CUSTOM_REPOSITORIES("custom_repositories"),
    DESKTOP("desktop"),
    DISCRETE_BUILD("discrete_build"),
    DOCUMENTS("documents"),
    DOCUMENT_SYNC("document_sync"),
    FAQ("faq"),
    GETTING_STARTED("getting_started"),
    GETTING_STARTED_ANDROID("getting_started", "android"),
    GETTING_STARTED_INSTALLATION("getting_started", "installation"),
    GETTING_STARTED_IPHONE_AND_IPAD("getting_started", "iphone-and-ipad"),
    GETTING_STARTED_LINUX("getting_started", "linux"),
    GETTING_STARTED_MAC("getting_started", "mac"),
    GETTING_STARTED_WINDOWS("getting_started", "windows"),
    LABELS("labels"),
    LOOK_AND_FEEL("look_and_feel"),
    MEMORIZE("memorize"),
    MY_DOCUMENTS("my_documents"),
    NAVIGATION("navigation"),
    NOTES("notes"),
    PLATFORM_DIFFERENCES("platform_differences"),
    READING_PLANS("reading_plans"),
    READING_PROGRESS("reading_progress"),
    SEARCH("search"),
    SEARCH_ADVANCED_SEARCH("search", "advanced-search"),
    SHARE("share"),
    SPEAK("speak"),
    STUDY_PADS("study_pads"),
    SUPPORT("support"),
    USAGE("usage"),
    VERSE_ACTION_DIALOG("verse_action_dialog"),
    WINDOWS("windows"),
    WORKSPACES("workspaces");

    val url: String get() = DocsLinks.page(this)
}
